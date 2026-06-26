begin;

create or replace function public.list_cat_audit_events(target_cat_id uuid)
returns table (
  id uuid,
  occurred_at timestamptz,
  actor_type public.audit_actor_type,
  event_type text,
  target_type text,
  outcome public.audit_outcome
)
language sql
security definer
set search_path = public, auth
as $$
  select
    e.id,
    e.occurred_at,
    e.actor_type,
    e.event_type,
    e.target_type,
    e.outcome
  from public.audit_events e
  where e.cat_id = target_cat_id
    and private.is_primary_caregiver(target_cat_id)
  order by e.occurred_at desc
  limit 50;
$$;

revoke all on function public.list_cat_audit_events(uuid) from public;
grant execute on function public.list_cat_audit_events(uuid) to authenticated;

commit;
