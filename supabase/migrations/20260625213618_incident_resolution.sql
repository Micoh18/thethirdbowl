begin;

create or replace function public.resolve_incident_assignment(
  p_assignment_id uuid,
  p_resolution_note text default null
)
returns table (
  assignment_id uuid,
  incident_id uuid,
  cat_id uuid,
  cat_name text,
  incident_state public.incident_state,
  resolved_at timestamptz
)
language plpgsql
security definer
set search_path = public, auth
as $$
#variable_conflict use_column
declare
  actor_id uuid;
  assignment_row public.incident_assignments;
  incident_row public.incidents;
  membership_row public.cat_memberships;
  cat_row public.cats;
  clean_note text;
begin
  actor_id := auth.uid();

  if actor_id is null then
    raise exception 'authenticated user required';
  end if;

  clean_note := nullif(left(trim(coalesce(p_resolution_note, '')), 1000), '');

  select a.*
  into assignment_row
  from public.incident_assignments a
  join public.cat_memberships m on m.id = a.membership_id
  where a.id = p_assignment_id
    and m.user_id = actor_id
    and m.status = 'active'
  for update of a;

  if assignment_row.id is null then
    raise exception 'incident assignment not available';
  end if;

  if assignment_row.state <> 'accepted' then
    raise exception 'accepted assignment required';
  end if;

  select *
  into membership_row
  from public.cat_memberships m
  where m.id = assignment_row.membership_id
    and m.user_id = actor_id
    and m.status = 'active';

  select *
  into incident_row
  from public.incidents i
  where i.id = assignment_row.incident_id
  for update;

  if incident_row.id is null or incident_row.state <> 'claimed' then
    raise exception 'claimed incident required';
  end if;

  if incident_row.claimed_by_membership_id <> membership_row.id then
    raise exception 'only the claimed responder can resolve this incident';
  end if;

  select *
  into cat_row
  from public.cats c
  where c.id = incident_row.cat_id;

  update public.incidents i
  set
    state = 'resolved',
    resolved_by_user_id = actor_id,
    resolved_at = now(),
    resolution_note = clean_note,
    updated_at = now()
  where i.id = incident_row.id
  returning * into incident_row;

  update public.access_grants g
  set
    revoked_at = now(),
    revocation_reason = 'incident_resolved'
  where g.incident_id = incident_row.id
    and g.membership_id = membership_row.id
    and g.revoked_at is null;

  insert into public.audit_events (
    actor_type,
    actor_id,
    cat_id,
    incident_id,
    event_type,
    target_type,
    target_id,
    outcome,
    metadata
  )
  values (
    'user',
    actor_id,
    incident_row.cat_id,
    incident_row.id,
    'incident.resolved',
    'incident',
    incident_row.id,
    'success',
    jsonb_build_object(
      'assignment_id',
      assignment_row.id,
      'membership_id',
      membership_row.id,
      'grant_revocation_reason',
      'incident_resolved'
    )
  );

  return query
  select
    assignment_row.id,
    incident_row.id,
    cat_row.id,
    cat_row.name,
    incident_row.state,
    incident_row.resolved_at;
end;
$$;

revoke all on function public.resolve_incident_assignment(uuid, text) from public;
grant execute on function public.resolve_incident_assignment(uuid, text) to authenticated;

commit;
