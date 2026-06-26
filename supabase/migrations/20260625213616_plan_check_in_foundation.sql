begin;

create or replace function public.get_or_create_plan(target_cat_id uuid)
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
begin
  actor_id := auth.uid();

  if actor_id is null then
    raise exception 'authenticated user required';
  end if;

  if not private.is_primary_caregiver(target_cat_id) then
    raise exception 'primary caregiver access required';
  end if;

  insert into public.continuity_plans (
    cat_id,
    status,
    timezone,
    schedule_type,
    schedule_config,
    grace_period_minutes,
    next_check_in_at
  )
  values (
    target_cat_id,
    'draft',
    'UTC',
    'manual_demo',
    jsonb_build_object('interval_minutes', 5),
    5,
    null
  )
  on conflict (cat_id) do update
  set updated_at = public.continuity_plans.updated_at
  returning * into plan_row;

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

create or replace function public.arm_demo_plan(target_cat_id uuid)
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
  ) then
    raise exception 'accepted Care Circle member required before arming';
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
    'manual_demo',
    jsonb_build_object('interval_minutes', 5),
    5,
    now() + interval '5 minutes',
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

create or replace function public.complete_demo_check_in(target_cat_id uuid)
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
    next_check_in_at = now() + interval '5 minutes',
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

revoke all on function public.get_or_create_plan(uuid) from public;
revoke all on function public.arm_demo_plan(uuid) from public;
revoke all on function public.complete_demo_check_in(uuid) from public;
grant execute on function public.get_or_create_plan(uuid) to authenticated;
grant execute on function public.arm_demo_plan(uuid) to authenticated;
grant execute on function public.complete_demo_check_in(uuid) to authenticated;

commit;
