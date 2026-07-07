begin;

create or replace function public.list_my_incident_capsule_sections(p_incident_id uuid)
returns table (
  incident_id uuid,
  cat_id uuid,
  cat_name text,
  scope public.data_scope,
  content_json jsonb,
  updated_at timestamptz
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

  if not exists (
    select 1
    from public.access_grants g
    join public.cat_memberships m on m.id = g.membership_id
    join public.incidents i on i.id = g.incident_id
    where g.incident_id = p_incident_id
      and m.user_id = actor_id
      and m.status = 'active'
      and i.state in ('active', 'awaiting_response', 'claimed')
      and g.revoked_at is null
      and g.expires_at > now()
  ) then
    raise exception 'active incident grant required';
  end if;

  return query
  select
    i.id,
    c.id,
    c.name,
    s.scope,
    s.content_json,
    s.updated_at
  from public.incidents i
  join public.cats c on c.id = i.cat_id
  join public.capsules cap on cap.cat_id = c.id
  join public.capsule_sections s on s.capsule_id = cap.id
  where i.id = p_incident_id
    and exists (
      select 1
      from public.access_grants g
      join public.cat_memberships m on m.id = g.membership_id
      where g.incident_id = i.id
        and m.user_id = actor_id
        and m.status = 'active'
        and g.revoked_at is null
        and g.expires_at > now()
        and (
          s.scope = any(g.scopes)
          or 'FULL_CAPSULE'::public.data_scope = any(g.scopes)
        )
    )
  order by
    case s.scope
      when 'CARE_CORE' then 1
      when 'HOME_ACCESS' then 2
      when 'MEDICAL' then 3
      else 4
    end;
end;
$$;

create or replace function public.trigger_developer_missed_check_in(target_cat_id uuid)
returns table (
  processed_plans integer,
  incidents_created integer,
  assignments_created integer
)
language plpgsql
security definer
set search_path = public, auth
as $$
#variable_conflict use_column
declare
  actor_id uuid;
  plan_row public.continuity_plans;
begin
  actor_id := auth.uid();

  if actor_id is null then
    raise exception 'authenticated user required';
  end if;

  if not private.is_primary_caregiver(target_cat_id) then
    raise exception 'primary caregiver access required';
  end if;

  select *
  into plan_row
  from public.continuity_plans p
  where p.cat_id = target_cat_id
  for update;

  if plan_row.id is null or plan_row.status <> 'armed' then
    raise exception 'armed plan required';
  end if;

  update public.continuity_plans p
  set
    next_check_in_at = now() - make_interval(mins => p.grace_period_minutes + 1),
    updated_at = now()
  where p.id = plan_row.id;

  insert into public.audit_events (
    actor_type,
    actor_id,
    cat_id,
    event_type,
    target_type,
    target_id,
    outcome,
    metadata
  )
  values (
    'user',
    actor_id,
    target_cat_id,
    'developer.missed_check_in_triggered',
    'continuity_plan',
    plan_row.id,
    'success',
    jsonb_build_object('source', 'developer_settings')
  );

  return query
  select *
  from public.process_due_check_ins(now());
end;
$$;

revoke all on function public.list_my_incident_capsule_sections(uuid) from public;
revoke all on function public.trigger_developer_missed_check_in(uuid) from public;
grant execute on function public.list_my_incident_capsule_sections(uuid) to authenticated;
grant execute on function public.trigger_developer_missed_check_in(uuid) to authenticated;

commit;
