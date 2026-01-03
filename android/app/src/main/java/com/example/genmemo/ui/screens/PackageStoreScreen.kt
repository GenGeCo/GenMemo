package com.example.genmemo.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.genmemo.data.api.GenMemoApiService
import com.example.genmemo.data.api.PackageListItem
import com.example.genmemo.data.auth.AuthManager
import com.example.genmemo.data.auth.AuthState
import com.example.genmemo.data.repository.InstalledPackageRepository
import com.example.genmemo.data.sync.ProgressSyncService
import com.example.genmemo.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackageStoreScreen(
    authManager: AuthManager,
    installedPackageRepository: InstalledPackageRepository,
    progressSyncService: ProgressSyncService,
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var selectedTab by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var publicPackages by remember { mutableStateOf<List<PackageListItem>>(emptyList()) }
    var myPackages by remember { mutableStateOf<List<PackageListItem>>(emptyList()) }

    var processingPackageId by remember { mutableStateOf<String?>(null) }

    // Collect installed package IDs
    val installedIds by installedPackageRepository.installedPackageIds.collectAsState(initial = emptyList())

    val authState by authManager.authState.collectAsState()
    val currentUser = (authState as? AuthState.LoggedIn)?.user

    // Load packages on first load and tab change
    LaunchedEffect(selectedTab, searchQuery) {
        isLoading = true
        errorMessage = null

        when (selectedTab) {
            0 -> {
                // Public store
                val result = GenMemoApiService.getPublicPackages(
                    search = searchQuery.takeIf { it.isNotBlank() }
                )
                when (result) {
                    is GenMemoApiService.ApiResult.Success -> {
                        publicPackages = result.data.data?.packages ?: emptyList()
                    }
                    is GenMemoApiService.ApiResult.Error -> {
                        errorMessage = result.message
                    }
                }
            }
            1 -> {
                // My packages (created by user on web)
                authManager.token?.let { token ->
                    val result = GenMemoApiService.getMyPackages(token)
                    when (result) {
                        is GenMemoApiService.ApiResult.Success -> {
                            myPackages = result.data.data?.packages ?: emptyList()
                        }
                        is GenMemoApiService.ApiResult.Error -> {
                            errorMessage = result.message
                        }
                    }
                }
            }
        }
        isLoading = false
    }

    fun installPackage(packageItem: PackageListItem) {
        processingPackageId = packageItem.code
        scope.launch {
            val result = GenMemoApiService.downloadPackage(packageItem.code)

            when (result) {
                is GenMemoApiService.ApiResult.Success -> {
                    // Save to local database using the code from list (ensures matching)
                    installedPackageRepository.installPackage(result.data, packageItem.code)
                    // Download progress from server if logged in
                    authManager.token?.let {
                        progressSyncService.downloadPackageProgress(packageItem.code)
                    }
                }
                is GenMemoApiService.ApiResult.Error -> {
                    errorMessage = result.message
                }
            }
            processingPackageId = null
        }
    }

    fun uninstallPackage(packageId: String) {
        processingPackageId = packageId
        scope.launch {
            installedPackageRepository.uninstallPackage(packageId)
            processingPackageId = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Store Pacchetti") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro")
                    }
                },
                actions = {
                    currentUser?.let { user ->
                        Text(
                            user.username,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    IconButton(onClick = { authManager.logout() }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, "Esci")
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
        ) {
            // Tab Row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Store") },
                    icon = { Icon(Icons.Default.Store, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("I Miei") },
                    icon = { Icon(Icons.Default.Folder, contentDescription = null) }
                )
            }

            // Search bar (only for Store tab)
            AnimatedVisibility(visible = selectedTab == 0) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Cerca pacchetti...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, "Cancella")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { focusManager.clearFocus() }
                    )
                )
            }

            // Content
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoading -> {
                        CircularProgressIndicator(color = Primary)
                    }
                    errorMessage != null -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = WrongRed
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                errorMessage ?: "Errore",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = {
                                scope.launch {
                                    isLoading = true
                                    errorMessage = null
                                    when (selectedTab) {
                                        0 -> {
                                            val result = GenMemoApiService.getPublicPackages()
                                            when (result) {
                                                is GenMemoApiService.ApiResult.Success -> publicPackages = result.data.data?.packages ?: emptyList()
                                                is GenMemoApiService.ApiResult.Error -> errorMessage = result.message
                                            }
                                        }
                                        1 -> {
                                            authManager.token?.let { token ->
                                                val result = GenMemoApiService.getMyPackages(token)
                                                when (result) {
                                                    is GenMemoApiService.ApiResult.Success -> myPackages = result.data.data?.packages ?: emptyList()
                                                    is GenMemoApiService.ApiResult.Error -> errorMessage = result.message
                                                }
                                            }
                                        }
                                    }
                                    isLoading = false
                                }
                            }) {
                                Text("Riprova")
                            }
                        }
                    }
                    else -> {
                        val packages = if (selectedTab == 0) publicPackages else myPackages

                        if (packages.isEmpty()) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Icon(
                                    if (selectedTab == 0) Icons.Default.SearchOff else Icons.Default.FolderOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    if (selectedTab == 0) "Nessun pacchetto trovato" else "Non hai ancora pacchetti",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(packages, key = { it.code }) { pkg ->
                                    val isInstalled = installedIds.contains(pkg.code)
                                    StorePackageCard(
                                        packageItem = pkg,
                                        isInstalled = isInstalled,
                                        isProcessing = processingPackageId == pkg.code,
                                        onInstall = { installPackage(pkg) },
                                        onUninstall = { uninstallPackage(pkg.code) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StorePackageCard(
    packageItem: PackageListItem,
    isInstalled: Boolean,
    isProcessing: Boolean,
    onInstall: () -> Unit,
    onUninstall: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isInstalled)
                CorrectGreenLight
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Title
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        packageItem.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (isInstalled) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = CorrectGreen
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Installato",
                                style = MaterialTheme.typography.labelSmall,
                                color = CorrectGreen,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Description if available
            packageItem.description?.takeIf { it.isNotBlank() }?.let { desc ->
                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Author
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    packageItem.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Questions count
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Quiz,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "${packageItem.total_questions} domande",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Download count
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "${packageItem.download_count}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action button
            if (isInstalled) {
                OutlinedButton(
                    onClick = onUninstall,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isProcessing,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = WrongRed
                    )
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Disinstalla")
                    }
                }
            } else {
                Button(
                    onClick = onInstall,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isProcessing,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Primary
                    )
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Installa")
                    }
                }
            }
        }
    }
}
