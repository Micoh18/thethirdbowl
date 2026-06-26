type ProcessorResult = {
  processed_plans: number
  incidents_created: number
  assignments_created: number
}

const jsonHeaders = {
  "Content-Type": "application/json",
}

Deno.serve(async (request) => {
  if (request.method !== "POST") {
    return jsonResponse({ error: "method_not_allowed" }, 405)
  }

  const expectedSecret = Deno.env.get("CHECK_IN_PROCESSOR_SECRET")
  const providedSecret = request.headers.get("x-processor-secret")

  if (!expectedSecret || providedSecret !== expectedSecret) {
    return jsonResponse({ error: "unauthorized" }, 401)
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL")
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")

  if (!supabaseUrl || !serviceRoleKey) {
    return jsonResponse({ error: "processor_not_configured" }, 500)
  }

  const parsedNow = await parseNow(request)
  if (!parsedNow.ok) {
    return jsonResponse({ error: "invalid_now" }, 400)
  }

  const now = parsedNow.value
  const rpcResponse = await fetch(`${supabaseUrl}/rest/v1/rpc/process_due_check_ins`, {
    method: "POST",
    headers: {
      ...jsonHeaders,
      "apikey": serviceRoleKey,
      "Authorization": `Bearer ${serviceRoleKey}`,
    },
    body: JSON.stringify(now ? { p_now: now } : {}),
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

  const result = normalizeResult(payload)

  return jsonResponse(
    {
      ok: true,
      processedPlans: result.processed_plans,
      incidentsCreated: result.incidents_created,
      assignmentsCreated: result.assignments_created,
    },
    200,
  )
})

async function parseNow(request: Request): Promise<{ ok: true; value: string | null } | { ok: false }> {
  const body = await request.json().catch(() => null)
  if (!body || typeof body !== "object" || !("now" in body)) {
    return { ok: true, value: null }
  }

  const value = (body as { now?: unknown }).now
  if (typeof value !== "string" || Number.isNaN(Date.parse(value))) {
    return { ok: false }
  }

  return { ok: true, value }
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

function jsonResponse(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: jsonHeaders,
  })
}
