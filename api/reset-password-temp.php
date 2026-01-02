<?php
/**
 * TEMPORARY: Reset user password
 * DELETE THIS FILE AFTER USE!
 */

require_once __DIR__ . '/../includes/init.php';

header('Content-Type: application/json');

$email = $_POST['email'] ?? '';
$newPassword = $_POST['new_password'] ?? '';
$secretKey = $_POST['secret_key'] ?? '';

// Security: require a secret key
if ($secretKey !== 'genmemo-reset-2024-temp') {
    die(json_encode(['error' => 'Invalid secret key']));
}

if (empty($email) || empty($newPassword)) {
    die(json_encode(['error' => 'Email and new_password required']));
}

// Hash the new password
$hash = password_hash($newPassword, PASSWORD_BCRYPT, ['cost' => HASH_COST]);

// Update user
$affected = db()->update('users',
    ['password_hash' => $hash],
    'email = ?',
    [$email]
);

if ($affected > 0) {
    echo json_encode([
        'success' => true,
        'message' => 'Password updated for ' . $email,
        'hash_length' => strlen($hash)
    ]);
} else {
    echo json_encode(['error' => 'User not found or no change']);
}
