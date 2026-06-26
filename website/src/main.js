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
      loadInvitations()
    } else {
      state.invitations = []
      state.status = 'Signed out.'
      render()
    }
  })

  if (state.session) {
    await loadInvitations()
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
  `
}

function renderInvitation(invitation) {
  return `
    <article class="invite">
      <div>
        <strong>${escapeHtml(invitation.cat_name)}</strong>
        <p>${escapeHtml(invitation.relationship_label)} · ${escapeHtml(invitation.status)}</p>
      </div>
      <button data-accept="${escapeAttribute(invitation.id)}" ${state.busy ? 'disabled' : ''}>Accept</button>
    </article>
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
  document.querySelector('#refresh')?.addEventListener('click', loadInvitations)

  document.querySelectorAll('[data-accept]').forEach((button) => {
    button.addEventListener('click', () => {
      acceptInvitation(button.dataset.accept)
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
    await loadInvitations()
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
  })
}

async function loadInvitations() {
  await withBusy('Loading invitations...', async () => {
    const { data, error } = await supabase.rpc('list_my_invitation_records')
    if (error) throw error
    state.invitations = data ?? []
    state.status = state.invitations.length
      ? 'Pending invitations loaded.'
      : 'No pending invitations for this email.'
  })
}

async function acceptInvitation(invitationId) {
  await withBusy('Accepting invitation...', async () => {
    const { error } = await supabase.rpc('accept_invitation_record', {
      p_invitation_id: invitationId,
    })

    if (error) throw error

    state.status = 'Invitation accepted.'
    await loadInvitations()
  })
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

initialize()
