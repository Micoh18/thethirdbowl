begin;

alter table public.incident_assignments
  add column if not exists cat_reached_at timestamptz;

create or replace function public.list_my_incident_assignments()
returns table (
  assignment_id uuid,
  incident_id uuid,
  cat_id uuid,
  cat_name text,
  incident_state public.incident_state,
  assignment_state public.incident_assignment_state,
  relationship_label text,
  activated_at timestamptz,
  response_deadline_at timestamptz,
  cat_reached_at timestamptz
)
language plpgsql
security definer
set search_path = public, auth
as $$
#variable_conflict use_column
declare
  actor_id uuid;
begin
  actor_id := auth.uid();

  if actor_id is null then
    raise exception 'authenticated user required';
  end if;

  return query
  select
    a.id,
    i.id,
    c.id,
    c.name,
    i.state,
    a.state,
    m.relationship_label,
    i.activated_at,
    a.response_deadline_at,
    a.cat_reached_at
  from public.incident_assignments a
  join public.incidents i on i.id = a.incident_id
  join public.cats c on c.id = i.cat_id
  join public.cat_memberships m on m.id = a.membership_id
  where m.user_id = actor_id
    and m.status = 'active'
    and i.state in ('active', 'awaiting_response', 'claimed')
  order by i.activated_at desc;
end;
$$;

create or replace function public.record_cat_reached(p_assignment_id uuid)
returns table (
  assignment_id uuid,
  incident_id uuid,
  cat_id uuid,
  cat_name text,
  cat_reached_at timestamptz
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
begin
  actor_id := auth.uid();

  if actor_id is null then
    raise exception 'authenticated user required';
  end if;

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
    raise exception 'only the claimed responder can update this handoff';
  end if;

  update public.incident_assignments
  set
    cat_reached_at = coalesce(cat_reached_at, now()),
    updated_at = now()
  where id = assignment_row.id
  returning * into assignment_row;

  select *
  into cat_row
  from public.cats c
  where c.id = incident_row.cat_id;

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
    'incident.cat_reached',
    'incident_assignment',
    assignment_row.id,
    'success',
    jsonb_build_object('membership_id', membership_row.id)
  );

  return query
  select
    assignment_row.id,
    incident_row.id,
    cat_row.id,
    cat_row.name,
    assignment_row.cat_reached_at;
end;
$$;

revoke all on function public.list_my_incident_assignments() from public;
revoke all on function public.record_cat_reached(uuid) from public;
grant execute on function public.list_my_incident_assignments() to authenticated;
grant execute on function public.record_cat_reached(uuid) to authenticated;

commit;
