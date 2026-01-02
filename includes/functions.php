<?php
/**
 * GenMemo Helper Functions
 */

if (!defined('GENMEMO')) {
    die('Direct access not allowed');
}

/**
 * Sanitize output for HTML
 */
function e(string $str): string {
    return htmlspecialchars($str, ENT_QUOTES, 'UTF-8');
}

/**
 * Generate a random string
 */
function randomString(int $length = 32): string {
    return bin2hex(random_bytes($length / 2));
}

/**
 * Format file size
 */
function formatFileSize(int $bytes): string {
    $units = ['B', 'KB', 'MB', 'GB'];
    $index = 0;
    while ($bytes >= 1024 && $index < count($units) - 1) {
        $bytes /= 1024;
        $index++;
    }
    return round($bytes, 2) . ' ' . $units[$index];
}

/**
 * Get relative time
 */
function timeAgo(string $datetime): string {
    $now = new DateTime();
    $ago = new DateTime($datetime);
    $diff = $now->diff($ago);

    if ($diff->y > 0) return $diff->y . ' year' . ($diff->y > 1 ? 's' : '') . ' ago';
    if ($diff->m > 0) return $diff->m . ' month' . ($diff->m > 1 ? 's' : '') . ' ago';
    if ($diff->d > 0) return $diff->d . ' day' . ($diff->d > 1 ? 's' : '') . ' ago';
    if ($diff->h > 0) return $diff->h . ' hour' . ($diff->h > 1 ? 's' : '') . ' ago';
    if ($diff->i > 0) return $diff->i . ' minute' . ($diff->i > 1 ? 's' : '') . ' ago';
    return 'Just now';
}

/**
 * Generate package JSON structure
 */
function createPackageStructure(array $data): array {
    return [
        'version' => '1.0',
        'id' => randomString(16),
        'name' => $data['name'] ?? 'Untitled Package',
        'description' => $data['description'] ?? '',
        'author' => $data['author'] ?? 'Anonymous',
        'created_at' => date('c'),
        'settings' => [
            'question_types' => $data['question_types'] ?? ['text'],
            'answer_types' => $data['answer_types'] ?? ['text'],
            'total_questions' => $data['total_questions'] ?? 0
        ],
        'questions' => $data['questions'] ?? []
    ];
}

/**
 * Validate package JSON structure
 */
function validatePackageJson(string $json): array {
    $data = json_decode($json, true);

    if (json_last_error() !== JSON_ERROR_NONE) {
        return ['valid' => false, 'error' => 'JSON non valido: ' . json_last_error_msg()];
    }

    if (!isset($data['questions']) || !is_array($data['questions'])) {
        return ['valid' => false, 'error' => 'Manca l\'array "questions"'];
    }

    foreach ($data['questions'] as $index => $q) {
        $qNum = $index + 1;

        if (!isset($q['question'])) {
            return ['valid' => false, 'error' => "Domanda #$qNum: manca il campo 'question'"];
        }

        $mode = $q['mode'] ?? 'multiple';

        // Validate based on mode
        if ($mode === 'multiple') {
            // Multiple choice needs answers array
            if (!isset($q['answers']) || !is_array($q['answers'])) {
                return ['valid' => false, 'error' => "Domanda #$qNum (multipla): manca l'array 'answers'"];
            }
        } else {
            // Other modes need correct_answer
            if (!isset($q['correct_answer'])) {
                return ['valid' => false, 'error' => "Domanda #$qNum ($mode): manca 'correct_answer'"];
            }
        }
    }

    return ['valid' => true, 'data' => $data];
}

/**
 * Send JSON response
 */
function jsonResponse(array $data, int $code = 200): void {
    http_response_code($code);
    header('Content-Type: application/json');
    echo json_encode($data);
    exit;
}

/**
 * Get flash message
 */
function flash(string $key, string $message = null) {
    if ($message !== null) {
        $_SESSION['flash'][$key] = $message;
    } else {
        $msg = $_SESSION['flash'][$key] ?? null;
        unset($_SESSION['flash'][$key]);
        return $msg;
    }
}

/**
 * Redirect with message
 */
function redirect(string $url, string $message = null, string $type = 'info'): void {
    if ($message) {
        flash($type, $message);
    }
    header("Location: $url");
    exit;
}

/**
 * Generate AI prompt for package creation
 */
function generateAiPrompt(array $config): string {
    $numQuestions = (int) $config['num_questions'];
    $topic = e($config['topic']);
    $answerModes = $config['answer_modes'] ?? ['multiple'];

    $hasQuestionTts = in_array('tts', $config['question_types']);
    $hasAnswerTts = in_array('tts', $config['answer_types']);
    $hasQuestionImage = in_array('image', $config['question_types']);
    $hasAnswerImage = in_array('image', $config['answer_types']);

    // Modalita di risposta
    $hasMultiple = in_array('multiple', $answerModes);
    $hasTrueFalse = in_array('truefalse', $answerModes);
    $hasWriteExact = in_array('write_exact', $answerModes);
    $hasWriteWord = in_array('write_word', $answerModes);
    $hasWritePartial = in_array('write_partial', $answerModes);

    $prompt = "Genera un pacchetto di $numQuestions domande sul tema: \"$topic\".\n\n";

    // Descrivi le modalita disponibili
    $modesDesc = [];
    if ($hasMultiple) $modesDesc[] = "risposta multipla (4 opzioni)";
    if ($hasTrueFalse) $modesDesc[] = "vero/falso";
    if ($hasWriteExact) $modesDesc[] = "scrivi risposta esatta";
    if ($hasWriteWord) $modesDesc[] = "scrivi una parola";
    if ($hasWritePartial) $modesDesc[] = "scrivi risposta (controllo parziale)";

    $prompt .= "MODALITA DI RISPOSTA DISPONIBILI: " . implode(', ', $modesDesc) . "\n";
    $prompt .= "Scegli tu la modalita piu appropriata per ogni domanda, variando tra quelle disponibili.\n\n";

    $prompt .= "FORMATO OUTPUT RICHIESTO (JSON valido):\n";
    $prompt .= "```json\n";
    $prompt .= "{\n";

    // Settings TTS a livello pacchetto
    if ($hasQuestionTts || $hasAnswerTts) {
        $prompt .= "  \"tts\": {\n";
        $prompt .= "    \"enabled\": true\n";
        $prompt .= "  },\n";
    }

    $prompt .= "  \"questions\": [\n";

    // Esempio per risposta multipla
    if ($hasMultiple) {
        $prompt .= "    // Esempio RISPOSTA MULTIPLA:\n";
        $prompt .= "    {\n";
        $prompt .= "      \"question\": \"Testo della domanda\",\n";
        $prompt .= "      \"mode\": \"multiple\",\n";
        if ($hasQuestionTts) $prompt .= "      \"question_tts\": true,\n";
        if ($hasQuestionImage) $prompt .= "      \"question_image\": \"[INSERIRE_IMMAGINE_Q1]\",\n";
        $prompt .= "      \"answers\": [\n";
        $prompt .= "        {\"text\": \"Risposta corretta\"" . ($hasAnswerTts ? ", \"tts\": true" : "") . ", \"correct\": true},\n";
        $prompt .= "        {\"text\": \"Risposta errata 1\"" . ($hasAnswerTts ? ", \"tts\": true" : "") . ", \"correct\": false},\n";
        $prompt .= "        {\"text\": \"Risposta errata 2\"" . ($hasAnswerTts ? ", \"tts\": true" : "") . ", \"correct\": false},\n";
        $prompt .= "        {\"text\": \"Risposta errata 3\"" . ($hasAnswerTts ? ", \"tts\": true" : "") . ", \"correct\": false}\n";
        $prompt .= "      ]\n";
        $prompt .= "    },\n";
    }

    // Esempio per vero/falso
    if ($hasTrueFalse) {
        $prompt .= "    // Esempio VERO/FALSO:\n";
        $prompt .= "    {\n";
        $prompt .= "      \"question\": \"Affermazione da valutare\",\n";
        $prompt .= "      \"mode\": \"truefalse\",\n";
        if ($hasQuestionTts) $prompt .= "      \"question_tts\": true,\n";
        $prompt .= "      \"correct_answer\": true  // oppure false\n";
        $prompt .= "    },\n";
    }

    // Esempio per scrivi risposta esatta
    if ($hasWriteExact) {
        $prompt .= "    // Esempio SCRIVI RISPOSTA ESATTA:\n";
        $prompt .= "    {\n";
        $prompt .= "      \"question\": \"Domanda che richiede risposta precisa\",\n";
        $prompt .= "      \"mode\": \"write_exact\",\n";
        if ($hasQuestionTts) $prompt .= "      \"question_tts\": true,\n";
        $prompt .= "      \"correct_answer\": \"risposta esatta\"  // case-insensitive\n";
        $prompt .= "    },\n";
    }

    // Esempio per scrivi una parola
    if ($hasWriteWord) {
        $prompt .= "    // Esempio SCRIVI UNA PAROLA:\n";
        $prompt .= "    {\n";
        $prompt .= "      \"question\": \"Come si dice 'cane' in inglese?\",\n";
        $prompt .= "      \"mode\": \"write_word\",\n";
        if ($hasQuestionTts) $prompt .= "      \"question_tts\": true,\n";
        $prompt .= "      \"correct_answer\": \"dog\"  // una sola parola\n";
        $prompt .= "    },\n";
    }

    // Esempio per scrivi risposta parziale
    if ($hasWritePartial) {
        $prompt .= "    // Esempio SCRIVI RISPOSTA (controllo parziale):\n";
        $prompt .= "    {\n";
        $prompt .= "      \"question\": \"Chi ha scoperto l'America?\",\n";
        $prompt .= "      \"mode\": \"write_partial\",\n";
        if ($hasQuestionTts) $prompt .= "      \"question_tts\": true,\n";
        $prompt .= "      \"correct_answer\": \"Colombo\",  // parola chiave da cercare\n";
        $prompt .= "      \"accept_also\": [\"Cristoforo\", \"1492\"]  // opzionale: altre parole accettate\n";
        $prompt .= "    },\n";
    }

    $prompt .= "  ]\n";
    $prompt .= "}\n";
    $prompt .= "```\n\n";

    $prompt .= "ISTRUZIONI:\n";
    $prompt .= "1. Genera esattamente $numQuestions domande\n";
    $prompt .= "2. Varia le modalita di risposta tra quelle disponibili\n";
    $prompt .= "3. Le domande devono essere variate e coprire diversi aspetti del tema\n";
    $prompt .= "4. Usa un linguaggio chiaro e appropriato\n";
    $prompt .= "5. Il campo \"mode\" e OBBLIGATORIO per ogni domanda\n";

    $instrNum = 6;
    if ($hasQuestionTts || $hasAnswerTts) {
        $prompt .= "$instrNum. I campi \"tts\": true indicano che il testo verra letto ad alta voce dall'app\n";
        $instrNum++;
    }
    if ($hasQuestionImage || $hasAnswerImage) {
        $prompt .= "$instrNum. I placeholder [INSERIRE_IMMAGINE_X] verranno sostituiti con le immagini dall'utente\n";
        $instrNum++;
    }

    $prompt .= "\nRispondi SOLO con il JSON, senza commenti o spiegazioni.";

    return $prompt;
}
