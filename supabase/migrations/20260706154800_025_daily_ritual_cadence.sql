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
  interval_minutes integer := 1440;
  grace_minutes integer := 60;
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
    grace_minutes,
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

create or replace function public.complete_continuity_check_in(target_cat_id uuid)
returns table (
  cycle_id uuid,
  plan_id uuid,
  cat_id uuid,
  completed_at timestamptz,
  next_check_in_at timestamptz
)
language plpgsql
security definer
set search_path = public, auth, extensions
as $$
#variable_conflict use_column
declare
  actor_id uuid;
  plan_row public.continuity_plans;
  cycle_row public.check_in_cycles;
  next_sequence integer;
  interval_minutes integer;
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

  interval_minutes := coalesce((plan_row.schedule_config ->> 'interval_minutes')::integer, 1440);

  select coalesce(max(sequence_number), 0) + 1
  into next_sequence
  from public.check_in_cycles
  where plan_id = plan_row.id;

  insert into public.check_in_cycles (
    plan_id,
    sequence_number,
    scheduled_at,
    due_at,
    grace_ends_at,
    state,
    completed_at,
    completed_by_user_id,
    completion_source
  )
  values (
    plan_row.id,
    next_sequence,
    coalesce(plan_row.next_check_in_at, now()),
    coalesce(plan_row.next_check_in_at, now()),
    coalesce(plan_row.next_check_in_at, now()) + make_interval(mins => plan_row.grace_period_minutes),
    'completed',
    now(),
    actor_id,
    'android'
  )
  returning * into cycle_row;

  update public.continuity_plans
  set
    schedule_type = case
      when schedule_type = 'manual_demo' then 'recurring_check_in'
      else schedule_type
    end,
    next_check_in_at = now() + make_interval(mins => interval_minutes),
    updated_at = now()
  where id = plan_row.id
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
    'check_in.completed',
    'check_in_cycle',
    cycle_row.id,
    'success',
    jsonb_build_object('completion_source', cycle_row.completion_source)
  );

  return query
  select
    cycle_row.id,
    plan_row.id,
    plan_row.cat_id,
    cycle_row.completed_at,
    plan_row.next_check_in_at;
end;
$$;

update public.continuity_plans p
set
  schedule_type = 'recurring_check_in',
  schedule_config = jsonb_set(
    coalesce(p.schedule_config, '{}'::jsonb),
    '{interval_minutes}',
    to_jsonb(1440),
    true
  ),
  grace_period_minutes = greatest(coalesce(p.grace_period_minutes, 0), 60),
  next_check_in_at = case
    when p.status = 'armed'
      and p.next_check_in_at is not null
      and p.next_check_in_at < now() + interval '23 hours'
      then now() + interval '1 day'
    else p.next_check_in_at
  end,
  updated_at = now()
where p.status = 'armed';

revoke all on function public.activate_continuity_plan(uuid) from public;
revoke all on function public.complete_continuity_check_in(uuid) from public;
grant execute on function public.activate_continuity_plan(uuid) to authenticated;
grant execute on function public.complete_continuity_check_in(uuid) to authenticated;

notify pgrst, 'reload schema';

commit;
