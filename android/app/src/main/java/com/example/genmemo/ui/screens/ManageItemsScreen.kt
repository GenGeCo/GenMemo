package com.example.genmemo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.genmemo.data.model.Category
import com.example.genmemo.data.model.ItemType
import com.example.genmemo.data.model.MemoryItem
import com.example.genmemo.data.repository.MemoryRepository
import com.example.genmemo.ui.theme.*
import com.example.genmemo.util.ImageUtils
import com.example.genmemo.util.SpacedRepetitionEngine
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageItemsScreen(
    repository: MemoryRepository,
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val items by repository.allItems.collectAsState(initial = emptyList())
    val categories by repository.allCategories.collectAsState(initial = emptyList())

    var filterType by remember { mutableStateOf<ItemType?>(null) }
    var showDeleteDialog by remember { mutableStateOf<MemoryItem?>(null) }
    var showDetailsDialog by remember { mutableStateOf<MemoryItem?>(null) }

    val filteredItems = when (filterType) {
        ItemType.IMAGE -> items.filter { it.type == ItemType.IMAGE }
        ItemType.QUESTION -> items.filter { it.type == ItemType.QUESTION }
        else -> items
    }

    val categoriesMap = categories.associateBy { it.id }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Elementi (${filteredItems.size})") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro")
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
            // Filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filterType == null,
                    onClick = { filterType = null },
                    label = { Text("Tutti") },
                    leadingIcon = if (filterType == null) {
                        { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                    } else null
                )
                FilterChip(
                    selected = filterType == ItemType.IMAGE,
                    onClick = { filterType = ItemType.IMAGE },
                    label = { Text("Immagini") },
                    leadingIcon = if (filterType == ItemType.IMAGE) {
                        { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                    } else null
                )
                FilterChip(
                    selected = filterType == ItemType.QUESTION,
                    onClick = { filterType = ItemType.QUESTION },
                    label = { Text("Domande") },
                    leadingIcon = if (filterType == ItemType.QUESTION) {
                        { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                    } else null
                )
            }

            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Inbox,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Nessun elemento",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        MemoryItemCard(
                            item = item,
                            category = item.categoryId?.let { categoriesMap[it] },
                            onDetails = { showDetailsDialog = item },
                            onDelete = { showDeleteDialog = item }
                        )
                    }
                }
            }
        }

        // Details Dialog
        showDetailsDialog?.let { item ->
            ItemDetailsDialog(
                item = item,
                category = item.categoryId?.let { categoriesMap[it] },
                onDismiss = { showDetailsDialog = null }
            )
        }

        // Delete Confirmation
        showDeleteDialog?.let { item ->
            AlertDialog(
                onDismissRequest = { showDeleteDialog = null },
                title = { Text("Elimina elemento?") },
                text = {
                    Text("Vuoi eliminare questo elemento? L'azione non può essere annullata.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                if (item.type == ItemType.IMAGE) {
                                    ImageUtils.deleteImage(item.question)
                                }
                                repository.deleteItem(item)
                                showDeleteDialog = null
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = WrongRed
                        )
                    ) {
                        Text("Elimina")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = null }) {
                        Text("Annulla")
                    }
                }
            )
        }
    }
}

@Composable
private fun MemoryItemCard(
    item: MemoryItem,
    category: Category?,
    onDetails: () -> Unit,
    onDelete: () -> Unit
) {
    val stats = SpacedRepetitionEngine.getItemStats(item)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Preview
            when (item.type) {
                ItemType.IMAGE -> {
                    AsyncImage(
                        model = File(item.question),
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                ItemType.QUESTION -> {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.QuestionMark,
                            contentDescription = null,
                            tint = Primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.answer,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (item.type == ItemType.QUESTION) {
                    Text(
                        text = item.question,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Score badge
                    ScoreBadge(score = stats.score, isMastered = stats.isMastered)

                    // Category badge
                    category?.let {
                        val catColor = Color(it.color.toInt())
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(catColor.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = it.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = catColor
                            )
                        }
                    }
                }
            }

            // Actions
            IconButton(onClick = onDetails) {
                Icon(
                    Icons.Default.Info,
                    "Dettagli",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Elimina", tint = WrongRed)
            }
        }
    }
}

@Composable
private fun ScoreBadge(score: Int, isMastered: Boolean) {
    val color = when {
        isMastered -> ScoreMastered
        score >= 70 -> ScoreHigh
        score >= 30 -> ScoreMedium
        else -> ScoreLow
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.2f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = if (isMastered) "Padroneggiato" else "$score%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun ItemDetailsDialog(
    item: MemoryItem,
    category: Category?,
    onDismiss: () -> Unit
) {
    val stats = SpacedRepetitionEngine.getItemStats(item)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dettagli Elemento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Preview
                when (item.type) {
                    ItemType.IMAGE -> {
                        AsyncImage(
                            model = File(item.question),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.5f)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    ItemType.QUESTION -> {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Domanda:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    item.question,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }

                // Answer
                Row {
                    Text(
                        "Risposta: ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        item.answer,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Category
                category?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Categoria: ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(it.color.toInt()))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            it.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                HorizontalDivider()

                // Stats
                Text(
                    "Statistiche",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatRow("Punteggio", "${stats.score}%")
                    StatRow("Streak correnti", "${stats.streak}")
                    StatRow("Giorni corretti", "${stats.correctDays} / 10")
                    StatRow(
                        "Prossimo ripasso",
                        if (stats.isDue) "Oggi!" else "Tra ${stats.daysUntilReview} giorni"
                    )
                    if (stats.isMastered) {
                        Text(
                            "Padroneggiato!",
                            color = ScoreMastered,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Chiudi")
            }
        }
    )
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
