begin;

alter table public.capsule_sections
  rename column content_ciphertext to content_json;

alter table public.capsule_sections
  alter column content_json type jsonb
  using convert_from(content_json, 'UTF8')::jsonb;

alter table public.capsule_sections
  alter column content_json set default '{}'::jsonb;

create policy "Primary caregivers can insert capsule sections"
  on public.capsule_sections for insert
  to authenticated
  with check (
    exists (
      select 1
      from public.capsules c
      where c.id = capsule_sections.capsule_id
        and private.is_primary_caregiver(c.cat_id)
    )
  );

create policy "Primary caregivers can update capsule sections"
  on public.capsule_sections for update
  to authenticated
  using (
    exists (
      select 1
      from public.capsules c
      where c.id = capsule_sections.capsule_id
        and private.is_primary_caregiver(c.cat_id)
    )
  )
  with check (
    exists (
      select 1
      from public.capsules c
      where c.id = capsule_sections.capsule_id
        and private.is_primary_caregiver(c.cat_id)
    )
  );

grant insert, update on public.capsule_sections to authenticated;

commit;
