package com.example.genmemo.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.genmemo.data.model.InstalledPackage
import com.example.genmemo.data.model.OnlinePackage
import com.example.genmemo.data.repository.InstalledPackageRepository
import com.example.genmemo.data.repository.MemoryRepository
import com.example.genmemo.data.sync.ProgressSyncService
import com.example.genmemo.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: MemoryRepository,
    installedPackageRepository: InstalledPackageRepository,
    progressSyncService: ProgressSyncService,
    onNavigateToMemorize: () -> Unit,
    onNavigateToReview: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToOnlinePackage: () -> Unit = {},
    onStartPackageQuiz: (OnlinePackage) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val itemsDue by repository.countItemsDueForReview().collectAsState(initial = 0)
    val totalItems by repository.countAllItems().collectAsState(initial = 0)

    // Installed packages
    val installedPackages by installedPackageRepository.allPackages.collectAsState(initial = emptyList())

    // Apply decay on screen load
    LaunchedEffect(Unit) {
        repository.applyDecayToOverdueItems()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "GenMemo",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Impostazioni",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Stats Card
            item {
                StatsCard(
                    itemsDue = itemsDue,
                    totalItems = totalItems
                )
            }

            // Main Buttons
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    MainActionButton(
                        modifier = Modifier.weight(1f),
                        title = "Memorizza",
                        subtitle = "Aggiungi nuove cose",
                        icon = Icons.Default.Add,
                        gradientColors = listOf(MemorizeColor, Color(0xFF059669)),
                        onClick = onNavigateToMemorize
                    )

                    MainActionButton(
                        modifier = Modifier.weight(1f),
                        title = "Ripassa",
                        subtitle = if (itemsDue > 0) "$itemsDue da ripassare" else "Tutto ok!",
                        icon = Icons.Default.Refresh,
                        gradientColors = listOf(ReviewColor, Color(0xFFD97706)),
                        onClick = onNavigateToReview,
                        badgeCount = if (itemsDue > 0) itemsDue else null
                    )
                }
            }

            // Online Package Button
            item {
                OutlinedButton(
                    onClick = onNavigateToOnlinePackage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Primary
                    )
                ) {
                    Icon(
                        Icons.Default.Store,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Store Pacchetti",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Installed Packages Section
            if (installedPackages.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Pacchetti Installati",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(installedPackages, key = { it.packageId }) { pkg ->
                    val questionsDue = progressSyncService.countQuestionsDue(
                        pkg.packageId,
                        pkg.totalQuestions
                    )

                    InstalledPackageCard(
                        installedPackage = pkg,
                        questionsDue = questionsDue,
                        onStartQuiz = {
                            scope.launch {
                                installedPackageRepository.updateLastPlayed(pkg.packageId)
                                val onlinePackage = installedPackageRepository.toOnlinePackage(pkg)
                                onStartPackageQuiz(onlinePackage)
                            }
                        }
                    )
                }
            }

            // Empty state / Motivational message
            item {
                Spacer(modifier = Modifier.height(16.dp))
                if (totalItems == 0 && installedPackages.isEmpty()) {
                    Text(
                        text = "Inizia ad aggiungere cose da memorizzare o installa pacchetti dallo Store!",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (itemsDue == 0 && installedPackages.all {
                    progressSyncService.countQuestionsDue(it.packageId, it.totalQuestions) == 0
                }) {
                    Text(
                        text = "Ottimo lavoro! Nessun ripasso urgente.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = CorrectGreen,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun InstalledPackageCard(
    installedPackage: InstalledPackage,
    questionsDue: Int,
    onStartQuiz: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (questionsDue > 0)
                ReviewColor.copy(alpha = 0.1f)
            else
                CorrectGreenLight
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Package info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    installedPackage.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Questions count
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Quiz,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "${installedPackage.totalQuestions}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Due count
                    if (questionsDue > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = ReviewColor
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "$questionsDue da ripassare",
                                style = MaterialTheme.typography.bodySmall,
                                color = ReviewColor,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = CorrectGreen
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Tutto ripassato!",
                                style = MaterialTheme.typography.bodySmall,
                                color = CorrectGreen,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Play button
            Button(
                onClick = onStartQuiz,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (questionsDue > 0) ReviewColor else CorrectGreen
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                if (questionsDue > 0) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("$questionsDue")
                }
            }
        }
    }
}

@Composable
private fun StatsCard(
    itemsDue: Int,
    totalItems: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(
                value = totalItems.toString(),
                label = "Elementi",
                color = Primary
            )

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(48.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
            )

            StatItem(
                value = itemsDue.toString(),
                label = "Da ripassare",
                color = if (itemsDue > 0) ReviewColor else CorrectGreen
            )
        }
    }
}

@Composable
private fun StatItem(
    value: String,
    label: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MainActionButton(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    gradientColors: List<Color>,
    onClick: () -> Unit,
    badgeCount: Int? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(100),
        label = "scale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .aspectRatio(0.9f)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = gradientColors[0].copy(alpha = 0.3f),
                spotColor = gradientColors[0].copy(alpha = 0.3f)
            )
            .clip(RoundedCornerShape(24.dp))
            .background(
                brush = Brush.verticalGradient(gradientColors)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        // Badge
        if (badgeCount != null) {
            Badge(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                containerColor = Color.White,
                contentColor = gradientColors[0]
            ) {
                Text(
                    text = badgeCount.toString(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(48.dp),
                tint = Color.White
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )
        }
    }
}
