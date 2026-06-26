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
  status: 'Checking session...',
  busy: false,
}

async function initialize() {
  const { data, error } = await supabase.auth.getSession()
  if (error) {
    state.status = error.message
  } else {
    state.session = data.session
    state.status = data.session
      ? 'Signed in.'
      : 'Sign in with the invited email to view Care Circle invitations.'
  }

  supabase.auth.onAuthStateChange((_event, session) => {
    state.session = session
    if (session) {
      loadPortalData()
    } else {
      state.invitations = []
      state.assignments = []
      state.careCoreByIncident = {}
      state.status = 'Signed out.'
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
    <section class="shell">
      <header class="header">
        <p class="eyebrow">Care Circle Portal</p>
        <h1>The Third Bowl</h1>
        <p>Accept a real invitation with the exact email that was invited.</p>
      </header>

      ${state.session ? renderSignedIn() : renderSignedOut()}

      <section class="status" aria-live="polite">
        ${state.busy ? '<span class="spinner"></span>' : ''}
        <span>${escapeHtml(state.status)}</span>
      </section>
    </section>
  `

  bindEvents()
}

function renderSignedOut() {
  return `
    <section class="panel">
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
        <button id="sign-up" class="secondary" ${state.busy ? 'disabled' : ''}>Sign up</button>
      </div>
    </section>
  `
}

function renderSignedIn() {
  const email = state.session?.user?.email ?? ''
  const invitations = state.invitations.length
    ? state.invitations.map(renderInvitation).join('')
    : '<p class="empty">No pending invitations for this email.</p>'
  const assignments = state.assignments.length
    ? state.assignments.map(renderAssignment).join('')
    : '<p class="empty">No active incident assignments for this email.</p>'

  return `
    <section class="panel">
      <div class="identity">
        <span>${escapeHtml(email)}</span>
        <button id="sign-out" class="secondary" ${state.busy ? 'disabled' : ''}>Sign out</button>
      </div>
    </section>

    <section class="panel">
      <div class="section-title">
        <h2>Pending Invitations</h2>
        <button id="refresh" class="secondary" ${state.busy ? 'disabled' : ''}>Refresh</button>
      </div>
      <div class="list">${invitations}</div>
    </section>

    <section class="panel">
      <div class="section-title">
        <h2>Incident Assignments</h2>
      </div>
      <p class="note">Assignments appear after the caregiver activates a missed check-in. Email delivery is not connected yet.</p>
      <div class="list">${assignments}</div>
    </section>
  `
}

function renderInvitation(invitation) {
  return `
    <article class="invite">
      <div>
        <strong>${escapeHtml(invitation.cat_name)}</strong>
        <p>${escapeHtml(invitation.relationship_label)} | ${escapeHtml(invitation.status)}</p>
      </div>
      <button data-accept="${escapeAttribute(invitation.id)}" ${state.busy ? 'disabled' : ''}>Accept</button>
    </article>
  `
}

function renderAssignment(assignment) {
  const careCore = state.careCoreByIncident[assignment.incident_id]
  const accepted = assignment.assignment_state === 'accepted'
  const acceptDisabled = state.busy || accepted

  return `
    <article class="invite">
      <div>
        <strong>${escapeHtml(assignment.cat_name)}</strong>
        <p>${escapeHtml(assignment.relationship_label)} | incident ${escapeHtml(assignment.incident_state)} | response ${escapeHtml(assignment.assignment_state)}</p>
        <p>Deadline: ${escapeHtml(formatDateTime(assignment.response_deadline_at))}</p>
      </div>
      <button data-accept-assignment="${escapeAttribute(assignment.assignment_id)}" ${acceptDisabled ? 'disabled' : ''}>
        ${accepted ? 'Accepted' : 'Accept incident'}
      </button>
      ${accepted ? `<button class="secondary" data-resolve-assignment="${escapeAttribute(assignment.assignment_id)}" ${state.busy ? 'disabled' : ''}>Resolve incident</button>` : ''}
      ${careCore ? renderCareCore(careCore) : ''}
    </article>
  `
}

function renderCareCore(careCore) {
  const content = careCore.content_json ?? {}
  return `
    <div class="care-core">
      <h3>Care Core</h3>
      <dl>
        <dt>Feeding and water</dt>
        <dd>${escapeHtml(content.feeding_and_water || 'Not set')}</dd>
        <dt>Hiding places</dt>
        <dd>${escapeHtml(content.hiding_places || 'Not set')}</dd>
        <dt>Do not do</dt>
        <dd>${escapeHtml(content.do_not_do || 'Not set')}</dd>
      </dl>
    </div>
  `
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
}

async function signIn() {
  await withBusy('Signing in...', async () => {
    const { error } = await supabase.auth.signInWithPassword({
      email: state.email,
      password: state.password,
    })

    if (error) throw error

    state.status = 'Signed in.'
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

    state.status = 'Account created. Verify the email before accepting invitations.'
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
  })
}

async function loadPortalData() {
  await withBusy('Loading portal records...', async () => {
    await loadInvitationsOnly()
    await loadAssignmentsOnly()
    state.status = [
      state.invitations.length ? `${state.invitations.length} pending invitation(s)` : 'no pending invitations',
      state.assignments.length ? `${state.assignments.length} incident assignment(s)` : 'no incident assignments',
    ].join('; ')
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

    state.status = 'Invitation accepted.'
    await loadInvitationsOnly()
    await loadAssignmentsOnly()
  })
}

async function acceptIncidentAssignment(assignmentId) {
  await withBusy('Accepting incident assignment...', async () => {
    const { data, error } = await supabase.rpc('accept_incident_assignment', {
      p_assignment_id: assignmentId,
    })

    if (error) throw error

    const accepted = data?.[0]
    if (accepted?.incident_id) {
      await loadIncidentCareCore(accepted.incident_id)
    }

    await loadAssignmentsOnly()
    state.status = 'Incident accepted. CARE_CORE grant loaded.'
  })
}

async function resolveIncidentAssignment(assignmentId) {
  const resolutionNote = window.prompt('Resolution note (optional)', 'Responder confirmed care coverage.')
  if (resolutionNote === null) return

  await withBusy('Resolving incident...', async () => {
    const { error } = await supabase.rpc('resolve_incident_assignment', {
      p_assignment_id: assignmentId,
      p_resolution_note: resolutionNote,
    })

    if (error) throw error

    await loadAssignmentsOnly()
    state.careCoreByIncident = Object.fromEntries(
      Object.entries(state.careCoreByIncident).filter(([incidentId]) =>
        state.assignments.some((assignment) => assignment.incident_id === incidentId),
      ),
    )
    state.status = 'Incident resolved. Active data grant revoked.'
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

async function withBusy(status, action) {
  state.busy = true
  state.status = status
  render()

  try {
    await action()
  } catch (error) {
    state.status = error.message || 'Request failed.'
  } finally {
    state.busy = false
    render()
  }
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

initialize()
