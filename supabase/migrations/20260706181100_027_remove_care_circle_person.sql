begin;

create or replace function public.remove_care_circle_person(p_invitation_id uuid)
returns table (
  invitation_id uuid,
  cat_id uuid,
  removed_status public.invitation_status
)
language plpgsql
security definer
set search_path = public, auth
as $$
#variable_conflict use_column
declare
  actor_id uuid;
  invitation_row public.invitations;
  membership_row public.cat_memberships;
begin
  actor_id := auth.uid();

  if actor_id is null then
    raise exception 'authenticated user required';
  end if;

  select *
  into invitation_row
  from public.invitations i
  where i.id = p_invitation_id
  for update;

  if invitation_row.id is null then
    raise exception 'care circle person not found';
  end if;

  if not private.is_primary_caregiver(invitation_row.cat_id) then
    raise exception 'primary caregiver access required';
  end if;

  if invitation_row.status = 'revoked' then
    return query
    select invitation_row.id, invitation_row.cat_id, invitation_row.status;
    return;
  end if;

  if invitation_row.status not in ('pending', 'accepted') then
    raise exception 'only pending or accepted Care Circle people can be removed';
  end if;

  if invitation_row.status = 'accepted' and invitation_row.accepted_by_user_id is not null then
    select *
    into membership_row
    from public.cat_memberships m
    where m.cat_id = invitation_row.cat_id
      and m.user_id = invitation_row.accepted_by_user_id
      and m.status in ('active', 'suspended')
    for update;

    if membership_row.id is not null then
      if exists (
        select 1
        from public.incident_assignments a
        join public.incidents i on i.id = a.incident_id
        where a.membership_id = membership_row.id
          and a.state in ('pending', 'notified', 'accepted')
          and i.state in ('active', 'awaiting_response', 'claimed')
      ) then
        raise exception 'resolve active handoff before removing this person';
      end if;

      update public.membership_roles
      set revoked_at = now()
      where membership_id = membership_row.id
        and revoked_at is null;

      update public.membership_permissions
      set revoked_at = now()
      where membership_id = membership_row.id
        and revoked_at is null;

      update public.membership_scopes
      set revoked_at = now()
      where membership_id = membership_row.id
        and revoked_at is null;

      update public.access_grants
      set
        revoked_at = now(),
        revocation_reason = coalesce(revocation_reason, 'Care Circle person removed by primary caregiver')
      where membership_id = membership_row.id
        and revoked_at is null;

      update public.cat_memberships
      set
        status = 'revoked',
        revoked_at = now(),
        suspended_at = null,
        updated_at = now()
      where id = membership_row.id;
    end if;
  end if;

  update public.invitations
  set
    status = 'revoked',
    updated_at = now()
  where id = invitation_row.id
  returning * into invitation_row;

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
    actor_id,
    invitation_row.cat_id,
    'care_circle.person_removed',
    'invitation',
    invitation_row.id,
    'success',
    jsonb_build_object(
      'relationship_label',
      invitation_row.relationship_label,
      'previous_status',
      case when membership_row.id is null then null else 'accepted' end,
      'membership_id',
      membership_row.id
    )
  );

  return query
  select invitation_row.id, invitation_row.cat_id, invitation_row.status;
end;
$$;

revoke all on function public.remove_care_circle_person(uuid) from public;
grant execute on function public.remove_care_circle_person(uuid) to authenticated;

notify pgrst, 'reload schema';

commit;
