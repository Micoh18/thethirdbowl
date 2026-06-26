begin;

drop function if exists public.list_invitation_records(uuid);

create function public.list_invitation_records(target_cat_id uuid)
returns table (
  id uuid,
  cat_id uuid,
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

drop function if exists public.list_my_invitation_records();

create function public.list_my_invitation_records()
returns table (
  id uuid,
  cat_id uuid,
  cat_name text,
  relationship_label text,
  status public.invitation_status,
  proposed_role text,
  proposed_scopes text[],
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
  join public.cats c on c.id = i.cat_id
  join public.profiles p on p.id = auth.uid()
  where i.status = 'pending'
    and i.expires_at > now()
    and p.status = 'active'
    and p.email_verified_at is not null
    and i.invited_email_hash = extensions.digest(lower(auth.jwt() ->> 'email'), 'sha256')
  order by i.created_at desc;
$$;

revoke all on function public.list_invitation_records(uuid) from public;
revoke all on function public.list_my_invitation_records() from public;
grant execute on function public.list_invitation_records(uuid) to authenticated;
grant execute on function public.list_my_invitation_records() to authenticated;

commit;
