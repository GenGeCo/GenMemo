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
        return ['valid' => false, 'error' => 'Invalid JSON format'];
    }

    if (!isset($data['questions']) || !is_array($data['questions'])) {
        return ['valid' => false, 'error' => 'Missing or invalid questions array'];
    }

    foreach ($data['questions'] as $index => $q) {
        if (!isset($q['question'])) {
            return ['valid' => false, 'error' => "Question #" . ($index + 1) . " is missing 'question' field"];
        }
        if (!isset($q['answers']) || !is_array($q['answers'])) {
            return ['valid' => false, 'error' => "Question #" . ($index + 1) . " is missing 'answers' array"];
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
    $questionTypes = implode(', ', $config['question_types']);
    $answerTypes = implode(', ', $config['answer_types']);
    $numQuestions = (int) $config['num_questions'];
    $topic = e($config['topic']);
    $language = $config['language'] ?? 'italiano';

    $prompt = "Genera un pacchetto di $numQuestions domande sul tema: \"$topic\" in lingua $language.\n\n";
    $prompt .= "FORMATO OUTPUT RICHIESTO (JSON valido):\n";
    $prompt .= "```json\n";
    $prompt .= "{\n";
    $prompt .= "  \"questions\": [\n";
    $prompt .= "    {\n";
    $prompt .= "      \"question\": \"Testo della domanda\",\n";

    if (in_array('image', $config['question_types'])) {
        $prompt .= "      \"question_image\": \"[INSERIRE_IMMAGINE_1]\",\n";
    }
    if (in_array('audio', $config['question_types'])) {
        $prompt .= "      \"question_audio\": \"[INSERIRE_AUDIO_1]\",\n";
    }

    $prompt .= "      \"answers\": [\n";
    $prompt .= "        {\n";
    $prompt .= "          \"text\": \"Risposta corretta\",\n";

    if (in_array('image', $config['answer_types'])) {
        $prompt .= "          \"image\": \"[INSERIRE_IMMAGINE_RISPOSTA]\",\n";
    }
    if (in_array('audio', $config['answer_types'])) {
        $prompt .= "          \"audio\": \"[INSERIRE_AUDIO_RISPOSTA]\",\n";
    }

    $prompt .= "          \"correct\": true\n";
    $prompt .= "        },\n";
    $prompt .= "        {\n";
    $prompt .= "          \"text\": \"Risposta errata 1\",\n";
    $prompt .= "          \"correct\": false\n";
    $prompt .= "        },\n";
    $prompt .= "        {\n";
    $prompt .= "          \"text\": \"Risposta errata 2\",\n";
    $prompt .= "          \"correct\": false\n";
    $prompt .= "        },\n";
    $prompt .= "        {\n";
    $prompt .= "          \"text\": \"Risposta errata 3\",\n";
    $prompt .= "          \"correct\": false\n";
    $prompt .= "        }\n";
    $prompt .= "      ]\n";
    $prompt .= "    }\n";
    $prompt .= "  ]\n";
    $prompt .= "}\n";
    $prompt .= "```\n\n";

    $prompt .= "ISTRUZIONI:\n";
    $prompt .= "1. Genera esattamente $numQuestions domande\n";
    $prompt .= "2. Ogni domanda deve avere 4 risposte (1 corretta, 3 errate)\n";
    $prompt .= "3. Le domande devono essere variate e coprire diversi aspetti del tema\n";
    $prompt .= "4. Usa un linguaggio chiaro e appropriato\n";

    if (in_array('image', $config['question_types']) || in_array('image', $config['answer_types'])) {
        $prompt .= "5. I placeholder [INSERIRE_IMMAGINE_X] verranno sostituiti con le immagini dall'utente\n";
    }
    if (in_array('audio', $config['question_types']) || in_array('audio', $config['answer_types'])) {
        $prompt .= "6. I placeholder [INSERIRE_AUDIO_X] verranno sostituiti con gli audio dall'utente\n";
    }

    $prompt .= "\nRispondi SOLO con il JSON, senza commenti o spiegazioni.";

    return $prompt;
}
