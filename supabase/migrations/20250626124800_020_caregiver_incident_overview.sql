begin;

create or replace function public.get_active_incident_for_cat(target_cat_id uuid)
returns table (
  incident_id uuid,
  cat_id uuid,
  cat_name text,
  incident_state public.incident_state,
  assignment_id uuid,
  assignment_state public.incident_assignment_state,
  assigned_relationship_label text,
  activated_at timestamptz,
  response_deadline_at timestamptz
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

  if not private.is_primary_caregiver(target_cat_id) then
    raise exception 'cat not available for this user';
  end if;

  return query
  select
    i.id,
    c.id,
    c.name,
    i.state,
    a.id,
    a.state,
    m.relationship_label,
    i.activated_at,
    a.response_deadline_at
  from public.incidents i
  join public.cats c on c.id = i.cat_id
  join public.incident_assignments a on a.incident_id = i.id
  join public.cat_memberships m on m.id = a.membership_id
  where i.cat_id = target_cat_id
    and i.state in ('active', 'awaiting_response', 'claimed')
  order by
    case a.state
      when 'accepted' then 0
      when 'notified' then 1
      when 'pending' then 2
      else 3
    end,
    a.step_number,
    i.activated_at desc
  limit 1;
end;
$$;

revoke all on function public.get_active_incident_for_cat(uuid) from public;
grant execute on function public.get_active_incident_for_cat(uuid) to authenticated;

commit;
