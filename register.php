<?php
require_once __DIR__ . '/includes/init.php';

// Redirect if already logged in
if (Auth::isLoggedIn()) {
    header('Location: index.php');
    exit;
}

$error = null;
$success = null;

// Handle form submission
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $username = trim($_POST['username'] ?? '');
    $email = trim($_POST['email'] ?? '');
    $password = $_POST['password'] ?? '';
    $passwordConfirm = $_POST['password_confirm'] ?? '';

    // CSRF check
    if (!Auth::verifyCsrf($_POST['csrf_token'] ?? '')) {
        $error = 'Invalid request. Please try again.';
    } elseif ($password !== $passwordConfirm) {
        $error = 'Le password non corrispondono.';
    } else {
        $result = Auth::register($username, $email, $password);
        if ($result['success']) {
            // Auto login after registration
            Auth::login($email, $password);
            header('Location: index.php');
            exit;
        } else {
            $error = $result['error'];
        }
    }
}
?>
<!DOCTYPE html>
<html lang="it">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Registrati - GenMemo</title>
    <link rel="stylesheet" href="styles.css">
</head>
<body>
    <div class="page-shell">
        <div class="content-inner">
            <!-- Header -->
            <header>
                <div class="brand">
                    <a href="index.php" style="display: flex; align-items: center; gap: 0.75rem; text-decoration: none; color: inherit;">
                        <div class="brand-logo">GM</div>
                        <div class="brand-text">
                            <div class="brand-text-title">GenMemo</div>
                            <div class="brand-text-sub">Quiz Package Creator</div>
                        </div>
                    </a>
                </div>
            </header>

            <!-- Registration Form -->
            <div class="auth-container">
                <div class="auth-card">
                    <h1 class="auth-title">Crea Account</h1>
                    <p class="auth-subtitle">Registrati per iniziare a creare pacchetti quiz</p>

                    <?php if ($error): ?>
                        <div class="alert alert-error">
                            <span class="alert-icon">!</span>
                            <?= e($error) ?>
                        </div>
                    <?php endif; ?>

                    <form method="POST" action="">
                        <input type="hidden" name="csrf_token" value="<?= Auth::csrfToken() ?>">

                        <div class="form-group">
                            <label class="form-label" for="username">Username</label>
                            <input
                                type="text"
                                id="username"
                                name="username"
                                class="form-input"
                                placeholder="Il tuo username"
                                value="<?= e($_POST['username'] ?? '') ?>"
                                required
                                minlength="3"
                                maxlength="50"
                            >
                            <p class="form-hint">3-50 caratteri, lettere e numeri</p>
                        </div>

                        <div class="form-group">
                            <label class="form-label" for="email">Email</label>
                            <input
                                type="email"
                                id="email"
                                name="email"
                                class="form-input"
                                placeholder="tuaemail@esempio.com"
                                value="<?= e($_POST['email'] ?? '') ?>"
                                required
                            >
                        </div>

                        <div class="form-group">
                            <label class="form-label" for="password">Password</label>
                            <input
                                type="password"
                                id="password"
                                name="password"
                                class="form-input"
                                placeholder="Minimo 8 caratteri"
                                required
                                minlength="8"
                            >
                        </div>

                        <div class="form-group">
                            <label class="form-label" for="password_confirm">Conferma Password</label>
                            <input
                                type="password"
                                id="password_confirm"
                                name="password_confirm"
                                class="form-input"
                                placeholder="Ripeti la password"
                                required
                            >
                        </div>

                        <button type="submit" class="btn-primary" style="width: 100%; justify-content: center;">
                            Registrati
                        </button>
                    </form>

                    <p class="auth-footer">
                        Hai gia un account? <a href="login.php">Accedi</a>
                    </p>
                </div>
            </div>

            <!-- Footer -->
            <footer class="footer">
                <p>GenMemo &copy; <?= date('Y') ?> - Powered by GenGeCo</p>
            </footer>
        </div>
    </div>
</body>
</html>
