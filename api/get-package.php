<?php
/**
 * API Endpoint for Android App
 * GET /api/get-package.php?code=PACKAGE_UUID
 *
 * Returns the package JSON directly for the app to use
 */

require_once __DIR__ . '/../includes/init.php';

header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET');

$code = trim($_GET['code'] ?? '');

if (empty($code)) {
    jsonResponse(['error' => 'Missing package code'], 400);
}

// Get package with author info
$package = db()->fetch(
    "SELECT p.*, u.username as author_name
     FROM packages p
     JOIN users u ON p.user_id = u.id
     WHERE p.uuid = ? AND p.status = 'published'",
    [$code]
);

if (!$package) {
    jsonResponse(['error' => 'Package not found'], 404);
}

// Check if JSON file exists
if (!$package['json_file_key']) {
    jsonResponse(['error' => 'Package has no content'], 404);
}

$jsonPath = __DIR__ . '/../uploads/' . $package['json_file_key'];

if (!file_exists($jsonPath)) {
    jsonResponse(['error' => 'Package file not found'], 404);
}

// Log download for stats
db()->insert('package_downloads', [
    'package_id' => $package['id'],
    'user_id' => null,
    'ip_address' => $_SERVER['REMOTE_ADDR'] ?? 'unknown',
    'user_agent' => substr($_SERVER['HTTP_USER_AGENT'] ?? '', 0, 255),
    'downloaded_at' => date('Y-m-d H:i:s')
]);

// Increment download count
db()->query(
    "UPDATE packages SET download_count = download_count + 1 WHERE id = ?",
    [$package['id']]
);

// Return package JSON
$packageData = json_decode(file_get_contents($jsonPath), true);

if (!$packageData) {
    jsonResponse(['error' => 'Invalid package data'], 500);
}

// Add metadata for the app
$packageData['_meta'] = [
    'uuid' => $package['uuid'],
    'name' => $package['name'],
    'author' => $package['author_name'] ?? 'Unknown',
    'download_count' => $package['download_count'] + 1,
    'server_url' => SITE_URL
];

echo json_encode($packageData, JSON_UNESCAPED_UNICODE);
