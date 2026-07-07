begin;

create or replace function private.set_cat_owner_from_auth()
returns trigger
language plpgsql
security definer
set search_path = public, auth
as $$
begin
  if auth.uid() is null then
    raise exception 'authenticated user required';
  end if;

  new.primary_caregiver_user_id := auth.uid();
  return new;
end;
$$;

revoke all on function private.set_cat_owner_from_auth() from public;

drop trigger if exists cats_set_owner_before_insert on public.cats;

create trigger cats_set_owner_before_insert
  before insert on public.cats
  for each row execute function private.set_cat_owner_from_auth();

drop policy if exists "Authenticated users can create their own cats" on public.cats;

create policy "Authenticated users can create their own cats"
  on public.cats for insert
  to authenticated
  with check (
    primary_caregiver_user_id = (select auth.uid())
  );

commit;
