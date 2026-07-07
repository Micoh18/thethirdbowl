  begin;

  create or replace function public.activate_demo_missed_check_in(target_cat_id uuid)
  returns table (
    incident_id uuid,
    cat_id uuid,
    cat_name text,
    incident_state public.incident_state,
    assignment_id uuid,
    assignment_state public.incident_assignment_state,
    assigned_relationship_label text,
    activated_at timestamptz,
    response_deadline_at timestamptz
  )
  language plpgsql
  security definer
  set search_path = public, auth, extensions
  as $$
  #variable_conflict use_column
  declare
    actor_id uuid;
    cat_row public.cats;
    plan_row public.continuity_plans;
    cycle_row public.check_in_cycles;
    incident_row public.incidents;
    assignment_row public.incident_assignments;
    responder_membership public.cat_memberships;
    next_sequence integer;
    due_time timestamptz;
  begin
    actor_id := auth.uid();

    if actor_id is null then
      raise exception 'authenticated user required';
    end if;

    if not private.is_primary_caregiver(target_cat_id) then
      raise exception 'primary caregiver access required';
    end if;

    select *
    into cat_row
    from public.cats c
    where c.id = target_cat_id
      and c.status = 'active';

    if cat_row.id is null then
      raise exception 'active cat required';
    end if;

    select *
    into plan_row
    from public.continuity_plans p
    where p.cat_id = target_cat_id
    for update;

    if plan_row.id is null or plan_row.status <> 'armed' then
      raise exception 'armed plan required';
    end if;

    select i.*
    into incident_row
    from public.incidents i
    where i.cat_id = target_cat_id
      and i.plan_id = plan_row.id
      and i.activation_type = 'missed_check_in'
      and i.state in ('active', 'awaiting_response', 'claimed')
    order by i.activated_at desc
    limit 1;

    if incident_row.id is null then
      select m.*
      into responder_membership
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
      order by m.accepted_at nulls last, m.created_at
      limit 1;

      if responder_membership.id is null then
        raise exception 'accepted incident responder required';
      end if;

      select coalesce(max(c.sequence_number), 0) + 1
      into next_sequence
      from public.check_in_cycles c
      where c.plan_id = plan_row.id;

      due_time := coalesce(plan_row.next_check_in_at, now());

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
        due_time + make_interval(mins => plan_row.grace_period_minutes),
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
        target_cat_id,
        plan_row.id,
        cycle_row.id,
        'missed_check_in',
        'awaiting_response',
        now()
      )
      returning * into incident_row;

      insert into public.incident_assignments (
        incident_id,
        membership_id,
        step_number,
        response_deadline_at,
        state
      )
      values (
        incident_row.id,
        responder_membership.id,
        1,
        now() + interval '30 minutes',
        'pending'
      )
      returning * into assignment_row;

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
          'system',
          null,
          target_cat_id,
          incident_row.id,
          'check_in.missed',
          'check_in_cycle',
          cycle_row.id,
          'success',
          jsonb_build_object('source', 'manual_demo')
        ),
        (
          'system',
          null,
          target_cat_id,
          incident_row.id,
          'incident.created',
          'incident',
          incident_row.id,
          'success',
          jsonb_build_object('activation_type', incident_row.activation_type)
        ),
        (
          'system',
          null,
          target_cat_id,
          incident_row.id,
          'incident.assignment_created',
          'incident_assignment',
          assignment_row.id,
          'success',
          jsonb_build_object('notification_delivery_connected', false)
        );
    else
      select a.*
      into assignment_row
      from public.incident_assignments a
      where a.incident_id = incident_row.id
      order by a.step_number
      limit 1;

      select m.*
      into responder_membership
      from public.cat_memberships m
      where m.id = assignment_row.membership_id;
    end if;

    return query
    select
      incident_row.id,
      cat_row.id,
      cat_row.name,
      incident_row.state,
      assignment_row.id,
      assignment_row.state,
      responder_membership.relationship_label,
      incident_row.activated_at,
      assignment_row.response_deadline_at;
  end;
  $$;

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
    response_deadline_at timestamptz
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
      a.response_deadline_at
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

  create or replace function public.accept_incident_assignment(p_assignment_id uuid)
  returns table (
    assignment_id uuid,
    incident_id uuid,
    cat_id uuid,
    cat_name text,
    incident_state public.incident_state,
    assignment_state public.incident_assignment_state,
    granted_scopes public.data_scope[],
    grant_expires_at timestamptz
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
    scopes_to_grant public.data_scope[];
    grant_expires timestamptz;
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

    select *
    into incident_row
    from public.incidents i
    where i.id = assignment_row.incident_id
    for update;

    if incident_row.id is null or incident_row.state not in ('active', 'awaiting_response') then
      raise exception 'incident is not accepting responses';
    end if;

    if assignment_row.state not in ('pending', 'notified') then
      raise exception 'assignment is not pending';
    end if;

    if assignment_row.response_deadline_at <= now() then
      update public.incident_assignments
      set
        state = 'timed_out',
        updated_at = now()
      where id = assignment_row.id;

      raise exception 'assignment response deadline has passed';
    end if;

    select *
    into membership_row
    from public.cat_memberships m
    where m.id = assignment_row.membership_id
      and m.status = 'active';

    select *
    into cat_row
    from public.cats c
    where c.id = incident_row.cat_id;

    select array_agg(distinct ms.scope order by ms.scope)
    into scopes_to_grant
    from public.membership_scopes ms
    where ms.membership_id = membership_row.id
      and ms.activation_context = 'ACTIVE_INCIDENT'
      and ms.revoked_at is null;

    if scopes_to_grant is null or cardinality(scopes_to_grant) = 0 then
      raise exception 'no incident data scopes assigned';
    end if;

    grant_expires := now() + interval '12 hours';

    update public.incident_assignments
    set
      state = 'accepted',
      responded_at = now(),
      updated_at = now()
    where id = assignment_row.id
    returning * into assignment_row;

    update public.incident_assignments
    set
      state = 'cancelled',
      updated_at = now()
    where incident_id = incident_row.id
      and id <> assignment_row.id
      and state in ('pending', 'notified');

    update public.incidents
    set
      state = 'claimed',
      claimed_by_membership_id = membership_row.id,
      claimed_at = now(),
      updated_at = now()
    where id = incident_row.id
    returning * into incident_row;

    insert into public.access_grants (
      incident_id,
      membership_id,
      scopes,
      expires_at,
      issued_by
    )
    values (
      incident_row.id,
      membership_row.id,
      scopes_to_grant,
      grant_expires,
      'workflow'
    );

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
        'user',
        actor_id,
        incident_row.cat_id,
        incident_row.id,
        'incident.assignment_accepted',
        'incident_assignment',
        assignment_row.id,
        'success',
        jsonb_build_object('membership_id', membership_row.id)
      ),
      (
        'system',
        null,
        incident_row.cat_id,
        incident_row.id,
        'access_grant.issued',
        'access_grant',
        null,
        'success',
        jsonb_build_object('scopes', scopes_to_grant, 'expires_at', grant_expires)
      );

    return query
    select
      assignment_row.id,
      incident_row.id,
      cat_row.id,
      cat_row.name,
      incident_row.state,
      assignment_row.state,
      scopes_to_grant,
      grant_expires;
  end;
  $$;

  create or replace function public.list_my_incident_care_core(p_incident_id uuid)
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

    if not private.has_incident_scope(p_incident_id, 'CARE_CORE') then
      raise exception 'active CARE_CORE grant required';
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
      and s.scope = 'CARE_CORE';
  end;
  $$;

  revoke all on function public.activate_demo_missed_check_in(uuid) from public;
  revoke all on function public.list_my_incident_assignments() from public;
  revoke all on function public.accept_incident_assignment(uuid) from public;
  revoke all on function public.list_my_incident_care_core(uuid) from public;
  grant execute on function public.activate_demo_missed_check_in(uuid) to authenticated;
  grant execute on function public.list_my_incident_assignments() to authenticated;
  grant execute on function public.accept_incident_assignment(uuid) to authenticated;
  grant execute on function public.list_my_incident_care_core(uuid) to authenticated;

  commit;
