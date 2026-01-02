<?php
/**
 * GenMemo Configuration
 *
 * SETUP: Copy this file to config.php and fill in your values
 */

// Prevent direct access
if (!defined('GENMEMO')) {
    die('Direct access not allowed');
}

// Error reporting (disable in production)
error_reporting(E_ALL);
ini_set('display_errors', 0); // Set to 1 for debugging

// Database Configuration (Netsons MariaDB)
define('DB_HOST', 'localhost');
define('DB_NAME', 'your_database_name'); // Create this in cPanel
define('DB_USER', 'your_db_user'); // From cPanel
define('DB_PASS', 'your_db_password'); // From cPanel
define('DB_CHARSET', 'utf8mb4');

// Cloudflare R2 Configuration
define('R2_ACCOUNT_ID', 'your_account_id');
define('R2_ACCESS_KEY_ID', 'your_access_key');
define('R2_SECRET_ACCESS_KEY', 'your_secret_key');
define('R2_BUCKET_NAME', 'genmemo');
define('R2_PUBLIC_URL', 'https://pub-xxxxx.r2.dev'); // Your R2 public URL

// Application Settings
define('SITE_URL', 'https://www.gruppogea.net/genmemo');
define('SITE_NAME', 'GenMemo');
define('UPLOAD_MAX_SIZE', 10 * 1024 * 1024); // 10MB
define('ALLOWED_IMAGE_TYPES', ['image/jpeg', 'image/png', 'image/gif', 'image/webp']);
define('ALLOWED_AUDIO_TYPES', ['audio/mpeg', 'audio/mp3', 'audio/wav', 'audio/ogg']);

// Session Configuration
define('SESSION_LIFETIME', 60 * 60 * 24 * 7); // 7 days

// Security
define('HASH_COST', 12); // bcrypt cost

// Timezone
date_default_timezone_set('Europe/Rome');

// Start session with secure settings
if (session_status() === PHP_SESSION_NONE) {
    session_set_cookie_params([
        'lifetime' => SESSION_LIFETIME,
        'path' => '/',
        'secure' => true,
        'httponly' => true,
        'samesite' => 'Lax'
    ]);
    session_start();
}
