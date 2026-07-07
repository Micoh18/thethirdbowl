begin;

alter table public.invitations
  add column if not exists invited_email_masked text;

drop function if exists public.create_invitation_record(uuid, text, text, text, text[]);

create function public.create_invitation_record(
  p_target_cat_id uuid,
  p_invited_email text,
  p_relationship_label text,
  p_proposed_role text default 'KEYHOLDER',
  p_proposed_scopes text[] default array['CARE_CORE']
)
returns table (
  id uuid,
  cat_id uuid,
  invited_email_masked text,
  relationship_label text,
  status public.invitation_status,
  proposed_role text,
  proposed_scopes text[],
  expires_at timestamptz,
  created_at timestamptz
)
language plpgsql
security definer
set search_path = public, auth, extensions
as $$
#variable_conflict use_column
declare
  actor_id uuid;
  normalized_email text;
  email_local text;
  email_domain text;
  masked_email text;
  raw_token text;
  created_invitation public.invitations;
  role_value public.membership_role;
  scope_values public.data_scope[];
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

  email_local := split_part(normalized_email, '@', 1);
  email_domain := split_part(normalized_email, '@', 2);
  masked_email := left(email_local, case when length(email_local) > 2 then 2 else 1 end) || '***@' || email_domain;

  role_value := p_proposed_role::public.membership_role;

  select array_agg(distinct scope_name::public.data_scope order by scope_name::public.data_scope)
  into scope_values
  from unnest(coalesce(p_proposed_scopes, array['CARE_CORE'])) as scope_name;

  if scope_values is null or cardinality(scope_values) = 0 then
    raise exception 'at least one incident data scope is required';
  end if;

  if 'FULL_CAPSULE'::public.data_scope = any(scope_values) then
    raise exception 'FULL_CAPSULE must not be granted from the mobile invitation flow';
  end if;

  raw_token := encode(extensions.gen_random_bytes(32), 'hex');

  insert into public.invitations (
    cat_id,
    invited_email_hash,
    invited_email_masked,
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
    masked_email,
    trim(p_relationship_label),
    array[role_value]::public.membership_role[],
    array['INCIDENT_RESPOND']::public.action_permission[],
    scope_values,
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
    created_invitation.invited_email_masked,
    created_invitation.relationship_label,
    created_invitation.status,
    coalesce(created_invitation.proposed_roles[1]::text, '') as proposed_role,
    coalesce(
      array(
        select scope_value::text
        from unnest(created_invitation.proposed_scopes) as scope_value
        order by scope_value::text
      ),
      array[]::text[]
    ) as proposed_scopes,
    created_invitation.expires_at,
    created_invitation.created_at;
end;
$$;

drop function if exists public.list_invitation_records(uuid);

create function public.list_invitation_records(target_cat_id uuid)
returns table (
  id uuid,
  cat_id uuid,
  invited_email_masked text,
  relationship_label text,
  status public.invitation_status,
  proposed_role text,
  proposed_scopes text[],
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
    coalesce(i.invited_email_masked, '') as invited_email_masked,
    i.relationship_label,
    i.status,
    coalesce(i.proposed_roles[1]::text, '') as proposed_role,
    coalesce(
      array(
        select scope_value::text
        from unnest(i.proposed_scopes) as scope_value
        order by scope_value::text
      ),
      array[]::text[]
    ) as proposed_scopes,
    i.expires_at,
    i.created_at
  from public.invitations i
  where i.cat_id = target_cat_id
    and private.is_primary_caregiver(i.cat_id)
  order by i.created_at desc;
$$;

revoke all on function public.create_invitation_record(uuid, text, text, text, text[]) from public;
revoke all on function public.list_invitation_records(uuid) from public;
grant execute on function public.create_invitation_record(uuid, text, text, text, text[]) to authenticated;
grant execute on function public.list_invitation_records(uuid) to authenticated;

notify pgrst, 'reload schema';

commit;
