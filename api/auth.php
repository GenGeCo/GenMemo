<?php
/**
 * API: Authentication for Android App
 *
 * POST /api/auth.php
 * Actions: login, register, verify, save-progress, get-progress
 */

require_once __DIR__ . '/../includes/init.php';

header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: POST, GET, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, Authorization');

// Handle preflight
if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit;
}

$action = $_POST['action'] ?? $_GET['action'] ?? '';

switch ($action) {
    case 'login':
        handleLogin();
        break;
    case 'register':
        handleRegister();
        break;
    case 'verify':
        handleVerifyToken();
        break;
    case 'save-progress':
        handleSaveProgress();
        break;
    case 'get-progress':
        handleGetProgress();
        break;
    case 'sync-question-progress':
        handleSyncQuestionProgress();
        break;
    case 'get-question-progress':
        handleGetQuestionProgress();
        break;
    case 'get-all-question-progress':
        handleGetAllQuestionProgress();
        break;
    case 'start-session':
        handleStartSession();
        break;
    case 'end-session':
        handleEndSession();
        break;
    case 'update-session':
        handleUpdateSession();
        break;
    case 'get-study-stats':
        handleGetStudyStats();
        break;
    default:
        jsonResponse(['error' => 'Invalid action'], 400);
}

/**
 * Login with email/password
 * Returns auth token for app
 */
function handleLogin() {
    $email = trim($_POST['email'] ?? '');
    $password = $_POST['password'] ?? '';

    if (empty($email) || empty($password)) {
        jsonResponse(['error' => 'Email e password richiesti'], 400);
    }

    $user = db()->fetch(
        "SELECT id, username, email, password_hash, is_active FROM users WHERE email = ?",
        [$email]
    );

    if (!$user || !password_verify($password, $user['password_hash'])) {
        jsonResponse(['error' => 'Credenziali non valide'], 401);
    }

    if (!$user['is_active']) {
        jsonResponse(['error' => 'Account disattivato.'], 403);
    }

    // Generate app token
    $token = bin2hex(random_bytes(32));
    $expiresAt = date('Y-m-d H:i:s', strtotime('+30 days'));

    // Save token
    db()->insert('app_tokens', [
        'user_id' => $user['id'],
        'token' => hash('sha256', $token),
        'device_info' => substr($_SERVER['HTTP_USER_AGENT'] ?? 'Android App', 0, 255),
        'expires_at' => $expiresAt,
        'created_at' => date('Y-m-d H:i:s')
    ]);

    jsonResponse([
        'success' => true,
        'token' => $token,
        'user' => [
            'id' => $user['id'],
            'username' => $user['username'],
            'email' => $user['email']
        ],
        'expires_at' => $expiresAt
    ]);
}

/**
 * Register new user from app
 */
function handleRegister() {
    $username = trim($_POST['username'] ?? '');
    $email = trim($_POST['email'] ?? '');
    $password = $_POST['password'] ?? '';

    // Validation
    if (empty($username) || empty($email) || empty($password)) {
        jsonResponse(['error' => 'Tutti i campi sono obbligatori'], 400);
    }

    if (strlen($username) < 3) {
        jsonResponse(['error' => 'Username deve avere almeno 3 caratteri'], 400);
    }

    if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
        jsonResponse(['error' => 'Email non valida'], 400);
    }

    if (strlen($password) < 6) {
        jsonResponse(['error' => 'Password deve avere almeno 6 caratteri'], 400);
    }

    // Check if exists
    $existing = db()->fetch(
        "SELECT id FROM users WHERE email = ? OR username = ?",
        [$email, $username]
    );

    if ($existing) {
        jsonResponse(['error' => 'Email o username già in uso'], 409);
    }

    // Create user (auto-active for app registration)
    $userId = db()->insert('users', [
        'username' => $username,
        'email' => $email,
        'password_hash' => password_hash($password, PASSWORD_BCRYPT, ['cost' => HASH_COST]),
        'is_active' => 1,
        'created_at' => date('Y-m-d H:i:s')
    ]);

    // Generate token
    $token = bin2hex(random_bytes(32));
    $expiresAt = date('Y-m-d H:i:s', strtotime('+30 days'));

    db()->insert('app_tokens', [
        'user_id' => $userId,
        'token' => hash('sha256', $token),
        'device_info' => substr($_SERVER['HTTP_USER_AGENT'] ?? 'Android App', 0, 255),
        'expires_at' => $expiresAt,
        'created_at' => date('Y-m-d H:i:s')
    ]);

    jsonResponse([
        'success' => true,
        'token' => $token,
        'user' => [
            'id' => $userId,
            'username' => $username,
            'email' => $email
        ],
        'expires_at' => $expiresAt
    ]);
}

/**
 * Verify if token is still valid
 */
function handleVerifyToken() {
    $token = $_POST['token'] ?? $_GET['token'] ?? '';

    if (empty($token)) {
        jsonResponse(['error' => 'Token richiesto'], 400);
    }

    $tokenHash = hash('sha256', $token);

    $appToken = db()->fetch(
        "SELECT at.*, u.username, u.email
         FROM app_tokens at
         JOIN users u ON at.user_id = u.id
         WHERE at.token = ? AND at.expires_at > NOW()",
        [$tokenHash]
    );

    if (!$appToken) {
        jsonResponse(['valid' => false, 'error' => 'Token non valido o scaduto'], 401);
    }

    jsonResponse([
        'valid' => true,
        'user' => [
            'id' => $appToken['user_id'],
            'username' => $appToken['username'],
            'email' => $appToken['email']
        ],
        'expires_at' => $appToken['expires_at']
    ]);
}

/**
 * Save quiz progress for a package
 */
function handleSaveProgress() {
    $user = authenticateRequest();

    $packageUuid = $_POST['package_uuid'] ?? '';
    $score = (int)($_POST['score'] ?? 0);
    $totalQuestions = (int)($_POST['total_questions'] ?? 0);
    $completedAt = $_POST['completed_at'] ?? date('Y-m-d H:i:s');
    $timeSpent = (int)($_POST['time_spent'] ?? 0); // seconds

    if (empty($packageUuid)) {
        jsonResponse(['error' => 'Package UUID richiesto'], 400);
    }

    // Get package
    $package = db()->fetch(
        "SELECT id FROM packages WHERE uuid = ?",
        [$packageUuid]
    );

    if (!$package) {
        jsonResponse(['error' => 'Pacchetto non trovato'], 404);
    }

    // Check if progress exists
    $existing = db()->fetch(
        "SELECT id, best_score, attempts FROM user_progress WHERE user_id = ? AND package_id = ?",
        [$user['id'], $package['id']]
    );

    if ($existing) {
        // Update existing
        $bestScore = max($existing['best_score'], $score);
        db()->query(
            "UPDATE user_progress SET
                last_score = ?,
                best_score = ?,
                attempts = attempts + 1,
                total_time_spent = total_time_spent + ?,
                last_played_at = ?,
                updated_at = ?
            WHERE id = ?",
            [$score, $bestScore, $timeSpent, $completedAt, date('Y-m-d H:i:s'), $existing['id']]
        );
    } else {
        // Create new
        db()->insert('user_progress', [
            'user_id' => $user['id'],
            'package_id' => $package['id'],
            'last_score' => $score,
            'best_score' => $score,
            'total_questions' => $totalQuestions,
            'attempts' => 1,
            'total_time_spent' => $timeSpent,
            'last_played_at' => $completedAt,
            'created_at' => date('Y-m-d H:i:s'),
            'updated_at' => date('Y-m-d H:i:s')
        ]);
    }

    jsonResponse([
        'success' => true,
        'message' => 'Progressi salvati'
    ]);
}

/**
 * Get user's progress for all packages or specific package
 */
function handleGetProgress() {
    $user = authenticateRequest();

    $packageUuid = $_GET['package_uuid'] ?? '';

    if (!empty($packageUuid)) {
        // Get progress for specific package
        $progress = db()->fetch(
            "SELECT up.*, p.uuid, p.name as package_name
             FROM user_progress up
             JOIN packages p ON up.package_id = p.id
             WHERE up.user_id = ? AND p.uuid = ?",
            [$user['id'], $packageUuid]
        );

        if (!$progress) {
            jsonResponse([
                'found' => false,
                'progress' => null
            ]);
        }

        jsonResponse([
            'found' => true,
            'progress' => [
                'package_uuid' => $progress['uuid'],
                'package_name' => $progress['package_name'],
                'last_score' => $progress['last_score'],
                'best_score' => $progress['best_score'],
                'total_questions' => $progress['total_questions'],
                'attempts' => $progress['attempts'],
                'total_time_spent' => $progress['total_time_spent'],
                'last_played_at' => $progress['last_played_at']
            ]
        ]);
    } else {
        // Get all progress
        $progressList = db()->fetchAll(
            "SELECT up.*, p.uuid, p.name as package_name
             FROM user_progress up
             JOIN packages p ON up.package_id = p.id
             WHERE up.user_id = ?
             ORDER BY up.last_played_at DESC",
            [$user['id']]
        );

        $result = array_map(function($p) {
            return [
                'package_uuid' => $p['uuid'],
                'package_name' => $p['package_name'],
                'last_score' => $p['last_score'],
                'best_score' => $p['best_score'],
                'total_questions' => $p['total_questions'],
                'attempts' => $p['attempts'],
                'total_time_spent' => $p['total_time_spent'],
                'last_played_at' => $p['last_played_at']
            ];
        }, $progressList);

        jsonResponse([
            'count' => count($result),
            'progress' => $result
        ]);
    }
}

/**
 * Authenticate request using Bearer token
 */
function authenticateRequest(): array {
    $token = '';

    // Check Authorization header
    $authHeader = $_SERVER['HTTP_AUTHORIZATION'] ?? '';
    if (preg_match('/Bearer\s+(.+)$/i', $authHeader, $matches)) {
        $token = $matches[1];
    }

    // Fallback to POST/GET
    if (empty($token)) {
        $token = $_POST['token'] ?? $_GET['token'] ?? '';
    }

    if (empty($token)) {
        jsonResponse(['error' => 'Autenticazione richiesta'], 401);
    }

    $tokenHash = hash('sha256', $token);

    $appToken = db()->fetch(
        "SELECT at.user_id, u.username, u.email
         FROM app_tokens at
         JOIN users u ON at.user_id = u.id
         WHERE at.token = ? AND at.expires_at > NOW()",
        [$tokenHash]
    );

    if (!$appToken) {
        jsonResponse(['error' => 'Token non valido o scaduto'], 401);
    }

    return [
        'id' => $appToken['user_id'],
        'username' => $appToken['username'],
        'email' => $appToken['email']
    ];
}

/**
 * Sync question progress from app (spaced repetition data)
 */
function handleSyncQuestionProgress() {
    $user = authenticateRequest();

    $packageUuid = $_POST['package_uuid'] ?? '';
    $progressJson = $_POST['progress'] ?? '';

    if (empty($packageUuid)) {
        jsonResponse(['error' => 'Package UUID richiesto'], 400);
    }

    if (empty($progressJson)) {
        jsonResponse(['error' => 'Progress data richiesto'], 400);
    }

    // Get package
    $package = db()->fetch(
        "SELECT id FROM packages WHERE uuid = ?",
        [$packageUuid]
    );

    if (!$package) {
        jsonResponse(['error' => 'Pacchetto non trovato'], 404);
    }

    // Parse progress JSON
    $progressData = json_decode($progressJson, true);
    if (!is_array($progressData)) {
        jsonResponse(['error' => 'Progress data non valido'], 400);
    }

    $syncedCount = 0;

    foreach ($progressData as $item) {
        if (!isset($item['question_index'])) {
            continue;
        }

        $questionIndex = (int)$item['question_index'];
        $score = (int)($item['score'] ?? 0);
        $intervalDays = (float)($item['interval_days'] ?? 1);
        $nextReviewDate = $item['next_review_date'] ?? date('Y-m-d H:i:s');
        $streak = (int)($item['streak'] ?? 0);
        $correctDays = (int)($item['correct_days'] ?? 0);
        $lastCorrectDate = $item['last_correct_date'] ?? null;

        // Check if exists
        $existing = db()->fetch(
            "SELECT id, updated_at FROM user_question_progress
             WHERE user_id = ? AND package_id = ? AND question_index = ?",
            [$user['id'], $package['id'], $questionIndex]
        );

        if ($existing) {
            // Update existing - server wins if same timestamp, otherwise most recent wins
            db()->update('user_question_progress', [
                'score' => $score,
                'interval_days' => $intervalDays,
                'next_review_date' => $nextReviewDate,
                'streak' => $streak,
                'correct_days' => $correctDays,
                'last_correct_date' => $lastCorrectDate,
                'updated_at' => date('Y-m-d H:i:s')
            ], 'id = ?', [$existing['id']]);
        } else {
            // Insert new
            db()->insert('user_question_progress', [
                'user_id' => $user['id'],
                'package_id' => $package['id'],
                'question_index' => $questionIndex,
                'score' => $score,
                'interval_days' => $intervalDays,
                'next_review_date' => $nextReviewDate,
                'streak' => $streak,
                'correct_days' => $correctDays,
                'last_correct_date' => $lastCorrectDate,
                'created_at' => date('Y-m-d H:i:s'),
                'updated_at' => date('Y-m-d H:i:s')
            ]);
        }

        $syncedCount++;
    }

    jsonResponse([
        'success' => true,
        'synced_count' => $syncedCount
    ]);
}

/**
 * Get question progress for a specific package
 */
function handleGetQuestionProgress() {
    $user = authenticateRequest();

    $packageUuid = $_GET['package_uuid'] ?? '';

    if (empty($packageUuid)) {
        jsonResponse(['error' => 'Package UUID richiesto'], 400);
    }

    // Get package
    $package = db()->fetch(
        "SELECT id FROM packages WHERE uuid = ?",
        [$packageUuid]
    );

    if (!$package) {
        jsonResponse(['error' => 'Pacchetto non trovato'], 404);
    }

    // Get all question progress for this package
    $progressList = db()->fetchAll(
        "SELECT question_index, score, interval_days, next_review_date,
                streak, correct_days, last_correct_date, updated_at
         FROM user_question_progress
         WHERE user_id = ? AND package_id = ?
         ORDER BY question_index ASC",
        [$user['id'], $package['id']]
    );

    // Get last sync time
    $lastSync = null;
    if (!empty($progressList)) {
        $lastSync = max(array_column($progressList, 'updated_at'));
    }

    $result = array_map(function($p) {
        return [
            'question_index' => (int)$p['question_index'],
            'score' => (int)$p['score'],
            'interval_days' => (float)$p['interval_days'],
            'next_review_date' => $p['next_review_date'],
            'streak' => (int)$p['streak'],
            'correct_days' => (int)$p['correct_days'],
            'last_correct_date' => $p['last_correct_date']
        ];
    }, $progressList);

    jsonResponse([
        'package_uuid' => $packageUuid,
        'progress' => $result,
        'last_sync' => $lastSync
    ]);
}

/**
 * Get all question progress for all packages
 */
function handleGetAllQuestionProgress() {
    $user = authenticateRequest();

    // Get all packages with progress
    $packages = db()->fetchAll(
        "SELECT DISTINCT p.id, p.uuid, p.name, p.total_questions
         FROM packages p
         INNER JOIN user_question_progress uqp ON p.id = uqp.package_id
         WHERE uqp.user_id = ?
         ORDER BY p.name ASC",
        [$user['id']]
    );

    $result = [];

    foreach ($packages as $pkg) {
        // Get question progress for this package
        $progressList = db()->fetchAll(
            "SELECT question_index, score, interval_days, next_review_date,
                    streak, correct_days, last_correct_date
             FROM user_question_progress
             WHERE user_id = ? AND package_id = ?
             ORDER BY question_index ASC",
            [$user['id'], $pkg['id']]
        );

        $progress = array_map(function($p) {
            return [
                'question_index' => (int)$p['question_index'],
                'score' => (int)$p['score'],
                'interval_days' => (float)$p['interval_days'],
                'next_review_date' => $p['next_review_date'],
                'streak' => (int)$p['streak'],
                'correct_days' => (int)$p['correct_days'],
                'last_correct_date' => $p['last_correct_date']
            ];
        }, $progressList);

        $result[] = [
            'package_uuid' => $pkg['uuid'],
            'package_name' => $pkg['name'],
            'questions_count' => (int)$pkg['total_questions'],
            'progress' => $progress
        ];
    }

    jsonResponse([
        'packages' => $result
    ]);
}

/**
 * Start a study session (when app opens or user starts studying a package)
 */
function handleStartSession() {
    $user = authenticateRequest();

    $packageUuid = $_POST['package_uuid'] ?? null;
    $deviceInfo = substr($_POST['device_info'] ?? '', 0, 255);
    $appVersion = substr($_POST['app_version'] ?? '', 0, 50);

    $packageId = null;
    if ($packageUuid) {
        $package = db()->fetch("SELECT id FROM packages WHERE uuid = ?", [$packageUuid]);
        $packageId = $package ? $package['id'] : null;
    }

    // Close any active sessions for this user (abandoned)
    db()->query(
        "UPDATE study_sessions SET status = 'abandoned', ended_at = NOW()
         WHERE user_id = ? AND status = 'active'",
        [$user['id']]
    );

    // Create new session
    $sessionId = db()->insert('study_sessions', [
        'user_id' => $user['id'],
        'package_id' => $packageId,
        'started_at' => date('Y-m-d H:i:s'),
        'device_info' => $deviceInfo ?: null,
        'app_version' => $appVersion ?: null,
        'status' => 'active',
        'created_at' => date('Y-m-d H:i:s')
    ]);

    jsonResponse([
        'success' => true,
        'session_id' => $sessionId,
        'started_at' => date('Y-m-d H:i:s')
    ]);
}

/**
 * End a study session (when app closes or user stops studying)
 */
function handleEndSession() {
    $user = authenticateRequest();

    $sessionId = (int)($_POST['session_id'] ?? 0);
    $questionsAnswered = (int)($_POST['questions_answered'] ?? 0);
    $correctAnswers = (int)($_POST['correct_answers'] ?? 0);

    // Get session
    $session = db()->fetch(
        "SELECT * FROM study_sessions WHERE id = ? AND user_id = ? AND status = 'active'",
        [$sessionId, $user['id']]
    );

    if (!$session) {
        // Try to find any active session for this user
        $session = db()->fetch(
            "SELECT * FROM study_sessions WHERE user_id = ? AND status = 'active' ORDER BY id DESC LIMIT 1",
            [$user['id']]
        );
    }

    if (!$session) {
        jsonResponse(['error' => 'Nessuna sessione attiva trovata'], 404);
    }

    $endedAt = date('Y-m-d H:i:s');
    $startedAt = strtotime($session['started_at']);
    $duration = time() - $startedAt;

    // Update session
    db()->query(
        "UPDATE study_sessions SET
            ended_at = ?,
            duration_seconds = ?,
            questions_answered = ?,
            correct_answers = ?,
            status = 'completed'
         WHERE id = ?",
        [$endedAt, $duration, $questionsAnswered, $correctAnswers, $session['id']]
    );

    // Update daily stats
    updateDailyStats($user['id'], $session['package_id'], $session['started_at'], $duration, $questionsAnswered, $correctAnswers);

    jsonResponse([
        'success' => true,
        'duration_seconds' => $duration,
        'ended_at' => $endedAt
    ]);
}

/**
 * Update session activity (periodic ping to track ongoing session)
 */
function handleUpdateSession() {
    $user = authenticateRequest();

    $sessionId = (int)($_POST['session_id'] ?? 0);
    $questionsAnswered = (int)($_POST['questions_answered'] ?? 0);
    $correctAnswers = (int)($_POST['correct_answers'] ?? 0);

    $session = db()->fetch(
        "SELECT * FROM study_sessions WHERE id = ? AND user_id = ? AND status = 'active'",
        [$sessionId, $user['id']]
    );

    if (!$session) {
        jsonResponse(['error' => 'Sessione non trovata'], 404);
    }

    db()->query(
        "UPDATE study_sessions SET
            questions_answered = ?,
            correct_answers = ?
         WHERE id = ?",
        [$questionsAnswered, $correctAnswers, $sessionId]
    );

    jsonResponse(['success' => true]);
}

/**
 * Get study statistics for dashboard
 */
function handleGetStudyStats() {
    $user = authenticateRequest();

    $packageUuid = $_GET['package_uuid'] ?? null;
    $days = min(90, max(1, (int)($_GET['days'] ?? 30)));

    $packageId = null;
    if ($packageUuid) {
        $package = db()->fetch("SELECT id FROM packages WHERE uuid = ?", [$packageUuid]);
        $packageId = $package ? $package['id'] : null;
    }

    // Get daily stats for the period
    $dateFrom = date('Y-m-d', strtotime("-{$days} days"));

    $wherePackage = $packageId ? "AND package_id = ?" : "";
    $params = $packageId ? [$user['id'], $dateFrom, $packageId] : [$user['id'], $dateFrom];

    $dailyStats = db()->fetchAll(
        "SELECT
            study_date,
            SUM(total_sessions) as sessions,
            SUM(total_duration_seconds) as duration,
            SUM(total_questions) as questions,
            SUM(total_correct) as correct,
            MIN(first_session_at) as first_time,
            MAX(last_session_at) as last_time
         FROM daily_study_stats
         WHERE user_id = ? AND study_date >= ? $wherePackage
         GROUP BY study_date
         ORDER BY study_date DESC",
        $params
    );

    // Get totals
    $totals = db()->fetch(
        "SELECT
            COUNT(DISTINCT study_date) as days_studied,
            COALESCE(SUM(total_sessions), 0) as total_sessions,
            COALESCE(SUM(total_duration_seconds), 0) as total_duration,
            COALESCE(SUM(total_questions), 0) as total_questions,
            COALESCE(SUM(total_correct), 0) as total_correct
         FROM daily_study_stats
         WHERE user_id = ? AND study_date >= ? $wherePackage",
        $params
    );

    jsonResponse([
        'period_days' => $days,
        'totals' => [
            'days_studied' => (int)$totals['days_studied'],
            'total_sessions' => (int)$totals['total_sessions'],
            'total_duration_seconds' => (int)$totals['total_duration'],
            'total_questions' => (int)$totals['total_questions'],
            'total_correct' => (int)$totals['total_correct'],
            'accuracy' => $totals['total_questions'] > 0
                ? round(($totals['total_correct'] / $totals['total_questions']) * 100, 1)
                : 0
        ],
        'daily' => array_map(function($d) {
            return [
                'date' => $d['study_date'],
                'sessions' => (int)$d['sessions'],
                'duration_seconds' => (int)$d['duration'],
                'questions' => (int)$d['questions'],
                'correct' => (int)$d['correct'],
                'first_time' => $d['first_time'],
                'last_time' => $d['last_time']
            ];
        }, $dailyStats)
    ]);
}

/**
 * Helper: Update daily aggregated stats
 */
function updateDailyStats($userId, $packageId, $sessionStart, $duration, $questions, $correct) {
    $studyDate = date('Y-m-d', strtotime($sessionStart));
    $sessionTime = date('H:i:s', strtotime($sessionStart));

    // Check if record exists
    $existing = db()->fetch(
        "SELECT id, first_session_at FROM daily_study_stats
         WHERE user_id = ? AND study_date = ? AND (package_id = ? OR (package_id IS NULL AND ? IS NULL))",
        [$userId, $studyDate, $packageId, $packageId]
    );

    if ($existing) {
        db()->query(
            "UPDATE daily_study_stats SET
                total_sessions = total_sessions + 1,
                total_duration_seconds = total_duration_seconds + ?,
                total_questions = total_questions + ?,
                total_correct = total_correct + ?,
                first_session_at = LEAST(COALESCE(first_session_at, ?), ?),
                last_session_at = GREATEST(COALESCE(last_session_at, ?), ?)
             WHERE id = ?",
            [$duration, $questions, $correct, $sessionTime, $sessionTime, $sessionTime, $sessionTime, $existing['id']]
        );
    } else {
        db()->insert('daily_study_stats', [
            'user_id' => $userId,
            'package_id' => $packageId,
            'study_date' => $studyDate,
            'total_sessions' => 1,
            'total_duration_seconds' => $duration,
            'total_questions' => $questions,
            'total_correct' => $correct,
            'first_session_at' => $sessionTime,
            'last_session_at' => $sessionTime
        ]);
    }
}
