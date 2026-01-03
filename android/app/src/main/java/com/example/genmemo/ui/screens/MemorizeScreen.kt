package com.example.genmemo.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.genmemo.data.model.Category
import com.example.genmemo.data.model.ItemType
import com.example.genmemo.data.model.MemoryItem
import com.example.genmemo.data.repository.MemoryRepository
import com.example.genmemo.ui.theme.*
import com.example.genmemo.util.ImageUtils
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemorizeScreen(
    repository: MemoryRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var selectedType by remember { mutableStateOf(ItemType.IMAGE) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var imagePath by remember { mutableStateOf<String?>(null) }
    var answer by remember { mutableStateOf("") }
    var question by remember { mutableStateOf("") }
    var imagePrompt by remember { mutableStateOf("Come si chiama?") }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var showCategoryDropdown by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }

    val categories by repository.allCategories.collectAsState(initial = emptyList())

    // Ensure default category exists
    LaunchedEffect(Unit) {
        repository.ensureDefaultCategory()
    }

    // Auto-select first category
    LaunchedEffect(categories) {
        if (selectedCategory == null && categories.isNotEmpty()) {
            selectedCategory = categories.first()
        }
    }

    // Image picker
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                isLoading = true
                val path = ImageUtils.processAndSaveImage(context, it)
                if (path != null) {
                    imagePath = path
                    imageUri = Uri.fromFile(File(path))
                }
                isLoading = false
            }
        }
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let {
            scope.launch {
                isLoading = true
                // Save bitmap directly
                val fileName = "img_${System.currentTimeMillis()}.jpg"
                val file = File(context.filesDir, fileName)
                file.outputStream().use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
                }
                imagePath = file.absolutePath
                imageUri = Uri.fromFile(file)
                isLoading = false
            }
        }
    }

    fun resetForm() {
        imageUri = null
        imagePath = null
        answer = ""
        question = ""
        imagePrompt = "Come si chiama?"
        showSuccess = true
    }

    fun saveItem() {
        if (selectedCategory == null) return

        scope.launch {
            isLoading = true
            val item = when (selectedType) {
                ItemType.IMAGE -> {
                    if (imagePath == null || answer.isBlank()) {
                        isLoading = false
                        return@launch
                    }
                    MemoryItem(
                        type = ItemType.IMAGE,
                        question = imagePath!!,
                        answer = answer.trim(),
                        categoryId = selectedCategory!!.id,
                        prompt = imagePrompt.trim().ifBlank { "Come si chiama?" }
                    )
                }
                ItemType.QUESTION -> {
                    if (question.isBlank() || answer.isBlank()) {
                        isLoading = false
                        return@launch
                    }
                    MemoryItem(
                        type = ItemType.QUESTION,
                        question = question.trim(),
                        answer = answer.trim(),
                        categoryId = selectedCategory!!.id
                    )
                }
            }
            repository.insertItem(item)
            isLoading = false
            resetForm()
        }
    }

    // Hide success message after delay
    LaunchedEffect(showSuccess) {
        if (showSuccess) {
            kotlinx.coroutines.delay(2000)
            showSuccess = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Memorizza") },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Type Selector
                TypeSelector(
                    selectedType = selectedType,
                    onTypeSelected = { selectedType = it }
                )

                // Category Selector
                CategorySelector(
                    categories = categories,
                    selectedCategory = selectedCategory,
                    expanded = showCategoryDropdown,
                    onExpandedChange = { showCategoryDropdown = it },
                    onCategorySelected = {
                        selectedCategory = it
                        showCategoryDropdown = false
                    }
                )

                // Content based on type
                AnimatedContent(
                    targetState = selectedType,
                    transitionSpec = {
                        fadeIn() + slideInHorizontally() togetherWith
                                fadeOut() + slideOutHorizontally()
                    },
                    label = "type_content"
                ) { type ->
                    when (type) {
                        ItemType.IMAGE -> {
                            ImageInputSection(
                                imageUri = imageUri,
                                isLoading = isLoading,
                                onPickImage = { imagePickerLauncher.launch("image/*") },
                                onTakePhoto = { cameraLauncher.launch(null) },
                                onRemoveImage = {
                                    imagePath?.let { ImageUtils.deleteImage(it) }
                                    imageUri = null
                                    imagePath = null
                                },
                                prompt = imagePrompt,
                                onPromptChange = { imagePrompt = it },
                                answer = answer,
                                onAnswerChange = { answer = it }
                            )
                        }
                        ItemType.QUESTION -> {
                            QuestionInputSection(
                                question = question,
                                onQuestionChange = { question = it },
                                answer = answer,
                                onAnswerChange = { answer = it }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Save Button
                val canSave = when (selectedType) {
                    ItemType.IMAGE -> imagePath != null && answer.isNotBlank() && selectedCategory != null
                    ItemType.QUESTION -> question.isNotBlank() && answer.isNotBlank() && selectedCategory != null
                }

                Button(
                    onClick = { saveItem() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = canSave && !isLoading,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MemorizeColor
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Salva", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            // Success Snackbar
            AnimatedVisibility(
                visible = showSuccess,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = CorrectGreen
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Salvato con successo!",
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeSelector(
    selectedType: ItemType,
    onTypeSelected: (ItemType) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TypeChip(
            modifier = Modifier.weight(1f),
            title = "Immagine",
            icon = Icons.Default.Image,
            selected = selectedType == ItemType.IMAGE,
            onClick = { onTypeSelected(ItemType.IMAGE) }
        )
        TypeChip(
            modifier = Modifier.weight(1f),
            title = "Domanda",
            icon = Icons.Default.QuestionAnswer,
            selected = selectedType == ItemType.QUESTION,
            onClick = { onTypeSelected(ItemType.QUESTION) }
        )
    }
}

@Composable
private fun TypeChip(
    modifier: Modifier = Modifier,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (selected) Primary else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = modifier
            .height(56.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = contentColor)
            Spacer(modifier = Modifier.width(8.dp))
            Text(title, color = contentColor, fontWeight = FontWeight.Medium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategorySelector(
    categories: List<Category>,
    selectedCategory: Category?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onCategorySelected: (Category) -> Unit
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange
    ) {
        OutlinedTextField(
            value = selectedCategory?.name ?: "Seleziona categoria",
            onValueChange = {},
            readOnly = true,
            label = { Text("Categoria") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            shape = RoundedCornerShape(12.dp)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
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
                    onClick = { onCategorySelected(category) }
                )
            }
        }
    }
}

@Composable
private fun ImageInputSection(
    imageUri: Uri?,
    isLoading: Boolean,
    onPickImage: () -> Unit,
    onTakePhoto: () -> Unit,
    onRemoveImage: () -> Unit,
    prompt: String,
    onPromptChange: (String) -> Unit,
    answer: String,
    onAnswerChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Image Preview / Picker
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.5f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator()
            } else if (imageUri != null) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = "Immagine selezionata",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    IconButton(
                        onClick = onRemoveImage,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(
                                Color.Black.copy(alpha = 0.5f),
                                RoundedCornerShape(8.dp)
                            )
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Rimuovi",
                            tint = Color.White
                        )
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = onPickImage,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Galleria")
                        }
                        OutlinedButton(
                            onClick = onTakePhoto,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Foto")
                        }
                    }
                    Text(
                        "Seleziona o scatta una foto",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Prompt field (the question to ask during review)
        OutlinedTextField(
            value = prompt,
            onValueChange = onPromptChange,
            label = { Text("Domanda") },
            placeholder = { Text("Es: Come si chiama questa persona?") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            supportingText = { Text("La domanda che verrà mostrata durante il ripasso") }
        )

        // Answer field
        OutlinedTextField(
            value = answer,
            onValueChange = onAnswerChange,
            label = { Text("Risposta") },
            placeholder = { Text("Es: Antonio, Parigi, Cane...") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )
    }
}

@Composable
private fun QuestionInputSection(
    question: String,
    onQuestionChange: (String) -> Unit,
    answer: String,
    onAnswerChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = question,
            onValueChange = onQuestionChange,
            label = { Text("Domanda") },
            placeholder = { Text("Es: Qual è la capitale della Francia?") },
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            shape = RoundedCornerShape(12.dp),
            maxLines = 4
        )

        OutlinedTextField(
            value = answer,
            onValueChange = onAnswerChange,
            label = { Text("Risposta") },
            placeholder = { Text("Es: Parigi") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )
    }
}
