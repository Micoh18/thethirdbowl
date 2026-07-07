begin;

grant usage on schema public to service_role;

grant select on table public.profiles to service_role;
grant select on table public.cats to service_role;
grant select on table public.incidents to service_role;
grant select on table public.incident_assignments to service_role;
grant update on table public.incident_assignments to service_role;
grant select, update on table public.notification_deliveries to service_role;

commit;
