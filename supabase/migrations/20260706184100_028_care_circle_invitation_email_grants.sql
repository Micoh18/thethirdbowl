begin;

grant usage on schema public to service_role;
grant select on table public.invitations to service_role;

commit;
