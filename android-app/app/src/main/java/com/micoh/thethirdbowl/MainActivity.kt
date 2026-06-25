package com.micoh.thethirdbowl

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.micoh.thethirdbowl.data.CapsuleRepository
import com.micoh.thethirdbowl.data.CatRepository
import com.micoh.thethirdbowl.data.CatRow
import com.micoh.thethirdbowl.data.CareCoreDraft
import com.micoh.thethirdbowl.data.SupabaseProvider
import com.micoh.thethirdbowl.ui.theme.TheThirdBowlTheme
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TheThirdBowlTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ThirdBowlApp()
                }
            }
        }
    }
}

@Composable
private fun ThirdBowlApp() {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        AuthScreen(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        )
    }
}

@Composable
private fun AuthScreen(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var signedInEmail by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Checking session...") }
    var isBusy by remember { mutableStateOf(false) }
    var catName by remember { mutableStateOf("") }
    var cats by remember { mutableStateOf(emptyList<CatRow>()) }
    var selectedCatId by remember { mutableStateOf<String?>(null) }
    var careCore by remember { mutableStateOf(CareCoreDraft()) }
    val catRepository = remember { CatRepository() }
    val capsuleRepository = remember { CapsuleRepository() }

    LaunchedEffect(Unit) {
        runCatching {
            SupabaseProvider.client.auth.currentSessionOrNull()
        }.onSuccess { session ->
            signedInEmail = session?.user?.email
            status = if (session == null) {
                "Sign in or create an account to start a real care plan."
            } else {
                "Signed in with Supabase."
            }
            if (session != null) {
                cats = catRepository.listMyCats()
                selectedCatId = cats.firstOrNull()?.id
                selectedCatId?.let { catId ->
                    careCore = capsuleRepository.loadCareCore(catId)
                }
            }
        }.onFailure { error ->
            status = error.readableMessage()
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "The Third Bowl",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "Emergency continuity for cats whose care depends on one person.",
            style = MaterialTheme.typography.bodyLarge
        )

        if (signedInEmail == null) {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it.trim() },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    enabled = !isBusy && email.isNotBlank() && password.length >= 6,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        scope.launch {
                            isBusy = true
                            status = "Signing in..."
                            runCatching {
                                SupabaseProvider.client.auth.signInWith(Email) {
                                    this.email = email
                                    this.password = password
                                }
                                SupabaseProvider.client.auth.currentSessionOrNull()
                            }.onSuccess { session ->
                                signedInEmail = session?.user?.email
                                cats = catRepository.listMyCats()
                                selectedCatId = cats.firstOrNull()?.id
                                selectedCatId?.let { catId ->
                                    careCore = capsuleRepository.loadCareCore(catId)
                                }
                                status = "Signed in with Supabase."
                            }.onFailure { error ->
                                status = error.readableMessage()
                            }
                            isBusy = false
                        }
                    }
                ) {
                    Text("Sign in")
                }
                OutlinedButton(
                    enabled = !isBusy && email.isNotBlank() && password.length >= 6,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        scope.launch {
                            isBusy = true
                            status = "Creating account..."
                            runCatching {
                                SupabaseProvider.client.auth.signUpWith(Email) {
                                    this.email = email
                                    this.password = password
                                }
                            }.onSuccess {
                                status = "Account created. Check the verification email before signing in."
                            }.onFailure { error ->
                                status = error.readableMessage()
                            }
                            isBusy = false
                        }
                    }
                ) {
                    Text("Sign up")
                }
            }
        } else {
            Text(
                text = signedInEmail.orEmpty(),
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedButton(
                enabled = !isBusy,
                onClick = {
                    scope.launch {
                        isBusy = true
                        status = "Signing out..."
                        runCatching {
                            SupabaseProvider.client.auth.signOut()
                        }.onSuccess {
                            signedInEmail = null
                            cats = emptyList()
                            selectedCatId = null
                            careCore = CareCoreDraft()
                            status = "Signed out."
                        }.onFailure { error ->
                            status = error.readableMessage()
                        }
                        isBusy = false
                    }
                }
            ) {
                Text("Sign out")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Cats",
                style = MaterialTheme.typography.titleLarge
            )
            if (cats.isEmpty()) {
                Text(
                    text = "No cats yet.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    cats.forEach { cat ->
                        OutlinedButton(
                            enabled = !isBusy,
                            onClick = {
                                scope.launch {
                                    isBusy = true
                                    status = "Loading ${cat.name}..."
                                    runCatching {
                                        capsuleRepository.loadCareCore(cat.id)
                                    }.onSuccess { draft ->
                                        selectedCatId = cat.id
                                        careCore = draft
                                        status = "Loaded ${cat.name}."
                                    }.onFailure { error ->
                                        status = error.readableMessage()
                                    }
                                    isBusy = false
                                }
                            }
                        ) {
                            Text(
                                text = if (selectedCatId == cat.id) {
                                    "${cat.name} selected"
                                } else {
                                    "${cat.name} (${cat.status})"
                                }
                            )
                        }
                    }
                }
            }
            OutlinedTextField(
                value = catName,
                onValueChange = { catName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Cat name") },
                singleLine = true
            )
            Button(
                enabled = !isBusy && catName.isNotBlank(),
                onClick = {
                    scope.launch {
                        isBusy = true
                        status = "Creating cat..."
                        runCatching {
                            catRepository.createCat(catName)
                        }.onSuccess { cat ->
                            catName = ""
                            cats = cats + cat
                            selectedCatId = cat.id
                            careCore = CareCoreDraft()
                            status = "Created ${cat.name} in Supabase."
                        }.onFailure { error ->
                            status = error.readableMessage()
                        }
                        isBusy = false
                    }
                }
            ) {
                Text("Add cat")
            }
            selectedCatId?.let { catId ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Care core",
                    style = MaterialTheme.typography.titleLarge
                )
                OutlinedTextField(
                    value = careCore.feedingAndWater,
                    onValueChange = { careCore = careCore.copy(feedingAndWater = it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Feeding and water") },
                    minLines = 3
                )
                OutlinedTextField(
                    value = careCore.hidingPlaces,
                    onValueChange = { careCore = careCore.copy(hidingPlaces = it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Hiding places") },
                    minLines = 2
                )
                OutlinedTextField(
                    value = careCore.doNotDo,
                    onValueChange = { careCore = careCore.copy(doNotDo = it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Do not do") },
                    minLines = 2
                )
                Button(
                    enabled = !isBusy,
                    onClick = {
                        scope.launch {
                            isBusy = true
                            status = "Saving care core..."
                            runCatching {
                                capsuleRepository.saveCareCore(catId, careCore)
                            }.onSuccess { savedDraft ->
                                careCore = savedDraft
                                status = "Care core saved in Supabase."
                            }.onFailure { error ->
                                status = error.readableMessage()
                            }
                            isBusy = false
                        }
                    }
                ) {
                    Text("Save care core")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isBusy) {
                CircularProgressIndicator()
            }
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun Throwable.readableMessage(): String {
    return message?.takeIf { it.isNotBlank() } ?: "The request failed."
}

@Preview(showBackground = true)
@Composable
private fun AuthScreenPreview() {
    TheThirdBowlTheme {
        AuthScreen(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        )
    }
}
