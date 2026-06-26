begin;

create extension if not exists pgcrypto with schema extensions;

create schema if not exists private;
revoke all on schema private from public;
grant usage on schema private to authenticated;

do $$
begin
  create type public.account_status as enum ('active', 'suspended', 'deleted');
exception when duplicate_object then null;
end $$;

do $$
begin
  create type public.cat_status as enum ('active', 'archived');
exception when duplicate_object then null;
end $$;

do $$
begin
  create type public.membership_status as enum ('active', 'suspended', 'revoked');
exception when duplicate_object then null;
end $$;

do $$
begin
  create type public.activation_context as enum ('STANDING', 'ACTIVE_COVERAGE', 'ACTIVE_INCIDENT');
exception when duplicate_object then null;
end $$;

do $$
begin
  create type public.membership_role as enum (
    'PRIMARY_CAREGIVER',
    'CO_CAREGIVER',
    'EMERGENCY_GUARDIAN',
    'KEYHOLDER',
    'MEDICAL_CONTACT'
  );
exception when duplicate_object then null;
end $$;

do $$
begin
  create type public.action_permission as enum (
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
  );
exception when duplicate_object then null;
end $$;

do $$
begin
  create type public.data_scope as enum (
    'CARE_CORE',
    'HOME_ACCESS',
    'MEDICAL',
    'COORDINATION',
    'FULL_CAPSULE'
  );
exception when duplicate_object then null;
end $$;

do $$
begin
  create type public.invitation_status as enum ('pending', 'accepted', 'declined', 'expired', 'revoked');
exception when duplicate_object then null;
end $$;

do $$
begin
  create type public.plan_status as enum ('draft', 'armed', 'paused', 'disarmed');
exception when duplicate_object then null;
end $$;

do $$
begin
  create type public.check_in_cycle_state as enum ('scheduled', 'due', 'completed', 'grace', 'missed', 'cancelled');
exception when duplicate_object then null;
end $$;

do $$
begin
  create type public.completion_source as enum ('android', 'secure_web');
exception when duplicate_object then null;
end $$;

do $$
begin
  create type public.incident_activation_type as enum ('missed_check_in', 'manual');
exception when duplicate_object then null;
end $$;

do $$
begin
  create type public.incident_state as enum ('active', 'awaiting_response', 'claimed', 'resolved', 'cancelled');
exception when duplicate_object then null;
end $$;

do $$
begin
  create type public.incident_assignment_state as enum (
    'pending',
    'notified',
    'accepted',
    'declined',
    'timed_out',
    'failed',
    'cancelled'
  );
exception when duplicate_object then null;
end $$;

do $$
begin
  create type public.grant_issuer as enum ('workflow', 'authorized_user');
exception when duplicate_object then null;
end $$;

do $$
begin
  create type public.notification_channel as enum ('email', 'push');
exception when duplicate_object then null;
end $$;

do $$
begin
  create type public.delivery_status as enum (
    'request_created',
    'provider_accepted',
    'delivered',
    'bounced',
    'rejected',
    'failed',
    'unknown'
  );
exception when duplicate_object then null;
end $$;

do $$
begin
  create type public.audit_actor_type as enum ('user', 'system', 'workflow', 'provider');
exception when duplicate_object then null;
end $$;

do $$
begin
  create type public.audit_outcome as enum ('success', 'denied', 'failed');
exception when duplicate_object then null;
end $$;

create or replace function public.set_updated_at()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  email_normalized text not null unique,
  email_verified_at timestamptz,
  display_name text not null default '',
  timezone text not null default 'UTC',
  status public.account_status not null default 'active',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint profiles_email_normalized_lowercase check (email_normalized = lower(email_normalized)),
  constraint profiles_display_name_length check (char_length(display_name) <= 120)
);

create table if not exists public.cats (
  id uuid primary key default extensions.gen_random_uuid(),
  primary_caregiver_user_id uuid not null references auth.users(id) on delete restrict,
  name text not null,
  approximate_age text,
  photo_object_key text,
  status public.cat_status not null default 'active',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint cats_name_length check (char_length(name) between 1 and 120)
);

create table if not exists public.cat_memberships (
  id uuid primary key default extensions.gen_random_uuid(),
  cat_id uuid not null references public.cats(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  relationship_label text not null,
  status public.membership_status not null default 'active',
  accepted_at timestamptz,
  suspended_at timestamptz,
  revoked_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint cat_memberships_relationship_label_length check (char_length(relationship_label) between 1 and 80)
);

create unique index if not exists cat_memberships_one_effective_per_user
  on public.cat_memberships (cat_id, user_id)
  where status in ('active', 'suspended');

create table if not exists public.membership_roles (
  membership_id uuid not null references public.cat_memberships(id) on delete cascade,
  role public.membership_role not null,
  assigned_by_user_id uuid references auth.users(id) on delete set null,
  assigned_at timestamptz not null default now(),
  revoked_at timestamptz,
  primary key (membership_id, role, assigned_at)
);

create unique index if not exists membership_roles_one_active_role
  on public.membership_roles (membership_id, role)
  where revoked_at is null;

create table if not exists public.membership_permissions (
  membership_id uuid not null references public.cat_memberships(id) on delete cascade,
  permission public.action_permission not null,
  activation_context public.activation_context not null,
  assigned_by_user_id uuid references auth.users(id) on delete set null,
  assigned_at timestamptz not null default now(),
  revoked_at timestamptz,
  primary key (membership_id, permission, activation_context, assigned_at)
);

create unique index if not exists membership_permissions_one_active_permission
  on public.membership_permissions (membership_id, permission, activation_context)
  where revoked_at is null;

create table if not exists public.membership_scopes (
  membership_id uuid not null references public.cat_memberships(id) on delete cascade,
  scope public.data_scope not null,
  activation_context public.activation_context not null,
  assigned_by_user_id uuid references auth.users(id) on delete set null,
  assigned_at timestamptz not null default now(),
  revoked_at timestamptz,
  primary key (membership_id, scope, activation_context, assigned_at)
);

create unique index if not exists membership_scopes_one_active_scope
  on public.membership_scopes (membership_id, scope, activation_context)
  where revoked_at is null;

create table if not exists public.invitations (
  id uuid primary key default extensions.gen_random_uuid(),
  cat_id uuid not null references public.cats(id) on delete cascade,
  invited_email_ciphertext bytea,
  invited_email_hash bytea not null,
  relationship_label text not null,
  proposed_roles public.membership_role[] not null default '{}',
  proposed_permissions public.action_permission[] not null default '{}',
  proposed_scopes public.data_scope[] not null default '{}',
  token_hash bytea not null unique,
  status public.invitation_status not null default 'pending',
  expires_at timestamptz not null,
  created_by_user_id uuid not null references auth.users(id) on delete restrict,
  accepted_by_user_id uuid references auth.users(id) on delete set null,
  accepted_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.capsules (
  id uuid primary key default extensions.gen_random_uuid(),
  cat_id uuid not null unique references public.cats(id) on delete cascade,
  version integer not null default 1,
  last_reviewed_at timestamptz,
  updated_by_user_id uuid references auth.users(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint capsules_positive_version check (version > 0)
);

create table if not exists public.capsule_sections (
  id uuid primary key default extensions.gen_random_uuid(),
  capsule_id uuid not null references public.capsules(id) on delete cascade,
  scope public.data_scope not null,
  schema_version integer not null default 1,
  content_ciphertext bytea not null,
  updated_by_user_id uuid references auth.users(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint capsule_sections_supported_scope check (scope in ('CARE_CORE', 'HOME_ACCESS', 'MEDICAL')),
  constraint capsule_sections_positive_schema_version check (schema_version > 0),
  unique (capsule_id, scope)
);

create table if not exists public.continuity_plans (
  id uuid primary key default extensions.gen_random_uuid(),
  cat_id uuid not null unique references public.cats(id) on delete cascade,
  status public.plan_status not null default 'draft',
  timezone text not null,
  schedule_type text not null,
  schedule_config jsonb not null default '{}'::jsonb,
  grace_period_minutes integer not null,
  next_check_in_at timestamptz,
  workflow_id text unique,
  armed_at timestamptz,
  paused_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint continuity_plans_grace_positive check (grace_period_minutes > 0)
);

create table if not exists public.check_in_cycles (
  id uuid primary key default extensions.gen_random_uuid(),
  plan_id uuid not null references public.continuity_plans(id) on delete cascade,
  sequence_number integer not null,
  scheduled_at timestamptz not null,
  due_at timestamptz not null,
  grace_ends_at timestamptz not null,
  state public.check_in_cycle_state not null default 'scheduled',
  completed_at timestamptz,
  completed_by_user_id uuid references auth.users(id) on delete set null,
  completion_source public.completion_source,
  created_at timestamptz not null default now(),
  constraint check_in_cycles_sequence_positive check (sequence_number > 0),
  constraint check_in_cycles_time_order check (scheduled_at <= due_at and due_at <= grace_ends_at),
  unique (plan_id, sequence_number)
);

create unique index if not exists check_in_cycles_one_active_cycle
  on public.check_in_cycles (plan_id)
  where state in ('scheduled', 'due', 'grace');

create table if not exists public.incidents (
  id uuid primary key default extensions.gen_random_uuid(),
  cat_id uuid not null references public.cats(id) on delete cascade,
  plan_id uuid not null references public.continuity_plans(id) on delete cascade,
  cycle_id uuid references public.check_in_cycles(id) on delete set null,
  activation_type public.incident_activation_type not null,
  state public.incident_state not null default 'active',
  activated_at timestamptz not null default now(),
  claimed_by_membership_id uuid references public.cat_memberships(id) on delete set null,
  claimed_at timestamptz,
  resolved_by_user_id uuid references auth.users(id) on delete set null,
  resolved_at timestamptz,
  resolution_note text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create unique index if not exists incidents_one_per_cycle
  on public.incidents (cycle_id)
  where cycle_id is not null;

create table if not exists public.incident_assignments (
  id uuid primary key default extensions.gen_random_uuid(),
  incident_id uuid not null references public.incidents(id) on delete cascade,
  membership_id uuid not null references public.cat_memberships(id) on delete cascade,
  step_number integer not null,
  response_deadline_at timestamptz not null,
  state public.incident_assignment_state not null default 'pending',
  notified_at timestamptz,
  responded_at timestamptz,
  notification_delivery_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint incident_assignments_step_positive check (step_number > 0),
  unique (incident_id, step_number),
  unique (incident_id, membership_id)
);

create table if not exists public.access_grants (
  id uuid primary key default extensions.gen_random_uuid(),
  incident_id uuid not null references public.incidents(id) on delete cascade,
  membership_id uuid not null references public.cat_memberships(id) on delete cascade,
  scopes public.data_scope[] not null,
  issued_at timestamptz not null default now(),
  expires_at timestamptz not null,
  revoked_at timestamptz,
  revocation_reason text,
  issued_by public.grant_issuer not null,
  created_at timestamptz not null default now(),
  constraint access_grants_expires_after_issue check (expires_at > issued_at)
);

create table if not exists public.notification_deliveries (
  id uuid primary key default extensions.gen_random_uuid(),
  recipient_user_id uuid references auth.users(id) on delete set null,
  channel public.notification_channel not null,
  notification_type text not null,
  provider text not null,
  provider_message_id text,
  recipient_address_hash bytea,
  provider_accepted_at timestamptz,
  delivery_status public.delivery_status not null default 'request_created',
  last_error_code text,
  incident_id uuid references public.incidents(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.incident_assignments
  add constraint incident_assignments_notification_delivery_fk
  foreign key (notification_delivery_id)
  references public.notification_deliveries(id)
  on delete set null;

create table if not exists public.audit_events (
  id uuid primary key default extensions.gen_random_uuid(),
  occurred_at timestamptz not null default now(),
  actor_type public.audit_actor_type not null,
  actor_id uuid,
  cat_id uuid references public.cats(id) on delete set null,
  incident_id uuid references public.incidents(id) on delete set null,
  event_type text not null,
  target_type text not null,
  target_id uuid,
  outcome public.audit_outcome not null,
  request_id text,
  metadata jsonb not null default '{}'::jsonb,
  previous_event_hash bytea,
  event_hash bytea
);

create index if not exists cats_primary_caregiver_idx on public.cats (primary_caregiver_user_id);
create index if not exists cat_memberships_user_idx on public.cat_memberships (user_id);
create index if not exists cat_memberships_cat_idx on public.cat_memberships (cat_id);
create index if not exists invitations_cat_status_idx on public.invitations (cat_id, status);
create index if not exists incidents_cat_state_idx on public.incidents (cat_id, state);
create index if not exists access_grants_membership_idx on public.access_grants (membership_id, revoked_at, expires_at);
create index if not exists audit_events_cat_time_idx on public.audit_events (cat_id, occurred_at desc);

create trigger profiles_set_updated_at
  before update on public.profiles
  for each row execute function public.set_updated_at();

create trigger cats_set_updated_at
  before update on public.cats
  for each row execute function public.set_updated_at();

create trigger cat_memberships_set_updated_at
  before update on public.cat_memberships
  for each row execute function public.set_updated_at();

create trigger invitations_set_updated_at
  before update on public.invitations
  for each row execute function public.set_updated_at();

create trigger capsules_set_updated_at
  before update on public.capsules
  for each row execute function public.set_updated_at();

create trigger capsule_sections_set_updated_at
  before update on public.capsule_sections
  for each row execute function public.set_updated_at();

create trigger continuity_plans_set_updated_at
  before update on public.continuity_plans
  for each row execute function public.set_updated_at();

create trigger incidents_set_updated_at
  before update on public.incidents
  for each row execute function public.set_updated_at();

create trigger incident_assignments_set_updated_at
  before update on public.incident_assignments
  for each row execute function public.set_updated_at();

create trigger notification_deliveries_set_updated_at
  before update on public.notification_deliveries
  for each row execute function public.set_updated_at();

create or replace function private.is_active_member(target_cat_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public, auth
as $$
  select exists (
    select 1
    from public.cat_memberships m
    where m.cat_id = target_cat_id
      and m.user_id = (select auth.uid())
      and (select auth.uid()) is not null
      and m.status = 'active'
  );
$$;

create or replace function private.is_primary_caregiver(target_cat_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public, auth
as $$
  select exists (
    select 1
    from public.cats c
    where c.id = target_cat_id
      and c.primary_caregiver_user_id = (select auth.uid())
      and (select auth.uid()) is not null
      and c.status = 'active'
  );
$$;

create or replace function private.has_incident_scope(target_incident_id uuid, requested_scope public.data_scope)
returns boolean
language sql
stable
security definer
set search_path = public, auth
as $$
  select exists (
    select 1
    from public.access_grants g
    join public.cat_memberships m on m.id = g.membership_id
    join public.incidents i on i.id = g.incident_id
    where g.incident_id = target_incident_id
      and m.user_id = (select auth.uid())
      and (select auth.uid()) is not null
      and m.status = 'active'
      and i.state in ('active', 'awaiting_response', 'claimed')
      and g.revoked_at is null
      and g.expires_at > now()
      and requested_scope = any(g.scopes)
  );
$$;

revoke all on function private.is_active_member(uuid) from public;
revoke all on function private.is_primary_caregiver(uuid) from public;
revoke all on function private.has_incident_scope(uuid, public.data_scope) from public;
grant execute on function private.is_active_member(uuid) to authenticated;
grant execute on function private.is_primary_caregiver(uuid) to authenticated;
grant execute on function private.has_incident_scope(uuid, public.data_scope) to authenticated;

alter table public.profiles enable row level security;
alter table public.cats enable row level security;
alter table public.cat_memberships enable row level security;
alter table public.membership_roles enable row level security;
alter table public.membership_permissions enable row level security;
alter table public.membership_scopes enable row level security;
alter table public.invitations enable row level security;
alter table public.capsules enable row level security;
alter table public.capsule_sections enable row level security;
alter table public.continuity_plans enable row level security;
alter table public.check_in_cycles enable row level security;
alter table public.incidents enable row level security;
alter table public.incident_assignments enable row level security;
alter table public.access_grants enable row level security;
alter table public.notification_deliveries enable row level security;
alter table public.audit_events enable row level security;

create policy "Users can read their own profile"
  on public.profiles for select
  to authenticated
  using ((select auth.uid()) = id);

create policy "Users can create their own profile"
  on public.profiles for insert
  to authenticated
  with check ((select auth.uid()) = id);

create policy "Users can update their own profile"
  on public.profiles for update
  to authenticated
  using ((select auth.uid()) = id)
  with check ((select auth.uid()) = id);

create policy "Primary caregivers can create their cats"
  on public.cats for insert
  to authenticated
  with check ((select auth.uid()) = primary_caregiver_user_id);

create policy "Active members can read their cats"
  on public.cats for select
  to authenticated
  using (private.is_active_member(id));

create policy "Primary caregivers can update their cats"
  on public.cats for update
  to authenticated
  using (private.is_primary_caregiver(id))
  with check (private.is_primary_caregiver(id));

create policy "Active members can read memberships for their cats"
  on public.cat_memberships for select
  to authenticated
  using (private.is_active_member(cat_id));

create policy "Active members can read roles for their cats"
  on public.membership_roles for select
  to authenticated
  using (
    exists (
      select 1
      from public.cat_memberships m
      where m.id = membership_roles.membership_id
        and private.is_active_member(m.cat_id)
    )
  );

create policy "Active members can read permissions for their cats"
  on public.membership_permissions for select
  to authenticated
  using (
    exists (
      select 1
      from public.cat_memberships m
      where m.id = membership_permissions.membership_id
        and private.is_active_member(m.cat_id)
    )
  );

create policy "Active members can read scopes for their cats"
  on public.membership_scopes for select
  to authenticated
  using (
    exists (
      select 1
      from public.cat_memberships m
      where m.id = membership_scopes.membership_id
        and private.is_active_member(m.cat_id)
    )
  );

create policy "Primary caregivers can read invitations for their cats"
  on public.invitations for select
  to authenticated
  using (private.is_primary_caregiver(cat_id));

create policy "Primary caregivers can read capsules"
  on public.capsules for select
  to authenticated
  using (private.is_primary_caregiver(cat_id));

create policy "Primary caregivers can update capsules"
  on public.capsules for update
  to authenticated
  using (private.is_primary_caregiver(cat_id))
  with check (private.is_primary_caregiver(cat_id));

create policy "Primary caregivers can read capsule sections"
  on public.capsule_sections for select
  to authenticated
  using (
    exists (
      select 1
      from public.capsules c
      where c.id = capsule_sections.capsule_id
        and private.is_primary_caregiver(c.cat_id)
    )
  );

create policy "Incident grants can read capsule sections"
  on public.capsule_sections for select
  to authenticated
  using (
    exists (
      select 1
      from public.capsules c
      join public.incidents i on i.cat_id = c.cat_id
      where c.id = capsule_sections.capsule_id
        and private.has_incident_scope(i.id, capsule_sections.scope)
    )
  );

create policy "Primary caregivers can read plans"
  on public.continuity_plans for select
  to authenticated
  using (private.is_primary_caregiver(cat_id));

create policy "Primary caregivers can read check-in cycles"
  on public.check_in_cycles for select
  to authenticated
  using (
    exists (
      select 1
      from public.continuity_plans p
      where p.id = check_in_cycles.plan_id
        and private.is_primary_caregiver(p.cat_id)
    )
  );

create policy "Relevant members can read incidents"
  on public.incidents for select
  to authenticated
  using (
    private.is_primary_caregiver(cat_id)
    or exists (
      select 1
      from public.access_grants g
      join public.cat_memberships m on m.id = g.membership_id
      where g.incident_id = incidents.id
        and m.user_id = (select auth.uid())
        and m.status = 'active'
        and g.revoked_at is null
        and g.expires_at > now()
    )
  );

create policy "Assigned members can read their assignments"
  on public.incident_assignments for select
  to authenticated
  using (
    exists (
      select 1
      from public.cat_memberships m
      where m.id = incident_assignments.membership_id
        and m.user_id = (select auth.uid())
        and m.status = 'active'
    )
  );

create policy "Members can read their active grants"
  on public.access_grants for select
  to authenticated
  using (
    exists (
      select 1
      from public.cat_memberships m
      where m.id = access_grants.membership_id
        and m.user_id = (select auth.uid())
        and m.status = 'active'
    )
  );

create policy "Users can read their notification delivery records"
  on public.notification_deliveries for select
  to authenticated
  using (recipient_user_id = (select auth.uid()));

create policy "Primary caregivers can read audit events"
  on public.audit_events for select
  to authenticated
  using (cat_id is not null and private.is_primary_caregiver(cat_id));

commit;
