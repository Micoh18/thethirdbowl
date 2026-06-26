begin;

create or replace function private.audit_capsule_section_write()
returns trigger
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  target_cat_id uuid;
  event_name text;
begin
  select c.cat_id
  into target_cat_id
  from public.capsules c
  where c.id = new.capsule_id;

  event_name := case
    when tg_op = 'INSERT' then 'capsule_section.created'
    else 'capsule_section.updated'
  end;

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
    auth.uid(),
    target_cat_id,
    event_name,
    'capsule_section',
    new.id,
    'success',
    jsonb_build_object(
      'scope',
      new.scope,
      'schema_version',
      new.schema_version
    )
  );

  update public.capsules
  set
    version = version + 1,
    updated_by_user_id = auth.uid(),
    updated_at = now()
  where id = new.capsule_id;

  return new;
end;
$$;

revoke all on function private.audit_capsule_section_write() from public;

drop trigger if exists capsule_sections_audit_after_insert on public.capsule_sections;
drop trigger if exists capsule_sections_audit_after_update on public.capsule_sections;

create trigger capsule_sections_audit_after_insert
  after insert on public.capsule_sections
  for each row execute function private.audit_capsule_section_write();

create trigger capsule_sections_audit_after_update
  after update on public.capsule_sections
  for each row execute function private.audit_capsule_section_write();

commit;
