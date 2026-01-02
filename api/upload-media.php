<?php
/**
 * API: Upload Media (Local Storage)
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

// Validate file
if ($file['error'] !== UPLOAD_ERR_OK) {
    jsonResponse(['success' => false, 'error' => 'Upload error: ' . $file['error']], 400);
}

if ($file['size'] > UPLOAD_MAX_SIZE) {
    jsonResponse(['success' => false, 'error' => 'File too large'], 400);
}

$mimeType = mime_content_type($file['tmp_name']);
$isImage = in_array($mimeType, ALLOWED_IMAGE_TYPES);
$isAudio = in_array($mimeType, ALLOWED_AUDIO_TYPES);

if (!$isImage && !$isAudio) {
    jsonResponse(['success' => false, 'error' => 'File type not allowed: ' . $mimeType], 400);
}

// Create upload directory for this package
$uploadDir = __DIR__ . '/../uploads/media/' . $packageId;
if (!is_dir($uploadDir)) {
    mkdir($uploadDir, 0755, true);
}

// Process image (resize if needed)
$uploadPath = $file['tmp_name'];
if ($isImage) {
    $resized = resizeImage($file['tmp_name'], 960, 540, 85);
    if ($resized) {
        $uploadPath = $resized;
        $mimeType = 'image/jpeg';
    }
}

// Generate unique filename
$ext = $isImage ? 'jpg' : strtolower(pathinfo($file['name'], PATHINFO_EXTENSION));
$filename = randomString(16) . '.' . $ext;
$destPath = $uploadDir . '/' . $filename;

// Move file
if (!move_uploaded_file($uploadPath, $destPath) && $uploadPath !== $file['tmp_name']) {
    // If resized file, copy instead
    if (!copy($uploadPath, $destPath)) {
        jsonResponse(['success' => false, 'error' => 'Failed to save file'], 500);
    }
    unlink($uploadPath);
} elseif ($uploadPath === $file['tmp_name'] && !move_uploaded_file($uploadPath, $destPath)) {
    jsonResponse(['success' => false, 'error' => 'Failed to move uploaded file'], 500);
}

// Clean up temp resized file
if ($uploadPath !== $file['tmp_name'] && file_exists($uploadPath)) {
    @unlink($uploadPath);
}

// Build result
$fileKey = "media/{$packageId}/{$filename}";
$fileUrl = SITE_URL . "/uploads/{$fileKey}";

// Determine file type for DB
$fileType = $isImage ? 'image' : 'audio';

// Save to database
try {
    db()->insert('package_media', [
        'package_id' => $package['id'],
        'file_key' => $fileKey,
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
                    $packageJson['questions'][$qIndex]['question_image'] = $fileUrl;
                }
            } else {
                // Replace placeholder in questions text
                $jsonString = json_encode($packageJson);
                $jsonString = str_replace($placeholder, $fileUrl, $jsonString);
                $packageJson = json_decode($jsonString, true);
            }

            file_put_contents($jsonPath, json_encode($packageJson, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE));
        }
    }

    jsonResponse([
        'success' => true,
        'url' => $fileUrl,
        'key' => $fileKey,
        'placeholder' => $placeholder
    ]);

} catch (Exception $e) {
    // Try to delete the uploaded file on error
    if (file_exists($destPath)) {
        @unlink($destPath);
    }
    jsonResponse(['success' => false, 'error' => 'Database error: ' . $e->getMessage()], 500);
}
