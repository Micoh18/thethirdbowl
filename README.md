# The Third Bowl

Emergency continuity for cats whose care lives in one person's head.

The Third Bowl lets a caregiver store structured care instructions, invite trusted people into a per-cat Care Circle, run a recurring availability check-in, and release only the needed care details during an incident.

## Repository Layout

- `android-app/`: native Android app built with Kotlin, Jetpack Compose, and Supabase.
- `website/`: Vite web app with a public landing page and Care Circle portal.
- `supabase/functions/process-due-check-ins/`: Supabase Edge Function that invokes the server-side missed check-in processor and sends Resend-backed Care Circle emails.
- `supabase/seed.sql`: placeholder seed file.

The Supabase schema SQL is applied manually by the project owner. This repository does not run database migrations automatically.

## Current Release Path

The implemented product path is:

1. A caregiver signs in on Android with Supabase Auth.
2. The caregiver creates a cat profile.
3. The caregiver edits Capsule sections for core care, home access, and medical context.
4. The caregiver invites a Care Circle contact with a role and scoped access template.
5. The invited contact signs into the web portal with the exact invited email and accepts.
6. The caregiver arms a short continuity ritual after minimum readiness checks.
7. The caregiver completes server-confirmed check-ins from Android.
8. A server-side processor can create missed check-in incidents from overdue plans.
9. The portal lets assigned responders accept responsibility, confirm the cat was reached, view authorized Capsule sections, and resolve the handoff.
10. Android and the portal show audit and incident state from Supabase.

## Setup

### Prerequisites

- Android Studio with its embedded JBR/JDK 21 selected as the Gradle JDK.
- Android SDK for API 36.
- Node.js 20 or newer.
- A Supabase project provisioned with the required schema and RPCs.

### Web

```bash
cd website
npm install
npm run dev
npm run build
```

### Vercel

This repository can be imported directly as a Vercel Git project. The root `vercel.json` builds the static portal from `website/`:

- Install command: `npm install --prefix website`
- Build command: `npm run build --prefix website`
- Output directory: `website/dist`

After deploying, attach the custom domain and set `CARE_PORTAL_URL` in the Supabase Edge Function to the final portal URL, for example `https://thethirdbowl.space/#/portal`.

### Android

Open `android-app/` in Android Studio, then set:

- Gradle JDK: Android Studio embedded JBR/JDK, not the VS Code RedHat Java runtime.
- Run target: emulator or Android device with USB debugging enabled.

Command-line build, when the local JVM is configured correctly:

```bash
cd android-app
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

### Missed Check-In Processor

The Edge Function requires:

- `SUPABASE_URL`
- `SUPABASE_SERVICE_ROLE_KEY`
- `CHECK_IN_PROCESSOR_SECRET`
- `RESEND_API_KEY`
- `EMAIL_FROM`, using a verified Resend sender domain
- `CARE_PORTAL_URL`, pointing to the deployed Vercel portal route, for example `https://your-vercel-app.vercel.app/#/portal`

Example request:

```bash
curl -X POST "$SUPABASE_URL/functions/v1/process-due-check-ins" \
  -H "x-processor-secret: $CHECK_IN_PROCESSOR_SECRET" \
  -H "content-type: application/json" \
  -d "{}"
```

The service role key must stay only in the Edge Function environment.
The Resend API key also stays only in the Edge Function environment. Android uses its signed-in Supabase JWT only to ask the function to send caregiver-scoped Care Circle invitation emails or pending incident emails for cats belonging to that caregiver.

If the Supabase project uses explicit Data API grants, run the manual SQL at `D:\hackthekitty\sql-manual\2026-07-06-email-delivery-service-role-grants.sql` before testing email dispatch.
For Care Circle invitation emails, also run `D:\hackthekitty\sql-manual\2026-07-06-care-circle-invitation-email-grants.sql`.

## Security Model

- Android and browser clients use only the Supabase URL and publishable key.
- Sensitive actions go through Supabase RPCs and RLS-backed tables.
- Invitations are bound to the normalized invited email.
- Incident Capsule access is scoped through temporary grants.
- The portal does not persist Supabase sessions to `localStorage`.
- The static site includes a restrictive CSP meta policy.
- Android Settings includes a hidden five-tap debug handoff trigger for rehearsal only. It must not be presented as proof of the real missed-check-in timer.

## Known Limitations Before Final Submission

- Real Care Circle invitation and incident email delivery is implemented through Resend in the Supabase Edge Function, but it still requires deployed secrets, a verified sender domain, and live end-to-end verification.
- Real Android push registration and FCM delivery are not implemented in this repository.
- The portal is still a static client-side app, not a server-managed HttpOnly cookie session.
- `HOME_ACCESS` and `MEDICAL` Capsule sections are stored as JSON and should not be claimed as application-level encrypted.
- The server-side timer is implemented through a due-check-in processor and scheduler pattern, not a dedicated Temporal workflow.
- The Supabase schema SQL is maintained outside the repository by project policy, so the final ZIP/submission must account for database reproducibility.
- Historical invitations may not show an email mask unless the new manual SQL has been applied and the invite was created after that point.
- Aikido/security scan artifacts and a complete manual end-to-end proof video are still required.

## Verification Status

Verified on July 6, 2026:

- `website`: `npm run build` passes.
- `website`: `npm audit --omit=dev` reports 0 vulnerabilities.
- `android-app`: `.\gradlew.bat :app:assembleDebug` passes.
- `android-app`: `.\gradlew.bat :app:testDebugUnitTest` passes.
- Android has been run on a physical device through Android Studio.
