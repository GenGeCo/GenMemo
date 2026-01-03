package com.example.genmemo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.genmemo.data.model.Category
import com.example.genmemo.data.model.ItemType
import com.example.genmemo.data.repository.MemoryRepository
import com.example.genmemo.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewSetupScreen(
    repository: MemoryRepository,
    onNavigateBack: () -> Unit,
    onStartReview: (type: String, categoryId: Long, count: Int, infiniteMode: Boolean) -> Unit
) {
    var selectedType by remember { mutableStateOf<String>("ALL") } // ALL, IMAGE, QUESTION
    var selectedCategory by remember { mutableStateOf<Category?>(null) } // null = all
    var questionCount by remember { mutableStateOf(20) }
    var showCategoryDropdown by remember { mutableStateOf(false) }
    var infiniteMode by remember { mutableStateOf(false) }

    val categories by repository.allCategories.collectAsState(initial = emptyList())
    val totalItems by repository.countAllItems().collectAsState(initial = 0)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configura Ripasso") },
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
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // Type Selection
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Cosa vuoi ripassare?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReviewTypeChip(
                        modifier = Modifier.weight(1f),
                        title = "Tutto",
                        icon = Icons.Default.Layers,
                        selected = selectedType == "ALL",
                        onClick = { selectedType = "ALL" }
                    )
                    ReviewTypeChip(
                        modifier = Modifier.weight(1f),
                        title = "Immagini",
                        icon = Icons.Default.Image,
                        selected = selectedType == "IMAGE",
                        onClick = { selectedType = "IMAGE" }
                    )
                    ReviewTypeChip(
                        modifier = Modifier.weight(1f),
                        title = "Domande",
                        icon = Icons.Default.QuestionAnswer,
                        selected = selectedType == "QUESTION",
                        onClick = { selectedType = "QUESTION" }
                    )
                }
            }

            // Category Selection
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Quale categoria?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                ExposedDropdownMenuBox(
                    expanded = showCategoryDropdown,
                    onExpandedChange = { showCategoryDropdown = it }
                ) {
                    OutlinedTextField(
                        value = selectedCategory?.name ?: "Tutte le categorie",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCategoryDropdown) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    ExposedDropdownMenu(
                        expanded = showCategoryDropdown,
                        onDismissRequest = { showCategoryDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Layers,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Tutte le categorie")
                                }
                            },
                            onClick = {
                                selectedCategory = null
                                showCategoryDropdown = false
                            }
                        )
                        HorizontalDivider()
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(Color(category.color.toInt()))
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(category.name)
                                    }
                                },
                                onClick = {
                                    selectedCategory = category
                                    showCategoryDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            // Question Count Slider
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Quante domande?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "$questionCount",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Primary
                    )
                }

                Slider(
                    value = questionCount.toFloat(),
                    onValueChange = { questionCount = it.toInt() },
                    valueRange = 10f..100f,
                    steps = 8, // 10, 20, 30, ... 100
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = Primary,
                        activeTrackColor = Primary
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("10", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("100", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Infinite Mode Toggle
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (infiniteMode) Primary.copy(alpha = 0.1f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { infiniteMode = !infiniteMode }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Modalità Infinita",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Ripeti finché non rispondi 2 volte corretto",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = infiniteMode,
                        onCheckedChange = { infiniteMode = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Primary,
                            checkedTrackColor = Primary.copy(alpha = 0.5f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Start Button
            Button(
                onClick = {
                    onStartReview(
                        selectedType,
                        selectedCategory?.id ?: -1L,
                        questionCount,
                        infiniteMode
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = totalItems > 0,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ReviewColor
                )
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Inizia Ripasso", style = MaterialTheme.typography.titleMedium)
            }

            if (totalItems == 0) {
                Text(
                    "Aggiungi prima qualcosa da memorizzare!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
private fun ReviewTypeChip(
    modifier: Modifier = Modifier,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (selected) ReviewColor else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = modifier
            .height(72.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(title, color = contentColor, style = MaterialTheme.typography.labelMedium)
        }
    }
}
