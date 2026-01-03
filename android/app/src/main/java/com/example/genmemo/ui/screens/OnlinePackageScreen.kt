package com.example.genmemo.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.genmemo.data.api.GenMemoApiService
import com.example.genmemo.data.auth.AuthManager
import com.example.genmemo.data.auth.AuthState
import com.example.genmemo.data.model.OnlinePackage
import com.example.genmemo.data.sync.ProgressSyncService
import com.example.genmemo.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlinePackageScreen(
    authManager: AuthManager,
    progressSyncService: ProgressSyncService,
    onNavigateBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onStartQuiz: (OnlinePackage) -> Unit
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var packageCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loadedPackage by remember { mutableStateOf<OnlinePackage?>(null) }

    val authState by authManager.authState.collectAsState()
    val isLoggedIn = authState is AuthState.LoggedIn
    val currentUser = (authState as? AuthState.LoggedIn)?.user

    // Count questions due for review when package is loaded
    val questionsDue = remember(loadedPackage, isLoggedIn) {
        if (isLoggedIn && loadedPackage != null) {
            progressSyncService.countQuestionsDue(
                loadedPackage!!.meta.uuid,
                loadedPackage!!.questions.size
            )
        } else {
            loadedPackage?.questions?.size ?: 0
        }
    }

    fun fetchPackage() {
        if (packageCode.isBlank()) {
            errorMessage = "Inserisci un codice pacchetto"
            return
        }

        focusManager.clearFocus()
        isLoading = true
        errorMessage = null
        loadedPackage = null

        scope.launch {
            val result = GenMemoApiService.getPackageSafe(packageCode.trim())
            isLoading = false

            when (result) {
                is GenMemoApiService.ApiResult.Success -> {
                    loadedPackage = result.data
                    // Download progress from server if logged in
                    if (isLoggedIn) {
                        progressSyncService.downloadPackageProgress(result.data.meta.uuid)
                    }
                }
                is GenMemoApiService.ApiResult.Error -> {
                    errorMessage = result.message
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pacchetti Online") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro")
                    }
                },
                actions = {
                    if (isLoggedIn) {
                        IconButton(onClick = { authManager.logout() }) {
                            Icon(Icons.AutoMirrored.Filled.Logout, "Esci")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Auth status card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isLoggedIn) CorrectGreenLight else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (isLoggedIn) Icons.Default.CheckCircle else Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = if (isLoggedIn) CorrectGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        if (isLoggedIn) {
                            Text(
                                "Connesso come ${currentUser?.username}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "I tuoi progressi verranno salvati",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                "Non sei connesso",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "Accedi per salvare i tuoi progressi",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (!isLoggedIn) {
                        TextButton(onClick = onNavigateToLogin) {
                            Text("Accedi")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Header icon
            Icon(
                Icons.Default.CloudDownload,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = Primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Scarica un Pacchetto",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                "Inserisci il codice del pacchetto per scaricarlo e iniziare il quiz",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
            )

            // Code input field
            OutlinedTextField(
                value = packageCode,
                onValueChange = {
                    packageCode = it
                    errorMessage = null
                    loadedPackage = null
                },
                label = { Text("Codice Pacchetto") },
                placeholder = { Text("es. abc123xyz") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.QrCode, contentDescription = null)
                },
                trailingIcon = {
                    if (packageCode.isNotEmpty()) {
                        IconButton(onClick = { packageCode = "" }) {
                            Icon(Icons.Default.Clear, "Cancella")
                        }
                    }
                },
                isError = errorMessage != null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { fetchPackage() }),
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Fetch button
            Button(
                onClick = { fetchPackage() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = packageCode.isNotBlank() && !isLoading,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cerca Pacchetto", style = MaterialTheme.typography.titleMedium)
                }
            }

            // Error message
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = WrongRedLight)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = WrongRed
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            errorMessage ?: "",
                            color = WrongRed,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Package info card
            AnimatedVisibility(
                visible = loadedPackage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                loadedPackage?.let { pkg ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        ) {
                            // Package name
                            Text(
                                pkg.meta.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Author
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Autore: ${pkg.meta.author}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Question count
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Quiz,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "${pkg.questions.size} domande totali",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Questions due for review (only if logged in)
                            if (isLoggedIn) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Schedule,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = if (questionsDue > 0) ReviewColor else CorrectGreen
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        if (questionsDue > 0) "$questionsDue da ripassare" else "Tutto ripassato!",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (questionsDue > 0) ReviewColor else CorrectGreen,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Download count
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "${pkg.meta.downloadCount} download",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // TTS indicator
                            if (pkg.tts.enabled) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.VolumeUp,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = Primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Audio TTS abilitato",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Primary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // Start quiz button
                            Button(
                                onClick = { onStartQuiz(pkg) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CorrectGreen)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (isLoggedIn && questionsDue > 0) "Ripassa ($questionsDue)" else "Inizia Quiz",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }

                            // Login prompt if not logged in
                            if (!isLoggedIn) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "Accedi per salvare i tuoi progressi e sfruttare la ripetizione spaziata",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
