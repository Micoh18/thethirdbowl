begin;

create or replace function public.create_invitation_record(
  p_target_cat_id uuid,
  p_invited_email text,
  p_relationship_label text
)
returns table (
  id uuid,
  cat_id uuid,
  relationship_label text,
  status public.invitation_status,
  expires_at timestamptz,
  created_at timestamptz
)
language plpgsql
security definer
set search_path = public, auth, extensions
as $$
declare
  actor_id uuid;
  normalized_email text;
  raw_token text;
  created_invitation public.invitations;
begin
  actor_id := auth.uid();
  normalized_email := lower(trim(p_invited_email));

  if actor_id is null then
    raise exception 'authenticated user required';
  end if;

  if not private.is_primary_caregiver(p_target_cat_id) then
    raise exception 'primary caregiver access required';
  end if;

  if normalized_email !~ '^[^@\s]+@[^@\s]+\.[^@\s]+$' then
    raise exception 'valid invited email required';
  end if;

  if length(trim(p_relationship_label)) = 0 or length(trim(p_relationship_label)) > 80 then
    raise exception 'relationship label must be between 1 and 80 characters';
  end if;

  raw_token := encode(extensions.gen_random_bytes(32), 'hex');

  insert into public.invitations (
    cat_id,
    invited_email_hash,
    relationship_label,
    proposed_roles,
    proposed_permissions,
    proposed_scopes,
    token_hash,
    status,
    expires_at,
    created_by_user_id
  )
  values (
    p_target_cat_id,
    extensions.digest(normalized_email, 'sha256'),
    trim(p_relationship_label),
    array['KEYHOLDER']::public.membership_role[],
    array['INCIDENT_RESPOND']::public.action_permission[],
    array['CARE_CORE']::public.data_scope[],
    extensions.digest(raw_token, 'sha256'),
    'pending',
    now() + interval '7 days',
    actor_id
  )
  returning * into created_invitation;

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
    p_target_cat_id,
    'invitation.created',
    'invitation',
    created_invitation.id,
    'success',
    jsonb_build_object(
      'relationship_label',
      created_invitation.relationship_label,
      'proposed_roles',
      created_invitation.proposed_roles,
      'proposed_scopes',
      created_invitation.proposed_scopes
    )
  );

  return query
  select
    created_invitation.id,
    created_invitation.cat_id,
    created_invitation.relationship_label,
    created_invitation.status,
    created_invitation.expires_at,
    created_invitation.created_at;
end;
$$;

create or replace function public.list_invitation_records(target_cat_id uuid)
returns table (
  id uuid,
  cat_id uuid,
  relationship_label text,
  status public.invitation_status,
  expires_at timestamptz,
  created_at timestamptz
)
language sql
security definer
set search_path = public, auth
as $$
  select
    i.id,
    i.cat_id,
    i.relationship_label,
    i.status,
    i.expires_at,
    i.created_at
  from public.invitations i
  where i.cat_id = target_cat_id
    and private.is_primary_caregiver(i.cat_id)
  order by i.created_at desc;
$$;

revoke all on function public.create_invitation_record(uuid, text, text) from public;
revoke all on function public.list_invitation_records(uuid) from public;
grant execute on function public.create_invitation_record(uuid, text, text) to authenticated;
grant execute on function public.list_invitation_records(uuid) to authenticated;

commit;
