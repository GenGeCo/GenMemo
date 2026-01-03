package com.example.genmemo.ui.screens

import android.speech.tts.TextToSpeech
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.example.genmemo.data.model.*
import com.example.genmemo.data.api.GenMemoApiService
import com.example.genmemo.data.auth.AuthManager
import com.example.genmemo.data.sync.ProgressSyncService
import com.example.genmemo.data.sync.SyncResult
import com.example.genmemo.ui.theme.*
import kotlinx.coroutines.launch
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineQuizScreen(
    onlinePackage: OnlinePackage,
    authManager: AuthManager?,
    progressSyncService: ProgressSyncService?,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    // Get questions due for review if spaced repetition is enabled
    val questionIndices = remember(onlinePackage) {
        if (progressSyncService != null) {
            // Get questions due for review, or all if none are due
            val dueQuestions = progressSyncService.getQuestionsDueForReview(
                onlinePackage.meta.uuid,
                onlinePackage.questions.size
            )
            if (dueQuestions.isNotEmpty()) dueQuestions else (0 until onlinePackage.questions.size).toList()
        } else {
            (0 until onlinePackage.questions.size).toList()
        }
    }

    var currentListIndex by remember { mutableStateOf(0) }
    var correctCount by remember { mutableStateOf(0) }
    var showResult by remember { mutableStateOf(false) }
    var isCorrect by remember { mutableStateOf(false) }
    var sessionComplete by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }

    // Session tracking for server sync
    var serverSessionId by remember { mutableStateOf<Int?>(null) }
    val sessionStartTime = remember { System.currentTimeMillis() }

    // Start server session when quiz begins (if authenticated)
    LaunchedEffect(Unit) {
        val token = authManager?.token
        if (token != null) {
            val result = GenMemoApiService.startSession(
                token = token,
                packageUuid = onlinePackage.meta.uuid,
                deviceInfo = android.os.Build.MODEL,
                appVersion = null
            )
            if (result is GenMemoApiService.ApiResult.Success) {
                serverSessionId = result.data.session_id
            }
        }
    }

    // The actual question index in the package
    val currentQuestionIndex = questionIndices.getOrNull(currentListIndex) ?: 0

    // User answers
    var userTextAnswer by remember { mutableStateOf("") }
    var selectedAnswerIndex by remember { mutableStateOf<Int?>(null) }
    var selectedTrueFalse by remember { mutableStateOf<Boolean?>(null) }

    // TTS
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }
    var availableLanguages by remember { mutableStateOf<Set<Locale>>(emptySet()) }
    // Detected language for current question (auto-detected per text)
    var currentDetectedLanguage by remember { mutableStateOf(Locale.ENGLISH) }
    // User override language (if user manually selects a different language)
    var overrideLanguage by remember { mutableStateOf<Locale?>(null) }
    // Speech rate (0.5 = slow, 1.0 = normal, 2.0 = fast)
    var speechRate by remember { mutableStateOf(1.0f) }

    // Initialize TTS
    LaunchedEffect(Unit) {
        if (onlinePackage.tts.enabled) {
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    // Get available languages
                    availableLanguages = tts?.availableLanguages ?: emptySet()
                    ttsReady = true
                }
            }
        }
    }

    // Reset override and detect language when question changes
    LaunchedEffect(currentQuestionIndex, ttsReady) {
        val question = onlinePackage.questions.getOrNull(currentQuestionIndex)
        if (question != null && onlinePackage.tts.enabled && ttsReady) {
            // Reset override for new question (each question gets fresh detection)
            overrideLanguage = null
            // Detect language from question text
            currentDetectedLanguage = detectLanguage(question.question)
            tts?.language = currentDetectedLanguage
            tts?.setSpeechRate(speechRate)

            if (question.questionTts) {
                tts?.speak(question.question, TextToSpeech.QUEUE_FLUSH, null, "question")
            }
        }
    }

    // Cleanup TTS
    DisposableEffect(Unit) {
        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }

    val currentQuestion = onlinePackage.questions.getOrNull(currentQuestionIndex)
    val progress = if (questionIndices.isNotEmpty()) {
        (currentListIndex + 1).toFloat() / questionIndices.size
    } else 0f

    fun checkAnswer() {
        currentQuestion?.let { q ->
            isCorrect = when (q.mode) {
                QuestionMode.MULTIPLE -> {
                    selectedAnswerIndex?.let { idx ->
                        q.answers?.getOrNull(idx)?.correct == true
                    } ?: false
                }
                QuestionMode.TRUE_FALSE -> {
                    selectedTrueFalse == q.correctAnswer?.asBoolean()
                }
                QuestionMode.WRITE_EXACT, QuestionMode.WRITE_WORD -> {
                    userTextAnswer.trim().lowercase() ==
                            q.correctAnswer?.asString()?.lowercase()
                }
                QuestionMode.WRITE_PARTIAL -> {
                    val answer = userTextAnswer.trim().lowercase()
                    val correct = q.correctAnswer?.asString()?.lowercase() ?: ""
                    val alternatives = q.acceptAlso?.map { it.lowercase() } ?: emptyList()

                    answer.contains(correct) || alternatives.any { answer.contains(it) }
                }
            }

            if (isCorrect) correctCount++

            // Update progress in spaced repetition system
            progressSyncService?.updateQuestionProgress(
                onlinePackage.meta.uuid,
                currentQuestionIndex,
                isCorrect
            )

            showResult = true
        }
    }

    fun nextQuestion() {
        if (currentListIndex < questionIndices.size - 1) {
            currentListIndex++
            userTextAnswer = ""
            selectedAnswerIndex = null
            selectedTrueFalse = null
            showResult = false
        } else {
            // Session complete - sync all progress with server
            isSyncing = true
            scope.launch {
                val token = authManager?.token

                // 1. Sync spaced repetition progress
                progressSyncService?.syncPackageProgress(onlinePackage.meta.uuid)

                // 2. Save general progress (score, time, attempts)
                if (token != null) {
                    val timeSpentSeconds = ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt()
                    val scorePercent = if (questionIndices.isNotEmpty()) {
                        (correctCount * 100) / questionIndices.size
                    } else 0

                    GenMemoApiService.saveProgress(
                        token = token,
                        packageUuid = onlinePackage.meta.uuid,
                        score = scorePercent,
                        totalQuestions = questionIndices.size,
                        timeSpent = timeSpentSeconds
                    )

                    // 3. End server session
                    serverSessionId?.let { sessionId ->
                        GenMemoApiService.endSession(
                            token = token,
                            sessionId = sessionId,
                            questionsAnswered = questionIndices.size,
                            correctAnswers = correctCount
                        )
                    }
                }

                isSyncing = false
                sessionComplete = true
            }
        }
    }

    // Show syncing indicator
    if (isSyncing) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Primary)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Sincronizzazione progressi...")
            }
        }
        return
    }

    if (sessionComplete) {
        OnlineQuizCompleteScreen(
            packageName = onlinePackage.meta.name,
            totalQuestions = questionIndices.size,
            correctAnswers = correctCount,
            onFinish = onFinish
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("${currentListIndex + 1} / ${questionIndices.size}")
                },
                navigationIcon = {
                    IconButton(onClick = onFinish) {
                        Icon(Icons.Default.Close, "Esci")
                    }
                },
                actions = {
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

            currentQuestion?.let { question ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Question content
                    val effectiveLanguage = overrideLanguage ?: currentDetectedLanguage
                    QuestionSection(
                        question = question,
                        ttsEnabled = onlinePackage.tts.enabled,
                        detectedLanguage = effectiveLanguage,
                        availableLanguages = availableLanguages,
                        speechRate = speechRate,
                        onSpeechRateChange = { newRate ->
                            speechRate = newRate
                            tts?.setSpeechRate(newRate)
                        },
                        onSpeak = { text ->
                            val locale = overrideLanguage ?: detectLanguage(text)
                            tts?.language = locale
                            tts?.setSpeechRate(speechRate)
                            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "manual")
                        },
                        onLanguageChange = { newLocale ->
                            overrideLanguage = newLocale
                            tts?.language = newLocale
                        },
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Answer section
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Result feedback
                        AnimatedVisibility(
                            visible = showResult,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            OnlineQuizResultFeedback(
                                isCorrect = isCorrect,
                                question = question,
                                userAnswer = when (question.mode) {
                                    QuestionMode.MULTIPLE -> selectedAnswerIndex?.let {
                                        question.answers?.getOrNull(it)?.text
                                    } ?: ""
                                    QuestionMode.TRUE_FALSE -> if (selectedTrueFalse == true) "Vero" else "Falso"
                                    else -> userTextAnswer
                                }
                            )
                        }

                        // Answer input based on mode
                        if (!showResult) {
                            when (question.mode) {
                                QuestionMode.MULTIPLE -> {
                                    MultipleChoiceAnswers(
                                        answers = question.answers ?: emptyList(),
                                        selectedIndex = selectedAnswerIndex,
                                        onSelect = { selectedAnswerIndex = it },
                                        ttsEnabled = onlinePackage.tts.enabled,
                                        availableLanguages = availableLanguages,
                                        speechRate = speechRate,
                                        onSpeechRateChange = { newRate ->
                                            speechRate = newRate
                                            tts?.setSpeechRate(newRate)
                                        },
                                        onSpeak = { text, locale ->
                                            tts?.language = locale
                                            tts?.setSpeechRate(speechRate)
                                            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "answer")
                                        }
                                    )
                                }

                                QuestionMode.TRUE_FALSE -> {
                                    TrueFalseAnswers(
                                        selected = selectedTrueFalse,
                                        onSelect = { selectedTrueFalse = it }
                                    )
                                }

                                QuestionMode.WRITE_EXACT, QuestionMode.WRITE_WORD, QuestionMode.WRITE_PARTIAL -> {
                                    WriteAnswerField(
                                        value = userTextAnswer,
                                        onValueChange = { userTextAnswer = it },
                                        onSubmit = { checkAnswer() },
                                        placeholder = when (question.mode) {
                                            QuestionMode.WRITE_WORD -> "Scrivi la parola..."
                                            else -> "Scrivi la risposta..."
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Submit button
                            Button(
                                onClick = { checkAnswer() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                enabled = when (question.mode) {
                                    QuestionMode.MULTIPLE -> selectedAnswerIndex != null
                                    QuestionMode.TRUE_FALSE -> selectedTrueFalse != null
                                    else -> userTextAnswer.isNotBlank()
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Primary)
                            ) {
                                Text("Verifica", style = MaterialTheme.typography.bodyLarge)
                            }
                        } else {
                            // Next button
                            Button(
                                onClick = { nextQuestion() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isCorrect) CorrectGreen else Primary
                                )
                            ) {
                                Text(
                                    if (currentListIndex < questionIndices.size - 1) "Prossima" else "Termina",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    if (currentListIndex < questionIndices.size - 1)
                                        Icons.Default.ArrowForward else Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionSection(
    question: OnlineQuestion,
    ttsEnabled: Boolean,
    detectedLanguage: Locale,
    availableLanguages: Set<Locale>,
    speechRate: Float,
    onSpeechRateChange: (Float) -> Unit,
    onSpeak: (String) -> Unit,
    onLanguageChange: (Locale) -> Unit,
    modifier: Modifier = Modifier
) {
    var showLanguageDialog by remember { mutableStateOf(false) }

    // Language selection dialog
    if (showLanguageDialog) {
        LanguageSelectionDialog(
            currentLanguage = detectedLanguage,
            availableLanguages = availableLanguages,
            speechRate = speechRate,
            onSpeechRateChange = onSpeechRateChange,
            onDismiss = { showLanguageDialog = false },
            onSelect = { locale ->
                onLanguageChange(locale)
                showLanguageDialog = false
            }
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Question image if present
        question.questionImage?.let { imageUrl ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                shape = RoundedCornerShape(16.dp)
            ) {
                SubcomposeAsyncImage(
                    model = imageUrl,
                    contentDescription = "Immagine domanda",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop,
                    loading = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = Primary,
                                strokeWidth = 2.dp
                            )
                        }
                    },
                    error = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.ImageNotSupported,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Immagine non disponibile",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    success = {
                        SubcomposeAsyncImageContent()
                    }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Question text card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = question.question,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )

                    if (ttsEnabled) {
                        // Language settings button
                        IconButton(
                            onClick = { showLanguageDialog = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.Language,
                                    contentDescription = "Lingua TTS",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = detectedLanguage.language.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 8.sp
                                )
                            }
                        }

                        // Speak button
                        IconButton(onClick = { onSpeak(question.question) }) {
                            Icon(
                                Icons.Default.VolumeUp,
                                contentDescription = "Leggi",
                                tint = Primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MultipleChoiceAnswers(
    answers: List<MultipleAnswer>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    ttsEnabled: Boolean,
    availableLanguages: Set<Locale>,
    speechRate: Float,
    onSpeechRateChange: (Float) -> Unit,
    onSpeak: (String, Locale) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        answers.forEachIndexed { index, answer ->
            val isSelected = selectedIndex == index
            // Each answer has its own detected language and optional override
            var detectedLanguage by remember(answer.text) { mutableStateOf(detectLanguage(answer.text)) }
            var overrideLanguage by remember { mutableStateOf<Locale?>(null) }
            var showLanguageDialog by remember { mutableStateOf(false) }

            val effectiveLanguage = overrideLanguage ?: detectedLanguage

            // Language dialog for this answer
            if (showLanguageDialog) {
                LanguageSelectionDialog(
                    currentLanguage = effectiveLanguage,
                    availableLanguages = availableLanguages,
                    speechRate = speechRate,
                    onSpeechRateChange = onSpeechRateChange,
                    onDismiss = { showLanguageDialog = false },
                    onSelect = { locale ->
                        overrideLanguage = locale
                        showLanguageDialog = false
                    }
                )
            }

            OutlinedCard(
                onClick = { onSelect(index) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = if (isSelected) Primary.copy(alpha = 0.1f)
                    else MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) Primary else MaterialTheme.colorScheme.outline
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onSelect(index) },
                        colors = RadioButtonDefaults.colors(selectedColor = Primary),
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        answer.text,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )

                    // Always show language and speak buttons (TTS controls)
                    // Language button for this answer
                    IconButton(
                        onClick = { showLanguageDialog = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Language,
                                contentDescription = "Lingua",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = effectiveLanguage.language.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 8.sp
                            )
                        }
                    }

                    // Speak button
                    if (ttsEnabled) {
                        IconButton(
                            onClick = { onSpeak(answer.text, effectiveLanguage) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.VolumeUp,
                                contentDescription = "Leggi",
                                tint = Primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrueFalseAnswers(
    selected: Boolean?,
    onSelect: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // True button
        OutlinedCard(
            onClick = { onSelect(true) },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = if (selected == true) CorrectGreen.copy(alpha = 0.1f)
                else MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(
                width = if (selected == true) 2.dp else 1.dp,
                color = if (selected == true) CorrectGreen else MaterialTheme.colorScheme.outline
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = if (selected == true) CorrectGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "VERO",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (selected == true) CorrectGreen else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // False button
        OutlinedCard(
            onClick = { onSelect(false) },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = if (selected == false) WrongRed.copy(alpha = 0.1f)
                else MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(
                width = if (selected == false) 2.dp else 1.dp,
                color = if (selected == false) WrongRed else MaterialTheme.colorScheme.outline
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = if (selected == false) WrongRed else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "FALSO",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (selected == false) WrongRed else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun WriteAnswerField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    placeholder: String
) {
    val focusManager = LocalFocusManager.current

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("La tua risposta") },
        placeholder = { Text(placeholder) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(
            onDone = {
                focusManager.clearFocus()
                if (value.isNotBlank()) onSubmit()
            }
        )
    )
}

@Composable
private fun OnlineQuizResultFeedback(
    isCorrect: Boolean,
    question: OnlineQuestion,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
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

                val correctAnswerText = when (question.mode) {
                    QuestionMode.MULTIPLE -> {
                        question.answers?.find { it.correct }?.text ?: ""
                    }
                    QuestionMode.TRUE_FALSE -> {
                        if (question.correctAnswer?.asBoolean() == true) "Vero" else "Falso"
                    }
                    else -> question.correctAnswer?.asString() ?: ""
                }

                Text(
                    text = correctAnswerText,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Hai risposto: $userAnswer",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun OnlineQuizCompleteScreen(
    packageName: String,
    totalQuestions: Int,
    correctAnswers: Int,
    onFinish: () -> Unit
) {
    val percentage = if (totalQuestions > 0) (correctAnswers * 100) / totalQuestions else 0

    val message = when {
        percentage >= 90 -> "Eccellente!"
        percentage >= 70 -> "Ottimo lavoro!"
        percentage >= 50 -> "Buon inizio!"
        else -> "Continua a esercitarti!"
    }

    val emoji = when {
        percentage >= 90 -> "ðŸ†"
        percentage >= 70 -> "â­"
        percentage >= 50 -> "ðŸ‘"
        else -> "ðŸ’ª"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = emoji, style = MaterialTheme.typography.displayLarge)

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = packageName,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

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

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onFinish,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary)
        ) {
            Icon(Icons.Default.Home, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Torna alla Home", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * Detects language from text using character ranges and common word patterns.
 * Supports many languages for international use.
 */
private fun detectLanguage(text: String): Locale {
    val cleanText = text.lowercase().trim()

    // Check for non-Latin scripts first (most reliable)
    val charCounts = mutableMapOf<String, Int>()

    for (char in cleanText) {
        val script = when {
            char in '\u0400'..'\u04FF' -> "cyrillic"  // Russian, Ukrainian, Bulgarian, etc.
            char in '\u0370'..'\u03FF' -> "greek"
            char in '\u0600'..'\u06FF' -> "arabic"
            char in '\u0590'..'\u05FF' -> "hebrew"
            char in '\u4E00'..'\u9FFF' -> "chinese"
            char in '\u3040'..'\u309F' || char in '\u30A0'..'\u30FF' -> "japanese"
            char in '\uAC00'..'\uD7AF' -> "korean"
            char in '\u0E00'..'\u0E7F' -> "thai"
            char in '\u0900'..'\u097F' -> "hindi"
            else -> null
        }
        script?.let { charCounts[it] = (charCounts[it] ?: 0) + 1 }
    }

    // If significant non-Latin script detected, return that language
    val totalChars = cleanText.count { it.isLetter() }
    charCounts.maxByOrNull { it.value }?.let { (script, count) ->
        if (count > totalChars * 0.3) {
            return when (script) {
                "cyrillic" -> Locale("ru")
                "greek" -> Locale("el")
                "arabic" -> Locale("ar")
                "hebrew" -> Locale("he")
                "chinese" -> Locale.CHINESE
                "japanese" -> Locale.JAPANESE
                "korean" -> Locale.KOREAN
                "thai" -> Locale("th")
                "hindi" -> Locale("hi")
                else -> Locale.ENGLISH
            }
        }
    }

    // Word-based detection for Latin-script languages
    val words = cleanText.split(Regex("[\\s,;.!?:]+")).filter { it.isNotEmpty() }
    val scores = mutableMapOf<String, Int>()

    // Italian patterns - only unique Italian words (removed ambiguous: a, in, per, con)
    val italianWords = setOf(
        "il", "lo", "la", "le", "gli", "un", "una", "uno",
        "di", "da", "del", "della", "dei", "delle", "dal", "dalla",
        "che", "chi", "cosa", "come", "dove", "quando", "perchÃ©", "quale",
        "Ã¨", "sono", "sei", "siamo", "siete", "essere", "stato", "stata",
        "ha", "ho", "hai", "hanno", "abbiamo", "avete", "avere", "aveva",
        "non", "piÃ¹", "molto", "poco", "tutto", "tutti", "tutte", "ogni",
        "questo", "questa", "questi", "queste", "quello", "quella",
        "tra", "fra", "su", "al", "alla", "allo", "agli", "alle",
        "ma", "perÃ²", "quindi", "perciÃ²", "oppure", "anche", "ancora",
        "nel", "nella", "nei", "nelle", "sul", "sulla", "sui", "sulle",
        "puÃ²", "vuole", "deve", "fa", "va", "sta", "dice", "vede",
        "sempre", "mai", "giÃ ", "ora", "adesso", "ieri", "oggi", "domani",
        "bene", "male", "cosÃ¬", "proprio", "cioÃ¨", "ecco", "dopo", "prima"
    )

    // English patterns - added more common words including "i", "like", "coffee", etc.
    val englishWords = setOf(
        "the", "an", "is", "are", "was", "were", "be", "been", "being",
        "have", "has", "had", "do", "does", "did", "will", "would", "could", "should",
        "what", "which", "who", "when", "where", "why", "how",
        "this", "that", "these", "those", "it", "its",
        "and", "or", "but", "if", "because", "although", "while",
        "for", "with", "about", "from", "into", "through", "of", "to",
        "not", "no", "yes", "very", "much", "many", "more", "most",
        "can", "may", "must", "shall", "might",
        "i", "you", "he", "she", "we", "they", "my", "your", "his", "her", "our", "their",
        "like", "want", "need", "know", "think", "see", "get", "make", "go", "come",
        "good", "bad", "new", "old", "big", "small", "long", "short",
        "just", "only", "also", "now", "then", "here", "there",
        "some", "any", "all", "every", "each", "both", "few",
        "coffee", "water", "food", "book", "car", "house", "work", "time", "day", "year"
    )

    // French patterns
    val frenchWords = setOf(
        "le", "la", "les", "un", "une", "des", "du", "de", "au", "aux",
        "est", "sont", "Ã©tait", "Ãªtre", "Ã©tÃ©", "suis", "es", "sommes",
        "je", "tu", "il", "elle", "nous", "vous", "ils", "elles", "on",
        "ce", "cette", "ces", "qui", "que", "quoi", "oÃ¹", "quand", "comment",
        "ne", "pas", "plus", "jamais", "rien", "personne",
        "avec", "pour", "dans", "sur", "sous", "entre", "vers",
        "et", "ou", "mais", "donc", "car", "ni", "parce",
        "trÃ¨s", "bien", "mal", "peu", "beaucoup", "trop", "aussi",
        "avoir", "fait", "peut", "veut", "doit"
    )

    // German patterns
    val germanWords = setOf(
        "der", "die", "das", "den", "dem", "des", "ein", "eine", "einer", "eines",
        "ist", "sind", "war", "waren", "sein", "gewesen", "bin", "bist",
        "ich", "du", "er", "sie", "es", "wir", "ihr",
        "was", "wer", "wie", "wo", "wann", "warum", "welche", "welcher",
        "nicht", "kein", "keine", "keiner", "nie", "niemals",
        "mit", "fÃ¼r", "auf", "in", "an", "bei", "nach", "von", "zu", "aus",
        "und", "oder", "aber", "denn", "weil", "wenn", "ob", "dass",
        "sehr", "viel", "mehr", "wenig", "gut", "schlecht",
        "haben", "hat", "hatte", "kann", "kÃ¶nnte", "muss", "will", "soll"
    )

    // Spanish patterns
    val spanishWords = setOf(
        "el", "la", "los", "las", "un", "una", "unos", "unas",
        "es", "son", "era", "eran", "ser", "sido", "soy", "eres", "somos",
        "yo", "tÃº", "Ã©l", "ella", "nosotros", "vosotros", "ellos", "ellas",
        "quÃ©", "quiÃ©n", "cÃ³mo", "dÃ³nde", "cuÃ¡ndo", "por quÃ©", "cuÃ¡l",
        "no", "nunca", "nada", "nadie", "ningÃºn", "ninguna",
        "con", "para", "en", "de", "a", "por", "sin", "sobre", "entre",
        "y", "o", "pero", "porque", "aunque", "si", "que",
        "muy", "mucho", "poco", "mÃ¡s", "menos", "bien", "mal",
        "tiene", "tengo", "puede", "puedo", "quiere", "debe", "hace", "va"
    )

    // Portuguese patterns
    val portugueseWords = setOf(
        "o", "a", "os", "as", "um", "uma", "uns", "umas",
        "Ã©", "sÃ£o", "era", "eram", "ser", "sido", "sou", "Ã©s", "somos",
        "eu", "tu", "ele", "ela", "nÃ³s", "vÃ³s", "eles", "elas", "vocÃª",
        "que", "quem", "como", "onde", "quando", "porquÃª", "qual",
        "nÃ£o", "nunca", "nada", "ninguÃ©m", "nenhum", "nenhuma",
        "com", "para", "em", "de", "a", "por", "sem", "sobre", "entre",
        "e", "ou", "mas", "porque", "embora", "se",
        "muito", "pouco", "mais", "menos", "bem", "mal",
        "tem", "tenho", "pode", "posso", "quer", "deve", "faz", "vai"
    )

    // Dutch patterns
    val dutchWords = setOf(
        "de", "het", "een", "van", "in", "op", "met", "voor", "aan",
        "is", "zijn", "was", "waren", "ben", "bent", "wordt",
        "ik", "jij", "je", "hij", "zij", "ze", "wij", "we", "jullie",
        "wat", "wie", "hoe", "waar", "wanneer", "waarom", "welke",
        "niet", "geen", "nooit", "niets", "niemand",
        "en", "of", "maar", "omdat", "als", "dat", "om",
        "zeer", "veel", "weinig", "meer", "minder", "goed", "slecht",
        "hebben", "heeft", "kan", "kunnen", "moet", "wil", "zal"
    )

    // Count word matches with weights - check ALL languages for each word
    for (word in words) {
        if (word in italianWords) scores["it"] = (scores["it"] ?: 0) + 3
        if (word in englishWords) scores["en"] = (scores["en"] ?: 0) + 3
        if (word in frenchWords) scores["fr"] = (scores["fr"] ?: 0) + 2
        if (word in germanWords) scores["de"] = (scores["de"] ?: 0) + 2
        if (word in spanishWords) scores["es"] = (scores["es"] ?: 0) + 2
        if (word in portugueseWords) scores["pt"] = (scores["pt"] ?: 0) + 2
        if (word in dutchWords) scores["nl"] = (scores["nl"] ?: 0) + 2
    }

    // Check for language-specific characters
    if (cleanText.contains(Regex("[Ã Ã¨Ã©Ã¬Ã²Ã¹]"))) scores["it"] = (scores["it"] ?: 0) + 2
    if (cleanText.contains(Regex("[Ã¤Ã¶Ã¼ÃŸ]"))) scores["de"] = (scores["de"] ?: 0) + 3
    if (cleanText.contains(Regex("[Ã Ã¢Ã§Ã©Ã¨ÃªÃ«Ã®Ã¯Ã´Ã»Ã¹]"))) scores["fr"] = (scores["fr"] ?: 0) + 2
    if (cleanText.contains(Regex("[Ã¡Ã©Ã­Ã³ÃºÃ¼Ã±Â¿Â¡]"))) scores["es"] = (scores["es"] ?: 0) + 2
    if (cleanText.contains(Regex("[Ã£ÃµÃ¡Ã©Ã­Ã³ÃºÃ§]"))) scores["pt"] = (scores["pt"] ?: 0) + 2
    if (cleanText.contains(Regex("[Ã¥Ã¤Ã¶]"))) scores["sv"] = (scores["sv"] ?: 0) + 3
    if (cleanText.contains(Regex("[Ã¦Ã¸Ã¥]"))) scores["no"] = (scores["no"] ?: 0) + 3

    // Return language with highest score, default to English
    val winner = scores.maxByOrNull { it.value }
    return when (winner?.key) {
        "it" -> Locale.ITALIAN
        "en" -> Locale.ENGLISH
        "fr" -> Locale.FRENCH
        "de" -> Locale.GERMAN
        "es" -> Locale("es")
        "pt" -> Locale("pt")
        "nl" -> Locale("nl")
        "sv" -> Locale("sv")
        "no" -> Locale("no")
        else -> Locale.ENGLISH
    }
}

@Composable
private fun LanguageSelectionDialog(
    currentLanguage: Locale,
    availableLanguages: Set<Locale>,
    speechRate: Float,
    onSpeechRateChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    onSelect: (Locale) -> Unit
) {
    // Common languages to show (subset of available)
    val commonLanguages = listOf(
        Locale.ENGLISH to "English",
        Locale.ITALIAN to "Italiano",
        Locale.FRENCH to "FranÃ§ais",
        Locale.GERMAN to "Deutsch",
        Locale("es") to "EspaÃ±ol",
        Locale("pt") to "PortuguÃªs",
        Locale("ru") to "Ð ÑƒÑÑÐºÐ¸Ð¹",
        Locale.CHINESE to "ä¸­æ–‡",
        Locale.JAPANESE to "æ—¥æœ¬èªž",
        Locale.KOREAN to "í•œêµ­ì–´",
        Locale("nl") to "Nederlands",
        Locale("pl") to "Polski",
        Locale("cs") to "ÄŒeÅ¡tina",
        Locale("sv") to "Svenska",
        Locale("no") to "Norsk",
        Locale("da") to "Dansk",
        Locale("tr") to "TÃ¼rkÃ§e",
        Locale("ro") to "RomÃ¢nÄƒ",
        Locale("hu") to "Magyar",
        Locale("el") to "Î•Î»Î»Î·Î½Î¹ÎºÎ¬",
        Locale("ar") to "Ø§Ù„Ø¹Ø±Ø¨ÙŠØ©"
    )

    // Filter to only show languages available in TTS, or show all if empty
    val languagesToShow = if (availableLanguages.isNotEmpty()) {
        commonLanguages.filter { (locale, _) ->
            availableLanguages.any { it.language == locale.language }
        }
    } else {
        commonLanguages
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Lingua Text-to-Speech")
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Lingua rilevata: ${currentLanguage.displayLanguage}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column {
                // Speech rate slider
                Text(
                    "VelocitÃ : ${String.format("%.1f", speechRate)}x",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.SlowMotionVideo,
                        contentDescription = "Lento",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = speechRate,
                        onValueChange = onSpeechRateChange,
                        valueRange = 0.5f..2.0f,
                        steps = 5,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Primary,
                            activeTrackColor = Primary
                        )
                    )
                    Icon(
                        Icons.Default.Speed,
                        contentDescription = "Veloce",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Seleziona lingua:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp)
                ) {
                    items(languagesToShow) { (locale, name) ->
                        val isSelected = locale.language == currentLanguage.language

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(locale) }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onSelect(locale) },
                                colors = RadioButtonDefaults.colors(selectedColor = Primary)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    locale.language.uppercase(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
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

