import { createClient } from '@supabase/supabase-js'
import './styles.css'

const SUPABASE_URL = 'https://iykvrljnzoaymkfkiibb.supabase.co'
const SUPABASE_PUBLISHABLE_KEY = 'sb_publishable_v2eykawiui-RRyyjoXp_WA_16HHfgFx'

const supabase = createClient(SUPABASE_URL, SUPABASE_PUBLISHABLE_KEY, {
  auth: {
    autoRefreshToken: true,
    persistSession: false,
    detectSessionInUrl: true,
  },
})

const app = document.querySelector('#app')

const CAT_SPRITES = Object.freeze({
  alert: '/assets/png/alert.png',
  cute: '/assets/png/cute.png',
  dizzy: '/assets/png/dizzy.png',
  guard: '/assets/png/guard.png',
  happy: '/assets/png/happy.png',
  key: '/assets/png/key.png',
  medic: '/assets/png/medic.png',
  neutral: '/assets/png/neutral.png',
  sleepy: '/assets/png/sleepy.png',
})

const CAT_SPRITE_ALTS = Object.freeze({
  alert: 'Incident warning cat',
  cute: 'Public welcome cat',
  dizzy: 'Loading state cat',
  guard: 'Human handoff responder cat',
  happy: 'Coverage confirmed cat',
  key: 'Home access helper cat',
  medic: 'Medical context helper cat',
  neutral: 'Neutral profile state cat',
  sleepy: 'Pending ritual cat',
})

let state = {
  email: '',
  password: '',
  passwordVisible: false,
  authMode: 'signUp',
  authNotice: null,
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
  signingOut: false,
}

function renderCatSprite(name, className = '', alt = '', decorative = false) {
  const src = CAT_SPRITES[name] ?? CAT_SPRITES.neutral
  const classes = ['cat-sprite', className].filter(Boolean).join(' ')
  const accessibility = decorative
    ? 'alt="" aria-hidden="true"'
    : `alt="${escapeAttribute(alt || CAT_SPRITE_ALTS[name] || `${humanize(name)} state cat`)}"`

  return `<img class="${escapeAttribute(classes)}" src="${escapeAttribute(src)}" ${accessibility} loading="eager" decoding="async" />`
}

function currentRoute() {
  return window.location.hash.startsWith('#/portal') || isPortalAuthReturn() ? 'portal' : 'landing'
}

function initialize() {
  window.addEventListener('hashchange', handleRouteChange)
  supabase.auth.onAuthStateChange(handleAuthStateChange)

  applyUrlContext()
  render()
  scrollToLandingTarget()
  hydrateSession()
}

async function hydrateSession() {
  applyUrlContext()
  const { data, error } = await supabase.auth.getSession()
  if (error) {
    setStatus('error', error.message)
  } else {
    state.session = data.session
    if (state.authNotice) {
      setStatus(state.authNotice.tone, state.authNotice.message)
    } else {
      setStatus(
        'info',
        data.session
          ? 'Your Care Circle workspace is ready.'
          : 'Sign in with the invited email to see what needs your attention.',
      )
    }
  }

  if (currentRoute() === 'portal' && state.session) {
    await loadPortalData()
    applyAuthNotice()
    cleanAuthReturnUrl()
  } else {
    render()
    cleanAuthReturnUrl()
  }

  scrollToLandingTarget()
}

async function handleAuthStateChange(_event, session) {
  applyUrlContext()
  state.session = session
  if (session) {
    if (currentRoute() === 'portal') {
      await loadPortalData()
      applyAuthNotice()
      cleanAuthReturnUrl()
    } else {
      render()
    }
  } else {
    state.invitations = []
    state.assignments = []
    state.careCoreByIncident = {}
    state.resolutionNotes = {}
    if (state.authNotice) {
      setStatus(state.authNotice.tone, state.authNotice.message)
    } else {
      setStatus('info', 'Signed out.')
    }
    render()
  }
}

async function handleRouteChange() {
  applyUrlContext()
  if (currentRoute() === 'portal' && state.session) {
    await loadPortalData()
    applyAuthNotice()
  } else {
    render()
  }

  scrollToLandingTarget()
}

function applyUrlContext() {
  const email = urlEmail()
  if (email && isValidEmail(email) && !state.email) {
    state.email = email
  }

  const authError = authErrorFromUrl()
  if (authError) {
    state.authNotice = { tone: 'error', message: authError }
    setStatus('error', authError)
    return
  }

  if (isConfirmedAuthReturn()) {
    const targetEmail = email || state.email
    state.authNotice = {
      tone: 'success',
      message: targetEmail
        ? `Email confirmed for ${targetEmail}. Sign in with that email to accept private care access.`
        : 'Email confirmed. Sign in to accept private care access.',
    }
    setStatus(state.authNotice.tone, state.authNotice.message)
  }
}

function applyAuthNotice() {
  if (state.authNotice) {
    setStatus(state.authNotice.tone, state.authNotice.message)
    render()
  }
}

function isPortalAuthReturn() {
  const params = new URLSearchParams(window.location.search)
  return params.get('auth') === 'confirmed' || params.get('portal') === '1'
}

function isConfirmedAuthReturn() {
  const params = new URLSearchParams(window.location.search)
  if (params.get('auth') === 'confirmed') return true

  const hashParams = currentHashParams()
  return hashParams.get('confirmed') === '1'
}

function authErrorFromUrl() {
  const hashParams = new URLSearchParams(window.location.hash.replace(/^#/, ''))
  const errorDescription = hashParams.get('error_description')
  if (errorDescription) return errorDescription.replace(/\+/g, ' ')

  const error = hashParams.get('error')
  return error ? `Authentication failed: ${error}` : ''
}

function urlEmail() {
  const params = new URLSearchParams(window.location.search)
  const searchEmail = params.get('email')
  if (searchEmail) return searchEmail.trim().toLowerCase()

  const hashEmail = currentHashParams().get('email')
  return hashEmail ? hashEmail.trim().toLowerCase() : ''
}

function currentHashParams() {
  const hash = window.location.hash || ''
  const queryIndex = hash.indexOf('?')
  if (queryIndex < 0) return new URLSearchParams()
  return new URLSearchParams(hash.slice(queryIndex + 1))
}

function portalUrlForEmail(email, params = {}) {
  const query = new URLSearchParams(params)
  if (email) query.set('email', email.trim().toLowerCase())
  const suffix = query.toString()
  return `${window.location.origin}${window.location.pathname}${suffix ? `?${suffix}` : ''}`
}

function cleanAuthReturnUrl() {
  if (!isPortalAuthReturn()) return

  const query = new URLSearchParams()
  const email = urlEmail() || state.email
  if (email) query.set('email', email)
  query.set('confirmed', '1')
  window.history.replaceState(null, '', `${window.location.pathname}#/portal?${query.toString()}`)
}

function scrollToLandingTarget() {
  if (currentRoute() === 'portal') return

  const id = window.location.hash.slice(1)
  if (!id || id.startsWith('/')) return

  window.requestAnimationFrame(() => {
    document.getElementById(id)?.scrollIntoView({ block: 'start' })
  })
}

function render() {
  if (currentRoute() === 'portal') {
    renderPortal()
    return
  }

  renderLanding()
}

function renderLanding() {
  app.innerHTML = `
    <main class="landing">
      ${renderLandingNav()}
      ${renderLandingHero()}
      ${renderProofStrip()}
      ${renderProblemSection()}
      ${renderMechanismSection()}
      ${renderScenarioSection()}
      ${renderTrustSection()}
      ${renderBuildProofSection()}
      ${renderPortalHandoff()}
      ${renderLandingFooter()}
    </main>
  `
}

function renderLandingNav() {
  return `
    <header class="site-nav" aria-label="Public site">
      <a class="brand-link" href="#" aria-label="The Third Bowl home">
        ${renderCatSprite('neutral', 'sprite-badge brand-bowl', 'The Third Bowl neutral cat logo')}
        <span>The Third Bowl</span>
      </a>
      <nav class="nav-links" aria-label="Landing sections">
        <a href="#how-it-works">How it works</a>
        <a href="#trust">Trust</a>
        <a class="nav-portal" href="#/portal">Portal</a>
      </nav>
    </header>
  `
}

function renderLandingHero() {
  return `
    <section class="landing-hero" aria-labelledby="hero-title">
      <div class="hero-copy-block">
        <p class="landing-eyebrow">Emergency continuity for cats</p>
        <h1 id="hero-title">The Third Bowl</h1>
        <p class="hero-lede">
          The plan nobody sees until it matters: a cat's fragile care routine made
          ready for the moment its human cannot come home.
        </p>
        <div class="hero-actions" aria-label="Primary actions">
          <a class="button primary" href="#/portal">Open Care Circle Portal</a>
          <a class="button secondary" href="#how-it-works">See how it works</a>
        </div>
      </div>
      <figure class="hero-art">
        <img src="/assets/hero-doodle.png" alt="Hand drawn cats gathered around a bowl" />
        <figcaption>
          Food is the first bowl. Water is the second. Continuity is the third.
        </figcaption>
      </figure>
    </section>
  `
}

function renderProofStrip() {
  return `
    <section class="proof-strip" aria-label="Product principles">
      ${renderProofItem('Private by default', 'Care details stay behind authentication and authorization.', 'neutral')}
      ${renderProofItem('Scoped release', 'Trusted people see only the sections they were granted.', 'key')}
      ${renderProofItem('Human handoff', 'A notification is not enough; someone must accept responsibility.', 'guard')}
      <img class="divider-doodles" src="/assets/divider-doodles.png" alt="" aria-hidden="true" />
    </section>
  `
}

function renderProofItem(title, body, sprite) {
  return `
    <article class="proof-item">
      ${renderCatSprite(sprite, 'sprite-badge proof-sprite', `${title} cat`)}
      <strong>${title}</strong>
      <p>${body}</p>
    </article>
  `
}

function renderProblemSection() {
  const fragments = [
    ['Food rhythm', 'What, when, and how the cat actually eats.', 'happy'],
    ['Hiding places', 'Where they disappear when a stranger enters.', 'sleepy'],
    ['Medication', 'Dose, timing, method, and what must not be guessed.', 'medic'],
    ['Home access', 'Who can enter, where supplies are, and what to avoid.', 'key'],
    ['Urgent signs', 'Which symptoms are normal for this cat and which are not.', 'alert'],
    ['The carrier', 'The one object that matters when care has to move.', 'guard'],
  ]

  return `
    <section class="section-band problem-band" aria-labelledby="problem-title">
      <div class="section-intro">
        <p class="landing-eyebrow">The gap</p>
        <h2 id="problem-title">Cats look independent until one person's memory disappears.</h2>
        <p>
          A phone contact is not a care plan. The useful knowledge is specific, practical,
          and often invisible to everyone except the caregiver.
        </p>
      </div>
      <div class="knowledge-grid">
        ${fragments.map(([title, body, sprite]) => `
          <article class="knowledge-tile">
            ${renderCatSprite(sprite, 'sprite-badge tile-sprite', `${title} cat`)}
            <h3>${title}</h3>
            <p>${body}</p>
          </article>
        `).join('')}
      </div>
    </section>
  `
}

function renderMechanismSection() {
  const mechanisms = [
    ['Capsule', 'Structured care knowledge: food, hiding places, handling, warnings, supplies, and contacts.', 'neutral'],
    ['Ritual', 'A recurring check-in that asks one narrow question: are you still available to carry the routine?', 'sleepy'],
    ['Release', 'Temporary disclosure by scope, so the right person sees the right instructions only when needed.', 'key'],
    ['Response', 'A verified trusted person accepts, declines, reaches the cat, or resolves the handoff with an audit trail.', 'guard'],
  ]

  return `
    <section id="how-it-works" class="section-band mechanism-band" aria-labelledby="mechanism-title">
      <div class="section-intro compact">
        <p class="landing-eyebrow">How it works</p>
        <h2 id="mechanism-title">Four parts, one continuity path.</h2>
      </div>
      <div class="mechanism-grid">
        ${mechanisms.map(([title, body, sprite], index) => `
          <article class="mechanism-panel">
            <div class="mechanism-head">
              <span class="step-number">0${index + 1}</span>
              ${renderCatSprite(sprite, 'sprite-badge mechanism-sprite', `${title} cat`)}
            </div>
            <h3>${title}</h3>
            <p>${body}</p>
          </article>
        `).join('')}
      </div>
    </section>
  `
}

function renderScenarioSection() {
  const steps = [
    ["Mara prepares August's Capsule", 'Food, medication, hiding place, carrier, vet, and the details only Mara knows.'],
    ['Her sister and neighbor verify', 'Each person joins with their own email and receives only the planned access scopes.'],
    ['A check-in is missed', 'The plan waits through the real grace period instead of relying on a client-side timer.'],
    ['A responder accepts', 'The neighbor signs in, receives permitted instructions, reaches August, and resolves the incident.'],
  ]

  return `
    <section class="section-band scenario-band" aria-labelledby="scenario-title">
      <div class="scenario-copy">
        <p class="landing-eyebrow">A real-world moment</p>
        <h2 id="scenario-title">When the routine breaks, the handoff should not.</h2>
        <p>
          The landing is public, but the product is built around a private operational flow:
          verified people, scoped information, explicit responsibility, and revocation when the incident ends.
        </p>
      </div>
      <div class="scenario-timeline" aria-label="Example continuity timeline">
        ${steps.map(([title, body]) => `
          <article class="timeline-row">
            <span aria-hidden="true"></span>
            <div>
              <h3>${title}</h3>
              <p>${body}</p>
            </div>
          </article>
        `).join('')}
      </div>
    </section>
  `
}

function renderTrustSection() {
  return `
    <section id="trust" class="section-band trust-band" aria-labelledby="trust-title">
      <div class="section-intro">
        <p class="landing-eyebrow">Trust model</p>
        <h2 id="trust-title">Sensitive care instructions should never travel as a loose link.</h2>
        <p>
          The Third Bowl treats addresses, medication, and entry instructions as private data.
          Trust is tied to verified people, active context, and limited access.
        </p>
      </div>
      <div class="trust-layout">
        <div class="trust-list" role="list">
          ${renderTrustRow('Verified humans', 'The invited email must authenticate before any private details are shown.', 'guard')}
          ${renderTrustRow('Minimum necessary disclosure', 'A neighbor, guardian, or medical contact can receive different sections.', 'key')}
          ${renderTrustRow('Temporary grants', 'Incident access is meant to end when the handoff is resolved.', 'sleepy')}
          ${renderTrustRow('Auditable actions', 'Invitations, acceptance, grants, and resolution belong in the history.', 'happy')}
        </div>
        <figure class="face-sheet">
          <div class="expression-grid" aria-label="Final cat expression set">
            ${['neutral', 'happy', 'guard', 'key', 'medic', 'alert', 'sleepy', 'dizzy', 'cute']
              .map((sprite) => renderCatSprite(sprite, 'expression-sprite'))
              .join('')}
          </div>
          <figcaption>Final expression set mapped to product states: care, access, medical context, alerts and loading.</figcaption>
        </figure>
      </div>
    </section>
  `
}

function renderTrustRow(title, body, sprite) {
  return `
    <article class="trust-row" role="listitem">
      ${renderCatSprite(sprite, 'sprite-badge trust-sprite', `${title} cat`)}
      <div>
        <h3>${title}</h3>
        <p>${body}</p>
      </div>
    </article>
  `
}

function renderBuildProofSection() {
  return `
    <section class="section-band proof-band" aria-labelledby="proof-title">
      <div class="section-intro compact">
        <p class="landing-eyebrow">Hackathon standard</p>
        <h2 id="proof-title">No fake safety theater.</h2>
        <p>
          The project standard is simple: if a behavior is shown as functional, it should run
          through the real product path. No fake incidents, no public Capsule pages, and no
          placeholder controls pretending to protect a cat.
        </p>
      </div>
      <div class="standard-grid">
        <article>
          <strong>Real identity</strong>
          <p>Care Circle access starts with authentication and exact invited email ownership.</p>
        </article>
        <article>
          <strong>Real workflow</strong>
          <p>Check-ins and escalation are designed around backend state, not decorative timers.</p>
        </article>
        <article>
          <strong>Real limits</strong>
          <p>The public website explains the product without exposing private cat data.</p>
        </article>
      </div>
    </section>
  `
}

function renderPortalHandoff() {
  return `
    <section class="portal-handoff" aria-labelledby="portal-handoff-title">
      ${renderCatSprite('guard', 'sprite-badge handoff-sprite', 'Care Circle responder cat')}
      <div>
        <p class="landing-eyebrow">Care Circle Portal</p>
        <h2 id="portal-handoff-title">Invited to help a cat?</h2>
        <p>
          Sign in with the exact email the caregiver invited. A link alone is not enough
          to reveal private care details.
        </p>
      </div>
      <a class="button primary" href="#/portal">Open the portal</a>
    </section>
  `
}

function renderLandingFooter() {
  return `
    <footer class="landing-footer">
      <p>The Third Bowl is not a veterinary diagnosis tool or an emergency-services replacement.</p>
      <a href="#top" aria-label="Back to top">Back to top</a>
    </footer>
  `
}

function renderPortal() {
  app.innerHTML = `
    <main class="portal-shell">
      ${renderPortalHero()}
      ${state.session ? renderSignedIn() : renderSignedOut()}
      ${renderStatus()}
    </main>
  `

  bindEvents()
}

function renderPortalHero() {
  return `
    <header class="portal-hero">
      <a class="portal-back" href="#" aria-label="Back to public landing">Public landing</a>
      ${renderCatSprite('neutral', 'sprite-badge portal-mark', 'The Third Bowl neutral cat logo')}
      <div>
        <p class="eyebrow">Care Circle Portal</p>
        <h1>Private care access</h1>
        <p class="hero-copy">Sign in with the invited email to accept requests, respond to incidents, and view only authorized care details.</p>
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
  const isSignInMode = state.authMode === 'signIn'
  const emailReady = isValidEmail(state.email)
  const passwordProfile = passwordSecurityProfile(state.password)
  const signInDisabled = state.busy || !canSignIn()
  const signUpDisabled = state.busy || !canSignUp()

  return `
    <section class="auth-panel secure-auth">
      <div class="secure-auth-header">
        <span class="auth-guard-badge">
          ${renderCatSprite('guard', 'auth-guard', 'Protected portal guard cat')}
        </span>
        <div>
          <p class="eyebrow">Protected access</p>
          <h2>${isSignInMode ? 'Secure sign-in' : 'Create your account'}</h2>
          <p>${
            isSignInMode
              ? 'Use the account that owns the invited email before private care details are shown.'
              : 'Start with the exact email the caregiver trusted. A link alone is not enough.'
          }</p>
        </div>
      </div>

      <div class="security-grid" aria-label="Care Circle security protections">
        <div class="security-chip">
          <strong>Verified email</strong>
          <span>Matches the invite.</span>
        </div>
        <div class="security-chip">
          <strong>Scoped access</strong>
          <span>Only authorized notes.</span>
        </div>
        <div class="security-chip">
          <strong>Audit trail</strong>
          <span>Actions stay explicit.</span>
        </div>
      </div>

      <label>
        Email
        <input
          id="email"
          type="email"
          autocomplete="email"
          inputmode="email"
          aria-invalid="${state.email && !emailReady ? 'true' : 'false'}"
          value="${escapeAttribute(state.email)}"
        />
        <span id="email-help" class="field-help ${state.email && !emailReady ? 'field-help-error' : ''}">
          ${state.email && !emailReady ? 'Enter a complete email address.' : 'Use the same email that received the invitation.'}
        </span>
      </label>

      <label>
        Password
        <span class="password-shell">
          <input
            id="password"
            type="${state.passwordVisible ? 'text' : 'password'}"
            autocomplete="${isSignInMode ? 'current-password' : 'new-password'}"
            value="${escapeAttribute(state.password)}"
          />
          <button id="toggle-password" class="input-action" type="button" aria-pressed="${state.passwordVisible ? 'true' : 'false'}">
            ${state.passwordVisible ? 'Hide' : 'Show'}
          </button>
        </span>
      </label>

      ${!isSignInMode ? `
        <div class="password-strength" id="password-strength" data-tone="${escapeAttribute(passwordProfile.tone)}">
          <div class="password-strength-head">
            <span>Password strength</span>
            <strong id="password-strength-label">${escapeHtml(passwordProfile.label)}</strong>
          </div>
          <span class="password-meter" style="--strength: ${passwordProfile.percent}%"><span></span></span>
        </div>

        <div class="password-checks" id="password-checks">
          ${passwordProfile.checks.map(renderPasswordCheck).join('')}
        </div>

        <p class="auth-note">Only new accounts need to meet every protection above.</p>
      ` : ''}

      <div class="actions auth-actions">
        ${
          isSignInMode
            ? `<button id="sign-in" ${signInDisabled ? 'disabled' : ''}>Sign in</button>`
            : `<button id="sign-up" ${signUpDisabled ? 'disabled' : ''}>Create account</button>`
        }
      </div>

      ${
        isSignInMode
          ? `<p class="auth-mode-switch">Need an account? <button id="show-sign-up" type="button">Create account instead</button></p>`
          : `<p class="auth-mode-switch">Already have an account? <button id="show-sign-in" type="button">Sign in instead</button></p>`
      }
    </section>

    <section class="trust-strip">
      ${renderTrustItem('Private by default', 'Care instructions appear only after sign-in and authorization.', 'neutral')}
      ${renderTrustItem('Scoped access', 'You see the details needed for your role, not the entire Capsule.', 'key')}
      ${renderTrustItem('Clear handoff', 'Accept, decline, reach the cat, or resolve with an explicit trail.', 'guard')}
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
      <button id="sign-out" class="secondary" ${state.signingOut ? 'disabled' : ''}>${state.signingOut ? 'Signing out...' : 'Sign out'}</button>
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
  const sprite = urgentCount ? 'alert' : acceptedCount ? 'guard' : 'happy'

  return `
    <section class="response-summary ${urgentCount ? 'urgent' : ''}">
      ${renderCatSprite(sprite, 'sprite-badge summary-sprite', `${title} cat`)}
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
  const sprite = spriteForScopes(invitation.proposed_scopes)

  return `
    <article class="care-card">
      <div class="card-head">
        <div class="cat-avatar">${renderCatSprite(sprite, 'sprite-badge avatar-sprite', `${role} cat`)}</div>
        <div>
          <h3>${escapeHtml(invitation.cat_name)}</h3>
          <p>${escapeHtml(invitation.relationship_label)} invitation - ${escapeHtml(role)}</p>
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
  const capsuleSections = state.careCoreByIncident[assignment.incident_id]
  const accepted = assignment.assignment_state === 'accepted'
  const catReached = Boolean(assignment.cat_reached_at)
  const acceptDisabled = state.busy || accepted
  const resolutionNote = state.resolutionNotes[assignment.assignment_id] ?? 'I confirmed care coverage and the cat has been reached.'
  const sprite = catReached ? 'happy' : accepted ? 'guard' : 'alert'

  return `
    <article class="care-card ${accepted ? 'accepted' : 'urgent'}">
      <div class="card-head">
        <div class="cat-avatar">${renderCatSprite(sprite, 'sprite-badge avatar-sprite', `${assignment.assignment_state} incident cat`)}</div>
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
      ${capsuleSections ? renderAuthorizedCapsule(capsuleSections) : ''}
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

function renderAuthorizedCapsule(sections) {
  const orderedSections = Array.isArray(sections) ? sections : [sections]
  return `
    <div class="care-core">
      <div class="section-title tight">
        <div>
          <p class="eyebrow">Authorized Capsule</p>
          <h3>Visible care details</h3>
        </div>
        ${renderPill(`${orderedSections.length} section${orderedSections.length === 1 ? '' : 's'}`)}
      </div>
      ${orderedSections.map(renderCapsuleSection).join('')}
    </div>
  `
}

function renderCapsuleSection(section) {
  const content = section.content_json ?? {}

  if (section.scope === 'HOME_ACCESS') {
    return renderDefinitionSection('Home access', section.scope, [
      ['Entry instructions', content.entry_instructions],
      ['Key or access location', content.key_location],
      ['Safe room or home hazards', content.safe_room],
    ])
  }

  if (section.scope === 'MEDICAL') {
    return renderDefinitionSection('Medical', section.scope, [
      ['Medication and dosing', content.medications],
      ['Vet and insurance context', content.vet_info],
      ['Medical warnings', content.medical_warnings],
    ])
  }

  return renderDefinitionSection('Core care', section.scope, [
    ['Food and water', content.feeding_and_water],
    ['Hiding places and approach', content.hiding_places],
    ['Never do this', content.do_not_do],
  ])
}

function renderDefinitionSection(title, scope, rows) {
  return `
    <section class="capsule-section">
      <div class="section-title tight">
        <div class="capsule-title">
          ${renderCatSprite(spriteForScopes([scope]), 'sprite-badge capsule-sprite', `${title} cat`)}
          <h4>${escapeHtml(title)}</h4>
        </div>
        ${renderPill(scope)}
      </div>
      <dl>
        ${rows.map(([label, value]) => `
          <dt>${escapeHtml(label)}</dt>
          <dd>${escapeHtml(value || 'Not set')}</dd>
        `).join('')}
      </dl>
    </section>
  `
}

function renderTrustItem(title, body, sprite = 'neutral') {
  return `
    <article>
      ${renderCatSprite(sprite, 'sprite-badge trust-item-sprite', `${title} cat`)}
      <strong>${escapeHtml(title)}</strong>
      <p>${escapeHtml(body)}</p>
    </article>
  `
}

function renderEmptyState(title, body) {
  return `
    <div class="empty-state">
      ${renderCatSprite('neutral', 'sprite-badge empty-sprite', '', true)}
      <strong>${escapeHtml(title)}</strong>
      <p>${escapeHtml(body)}</p>
    </div>
  `
}

function renderPill(value) {
  const text = humanize(value)
  return `<span class="pill">${escapeHtml(text)}</span>`
}

function spriteForScopes(scopes) {
  const normalized = Array.isArray(scopes) ? scopes.map((scope) => String(scope).toUpperCase()) : []

  if (normalized.includes('MEDICAL')) return 'medic'
  if (normalized.includes('HOME_ACCESS')) return 'key'
  return 'guard'
}

function renderPasswordCheck(check) {
  return `
    <span class="password-check ${check.met ? 'met' : ''}" data-password-check="${escapeAttribute(check.id)}">
      <span aria-hidden="true"></span>
      ${escapeHtml(check.label)}
    </span>
  `
}

function isValidEmail(value) {
  const email = String(value).trim()
  return email.length >= 3 && email.length <= 254 && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)
}

function passwordSecurityProfile(value) {
  const password = String(value)
  const checks = [
    { id: 'length', label: '12+ characters', met: password.length >= 12 },
    { id: 'lower', label: 'Lowercase letter', met: /[a-z]/.test(password) },
    { id: 'upper', label: 'Uppercase letter', met: /[A-Z]/.test(password) },
    { id: 'number', label: 'Number', met: /\d/.test(password) },
    { id: 'symbol', label: 'Symbol', met: /[^A-Za-z0-9]/.test(password) },
  ]
  const score = checks.filter((check) => check.met).length
  const label = score <= 1 ? 'Weak' : score <= 3 ? 'Improving' : score === 4 ? 'Almost ready' : 'Strong'
  const tone = score <= 1 ? 'weak' : score <= 3 ? 'medium' : score === 4 ? 'almost' : 'strong'

  return {
    checks,
    label,
    percent: score * 20,
    score,
    strong: score === checks.length,
    tone,
  }
}

function canSignIn() {
  return isValidEmail(state.email) && state.password.length > 0
}

function canSignUp() {
  return isValidEmail(state.email) && passwordSecurityProfile(state.password).strong
}

function syncAuthControls() {
  const emailInput = document.querySelector('#email')
  const passwordInput = document.querySelector('#password')
  if (!emailInput || !passwordInput) return

  const emailReady = isValidEmail(state.email)
  const profile = passwordSecurityProfile(state.password)
  const emailHelp = document.querySelector('#email-help')
  const signInButton = document.querySelector('#sign-in')
  const signUpButton = document.querySelector('#sign-up')
  const strength = document.querySelector('#password-strength')
  const strengthLabel = document.querySelector('#password-strength-label')
  const meter = document.querySelector('.password-meter')
  const toggle = document.querySelector('#toggle-password')

  emailInput.setAttribute('aria-invalid', state.email && !emailReady ? 'true' : 'false')
  if (emailHelp) {
    emailHelp.textContent = state.email && !emailReady
      ? 'Enter a complete email address.'
      : 'Use the same email that received the invitation.'
    emailHelp.classList.toggle('field-help-error', Boolean(state.email && !emailReady))
  }

  passwordInput.type = state.passwordVisible ? 'text' : 'password'
  if (toggle) {
    toggle.textContent = state.passwordVisible ? 'Hide' : 'Show'
    toggle.setAttribute('aria-pressed', state.passwordVisible ? 'true' : 'false')
  }

  if (signInButton) signInButton.disabled = state.busy || !canSignIn()
  if (signUpButton) signUpButton.disabled = state.busy || !canSignUp()
  if (strength) strength.dataset.tone = profile.tone
  if (strengthLabel) strengthLabel.textContent = profile.label
  if (meter) meter.style.setProperty('--strength', `${profile.percent}%`)

  profile.checks.forEach((check) => {
    document.querySelector(`[data-password-check="${check.id}"]`)?.classList.toggle('met', check.met)
  })
}

function bindEvents() {
  document.querySelector('#email')?.addEventListener('input', (event) => {
    state.email = event.target.value.trim()
    syncAuthControls()
  })

  document.querySelector('#password')?.addEventListener('input', (event) => {
    state.password = event.target.value
    syncAuthControls()
  })

  document.querySelector('#toggle-password')?.addEventListener('click', () => {
    state.passwordVisible = !state.passwordVisible
    syncAuthControls()
  })

  document.querySelector('#sign-in')?.addEventListener('click', signIn)
  document.querySelector('#sign-up')?.addEventListener('click', signUp)
  document.querySelector('#show-sign-in')?.addEventListener('click', () => {
    state.authMode = 'signIn'
    render()
  })
  document.querySelector('#show-sign-up')?.addEventListener('click', () => {
    state.authMode = 'signUp'
    render()
  })
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

  syncAuthControls()
}

async function signIn() {
  state.authMode = 'signIn'
  if (!canSignIn()) {
    setStatus('error', 'Enter a valid email and password before signing in.')
    render()
    return
  }

  await withBusy('Signing in...', async () => {
    const { error } = await supabase.auth.signInWithPassword({
      email: state.email,
      password: state.password,
    })

    if (error) throw new Error(authErrorMessage(error))

    setStatus('success', 'Signed in. Checking invitations and incidents.')
    await loadPortalData()
  })
}

async function signUp() {
  state.authMode = 'signUp'
  if (!canSignUp()) {
    setStatus('error', 'Use a valid email and a stronger password before creating an account.')
    render()
    return
  }

  await withBusy('Creating account...', async () => {
    const { error } = await supabase.auth.signUp({
      email: state.email,
      password: state.password,
      options: {
        emailRedirectTo: portalUrlForEmail(state.email, { auth: 'confirmed' }),
      },
    })

    if (error) throw new Error(authErrorMessage(error))

    setStatus('success', 'Account created. Verify the email before accepting private care access.')
  })
}

async function signOut() {
  if (state.signingOut) return

  state.signingOut = true
  setStatus('info', 'Signing out...')
  render()

  try {
    const { error } = await supabase.auth.signOut()
    if (error) throw error
    state.session = null
    state.invitations = []
    state.assignments = []
    state.careCoreByIncident = {}
    state.resolutionNotes = {}
    state.busy = false
    setStatus('info', 'Signed out.')
  } catch (error) {
    setStatus('error', error.message || 'Sign out failed.')
  } finally {
    state.signingOut = false
    render()
  }
}

async function loadPortalData() {
  await withBusy('Refreshing your Care Circle...', async () => {
    await loadInvitationsOnly()
    await loadAssignmentsOnly()
    await loadAuthorizedCapsuleSectionsForAcceptedAssignments()

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

async function loadAuthorizedCapsuleSectionsForAcceptedAssignments() {
  const acceptedIncidentIds = [
    ...new Set(
      state.assignments
        .filter((assignment) => assignment.assignment_state === 'accepted')
        .map((assignment) => assignment.incident_id),
    ),
  ]

  for (const incidentId of acceptedIncidentIds) {
    if (!state.careCoreByIncident[incidentId]) {
      await loadIncidentCapsuleSections(incidentId)
    }
  }
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
      await loadIncidentCapsuleSections(accepted.incident_id)
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

async function loadIncidentCapsuleSections(incidentId) {
  const { data, error } = await supabase.rpc('list_my_incident_capsule_sections', {
    p_incident_id: incidentId,
  })

  if (error) throw error

  const sections = data ?? []
  if (sections.length) {
    state.careCoreByIncident = {
      ...state.careCoreByIncident,
      [incidentId]: sections,
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

function authErrorMessage(error) {
  const message = error?.message || ''
  if (/invalid login credentials/i.test(message)) {
    return 'We could not verify those credentials. Check the email and password, then try again.'
  }
  if (/email not confirmed/i.test(message)) {
    return 'Verify the email address before signing in.'
  }
  return message || 'Authentication failed.'
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
