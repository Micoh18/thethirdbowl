begin;

create or replace function public.list_my_invitation_records()
returns table (
  id uuid,
  cat_id uuid,
  cat_name text,
  relationship_label text,
  status public.invitation_status,
  expires_at timestamptz,
  created_at timestamptz
)
language sql
security definer
set search_path = public, auth, extensions
as $$
  select
    i.id,
    i.cat_id,
    c.name as cat_name,
    i.relationship_label,
    i.status,
    i.expires_at,
    i.created_at
  from public.invitations i
  join public.cats c on c.id = i.cat_id
  join public.profiles p on p.id = auth.uid()
  where i.status = 'pending'
    and i.expires_at > now()
    and p.status = 'active'
    and p.email_verified_at is not null
    and i.invited_email_hash = extensions.digest(lower(auth.jwt() ->> 'email'), 'sha256')
  order by i.created_at desc;
$$;

create or replace function public.accept_invitation_record(p_invitation_id uuid)
returns table (
  membership_id uuid,
  cat_id uuid,
  cat_name text,
  relationship_label text
)
language plpgsql
security definer
set search_path = public, auth, extensions
as $$
#variable_conflict use_column
declare
  actor_id uuid;
  actor_email text;
  matched_invitation public.invitations;
  created_membership_id uuid;
  role_value public.membership_role;
  permission_value public.action_permission;
  scope_value public.data_scope;
begin
  actor_id := auth.uid();
  actor_email := lower(auth.jwt() ->> 'email');

  if actor_id is null then
    raise exception 'authenticated user required';
  end if;

  if actor_email is null or length(actor_email) = 0 then
    raise exception 'authenticated email required';
  end if;

  if not exists (
    select 1
    from public.profiles p
    where p.id = actor_id
      and p.status = 'active'
      and p.email_verified_at is not null
  ) then
    raise exception 'verified active profile required';
  end if;

  select *
  into matched_invitation
  from public.invitations i
  where i.id = p_invitation_id
    and i.status = 'pending'
    and i.expires_at > now()
    and i.invited_email_hash = extensions.digest(actor_email, 'sha256')
  for update;

  if matched_invitation.id is null then
    raise exception 'invitation not available';
  end if;

  insert into public.cat_memberships (
    cat_id,
    user_id,
    relationship_label,
    status,
    accepted_at
  )
  values (
    matched_invitation.cat_id,
    actor_id,
    matched_invitation.relationship_label,
    'active',
    now()
  )
  on conflict (cat_id, user_id) where status in ('active', 'suspended')
  do update
  set
    relationship_label = excluded.relationship_label,
    status = 'active',
    accepted_at = coalesce(public.cat_memberships.accepted_at, now()),
    revoked_at = null,
    suspended_at = null,
    updated_at = now()
  returning id into created_membership_id;

  foreach role_value in array matched_invitation.proposed_roles loop
    insert into public.membership_roles (
      membership_id,
      role,
      assigned_by_user_id
    )
    values (
      created_membership_id,
      role_value,
      matched_invitation.created_by_user_id
    )
    on conflict do nothing;
  end loop;

  foreach permission_value in array matched_invitation.proposed_permissions loop
    insert into public.membership_permissions (
      membership_id,
      permission,
      activation_context,
      assigned_by_user_id
    )
    values (
      created_membership_id,
      permission_value,
      'ACTIVE_INCIDENT',
      matched_invitation.created_by_user_id
    )
    on conflict do nothing;
  end loop;

  foreach scope_value in array matched_invitation.proposed_scopes loop
    insert into public.membership_scopes (
      membership_id,
      scope,
      activation_context,
      assigned_by_user_id
    )
    values (
      created_membership_id,
      scope_value,
      'ACTIVE_INCIDENT',
      matched_invitation.created_by_user_id
    )
    on conflict do nothing;
  end loop;

  update public.invitations
  set
    status = 'accepted',
    accepted_by_user_id = actor_id,
    accepted_at = now(),
    updated_at = now()
  where id = matched_invitation.id;

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
    matched_invitation.cat_id,
    'invitation.accepted',
    'invitation',
    matched_invitation.id,
    'success',
    jsonb_build_object(
      'membership_id',
      created_membership_id,
      'relationship_label',
      matched_invitation.relationship_label
    )
  );

  return query
  select
    created_membership_id,
    c.id,
    c.name,
    matched_invitation.relationship_label
  from public.cats c
  where c.id = matched_invitation.cat_id;
end;
$$;

revoke all on function public.list_my_invitation_records() from public;
revoke all on function public.accept_invitation_record(uuid) from public;
grant execute on function public.list_my_invitation_records() to authenticated;
grant execute on function public.accept_invitation_record(uuid) to authenticated;

commit;
