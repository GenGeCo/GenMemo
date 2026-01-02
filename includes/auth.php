<?php
/**
 * GenMemo Authentication Functions
 */

if (!defined('GENMEMO')) {
    die('Direct access not allowed');
}

class Auth {

    /**
     * Register a new user
     */
    public static function register(string $username, string $email, string $password): array {
        // Validate input
        if (strlen($username) < 3 || strlen($username) > 50) {
            return ['success' => false, 'error' => 'Username must be 3-50 characters'];
        }

        if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
            return ['success' => false, 'error' => 'Invalid email address'];
        }

        if (strlen($password) < 8) {
            return ['success' => false, 'error' => 'Password must be at least 8 characters'];
        }

        // Check if username or email already exists
        $existing = db()->fetch(
            "SELECT id FROM users WHERE username = ? OR email = ?",
            [$username, $email]
        );

        if ($existing) {
            return ['success' => false, 'error' => 'Username or email already registered'];
        }

        // Hash password
        $passwordHash = password_hash($password, PASSWORD_BCRYPT, ['cost' => HASH_COST]);

        // Insert user
        try {
            $userId = db()->insert('users', [
                'username' => $username,
                'email' => $email,
                'password_hash' => $passwordHash,
                'created_at' => date('Y-m-d H:i:s')
            ]);

            return ['success' => true, 'user_id' => $userId];
        } catch (Exception $e) {
            return ['success' => false, 'error' => 'Registration failed. Please try again.'];
        }
    }

    /**
     * Login user
     */
    public static function login(string $email, string $password): array {
        $user = db()->fetch(
            "SELECT id, username, email, password_hash FROM users WHERE email = ?",
            [$email]
        );

        if (!$user || !password_verify($password, $user['password_hash'])) {
            return ['success' => false, 'error' => 'Invalid email or password'];
        }

        // Set session
        $_SESSION['user_id'] = $user['id'];
        $_SESSION['username'] = $user['username'];
        $_SESSION['email'] = $user['email'];
        $_SESSION['logged_in_at'] = time();

        // Update last login
        db()->update('users',
            ['last_login' => date('Y-m-d H:i:s')],
            'id = ?',
            [$user['id']]
        );

        return ['success' => true, 'user' => [
            'id' => $user['id'],
            'username' => $user['username'],
            'email' => $user['email']
        ]];
    }

    /**
     * Logout user
     */
    public static function logout(): void {
        $_SESSION = [];
        session_destroy();
    }

    /**
     * Check if user is logged in
     */
    public static function isLoggedIn(): bool {
        return isset($_SESSION['user_id']);
    }

    /**
     * Get current user
     */
    public static function user(): ?array {
        if (!self::isLoggedIn()) {
            return null;
        }

        return [
            'id' => $_SESSION['user_id'],
            'username' => $_SESSION['username'],
            'email' => $_SESSION['email']
        ];
    }

    /**
     * Get current user ID
     */
    public static function userId(): ?int {
        return $_SESSION['user_id'] ?? null;
    }

    /**
     * Require login (redirect if not logged in)
     */
    public static function requireLogin(): void {
        if (!self::isLoggedIn()) {
            header('Location: login.php');
            exit;
        }
    }

    /**
     * Generate CSRF token
     */
    public static function csrfToken(): string {
        if (!isset($_SESSION['csrf_token'])) {
            $_SESSION['csrf_token'] = bin2hex(random_bytes(32));
        }
        return $_SESSION['csrf_token'];
    }

    /**
     * Verify CSRF token
     */
    public static function verifyCsrf(string $token): bool {
        return isset($_SESSION['csrf_token']) && hash_equals($_SESSION['csrf_token'], $token);
    }
}
