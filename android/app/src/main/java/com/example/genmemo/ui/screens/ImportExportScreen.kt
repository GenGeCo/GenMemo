package com.example.genmemo.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.genmemo.data.model.ItemType
import com.example.genmemo.data.repository.MemoryRepository
import com.example.genmemo.ui.theme.*
import com.example.genmemo.util.ImportExportManager
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportExportScreen(
    repository: MemoryRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showInstructionsChoice by remember { mutableStateOf(false) }
    var showAIInstructions by remember { mutableStateOf(false) }
    var selectedInstructionType by remember { mutableStateOf("text") } // "text" or "images"
    var isImporting by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<String?>(null) }
    var importProgress by remember { mutableStateOf<String?>(null) }

    // File picker for import
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                isImporting = true
                importProgress = "Leggendo il file..."
                importResult = null

                try {
                    val jsonContent = ImportExportManager.readJsonFromUri(context, it)
                    if (jsonContent != null) {
                        val result = ImportExportManager.parseJsonImport(jsonContent)
                        if (result != null) {
                            val (category, items) = result

                            if (category != null) {
                                // Check if there are images to download
                                val imageItems = items.filter { item ->
                                    item.type == ItemType.IMAGE && ImportExportManager.isUrl(item.question)
                                }

                                val updatedItems = if (imageItems.isNotEmpty()) {
                                    importProgress = "Scaricando ${imageItems.size} immagini..."
                                    val imageDir = File(context.filesDir, "images")
                                    if (!imageDir.exists()) imageDir.mkdirs()

                                    var downloadedCount = 0
                                    items.map { item ->
                                        if (item.type == ItemType.IMAGE && ImportExportManager.isUrl(item.question)) {
                                            downloadedCount++
                                            importProgress = "Scaricando immagine $downloadedCount/${imageItems.size}..."

                                            val localPath = ImportExportManager.downloadImage(item.question, imageDir)
                                            if (localPath != null) {
                                                item.copy(question = localPath)
                                            } else {
                                                // Keep URL if download fails, will show placeholder
                                                item
                                            }
                                        } else {
                                            item
                                        }
                                    }
                                } else {
                                    items
                                }

                                importProgress = "Salvando nel database..."
                                val count = repository.importCategoryWithItems(category, updatedItems)

                                val imageNote = if (imageItems.isNotEmpty()) {
                                    " (${imageItems.size} immagini scaricate)"
                                } else ""

                                importResult = "Importate $count schede nella categoria '${category.name}'$imageNote"
                            } else {
                                importResult = "Errore: categoria non valida"
                            }
                        } else {
                            importResult = "Errore: formato JSON non valido"
                        }
                    } else {
                        importResult = "Errore: impossibile leggere il file"
                    }
                } catch (e: Exception) {
                    importResult = "Errore: ${e.message}"
                }

                importProgress = null
                isImporting = false
            }
        }
    }

    // File picker for export
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            scope.launch {
                isExporting = true
                try {
                    val categories = repository.getAllCategoriesOnce()
                    val itemsByCategory = mutableMapOf<Long, List<com.example.genmemo.data.model.MemoryItem>>()

                    categories.forEach { category ->
                        itemsByCategory[category.id] = repository.getItemsByCategoryOnce(category.id)
                    }

                    val jsonContent = ImportExportManager.exportAllToJson(categories, itemsByCategory)
                    val success = ImportExportManager.writeJsonToUri(context, it, jsonContent)

                    if (success) {
                        Toast.makeText(context, "Esportazione completata!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Errore durante l'esportazione", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Errore: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                isExporting = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import / Export") },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // AI Instructions Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Primary.copy(alpha = 0.1f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Genera con AI",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        "Scarica le istruzioni per far generare flashcard a ChatGPT, Claude, Gemini o altra AI!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { showInstructionsChoice = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scarica Istruzioni AI")
                    }
                }
            }

            HorizontalDivider()

            // Import Section
            Text(
                "Importa Schede",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.FileDownload,
                            contentDescription = null,
                            tint = CorrectGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Importa da JSON",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "Carica un file .json generato dall'AI o da backup",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = {
                            importLauncher.launch(arrayOf("application/json", "*/*"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isImporting
                    ) {
                        if (isImporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isImporting) "Importando..." else "Seleziona File JSON")
                    }

                    importProgress?.let { progress ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                progress,
                                style = MaterialTheme.typography.bodySmall,
                                color = Primary
                            )
                        }
                    }

                    importResult?.let { result ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            result,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (result.startsWith("Errore")) WrongRed else CorrectGreen
                        )
                    }
                }
            }

            // Export Section
            Text(
                "Esporta",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.FileUpload,
                            contentDescription = null,
                            tint = ReviewColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Esporta tutte le schede",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "Salva un backup di tutte le tue flashcard",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = {
                            exportLauncher.launch("genmemo_backup.json")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isExporting
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Save, contentDescription = null)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isExporting) "Esportando..." else "Esporta JSON")
                    }
                }
            }

            // Tips
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Come funziona",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "1. Scarica le istruzioni AI (solo testo o con immagini)\n" +
                        "2. Incollale a ChatGPT/Claude e chiedi il corso che vuoi\n" +
                        "3. Salva il file JSON generato\n" +
                        "4. Importalo qui e inizia a studiare!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Instructions Choice Dialog
    if (showInstructionsChoice) {
        AlertDialog(
            onDismissRequest = { showInstructionsChoice = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = Primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tipo di Istruzioni")
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Scegli il tipo di flashcard che vuoi generare:",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    // Text Only Option
                    Card(
                        onClick = {
                            selectedInstructionType = "text"
                            showInstructionsChoice = false
                            showAIInstructions = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.TextFields,
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    "Solo Testo",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Domande e risposte testuali",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // With Images Option
                    Card(
                        onClick = {
                            selectedInstructionType = "images"
                            showInstructionsChoice = false
                            showAIInstructions = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = null,
                                tint = CorrectGreen,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    "Testo + Immagini",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Con foto da Wikipedia/Wikimedia",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showInstructionsChoice = false }) {
                    Text("Annulla")
                }
            }
        )
    }

    // AI Instructions Dialog
    if (showAIInstructions) {
        val instructions = if (selectedInstructionType == "images") {
            ImportExportManager.generateAIInstructionsWithImages()
        } else {
            ImportExportManager.generateAIInstructionsTextOnly()
        }

        val title = if (selectedInstructionType == "images") {
            "Istruzioni Testo + Immagini"
        } else {
            "Istruzioni Solo Testo"
        }

        AlertDialog(
            onDismissRequest = { showAIInstructions = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (selectedInstructionType == "images") Icons.Default.Image else Icons.Default.TextFields,
                        contentDescription = null,
                        tint = Primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(title)
                }
            },
            text = {
                Column {
                    Text(
                        "Clicca 'Copia' e incolla il testo a ChatGPT, Claude, Gemini o altra AI.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        if (selectedInstructionType == "images") {
                            "Esempi di richieste:\n" +
                            "• 'Fammi flashcard sulle bandiere europee'\n" +
                            "• 'Crea un corso sui monumenti famosi'\n" +
                            "• 'Genera flashcard sugli animali'"
                        } else {
                            "Esempi di richieste:\n" +
                            "• 'Fammi 100 flashcard di inglese B1'\n" +
                            "• 'Crea un corso sulle capitali europee'\n" +
                            "• 'Genera flashcard sui verbi irregolari'"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("GenMemo AI Instructions", instructions)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Istruzioni copiate negli appunti!", Toast.LENGTH_SHORT).show()
                        showAIInstructions = false
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Copia")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, instructions)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Condividi istruzioni"))
                    }
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Condividi")
                }
            }
        )
    }
}
