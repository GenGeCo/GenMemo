<?php
require_once __DIR__ . '/../includes/init.php';

$packageId = $_GET['id'] ?? '';

// Get package
$package = db()->fetch(
    "SELECT * FROM packages WHERE uuid = ? AND status = 'published'",
    [$packageId]
);

if (!$package || !$package['json_file_key']) {
    http_response_code(404);
    die('Package not found');
}

$jsonPath = __DIR__ . '/../uploads/' . $package['json_file_key'];

if (!file_exists($jsonPath)) {
    http_response_code(404);
    die('File not found');
}

// Log download
$userId = Auth::userId();
db()->insert('package_downloads', [
    'package_id' => $package['id'],
    'user_id' => $userId,
    'ip_address' => $_SERVER['REMOTE_ADDR'] ?? 'unknown',
    'user_agent' => substr($_SERVER['HTTP_USER_AGENT'] ?? '', 0, 255),
    'downloaded_at' => date('Y-m-d H:i:s')
]);

// Increment download count
db()->query(
    "UPDATE packages SET download_count = download_count + 1 WHERE id = ?",
    [$package['id']]
);

// Send file
$filename = preg_replace('/[^a-zA-Z0-9_-]/', '_', $package['name']) . '.json';

header('Content-Type: application/json');
header('Content-Disposition: attachment; filename="' . $filename . '"');
header('Content-Length: ' . filesize($jsonPath));
header('Cache-Control: no-cache, must-revalidate');

readfile($jsonPath);
exit;
