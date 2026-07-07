begin;

create or replace function private.capsule_encryption_key()
returns text
language plpgsql
stable
security definer
set search_path = public, vault
as $$
declare
  encryption_key text;
begin
  select nullif(ds.decrypted_secret, '')
  into encryption_key
  from vault.decrypted_secrets ds
  where ds.name = 'capsule_encryption_key'
  order by ds.updated_at desc nulls last, ds.created_at desc
  limit 1;

  if encryption_key is null then
    encryption_key := nullif(current_setting('app.capsule_encryption_key', true), '');
  end if;

  if encryption_key is null then
    raise exception 'capsule encryption key is not configured';
  end if;

  if length(encryption_key) < 32 then
    raise exception 'capsule encryption key must be at least 32 characters';
  end if;

  return encryption_key;
end;
$$;

revoke all on function private.capsule_encryption_key() from public;

notify pgrst, 'reload schema';

commit;
