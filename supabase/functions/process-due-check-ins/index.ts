type ProcessorResult = {
  processed_plans: number
  incidents_created: number
  assignments_created: number
}

type RequestBody = {
  now?: unknown
  sendInvitation?: unknown
  sendPendingOnly?: unknown
  maxEmails?: unknown
  invitationId?: unknown
  invitedEmail?: unknown
}

type PendingDelivery = {
  id: string
  recipient_user_id: string | null
  incident_id: string | null
  created_at: string
}

type ProfileRow = {
  id: string
  email_normalized: string
}

type IncidentRow = {
  id: string
  cat_id: string
  activated_at: string
}

type CatRow = {
  id: string
  name: string
  primary_caregiver_user_id: string
}

type AssignmentRow = {
  id: string
  response_deadline_at: string
  step_number: number
  state: string
}

type InvitationRow = {
  id: string
  cat_id: string
  invited_email_hash: string | null
  relationship_label: string
  status: string
  expires_at: string
  created_by_user_id: string
  proposed_roles: string[] | null
  proposed_scopes: string[] | null
}

type EmailResult = {
  deliveryId: string
  status: "sent" | "failed" | "skipped"
  providerMessageId?: string
  error?: string
}

type EmailSummary = {
  configured: boolean
  sent: number
  failed: number
  skipped: number
  results: EmailResult[]
}

const jsonHeaders = {
  "Content-Type": "application/json",
}

Deno.serve(async (request) => {
  if (request.method !== "POST") {
    return jsonResponse({ error: "method_not_allowed" }, 405)
  }

  const body = await request.json().catch(() => null) as RequestBody | null

  const supabaseUrl = Deno.env.get("SUPABASE_URL")
  const serviceRoleKey = getServiceRoleKey()

  if (!supabaseUrl || !serviceRoleKey) {
    return jsonResponse({ error: "processor_not_configured" }, 500)
  }

  const authMode = await authorizeRequest(request, body, supabaseUrl)
  if (!authMode.ok) {
    return jsonResponse({ error: authMode.error }, authMode.status)
  }

  if (body?.sendInvitation === true) {
    if (!authMode.ownerUserId) {
      return jsonResponse({ error: "missing_user_token" }, 401)
    }

    const emailSummary = await sendCareCircleInvitationEmail({
      supabaseUrl,
      serviceRoleKey,
      ownerUserId: authMode.ownerUserId,
      body,
    })

    return jsonResponse(
      {
        ok: true,
        mode: "invitation_email",
        emails: emailSummary,
      },
      200,
    )
  }

  const parsedNow = parseNow(body)
  if (!parsedNow.ok) {
    return jsonResponse({ error: "invalid_now" }, 400)
  }

  let result: ProcessorResult = {
    processed_plans: 0,
    incidents_created: 0,
    assignments_created: 0,
  }

  if (!authMode.sendPendingOnly) {
    const rpcResponse = await fetch(`${supabaseUrl}/rest/v1/rpc/process_due_check_ins`, {
      method: "POST",
      headers: serviceHeaders(serviceRoleKey),
      body: JSON.stringify(parsedNow.value ? { p_now: parsedNow.value } : {}),
    })

    const payload = await rpcResponse.json().catch(() => null)

    if (!rpcResponse.ok) {
      return jsonResponse(
        {
          error: "processor_failed",
          detail: payload,
        },
        502,
      )
    }

    result = normalizeResult(payload)
  }

  const emailSummary = await sendPendingIncidentEmails({
    supabaseUrl,
    serviceRoleKey,
    ownerUserId: authMode.ownerUserId,
    maxEmails: parseMaxEmails(body),
  })

  return jsonResponse(
    {
      ok: true,
      mode: authMode.sendPendingOnly ? "email_only" : "processor",
      processedPlans: result.processed_plans,
      incidentsCreated: result.incidents_created,
      assignmentsCreated: result.assignments_created,
      emails: emailSummary,
    },
    200,
  )
})

async function authorizeRequest(
  request: Request,
  body: RequestBody | null,
  supabaseUrl: string,
): Promise<
  | { ok: true; sendPendingOnly: boolean; ownerUserId: string | null }
  | { ok: false; status: number; error: string }
> {
  const expectedSecret = Deno.env.get("CHECK_IN_PROCESSOR_SECRET")
  const providedSecret = request.headers.get("x-processor-secret")

  if (expectedSecret && providedSecret === expectedSecret) {
    return { ok: true, sendPendingOnly: false, ownerUserId: null }
  }

  if (body?.sendPendingOnly !== true && body?.sendInvitation !== true) {
    return { ok: false, status: 401, error: "unauthorized" }
  }

  const token = bearerToken(request)
  if (!token) {
    return { ok: false, status: 401, error: "missing_user_token" }
  }

  const publishableKey = getPublishableKey() ?? getServiceRoleKey()
  if (!publishableKey) {
    return { ok: false, status: 500, error: "auth_not_configured" }
  }

  const userResponse = await fetch(`${trimSlash(supabaseUrl)}/auth/v1/user`, {
    headers: {
      "apikey": publishableKey,
      "Authorization": `Bearer ${token}`,
    },
  })

  const userPayload = await userResponse.json().catch(() => null)
  const userId = userPayload && typeof userPayload === "object" ? (userPayload as { id?: unknown }).id : null

  if (!userResponse.ok || typeof userId !== "string") {
    return { ok: false, status: 401, error: "invalid_user_token" }
  }

  return { ok: true, sendPendingOnly: true, ownerUserId: userId }
}

function parseNow(body: RequestBody | null): { ok: true; value: string | null } | { ok: false } {
  if (!body || typeof body !== "object" || !("now" in body)) {
    return { ok: true, value: null }
  }

  const value = (body as { now?: unknown }).now
  if (typeof value !== "string" || Number.isNaN(Date.parse(value))) {
    return { ok: false }
  }

  return { ok: true, value }
}

function parseMaxEmails(body: RequestBody | null): number {
  const raw = Number(body?.maxEmails ?? 25)
  if (!Number.isFinite(raw)) return 25
  return Math.max(1, Math.min(Math.trunc(raw), 25))
}

function normalizeResult(payload: unknown): ProcessorResult {
  const row = Array.isArray(payload) ? payload[0] : payload
  if (!row || typeof row !== "object") {
    return {
      processed_plans: 0,
      incidents_created: 0,
      assignments_created: 0,
    }
  }

  const result = row as Partial<ProcessorResult>
  return {
    processed_plans: Number(result.processed_plans ?? 0),
    incidents_created: Number(result.incidents_created ?? 0),
    assignments_created: Number(result.assignments_created ?? 0),
  }
}

async function sendPendingIncidentEmails(options: {
  supabaseUrl: string
  serviceRoleKey: string
  ownerUserId: string | null
  maxEmails: number
}): Promise<EmailSummary> {
  const resendApiKey = Deno.env.get("RESEND_API_KEY")
  const emailFrom = Deno.env.get("EMAIL_FROM")

  if (!resendApiKey || !emailFrom) {
    return {
      configured: false,
      sent: 0,
      failed: 0,
      skipped: 0,
      results: [],
    }
  }

  const deliveries = await restSelect<PendingDelivery>(
    options.supabaseUrl,
    options.serviceRoleKey,
    "notification_deliveries",
    {
      select: "id,recipient_user_id,incident_id,created_at",
      channel: "eq.email",
      notification_type: "eq.incident_assignment",
      delivery_status: "eq.request_created",
      order: "created_at.asc",
      limit: String(options.ownerUserId ? Math.min(Math.max(options.maxEmails * 5, 25), 100) : options.maxEmails),
    },
  )

  const results: EmailResult[] = []

  for (const delivery of deliveries) {
    if (results.filter((result) => result.status !== "skipped").length >= options.maxEmails) {
      break
    }

    const result = await sendIncidentEmail({
      ...options,
      delivery,
      resendApiKey,
      emailFrom,
    })
    results.push(result)
  }

  return {
    configured: true,
    sent: results.filter((result) => result.status === "sent").length,
    failed: results.filter((result) => result.status === "failed").length,
    skipped: results.filter((result) => result.status === "skipped").length,
    results,
  }
}

async function sendIncidentEmail(options: {
  supabaseUrl: string
  serviceRoleKey: string
  ownerUserId: string | null
  delivery: PendingDelivery
  resendApiKey: string
  emailFrom: string
}): Promise<EmailResult> {
  const { delivery } = options

  try {
    if (!delivery.recipient_user_id || !delivery.incident_id) {
      await markDeliveryFailed(options, delivery.id, "missing_delivery_target")
      return { deliveryId: delivery.id, status: "failed", error: "missing_delivery_target" }
    }

    const profile = await singleOrNull<ProfileRow>(
      options.supabaseUrl,
      options.serviceRoleKey,
      "profiles",
      {
        select: "id,email_normalized",
        id: `eq.${delivery.recipient_user_id}`,
        limit: "1",
      },
    )

    const incident = await singleOrNull<IncidentRow>(
      options.supabaseUrl,
      options.serviceRoleKey,
      "incidents",
      {
        select: "id,cat_id,activated_at",
        id: `eq.${delivery.incident_id}`,
        limit: "1",
      },
    )

    if (!profile?.email_normalized || !incident) {
      await markDeliveryFailed(options, delivery.id, "missing_email_context")
      return { deliveryId: delivery.id, status: "failed", error: "missing_email_context" }
    }

    const cat = await singleOrNull<CatRow>(
      options.supabaseUrl,
      options.serviceRoleKey,
      "cats",
      {
        select: "id,name,primary_caregiver_user_id",
        id: `eq.${incident.cat_id}`,
        limit: "1",
      },
    )

    if (!cat) {
      await markDeliveryFailed(options, delivery.id, "missing_cat_context")
      return { deliveryId: delivery.id, status: "failed", error: "missing_cat_context" }
    }

    if (options.ownerUserId && cat.primary_caregiver_user_id !== options.ownerUserId) {
      return { deliveryId: delivery.id, status: "skipped", error: "not_owner_incident" }
    }

    const assignment = await singleOrNull<AssignmentRow>(
      options.supabaseUrl,
      options.serviceRoleKey,
      "incident_assignments",
      {
        select: "id,response_deadline_at,step_number,state",
        notification_delivery_id: `eq.${delivery.id}`,
        limit: "1",
      },
    )

    const message = incidentEmailMessage({
      catName: cat.name,
      deadline: assignment?.response_deadline_at ?? null,
      portalUrl: Deno.env.get("CARE_PORTAL_URL") ?? null,
    })

    const providerResponse = await fetch("https://api.resend.com/emails", {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${options.resendApiKey}`,
        "Content-Type": "application/json",
        "Idempotency-Key": `the-third-bowl-${delivery.id}`,
      },
      body: JSON.stringify({
        from: options.emailFrom,
        to: [profile.email_normalized],
        subject: `The Third Bowl: ${cat.name} needs backup care`,
        html: message.html,
        text: message.text,
      }),
    })

    const providerPayload = await providerResponse.json().catch(() => null)
    const providerMessageId = providerPayload && typeof providerPayload === "object"
      ? (providerPayload as { id?: unknown }).id
      : null

    if (!providerResponse.ok || typeof providerMessageId !== "string") {
      const error = providerErrorCode(providerPayload, providerResponse.status)
      await markDeliveryFailed(options, delivery.id, error)
      return { deliveryId: delivery.id, status: "failed", error }
    }

    const acceptedAt = new Date().toISOString()
    await restPatch(
      options.supabaseUrl,
      options.serviceRoleKey,
      "notification_deliveries",
      { id: `eq.${delivery.id}` },
      {
        provider: "resend",
        provider_message_id: providerMessageId,
        provider_accepted_at: acceptedAt,
        delivery_status: "provider_accepted",
        last_error_code: null,
      },
    )
    await restPatch(
      options.supabaseUrl,
      options.serviceRoleKey,
      "incident_assignments",
      {
        notification_delivery_id: `eq.${delivery.id}`,
        state: "eq.pending",
      },
      {
        state: "notified",
        notified_at: acceptedAt,
      },
    )

    return {
      deliveryId: delivery.id,
      status: "sent",
      providerMessageId,
    }
  } catch (error) {
    const code = error instanceof Error ? error.message : "email_send_exception"
    await markDeliveryFailed(options, delivery.id, code).catch(() => null)
    return { deliveryId: delivery.id, status: "failed", error: code }
  }
}

async function sendCareCircleInvitationEmail(options: {
  supabaseUrl: string
  serviceRoleKey: string
  ownerUserId: string
  body: RequestBody | null
}): Promise<EmailSummary> {
  const resendApiKey = Deno.env.get("RESEND_API_KEY")
  const emailFrom = Deno.env.get("EMAIL_FROM")

  if (!resendApiKey || !emailFrom) {
    return {
      configured: false,
      sent: 0,
      failed: 0,
      skipped: 0,
      results: [],
    }
  }

  const parsed = parseInvitationEmailRequest(options.body)
  if (!parsed.ok) {
    return singleEmailSummary({
      deliveryId: "care_circle_invitation",
      status: "failed",
      error: parsed.error,
    })
  }

  const invitation = await singleOrNull<InvitationRow>(
    options.supabaseUrl,
    options.serviceRoleKey,
    "invitations",
    {
      select: "id,cat_id,invited_email_hash,relationship_label,status,expires_at,created_by_user_id,proposed_roles,proposed_scopes",
      id: `eq.${parsed.invitationId}`,
      limit: "1",
    },
  )

  if (!invitation || invitation.created_by_user_id !== options.ownerUserId) {
    return singleEmailSummary({
      deliveryId: parsed.invitationId,
      status: "failed",
      error: "invitation_not_found",
    })
  }

  const storedEmailHash = postgresByteaHex(invitation.invited_email_hash)
  if (storedEmailHash) {
    const requestedEmailHash = await sha256Hex(parsed.invitedEmail)
    if (storedEmailHash !== requestedEmailHash) {
      return singleEmailSummary({
        deliveryId: parsed.invitationId,
        status: "failed",
        error: "invitation_email_mismatch",
      })
    }
  }

  const cat = await singleOrNull<CatRow>(
    options.supabaseUrl,
    options.serviceRoleKey,
    "cats",
    {
      select: "id,name,primary_caregiver_user_id",
      id: `eq.${invitation.cat_id}`,
      limit: "1",
    },
  )

  if (!cat || cat.primary_caregiver_user_id !== options.ownerUserId) {
    return singleEmailSummary({
      deliveryId: parsed.invitationId,
      status: "failed",
      error: "invitation_not_owned",
    })
  }

  if (invitation.status !== "pending") {
    return singleEmailSummary({
      deliveryId: parsed.invitationId,
      status: "skipped",
      error: "invitation_not_pending",
    })
  }

  if (Number.isFinite(Date.parse(invitation.expires_at)) && Date.parse(invitation.expires_at) <= Date.now()) {
    return singleEmailSummary({
      deliveryId: parsed.invitationId,
      status: "skipped",
      error: "invitation_expired",
    })
  }

  const message = invitationEmailMessage({
    catName: cat.name,
    invitedEmail: parsed.invitedEmail,
    relationshipLabel: invitation.relationship_label,
    role: invitation.proposed_roles?.[0] ?? null,
    scopes: invitation.proposed_scopes ?? [],
    expiresAt: invitation.expires_at,
    portalUrl: Deno.env.get("CARE_PORTAL_URL") ?? null,
  })

  const providerResponse = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${resendApiKey}`,
      "Content-Type": "application/json",
      "Idempotency-Key": `the-third-bowl-invite-${invitation.id}`,
    },
    body: JSON.stringify({
      from: emailFrom,
      to: [parsed.invitedEmail],
      subject: `The Third Bowl: ${cat.name} invited you to their Care Circle`,
      html: message.html,
      text: message.text,
    }),
  })

  const providerPayload = await providerResponse.json().catch(() => null)
  const providerMessageId = providerPayload && typeof providerPayload === "object"
    ? (providerPayload as { id?: unknown }).id
    : null

  if (!providerResponse.ok || typeof providerMessageId !== "string") {
    return singleEmailSummary({
      deliveryId: invitation.id,
      status: "failed",
      error: providerErrorCode(providerPayload, providerResponse.status),
    })
  }

  return singleEmailSummary({
    deliveryId: invitation.id,
    status: "sent",
    providerMessageId,
  })
}

function parseInvitationEmailRequest(
  body: RequestBody | null,
):
  | { ok: true; invitationId: string; invitedEmail: string }
  | { ok: false; error: string } {
  const invitationId = typeof body?.invitationId === "string" ? body.invitationId.trim() : ""
  const invitedEmail = typeof body?.invitedEmail === "string" ? body.invitedEmail.trim().toLowerCase() : ""

  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(invitationId)) {
    return { ok: false, error: "invalid_invitation_id" }
  }

  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(invitedEmail)) {
    return { ok: false, error: "invalid_invited_email" }
  }

  return { ok: true, invitationId, invitedEmail }
}

function singleEmailSummary(result: EmailResult): EmailSummary {
  return {
    configured: true,
    sent: result.status === "sent" ? 1 : 0,
    failed: result.status === "failed" ? 1 : 0,
    skipped: result.status === "skipped" ? 1 : 0,
    results: [result],
  }
}

function incidentEmailMessage(input: {
  catName: string
  deadline: string | null
  portalUrl: string | null
}): { html: string; text: string } {
  const escapedCat = escapeHtml(input.catName)
  const deadlineLine = input.deadline
    ? `Please respond before ${new Date(input.deadline).toLocaleString("en-US", { timeZone: "UTC" })} UTC.`
    : "Please respond as soon as you can."
  const portalUrl = input.portalUrl?.trim()
  const escapedPortalUrl = portalUrl ? escapeHtml(portalUrl) : null

  const html = `
    <div style="font-family:Arial,sans-serif;line-height:1.5;color:#1f2933">
      <h1 style="font-size:20px;margin:0 0 12px">The Third Bowl handoff</h1>
      <p>A continuity check-in was missed for <strong>${escapedCat}</strong>.</p>
      <p>${escapeHtml(deadlineLine)}</p>
      ${
        escapedPortalUrl
          ? `<p><a href="${escapedPortalUrl}" style="display:inline-block;padding:10px 14px;border-radius:6px;background:#1f6f68;color:white;text-decoration:none">Open Care Circle Portal</a></p>`
          : "<p>Open the Care Circle Portal and sign in with this email to accept responsibility.</p>"
      }
      <p style="font-size:13px;color:#52616b">You are receiving this because this email belongs to an accepted Care Circle responder.</p>
    </div>
  `.trim()

  const text = [
    "The Third Bowl handoff",
    "",
    `A continuity check-in was missed for ${input.catName}.`,
    deadlineLine,
    portalUrl ? `Open the Care Circle Portal: ${portalUrl}` : "Open the Care Circle Portal and sign in with this email to accept responsibility.",
  ].join("\n")

  return { html, text }
}

function invitationEmailMessage(input: {
  catName: string
  invitedEmail: string
  relationshipLabel: string
  role: string | null
  scopes: string[]
  expiresAt: string
  portalUrl: string | null
}): { html: string; text: string } {
  const escapedCat = escapeHtml(input.catName)
  const escapedRelationship = escapeHtml(input.relationshipLabel)
  const escapedEmail = escapeHtml(input.invitedEmail)
  const portalUrl = input.portalUrl?.trim()
  const escapedPortalUrl = portalUrl ? escapeHtml(portalUrl) : null
  const role = input.role ? humanize(input.role) : "Trusted contact"
  const scopes = input.scopes.length ? input.scopes.map(humanize).join(", ") : "Core care"
  const expiresLine = Number.isFinite(Date.parse(input.expiresAt))
    ? `This invitation expires ${new Date(input.expiresAt).toLocaleString("en-US", { timeZone: "UTC" })} UTC.`
    : "This invitation expires soon."

  const html = `
    <div style="font-family:Arial,sans-serif;line-height:1.5;color:#1f2933">
      <h1 style="font-size:20px;margin:0 0 12px">The Third Bowl Care Circle invitation</h1>
      <p>You were invited to help <strong>${escapedCat}</strong> as <strong>${escapedRelationship}</strong>.</p>
      <p>Role: ${escapeHtml(role)}. Incident access: ${escapeHtml(scopes)}.</p>
      <p>Open the Care Circle Portal and sign in or create an account with <strong>${escapedEmail}</strong>.</p>
      ${
        escapedPortalUrl
          ? `<p><a href="${escapedPortalUrl}" style="display:inline-block;padding:10px 14px;border-radius:6px;background:#1f6f68;color:white;text-decoration:none">Open Care Circle Portal</a></p>`
          : ""
      }
      <p>${escapeHtml(expiresLine)}</p>
      <p style="font-size:13px;color:#52616b">A caregiver added this email to a private Care Circle. Access is only shown after email-based sign-in.</p>
    </div>
  `.trim()

  const text = [
    "The Third Bowl Care Circle invitation",
    "",
    `You were invited to help ${input.catName} as ${input.relationshipLabel}.`,
    `Role: ${role}. Incident access: ${scopes}.`,
    `Sign in or create an account with ${input.invitedEmail}.`,
    portalUrl ? `Open the Care Circle Portal: ${portalUrl}` : "Open the Care Circle Portal to accept the invitation.",
    expiresLine,
  ].join("\n")

  return { html, text }
}

async function markDeliveryFailed(
  options: { supabaseUrl: string; serviceRoleKey: string },
  deliveryId: string,
  errorCode: string,
) {
  await restPatch(
    options.supabaseUrl,
    options.serviceRoleKey,
    "notification_deliveries",
    { id: `eq.${deliveryId}` },
    {
      provider: "resend",
      delivery_status: "failed",
      last_error_code: truncate(errorCode, 120),
    },
  )
}

async function singleOrNull<T>(
  supabaseUrl: string,
  serviceRoleKey: string,
  resource: string,
  params: Record<string, string>,
): Promise<T | null> {
  const rows = await restSelect<T>(supabaseUrl, serviceRoleKey, resource, params)
  return rows[0] ?? null
}

async function restSelect<T>(
  supabaseUrl: string,
  serviceRoleKey: string,
  resource: string,
  params: Record<string, string>,
): Promise<T[]> {
  const response = await fetch(restUrl(supabaseUrl, resource, params), {
    headers: serviceHeaders(serviceRoleKey),
  })
  const payload = await response.json().catch(() => null)

  if (!response.ok) {
    throw new Error(`rest_select_failed_${response.status}_${JSON.stringify(payload)}`)
  }

  return Array.isArray(payload) ? payload as T[] : []
}

async function restPatch(
  supabaseUrl: string,
  serviceRoleKey: string,
  resource: string,
  filters: Record<string, string>,
  body: Record<string, unknown>,
) {
  const response = await fetch(restUrl(supabaseUrl, resource, filters), {
    method: "PATCH",
    headers: {
      ...serviceHeaders(serviceRoleKey),
      "Prefer": "return=minimal",
    },
    body: JSON.stringify(body),
  })

  if (!response.ok) {
    const payload = await response.text().catch(() => "")
    throw new Error(`rest_patch_failed_${response.status}_${payload}`)
  }
}

function restUrl(supabaseUrl: string, resource: string, params: Record<string, string>): string {
  const url = new URL(`${trimSlash(supabaseUrl)}/rest/v1/${resource}`)
  for (const [key, value] of Object.entries(params)) {
    url.searchParams.set(key, value)
  }
  return url.toString()
}

function serviceHeaders(serviceRoleKey: string): HeadersInit {
  return {
    ...jsonHeaders,
    "apikey": serviceRoleKey,
    "Authorization": `Bearer ${serviceRoleKey}`,
  }
}

function getServiceRoleKey(): string | null {
  return Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? keyFromJsonSecret("SUPABASE_SECRET_KEYS")
}

function getPublishableKey(): string | null {
  return Deno.env.get("SUPABASE_ANON_KEY") ?? keyFromJsonSecret("SUPABASE_PUBLISHABLE_KEYS")
}

function keyFromJsonSecret(name: string): string | null {
  const raw = Deno.env.get(name)
  if (!raw) return null

  try {
    const parsed = JSON.parse(raw) as Record<string, unknown>
    const defaultKey = parsed.default
    return typeof defaultKey === "string" ? defaultKey : null
  } catch {
    return null
  }
}

function bearerToken(request: Request): string | null {
  const authorization = request.headers.get("authorization") ?? ""
  const match = authorization.match(/^Bearer\s+(.+)$/i)
  return match?.[1] ?? null
}

function providerErrorCode(payload: unknown, status: number): string {
  if (payload && typeof payload === "object") {
    const objectPayload = payload as { name?: unknown; error?: unknown; message?: unknown }
    const value = objectPayload.name ?? objectPayload.error ?? objectPayload.message
    if (typeof value === "string" && value.trim()) {
      return truncate(`resend_${value}`, 120)
    }
  }
  return `resend_http_${status}`
}

function escapeHtml(value: string): string {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;")
}

function humanize(value: string): string {
  return value
    .toLowerCase()
    .split("_")
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ")
}

async function sha256Hex(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value))
  return Array.from(new Uint8Array(digest))
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("")
}

function postgresByteaHex(value: string | null): string | null {
  if (!value) return null

  const normalized = value.trim().replace(/^\\x/i, "").toLowerCase()
  return /^[0-9a-f]{64}$/.test(normalized) ? normalized : null
}

function truncate(value: string, maxLength: number): string {
  return value.length <= maxLength ? value : value.slice(0, maxLength)
}

function trimSlash(value: string): string {
  return value.replace(/\/$/, "")
}

function jsonResponse(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: jsonHeaders,
  })
}
