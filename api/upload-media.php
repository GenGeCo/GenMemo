<?php
/**
 * API: Upload Media to R2
 * Handles image and audio uploads for packages
 */

require_once __DIR__ . '/../includes/init.php';

Auth::requireLogin();
$user = Auth::user();

header('Content-Type: application/json');

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    jsonResponse(['success' => false, 'error' => 'Method not allowed'], 405);
}

// CSRF check
if (!Auth::verifyCsrf($_POST['csrf_token'] ?? '')) {
    jsonResponse(['success' => false, 'error' => 'Invalid request'], 403);
}

$packageId = $_POST['package_id'] ?? '';
$placeholder = $_POST['placeholder'] ?? '';
$questionIndex = (int)($_POST['question_index'] ?? 0);

// Verify package ownership
$package = db()->fetch(
    "SELECT * FROM packages WHERE uuid = ? AND user_id = ?",
    [$packageId, $user['id']]
);

if (!$package) {
    jsonResponse(['success' => false, 'error' => 'Package not found'], 404);
}

// Check if file was uploaded
if (!isset($_FILES['file']) || $_FILES['file']['error'] === UPLOAD_ERR_NO_FILE) {
    jsonResponse(['success' => false, 'error' => 'No file uploaded'], 400);
}

$file = $_FILES['file'];

// Upload to R2
$folder = "packages/{$packageId}";
$result = r2()->uploadFromForm($file, $folder);

if (!$result['success']) {
    jsonResponse(['success' => false, 'error' => $result['error']], 400);
}

// Determine file type
$mimeType = mime_content_type($file['tmp_name']);
$fileType = in_array($mimeType, ALLOWED_IMAGE_TYPES) ? 'image' : 'audio';

// Save to database
try {
    db()->insert('package_media', [
        'package_id' => $package['id'],
        'file_key' => $result['key'],
        'file_name' => $file['name'],
        'file_type' => $fileType,
        'mime_type' => $mimeType,
        'file_size' => $file['size'],
        'placeholder' => $placeholder,
        'created_at' => date('Y-m-d H:i:s')
    ]);

    // Update the package JSON with the new URL
    $jsonPath = __DIR__ . '/../uploads/' . $package['json_file_key'];
    if (file_exists($jsonPath)) {
        $packageJson = json_decode(file_get_contents($jsonPath), true);

        if ($packageJson) {
            // Check if this is a direct question image upload (placeholder format: [QUESTION_IMAGE_X])
            if (preg_match('/^\[QUESTION_IMAGE_(\d+)\]$/', $placeholder, $matches)) {
                $qIndex = (int)$matches[1] - 1; // Convert to 0-based index
                if (isset($packageJson['questions'][$qIndex])) {
                    $packageJson['questions'][$qIndex]['question_image'] = $result['url'];
                }
            } else {
                // Replace placeholder in questions text
                $jsonString = json_encode($packageJson);
                $jsonString = str_replace($placeholder, $result['url'], $jsonString);
                $packageJson = json_decode($jsonString, true);
            }

            file_put_contents($jsonPath, json_encode($packageJson, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE));
        }
    }

    jsonResponse([
        'success' => true,
        'url' => $result['url'],
        'key' => $result['key'],
        'placeholder' => $placeholder
    ]);

} catch (Exception $e) {
    // Try to delete the uploaded file on error
    r2()->delete($result['key']);
    jsonResponse(['success' => false, 'error' => 'Database error: ' . $e->getMessage()], 500);
}
