<?php
require_once __DIR__ . '/../includes/init.php';

Auth::requireLogin();
$user = Auth::user();

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    header('Location: ../create.php');
    exit;
}

// CSRF check
if (!Auth::verifyCsrf($_POST['csrf_token'] ?? '')) {
    flash('error', 'Invalid request. Please try again.');
    header('Location: ../create.php');
    exit;
}

$packageId = $_POST['package_id'] ?? '';
$jsonContent = trim($_POST['json_content'] ?? '');

// Verify package ownership
$package = db()->fetch(
    "SELECT * FROM packages WHERE uuid = ? AND user_id = ?",
    [$packageId, $user['id']]
);

if (!$package) {
    flash('error', 'Pacchetto non trovato.');
    header('Location: ../my-packages.php');
    exit;
}

// Validate JSON
$validation = validatePackageJson($jsonContent);

if (!$validation['valid']) {
    flash('error', 'Errore nel JSON: ' . $validation['error']);
    header("Location: ../create.php?step=3&package=$packageId&method=ai");
    exit;
}

$data = $validation['data'];
$questions = $data['questions'];

// Create package JSON structure
$packageJson = createPackageStructure([
    'name' => $package['name'],
    'description' => $package['description'],
    'author' => $user['username'],
    'question_types' => json_decode($package['question_types'], true),
    'answer_types' => json_decode($package['answer_types'], true),
    'total_questions' => count($questions),
    'questions' => $questions
]);

// Save JSON to file (local for now, R2 later)
$jsonFilename = $packageId . '.json';
$jsonPath = __DIR__ . '/../uploads/' . $jsonFilename;

if (!file_put_contents($jsonPath, json_encode($packageJson, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE))) {
    flash('error', 'Errore nel salvataggio del file.');
    header("Location: ../create.php?step=3&package=$packageId&method=ai");
    exit;
}

// Update package in database
db()->update('packages', [
    'json_file_key' => $jsonFilename,
    'total_questions' => count($questions),
    'updated_at' => date('Y-m-d H:i:s')
], 'id = ?', [$package['id']]);

// Check if media is needed (look for image placeholders in the JSON)
$jsonString = json_encode($packageJson);
$hasImagePlaceholders = preg_match('/\[INSERIRE_IMMAGINE_[^\]]+\]/', $jsonString);

// Also check if image type was selected
$questionTypes = json_decode($package['question_types'], true);
$answerTypes = json_decode($package['answer_types'], true);
$needsMedia = $hasImagePlaceholders || in_array('image', $questionTypes) || in_array('image', $answerTypes);

if ($needsMedia) {
    // Go to media upload step
    header("Location: ../create.php?step=4&package=$packageId");
} else {
    // Go directly to publish
    header("Location: ../publish.php?package=$packageId");
}
exit;
