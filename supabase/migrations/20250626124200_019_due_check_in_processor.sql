begin;

create or replace function public.process_due_check_ins(p_now timestamptz default now())
returns table (
  processed_plans integer,
  incidents_created integer,
  assignments_created integer
)
language plpgsql
security definer
set search_path = public, auth, extensions
as $$
#variable_conflict use_column
declare
  plan_row public.continuity_plans;
  cycle_row public.check_in_cycles;
  incident_row public.incidents;
  assignment_row public.incident_assignments;
  responder_membership public.cat_memberships;
  delivery_row public.notification_deliveries;
  next_sequence integer;
  due_time timestamptz;
  grace_end_time timestamptz;
  processed_count integer := 0;
  incident_count integer := 0;
  assignment_count integer := 0;
begin
  for plan_row in
    select *
    from public.continuity_plans p
    where p.status = 'armed'
      and p.next_check_in_at is not null
      and p.next_check_in_at + make_interval(mins => p.grace_period_minutes) <= p_now
      and not exists (
        select 1
        from public.incidents i
        where i.plan_id = p.id
          and i.activation_type = 'missed_check_in'
          and i.state in ('active', 'awaiting_response', 'claimed')
      )
    order by p.next_check_in_at
    for update skip locked
  loop
    processed_count := processed_count + 1;

    select m.*
    into responder_membership
    from public.cat_memberships m
    where m.cat_id = plan_row.cat_id
      and m.status = 'active'
      and m.user_id <> (
        select c.primary_caregiver_user_id
        from public.cats c
        where c.id = plan_row.cat_id
      )
      and exists (
        select 1
        from public.membership_permissions mp
        where mp.membership_id = m.id
          and mp.permission = 'INCIDENT_RESPOND'
          and mp.activation_context = 'ACTIVE_INCIDENT'
          and mp.revoked_at is null
      )
    order by m.accepted_at nulls last, m.created_at
    limit 1;

    if responder_membership.id is null then
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
        'workflow',
        null,
        plan_row.cat_id,
        'check_in.missed_no_responder',
        'continuity_plan',
        plan_row.id,
        'failed',
        jsonb_build_object('next_check_in_at', plan_row.next_check_in_at)
      );

      continue;
    end if;

    select coalesce(max(c.sequence_number), 0) + 1
    into next_sequence
    from public.check_in_cycles c
    where c.plan_id = plan_row.id;

    due_time := plan_row.next_check_in_at;
    grace_end_time := due_time + make_interval(mins => plan_row.grace_period_minutes);

    insert into public.check_in_cycles (
      plan_id,
      sequence_number,
      scheduled_at,
      due_at,
      grace_ends_at,
      state
    )
    values (
      plan_row.id,
      next_sequence,
      due_time,
      due_time,
      grace_end_time,
      'missed'
    )
    returning * into cycle_row;

    insert into public.incidents (
      cat_id,
      plan_id,
      cycle_id,
      activation_type,
      state,
      activated_at
    )
    values (
      plan_row.cat_id,
      plan_row.id,
      cycle_row.id,
      'missed_check_in',
      'awaiting_response',
      p_now
    )
    returning * into incident_row;

    insert into public.notification_deliveries (
      recipient_user_id,
      channel,
      notification_type,
      provider,
      incident_id,
      delivery_status
    )
    values (
      responder_membership.user_id,
      'email',
      'incident_assignment',
      'email_adapter',
      incident_row.id,
      'request_created'
    )
    returning * into delivery_row;

    insert into public.incident_assignments (
      incident_id,
      membership_id,
      step_number,
      response_deadline_at,
      state,
      notification_delivery_id
    )
    values (
      incident_row.id,
      responder_membership.id,
      1,
      p_now + interval '30 minutes',
      'pending',
      delivery_row.id
    )
    returning * into assignment_row;

    update public.continuity_plans
    set
      next_check_in_at = null,
      updated_at = p_now
    where id = plan_row.id;

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
    values
      (
        'workflow',
        null,
        plan_row.cat_id,
        incident_row.id,
        'check_in.missed',
        'check_in_cycle',
        cycle_row.id,
        'success',
        jsonb_build_object('source', 'due_check_in_processor')
      ),
      (
        'workflow',
        null,
        plan_row.cat_id,
        incident_row.id,
        'incident.created',
        'incident',
        incident_row.id,
        'success',
        jsonb_build_object('activation_type', incident_row.activation_type)
      ),
      (
        'workflow',
        null,
        plan_row.cat_id,
        incident_row.id,
        'incident.assignment_created',
        'incident_assignment',
        assignment_row.id,
        'success',
        jsonb_build_object('notification_delivery_id', delivery_row.id)
      );

    incident_count := incident_count + 1;
    assignment_count := assignment_count + 1;
  end loop;

  return query
  select processed_count, incident_count, assignment_count;
end;
$$;

revoke all on function public.process_due_check_ins(timestamptz) from public;
grant execute on function public.process_due_check_ins(timestamptz) to service_role;

commit;
