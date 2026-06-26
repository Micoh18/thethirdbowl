begin;

create or replace function public.activate_continuity_plan(target_cat_id uuid)
returns table (
  id uuid,
  cat_id uuid,
  status public.plan_status,
  timezone text,
  schedule_type text,
  grace_period_minutes integer,
  next_check_in_at timestamptz
)
language plpgsql
security definer
set search_path = public, auth
as $$
#variable_conflict use_column
declare
  actor_id uuid;
  plan_row public.continuity_plans;
  interval_minutes integer := 5;
begin
  actor_id := auth.uid();

  if actor_id is null then
    raise exception 'authenticated user required';
  end if;

  if not private.is_primary_caregiver(target_cat_id) then
    raise exception 'primary caregiver access required';
  end if;

  if not exists (
    select 1
    from public.capsules c
    join public.capsule_sections s on s.capsule_id = c.id
    where c.cat_id = target_cat_id
      and s.scope = 'CARE_CORE'
  ) then
    raise exception 'CARE_CORE capsule section required before arming';
  end if;

  if not exists (
    select 1
    from public.cat_memberships m
    where m.cat_id = target_cat_id
      and m.status = 'active'
      and m.user_id <> actor_id
      and exists (
        select 1
        from public.membership_permissions mp
        where mp.membership_id = m.id
          and mp.permission = 'INCIDENT_RESPOND'
          and mp.activation_context = 'ACTIVE_INCIDENT'
          and mp.revoked_at is null
      )
      and exists (
        select 1
        from public.membership_scopes ms
        where ms.membership_id = m.id
          and ms.scope = 'CARE_CORE'
          and ms.activation_context = 'ACTIVE_INCIDENT'
          and ms.revoked_at is null
      )
  ) then
    raise exception 'accepted Care Circle responder with CARE_CORE access required before arming';
  end if;

  insert into public.continuity_plans (
    cat_id,
    status,
    timezone,
    schedule_type,
    schedule_config,
    grace_period_minutes,
    next_check_in_at,
    armed_at
  )
  values (
    target_cat_id,
    'armed',
    'UTC',
    'recurring_check_in',
    jsonb_build_object('interval_minutes', interval_minutes),
    5,
    now() + make_interval(mins => interval_minutes),
    now()
  )
  on conflict (cat_id) do update
  set
    status = 'armed',
    timezone = excluded.timezone,
    schedule_type = excluded.schedule_type,
    schedule_config = excluded.schedule_config,
    grace_period_minutes = excluded.grace_period_minutes,
    next_check_in_at = excluded.next_check_in_at,
    armed_at = now(),
    paused_at = null,
    updated_at = now()
  returning * into plan_row;

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
    'plan.armed',
    'continuity_plan',
    plan_row.id,
    'success',
    jsonb_build_object('schedule_type', plan_row.schedule_type)
  );

  return query
  select
    plan_row.id,
    plan_row.cat_id,
    plan_row.status,
    plan_row.timezone,
    plan_row.schedule_type,
    plan_row.grace_period_minutes,
    plan_row.next_check_in_at;
end;
$$;

revoke all on function public.activate_continuity_plan(uuid) from public;
grant execute on function public.activate_continuity_plan(uuid) to authenticated;

commit;
