# process-due-check-ins

Runs the server-side continuity processor and sends Resend-backed Care Circle emails. A scheduler should call the processor mode on a short interval.

Required environment variables:

- `SUPABASE_URL`
- `SUPABASE_SERVICE_ROLE_KEY`
- `CHECK_IN_PROCESSOR_SECRET`
- `RESEND_API_KEY`
- `EMAIL_FROM`, for example `The Third Bowl <alerts@your-domain.com>`
- `CARE_PORTAL_URL`, for example `https://your-vercel-app.vercel.app/#/portal`

Optional environment variables:

- `SUPABASE_ANON_KEY` or `SUPABASE_PUBLISHABLE_KEYS`; used to verify a signed-in Android user's JWT when the function is called to send caregiver-scoped emails.

If the Supabase project requires explicit Data API grants, apply the committed migrations in `supabase/migrations/`; the email delivery and Care Circle invitation grants are included there.

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

Email delivery:

- The processor creates `notification_deliveries` rows with `delivery_status = request_created`.
- This Edge Function sends pending incident-assignment emails through Resend.
- On provider acceptance, it updates `notification_deliveries.delivery_status` to `provider_accepted`, stores `provider_message_id`, and marks the assignment `notified`.
- The Android five-tap debug flow can call this function with the signed-in caregiver JWT and `{ "sendPendingOnly": true }` after the debug RPC creates an incident. In that mode, the function only sends pending deliveries for cats owned by that caregiver.
- Android can call this function with the signed-in caregiver JWT and `{ "sendInvitation": true, "invitationId": "...", "invitedEmail": "person@example.com" }` after creating a Care Circle invitation. In that mode, the function verifies the invitation belongs to that caregiver before sending.
