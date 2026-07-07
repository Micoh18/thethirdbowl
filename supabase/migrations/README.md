# Supabase Migrations

This directory is the committed backend source of truth for The Third Bowl.
Run files in timestamp order with the Supabase CLI or Supabase SQL tooling.

Codex does not apply these migrations to the live project in this workspace;
the project owner runs SQL manually.

## Clean Rebuild

For a local clean database:

```bash
cd TheThirdBowl
supabase db reset
supabase functions deploy process-due-check-ins
```

For a hosted project, apply the migrations in timestamp order, then deploy the
Edge Function and set its secrets:

- `SUPABASE_URL`
- `SUPABASE_SERVICE_ROLE_KEY`
- `CHECK_IN_PROCESSOR_SECRET`
- `RESEND_API_KEY`
- `EMAIL_FROM`
- `CARE_PORTAL_URL`

## Capsule Encryption Key

Migration `20260707120000_029_capsule_sensitive_field_encryption.sql` encrypts
`HOME_ACCESS` and `MEDICAL` Capsule sections with server-managed field-level
encryption. Configure the DB key before writing or backfilling sensitive
sections:

```sql
alter database postgres
  set "app.capsule_encryption_key" = 'REPLACE_WITH_LONG_RANDOM_SECRET';
```

If the key is configured after the migration, run the manual helper at
`D:\hackthekitty\sql-manual\2026-07-07-capsule-encryption-key.sql`, replacing the
placeholder with a real secret. The backfill proof query should show ciphertext
for `HOME_ACCESS` and `MEDICAL`, with `content_json` containing only an encrypted
marker.

## Intentional Exclusions

These historical manual SQL files are not committed as replayed migrations:

- `2026-07-04-missed-check-in.sql`: discovery and one-shot operational check.
- `2026-07-06-resolve-pulga-active-handoff.sql`: data cleanup for one demo cat.
- `2026-07-06-remove-demo-developer-rpcs.sql`: not applied because the Android
  five-tap debug handoff trigger remains available for the hackathon demo.

## Verification Targets

- All public tables created by the baseline have RLS enabled.
- Public client writes to `capsule_sections` are revoked; Android uses
  `upsert_cat_capsule_sections`.
- Caregiver Capsule reads use `list_cat_capsule_sections`.
- Responder portal reads use `list_my_incident_capsule_sections`.
- `HOME_ACCESS` and `MEDICAL` rows store ciphertext in
  `content_ciphertext`, not plaintext in `content_json`.
