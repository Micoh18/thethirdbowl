# process-due-check-ins

Runs the server-side continuity processor. A scheduler should call this function on a short interval.

Required environment variables:

- `SUPABASE_URL`
- `SUPABASE_SERVICE_ROLE_KEY`
- `CHECK_IN_PROCESSOR_SECRET`

Request:

```bash
curl -X POST "$SUPABASE_URL/functions/v1/process-due-check-ins" \
  -H "x-processor-secret: $CHECK_IN_PROCESSOR_SECRET" \
  -H "content-type: application/json" \
  -d "{}"
```

Optional body for controlled testing:

```json
{
  "now": "2026-06-26T18:00:00Z"
}
```

The function calls `public.process_due_check_ins`, which creates missed check-in incidents from server-side plan deadlines. It does not expose service-role credentials to Android or the browser.
