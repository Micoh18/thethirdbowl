# Submission Audit

Date: July 6, 2026

Scope audited:

- Hackathon rules and submission requirements.
- Product specification and no-mock policy.
- Android app source.
- Web landing and Care Circle portal.
- Supabase Edge Function.
- Out-of-repository SQL migration references.
- Build and dependency checks that could run locally.

## Executive Status

The project has a coherent Android plus web prototype connected to Supabase Auth/PostgREST and a server-side missed check-in processor. It is not yet submission-complete against the full product specification because provider email still needs deployed secrets/domain verification, push delivery, complete reproducible backend setup, final Android device verification, and security scan artifacts are still missing or unverified.

The strongest current submission angle is a smaller, truthful release: Android caregiver flow, scoped Care Circle portal, server-side incident state, temporary grants, audit trail, and incident email only after a live Resend receipt is shown. Do not claim real push delivery, application-level encryption, server-managed web sessions, or provider-confirmed email delivery until those are completed and shown.

## Evidence Collected

| Area | Evidence | Result |
| --- | --- | --- |
| Web production build | `npm run build` in `website/` | Pass |
| Web dependency audit | `npm audit --omit=dev` in `website/` | Pass, 0 vulnerabilities |
| Android CLI build | `.\gradlew.bat :app:assembleDebug` | Pass |
| Android tests | `.\gradlew.bat :app:testDebugUnitTest` | Pass |
| Android device run | Android Studio physical device run reported by user | Pass |
| Debug simulator paths | Android Settings exposes a hidden five-tap debug handoff trigger by user request | Present; must be treated as rehearsal-only |
| Supabase service credential exposure | Static search did not find service-role literal values in client code | No literal service role found |
| README | Root README was missing | Added |
| Security posture | Portal persisted Supabase sessions in browser storage | Improved by disabling session persistence and adding CSP |

## Work Completed During Audit

- Restored the Android Settings missed-check-in trigger behind five logo taps by user request, with explicit debug/rehearsal copy.
- Added masked invitation email support in Android People and access.
- Removed the unused Foojay Gradle toolchain resolver plugin from `settings.gradle.kts` to avoid an extra uncached plugin dependency.
- Changed the web Supabase client to avoid persistent browser session storage.
- Added a static CSP and referrer policy to the web entrypoint.
- Added Resend-backed incident email dispatch in the Supabase Edge Function, including provider status updates and Android debug email draining.
- Added a root `README.md` with setup, release path, security model, verification status, and known limitations.
- Added this submission audit document.
- Prepared manual SQL outside the repository at `D:\hackthekitty\sql-manual\2026-07-06-remove-demo-developer-rpcs.sql` to revoke/drop historical demo/developer RPCs before final submission.
- Prepared manual SQL outside the repository at `D:\hackthekitty\sql-manual\2026-07-06-invitation-email-mask.sql` to store and return `invited_email_masked` for new invitations.

## P0 Submission Blockers

1. Real email delivery must be deployed with Resend secrets, a verified sender domain, and a live receipt, or the final demo must not claim provider-delivered invitation/escalation email.
2. Real Android push reminders are not present in repository code.
3. The final backend schema is not reproducible from committed migrations because SQL is maintained out of repo by project policy.
4. A full end-to-end run has not been captured: caregiver, invite, exact-email portal acceptance, check-in, missed cycle, incident acceptance, scoped access, resolution, revocation, and audit.
5. Aikido or equivalent security scan artifact is missing.

## P1 Risks

- The portal is static and cannot provide HttpOnly server-managed sessions.
- `HOME_ACCESS` and `MEDICAL` data are stored as JSON, not application-level ciphertext.
- The due-check-in mechanism is a processor/scheduler model rather than a Temporal durable workflow.
- The Android app now intentionally keeps a hidden debug trigger; final submission narration must not present that path as real timer proof.
- The Android app embeds publishable Supabase configuration in `BuildConfig`, which is acceptable for public client keys but should be documented as non-secret.

## Recommended Final Submission Scope

Claim only what can be shown truthfully:

- Android-authenticated caregiver creates a cat.
- Care Capsule data persists in Supabase.
- Care Circle invitation records are scoped by role and exact email.
- Portal requires authentication with the invited email.
- Incident acceptance grants only authorized Capsule sections.
- Resolution revokes incident access.
- Audit history records sensitive transitions.

Avoid claiming:

- Provider-confirmed email delivery before Resend is configured and a live receipt is shown.
- Android push notification delivery.
- Application-level encryption.
- Server-managed web sessions.
- Temporal workflow execution.
- Any simulated or manually fast-forwarded incident as if it were a real missed timer.

## Next Best Actions

1. Manually run `D:\hackthekitty\sql-manual\2026-07-06-invitation-email-mask.sql` so new invites show masked emails in People and access.
2. Verify live Supabase flows with two real email accounts.
3. Deploy the Edge Function with Resend secrets and verify one live incident email receipt.
4. Record a short final proof video using only real state transitions.
5. Export security scan results and add them to the submission package.
