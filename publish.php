<?php
require_once __DIR__ . '/includes/init.php';

Auth::requireLogin();
$user = Auth::user();

$packageId = $_GET['package'] ?? '';

// Get package
$package = db()->fetch(
    "SELECT * FROM packages WHERE uuid = ? AND user_id = ?",
    [$packageId, $user['id']]
);

if (!$package) {
    header('Location: my-packages.php');
    exit;
}

$error = null;
$success = null;

// Handle publish action
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    if (!Auth::verifyCsrf($_POST['csrf_token'] ?? '')) {
        $error = 'Invalid request. Please try again.';
    } else {
        $action = $_POST['action'] ?? '';
        $isPublic = isset($_POST['is_public']) ? 1 : 0;

        if ($action === 'publish') {
            // Check if package has questions
            if (!$package['json_file_key']) {
                $error = 'Il pacchetto non ha domande. Torna indietro e aggiungile.';
            } else {
                db()->update('packages', [
                    'status' => 'published',
                    'is_public' => $isPublic,
                    'published_at' => date('Y-m-d H:i:s'),
                    'updated_at' => date('Y-m-d H:i:s')
                ], 'id = ?', [$package['id']]);

                $success = true;

                // Refresh package data
                $package = db()->fetch(
                    "SELECT * FROM packages WHERE uuid = ?",
                    [$packageId]
                );
            }
        }
    }
}
?>
<!DOCTYPE html>
<html lang="it">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Pubblica Pacchetto - GenMemo</title>
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

                <nav class="nav-links">
                    <a href="index.php" class="nav-link">Home</a>
                    <a href="packages.php" class="nav-link">Pacchetti</a>
                    <a href="create.php" class="nav-link">Crea</a>
                    <a href="my-packages.php" class="nav-link">I Miei Pacchetti</a>
                </nav>

                <div class="user-menu">
                    <div class="user-info">
                        <div class="user-avatar"><?= strtoupper(substr($user['username'], 0, 1)) ?></div>
                        <span><?= e($user['username']) ?></span>
                    </div>
                    <a href="logout.php" class="btn-ghost btn-small">Esci</a>
                </div>
            </header>

            <section class="section" style="max-width: 600px; margin: 0 auto;">
                <?php if ($success): ?>
                    <!-- Success State -->
                    <div style="text-align: center; padding: 2rem 0;">
                        <div style="font-size: 4rem; margin-bottom: 1rem;">OK</div>
                        <h1 class="section-title" style="justify-content: center;">Pacchetto Pubblicato!</h1>
                        <p class="section-subtitle">
                            "<?= e($package['name']) ?>" e ora
                            <?= $package['is_public'] ? 'disponibile pubblicamente' : 'privato (solo tu puoi vederlo)' ?>.
                        </p>

                        <div class="alert alert-success">
                            <span class="alert-icon">i</span>
                            <div>
                                <strong>Link al pacchetto:</strong><br>
                                <code style="word-break: break-all;"><?= SITE_URL ?>/package.php?id=<?= $package['uuid'] ?></code>
                            </div>
                        </div>

                        <div class="alert alert-info">
                            <span class="alert-icon">APP</span>
                            <div>
                                <strong>Per l'app Android:</strong><br>
                                Usa questo codice: <code style="font-size: 1.1rem; font-weight: bold;"><?= $package['uuid'] ?></code>
                            </div>
                        </div>

                        <div style="display: flex; gap: 1rem; margin-top: 2rem; justify-content: center;">
                            <a href="package.php?id=<?= $package['uuid'] ?>" class="btn-primary">
                                Visualizza Pacchetto
                            </a>
                            <a href="my-packages.php" class="btn-ghost">
                                I Miei Pacchetti
                            </a>
                        </div>
                    </div>

                <?php else: ?>
                    <!-- Publish Form -->
                    <h1 class="section-title" style="justify-content: center;">Pubblica Pacchetto</h1>
                    <p class="section-subtitle">Rivedi le impostazioni prima di pubblicare</p>

                    <?php if ($error): ?>
                        <div class="alert alert-error">
                            <span class="alert-icon">!</span>
                            <?= e($error) ?>
                        </div>
                    <?php endif; ?>

                    <!-- Package Summary -->
                    <div class="feature-card" style="margin-bottom: 2rem;">
                        <h3 class="feature-title" style="margin-bottom: 0.5rem;"><?= e($package['name']) ?></h3>
                        <?php if ($package['description']): ?>
                            <p class="feature-text"><?= e($package['description']) ?></p>
                        <?php endif; ?>

                        <div style="display: flex; gap: 1rem; margin-top: 1rem; flex-wrap: wrap;">
                            <span class="package-stat">
                                <span class="package-stat-icon">?</span>
                                <?= $package['total_questions'] ?> domande
                            </span>

                            <?php
                            $questionTypes = json_decode($package['question_types'], true) ?? [];
                            $answerTypes = json_decode($package['answer_types'], true) ?? [];
                            $allTypes = array_unique(array_merge($questionTypes, $answerTypes));
                            ?>

                            <?php foreach ($allTypes as $type): ?>
                                <span class="package-badge badge-<?= $type === 'text' ? 'text' : ($type === 'audio' ? 'audio' : 'image') ?>">
                                    <?= ucfirst($type) ?>
                                </span>
                            <?php endforeach; ?>
                        </div>
                    </div>

                    <form method="POST" action="">
                        <input type="hidden" name="csrf_token" value="<?= Auth::csrfToken() ?>">
                        <input type="hidden" name="action" value="publish">

                        <div class="form-group">
                            <label class="checkbox-item">
                                <input type="checkbox" name="is_public" value="1" class="checkbox-input" checked>
                                <span class="checkbox-label">Rendi pubblico</span>
                            </label>
                            <p class="form-hint">
                                Se pubblico, il pacchetto sara visibile a tutti nella lista.
                                Altrimenti solo tu potrai accedervi tramite il link diretto.
                            </p>
                        </div>

                        <div style="display: flex; gap: 1rem; margin-top: 2rem;">
                            <a href="my-packages.php" class="btn-ghost" style="flex: 1; justify-content: center;">
                                Salva come Bozza
                            </a>
                            <button type="submit" class="btn-primary btn-success" style="flex: 2; justify-content: center;">
                                Pubblica Ora
                            </button>
                        </div>
                    </form>
                <?php endif; ?>
            </section>

            <!-- Footer -->
            <footer class="footer">
                <p>GenMemo &copy; <?= date('Y') ?> - Powered by GenGeCo</p>
            </footer>
        </div>
    </div>
</body>
</html>
