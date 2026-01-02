<?php
require_once __DIR__ . '/includes/init.php';

// Redirect if already logged in
if (Auth::isLoggedIn()) {
    header('Location: index.php');
    exit;
}

$error = null;

// Handle form submission
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $email = trim($_POST['email'] ?? '');
    $password = $_POST['password'] ?? '';

    // CSRF check
    if (!Auth::verifyCsrf($_POST['csrf_token'] ?? '')) {
        $error = 'Invalid request. Please try again.';
    } else {
        $result = Auth::login($email, $password);
        if ($result['success']) {
            // Redirect to intended page or home
            $redirect = $_GET['redirect'] ?? 'index.php';
            header('Location: ' . $redirect);
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
    <title>Accedi - GenMemo</title>
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

            <!-- Login Form -->
            <div class="auth-container">
                <div class="auth-card">
                    <h1 class="auth-title">Bentornato</h1>
                    <p class="auth-subtitle">Accedi al tuo account GenMemo</p>

                    <?php if ($error): ?>
                        <div class="alert alert-error">
                            <span class="alert-icon">!</span>
                            <?= e($error) ?>
                        </div>
                    <?php endif; ?>

                    <?php if (isset($_GET['registered'])): ?>
                        <div class="alert alert-success">
                            <span class="alert-icon">OK</span>
                            Registrazione completata! Ora puoi accedere.
                        </div>
                    <?php endif; ?>

                    <form method="POST" action="">
                        <input type="hidden" name="csrf_token" value="<?= Auth::csrfToken() ?>">

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
                                placeholder="La tua password"
                                required
                            >
                        </div>

                        <button type="submit" class="btn-primary" style="width: 100%; justify-content: center;">
                            Accedi
                        </button>
                    </form>

                    <p class="auth-footer">
                        Non hai un account? <a href="register.php">Registrati</a>
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
