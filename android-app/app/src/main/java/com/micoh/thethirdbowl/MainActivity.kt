package com.micoh.thethirdbowl

import android.content.Intent
import android.os.Bundle
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
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
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(AppTab.Home) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var signedInEmail by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<UiStatus>(UiStatus.Info("Checking your session...")) }
    var isBusy by remember { mutableStateOf(false) }
    var isLoadingCats by remember { mutableStateOf(false) }
    var catName by remember { mutableStateOf("") }
    var cats by remember { mutableStateOf(emptyList<CatRow>()) }
    var selectedCatId by remember { mutableStateOf<String?>(null) }
    var careCore by remember { mutableStateOf(CareCoreDraft()) }
    var invitationEmail by remember { mutableStateOf("") }
    var relationshipLabel by remember { mutableStateOf("") }
    var selectedAccessTemplate by remember { mutableStateOf(CareCircleAccessTemplate.CoreCare) }
    var invitations by remember { mutableStateOf(emptyList<InvitationRow>()) }
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
        invitations = invitationRepository.listInvitations(catId)
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
                runCatching {
                    loadAccountState()
                }.onSuccess {
                    status = UiStatus.Success("Your continuity workspace is ready.")
                }.onFailure { error ->
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
            runCatching {
                loadAccountState()
            }.onSuccess {
                selectedTab = AppTab.Home
            }.onFailure { error ->
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
                        loadAccountState()
                        isLoadingCats = false
                        selectedTab = AppTab.Home
                        status = UiStatus.Success("Welcome back. Your plans are synced.")
                    }.onFailure { error ->
                        status = UiStatus.Error(error.readableMessage())
                        isLoadingCats = false
                    }
                    isBusy = false
                }
            },
            onSignUp = {
                scope.launch {
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
            NavigationBar {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = {
                            Text(
                                text = tab.marker,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        },
                        label = { Text(tab.label) },
                    )
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
                signedInEmail = signedInEmail.orEmpty(),
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
                    onSelectCat = { cat ->
                        scope.launch {
                            isBusy = true
                            status = UiStatus.Info("Loading ${cat.name}...")
                            runCatching {
                                loadSelectedCatState(cat.id)
                            }.onSuccess {
                                selectedCatId = cat.id
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
                    isBusy = isBusy,
                    onInvitationEmailChange = { invitationEmail = it.trim() },
                    onRelationshipLabelChange = { relationshipLabel = it },
                    onAccessTemplateChange = { selectedAccessTemplate = it },
                    onCreateInvitation = {
                        val catId = selectedCatId ?: return@CircleScreen
                        scope.launch {
                            isBusy = true
                            status = UiStatus.Info("Preparing the invitation...")
                            runCatching {
                                invitationRepository.createInvitation(
                                    catId = catId,
                                    email = invitationEmail,
                                    relationshipLabel = relationshipLabel,
                                    proposedRole = selectedAccessTemplate.role,
                                    proposedScopes = selectedAccessTemplate.scopes,
                                )
                            }.onSuccess { invitation ->
                                invitationEmail = ""
                                relationshipLabel = ""
                                invitations = listOf(invitation) + invitations
                                auditEvents = auditRepository.listCatEvents(catId)
                                status = UiStatus.Success("Contact added to the Care Circle.")
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
                    plan = plan,
                    incident = incident,
                    isBusy = isBusy,
                    onTriggerMissedCheckIn = {
                        val catId = selectedCatId ?: return@SettingsScreen
                        scope.launch {
                            isBusy = true
                            status = UiStatus.Info("Triggering developer inactivity...")
                            runCatching {
                                planRepository.triggerDeveloperMissedCheckIn(catId)
                            }.onSuccess { result ->
                                plan = planRepository.getOrCreatePlan(catId)
                                incident = incidentRepository.getActiveIncident(catId)
                                auditEvents = auditRepository.listCatEvents(catId)
                                status = UiStatus.Success(
                                    "Developer trigger processed ${result.processedPlans} plan(s), created ${result.incidentsCreated} incident(s).",
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
        CatLoadingIndicator()
        StatusBanner(status = status, isBusy = false)
    }
}

@Composable
private fun CatLoadingIndicator() {
    val transition = rememberInfiniteTransition(label = "cat-loading")
    val bowlScale by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bowl-scale",
    )
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400),
            repeatMode = RepeatMode.Restart,
        ),
        label = "loading-sweep",
    )
    val dots = listOf("Capsule", "Circle", "Plan")

    SectionCard(title = "Syncing workspace") {
        Box(
            modifier = Modifier
                .size(72.dp)
                .align(Alignment.CenterHorizontally)
                .scale(bowlScale)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "B",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            dots.forEachIndexed { index, label ->
                val active = ((sweep * dots.size).toInt() % dots.size) == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (active) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        ),
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            dots.forEachIndexed { index, label ->
                val active = ((sweep * dots.size).toInt() % dots.size) == index
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        BrandMark()
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "The Third Bowl",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "A quiet continuity plan for the cat whose routine lives in your head.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                Text(
                    text = "Start with verified email",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Trusted contacts and private care instructions are tied to real accounts, not anonymous links.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = onEmailChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        enabled = !isBusy && email.isNotBlank() && password.length >= 6,
                        modifier = Modifier.weight(1f),
                        onClick = onSignIn,
                    ) {
                        Text("Sign in")
                    }
                    OutlinedButton(
                        enabled = !isBusy && email.isNotBlank() && password.length >= 6,
                        modifier = Modifier.weight(1f),
                        onClick = onSignUp,
                    ) {
                        Text("Create")
                    }
                }
            }
        }

        TrustChecklist()
        StatusBanner(status = status, isBusy = isBusy)
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
    onCatNameChange: (String) -> Unit,
    onCreateCat: () -> Unit,
    onSelectCat: (CatRow) -> Unit,
    onArmPlan: () -> Unit,
    onCheckIn: () -> Unit,
    onGoToCapsule: () -> Unit,
    onGoToCircle: () -> Unit,
) {
    val readiness = coverageReadiness(careCore, invitations, plan, incident)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (selectedCat == null) {
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
            Button(
                enabled = !isBusy && catName.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                onClick = onCreateCat,
            ) {
                Text("Create care profile")
            }
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

        SectionCard(title = "Continuity timeline") {
            TimelineRow(
                label = "Routine captured",
                value = if (careCore.completionCount() > 0) "${careCore.completionCount()}/3 essentials" else "Not started",
                active = careCore.completionCount() > 0,
            )
            TimelineRow(
                label = "Care circle",
                value = if (readiness.hasAcceptedCareResponder) {
                    "${readiness.acceptedResponderCount} accepted responder(s)"
                } else if (invitations.isEmpty()) {
                    "No contacts invited"
                } else {
                    "Waiting for acceptance"
                },
                active = readiness.hasAcceptedCareResponder,
            )
            TimelineRow(
                label = "Ritual",
                value = plan?.let { "Status: ${it.statusLabel()}" } ?: "Not configured",
                active = plan?.status == "armed",
            )
            TimelineRow(
                label = "Response",
                value = incident?.let { "${it.assignedRelationshipLabel} - ${it.assignmentState.humanLabel()}" } ?: "No active incident",
                active = incident != null,
            )
        }
    }
}

@Composable
private fun ActiveIncidentCard(incident: IncidentRow) {
    val responderAccepted = incident.assignmentState == "accepted"
    val catReached = incident.catReachedAt != null
    SectionCard(title = "Active handoff") {
        Text(
            text = if (catReached) {
                "${incident.assignedRelationshipLabel} reported reaching ${incident.catName}. Keep the resolution explicit."
            } else {
                "${incident.assignedRelationshipLabel} has been asked to reach ${incident.catName}. Watch the handoff, not just the alert."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
            expanded = openSection == CapsuleEditorSection.CoreCare,
            onToggle = { openSection = CapsuleEditorSection.CoreCare },
        ) {
            Text(
                text = "This is the minimum scope required before the ritual can be trusted. It can be released only to an accepted responder during an active incident.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = careCore.feedingAndWater,
                onValueChange = { onCareCoreChange(careCore.copy(feedingAndWater = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Food, water and daily rhythm") },
                supportingText = { Text("What should someone do in the first few hours?") },
                minLines = 3,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = careCore.hidingPlaces,
                onValueChange = { onCareCoreChange(careCore.copy(hidingPlaces = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Hiding places and approach") },
                supportingText = { Text("Where will ${selectedCat.name} hide, and how should a trusted person approach?") },
                minLines = 3,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = careCore.doNotDo,
                onValueChange = { onCareCoreChange(careCore.copy(doNotDo = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Never do this") },
                supportingText = { Text("Hard rules that prevent stress, escape or mistakes.") },
                minLines = 3,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth(),
                onClick = onSaveCareCore,
            ) {
                Text("Save Capsule")
            }
        }

        ExpandableSectionCard(
            title = "Home access",
            summary = if (careCore.homeAccessCompletionCount() > 0) {
                "${careCore.homeAccessCompletionCount()}/3 details written"
            } else {
                "Collapsed - only for Home access helpers"
            },
            expanded = openSection == CapsuleEditorSection.HomeAccess,
            onToggle = { openSection = CapsuleEditorSection.HomeAccess },
        ) {
            Text(
                text = "Only contacts invited as Home access helpers can see this during an active incident.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = careCore.entryInstructions,
                onValueChange = { onCareCoreChange(careCore.copy(entryInstructions = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Entry instructions") },
                supportingText = { Text("Building, door, alarm or timing details.") },
                minLines = 3,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = careCore.keyLocation,
                onValueChange = { onCareCoreChange(careCore.copy(keyLocation = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Key or access location") },
                supportingText = { Text("Keep this practical and only for people you trust with home access.") },
                minLines = 2,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = careCore.safeRoom,
                onValueChange = { onCareCoreChange(careCore.copy(safeRoom = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Safe room or home hazards") },
                supportingText = { Text("Where to contain the cat, and what to avoid in the home.") },
                minLines = 2,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth(),
                onClick = onSaveCareCore,
            ) {
                Text("Save home access")
            }
        }

        ExpandableSectionCard(
            title = "Medical",
            summary = if (careCore.medicalCompletionCount() > 0) {
                "${careCore.medicalCompletionCount()}/3 details written"
            } else {
                "Collapsed - only for Medical helpers"
            },
            expanded = openSection == CapsuleEditorSection.Medical,
            onToggle = { openSection = CapsuleEditorSection.Medical },
        ) {
            Text(
                text = "Only contacts invited as Medical helpers can see this during an active incident.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = careCore.medications,
                onValueChange = { onCareCoreChange(careCore.copy(medications = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Medication and dosing") },
                supportingText = { Text("Medicine names, dose windows and what to do if a dose was missed.") },
                minLines = 3,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = careCore.vetInfo,
                onValueChange = { onCareCoreChange(careCore.copy(vetInfo = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Vet and insurance context") },
                supportingText = { Text("Clinic, phone, policy or emergency contact details.") },
                minLines = 2,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = careCore.medicalWarnings,
                onValueChange = { onCareCoreChange(careCore.copy(medicalWarnings = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Medical warnings") },
                supportingText = { Text("Allergies, stress signs, forbidden foods or handling limits.") },
                minLines = 2,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth(),
                onClick = onSaveCareCore,
            ) {
                Text("Save medical")
            }
        }
    }
}

@Composable
private fun CapsuleDisclosureCard(catName: String) {
    SectionCard(title = "How release works") {
        Text(
            text = "The Capsule is private by default. During an incident, a responder sees only the scopes you granted before the ritual was armed.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        TimelineRow(
            label = "Core care",
            value = "Food, water, hiding places and hard rules for ${catName}. Required for coverage.",
            active = true,
        )
        TimelineRow(
            label = "Home access",
            value = "Entry details stay separated from routine care.",
            active = false,
        )
        TimelineRow(
            label = "Medical",
            value = "Vet and medication context stay separated from general responders.",
            active = false,
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
    isBusy: Boolean,
    onInvitationEmailChange: (String) -> Unit,
    onRelationshipLabelChange: (String) -> Unit,
    onAccessTemplateChange: (CareCircleAccessTemplate) -> Unit,
    onCreateInvitation: () -> Unit,
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

        SectionCard(title = "Invite a trusted person") {
            Text(
                text = "Start with someone who can truly reach the cat, understand the home, or coordinate care if you cannot.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = invitationEmail,
                onValueChange = onInvitationEmailChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Trusted person's email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = relationshipLabel,
                onValueChange = onRelationshipLabelChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Relationship to the cat") },
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "What should they be able to help with?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            AccessTemplateSelector(
                selected = selectedAccessTemplate,
                onSelected = onAccessTemplateChange,
            )
            Spacer(modifier = Modifier.height(12.dp))
            AccessTemplateSummary(template = selectedAccessTemplate)
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                enabled = !isBusy && invitationEmail.isNotBlank() && relationshipLabel.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                onClick = onCreateInvitation,
            ) {
                Text("Add to Care Circle")
            }
        }

        SectionCard(title = "People and access") {
            if (invitations.isEmpty()) {
                EmptyInline(
                    title = "No contacts yet",
                    body = "A plan should not rely on a pending or missing person. Add someone who can genuinely respond.",
                )
            } else {
                invitations.forEachIndexed { index, invitation ->
                    if (index > 0) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    }
                    InvitationCard(invitation)
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
        SectionCard(title = "Current state") {
            TimelineRow(
                label = "Profile",
                value = selectedCat?.let { "${it.name} is ${it.status}" } ?: "No profile",
                active = selectedCat != null,
            )
            TimelineRow(
                label = "Capsule",
                value = "${careCore.completionCount()}/3 core sections complete",
                active = careCore.completionCount() == 3,
            )
            TimelineRow(
                label = "Care Circle",
                value = if (readiness.hasAcceptedCareResponder) {
                    "${readiness.acceptedResponderCount} accepted responder(s)"
                } else if (invitations.isEmpty()) {
                    "No contacts invited"
                } else {
                    "Waiting for acceptance"
                },
                active = readiness.hasAcceptedCareResponder,
            )
            TimelineRow(
                label = "Plan",
                value = plan?.let { "${it.statusLabel()} - ${it.nextCheckInAt?.humanDateTime() ?: "no deadline"}" } ?: "No plan",
                active = plan?.status == "armed",
            )
            TimelineRow(
                label = "Incident",
                value = incident?.let { "${it.incidentState.humanLabel()} assigned to ${it.assignedRelationshipLabel}" } ?: "No active incident",
                active = incident != null,
            )
        }
        SectionCard(title = "Audit trail") {
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
                auditEvents.forEachIndexed { index, event ->
                    if (index > 0) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                    }
                    AuditEventCard(event)
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    selectedCat: CatRow?,
    plan: PlanRow?,
    incident: IncidentRow?,
    isBusy: Boolean,
    onTriggerMissedCheckIn: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle(
            eyebrow = selectedCat?.name ?: "No cat selected",
            title = "Settings",
            body = "Operational controls for local testing and product setup.",
        )

        SectionCard(title = "Developer settings") {
            Text(
                text = "Use this only while testing. It moves the selected cat's armed plan past its grace window and asks the backend processor to create the missed check-in incident.",
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
                Text("Trigger inactivity now")
            }
        }
    }
}

@Composable
private fun AppHeader(
    signedInEmail: String,
    isBusy: Boolean,
    onSignOut: () -> Unit,
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
                    text = signedInEmail,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        TextButton(enabled = !isBusy, onClick = onSignOut) {
            Text("Sign out")
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
    onGoToCapsule: () -> Unit,
    onGoToCircle: () -> Unit,
) {
    val readiness = coverageReadiness(careCore, invitations, plan, incident)
    val state = continuityState(plan, incident, readiness)
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = state.container),
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CatAvatar(name = cat.name)
                    Spacer(modifier = Modifier.width(12.dp))
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
                StatusPill(text = state.badge, color = state.accent)
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

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricChip(label = "Capsule", value = "${careCore.completionCount()}/3")
                MetricChip(label = "Circle", value = readiness.acceptedResponderCount.toString())
                MetricChip(label = "Next", value = plan?.nextCheckInAt?.humanDateTime(short = true) ?: "Unset")
            }

            CoverageRequirementList(readiness)

            if (plan?.status == "armed") {
                Button(
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = state.accent),
                    onClick = onCheckIn,
                ) {
                    Text("Confirm availability")
                }
            } else if (!readiness.coreCareComplete) {
                Button(
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = state.accent),
                    onClick = onGoToCapsule,
                ) {
                    Text("Finish Capsule")
                }
            } else if (!readiness.hasAcceptedCareResponder) {
                Button(
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = state.accent),
                    onClick = onGoToCircle,
                ) {
                    Text("Add a responder")
                }
            } else {
                Button(
                    enabled = !isBusy && readiness.canActivateRitual,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = state.accent),
                    onClick = onArmPlan,
                ) {
                    Text("Activate ritual")
                }
            }
        }
    }
}

@Composable
private fun CoverageRequirementList(readiness: CoverageReadiness) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .padding(14.dp),
    ) {
        Text(
            text = readiness.qualityLabel,
            style = MaterialTheme.typography.labelLarge,
            color = readiness.accent,
            fontWeight = FontWeight.SemiBold,
        )
        readiness.requirements.forEach { requirement ->
            TimelineRow(
                label = requirement.label,
                value = requirement.detail,
                active = requirement.complete,
            )
        }
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
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
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
    SectionCard(title = "Readiness") {
        Text(
            text = "$completed of 3 release-ready details complete",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
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
private fun InvitationCard(invitation: InvitationRow) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = invitation.relationshipLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = invitation.proposedRole.humanLabel().ifBlank { "Trusted contact" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Expires ${invitation.expiresAt.humanDateTime()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (invitation.proposedScopes.isNotEmpty()) {
                Text(
                    text = "Incident access: ${invitation.proposedScopes.joinToString { it.humanLabel() }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        StatusPill(
            text = invitation.status.lowercase().replaceFirstChar { it.uppercase() },
            color = if (invitation.status.equals("accepted", ignoreCase = true)) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.tertiary
            },
        )
    }
}

@Composable
private fun AccessTemplateSelector(
    selected: CareCircleAccessTemplate,
    onSelected: (CareCircleAccessTemplate) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CareCircleAccessTemplate.entries.forEach { template ->
            AssistChip(
                onClick = { onSelected(template) },
                label = {
                    Text(
                        text = if (template == selected) {
                            "${template.title} selected"
                        } else {
                            template.title
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun AccessTemplateSummary(template: CareCircleAccessTemplate) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = template.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = template.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = "During an incident they can see: ${template.visibleScopes}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
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
                .padding(top = 5.dp)
                .size(12.dp)
                .clip(CircleShape)
                .background(event.outcome.auditColor()),
        )
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
                    text = event.eventType.humanLabel(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(10.dp))
                StatusPill(
                    text = event.outcome.humanLabel(),
                    color = event.outcome.auditColor(),
                )
            }
            Text(
                text = "${event.actorType.humanLabel()} changed ${event.targetType.humanLabel()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = event.occurredAt.humanDateTime(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SensitiveScopeCard(
    title: String,
    status: String,
    body: String,
    visibleWhen: String,
    enabled: Boolean,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(
                    text = status,
                    color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = visibleWhen,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
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
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun ExpandableSectionCard(
    title: String,
    summary: String,
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
                Column(modifier = Modifier.weight(1f)) {
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
            BrandMark(size = 48)
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
private fun StatusPill(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun CatAvatar(name: String) {
    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.firstOrNull()?.uppercaseChar()?.toString() ?: "C",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun BrandMark(size: Int = 64) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 3).dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Box(
                modifier = Modifier
                    .size((size * 0.42f).dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size((size * 0.12f).dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary),
                    )
                }
            }
        }
    }
}

private fun continuityState(
    plan: PlanRow?,
    incident: IncidentRow?,
    readiness: CoverageReadiness,
): ContinuityVisualState {
    if (incident != null) {
        return ContinuityVisualState(
            title = "Action is needed",
            body = "An incident is active and assigned to ${incident.assignedRelationshipLabel}. Keep the handoff explicit.",
            badge = "Incident",
            accent = Color(0xFFB42318),
            container = Color(0xFFFFF0ED),
        )
    }

    return if (plan?.status == "armed" && readiness.isReliable) {
        ContinuityVisualState(
            title = "Covered until the next check-in",
            body = plan.nextCheckInAt?.let { "Next confirmation: ${it.humanDateTime()}." }
                ?: "The ritual is armed, but no next deadline was returned.",
            badge = "Covered",
            accent = Color(0xFF1F7A5A),
            container = Color(0xFFEAF7EF),
        )
    } else if (plan?.status == "armed") {
        ContinuityVisualState(
            title = "Coverage needs attention",
            body = readiness.body,
            badge = "Review",
            accent = Color(0xFF8A5A00),
            container = Color(0xFFFFF6DB),
        )
    } else if (!readiness.canActivateRitual) {
        ContinuityVisualState(
            title = readiness.title,
            body = readiness.body,
            badge = "Setup",
            accent = readiness.accent,
            container = Color(0xFFFFF6DB),
        )
    } else {
        ContinuityVisualState(
            title = "Ready to activate",
            body = "The core routine and a verified responder are in place. Activate the ritual when you are ready.",
            badge = "Ready",
            accent = Color(0xFF1F7A5A),
            container = Color(0xFFEAF7EF),
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
                "No accepted CARE_CORE responder yet"
            },
            complete = hasAcceptedCareResponder,
        ),
        CoverageRequirement(
            label = "Ritual",
            detail = if (plan?.status == "armed") {
                plan.nextCheckInAt?.let { "Next check-in ${it.humanDateTime(short = true)}" } ?: "Armed without a deadline"
            } else {
                "Not active yet"
            },
            complete = plan?.status == "armed",
        ),
    )
    val canActivate = coreCareComplete && hasAcceptedCareResponder && incident == null

    return CoverageReadiness(
        title = when {
            incident != null -> "Incident in progress"
            !coreCareComplete -> "Finish the care essentials"
            !hasAcceptedCareResponder -> "Add a verified responder"
            else -> "Ready to activate"
        },
        body = when {
            incident != null -> "An incident is already active. Keep the handoff explicit before changing the ritual."
            !coreCareComplete -> "Complete food, hiding places and never-do notes before relying on escalation."
            !hasAcceptedCareResponder -> "Invite a real person and wait for acceptance before this plan can protect the cat."
            else -> "The minimum pieces are ready: core care and an accepted responder."
        },
        qualityLabel = when {
            incident != null -> "Incident active"
            plan?.status == "armed" && coreCareComplete && hasAcceptedCareResponder -> "Reliable coverage"
            plan?.status == "armed" -> "Coverage has gaps"
            canActivate -> "Ready for coverage"
            else -> "Not reliable yet"
        },
        coreCareComplete = coreCareComplete,
        hasAcceptedCareResponder = hasAcceptedCareResponder,
        acceptedResponderCount = acceptedResponders.size,
        canActivateRitual = canActivate,
        isReliable = plan?.status == "armed" && coreCareComplete && hasAcceptedCareResponder,
        requirements = requirements,
        accent = if (canActivate || (plan?.status == "armed" && coreCareComplete && hasAcceptedCareResponder)) {
            Color(0xFF1F7A5A)
        } else {
            Color(0xFF8A5A00)
        },
    )
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
)

private enum class AppTab(
    val label: String,
    val marker: String,
) {
    Home("Home", "H"),
    Capsule("Capsule", "C"),
    Circle("Circle", "P"),
    History("History", "A"),
    Settings("Settings", "S"),
}

private enum class CareCircleAccessTemplate(
    val title: String,
    val description: String,
    val role: String,
    val scopes: List<String>,
    val visibleScopes: String,
) {
    CoreCare(
        title = "Core care helper",
        description = "Best for someone who can feed, find and calm the cat without seeing home entry or medical details.",
        role = "EMERGENCY_GUARDIAN",
        scopes = listOf("CARE_CORE"),
        visibleScopes = "food, water, hiding places and never-do notes",
    ),
    HomeHelper(
        title = "Home access helper",
        description = "Best for a neighbor or keyholder who may need to enter the home and reach the cat.",
        role = "KEYHOLDER",
        scopes = listOf("CARE_CORE", "HOME_ACCESS"),
        visibleScopes = "core care plus home access instructions",
    ),
    MedicalHelper(
        title = "Medical helper",
        description = "Best for a vet-aware contact who may need medication or health context during an incident.",
        role = "MEDICAL_CONTACT",
        scopes = listOf("CARE_CORE", "MEDICAL"),
        visibleScopes = "core care plus medical context",
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

private fun String.humanLabel(): String {
    return lowercase()
        .replace(".", " ")
        .replace("_", " ")
        .split(" ")
        .filter { it.isNotBlank() }
        .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
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

private fun Throwable.readableMessage(): String {
    return message?.takeIf { it.isNotBlank() } ?: "The request failed."
}

private const val AUTH_CALLBACK_URL = "thethirdbowl://auth-callback"

@Preview(showBackground = true)
@Composable
private fun AuthExperiencePreview() {
    TheThirdBowlTheme {
        AuthExperience(
            email = "mara@example.com",
            password = "password",
            status = UiStatus.Info("Create an account or sign in to build a continuity plan."),
            isBusy = false,
            onEmailChange = {},
            onPasswordChange = {},
            onSignIn = {},
            onSignUp = {},
        )
    }
}
