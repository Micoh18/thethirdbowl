begin;

create or replace function private.sync_profile_from_auth_user()
returns trigger
language plpgsql
security definer
set search_path = public, auth
as $$
begin
  insert into public.profiles (
    id,
    email_normalized,
    email_verified_at,
    display_name,
    timezone,
    status
  )
  values (
    new.id,
    lower(new.email),
    new.email_confirmed_at,
    coalesce(new.raw_user_meta_data ->> 'display_name', ''),
    'UTC',
    'active'
  )
  on conflict (id) do update
  set
    email_normalized = excluded.email_normalized,
    email_verified_at = excluded.email_verified_at,
    updated_at = now();

  return new;
end;
$$;

revoke all on function private.sync_profile_from_auth_user() from public;

drop trigger if exists users_sync_profile_after_insert on auth.users;
drop trigger if exists users_sync_profile_after_update on auth.users;

create trigger users_sync_profile_after_insert
  after insert on auth.users
  for each row execute function private.sync_profile_from_auth_user();

create trigger users_sync_profile_after_update
  after update of email, email_confirmed_at, raw_user_meta_data on auth.users
  for each row execute function private.sync_profile_from_auth_user();

insert into public.profiles (
  id,
  email_normalized,
  email_verified_at,
  display_name,
  timezone,
  status
)
select
  u.id,
  lower(u.email),
  u.email_confirmed_at,
  coalesce(u.raw_user_meta_data ->> 'display_name', ''),
  'UTC',
  'active'
from auth.users u
on conflict (id) do update
set
  email_normalized = excluded.email_normalized,
  email_verified_at = excluded.email_verified_at,
  updated_at = now();

commit;
