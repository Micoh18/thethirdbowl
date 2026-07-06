package com.micoh.thethirdbowl

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.micoh.thethirdbowl.data.AuditEventRow
import com.micoh.thethirdbowl.data.AuditRepository
import com.micoh.thethirdbowl.data.CapsuleRepository
import com.micoh.thethirdbowl.data.CatRepository
import com.micoh.thethirdbowl.data.CatRow
import com.micoh.thethirdbowl.data.CareCoreDraft
import com.micoh.thethirdbowl.data.IncidentRow
import com.micoh.thethirdbowl.data.IncidentRepository
import com.micoh.thethirdbowl.data.InvitationRepository
import com.micoh.thethirdbowl.data.InvitationRow
import com.micoh.thethirdbowl.data.PlanRepository
import com.micoh.thethirdbowl.data.PlanRow
import com.micoh.thethirdbowl.data.SupabaseProvider
import com.micoh.thethirdbowl.ui.theme.TheThirdBowlTheme
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.providers.builtin.Email
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var authCallbackStatus by mutableStateOf<String?>(null)
    private var authCallbackEmail by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleAuthCallback(intent)
        enableEdgeToEdge()
        setContent {
            TheThirdBowlTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ThirdBowlApp(
                        authCallbackStatus = authCallbackStatus,
                        authCallbackEmail = authCallbackEmail,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthCallback(intent)
    }

    private fun handleAuthCallback(intent: Intent?) {
        if (intent == null) return

        SupabaseProvider.client.handleDeeplinks(
            intent,
            { session ->
                authCallbackEmail = session.user?.email
                authCallbackStatus = "Email verified. You are signed in."
            },
            { error ->
                authCallbackStatus = error.readableMessage()
            },
        )
    }
}

@Composable
private fun ThirdBowlApp(
    authCallbackStatus: String?,
    authCallbackEmail: String?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(AppTab.Home) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var signedInEmail by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<UiStatus>(UiStatus.Info("Checking your session...")) }
    var isBusy by remember { mutableStateOf(false) }
    var isLoadingCats by remember { mutableStateOf(false) }
    var accountLoadFailed by remember { mutableStateOf(false) }
    var catName by remember { mutableStateOf("") }
    var cats by remember { mutableStateOf(emptyList<CatRow>()) }
    var selectedCatId by remember { mutableStateOf<String?>(null) }
    var careCore by remember { mutableStateOf(CareCoreDraft()) }
    var invitationEmail by remember { mutableStateOf("") }
    var relationshipLabel by remember { mutableStateOf("") }
    var selectedAccessTemplate by remember { mutableStateOf(CareCircleAccessTemplate.CoreCare) }
    var selectedResponderSprite by remember { mutableStateOf(CatSpriteKind.Guard) }
    var isResponderLogoSelectorOpen by remember { mutableStateOf(false) }
    var responderSpriteCustomized by remember { mutableStateOf(false) }
    var isPeopleEditing by remember { mutableStateOf(false) }
    var invitations by remember { mutableStateOf(emptyList<InvitationRow>()) }
    val responderSpriteStore = remember(context) { ResponderSpriteStore(context.applicationContext) }
    val invitationEmailStore = remember(context) { InvitationEmailStore(context.applicationContext) }
    var invitationSprites by remember { mutableStateOf(responderSpriteStore.load()) }
    var invitationMaskedEmails by remember { mutableStateOf(invitationEmailStore.load()) }
    var plan by remember { mutableStateOf<PlanRow?>(null) }
    var incident by remember { mutableStateOf<IncidentRow?>(null) }
    var auditEvents by remember { mutableStateOf(emptyList<AuditEventRow>()) }
    val catRepository = remember { CatRepository() }
    val capsuleRepository = remember { CapsuleRepository() }
    val invitationRepository = remember { InvitationRepository() }
    val planRepository = remember { PlanRepository() }
    val incidentRepository = remember { IncidentRepository() }
    val auditRepository = remember { AuditRepository() }
    val selectedCat = cats.firstOrNull { it.id == selectedCatId }

    suspend fun loadSelectedCatState(catId: String) {
        careCore = capsuleRepository.loadCareCore(catId)
        invitations = invitationRepository.listInvitations(catId).activeCareCircleRows()
        plan = planRepository.getOrCreatePlan(catId)
        incident = incidentRepository.getActiveIncident(catId)
        auditEvents = auditRepository.listCatEvents(catId)
    }

    suspend fun loadAccountState() {
        cats = catRepository.listMyCats()
        selectedCatId = cats.firstOrNull()?.id
        selectedCatId?.let { catId ->
            loadSelectedCatState(catId)
        }
    }

    LaunchedEffect(Unit) {
        runCatching {
            SupabaseProvider.client.auth.currentSessionOrNull()
        }.onSuccess { session ->
            signedInEmail = session?.user?.email
            if (session == null) {
                status = UiStatus.Info("Create an account or sign in to build a continuity plan.")
            } else {
                isLoadingCats = true
                status = UiStatus.Info("Loading cats...")
                accountLoadFailed = false
                runCatching {
                    loadAccountState()
                }.onSuccess {
                    accountLoadFailed = false
                    status = UiStatus.Success("Your continuity workspace is ready.")
                }.onFailure { error ->
                    accountLoadFailed = true
                    status = UiStatus.Error(error.readableMessage())
                }
                isLoadingCats = false
            }
        }.onFailure { error ->
            status = UiStatus.Error(error.readableMessage())
        }
    }

    LaunchedEffect(authCallbackStatus, authCallbackEmail) {
        if (authCallbackStatus == null) return@LaunchedEffect

        status = UiStatus.Success(authCallbackStatus)
        if (authCallbackEmail != null) {
            signedInEmail = authCallbackEmail
            isLoadingCats = true
            status = UiStatus.Info("Loading cats...")
            accountLoadFailed = false
            runCatching {
                loadAccountState()
            }.onSuccess {
                accountLoadFailed = false
                selectedTab = AppTab.Home
            }.onFailure { error ->
                accountLoadFailed = true
                status = UiStatus.Error(error.readableMessage())
            }
            isLoadingCats = false
        }
    }

    if (signedInEmail == null) {
        AuthExperience(
            email = email,
            password = password,
            status = status,
            isBusy = isBusy,
            onEmailChange = { email = it.trim() },
            onPasswordChange = { password = it },
            onSignIn = {
                scope.launch {
                    if (!email.isValidEmailInput() || password.isBlank()) {
                        status = UiStatus.Error("Enter a valid email and password before signing in.")
                        return@launch
                    }
                    isBusy = true
                    status = UiStatus.Info("Signing in...")
                    runCatching {
                        SupabaseProvider.client.auth.signInWith(Email) {
                            this.email = email
                            this.password = password
                        }
                        SupabaseProvider.client.auth.currentSessionOrNull()
                    }.onSuccess { session ->
                        signedInEmail = session?.user?.email
                        isLoadingCats = true
                        status = UiStatus.Info("Loading cats...")
                        accountLoadFailed = false
                        loadAccountState()
                        isLoadingCats = false
                        accountLoadFailed = false
                        selectedTab = AppTab.Home
                        status = UiStatus.Success("Welcome back. Your plans are synced.")
                    }.onFailure { error ->
                        accountLoadFailed = signedInEmail != null
                        status = UiStatus.Error(error.authReadableMessage())
                        isLoadingCats = false
                    }
                    isBusy = false
                }
            },
            onSignUp = {
                scope.launch {
                    if (!email.isValidEmailInput()) {
                        status = UiStatus.Error("Use a valid email before creating an account.")
                        return@launch
                    }
                    if (!passwordSecurityProfile(password).isStrong) {
                        status = UiStatus.Error("Use a stronger password before creating an account.")
                        return@launch
                    }
                    isBusy = true
                    status = UiStatus.Info("Creating your account...")
                    runCatching {
                        SupabaseProvider.client.auth.signUpWith(
                            provider = Email,
                            redirectUrl = AUTH_CALLBACK_URL,
                        ) {
                            this.email = email
                            this.password = password
                        }
                    }.onSuccess {
                        status = UiStatus.Success("Account created. Open the verification email on this device.")
                    }.onFailure { error ->
                        status = UiStatus.Error(error.readableMessage())
                    }
                    isBusy = false
                }
            },
        )
        return
    }

    if (isLoadingCats) {
        LoadingCatsExperience(status = status)
        return
    }

    Scaffold(
        bottomBar = {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    AppTab.entries.forEach { tab ->
                        val selected = selectedTab == tab
                        NavigationBarItem(
                            selected = selected,
                            onClick = { selectedTab = tab },
                            icon = {
                                NavIcon(
                                    kind = tab.icon,
                                    selected = selected,
                                )
                            },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppHeader(
                selectedCat = selectedCat,
            )

            StatusBanner(status = status, isBusy = isBusy)

            when (selectedTab) {
                AppTab.Home -> HomeScreen(
                    cats = cats,
                    selectedCat = selectedCat,
                    selectedCatId = selectedCatId,
                    catName = catName,
                    careCore = careCore,
                    invitations = invitations,
                    plan = plan,
                    incident = incident,
                    isBusy = isBusy,
                    accountLoadFailed = accountLoadFailed,
                    onCatNameChange = { catName = it },
                    onCreateCat = {
                        scope.launch {
                            isBusy = true
                            status = UiStatus.Info("Creating the cat profile...")
                            runCatching {
                                catRepository.createCat(catName)
                            }.onSuccess { cat ->
                                catName = ""
                                cats = cats + cat
                                selectedCatId = cat.id
                                careCore = CareCoreDraft()
                                invitations = emptyList()
                                isPeopleEditing = false
                                incident = null
                                plan = runCatching { planRepository.getOrCreatePlan(cat.id) }.getOrNull()
                                auditEvents = runCatching { auditRepository.listCatEvents(cat.id) }.getOrDefault(emptyList())
                                status = UiStatus.Success("${cat.name} now has a place for their continuity plan.")
                            }.onFailure { error ->
                                status = UiStatus.Error(error.readableMessage())
                            }
                            isBusy = false
                        }
                    },
                    onRetryAccountLoad = {
                        scope.launch {
                            isBusy = true
                            status = UiStatus.Info("Retrying the sync...")
                            accountLoadFailed = false
                            runCatching {
                                loadAccountState()
                            }.onSuccess {
                                accountLoadFailed = false
                                status = UiStatus.Success("Your continuity workspace is ready.")
                            }.onFailure { error ->
                                accountLoadFailed = true
                                status = UiStatus.Error(error.readableMessage())
                            }
                            isBusy = false
                        }
                    },
                    onRefreshCoverage = {
                        val catId = selectedCatId ?: return@HomeScreen
                        scope.launch {
                            isBusy = true
                            status = UiStatus.Info("Refreshing coverage state...")
                            runCatching {
                                loadSelectedCatState(catId)
                            }.onSuccess {
                                status = UiStatus.Success("Coverage state refreshed.")
                            }.onFailure { error ->
                                status = UiStatus.Error(error.readableMessage())
                            }
                            isBusy = false
                        }
                    },
                    onSelectCat = { cat ->
                        scope.launch {
                            isBusy = true
                            status = UiStatus.Info("Loading ${cat.name}...")
                            runCatching {
                                loadSelectedCatState(cat.id)
                            }.onSuccess {
                                selectedCatId = cat.id
                                isPeopleEditing = false
                                status = UiStatus.Success("${cat.name}'s plan is ready.")
                            }.onFailure { error ->
                                status = UiStatus.Error(error.readableMessage())
                            }
                            isBusy = false
                        }
                    },
                    onArmPlan = {
                        val catId = selectedCatId ?: return@HomeScreen
                        val readiness = coverageReadiness(careCore, invitations, plan, incident)
                        if (!readiness.canActivateRitual) {
                            status = UiStatus.Info(readiness.body)
                            selectedTab = if (!readiness.coreCareComplete) AppTab.Capsule else AppTab.Circle
                            return@HomeScreen
                        }
                        scope.launch {
                            isBusy = true
                            status = UiStatus.Info("Activating the ritual...")
                            runCatching {
                                planRepository.armPlan(catId)
                            }.onSuccess { armedPlan ->
                                plan = armedPlan
                                incident = incidentRepository.getActiveIncident(catId)
                                auditEvents = auditRepository.listCatEvents(catId)
                                status = UiStatus.Success("Ritual active. Your next check-in is confirmed.")
                            }.onFailure { error ->
                                status = UiStatus.Error(error.readableMessage())
                            }
                            isBusy = false
                        }
                    },
                    onCheckIn = {
                        val catId = selectedCatId ?: return@HomeScreen
                        scope.launch {
                            isBusy = true
                            status = UiStatus.Info("Confirming your availability...")
                            runCatching {
                                planRepository.completeCheckIn(catId)
                            }.onSuccess { result ->
                                plan = plan?.copy(nextCheckInAt = result.nextCheckInAt)
                                incident = incidentRepository.getActiveIncident(catId)
                                auditEvents = auditRepository.listCatEvents(catId)
                                status = UiStatus.Success("Availability confirmed. The next check-in is scheduled.")
                            }.onFailure { error ->
                                status = UiStatus.Error(error.readableMessage())
                            }
                            isBusy = false
                        }
                    },
                    onGoToCapsule = { selectedTab = AppTab.Capsule },
                    onGoToCircle = { selectedTab = AppTab.Circle },
                )

                AppTab.Capsule -> CapsuleScreen(
                    selectedCat = selectedCat,
                    careCore = careCore,
                    isBusy = isBusy,
                    onCareCoreChange = { careCore = it },
                    onSaveCareCore = {
                        val catId = selectedCatId ?: return@CapsuleScreen
                        scope.launch {
                            isBusy = true
                            status = UiStatus.Info("Saving the care routine...")
                            runCatching {
                                capsuleRepository.saveCareCore(catId, careCore)
                            }.onSuccess { savedDraft ->
                                careCore = savedDraft
                                auditEvents = auditRepository.listCatEvents(catId)
                                status = UiStatus.Success("Routine saved. Your cat's essentials are ready to share when authorized.")
                            }.onFailure { error ->
                                status = UiStatus.Error(error.readableMessage())
                            }
                            isBusy = false
                        }
                    },
                )

                AppTab.Circle -> CircleScreen(
                    selectedCat = selectedCat,
                    invitations = invitations,
                    invitationEmail = invitationEmail,
                    relationshipLabel = relationshipLabel,
                    selectedAccessTemplate = selectedAccessTemplate,
                    selectedResponderSprite = selectedResponderSprite,
                    isResponderLogoSelectorOpen = isResponderLogoSelectorOpen,
                    invitationSprites = invitationSprites,
                    invitationMaskedEmails = invitationMaskedEmails,
                    isBusy = isBusy,
                    isPeopleEditing = isPeopleEditing,
                    onInvitationEmailChange = { invitationEmail = it.trim() },
                    onRelationshipLabelChange = { relationshipLabel = it },
                    onAccessTemplateChange = { template ->
                        selectedAccessTemplate = template
                        if (!responderSpriteCustomized) {
                            selectedResponderSprite = template.sprite
                        }
                    },
                    onResponderLogoSelectorToggle = {
                        isResponderLogoSelectorOpen = !isResponderLogoSelectorOpen
                    },
                    onResponderSpriteChange = { sprite ->
                        selectedResponderSprite = sprite
                        responderSpriteCustomized = true
                        isResponderLogoSelectorOpen = false
                    },
                    onCreateInvitation = {
                        val catId = selectedCatId ?: return@CircleScreen
                        val responderSprite = selectedResponderSprite
                        val invitedEmail = invitationEmail
                        val invitedRelationshipLabel = relationshipLabel
                        val invitedAccessTemplate = selectedAccessTemplate
                        val maskedEmail = invitedEmail.maskedEmailForDisplay()
                        scope.launch {
                            isBusy = true
                            status = UiStatus.Info("Preparing the invitation...")
                            runCatching {
                                val invitation = invitationRepository.createInvitation(
                                    catId = catId,
                                    email = invitedEmail,
                                    relationshipLabel = invitedRelationshipLabel,
                                    proposedRole = invitedAccessTemplate.role,
                                    proposedScopes = invitedAccessTemplate.scopes,
                                )
                                val emailResult = runCatching {
                                    invitationRepository.sendInvitationEmail(
                                        invitationId = invitation.id,
                                        email = invitedEmail,
                                    )
                                }
                                invitation to emailResult
                            }.onSuccess { result ->
                                val invitation = result.first
                                responderSpriteStore.save(invitation.id, responderSprite)
                                invitationEmailStore.save(invitation.id, maskedEmail)
                                invitationSprites = invitationSprites + (invitation.id to responderSprite)
                                invitationMaskedEmails = invitationMaskedEmails + (invitation.id to maskedEmail)
                                invitationEmail = ""
                                relationshipLabel = ""
                                selectedResponderSprite = invitedAccessTemplate.sprite
                                isResponderLogoSelectorOpen = false
                                responderSpriteCustomized = false
                                invitations = listOf(
                                    invitation.copy(
                                        invitedEmailMasked = invitation.invitedEmailMasked.ifBlank { maskedEmail },
                                    ),
                                ) + invitations
                                auditEvents = auditRepository.listCatEvents(catId)
                                val emailCopy = result.second.fold(
                                    onSuccess = { emailResult ->
                                        when {
                                            !emailResult.configured -> " Email provider is not configured."
                                            emailResult.sent > 0 -> " Invitation email sent."
                                            emailResult.failed > 0 -> " Invitation email failed."
                                            emailResult.skipped > 0 -> " Invitation email skipped."
                                            else -> " No invitation email was sent."
                                        }
                                    },
                                    onFailure = { error ->
                                        " Invitation email failed: ${error.readableMessage()}"
                                    },
                                )
                                status = UiStatus.Success("Contact added to the Care Circle.$emailCopy")
                            }.onFailure { error ->
                                status = UiStatus.Error(error.readableMessage())
                            }
                            isBusy = false
                        }
                    },
                    onTogglePeopleEditing = {
                        isPeopleEditing = !isPeopleEditing
                    },
                    onRemoveInvitation = { invitation ->
                        val catId = selectedCatId ?: return@CircleScreen
                        scope.launch {
                            isBusy = true
                            status = UiStatus.Info("Removing ${invitation.relationshipLabel} from the Care Circle...")
                            runCatching {
                                invitationRepository.removeCareCirclePerson(invitation.id)
                            }.onSuccess {
                                responderSpriteStore.remove(invitation.id)
                                invitationEmailStore.remove(invitation.id)
                                invitationSprites = invitationSprites - invitation.id
                                invitationMaskedEmails = invitationMaskedEmails - invitation.id
                                invitations = invitationRepository.listInvitations(catId).activeCareCircleRows()
                                auditEvents = auditRepository.listCatEvents(catId)
                                if (invitations.isEmpty()) {
                                    isPeopleEditing = false
                                }
                                status = UiStatus.Success("${invitation.relationshipLabel} was removed from the Care Circle.")
                            }.onFailure { error ->
                                status = UiStatus.Error(error.readableMessage())
                            }
                            isBusy = false
                        }
                    },
                )

                AppTab.History -> HistoryScreen(
                    selectedCat = selectedCat,
                    plan = plan,
                    incident = incident,
                    invitations = invitations,
                    careCore = careCore,
                    auditEvents = auditEvents,
                    isBusy = isBusy,
                    onRefresh = {
                        val catId = selectedCatId ?: return@HistoryScreen
                        scope.launch {
                            isBusy = true
                            status = UiStatus.Info("Refreshing history...")
                            runCatching {
                                auditRepository.listCatEvents(catId)
                            }.onSuccess { events ->
                                auditEvents = events
                                status = UiStatus.Success("History refreshed.")
                            }.onFailure { error ->
                                status = UiStatus.Error(error.readableMessage())
                            }
                            isBusy = false
                        }
                    },
                )

                AppTab.Settings -> SettingsScreen(
                    selectedCat = selectedCat,
                    signedInEmail = signedInEmail.orEmpty(),
                    plan = plan,
                    incident = incident,
                    isBusy = isBusy,
                    onSignOut = {
                        scope.launch {
                            isBusy = true
                            status = UiStatus.Info("Signing out...")
                            runCatching {
                                SupabaseProvider.client.auth.signOut()
                            }.onSuccess {
                                signedInEmail = null
                                cats = emptyList()
                                selectedCatId = null
                                careCore = CareCoreDraft()
                                invitationEmail = ""
                                relationshipLabel = ""
                                selectedAccessTemplate = CareCircleAccessTemplate.CoreCare
                                invitations = emptyList()
                                isPeopleEditing = false
                                plan = null
                                incident = null
                                auditEvents = emptyList()
                                selectedTab = AppTab.Home
                                status = UiStatus.Info("Signed out.")
                            }.onFailure { error ->
                                status = UiStatus.Error(error.readableMessage())
                            }
                            isBusy = false
                        }
                    },
                    onTriggerMissedCheckIn = {
                        val catId = selectedCatId ?: return@SettingsScreen
                        scope.launch {
                            isBusy = true
                            status = UiStatus.Info("Running debug handoff trigger...")
                            runCatching {
                                val processorResult = planRepository.triggerDeveloperMissedCheckIn(catId)
                                val emailResult = runCatching {
                                    planRepository.sendPendingIncidentEmails()
                                }
                                processorResult to emailResult
                            }.onSuccess { result ->
                                plan = planRepository.getOrCreatePlan(catId)
                                incident = incidentRepository.getActiveIncident(catId)
                                auditEvents = auditRepository.listCatEvents(catId)
                                val processorResult = result.first
                                val emailCopy = result.second.fold(
                                    onSuccess = { emailResult ->
                                        if (emailResult.configured) {
                                            " Email: ${emailResult.sent} sent, ${emailResult.failed} failed, ${emailResult.skipped} skipped."
                                        } else {
                                            " Email provider is not configured."
                                        }
                                    },
                                    onFailure = { error ->
                                        " Email dispatch failed: ${error.readableMessage()}"
                                    },
                                )
                                status = UiStatus.Success(
                                    "Debug trigger processed ${processorResult.processedPlans} plan(s), created ${processorResult.incidentsCreated} incident(s) and ${processorResult.assignmentsCreated} assignment(s).$emailCopy",
                                )
                            }.onFailure { error ->
                                status = UiStatus.Error(error.readableMessage())
                            }
                            isBusy = false
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun LoadingCatsExperience(status: UiStatus) {
    val transition = rememberInfiniteTransition(label = "loading-dizzy")
    val spriteScale by transition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dizzy-scale",
    )
    val spriteRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "dizzy-rotation",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        BrandMark()
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Loading cats...",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Syncing profiles, Capsule sections, Care Circle and continuity state.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CatSprite(
            kind = CatSpriteKind.Dizzy,
            size = 132.dp,
            contentDescription = "Loading cat",
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .rotate(spriteRotation)
                .scale(spriteScale),
        )
        WorkspaceSkeleton()
        StatusBanner(status = status, isBusy = false)
    }
}

@Composable
private fun WorkspaceSkeleton() {
    val transition = rememberInfiniteTransition(label = "workspace-skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.38f,
        targetValue = 0.72f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeleton-alpha",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SkeletonBlock(widthFraction = 0.42f, alpha = alpha)
        SkeletonBlock(widthFraction = 0.86f, alpha = alpha)
        SkeletonBlock(widthFraction = 0.74f, alpha = alpha)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)),
                )
            }
        }
    }
}

@Composable
private fun SkeletonBlock(
    widthFraction: Float,
    alpha: Float,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(14.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)),
    )
}

@Composable
private fun AuthExperience(
    email: String,
    password: String,
    status: UiStatus,
    isBusy: Boolean,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSignIn: () -> Unit,
    onSignUp: () -> Unit,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    var isSignInMode by remember { mutableStateOf(false) }
    val emailValid = email.isValidEmailInput()
    val passwordProfile = passwordSecurityProfile(password)
    val canSignIn = !isBusy && emailValid && password.isNotBlank()
    val canCreate = !isBusy && emailValid && passwordProfile.isStrong

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BrandMark(size = 56)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Protected care workspace",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "The Third Bowl",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Text(
            text = "Create an account or sign in before private routines, contacts, home access, or medical notes are unlocked.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconPill(
                text = "Email verified",
                icon = IconKind.UserCheck,
                color = MaterialTheme.colorScheme.primary,
            )
            IconPill(
                text = "Scoped access",
                icon = IconKind.Lock,
                color = MaterialTheme.colorScheme.secondary,
            )
        }

        ElevatedCard(
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CatSprite(
                        kind = CatSpriteKind.Guard,
                        size = 56.dp,
                        padding = 6.dp,
                        contentDescription = "Protected access cat",
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = if (isSignInMode) "Secure sign-in" else "Create your account",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = if (isSignInMode) {
                                "Use the account that owns the invited email."
                            } else {
                                "Start with the exact email that received the invite."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                OutlinedTextField(
                    value = email,
                    onValueChange = onEmailChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Email") },
                    singleLine = true,
                    isError = email.isNotBlank() && !emailValid,
                    supportingText = {
                        Text(
                            if (email.isBlank()) {
                                "Use the same email that received the invite."
                            } else if (emailValid) {
                                "Email format looks ready."
                            } else {
                                "Enter a complete email address."
                            },
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        TextButton(onClick = { passwordVisible = !passwordVisible }) {
                            Text(if (passwordVisible) "Hide" else "Show")
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                if (!isSignInMode) {
                    PasswordSecurityMeter(profile = passwordProfile)
                    PasswordRequirementChecklist(profile = passwordProfile)
                    Text(
                        text = "Only new accounts need to meet every protection above.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isSignInMode) {
                    Button(
                        enabled = canSignIn,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onSignIn,
                    ) {
                        Text("Sign in")
                    }
                    AuthModeSwitchRow(
                        prompt = "Need an account?",
                        action = "Create account instead",
                        onClick = { isSignInMode = false },
                    )
                } else {
                    Button(
                        enabled = canCreate,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onSignUp,
                    ) {
                        Text("Create account")
                    }
                    AuthModeSwitchRow(
                        prompt = "Already have an account?",
                        action = "Sign in instead",
                        onClick = { isSignInMode = true },
                    )
                }
            }
        }

        TrustChecklist()
        StatusBanner(status = status, isBusy = isBusy)
    }
}

@Composable
private fun AuthModeSwitchRow(
    prompt: String,
    action: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = prompt,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onClick) {
            Text(
                text = action,
                textDecoration = TextDecoration.Underline,
            )
        }
    }
}

@Composable
private fun PasswordSecurityMeter(profile: PasswordSecurityProfile) {
    val meterColor = when {
        profile.score >= 5 -> MaterialTheme.colorScheme.primary
        profile.score >= 3 -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.error
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Password strength",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = profile.label,
                style = MaterialTheme.typography.labelMedium,
                color = meterColor,
                fontWeight = FontWeight.SemiBold,
            )
        }
        LinearProgressIndicator(
            progress = { profile.score / 5f },
            modifier = Modifier.fillMaxWidth(),
            color = meterColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun PasswordRequirementChecklist(profile: PasswordSecurityProfile) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        profile.requirements.forEach { requirement ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(
                            if (requirement.met) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (requirement.met) {
                        LineIcon(
                            kind = IconKind.CheckCircle,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
                Text(
                    text = requirement.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (requirement.met) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    cats: List<CatRow>,
    selectedCat: CatRow?,
    selectedCatId: String?,
    catName: String,
    careCore: CareCoreDraft,
    invitations: List<InvitationRow>,
    plan: PlanRow?,
    incident: IncidentRow?,
    isBusy: Boolean,
    accountLoadFailed: Boolean,
    onCatNameChange: (String) -> Unit,
    onCreateCat: () -> Unit,
    onRetryAccountLoad: () -> Unit,
    onRefreshCoverage: () -> Unit,
    onSelectCat: (CatRow) -> Unit,
    onArmPlan: () -> Unit,
    onCheckIn: () -> Unit,
    onGoToCapsule: () -> Unit,
    onGoToCircle: () -> Unit,
) {
    val readiness = coverageReadiness(careCore, invitations, plan, incident)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (selectedCat == null) {
            if (accountLoadFailed) {
                EmptyStateCard(
                    title = "Couldn't load care profiles",
                    body = "The app could not reach Supabase from this device. Check Wi-Fi or mobile data, then retry the sync.",
                )
                PrimaryActionButton(
                    text = "Retry sync",
                    icon = IconKind.CheckCircle,
                    enabled = !isBusy,
                    isBusy = isBusy,
                    onClick = onRetryAccountLoad,
                )
                return
            }
            EmptyStateCard(
                title = "Create the first care profile",
                body = "Add the cat whose routine you want to protect. The Capsule and Ritual will attach to this profile.",
            )
            OutlinedTextField(
                value = catName,
                onValueChange = onCatNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Cat name") },
                singleLine = true,
            )
            PrimaryActionButton(
                text = "Create care profile",
                icon = IconKind.CheckCircle,
                enabled = !isBusy && catName.isNotBlank(),
                isBusy = isBusy,
                onClick = onCreateCat,
            )
            return
        }

        CatSwitcher(
            cats = cats,
            selectedCatId = selectedCatId,
            isBusy = isBusy,
            onSelectCat = onSelectCat,
        )

        ContinuityStatusCard(
            cat = selectedCat,
            careCore = careCore,
            invitations = invitations,
            plan = plan,
            incident = incident,
            isBusy = isBusy,
            onArmPlan = onArmPlan,
            onCheckIn = onCheckIn,
            onRefreshCoverage = onRefreshCoverage,
            onGoToCapsule = onGoToCapsule,
            onGoToCircle = onGoToCircle,
        )

        if (incident != null) {
            ActiveIncidentCard(incident = incident)
        }

        ReadinessGrid(
            careCore = careCore,
            invitations = invitations,
            plan = plan,
            onGoToCapsule = onGoToCapsule,
            onGoToCircle = onGoToCircle,
        )

        SectionCard(title = "Trust at a glance", icon = IconKind.Shield) {
            SettingsOptionRow(
                icon = IconKind.Capsule,
                title = if (careCore.completionCount() == 3) "Capsule ready" else "Capsule needs details",
                body = if (careCore.completionCount() == 3) {
                    "Food, hiding places and hard rules are written."
                } else {
                    "${careCore.completionCount()}/3 essentials complete."
                },
            )
            Spacer(modifier = Modifier.height(10.dp))
            SettingsOptionRow(
                icon = IconKind.UserCheck,
                title = if (readiness.hasAcceptedCareResponder) "Responder ready" else "Responder pending",
                body = if (readiness.hasAcceptedCareResponder) {
                    "${readiness.acceptedResponderCount} trusted person can see core care during an incident."
                } else if (invitations.isEmpty()) {
                    "Invite someone who can genuinely reach the cat."
                } else {
                    "Waiting for an invited person to accept."
                },
            )
            Spacer(modifier = Modifier.height(10.dp))
            SettingsOptionRow(
                icon = IconKind.Clock,
                title = if (plan?.status == "armed") "Ritual active" else "Ritual not active",
                body = plan?.checkInSummary() ?: "Activate once Capsule and Circle are ready.",
            )
        }
    }
}

@Composable
private fun ActiveIncidentCard(incident: IncidentRow) {
    val responderAccepted = incident.assignmentState == "accepted"
    val catReached = incident.catReachedAt != null
    SectionCard(title = "Active handoff", icon = IconKind.Alert) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CatSprite(
                kind = CatSpriteKind.Alert,
                size = 64.dp,
                contentDescription = "Incident warning cat",
            )
            Text(
                text = if (catReached) {
                    "${incident.assignedRelationshipLabel} reported reaching ${incident.catName}. Keep the resolution explicit."
                } else {
                    "${incident.assignedRelationshipLabel} has been asked to reach ${incident.catName}. Watch the handoff, not just the alert."
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        IncidentProgressHeader(incident)
        Spacer(modifier = Modifier.height(14.dp))
        TimelineRow(
            label = "Incident opened",
            value = incident.activatedAt.humanDateTime(),
            active = true,
        )
        TimelineRow(
            label = "Responder assigned",
            value = "${incident.assignedRelationshipLabel} is the current responder",
            active = incident.assignmentState in listOf("pending", "notified", "accepted"),
        )
        TimelineRow(
            label = "Responsibility",
            value = if (responderAccepted) {
                "Accepted by ${incident.assignedRelationshipLabel}"
            } else {
                "Waiting for acceptance before the handoff is trusted"
            },
            active = responderAccepted,
        )
        TimelineRow(
            label = "Cat reached",
            value = incident.catReachedAt?.humanDateTime() ?: "Not confirmed yet",
            active = catReached,
        )
        TimelineRow(
            label = "Deadline",
            value = incident.responseDeadlineAt.humanDateTime(),
            active = !catReached,
        )
    }
}

@Composable
private fun IncidentProgressHeader(incident: IncidentRow) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricChip(label = "State", value = incident.incidentState.humanLabel())
        MetricChip(label = "Handoff", value = incident.assignmentState.humanLabel())
        MetricChip(label = "Reached", value = incident.catReachedAt?.humanDateTime(short = true) ?: "No")
    }
}

@Composable
private fun CapsuleScreen(
    selectedCat: CatRow?,
    careCore: CareCoreDraft,
    isBusy: Boolean,
    onCareCoreChange: (CareCoreDraft) -> Unit,
    onSaveCareCore: () -> Unit,
) {
    var openSection by remember(selectedCat?.id) { mutableStateOf(CapsuleEditorSection.CoreCare) }

    if (selectedCat == null) {
        EmptyStateCard(
            title = "No cat selected",
            body = "Create a cat profile before writing a Capsule.",
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle(
            eyebrow = selectedCat.name,
            title = "Capsule",
            body = "Write the small details that make ${selectedCat.name}'s care possible when you cannot be there.",
        )
        CompletionCard(careCore)
        CapsuleDisclosureCard(catName = selectedCat.name)
        ExpandableSectionCard(
            title = "Core care",
            summary = "${careCore.completionCount()}/3 essentials complete",
            sprite = CatSpriteKind.Guard,
            expanded = openSection == CapsuleEditorSection.CoreCare,
            onToggle = { openSection = CapsuleEditorSection.CoreCare },
        ) {
            Text(
                text = "Required for coverage. Released only to accepted responders during an active incident.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            CareNoteField(
                value = careCore.feedingAndWater,
                onValueChange = { onCareCoreChange(careCore.copy(feedingAndWater = it)) },
                label = "Food, water and rhythm",
                placeholder = "Example: eats at 8 and 20h; fresh water in the kitchen.",
                supportingText = "What should someone do in the first few hours?",
                icon = IconKind.Bowl,
                minLines = 3,
            )
            Spacer(modifier = Modifier.height(12.dp))
            CareNoteField(
                value = careCore.hidingPlaces,
                onValueChange = { onCareCoreChange(careCore.copy(hidingPlaces = it)) },
                label = "Hiding places and approach",
                placeholder = "Example: hides under the bed; sit nearby and let ${selectedCat.name} come out.",
                supportingText = "Where to look and how to avoid stress.",
                icon = IconKind.HouseSearch,
                minLines = 3,
            )
            Spacer(modifier = Modifier.height(12.dp))
            CareNoteField(
                value = careCore.doNotDo,
                onValueChange = { onCareCoreChange(careCore.copy(doNotDo = it)) },
                label = "Never do this",
                placeholder = "Example: do not open the balcony; do not pick up unless necessary.",
                supportingText = "Hard rules that prevent stress, escape or mistakes.",
                icon = IconKind.Alert,
                minLines = 3,
            )
            Spacer(modifier = Modifier.height(16.dp))
            PrimaryActionButton(
                text = "Save Capsule",
                icon = IconKind.CheckCircle,
                enabled = !isBusy,
                isBusy = isBusy,
                onClick = onSaveCareCore,
            )
        }

        ExpandableSectionCard(
            title = "Home access",
            summary = if (careCore.homeAccessCompletionCount() > 0) {
                "${careCore.homeAccessCompletionCount()}/3 details written"
            } else {
                "Collapsed - only for Home access helpers"
            },
            sprite = CatSpriteKind.Key,
            expanded = openSection == CapsuleEditorSection.HomeAccess,
            onToggle = { openSection = CapsuleEditorSection.HomeAccess },
        ) {
            Text(
                text = "Only Home access helpers can see this during an active incident.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            CareNoteField(
                value = careCore.entryInstructions,
                onValueChange = { onCareCoreChange(careCore.copy(entryInstructions = it)) },
                label = "Entry instructions",
                placeholder = "Example: concierge knows Sofia; apartment 503; alarm off before door.",
                supportingText = "Building, door, alarm or timing details.",
                icon = IconKind.Key,
                minLines = 3,
            )
            Spacer(modifier = Modifier.height(12.dp))
            CareNoteField(
                value = careCore.keyLocation,
                onValueChange = { onCareCoreChange(careCore.copy(keyLocation = it)) },
                label = "Key or access location",
                placeholder = "Example: spare key with neighbor Sofia, not under the mat.",
                supportingText = "Keep this practical and only for trusted keyholders.",
                icon = IconKind.Lock,
                minLines = 2,
            )
            Spacer(modifier = Modifier.height(12.dp))
            CareNoteField(
                value = careCore.safeRoom,
                onValueChange = { onCareCoreChange(careCore.copy(safeRoom = it)) },
                label = "Safe room or home hazards",
                placeholder = "Example: keep bedroom closed; window latch is loose.",
                supportingText = "Where to contain the cat and what to avoid.",
                icon = IconKind.Home,
                minLines = 2,
            )
            Spacer(modifier = Modifier.height(16.dp))
            PrimaryActionButton(
                text = "Save home access",
                icon = IconKind.Key,
                enabled = !isBusy,
                isBusy = isBusy,
                onClick = onSaveCareCore,
            )
        }

        ExpandableSectionCard(
            title = "Medical",
            summary = if (careCore.medicalCompletionCount() > 0) {
                "${careCore.medicalCompletionCount()}/3 details written"
            } else {
                "Collapsed - only for Medical helpers"
            },
            sprite = CatSpriteKind.Medic,
            expanded = openSection == CapsuleEditorSection.Medical,
            onToggle = { openSection = CapsuleEditorSection.Medical },
        ) {
            Text(
                text = "Only Medical helpers can see this during an active incident.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            CareNoteField(
                value = careCore.medications,
                onValueChange = { onCareCoreChange(careCore.copy(medications = it)) },
                label = "Medication and dosing",
                placeholder = "Example: 1/2 pill at 9h with food; call vet if missed.",
                supportingText = "Medicine names, dose windows and missed-dose guidance.",
                icon = IconKind.Cross,
                minLines = 3,
            )
            Spacer(modifier = Modifier.height(12.dp))
            CareNoteField(
                value = careCore.vetInfo,
                onValueChange = { onCareCoreChange(careCore.copy(vetInfo = it)) },
                label = "Vet and insurance context",
                placeholder = "Example: Vet Sur, +56..., policy number, emergency clinic.",
                supportingText = "Clinic, phone, policy or emergency contact details.",
                icon = IconKind.UserCheck,
                minLines = 2,
            )
            Spacer(modifier = Modifier.height(12.dp))
            CareNoteField(
                value = careCore.medicalWarnings,
                onValueChange = { onCareCoreChange(careCore.copy(medicalWarnings = it)) },
                label = "Medical warnings",
                placeholder = "Example: allergic to X; stress sign is open-mouth breathing.",
                supportingText = "Allergies, stress signs or handling limits.",
                icon = IconKind.Alert,
                minLines = 2,
            )
            Spacer(modifier = Modifier.height(16.dp))
            PrimaryActionButton(
                text = "Save medical",
                icon = IconKind.Cross,
                enabled = !isBusy,
                isBusy = isBusy,
                onClick = onSaveCareCore,
            )
        }
    }
}

@Composable
private fun CapsuleDisclosureCard(catName: String) {
    SectionCard(title = "How release works", icon = IconKind.Lock) {
        Text(
            text = "The Capsule is private. During an incident, each person sees only what you already authorized.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        SettingsOptionRow(
            icon = IconKind.Bowl,
            sprite = CatSpriteKind.Guard,
            title = "Core care",
            body = "Food, water, hiding places and hard rules for ${catName}.",
        )
        Spacer(modifier = Modifier.height(10.dp))
        SettingsOptionRow(
            icon = IconKind.Key,
            sprite = CatSpriteKind.Key,
            title = "Home access",
            body = "Entry details stay separated from routine care.",
        )
        Spacer(modifier = Modifier.height(10.dp))
        SettingsOptionRow(
            icon = IconKind.Cross,
            sprite = CatSpriteKind.Medic,
            title = "Medical",
            body = "Vet and medication context stays separated from general responders.",
        )
    }
}

@Composable
private fun CircleScreen(
    selectedCat: CatRow?,
    invitations: List<InvitationRow>,
    invitationEmail: String,
    relationshipLabel: String,
    selectedAccessTemplate: CareCircleAccessTemplate,
    selectedResponderSprite: CatSpriteKind,
    isResponderLogoSelectorOpen: Boolean,
    invitationSprites: Map<String, CatSpriteKind>,
    invitationMaskedEmails: Map<String, String>,
    isBusy: Boolean,
    isPeopleEditing: Boolean,
    onInvitationEmailChange: (String) -> Unit,
    onRelationshipLabelChange: (String) -> Unit,
    onAccessTemplateChange: (CareCircleAccessTemplate) -> Unit,
    onResponderLogoSelectorToggle: () -> Unit,
    onResponderSpriteChange: (CatSpriteKind) -> Unit,
    onCreateInvitation: () -> Unit,
    onTogglePeopleEditing: () -> Unit,
    onRemoveInvitation: (InvitationRow) -> Unit,
) {
    if (selectedCat == null) {
        EmptyStateCard(
            title = "No cat selected",
            body = "Create a cat profile before inviting trusted contacts.",
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle(
            eyebrow = selectedCat.name,
            title = "Care Circle",
            body = "Invite real people, then make it obvious what they can see and when they can act.",
        )

        SectionCard(title = "Invite a trusted person", icon = IconKind.UserCheck) {
            SettingsOptionRow(
                icon = IconKind.User,
                title = "1. Person",
                body = "Start with someone who can truly reach the cat or coordinate care.",
            )
            Spacer(modifier = Modifier.height(12.dp))
            CareNoteField(
                value = invitationEmail,
                onValueChange = onInvitationEmailChange,
                label = "Trusted person's email",
                placeholder = "sofia@example.com",
                supportingText = "The invitation is tied to this exact email.",
                icon = IconKind.User,
                minLines = 1,
                keyboardType = KeyboardType.Email,
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(12.dp))
            CareNoteField(
                value = relationshipLabel,
                onValueChange = onRelationshipLabelChange,
                label = "Relationship to the cat",
                placeholder = "Neighbor, keyholder, sister, vet friend",
                supportingText = "Shown in the handoff so the role feels human.",
                icon = IconKind.Circle,
                minLines = 1,
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(16.dp))
            SettingsOptionRow(
                icon = IconKind.User,
                sprite = selectedResponderSprite,
                title = "2. Logo",
                body = "Pick the cat face this person will use inside your Care Circle.",
            )
            Spacer(modifier = Modifier.height(8.dp))
            ResponderLogoPickerSummary(
                selected = selectedResponderSprite,
                expanded = isResponderLogoSelectorOpen,
                onToggle = onResponderLogoSelectorToggle,
            )
            if (isResponderLogoSelectorOpen) {
                Spacer(modifier = Modifier.height(10.dp))
                ResponderLogoSelector(
                    selected = selectedResponderSprite,
                    onSelected = onResponderSpriteChange,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            SettingsOptionRow(
                icon = IconKind.Lock,
                title = "3. Access",
                body = "Choose what this person can see during an incident.",
            )
            Spacer(modifier = Modifier.height(8.dp))
            AccessTemplateSelector(
                selected = selectedAccessTemplate,
                onSelected = onAccessTemplateChange,
            )
            Spacer(modifier = Modifier.height(16.dp))
            PrimaryActionButton(
                text = "Add to Care Circle",
                icon = IconKind.UserCheck,
                enabled = !isBusy && invitationEmail.isNotBlank() && relationshipLabel.isNotBlank(),
                isBusy = isBusy,
                onClick = onCreateInvitation,
            )
        }

        SectionCard(title = "People and access", icon = IconKind.Circle) {
            val visibleInvitations = invitations.activeCareCircleRows()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${visibleInvitations.size} active contact(s)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (isPeopleEditing) {
                            "Remove stale invites or revoke access for accepted responders."
                        } else {
                            "Use Edit only when the Care Circle needs cleanup."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (visibleInvitations.isNotEmpty()) {
                    OutlinedButton(
                        enabled = !isBusy,
                        onClick = onTogglePeopleEditing,
                    ) {
                        LineIcon(
                            kind = if (isPeopleEditing) IconKind.CheckCircle else IconKind.Edit,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isPeopleEditing) "Done" else "Edit")
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (visibleInvitations.isEmpty()) {
                EmptyInline(
                    title = "No contacts yet",
                    body = "A plan should not rely on a pending or missing person. Add someone who can genuinely respond.",
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    visibleInvitations.forEach { invitation ->
                        InvitationCard(
                            invitation = invitation,
                            sprite = invitationSprites[invitation.id] ?: invitation.proposedScopes.responderSpriteFallback(),
                            maskedEmail = invitation.invitedEmailMasked.ifBlank {
                                invitationMaskedEmails[invitation.id].orEmpty()
                            },
                            isEditing = isPeopleEditing,
                            isBusy = isBusy,
                            onRemove = { onRemoveInvitation(invitation) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryScreen(
    selectedCat: CatRow?,
    plan: PlanRow?,
    incident: IncidentRow?,
    invitations: List<InvitationRow>,
    careCore: CareCoreDraft,
    auditEvents: List<AuditEventRow>,
    isBusy: Boolean,
    onRefresh: () -> Unit,
) {
    val readiness = coverageReadiness(careCore, invitations, plan, incident)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle(
            eyebrow = selectedCat?.name ?: "No cat selected",
            title = "History",
            body = "Every sensitive action should leave a clear trail: who acted, what changed and when access ended.",
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            OutlinedButton(
                enabled = !isBusy && selectedCat != null,
                onClick = onRefresh,
            ) {
                Text("Refresh history")
            }
        }
        SectionCard(title = "Current state", icon = IconKind.Shield) {
            SettingsOptionRow(
                icon = IconKind.Home,
                title = selectedCat?.let { "${it.name} profile" } ?: "No profile",
                body = selectedCat?.let { "Status: ${it.status.humanLabel()}" } ?: "Create a cat profile from Home.",
            )
            Spacer(modifier = Modifier.height(10.dp))
            SettingsOptionRow(
                icon = IconKind.Capsule,
                title = if (careCore.completionCount() == 3) "Capsule ready" else "Capsule in progress",
                body = "${careCore.completionCount()}/3 core care notes complete.",
            )
            Spacer(modifier = Modifier.height(10.dp))
            SettingsOptionRow(
                icon = IconKind.Circle,
                title = if (readiness.hasAcceptedCareResponder) "Care Circle ready" else "Care Circle pending",
                body = if (readiness.hasAcceptedCareResponder) {
                    "${readiness.acceptedResponderCount} accepted responder can help during an incident."
                } else if (invitations.isEmpty()) {
                    "No trusted contacts invited."
                } else {
                    "Waiting for acceptance."
                },
            )
            Spacer(modifier = Modifier.height(10.dp))
            SettingsOptionRow(
                icon = IconKind.Clock,
                title = plan?.statusLabel() ?: "No ritual",
                body = plan?.checkInSummary() ?: "No deadline yet.",
            )
            Spacer(modifier = Modifier.height(10.dp))
            SettingsOptionRow(
                icon = if (incident != null) IconKind.Alert else IconKind.CheckCircle,
                title = if (incident != null) "Incident active" else "No active incident",
                body = incident?.let { "${it.incidentState.humanLabel()} assigned to ${it.assignedRelationshipLabel}." }
                    ?: "No one currently has emergency handoff access.",
            )
        }
        SectionCard(title = "Trust log", icon = IconKind.History) {
            if (selectedCat == null) {
                EmptyInline(
                    title = "No cat selected",
                    body = "Create or select a cat to view its audit history.",
                )
            } else if (auditEvents.isEmpty()) {
                EmptyInline(
                    title = "No events loaded yet",
                    body = "Sensitive actions for ${selectedCat.name} will appear here after they are written by the backend.",
                )
            } else {
                groupedAuditEvents(auditEvents).forEachIndexed { groupIndex, group ->
                    if (groupIndex > 0) {
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                    Text(
                        text = group.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    group.events.forEachIndexed { eventIndex, event ->
                        if (eventIndex > 0) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                        }
                        AuditEventCard(event)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    selectedCat: CatRow?,
    signedInEmail: String,
    plan: PlanRow?,
    incident: IncidentRow?,
    isBusy: Boolean,
    onSignOut: () -> Unit,
    onTriggerMissedCheckIn: () -> Unit,
) {
    var logoTapCount by remember { mutableStateOf(0) }
    var debugToolsVisible by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle(
            eyebrow = selectedCat?.name ?: "No cat selected",
            title = "Settings",
            body = "Account, privacy and ritual preferences for the continuity plan.",
        )

        SectionCard(title = "Account", icon = IconKind.User) {
            SettingsOptionRow(
                icon = IconKind.UserCheck,
                title = signedInEmail,
                body = "Verified account for private Capsule access.",
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth(),
                onClick = onSignOut,
            ) {
                Text("Sign out")
            }
        }

        SectionCard(title = "Cat profiles", icon = IconKind.Home) {
            SettingsOptionRow(
                icon = IconKind.Home,
                title = selectedCat?.name ?: "No active profile",
                body = selectedCat?.let { "Status: ${it.status.humanLabel()}" } ?: "Create a cat profile from Home.",
            )
        }

        SectionCard(title = "Notification preferences", icon = IconKind.Bell) {
            SettingsOptionRow(
                icon = IconKind.Clock,
                title = "Check-in reminders",
                body = plan?.checkInSummary()
                    ?: "Shown once the ritual is active.",
            )
        }

        SectionCard(title = "Ritual schedule", icon = IconKind.Clock) {
            SettingsOptionRow(
                icon = IconKind.Shield,
                title = plan?.statusLabel() ?: "Not active",
                body = plan?.checkInSummary()
                    ?: "Activate the ritual after Capsule and Circle are ready.",
            )
            if (incident != null) {
                Spacer(modifier = Modifier.height(10.dp))
                IconPill(
                    text = "Incident active",
                    icon = IconKind.Alert,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        SectionCard(title = "Privacy and access", icon = IconKind.Lock) {
            SettingsOptionRow(
                icon = IconKind.Lock,
                title = "Scoped Capsule release",
                body = "Trusted people see only the sections already granted for an active incident.",
            )
            Spacer(modifier = Modifier.height(10.dp))
            SettingsOptionRow(
                icon = IconKind.History,
                title = "Audit history",
                body = "Sensitive activity is recorded as a human-readable trail.",
            )
        }

        SectionCard(title = "About The Third Bowl", icon = IconKind.Capsule) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier.clickable {
                        logoTapCount += 1
                        if (logoTapCount >= 5) {
                            debugToolsVisible = true
                        }
                    },
                ) {
                    BrandMark(size = 46)
                }
                Column {
                    Text(
                        text = "Continuity for the cat at home",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "A private plan for care, access and handoff trust.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (debugToolsVisible) {
            SectionCard(title = "Debug handoff trigger", icon = IconKind.Alert) {
                Text(
                    text = "Rehearsal tool: moves the selected cat's armed plan past its grace window and asks the backend processor to create a handoff. Do not present this as proof of the real timer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                TimelineRow(
                    label = "Selected cat",
                    value = selectedCat?.name ?: "No cat selected",
                    active = selectedCat != null,
                )
                TimelineRow(
                    label = "Plan",
                    value = plan?.let { "${it.statusLabel()} - ${it.nextCheckInAt?.humanDateTime() ?: "no deadline"}" } ?: "No plan",
                    active = plan?.status == "armed",
                )
                TimelineRow(
                    label = "Incident",
                    value = incident?.let { "${it.incidentState.humanLabel()} already active" } ?: "No active incident",
                    active = incident == null,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    enabled = !isBusy && selectedCat != null && plan?.status == "armed" && incident == null,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onTriggerMissedCheckIn,
                ) {
                    Text("Create debug handoff")
                }
            }
        }
    }
}

@Composable
private fun SettingsOptionRow(
    icon: IconKind,
    sprite: CatSpriteKind? = null,
    title: String,
    body: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (sprite != null) {
            CatSprite(
                kind = sprite,
                size = 42.dp,
                padding = 5.dp,
                contentDescription = "$title cat",
            )
        } else {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                LineIcon(
                    kind = icon,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NavIcon(
    kind: IconKind,
    selected: Boolean,
) {
    val tint = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .width(46.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        LineIcon(
            kind = kind,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun LineIcon(
    kind: IconKind,
    tint: Color,
    modifier: Modifier = Modifier.size(22.dp),
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val min = minOf(w, h)
        val strokeWidth = (min * 0.095f).coerceAtLeast(1.6f)
        val stroke = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        fun p(x: Float, y: Float) = Offset(w * x, h * y)

        when (kind) {
            IconKind.Home -> {
                drawLine(tint, p(0.14f, 0.52f), p(0.50f, 0.18f), strokeWidth, cap = StrokeCap.Round)
                drawLine(tint, p(0.50f, 0.18f), p(0.86f, 0.52f), strokeWidth, cap = StrokeCap.Round)
                drawRoundRect(
                    color = tint,
                    topLeft = p(0.25f, 0.47f),
                    size = androidx.compose.ui.geometry.Size(w * 0.50f, h * 0.38f),
                    cornerRadius = CornerRadius(min * 0.07f, min * 0.07f),
                    style = stroke,
                )
            }

            IconKind.Capsule -> {
                drawRoundRect(
                    color = tint,
                    topLeft = p(0.24f, 0.14f),
                    size = androidx.compose.ui.geometry.Size(w * 0.52f, h * 0.72f),
                    cornerRadius = CornerRadius(min * 0.10f, min * 0.10f),
                    style = stroke,
                )
                drawLine(tint, p(0.38f, 0.14f), p(0.38f, 0.86f), strokeWidth, cap = StrokeCap.Round)
                drawLine(tint, p(0.48f, 0.36f), p(0.66f, 0.36f), strokeWidth, cap = StrokeCap.Round)
                drawLine(tint, p(0.48f, 0.52f), p(0.64f, 0.52f), strokeWidth, cap = StrokeCap.Round)
            }

            IconKind.Circle -> {
                drawCircle(tint, radius = min * 0.13f, center = p(0.50f, 0.32f), style = stroke)
                drawCircle(tint, radius = min * 0.10f, center = p(0.27f, 0.40f), style = stroke)
                drawCircle(tint, radius = min * 0.10f, center = p(0.73f, 0.40f), style = stroke)
                drawLine(tint, p(0.34f, 0.72f), p(0.66f, 0.72f), strokeWidth, cap = StrokeCap.Round)
                drawLine(tint, p(0.15f, 0.76f), p(0.34f, 0.65f), strokeWidth, cap = StrokeCap.Round)
                drawLine(tint, p(0.66f, 0.65f), p(0.85f, 0.76f), strokeWidth, cap = StrokeCap.Round)
            }

            IconKind.History -> {
                drawCircle(tint, radius = min * 0.35f, center = p(0.52f, 0.52f), style = stroke)
                drawLine(tint, p(0.52f, 0.52f), p(0.52f, 0.32f), strokeWidth, cap = StrokeCap.Round)
                drawLine(tint, p(0.52f, 0.52f), p(0.68f, 0.60f), strokeWidth, cap = StrokeCap.Round)
                drawLine(tint, p(0.14f, 0.32f), p(0.28f, 0.32f), strokeWidth, cap = StrokeCap.Round)
                drawLine(tint, p(0.14f, 0.32f), p(0.14f, 0.18f), strokeWidth, cap = StrokeCap.Round)
            }

            IconKind.Settings -> {
                drawCircle(tint, radius = min * 0.20f, center = p(0.50f, 0.50f), style = stroke)
                listOf(
                    p(0.50f, 0.08f) to p(0.50f, 0.23f),
                    p(0.50f, 0.77f) to p(0.50f, 0.92f),
                    p(0.08f, 0.50f) to p(0.23f, 0.50f),
                    p(0.77f, 0.50f) to p(0.92f, 0.50f),
                    p(0.20f, 0.20f) to p(0.30f, 0.30f),
                    p(0.70f, 0.70f) to p(0.80f, 0.80f),
                    p(0.80f, 0.20f) to p(0.70f, 0.30f),
                    p(0.30f, 0.70f) to p(0.20f, 0.80f),
                ).forEach { (start, end) ->
                    drawLine(tint, start, end, strokeWidth, cap = StrokeCap.Round)
                }
            }

            IconKind.Shield -> {
                val path = Path().apply {
                    moveTo(w * 0.50f, h * 0.10f)
                    lineTo(w * 0.82f, h * 0.22f)
                    lineTo(w * 0.74f, h * 0.66f)
                    quadraticTo(w * 0.50f, h * 0.90f, w * 0.26f, h * 0.66f)
                    lineTo(w * 0.18f, h * 0.22f)
                    close()
                }
                drawPath(path, tint, style = stroke)
                drawLine(tint, p(0.36f, 0.52f), p(0.46f, 0.62f), strokeWidth, cap = StrokeCap.Round)
                drawLine(tint, p(0.46f, 0.62f), p(0.66f, 0.40f), strokeWidth, cap = StrokeCap.Round)
            }

            IconKind.Clock -> {
                drawCircle(tint, radius = min * 0.35f, center = p(0.50f, 0.50f), style = stroke)
                drawLine(tint, p(0.50f, 0.50f), p(0.50f, 0.30f), strokeWidth, cap = StrokeCap.Round)
                drawLine(tint, p(0.50f, 0.50f), p(0.64f, 0.58f), strokeWidth, cap = StrokeCap.Round)
            }

            IconKind.Alert -> {
                val path = Path().apply {
                    moveTo(w * 0.50f, h * 0.12f)
                    lineTo(w * 0.88f, h * 0.82f)
                    lineTo(w * 0.12f, h * 0.82f)
                    close()
                }
                drawPath(path, tint, style = stroke)
                drawLine(tint, p(0.50f, 0.34f), p(0.50f, 0.58f), strokeWidth, cap = StrokeCap.Round)
                drawCircle(tint, radius = min * 0.035f, center = p(0.50f, 0.70f))
            }

            IconKind.CheckCircle -> {
                drawCircle(tint, radius = min * 0.35f, center = p(0.50f, 0.50f), style = stroke)
                drawLine(tint, p(0.34f, 0.52f), p(0.46f, 0.64f), strokeWidth, cap = StrokeCap.Round)
                drawLine(tint, p(0.46f, 0.64f), p(0.70f, 0.38f), strokeWidth, cap = StrokeCap.Round)
            }

            IconKind.Lock -> {
                drawRoundRect(
                    color = tint,
                    topLeft = p(0.22f, 0.43f),
                    size = androidx.compose.ui.geometry.Size(w * 0.56f, h * 0.42f),
                    cornerRadius = CornerRadius(min * 0.08f, min * 0.08f),
                    style = stroke,
                )
                drawLine(tint, p(0.34f, 0.43f), p(0.34f, 0.31f), strokeWidth, cap = StrokeCap.Round)
                drawLine(tint, p(0.66f, 0.43f), p(0.66f, 0.31f), strokeWidth, cap = StrokeCap.Round)
                drawLine(tint, p(0.34f, 0.31f), p(0.50f, 0.20f), strokeWidth, cap = StrokeCap.Round)
                drawLine(tint, p(0.50f, 0.20f), p(0.66f, 0.31f), strokeWidth, cap = StrokeCap.Round)
            }

            IconKind.Key -> {
                drawCircle(tint, radius = min * 0.15f, center = p(0.30f, 0.48f), style = stroke)
                drawLine(tint, p(0.45f, 0.48f), p(0.86f, 0.48f), strokeWidth, cap = StrokeCap.Round)
                drawLine(tint, p(0.70f, 0.48f), p(0.70f, 0.64f), strokeWidth, cap = StrokeCap.Round)
                drawLine(tint, p(0.82f, 0.48f), p(0.82f, 0.60f), strokeWidth, cap = StrokeCap.Round)
            }

            IconKind.Cross -> {
                drawLine(tint, p(0.50f, 0.18f), p(0.50f, 0.82f), strokeWidth, cap = StrokeCap.Round)
                drawLine(tint, p(0.18f, 0.50f), p(0.82f, 0.50f), strokeWidth, cap = StrokeCap.Round)
            }

            IconKind.User -> {
                drawCircle(tint, radius = min * 0.16f, center = p(0.50f, 0.34f), style = stroke)
                drawRoundRect(
                    color = tint,
                    topLeft = p(0.25f, 0.58f),
                    size = androidx.compose.ui.geometry.Size(w * 0.50f, h * 0.25f),
                    cornerRadius = CornerRadius(min * 0.16f, min * 0.16f),
                    style = stroke,
                )
            }

            IconKind.UserCheck -> {
                drawCircle(tint, radius = min * 0.14f, center = p(0.38f, 0.34f), style = stroke)
                drawLine(tint, p(0.18f, 0.78f), p(0.55f, 0.78f), strokeWidth, cap = StrokeCap.Round)
                drawLine(tint, p(0.62f, 0.48f), p(0.72f, 0.60f), strokeWidth, cap = StrokeCap.Round)
                drawLine(tint, p(0.72f, 0.60f), p(0.88f, 0.38f), strokeWidth, cap = StrokeCap.Round)
            }

            IconKind.MapPin -> {
                val path = Path().apply {
                    moveTo(w * 0.50f, h * 0.90f)
                    cubicTo(w * 0.22f, h * 0.58f, w * 0.22f, h * 0.20f, w * 0.50f, h * 0.16f)
                    cubicTo(w * 0.78f, h * 0.20f, w * 0.78f, h * 0.58f, w * 0.50f, h * 0.90f)
                }
                drawPath(path, tint, style = stroke)
                drawCircle(tint, radius = min * 0.11f, center = p(0.50f, 0.42f), style = stroke)
            }

            IconKind.Bowl -> {
                drawLine(tint, p(0.18f, 0.48f), p(0.82f, 0.48f), strokeWidth, cap = StrokeCap.Round)
                drawRoundRect(
                    color = tint,
                    topLeft = p(0.24f, 0.48f),
                    size = androidx.compose.ui.geometry.Size(w * 0.52f, h * 0.28f),
                    cornerRadius = CornerRadius(min * 0.18f, min * 0.18f),
                    style = stroke,
                )
                drawCircle(tint, radius = min * 0.04f, center = p(0.40f, 0.32f))
                drawCircle(tint, radius = min * 0.04f, center = p(0.55f, 0.26f))
            }

            IconKind.HouseSearch -> {
                drawLine(tint, p(0.15f, 0.48f), p(0.42f, 0.22f), strokeWidth, cap = StrokeCap.Round)
                drawLine(tint, p(0.42f, 0.22f), p(0.68f, 0.48f), strokeWidth, cap = StrokeCap.Round)
                drawRoundRect(
                    color = tint,
                    topLeft = p(0.24f, 0.46f),
                    size = androidx.compose.ui.geometry.Size(w * 0.38f, h * 0.34f),
                    cornerRadius = CornerRadius(min * 0.06f, min * 0.06f),
                    style = stroke,
                )
                drawCircle(tint, radius = min * 0.13f, center = p(0.72f, 0.68f), style = stroke)
                drawLine(tint, p(0.81f, 0.77f), p(0.90f, 0.86f), strokeWidth, cap = StrokeCap.Round)
            }

            IconKind.Bell -> {
                drawLine(tint, p(0.30f, 0.70f), p(0.70f, 0.70f), strokeWidth, cap = StrokeCap.Round)
                drawLine(tint, p(0.34f, 0.70f), p(0.34f, 0.42f), strokeWidth, cap = StrokeCap.Round)
                drawLine(tint, p(0.66f, 0.70f), p(0.66f, 0.42f), strokeWidth, cap = StrokeCap.Round)
                drawLine(tint, p(0.34f, 0.42f), p(0.50f, 0.24f), strokeWidth, cap = StrokeCap.Round)
                drawLine(tint, p(0.50f, 0.24f), p(0.66f, 0.42f), strokeWidth, cap = StrokeCap.Round)
                drawCircle(tint, radius = min * 0.04f, center = p(0.50f, 0.82f))
            }

            IconKind.Edit -> {
                drawLine(tint, p(0.28f, 0.72f), p(0.72f, 0.28f), strokeWidth, cap = StrokeCap.Round)
                drawLine(tint, p(0.64f, 0.20f), p(0.80f, 0.36f), strokeWidth, cap = StrokeCap.Round)
                drawLine(tint, p(0.22f, 0.78f), p(0.34f, 0.74f), strokeWidth, cap = StrokeCap.Round)
                drawLine(tint, p(0.22f, 0.78f), p(0.26f, 0.66f), strokeWidth, cap = StrokeCap.Round)
            }

            IconKind.Trash -> {
                drawLine(tint, p(0.24f, 0.30f), p(0.76f, 0.30f), strokeWidth, cap = StrokeCap.Round)
                drawLine(tint, p(0.40f, 0.20f), p(0.60f, 0.20f), strokeWidth, cap = StrokeCap.Round)
                drawLine(tint, p(0.45f, 0.20f), p(0.40f, 0.30f), strokeWidth, cap = StrokeCap.Round)
                drawLine(tint, p(0.55f, 0.20f), p(0.60f, 0.30f), strokeWidth, cap = StrokeCap.Round)
                drawRoundRect(
                    color = tint,
                    topLeft = p(0.30f, 0.34f),
                    size = androidx.compose.ui.geometry.Size(w * 0.40f, h * 0.46f),
                    cornerRadius = CornerRadius(min * 0.05f, min * 0.05f),
                    style = stroke,
                )
                drawLine(tint, p(0.43f, 0.46f), p(0.43f, 0.68f), strokeWidth, cap = StrokeCap.Round)
                drawLine(tint, p(0.57f, 0.46f), p(0.57f, 0.68f), strokeWidth, cap = StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun IconPill(
    text: String,
    icon: IconKind,
    color: Color,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LineIcon(
            kind = icon,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

@Composable
private fun SpritePill(
    text: String,
    sprite: CatSpriteKind,
    color: Color,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CatSprite(
            kind = sprite,
            size = 26.dp,
            padding = 3.dp,
            contentDescription = null,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

@Composable
private fun PrimaryActionButton(
    text: String,
    icon: IconKind,
    enabled: Boolean,
    isBusy: Boolean,
    onClick: () -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.primary,
) {
    val scale by animateFloatAsState(
        targetValue = if (isBusy) 0.98f else 1f,
        label = "primary-action-scale",
    )
    Button(
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .scale(scale),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor),
        onClick = onClick,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                LineIcon(
                    kind = icon,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(text)
        }
    }
}

@Composable
private fun CatPortrait(
    name: String,
    fallbackSprite: CatSpriteKind = CatSpriteKind.Neutral,
    size: Dp = 84.dp,
    modifier: Modifier = Modifier,
) {
    CatSprite(
        kind = fallbackSprite,
        size = size,
        contentDescription = "$name profile illustration",
        modifier = modifier,
    )
}

@Composable
private fun CatIllustration(
    modifier: Modifier = Modifier.size(84.dp),
) {
    val face = MaterialTheme.colorScheme.primaryContainer
    val ink = MaterialTheme.colorScheme.primary
    val clay = MaterialTheme.colorScheme.tertiary
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val min = minOf(w, h)
        val strokeWidth = min * 0.045f

        val leftEar = Path().apply {
            moveTo(w * 0.23f, h * 0.28f)
            lineTo(w * 0.32f, h * 0.06f)
            lineTo(w * 0.44f, h * 0.30f)
            close()
        }
        val rightEar = Path().apply {
            moveTo(w * 0.56f, h * 0.30f)
            lineTo(w * 0.68f, h * 0.06f)
            lineTo(w * 0.77f, h * 0.28f)
            close()
        }
        drawPath(leftEar, face)
        drawPath(rightEar, face)
        drawPath(leftEar, clay.copy(alpha = 0.18f), style = Stroke(width = strokeWidth))
        drawPath(rightEar, clay.copy(alpha = 0.18f), style = Stroke(width = strokeWidth))
        drawCircle(face, radius = min * 0.38f, center = Offset(w * 0.50f, h * 0.52f))
        drawCircle(ink, radius = min * 0.035f, center = Offset(w * 0.38f, h * 0.48f))
        drawCircle(ink, radius = min * 0.035f, center = Offset(w * 0.62f, h * 0.48f))
        drawCircle(clay, radius = min * 0.035f, center = Offset(w * 0.50f, h * 0.58f))
        drawLine(ink.copy(alpha = 0.75f), Offset(w * 0.18f, h * 0.56f), Offset(w * 0.38f, h * 0.58f), strokeWidth, cap = StrokeCap.Round)
        drawLine(ink.copy(alpha = 0.75f), Offset(w * 0.18f, h * 0.66f), Offset(w * 0.38f, h * 0.62f), strokeWidth, cap = StrokeCap.Round)
        drawLine(ink.copy(alpha = 0.75f), Offset(w * 0.62f, h * 0.58f), Offset(w * 0.82f, h * 0.56f), strokeWidth, cap = StrokeCap.Round)
        drawLine(ink.copy(alpha = 0.75f), Offset(w * 0.62f, h * 0.62f), Offset(w * 0.82f, h * 0.66f), strokeWidth, cap = StrokeCap.Round)
    }
}

@Composable
private fun CareNoteField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    supportingText: String,
    icon: IconKind,
    minLines: Int = 3,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = false,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    LineIcon(
                        kind = icon,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(19.dp),
                    )
                }
                Column {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(placeholder) },
                minLines = minLines,
                singleLine = singleLine,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
            )
        }
    }
}

@Composable
private fun AppHeader(
    selectedCat: CatRow?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(size = 44)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "The Third Bowl",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = selectedCat?.let { "${it.name}'s continuity ritual" } ?: "Continuity for the cat at home",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            LineIcon(
                kind = IconKind.User,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun ContinuityStatusCard(
    cat: CatRow,
    careCore: CareCoreDraft,
    invitations: List<InvitationRow>,
    plan: PlanRow?,
    incident: IncidentRow?,
    isBusy: Boolean,
    onArmPlan: () -> Unit,
    onCheckIn: () -> Unit,
    onRefreshCoverage: () -> Unit,
    onGoToCapsule: () -> Unit,
    onGoToCircle: () -> Unit,
) {
    val readiness = coverageReadiness(careCore, invitations, plan, incident)
    val state = continuityState(cat.name, plan, incident, readiness)
    val checkInOverdue = plan?.isPastGraceWindow() == true && incident == null
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = state.container),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    CatPortrait(
                        name = cat.name,
                        fallbackSprite = state.sprite,
                    )
                    Column {
                        Text(
                            text = cat.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Care continuity",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconPill(text = state.badge, icon = state.icon, color = state.accent)
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = state.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HomeSignalRow(
                careCore = careCore,
                readiness = readiness,
                plan = plan,
            )

            if (incident != null) {
                PrimaryActionButton(
                    text = "Refresh handoff status",
                    icon = IconKind.Alert,
                    enabled = !isBusy,
                    isBusy = isBusy,
                    containerColor = state.accent,
                    onClick = onRefreshCoverage,
                )
            } else if (checkInOverdue) {
                PrimaryActionButton(
                    text = "Refresh incident status",
                    icon = IconKind.Alert,
                    enabled = !isBusy,
                    isBusy = isBusy,
                    containerColor = state.accent,
                    onClick = onRefreshCoverage,
                )
            } else if (plan?.status == "armed") {
                PrimaryActionButton(
                    text = "Confirm availability",
                    icon = IconKind.CheckCircle,
                    enabled = !isBusy,
                    isBusy = isBusy,
                    containerColor = state.accent,
                    onClick = onCheckIn,
                )
            } else if (!readiness.coreCareComplete) {
                PrimaryActionButton(
                    text = "Finish Capsule",
                    icon = IconKind.Capsule,
                    enabled = !isBusy,
                    isBusy = isBusy,
                    containerColor = state.accent,
                    onClick = onGoToCapsule,
                )
            } else if (!readiness.hasAcceptedCareResponder) {
                PrimaryActionButton(
                    text = "Add a responder",
                    icon = IconKind.UserCheck,
                    enabled = !isBusy,
                    isBusy = isBusy,
                    containerColor = state.accent,
                    onClick = onGoToCircle,
                )
            } else {
                PrimaryActionButton(
                    text = "Activate ritual",
                    icon = IconKind.Shield,
                    enabled = !isBusy && readiness.canActivateRitual,
                    isBusy = isBusy,
                    containerColor = state.accent,
                    onClick = onArmPlan,
                )
            }
        }
    }
}

@Composable
private fun HomeSignalRow(
    careCore: CareCoreDraft,
    readiness: CoverageReadiness,
    plan: PlanRow?,
) {
    val ritualOverdue = plan?.isPastGraceWindow() == true
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CompactSignal(
            modifier = Modifier.weight(1f),
            icon = IconKind.Capsule,
            title = "Capsule",
            value = if (careCore.completionCount() == 3) "Ready" else "${careCore.completionCount()}/3",
            active = careCore.completionCount() == 3,
        )
        CompactSignal(
            modifier = Modifier.weight(1f),
            icon = IconKind.UserCheck,
            title = "Responder",
            value = if (readiness.hasAcceptedCareResponder) "Ready" else "Waiting",
            active = readiness.hasAcceptedCareResponder,
        )
        CompactSignal(
            modifier = Modifier.weight(1f),
            icon = IconKind.Shield,
            title = "Ritual",
            value = when {
                ritualOverdue -> "Missed"
                plan?.status == "armed" -> "Active"
                else -> "Off"
            },
            active = plan?.status == "armed" && !ritualOverdue,
        )
    }
}

@Composable
private fun CompactSignal(
    modifier: Modifier,
    icon: IconKind,
    title: String,
    value: String,
    active: Boolean,
) {
    val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LineIcon(
            kind = icon,
            tint = color,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ReadinessGrid(
    careCore: CareCoreDraft,
    invitations: List<InvitationRow>,
    plan: PlanRow?,
    onGoToCapsule: () -> Unit,
    onGoToCircle: () -> Unit,
) {
    val readiness = coverageReadiness(careCore, invitations, plan, incident = null)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ReadinessCard(
            icon = IconKind.Capsule,
            title = "Capsule essentials",
            body = if (careCore.completionCount() == 3) {
                "Core care is ready for controlled sharing."
            } else {
                "Complete food, hiding places and never-do notes."
            },
            progress = careCore.completionCount() / 3f,
            action = "Edit Capsule",
            onAction = onGoToCapsule,
        )
        ReadinessCard(
            icon = IconKind.UserCheck,
            title = "Trusted people",
            body = if (readiness.hasAcceptedCareResponder) {
                "${readiness.acceptedResponderCount} accepted responder(s) can see core care during an incident."
            } else {
                "Add an accepted person with core care access before relying on escalation."
            },
            progress = if (readiness.hasAcceptedCareResponder) 1f else 0f,
            action = "Open Circle",
            onAction = onGoToCircle,
        )
        ReadinessCard(
            icon = IconKind.Shield,
            title = "Ritual",
            body = if (readiness.canActivateRitual || plan?.status == "armed") {
                plan?.let { "Current state: ${it.statusLabel()}." } ?: "Ready to activate the recurring check-in ritual."
            } else {
                "Finish the missing requirements before this can be trusted."
            },
            progress = if (plan?.status == "armed") 1f else 0.35f,
            action = "Review above",
            onAction = {},
            actionEnabled = false,
        )
    }
}

@Composable
private fun ReadinessCard(
    icon: IconKind,
    title: String,
    body: String,
    progress: Float,
    action: String,
    onAction: () -> Unit,
    actionEnabled: Boolean = true,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LineIcon(
                        kind = icon,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                TextButton(enabled = actionEnabled, onClick = onAction) {
                    Text(action)
                }
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CatSwitcher(
    cats: List<CatRow>,
    selectedCatId: String?,
    isBusy: Boolean,
    onSelectCat: (CatRow) -> Unit,
) {
    if (cats.size <= 1) return

    SectionCard(title = "Cats") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            cats.forEach { cat ->
                AssistChip(
                    onClick = { onSelectCat(cat) },
                    enabled = !isBusy,
                    label = { Text(if (cat.id == selectedCatId) "${cat.name} selected" else cat.name) },
                )
            }
        }
    }
}

@Composable
private fun CompletionCard(careCore: CareCoreDraft) {
    val completed = careCore.completionCount()
    SectionCard(title = "Capsule readiness", icon = IconKind.Capsule) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CatSprite(
                kind = if (completed == 3) CatSpriteKind.Happy else CatSpriteKind.Sleepy,
                size = 58.dp,
                contentDescription = if (completed == 3) "Capsule ready cat" else "Capsule pending cat",
            )
            IconPill(
                text = if (completed == 3) "Core care ready" else "$completed/3 complete",
                icon = if (completed == 3) IconKind.CheckCircle else IconKind.Clock,
                color = if (completed == 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { completed / 3f },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (completed == 3) {
                "Core care is ready for a verified responder during an active incident."
            } else {
                "Keep each note practical: a responder should know what to do without reading a wall of text."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InvitationCard(
    invitation: InvitationRow,
    sprite: CatSpriteKind,
    maskedEmail: String,
    isEditing: Boolean,
    isBusy: Boolean,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ContactAvatar(sprite = sprite)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = invitation.relationshipLabel,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = invitation.proposedRole.humanLabel().ifBlank { "Trusted contact" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val emailLine = maskedEmail.ifBlank {
                            if (invitation.status.equals("pending", ignoreCase = true)) {
                                "Invited email unavailable"
                            } else {
                                ""
                            }
                        }
                        if (emailLine.isNotBlank()) {
                            Text(
                                text = emailLine,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                IconPill(
                    text = invitation.status.lowercase().replaceFirstChar { it.uppercase() },
                    icon = if (invitation.status.equals("accepted", ignoreCase = true)) IconKind.UserCheck else IconKind.Clock,
                    color = if (invitation.status.equals("accepted", ignoreCase = true)) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.secondary
                    },
                )
            }
            Text(
                text = "Can see during incident:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ScopePillRow(scopes = invitation.proposedScopes)
            Text(
                text = "Expires ${invitation.expiresAt.humanDateTime()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isEditing) {
                val accepted = invitation.status.equals("accepted", ignoreCase = true)
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBusy,
                    onClick = onRemove,
                ) {
                    LineIcon(
                        kind = IconKind.Trash,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (accepted) "Revoke access" else "Cancel invite",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResponderLogoPickerSummary(
    selected: CatSpriteKind,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    OutlinedButton(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        onClick = onToggle,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CatSprite(
                    kind = selected,
                    size = 38.dp,
                    padding = 4.dp,
                    contentDescription = "${selected.logoLabel()} selected responder logo",
                )
                Text(
                    text = "Responder logo",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = if (expanded) "Hide logos" else "Choose logo",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ResponderLogoSelector(
    selected: CatSpriteKind,
    onSelected: (CatSpriteKind) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CareCircleResponderSprites.chunked(3).forEach { rowSprites ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowSprites.forEach { sprite ->
                    val isSelected = sprite == selected
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(78.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
                                },
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                shape = RoundedCornerShape(18.dp),
                            )
                            .clickable { onSelected(sprite) }
                            .padding(vertical = 10.dp, horizontal = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CatSprite(
                            kind = sprite,
                            size = 56.dp,
                            padding = 5.dp,
                            contentDescription = "${sprite.logoLabel()} responder logo",
                        )
                    }
                }
                repeat(3 - rowSprites.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AccessTemplateSelector(
    selected: CareCircleAccessTemplate,
    onSelected: (CareCircleAccessTemplate) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CareCircleAccessTemplate.entries.forEach { template ->
            val isSelected = template == selected
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelected(template) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        CatSprite(
                            kind = template.sprite,
                            size = 38.dp,
                            contentDescription = "${template.title} cat",
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = template.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = template.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        IconPill(
                            text = template.sensitivity,
                            icon = if (template.scopes.any { it == "HOME_ACCESS" }) IconKind.Key else if (template.scopes.any { it == "MEDICAL" }) IconKind.Cross else IconKind.Lock,
                            color = if (template.scopes.size > 1) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        ScopePillRow(scopes = template.scopes)
                    }
                    if (isSelected) {
                        LineIcon(
                            kind = IconKind.CheckCircle,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactAvatar(sprite: CatSpriteKind) {
    CatSprite(
        kind = sprite,
        size = 48.dp,
        contentDescription = "${sprite.logoLabel()} trusted responder cat",
    )
}

@Composable
private fun ScopePillRow(scopes: List<String>) {
    val visibleScopes = scopes.ifEmpty { listOf("CARE_CORE") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        visibleScopes.forEach { scope ->
            SpritePill(
                text = scope.humanLabel(),
                sprite = scope.spriteKind(),
                color = scope.scopeColor(),
            )
        }
    }
}

@Composable
private fun AuditEventCard(event: AuditEventRow) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(event.outcome.auditColor().copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            LineIcon(
                kind = event.auditIcon(),
                tint = event.outcome.auditColor(),
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = event.auditTitle(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (!event.outcome.equals("success", ignoreCase = true)) {
                    Spacer(modifier = Modifier.width(10.dp))
                    IconPill(
                        text = event.outcome.humanLabel(),
                        icon = IconKind.Alert,
                        color = event.outcome.auditColor(),
                    )
                }
            }
            Text(
                text = event.auditDetail(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = event.occurredAt.humanTime(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class AuditEventGroup(
    val label: String,
    val events: List<AuditEventRow>,
)

private fun groupedAuditEvents(events: List<AuditEventRow>): List<AuditEventGroup> {
    return events
        .groupBy { it.occurredAt.humanDateGroup() }
        .map { (label, groupedEvents) -> AuditEventGroup(label, groupedEvents) }
}

private fun AuditEventRow.auditTitle(): String {
    return when (eventType.lowercase()) {
        "capsule_section.updated" -> "Capsule updated"
        "capsule_section.created" -> "Care note added"
        "invitation.created" -> "Contact invited"
        "invitation.accepted" -> "Contact accepted"
        "plan.armed" -> "Ritual activated"
        "check_in.completed" -> "Availability confirmed"
        "incident.created" -> "Incident opened"
        "incident.resolved" -> "Incident resolved"
        else -> eventType.humanLabel()
    }
}

private fun AuditEventRow.auditDetail(): String {
    return when (eventType.lowercase()) {
        "capsule_section.updated" -> "Care instructions were updated for a scoped Capsule section."
        "capsule_section.created" -> "A new care note became available for authorized release."
        "invitation.created" -> "A trusted contact was invited to join the Care Circle."
        "invitation.accepted" -> "A trusted contact accepted the role and can help if needed."
        "plan.armed" -> "The check-in ritual started watching for missed availability."
        "check_in.completed" -> "The caregiver confirmed they can still care for the cat."
        "incident.created" -> "A missed check-in opened a handoff workflow."
        "incident.resolved" -> "The handoff was closed and temporary access ended."
        else -> "${actorType.humanLabel()} acted on ${targetType.humanLabel()}."
    }
}

private fun AuditEventRow.auditIcon(): IconKind {
    return when {
        eventType.contains("capsule", ignoreCase = true) -> IconKind.Capsule
        eventType.contains("invitation", ignoreCase = true) -> IconKind.UserCheck
        eventType.contains("plan", ignoreCase = true) -> IconKind.Shield
        eventType.contains("check_in", ignoreCase = true) -> IconKind.CheckCircle
        eventType.contains("incident", ignoreCase = true) -> IconKind.Alert
        else -> IconKind.History
    }
}

@Composable
private fun TrustChecklist() {
    SectionCard(title = "What this protects") {
        TimelineRow(label = "Private by default", value = "Care details stay scoped.", active = true)
        TimelineRow(label = "Verified humans", value = "Contacts must prove email ownership.", active = true)
        TimelineRow(label = "No guessing", value = "Every handoff should be accepted, timed and auditable.", active = true)
    }
}

@Composable
private fun ScreenTitle(eyebrow: String, title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = eyebrow,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: IconKind? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (icon != null) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        LineIcon(
                            kind = icon,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun ExpandableSectionCard(
    title: String,
    summary: String,
    icon: IconKind? = null,
    sprite: CatSpriteKind? = null,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (sprite != null) {
                        CatSprite(
                            kind = sprite,
                            size = 46.dp,
                            padding = 5.dp,
                            contentDescription = "$title cat",
                        )
                    } else if (icon != null) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            LineIcon(
                                kind = icon,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                OutlinedButton(onClick = onToggle) {
                    Text(if (expanded) "Editing" else "Edit")
                }
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(6.dp))
                content()
            }
        }
    }
}

@Composable
private fun EmptyStateCard(title: String, body: String) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CatSprite(
                kind = CatSpriteKind.Neutral,
                size = 64.dp,
                contentDescription = null,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyInline(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp),
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusBanner(status: UiStatus, isBusy: Boolean) {
    val container = when (status) {
        is UiStatus.Error -> MaterialTheme.colorScheme.errorContainer
        is UiStatus.Success -> MaterialTheme.colorScheme.primaryContainer
        is UiStatus.Info -> MaterialTheme.colorScheme.secondaryContainer
    }
    val content = when (status) {
        is UiStatus.Error -> MaterialTheme.colorScheme.onErrorContainer
        is UiStatus.Success -> MaterialTheme.colorScheme.onPrimaryContainer
        is UiStatus.Info -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(container)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (isBusy) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = content,
            )
        }
        Text(
            text = status.message,
            style = MaterialTheme.typography.bodyMedium,
            color = content,
        )
    }
}

@Composable
private fun TimelineRow(label: String, value: String, active: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(12.dp)
                .clip(CircleShape)
                .background(
                    if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MetricChip(label: String, value: String) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CatSprite(
    kind: CatSpriteKind,
    size: Dp = 84.dp,
    padding: Dp = 8.dp,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            )
            .padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = kind.drawableRes),
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun BrandMark(size: Int = 64) {
    CatSprite(
        kind = CatSpriteKind.Neutral,
        size = size.dp,
        padding = (size * 0.10f).dp,
        contentDescription = "The Third Bowl neutral cat logo",
    )
}

private fun continuityState(
    catName: String,
    plan: PlanRow?,
    incident: IncidentRow?,
    readiness: CoverageReadiness,
): ContinuityVisualState {
    if (incident != null) {
        return ContinuityVisualState(
            title = "${catName} needs a handoff",
            body = "An incident is active and assigned to ${incident.assignedRelationshipLabel}. Keep the handoff explicit.",
            badge = "Incident",
            accent = Color(0xFFB42318),
            container = Color(0xFFFFF0ED),
            icon = IconKind.Alert,
            sprite = CatSpriteKind.Alert,
        )
    }

    if (plan?.isPastGraceWindow() == true) {
        return ContinuityVisualState(
            title = "${catName} missed a check-in",
            body = plan.nextCheckInAt?.let { "The check-in window passed ${it.humanCheckInDeadline()}. The incident processor should open a handoff before this plan can be considered covered again." }
                ?: "The ritual is armed but its deadline is missing, so the plan needs review.",
            badge = "Overdue",
            accent = Color(0xFFB42318),
            container = Color(0xFFFFF0ED),
            icon = IconKind.Alert,
            sprite = CatSpriteKind.Alert,
        )
    }

    return if (plan?.status == "armed" && readiness.isReliable) {
        ContinuityVisualState(
            title = "${catName} is covered",
            body = plan.nextCheckInAt?.let { "Next check-in: ${it.humanCheckInDeadline()}. Your ritual is active and a trusted responder can step in if something fails." }
                ?: "The ritual is armed, but no next deadline was returned.",
            badge = "Covered",
            accent = Color(0xFF1F7A5A),
            container = Color(0xFFEAF7EF),
            icon = IconKind.Shield,
            sprite = CatSpriteKind.Happy,
        )
    } else if (plan?.status == "armed") {
        ContinuityVisualState(
            title = "${catName}'s coverage needs attention",
            body = readiness.body,
            badge = "Review",
            accent = Color(0xFF8A5A00),
            container = Color(0xFFFFF6DB),
            icon = IconKind.Clock,
            sprite = CatSpriteKind.Sleepy,
        )
    } else if (!readiness.canActivateRitual) {
        ContinuityVisualState(
            title = readiness.title,
            body = readiness.body,
            badge = "Setup",
            accent = readiness.accent,
            container = Color(0xFFFFF6DB),
            icon = IconKind.Clock,
            sprite = CatSpriteKind.Sleepy,
        )
    } else {
        ContinuityVisualState(
            title = "${catName} is ready for coverage",
            body = "The core routine and a verified responder are in place. Activate the ritual when you are ready.",
            badge = "Ready",
            accent = Color(0xFF1F7A5A),
            container = Color(0xFFEAF7EF),
            icon = IconKind.CheckCircle,
            sprite = CatSpriteKind.Happy,
        )
    }
}

private fun coverageReadiness(
    careCore: CareCoreDraft,
    invitations: List<InvitationRow>,
    plan: PlanRow?,
    incident: IncidentRow?,
): CoverageReadiness {
    val coreCount = careCore.completionCount()
    val coreCareComplete = coreCount == 3
    val checkInOverdue = plan?.isPastGraceWindow() == true && incident == null
    val acceptedResponders = invitations.filter { invitation ->
        invitation.status.equals("accepted", ignoreCase = true) &&
            invitation.proposedScopes.any { scope -> scope.equals("CARE_CORE", ignoreCase = true) }
    }
    val hasAcceptedCareResponder = acceptedResponders.isNotEmpty()
    val requirements = listOf(
        CoverageRequirement(
            label = "Capsule",
            detail = if (coreCareComplete) "Core care is complete" else "$coreCount/3 essentials complete",
            complete = coreCareComplete,
        ),
        CoverageRequirement(
            label = "Responder",
            detail = if (hasAcceptedCareResponder) {
                "${acceptedResponders.size} accepted responder(s)"
            } else {
                "No accepted core care responder yet"
            },
            complete = hasAcceptedCareResponder,
        ),
        CoverageRequirement(
            label = "Ritual",
            detail = if (checkInOverdue) {
                plan?.nextCheckInAt?.let { "Missed check-in ${it.humanDateTime(short = true)}" } ?: "Missed deadline"
            } else if (plan?.status == "armed") {
                plan.nextCheckInAt?.let { "Next check-in ${it.humanDateTime(short = true)}" } ?: "Armed without a deadline"
            } else {
                "Not active yet"
            },
            complete = plan?.status == "armed" && !checkInOverdue,
        ),
    )
    val canActivate = coreCareComplete && hasAcceptedCareResponder && incident == null

    return CoverageReadiness(
        title = when {
            incident != null -> "Incident in progress"
            checkInOverdue -> "Missed check-in pending"
            !coreCareComplete -> "Finish the care essentials"
            !hasAcceptedCareResponder -> "Add a verified responder"
            else -> "Ready to activate"
        },
        body = when {
            incident != null -> "An incident is already active. Keep the handoff explicit before changing the ritual."
            checkInOverdue -> "The check-in window passed, but no active incident is loaded yet. Refresh after the server-side processor runs."
            !coreCareComplete -> "Complete food, hiding places and never-do notes before relying on escalation."
            !hasAcceptedCareResponder -> "Invite a real person and wait for acceptance before this plan can protect the cat."
            else -> "The minimum pieces are ready: core care and an accepted responder."
        },
        qualityLabel = when {
            incident != null -> "Incident active"
            checkInOverdue -> "Check-in missed"
            plan?.status == "armed" && coreCareComplete && hasAcceptedCareResponder -> "Reliable coverage"
            plan?.status == "armed" -> "Coverage has gaps"
            canActivate -> "Ready for coverage"
            else -> "Not reliable yet"
        },
        coreCareComplete = coreCareComplete,
        hasAcceptedCareResponder = hasAcceptedCareResponder,
        acceptedResponderCount = acceptedResponders.size,
        canActivateRitual = canActivate,
        isReliable = plan?.status == "armed" && coreCareComplete && hasAcceptedCareResponder && !checkInOverdue,
        requirements = requirements,
        accent = if (checkInOverdue) {
            Color(0xFFB42318)
        } else if (canActivate || (plan?.status == "armed" && coreCareComplete && hasAcceptedCareResponder)) {
            Color(0xFF1F7A5A)
        } else {
            Color(0xFF8A5A00)
        },
    )
}

private fun List<InvitationRow>.activeCareCircleRows(): List<InvitationRow> {
    return filter { invitation ->
        invitation.status.equals("pending", ignoreCase = true) ||
            invitation.status.equals("accepted", ignoreCase = true)
    }
}

private data class CoverageReadiness(
    val title: String,
    val body: String,
    val qualityLabel: String,
    val coreCareComplete: Boolean,
    val hasAcceptedCareResponder: Boolean,
    val acceptedResponderCount: Int,
    val canActivateRitual: Boolean,
    val isReliable: Boolean,
    val requirements: List<CoverageRequirement>,
    val accent: Color,
)

private data class CoverageRequirement(
    val label: String,
    val detail: String,
    val complete: Boolean,
)

private data class ContinuityVisualState(
    val title: String,
    val body: String,
    val badge: String,
    val accent: Color,
    val container: Color,
    val icon: IconKind,
    val sprite: CatSpriteKind,
)

private enum class AppTab(
    val label: String,
    val icon: IconKind,
) {
    Home("Home", IconKind.Home),
    Capsule("Capsule", IconKind.Capsule),
    Circle("Circle", IconKind.Circle),
    History("History", IconKind.History),
    Settings("Settings", IconKind.Settings),
}

private enum class CatSpriteKind(val drawableRes: Int) {
    Alert(R.drawable.cat_alert),
    Cute(R.drawable.cat_cute),
    Dizzy(R.drawable.cat_dizzy),
    Guard(R.drawable.cat_guard),
    Happy(R.drawable.cat_happy),
    Key(R.drawable.cat_key),
    Medic(R.drawable.cat_medic),
    Neutral(R.drawable.cat_neutral),
    Sleepy(R.drawable.cat_sleepy),
}

private val CareCircleResponderSprites = listOf(
    CatSpriteKind.Guard,
    CatSpriteKind.Key,
    CatSpriteKind.Medic,
    CatSpriteKind.Happy,
    CatSpriteKind.Cute,
    CatSpriteKind.Neutral,
    CatSpriteKind.Alert,
    CatSpriteKind.Sleepy,
    CatSpriteKind.Dizzy,
)

private class ResponderSpriteStore(context: Context) {
    private val preferences = context.getSharedPreferences("care_circle_responder_sprites", Context.MODE_PRIVATE)

    fun load(): Map<String, CatSpriteKind> {
        return preferences.all.mapNotNull { (key, value) ->
            if (!key.startsWith(KeyPrefix) || value !is String) return@mapNotNull null
            val sprite = CatSpriteKind.entries.firstOrNull { it.name == value } ?: return@mapNotNull null
            key.removePrefix(KeyPrefix) to sprite
        }.toMap()
    }

    fun save(invitationId: String, sprite: CatSpriteKind) {
        preferences.edit().putString("$KeyPrefix$invitationId", sprite.name).apply()
    }

    fun remove(invitationId: String) {
        preferences.edit().remove("$KeyPrefix$invitationId").apply()
    }

    private companion object {
        const val KeyPrefix = "invitation:"
    }
}

private class InvitationEmailStore(context: Context) {
    private val preferences = context.getSharedPreferences("care_circle_invitation_emails", Context.MODE_PRIVATE)

    fun load(): Map<String, String> {
        return preferences.all.mapNotNull { (key, value) ->
            if (!key.startsWith(KeyPrefix) || value !is String) return@mapNotNull null
            key.removePrefix(KeyPrefix) to value
        }.toMap()
    }

    fun save(invitationId: String, maskedEmail: String) {
        if (maskedEmail.isBlank()) return
        preferences.edit().putString("$KeyPrefix$invitationId", maskedEmail).apply()
    }

    fun remove(invitationId: String) {
        preferences.edit().remove("$KeyPrefix$invitationId").apply()
    }

    private companion object {
        const val KeyPrefix = "invitation:"
    }
}

private enum class IconKind {
    Home,
    Capsule,
    Circle,
    History,
    Settings,
    Shield,
    Clock,
    Alert,
    CheckCircle,
    Lock,
    Key,
    Cross,
    User,
    UserCheck,
    MapPin,
    Bowl,
    HouseSearch,
    Bell,
    Edit,
    Trash,
}

private enum class CareCircleAccessTemplate(
    val title: String,
    val description: String,
    val role: String,
    val scopes: List<String>,
    val visibleScopes: String,
    val sensitivity: String,
    val icon: IconKind,
    val sprite: CatSpriteKind,
) {
    CoreCare(
        title = "Core care helper",
        description = "Best for someone who can feed, find and calm the cat without seeing home entry or medical details.",
        role = "EMERGENCY_GUARDIAN",
        scopes = listOf("CARE_CORE"),
        visibleScopes = "food, water, hiding places and never-do notes",
        sensitivity = "Core",
        icon = IconKind.Bowl,
        sprite = CatSpriteKind.Guard,
    ),
    HomeHelper(
        title = "Home access helper",
        description = "Best for a neighbor or keyholder who may need to enter the home and reach the cat.",
        role = "KEYHOLDER",
        scopes = listOf("CARE_CORE", "HOME_ACCESS"),
        visibleScopes = "core care plus home access instructions",
        sensitivity = "Home access",
        icon = IconKind.Key,
        sprite = CatSpriteKind.Key,
    ),
    MedicalHelper(
        title = "Medical helper",
        description = "Best for a vet-aware contact who may need medication or health context during an incident.",
        role = "MEDICAL_CONTACT",
        scopes = listOf("CARE_CORE", "MEDICAL"),
        visibleScopes = "core care plus medical context",
        sensitivity = "Sensitive",
        icon = IconKind.Cross,
        sprite = CatSpriteKind.Medic,
    ),
}

private enum class CapsuleEditorSection {
    CoreCare,
    HomeAccess,
    Medical,
}

private sealed class UiStatus(open val message: String) {
    data class Info(override val message: String) : UiStatus(message)
    data class Success(override val message: String) : UiStatus(message)
    data class Error(override val message: String) : UiStatus(message)
}

private fun CareCoreDraft.completionCount(): Int {
    return listOf(feedingAndWater, hidingPlaces, doNotDo).count { it.isNotBlank() }
}

private fun CareCoreDraft.homeAccessCompletionCount(): Int {
    return listOf(entryInstructions, keyLocation, safeRoom).count { it.isNotBlank() }
}

private fun CareCoreDraft.medicalCompletionCount(): Int {
    return listOf(medications, vetInfo, medicalWarnings).count { it.isNotBlank() }
}

private fun PlanRow.statusLabel(): String {
    return status.lowercase().replaceFirstChar { it.uppercase() }
}

private fun PlanRow.isPastGraceWindow(now: Instant = Instant.now()): Boolean {
    if (!status.equals("armed", ignoreCase = true)) return false
    val deadline = nextCheckInAt?.parseInstantOrNull() ?: return false
    val graceDeadline = deadline.plusSeconds(gracePeriodMinutes.toLong() * 60L)
    return now.isAfter(graceDeadline)
}

private fun PlanRow.checkInSummary(): String {
    return when {
        isPastGraceWindow() -> nextCheckInAt?.let { "Missed check-in ${it.humanCheckInDeadline()}." } ?: "Missed check-in deadline."
        nextCheckInAt != null -> "Next check-in ${nextCheckInAt.humanCheckInDeadline()}."
        else -> "No deadline yet."
    }
}

private fun String.parseInstantOrNull(): Instant? {
    return runCatching { Instant.parse(this) }.getOrNull()
}

private fun String.humanLabel(): String {
    return lowercase()
        .replace(".", " ")
        .replace("_", " ")
        .split(" ")
        .filter { it.isNotBlank() }
        .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
}

private fun String.spriteKind(): CatSpriteKind {
    return when (uppercase()) {
        "HOME_ACCESS" -> CatSpriteKind.Key
        "MEDICAL" -> CatSpriteKind.Medic
        else -> CatSpriteKind.Guard
    }
}

private fun List<String>.responderSpriteFallback(): CatSpriteKind {
    return when {
        any { it.equals("HOME_ACCESS", ignoreCase = true) } -> CatSpriteKind.Key
        any { it.equals("MEDICAL", ignoreCase = true) } -> CatSpriteKind.Medic
        else -> CatSpriteKind.Guard
    }
}

private fun CatSpriteKind.logoLabel(): String {
    return when (this) {
        CatSpriteKind.Alert -> "Alert"
        CatSpriteKind.Cute -> "Cute"
        CatSpriteKind.Dizzy -> "Dizzy"
        CatSpriteKind.Guard -> "Guard"
        CatSpriteKind.Happy -> "Happy"
        CatSpriteKind.Key -> "Key"
        CatSpriteKind.Medic -> "Medic"
        CatSpriteKind.Neutral -> "Neutral"
        CatSpriteKind.Sleepy -> "Sleepy"
    }
}

private fun String.scopeColor(): Color {
    return when (uppercase()) {
        "HOME_ACCESS" -> Color(0xFF8A5A00)
        "MEDICAL" -> Color(0xFFB42318)
        else -> Color(0xFF1F7A5A)
    }
}

private fun String.auditColor(): Color {
    return when (lowercase()) {
        "success" -> Color(0xFF1F7A5A)
        "denied" -> Color(0xFF8A5A00)
        "failed" -> Color(0xFFB42318)
        else -> Color(0xFF60665D)
    }
}

private fun String.humanDateTime(short: Boolean = false): String {
    return runCatching {
        val formatter = if (short) {
            DateTimeFormatter.ofPattern("MMM d, HH:mm")
        } else {
            DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm")
        }
        Instant.parse(this)
            .atZone(ZoneId.systemDefault())
            .format(formatter)
    }.getOrElse { this }
}

private fun String.humanCheckInDeadline(): String {
    return runCatching {
        val zone = ZoneId.systemDefault()
        val moment = Instant.parse(this).atZone(zone)
        val date = moment.toLocalDate()
        val today = LocalDate.now(zone)
        val dateLabel = when (date) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            else -> moment.format(DateTimeFormatter.ofPattern("MMM d"))
        }
        "$dateLabel, ${moment.format(DateTimeFormatter.ofPattern("HH:mm"))}"
    }.getOrElse { humanDateTime(short = true) }
}

private fun String.humanDateGroup(): String {
    return runCatching {
        val zone = ZoneId.systemDefault()
        val moment = Instant.parse(this).atZone(zone)
        val date = moment.toLocalDate()
        val today = LocalDate.now(zone)
        when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> moment.format(DateTimeFormatter.ofPattern("MMM d"))
        }
    }.getOrElse { "Earlier" }
}

private fun String.humanTime(): String {
    return runCatching {
        Instant.parse(this)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm"))
    }.getOrElse { this }
}

private fun Throwable.readableMessage(): String {
    val rawMessage = message.orEmpty()
    return when {
        rawMessage.contains("remove_care_circle_person", ignoreCase = true) &&
            rawMessage.contains("schema cache", ignoreCase = true) ->
            "Care Circle removal is not installed in Supabase yet. Run the remove-care-circle SQL, then retry."
        else -> rawMessage.takeIf { it.isNotBlank() } ?: "The request failed."
    }
}

private fun Throwable.authReadableMessage(): String {
    val rawMessage = message.orEmpty()
    return when {
        rawMessage.contains("Invalid login credentials", ignoreCase = true) ->
            "We could not verify those credentials. Check the email and password, then try again."
        rawMessage.contains("Email not confirmed", ignoreCase = true) ->
            "Verify the email address before signing in."
        else -> readableMessage()
    }
}

private data class PasswordRequirement(
    val label: String,
    val met: Boolean,
)

private data class PasswordSecurityProfile(
    val score: Int,
    val label: String,
    val requirements: List<PasswordRequirement>,
) {
    val isStrong: Boolean
        get() = score == requirements.size
}

private fun passwordSecurityProfile(password: String): PasswordSecurityProfile {
    val requirements = listOf(
        PasswordRequirement("12 or more characters", password.length >= 12),
        PasswordRequirement("Lowercase letter", password.any { it.isLowerCase() }),
        PasswordRequirement("Uppercase letter", password.any { it.isUpperCase() }),
        PasswordRequirement("Number", password.any { it.isDigit() }),
        PasswordRequirement("Symbol", password.any { !it.isLetterOrDigit() }),
    )
    val score = requirements.count { it.met }
    val label = when (score) {
        0, 1 -> "Weak"
        2, 3 -> "Improving"
        4 -> "Almost ready"
        else -> "Strong"
    }
    return PasswordSecurityProfile(score = score, label = label, requirements = requirements)
}

private fun String.isValidEmailInput(): Boolean {
    val trimmed = trim()
    if (trimmed.length !in 3..254) return false
    return Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(trimmed)
}

private fun String.maskedEmailForDisplay(): String {
    val normalized = trim().lowercase()
    val atIndex = normalized.indexOf("@")
    if (atIndex <= 0 || atIndex == normalized.lastIndex) return ""

    val local = normalized.substring(0, atIndex)
    val domain = normalized.substring(atIndex + 1)
    val visibleLocal = when {
        local.length <= 1 -> local
        local.length == 2 -> local.take(1)
        else -> local.take(2)
    }

    return "$visibleLocal***@$domain"
}

private const val AUTH_CALLBACK_URL = "thethirdbowl://auth-callback"

@Preview(showBackground = true)
@Composable
private fun AuthExperiencePreview() {
    TheThirdBowlTheme {
        AuthExperience(
            email = "mara@example.com",
            password = "StrongKitty#2026",
            status = UiStatus.Info("Create an account or sign in to build a continuity plan."),
            isBusy = false,
            onEmailChange = {},
            onPasswordChange = {},
            onSignIn = {},
            onSignUp = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 720)
@Composable
private fun LoadingCatsExperiencePreview() {
    TheThirdBowlTheme {
        LoadingCatsExperience(
            status = UiStatus.Info("Loading cats..."),
        )
    }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun CatSpriteSetPreview() {
    TheThirdBowlTheme {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CatSpriteKind.entries.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    row.forEach { sprite ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            CatSprite(
                                kind = sprite,
                                size = 74.dp,
                                contentDescription = null,
                            )
                            Text(
                                text = sprite.name,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}
