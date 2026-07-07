begin;

create or replace function private.bootstrap_primary_membership()
returns trigger
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  primary_membership_id uuid;
begin
  insert into public.cat_memberships (
    cat_id,
    user_id,
    relationship_label,
    status,
    accepted_at
  )
  values (
    new.id,
    new.primary_caregiver_user_id,
    'Primary caregiver',
    'active',
    now()
  )
  returning id into primary_membership_id;

  insert into public.membership_roles (
    membership_id,
    role,
    assigned_by_user_id
  )
  values (
    primary_membership_id,
    'PRIMARY_CAREGIVER',
    new.primary_caregiver_user_id
  );

  insert into public.membership_permissions (
    membership_id,
    permission,
    activation_context,
    assigned_by_user_id
  )
  select
    primary_membership_id,
    permission,
    'STANDING',
    new.primary_caregiver_user_id
  from unnest(array[
    'CAT_VIEW_BASIC',
    'CAPSULE_EDIT',
    'CARE_LOG_READ',
    'CARE_LOG_WRITE',
    'CHECK_IN_COMPLETE',
    'PLAN_MANAGE',
    'CIRCLE_MANAGE',
    'COVERAGE_MANAGE',
    'INCIDENT_RESPOND',
    'INCIDENT_RESOLVE',
    'AUDIT_READ'
  ]::public.action_permission[]) as permission;

  insert into public.membership_scopes (
    membership_id,
    scope,
    activation_context,
    assigned_by_user_id
  )
  select
    primary_membership_id,
    scope,
    'STANDING',
    new.primary_caregiver_user_id
  from unnest(array[
    'CARE_CORE',
    'HOME_ACCESS',
    'MEDICAL',
    'COORDINATION',
    'FULL_CAPSULE'
  ]::public.data_scope[]) as scope;

  insert into public.capsules (
    cat_id,
    updated_by_user_id
  )
  values (
    new.id,
    new.primary_caregiver_user_id
  );

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
    new.primary_caregiver_user_id,
    new.id,
    'cat.created',
    'cat',
    new.id,
    'success',
    jsonb_build_object('bootstrap', true)
  );

  return new;
end;
$$;

revoke all on function private.bootstrap_primary_membership() from public;

drop trigger if exists cats_bootstrap_primary_membership on public.cats;

create trigger cats_bootstrap_primary_membership
  after insert on public.cats
  for each row execute function private.bootstrap_primary_membership();

grant usage on schema public to authenticated;

grant select, insert, update on public.profiles to authenticated;
grant select, insert, update on public.cats to authenticated;
grant select on public.cat_memberships to authenticated;
grant select on public.membership_roles to authenticated;
grant select on public.membership_permissions to authenticated;
grant select on public.membership_scopes to authenticated;
grant select on public.invitations to authenticated;
grant select, update on public.capsules to authenticated;
grant select on public.capsule_sections to authenticated;
grant select on public.continuity_plans to authenticated;
grant select on public.check_in_cycles to authenticated;
grant select on public.incidents to authenticated;
grant select on public.incident_assignments to authenticated;
grant select on public.access_grants to authenticated;
grant select on public.notification_deliveries to authenticated;
grant select on public.audit_events to authenticated;

grant all on all tables in schema public to service_role;
grant usage on schema private to service_role;

commit;
