begin;

alter table public.cats
  alter column primary_caregiver_user_id set default auth.uid();

drop policy if exists "Primary caregivers can create their cats" on public.cats;
drop policy if exists "Authenticated users can create their own cats" on public.cats;

create policy "Authenticated users can create their own cats"
  on public.cats for insert
  to authenticated
  with check (
    primary_caregiver_user_id = (select auth.uid())
    and exists (
      select 1
      from public.profiles p
      where p.id = (select auth.uid())
        and p.status = 'active'
        and p.email_verified_at is not null
    )
  );

commit;
