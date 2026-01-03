package com.example.genmemo.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.genmemo.data.model.ItemType
import com.example.genmemo.data.model.MemoryItem
import com.example.genmemo.data.repository.MemoryRepository
import com.example.genmemo.ui.theme.*
import com.example.genmemo.util.SpacedRepetitionEngine
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewSessionScreen(
    repository: MemoryRepository,
    itemType: String,
    categoryId: Long?,
    questionCount: Int,
    infiniteMode: Boolean = false,
    onFinish: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var items by remember { mutableStateOf<List<MemoryItem>>(emptyList()) }
    var currentIndex by remember { mutableStateOf(0) }
    var userAnswer by remember { mutableStateOf("") }
    var showResult by remember { mutableStateOf(false) }
    var isCorrect by remember { mutableStateOf(false) }
    var correctCount by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var sessionComplete by remember { mutableStateOf(false) }

    // Infinite mode tracking: consecutive correct answers per item
    var consecutiveCorrect by remember { mutableStateOf(0) }
    var skippedCount by remember { mutableStateOf(0) }

    // Load items
    LaunchedEffect(Unit) {
        val type = when (itemType) {
            "IMAGE" -> ItemType.IMAGE
            "QUESTION" -> ItemType.QUESTION
            else -> null
        }
        items = repository.getItemsForReview(
            count = questionCount,
            type = type,
            categoryId = categoryId
        )
        isLoading = false

        if (items.isEmpty()) {
            sessionComplete = true
        }
    }

    val currentItem = items.getOrNull(currentIndex)
    val progress = if (items.isNotEmpty()) (currentIndex + 1).toFloat() / items.size else 0f

    fun submitAnswer() {
        if (currentItem == null || showResult) return

        focusManager.clearFocus()
        isCorrect = SpacedRepetitionEngine.checkAnswer(userAnswer, currentItem.answer)

        if (isCorrect) {
            correctCount++
            consecutiveCorrect++
        } else {
            consecutiveCorrect = 0 // Reset on wrong answer
        }

        // Update item in database
        scope.launch {
            repository.processAnswer(currentItem, isCorrect)
        }

        showResult = true
    }

    fun skipQuestion() {
        skippedCount++
        consecutiveCorrect = 0
        if (currentIndex < items.size - 1) {
            currentIndex++
            userAnswer = ""
            showResult = false
        } else {
            sessionComplete = true
        }
    }

    fun nextQuestion() {
        // In infinite mode, need 2 consecutive correct to advance
        val canAdvance = if (infiniteMode) {
            consecutiveCorrect >= 2
        } else {
            true
        }

        if (canAdvance) {
            consecutiveCorrect = 0 // Reset for next item
            if (currentIndex < items.size - 1) {
                currentIndex++
                userAnswer = ""
                showResult = false
            } else {
                sessionComplete = true
            }
        } else {
            // In infinite mode with less than 2 consecutive: repeat same question
            userAnswer = ""
            showResult = false
        }
    }

    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    if (sessionComplete) {
        SessionCompleteScreen(
            totalQuestions = items.size,
            correctAnswers = correctCount,
            onFinish = onFinish
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("${currentIndex + 1} / ${items.size}")
                },
                navigationIcon = {
                    IconButton(onClick = onFinish) {
                        Icon(Icons.Default.Close, "Esci")
                    }
                },
                actions = {
                    // Score indicator
                    Text(
                        "$correctCount corrette",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CorrectGreen,
                        modifier = Modifier.padding(end = 16.dp)
                    )
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
            // Progress bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = Primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Question content
                currentItem?.let { item ->
                    QuestionContent(
                        item = item,
                        showResult = showResult,
                        isCorrect = isCorrect,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Answer section
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AnimatedVisibility(
                        visible = showResult,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        ResultFeedback(
                            isCorrect = isCorrect,
                            correctAnswer = currentItem?.answer ?: "",
                            userAnswer = userAnswer
                        )
                    }

                    if (!showResult) {
                        OutlinedTextField(
                            value = userAnswer,
                            onValueChange = { userAnswer = it },
                            label = { Text("La tua risposta") },
                            placeholder = { Text("Scrivi qui...") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submitAnswer() })
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Skip button
                            OutlinedButton(
                                onClick = { skipQuestion() },
                                modifier = Modifier
                                    .weight(0.35f)
                                    .height(56.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(
                                    Icons.Default.SkipNext,
                                    contentDescription = "Salta",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Salta", style = MaterialTheme.typography.bodyMedium)
                            }

                            // Submit button
                            Button(
                                onClick = { submitAnswer() },
                                modifier = Modifier
                                    .weight(0.65f)
                                    .height(56.dp),
                                enabled = userAnswer.isNotBlank(),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Primary
                                )
                            ) {
                                Text("Verifica", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    } else {
                        // Show infinite mode progress if active
                        if (infiniteMode && isCorrect && consecutiveCorrect < 2) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Primary.copy(alpha = 0.1f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Repeat,
                                        contentDescription = null,
                                        tint = Primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Corretto $consecutiveCorrect/2 - Ripeti ancora!",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Determine button text based on mode
                        val buttonText = when {
                            infiniteMode && isCorrect && consecutiveCorrect < 2 -> "Riprova"
                            currentIndex < items.size - 1 -> "Prossima"
                            else -> "Termina"
                        }

                        val buttonIcon = when {
                            infiniteMode && isCorrect && consecutiveCorrect < 2 -> Icons.Default.Refresh
                            currentIndex < items.size - 1 -> Icons.Default.ArrowForward
                            else -> Icons.Default.Check
                        }

                        Button(
                            onClick = { nextQuestion() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isCorrect) CorrectGreen else Primary
                            )
                        ) {
                            Text(buttonText, style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(buttonIcon, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionContent(
    item: MemoryItem,
    showResult: Boolean,
    isCorrect: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        when (item.type) {
            ItemType.IMAGE -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = File(item.question),
                            contentDescription = "Immagine da identificare",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(24.dp)),
                            contentScale = ContentScale.Crop
                        )

                        // Overlay for result
                        if (showResult) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        if (isCorrect) CorrectGreen.copy(alpha = 0.2f)
                                        else WrongRed.copy(alpha = 0.2f)
                                    )
                            )
                        }
                    }
                }

                Text(
                    text = item.prompt,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                )
            }

            ItemType.QUESTION -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.QuestionMark,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Primary
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = item.question,
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultFeedback(
    isCorrect: Boolean,
    correctAnswer: String,
    userAnswer: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCorrect) CorrectGreenLight else WrongRedLight
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isCorrect) Icons.Default.CheckCircle else Icons.Default.Cancel,
                    contentDescription = null,
                    tint = if (isCorrect) CorrectGreen else WrongRed,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (isCorrect) "Corretto!" else "Sbagliato",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isCorrect) CorrectGreen else WrongRed
                )
            }

            if (!isCorrect) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "La risposta era:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = correctAnswer,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Hai scritto: $userAnswer",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SessionCompleteScreen(
    totalQuestions: Int,
    correctAnswers: Int,
    onFinish: () -> Unit
) {
    val percentage = if (totalQuestions > 0) (correctAnswers * 100) / totalQuestions else 0

    val message = when {
        totalQuestions == 0 -> "Nessun elemento da ripassare!"
        percentage >= 90 -> "Eccellente!"
        percentage >= 70 -> "Ottimo lavoro!"
        percentage >= 50 -> "Buon inizio!"
        else -> "Continua a esercitarti!"
    }

    val emoji = when {
        totalQuestions == 0 -> "🎯"
        percentage >= 90 -> "🏆"
        percentage >= 70 -> "⭐"
        percentage >= 50 -> "👍"
        else -> "💪"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = emoji,
            style = MaterialTheme.typography.displayLarge
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (totalQuestions > 0) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "$correctAnswers / $totalQuestions",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = Primary
                    )
                    Text(
                        text = "Risposte corrette",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    LinearProgressIndicator(
                        progress = { percentage / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = when {
                            percentage >= 70 -> CorrectGreen
                            percentage >= 50 -> ReviewColor
                            else -> WrongRed
                        },
                        trackColor = MaterialTheme.colorScheme.surface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "$percentage%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            percentage >= 70 -> CorrectGreen
                            percentage >= 50 -> ReviewColor
                            else -> WrongRed
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onFinish,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Primary
            )
        ) {
            Icon(Icons.Default.Home, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Torna alla Home", style = MaterialTheme.typography.titleMedium)
        }
    }
}
