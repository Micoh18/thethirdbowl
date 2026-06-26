import { createClient } from '@supabase/supabase-js'
import './styles.css'

const SUPABASE_URL = 'https://iykvrljnzoaymkfkiibb.supabase.co'
const SUPABASE_PUBLISHABLE_KEY = 'sb_publishable_v2eykawiui-RRyyjoXp_WA_16HHfgFx'

const supabase = createClient(SUPABASE_URL, SUPABASE_PUBLISHABLE_KEY, {
  auth: {
    autoRefreshToken: true,
    persistSession: true,
    detectSessionInUrl: true,
  },
})

const app = document.querySelector('#app')

let state = {
  email: '',
  password: '',
  session: null,
  invitations: [],
  assignments: [],
  careCoreByIncident: {},
  resolutionNotes: {},
  status: {
    tone: 'info',
    message: 'Checking your session...',
  },
  busy: false,
}

async function initialize() {
  const { data, error } = await supabase.auth.getSession()
  if (error) {
    setStatus('error', error.message)
  } else {
    state.session = data.session
    setStatus(
      'info',
      data.session
        ? 'Your Care Circle workspace is ready.'
        : 'Sign in with the invited email to see what needs your attention.',
    )
  }

  supabase.auth.onAuthStateChange((_event, session) => {
    state.session = session
    if (session) {
      loadPortalData()
    } else {
      state.invitations = []
      state.assignments = []
      state.careCoreByIncident = {}
      state.resolutionNotes = {}
      setStatus('info', 'Signed out.')
      render()
    }
  })

  if (state.session) {
    await loadPortalData()
  }

  render()
}

function render() {
  app.innerHTML = `
    <main class="shell">
      ${renderHero()}
      ${state.session ? renderSignedIn() : renderSignedOut()}
      ${renderStatus()}
    </main>
  `

  bindEvents()
}

function renderHero() {
  return `
    <header class="hero">
      <div class="brand-mark" aria-hidden="true">
        <span></span>
        <i></i><i></i><i></i>
      </div>
      <div>
        <p class="eyebrow">Care Circle Portal</p>
        <h1>The Third Bowl</h1>
        <p class="hero-copy">A quiet handoff space for trusted people who may need to help a cat when their caregiver cannot.</p>
      </div>
    </header>
  `
}

function renderStatus() {
  return `
    <section class="status status-${escapeAttribute(state.status.tone)}" aria-live="polite">
      ${state.busy ? '<span class="spinner"></span>' : '<span class="status-dot"></span>'}
      <span>${escapeHtml(state.status.message)}</span>
    </section>
  `
}

function renderSignedOut() {
  return `
    <section class="auth-panel">
      <div class="panel-copy">
        <h2>Verify before viewing private care details</h2>
        <p>Invitations and incident access are tied to the exact email the caregiver trusted. A link alone is not enough.</p>
      </div>
      <label>
        Email
        <input id="email" type="email" autocomplete="email" value="${escapeAttribute(state.email)}" />
      </label>
      <label>
        Password
        <input id="password" type="password" autocomplete="current-password" value="${escapeAttribute(state.password)}" />
      </label>
      <div class="actions">
        <button id="sign-in" ${state.busy ? 'disabled' : ''}>Sign in</button>
        <button id="sign-up" class="secondary" ${state.busy ? 'disabled' : ''}>Create account</button>
      </div>
    </section>

    <section class="trust-strip">
      ${renderTrustItem('Private by default', 'Care instructions appear only after sign-in and authorization.')}
      ${renderTrustItem('Scoped access', 'You see the details needed for your role, not the entire Capsule.')}
      ${renderTrustItem('Clear handoff', 'Accept, decline, reach the cat, or resolve with an explicit trail.')}
    </section>
  `
}

function renderSignedIn() {
  const email = state.session?.user?.email ?? ''
  const urgentAssignments = state.assignments.filter((assignment) =>
    ['pending', 'notified'].includes(assignment.assignment_state),
  )
  const acceptedAssignments = state.assignments.filter((assignment) =>
    assignment.assignment_state === 'accepted',
  )

  return `
    <section class="identity-card">
      <div>
        <p class="eyebrow">Signed in as</p>
        <strong>${escapeHtml(email)}</strong>
      </div>
      <button id="sign-out" class="secondary" ${state.busy ? 'disabled' : ''}>Sign out</button>
    </section>

    ${renderResponseSummary(urgentAssignments.length, acceptedAssignments.length)}

    <section class="grid">
      <section class="panel">
        <div class="section-title">
          <div>
            <p class="eyebrow">Invitations</p>
            <h2>Join a cat's care circle</h2>
          </div>
          <button id="refresh" class="secondary compact" ${state.busy ? 'disabled' : ''}>Refresh</button>
        </div>
        <div class="list">
          ${
            state.invitations.length
              ? state.invitations.map(renderInvitation).join('')
              : renderEmptyState('No pending invitations', 'When a caregiver invites this email, the request will appear here with the cat and relationship.')
          }
        </div>
      </section>

      <section class="panel">
        <div class="section-title">
          <div>
            <p class="eyebrow">Incidents</p>
            <h2>Respond when care may be interrupted</h2>
          </div>
        </div>
        <div class="list">
          ${
            state.assignments.length
              ? state.assignments.map(renderAssignment).join('')
              : renderEmptyState('No active incident assignments', 'If a caregiver misses a check-in and you are selected to help, the request will appear here.')
          }
        </div>
      </section>
    </section>
  `
}

function renderResponseSummary(urgentCount, acceptedCount) {
  const title = urgentCount
    ? `${urgentCount} response${urgentCount === 1 ? '' : 's'} need attention`
    : acceptedCount
      ? 'You are covering an active incident'
      : 'Nothing needs action right now'
  const body = urgentCount
    ? 'Review the deadline, accept only if you can act, or decline so the plan can move on.'
    : acceptedCount
      ? 'Use the authorized instructions, confirm the cat is reached, then resolve the handoff.'
      : 'You are signed in and ready if a trusted caregiver needs you.'

  return `
    <section class="response-summary ${urgentCount ? 'urgent' : ''}">
      <div>
        <p class="eyebrow">Current status</p>
        <h2>${escapeHtml(title)}</h2>
        <p>${escapeHtml(body)}</p>
      </div>
    </section>
  `
}

function renderInvitation(invitation) {
  const scopes = Array.isArray(invitation.proposed_scopes)
    ? invitation.proposed_scopes.map(humanize).join(', ')
    : 'Care instructions selected by the caregiver'
  const role = invitation.proposed_role ? humanize(invitation.proposed_role) : 'Trusted contact'

  return `
    <article class="care-card">
      <div class="card-head">
        <div class="cat-avatar">${escapeHtml(initialFor(invitation.cat_name))}</div>
        <div>
          <h3>${escapeHtml(invitation.cat_name)}</h3>
          <p>${escapeHtml(invitation.relationship_label)} invitation · ${escapeHtml(role)}</p>
        </div>
        ${renderPill(invitation.status)}
      </div>
      <div class="card-body">
        <p>You will join this cat's trusted circle with incident access limited to: ${escapeHtml(scopes)}.</p>
        <p class="meta">Expires ${escapeHtml(formatDateTime(invitation.expires_at))}</p>
      </div>
      <button data-accept="${escapeAttribute(invitation.id)}" ${state.busy ? 'disabled' : ''}>Accept invitation</button>
    </article>
  `
}

function renderAssignment(assignment) {
  const careCore = state.careCoreByIncident[assignment.incident_id]
  const accepted = assignment.assignment_state === 'accepted'
  const catReached = Boolean(assignment.cat_reached_at)
  const acceptDisabled = state.busy || accepted
  const resolutionNote = state.resolutionNotes[assignment.assignment_id] ?? 'I confirmed care coverage and the cat has been reached.'

  return `
    <article class="care-card ${accepted ? 'accepted' : 'urgent'}">
      <div class="card-head">
        <div class="cat-avatar">${escapeHtml(initialFor(assignment.cat_name))}</div>
        <div>
          <h3>${escapeHtml(assignment.cat_name)}</h3>
          <p>${escapeHtml(assignment.relationship_label)} response request</p>
        </div>
        ${renderPill(assignment.assignment_state)}
      </div>
      <div class="deadline">
        <span>Response deadline</span>
        <strong>${escapeHtml(formatDateTime(assignment.response_deadline_at))}</strong>
      </div>
      <p class="meta">Incident state: ${escapeHtml(humanize(assignment.incident_state))}</p>
      ${accepted ? renderHandoffSteps(assignment, catReached) : ''}
      <button data-accept-assignment="${escapeAttribute(assignment.assignment_id)}" ${acceptDisabled ? 'disabled' : ''}>
        ${accepted ? 'Responsibility accepted' : 'Accept responsibility'}
      </button>
      ${accepted ? renderResolutionControls(assignment.assignment_id, resolutionNote, catReached) : ''}
      ${careCore ? renderCareCore(careCore) : ''}
    </article>
  `
}

function renderHandoffSteps(assignment, catReached) {
  return `
    <div class="handoff-steps">
      <div class="handoff-step done">
        <span></span>
        <p>Responsibility accepted</p>
      </div>
      <div class="handoff-step ${catReached ? 'done' : ''}">
        <span></span>
        <p>${catReached ? `Cat reached at ${escapeHtml(formatDateTime(assignment.cat_reached_at))}` : 'Cat not reached yet'}</p>
      </div>
      ${
        catReached
          ? ''
          : `<button class="secondary" data-cat-reached="${escapeAttribute(assignment.assignment_id)}" ${state.busy ? 'disabled' : ''}>Confirm cat reached</button>`
      }
    </div>
  `
}

function renderResolutionControls(assignmentId, resolutionNote, catReached) {
  return `
    <div class="resolution">
      <label>
        Handoff note
        <textarea data-resolution-note="${escapeAttribute(assignmentId)}" rows="3">${escapeHtml(resolutionNote)}</textarea>
      </label>
      <button class="secondary" data-resolve-assignment="${escapeAttribute(assignmentId)}" ${state.busy || !catReached ? 'disabled' : ''}>
        Resolve handoff
      </button>
    </div>
  `
}

function renderCareCore(careCore) {
  const content = careCore.content_json ?? {}
  return `
    <div class="care-core">
      <div class="section-title tight">
        <div>
          <p class="eyebrow">Authorized Capsule</p>
          <h3>Core care</h3>
        </div>
        ${renderPill('CARE_CORE')}
      </div>
      <dl>
        <dt>Food and water</dt>
        <dd>${escapeHtml(content.feeding_and_water || 'Not set')}</dd>
        <dt>Hiding places and approach</dt>
        <dd>${escapeHtml(content.hiding_places || 'Not set')}</dd>
        <dt>Never do this</dt>
        <dd>${escapeHtml(content.do_not_do || 'Not set')}</dd>
      </dl>
    </div>
  `
}

function renderTrustItem(title, body) {
  return `
    <article>
      <strong>${escapeHtml(title)}</strong>
      <p>${escapeHtml(body)}</p>
    </article>
  `
}

function renderEmptyState(title, body) {
  return `
    <div class="empty-state">
      <strong>${escapeHtml(title)}</strong>
      <p>${escapeHtml(body)}</p>
    </div>
  `
}

function renderPill(value) {
  const text = humanize(value)
  return `<span class="pill">${escapeHtml(text)}</span>`
}

function bindEvents() {
  document.querySelector('#email')?.addEventListener('input', (event) => {
    state.email = event.target.value.trim()
  })

  document.querySelector('#password')?.addEventListener('input', (event) => {
    state.password = event.target.value
  })

  document.querySelector('#sign-in')?.addEventListener('click', signIn)
  document.querySelector('#sign-up')?.addEventListener('click', signUp)
  document.querySelector('#sign-out')?.addEventListener('click', signOut)
  document.querySelector('#refresh')?.addEventListener('click', loadPortalData)

  document.querySelectorAll('[data-accept]').forEach((button) => {
    button.addEventListener('click', () => {
      acceptInvitation(button.dataset.accept)
    })
  })

  document.querySelectorAll('[data-accept-assignment]').forEach((button) => {
    button.addEventListener('click', () => {
      acceptIncidentAssignment(button.dataset.acceptAssignment)
    })
  })

  document.querySelectorAll('[data-resolve-assignment]').forEach((button) => {
    button.addEventListener('click', () => {
      resolveIncidentAssignment(button.dataset.resolveAssignment)
    })
  })

  document.querySelectorAll('[data-cat-reached]').forEach((button) => {
    button.addEventListener('click', () => {
      recordCatReached(button.dataset.catReached)
    })
  })

  document.querySelectorAll('[data-resolution-note]').forEach((textarea) => {
    textarea.addEventListener('input', () => {
      state.resolutionNotes = {
        ...state.resolutionNotes,
        [textarea.dataset.resolutionNote]: textarea.value,
      }
    })
  })
}

async function signIn() {
  await withBusy('Signing in...', async () => {
    const { error } = await supabase.auth.signInWithPassword({
      email: state.email,
      password: state.password,
    })

    if (error) throw error

    setStatus('success', 'Signed in. Checking invitations and incidents.')
    await loadPortalData()
  })
}

async function signUp() {
  await withBusy('Creating account...', async () => {
    const { error } = await supabase.auth.signUp({
      email: state.email,
      password: state.password,
      options: {
        emailRedirectTo: window.location.origin,
      },
    })

    if (error) throw error

    setStatus('success', 'Account created. Verify the email before accepting private care access.')
  })
}

async function signOut() {
  await withBusy('Signing out...', async () => {
    const { error } = await supabase.auth.signOut()
    if (error) throw error
    state.session = null
    state.invitations = []
    state.assignments = []
    state.careCoreByIncident = {}
    state.resolutionNotes = {}
  })
}

async function loadPortalData() {
  await withBusy('Refreshing your Care Circle...', async () => {
    await loadInvitationsOnly()
    await loadAssignmentsOnly()

    if (state.assignments.length) {
      setStatus('info', `${state.assignments.length} incident assignment(s) available.`)
    } else if (state.invitations.length) {
      setStatus('info', `${state.invitations.length} invitation(s) awaiting your decision.`)
    } else {
      setStatus('success', 'No action needed right now.')
    }
  })
}

async function loadInvitationsOnly() {
  const { data, error } = await supabase.rpc('list_my_invitation_records')
  if (error) throw error
  state.invitations = data ?? []
}

async function loadAssignmentsOnly() {
  const { data, error } = await supabase.rpc('list_my_incident_assignments')
  if (error) throw error
  state.assignments = data ?? []
}

async function acceptInvitation(invitationId) {
  await withBusy('Accepting invitation...', async () => {
    const { error } = await supabase.rpc('accept_invitation_record', {
      p_invitation_id: invitationId,
    })

    if (error) throw error

    setStatus('success', 'Invitation accepted. You are now part of this Care Circle.')
    await loadInvitationsOnly()
    await loadAssignmentsOnly()
  })
}

async function acceptIncidentAssignment(assignmentId) {
  await withBusy('Accepting responsibility...', async () => {
    const { data, error } = await supabase.rpc('accept_incident_assignment', {
      p_assignment_id: assignmentId,
    })

    if (error) throw error

    const accepted = data?.[0]
    if (accepted?.incident_id) {
      await loadIncidentCareCore(accepted.incident_id)
    }

    await loadAssignmentsOnly()
    setStatus('success', 'Responsibility accepted. Authorized care instructions are available.')
  })
}

async function recordCatReached(assignmentId) {
  await withBusy('Confirming the cat has been reached...', async () => {
    const { error } = await supabase.rpc('record_cat_reached', {
      p_assignment_id: assignmentId,
    })

    if (error) throw error

    await loadAssignmentsOnly()
    setStatus('success', 'Cat reached. You can resolve the handoff when care is covered.')
  })
}

async function resolveIncidentAssignment(assignmentId) {
  const resolutionNote = state.resolutionNotes[assignmentId] ?? ''

  await withBusy('Resolving handoff...', async () => {
    const { error } = await supabase.rpc('resolve_incident_assignment', {
      p_assignment_id: assignmentId,
      p_resolution_note: resolutionNote,
    })

    if (error) throw error

    await loadAssignmentsOnly()
    const remainingAssignmentIds = new Set(state.assignments.map((assignment) => assignment.assignment_id))
    state.careCoreByIncident = Object.fromEntries(
      Object.entries(state.careCoreByIncident).filter(([incidentId]) =>
        state.assignments.some((assignment) => assignment.incident_id === incidentId),
      ),
    )
    state.resolutionNotes = Object.fromEntries(
      Object.entries(state.resolutionNotes).filter(([noteAssignmentId]) =>
        remainingAssignmentIds.has(noteAssignmentId),
      ),
    )
    setStatus('success', 'Handoff resolved. Temporary care access has been revoked.')
  })
}

async function loadIncidentCareCore(incidentId) {
  const { data, error } = await supabase.rpc('list_my_incident_care_core', {
    p_incident_id: incidentId,
  })

  if (error) throw error

  const careCore = data?.[0]
  if (careCore) {
    state.careCoreByIncident = {
      ...state.careCoreByIncident,
      [incidentId]: careCore,
    }
  }
}

async function withBusy(message, action) {
  state.busy = true
  setStatus('info', message)
  render()

  try {
    await action()
  } catch (error) {
    setStatus('error', error.message || 'Request failed.')
  } finally {
    state.busy = false
    render()
  }
}

function setStatus(tone, message) {
  state.status = { tone, message }
}

function escapeHtml(value) {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;')
}

function escapeAttribute(value) {
  return escapeHtml(value).replaceAll('`', '&#096;')
}

function formatDateTime(value) {
  if (!value) return 'Not set'
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

function humanize(value) {
  return String(value || '')
    .toLowerCase()
    .split('_')
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ')
}

function initialFor(value) {
  return String(value || 'C').trim().charAt(0).toUpperCase()
}

initialize()
