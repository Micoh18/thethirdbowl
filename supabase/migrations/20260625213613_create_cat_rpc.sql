begin;

create or replace function public.create_cat(cat_name text)
returns public.cats
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  actor_id uuid;
  created_cat public.cats;
begin
  actor_id := auth.uid();

  if actor_id is null then
    raise exception 'authenticated user required';
  end if;

  if length(trim(cat_name)) = 0 or length(trim(cat_name)) > 120 then
    raise exception 'cat name must be between 1 and 120 characters';
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

  insert into public.cats (
    primary_caregiver_user_id,
    name
  )
  values (
    actor_id,
    trim(cat_name)
  )
  returning * into created_cat;

  return created_cat;
end;
$$;

revoke all on function public.create_cat(text) from public;
grant execute on function public.create_cat(text) to authenticated;

commit;
