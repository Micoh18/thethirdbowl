begin;

create extension if not exists pgcrypto with schema extensions;

alter table public.capsule_sections
  add column if not exists content_ciphertext bytea,
  add column if not exists content_encryption_key_id text,
  add column if not exists content_encrypted_at timestamptz;

comment on column public.capsule_sections.content_json is
  'Plain JSON for CARE_CORE. For HOME_ACCESS and MEDICAL this stores only an encrypted marker; plaintext is held in content_ciphertext.';

comment on column public.capsule_sections.content_ciphertext is
  'pgcrypto ciphertext for sensitive Capsule sections. Decryption is only exposed through authorization-checked RPCs.';

create or replace function private.capsule_encryption_key()
returns text
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  encryption_key text;
begin
  encryption_key := nullif(current_setting('app.capsule_encryption_key', true), '');

  if encryption_key is null then
    raise exception 'capsule encryption key is not configured';
  end if;

  if length(encryption_key) < 32 then
    raise exception 'capsule encryption key must be at least 32 characters';
  end if;

  return encryption_key;
end;
$$;

create or replace function private.is_sensitive_capsule_scope(p_scope public.data_scope)
returns boolean
language sql
immutable
security definer
set search_path = public
as $$
  select p_scope in ('HOME_ACCESS'::public.data_scope, 'MEDICAL'::public.data_scope);
$$;

create or replace function private.capsule_section_plaintext(
  p_scope public.data_scope,
  p_content_json jsonb,
  p_content_ciphertext bytea
)
returns jsonb
language plpgsql
stable
security definer
set search_path = public, extensions
as $$
begin
  if private.is_sensitive_capsule_scope(p_scope) and p_content_ciphertext is not null then
    return extensions.pgp_sym_decrypt(
      p_content_ciphertext,
      private.capsule_encryption_key()
    )::jsonb;
  end if;

  return coalesce(p_content_json, '{}'::jsonb);
end;
$$;

create or replace function private.encrypt_sensitive_capsule_section()
returns trigger
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  key_id text := 'db-guc-v1';
  plaintext jsonb;
begin
  if private.is_sensitive_capsule_scope(new.scope) then
    if new.content_ciphertext is null
      or coalesce(new.content_json ->> '_encrypted', 'false') <> 'true'
    then
      plaintext := coalesce(new.content_json, '{}'::jsonb);
      new.content_ciphertext := extensions.pgp_sym_encrypt(
        plaintext::text,
        private.capsule_encryption_key(),
        'cipher-algo=aes256, compress-algo=1'
      );
      new.content_json := jsonb_build_object(
        '_encrypted',
        true,
        'scope',
        new.scope::text,
        'key_id',
        key_id
      );
      new.content_encryption_key_id := key_id;
      new.content_encrypted_at := now();
    end if;
  else
    new.content_ciphertext := null;
    new.content_encryption_key_id := null;
    new.content_encrypted_at := null;
  end if;

  return new;
end;
$$;

revoke all on function private.capsule_encryption_key() from public;
revoke all on function private.is_sensitive_capsule_scope(public.data_scope) from public;
revoke all on function private.capsule_section_plaintext(public.data_scope, jsonb, bytea) from public;
revoke all on function private.encrypt_sensitive_capsule_section() from public;

drop trigger if exists capsule_sections_encrypt_sensitive_before_write on public.capsule_sections;

create trigger capsule_sections_encrypt_sensitive_before_write
  before insert or update of scope, content_json, content_ciphertext
  on public.capsule_sections
  for each row execute function private.encrypt_sensitive_capsule_section();

create or replace function public.list_cat_capsule_sections(p_target_cat_id uuid)
returns table (
  id uuid,
  capsule_id uuid,
  scope public.data_scope,
  schema_version integer,
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

  if not private.is_primary_caregiver(p_target_cat_id) then
    raise exception 'primary caregiver access required';
  end if;

  return query
  select
    s.id,
    s.capsule_id,
    s.scope,
    s.schema_version,
    private.capsule_section_plaintext(s.scope, s.content_json, s.content_ciphertext) as content_json,
    s.updated_at
  from public.capsules c
  join public.capsule_sections s on s.capsule_id = c.id
  where c.cat_id = p_target_cat_id
  order by
    case s.scope
      when 'CARE_CORE' then 1
      when 'HOME_ACCESS' then 2
      when 'MEDICAL' then 3
      else 4
    end;
end;
$$;

create or replace function public.upsert_cat_capsule_sections(
  p_target_cat_id uuid,
  p_care_core jsonb default '{}'::jsonb,
  p_home_access jsonb default '{}'::jsonb,
  p_medical jsonb default '{}'::jsonb
)
returns table (
  id uuid,
  capsule_id uuid,
  scope public.data_scope,
  schema_version integer,
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
  target_capsule_id uuid;
  section_input record;
  section_row public.capsule_sections;
begin
  actor_id := auth.uid();

  if actor_id is null then
    raise exception 'authenticated user required';
  end if;

  if not private.is_primary_caregiver(p_target_cat_id) then
    raise exception 'primary caregiver access required';
  end if;

  select c.id
  into target_capsule_id
  from public.capsules c
  where c.cat_id = p_target_cat_id;

  if target_capsule_id is null then
    raise exception 'capsule not found';
  end if;

  for section_input in
    select *
    from (
      values
        ('CARE_CORE'::public.data_scope, coalesce(p_care_core, '{}'::jsonb)),
        ('HOME_ACCESS'::public.data_scope, coalesce(p_home_access, '{}'::jsonb)),
        ('MEDICAL'::public.data_scope, coalesce(p_medical, '{}'::jsonb))
    ) as input(scope, content_json)
  loop
    insert into public.capsule_sections (
      capsule_id,
      scope,
      schema_version,
      content_json,
      updated_by_user_id
    )
    values (
      target_capsule_id,
      section_input.scope,
      1,
      section_input.content_json,
      actor_id
    )
    on conflict (capsule_id, scope) do update
    set
      schema_version = excluded.schema_version,
      content_json = excluded.content_json,
      content_ciphertext = excluded.content_ciphertext,
      content_encryption_key_id = excluded.content_encryption_key_id,
      content_encrypted_at = excluded.content_encrypted_at,
      updated_by_user_id = actor_id,
      updated_at = now()
    returning * into section_row;
  end loop;

  return query
  select *
  from public.list_cat_capsule_sections(p_target_cat_id);
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
    private.capsule_section_plaintext(s.scope, s.content_json, s.content_ciphertext) as content_json,
    s.updated_at
  from public.incidents i
  join public.cats c on c.id = i.cat_id
  join public.capsules cap on cap.cat_id = c.id
  join public.capsule_sections s on s.capsule_id = cap.id
  where i.id = p_incident_id
    and s.scope = 'CARE_CORE';
end;
$$;

create or replace function public.list_my_incident_capsule_sections(p_incident_id uuid)
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

  if not exists (
    select 1
    from public.access_grants g
    join public.cat_memberships m on m.id = g.membership_id
    join public.incidents i on i.id = g.incident_id
    where g.incident_id = p_incident_id
      and m.user_id = actor_id
      and m.status = 'active'
      and i.state in ('active', 'awaiting_response', 'claimed')
      and g.revoked_at is null
      and g.expires_at > now()
  ) then
    raise exception 'active incident grant required';
  end if;

  return query
  select
    i.id,
    c.id,
    c.name,
    s.scope,
    private.capsule_section_plaintext(s.scope, s.content_json, s.content_ciphertext) as content_json,
    s.updated_at
  from public.incidents i
  join public.cats c on c.id = i.cat_id
  join public.capsules cap on cap.cat_id = c.id
  join public.capsule_sections s on s.capsule_id = cap.id
  where i.id = p_incident_id
    and exists (
      select 1
      from public.access_grants g
      join public.cat_memberships m on m.id = g.membership_id
      where g.incident_id = i.id
        and m.user_id = actor_id
        and m.status = 'active'
        and g.revoked_at is null
        and g.expires_at > now()
        and (
          s.scope = any(g.scopes)
          or 'FULL_CAPSULE'::public.data_scope = any(g.scopes)
        )
    )
  order by
    case s.scope
      when 'CARE_CORE' then 1
      when 'HOME_ACCESS' then 2
      when 'MEDICAL' then 3
      else 4
    end;
end;
$$;

create or replace function public.backfill_capsule_sensitive_sections()
returns table (
  sections_encrypted integer
)
language plpgsql
security definer
set search_path = public
as $$
declare
  encrypted_count integer;
begin
  update public.capsule_sections
  set content_json = content_json
  where scope in ('HOME_ACCESS'::public.data_scope, 'MEDICAL'::public.data_scope)
    and content_ciphertext is null;

  get diagnostics encrypted_count = row_count;

  return query
  select encrypted_count;
end;
$$;

revoke select, insert, update on public.capsule_sections from authenticated;

revoke all on function public.list_cat_capsule_sections(uuid) from public;
revoke all on function public.upsert_cat_capsule_sections(uuid, jsonb, jsonb, jsonb) from public;
revoke all on function public.list_my_incident_care_core(uuid) from public;
revoke all on function public.list_my_incident_capsule_sections(uuid) from public;
revoke all on function public.backfill_capsule_sensitive_sections() from public;
grant execute on function public.list_cat_capsule_sections(uuid) to authenticated;
grant execute on function public.upsert_cat_capsule_sections(uuid, jsonb, jsonb, jsonb) to authenticated;
grant execute on function public.list_my_incident_care_core(uuid) to authenticated;
grant execute on function public.list_my_incident_capsule_sections(uuid) to authenticated;
grant execute on function public.backfill_capsule_sensitive_sections() to service_role;

do $$
declare
  encrypted_count integer;
begin
  if nullif(current_setting('app.capsule_encryption_key', true), '') is not null then
    select b.sections_encrypted
    into encrypted_count
    from public.backfill_capsule_sensitive_sections() b;
    raise notice 'Encrypted % existing sensitive Capsule section(s).', encrypted_count;
  else
    raise notice 'Skipping existing sensitive Capsule backfill because app.capsule_encryption_key is not configured.';
  end if;
end $$;

notify pgrst, 'reload schema';

commit;
