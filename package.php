<?php
require_once __DIR__ . '/includes/init.php';

$user = Auth::user();
$packageId = $_GET['id'] ?? '';

// Get package
$package = db()->fetch(
    "SELECT p.*, u.username as author_name
     FROM packages p
     JOIN users u ON p.user_id = u.id
     WHERE p.uuid = ?",
    [$packageId]
);

if (!$package) {
    header('Location: packages.php');
    exit;
}

// Check access: must be published OR owned by current user
$canAccess = ($package['status'] === 'published') ||
             ($user && $package['user_id'] == $user['id']);

if (!$canAccess) {
    header('Location: packages.php');
    exit;
}

// Load package JSON
$packageJson = null;
if ($package['json_file_key']) {
    $jsonPath = __DIR__ . '/uploads/' . $package['json_file_key'];
    if (file_exists($jsonPath)) {
        $packageJson = json_decode(file_get_contents($jsonPath), true);
    }
}

$questionTypes = json_decode($package['question_types'], true) ?? [];
$answerTypes = json_decode($package['answer_types'], true) ?? [];
$allTypes = array_unique(array_merge($questionTypes, $answerTypes));
?>
<!DOCTYPE html>
<html lang="it">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title><?= e($package['name']) ?> - GenMemo</title>
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
                    <?php if ($user): ?>
                        <a href="create.php" class="nav-link">Crea</a>
                        <a href="my-packages.php" class="nav-link">I Miei Pacchetti</a>
                    <?php endif; ?>
                </nav>

                <div class="user-menu">
                    <?php if ($user): ?>
                        <div class="user-info">
                            <div class="user-avatar"><?= strtoupper(substr($user['username'], 0, 1)) ?></div>
                            <span><?= e($user['username']) ?></span>
                        </div>
                        <a href="logout.php" class="btn-ghost btn-small">Esci</a>
                    <?php else: ?>
                        <a href="login.php" class="btn-ghost btn-small">Accedi</a>
                        <a href="register.php" class="btn-primary btn-small">Registrati</a>
                    <?php endif; ?>
                </div>
            </header>

            <!-- Package Details -->
            <section class="section">
                <div style="display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 1rem; margin-bottom: 2rem;">
                    <div>
                        <h1 class="section-title" style="margin: 0; justify-content: flex-start;">
                            <?= e($package['name']) ?>
                        </h1>
                        <p style="color: var(--text-muted); margin-top: 0.5rem;">
                            di <?= e($package['author_name']) ?> -
                            <?= timeAgo($package['created_at']) ?>
                        </p>
                    </div>

                    <div style="display: flex; gap: 0.5rem; flex-wrap: wrap;">
                        <?php if (in_array('text', $allTypes)): ?>
                            <span class="package-badge badge-text">Testo</span>
                        <?php endif; ?>
                        <?php if (in_array('audio', $allTypes)): ?>
                            <span class="package-badge badge-audio">Audio</span>
                        <?php endif; ?>
                        <?php if (in_array('image', $allTypes)): ?>
                            <span class="package-badge badge-image">Immagini</span>
                        <?php endif; ?>
                    </div>
                </div>

                <?php if ($package['description']): ?>
                    <p style="color: var(--text-muted); margin-bottom: 2rem; line-height: 1.7;">
                        <?= nl2br(e($package['description'])) ?>
                    </p>
                <?php endif; ?>

                <!-- Stats -->
                <div class="grid-3" style="margin-bottom: 2rem;">
                    <div class="feature-card" style="text-align: center;">
                        <span class="feature-icon">?</span>
                        <div style="font-size: 2rem; font-weight: 700; color: var(--accent);">
                            <?= $package['total_questions'] ?>
                        </div>
                        <div style="color: var(--text-muted);">Domande</div>
                    </div>

                    <div class="feature-card" style="text-align: center;">
                        <span class="feature-icon">DL</span>
                        <div style="font-size: 2rem; font-weight: 700; color: var(--accent);">
                            <?= $package['download_count'] ?>
                        </div>
                        <div style="color: var(--text-muted);">Download</div>
                    </div>

                    <div class="feature-card" style="text-align: center;">
                        <span class="feature-icon">ID</span>
                        <div style="font-size: 1rem; font-weight: 700; color: var(--accent); word-break: break-all;">
                            <?= $package['uuid'] ?>
                        </div>
                        <div style="color: var(--text-muted);">Codice App</div>
                    </div>
                </div>

                <!-- Download Actions -->
                <div class="feature-card">
                    <h3 class="feature-title">Scarica Pacchetto</h3>

                    <div style="display: flex; gap: 1rem; flex-wrap: wrap; margin-top: 1rem;">
                        <a href="api/download.php?id=<?= $package['uuid'] ?>" class="btn-primary">
                            Scarica JSON
                        </a>
                        <button class="btn-secondary" onclick="copyCode('<?= $package['uuid'] ?>')">
                            Copia Codice per App
                        </button>
                    </div>

                    <div class="alert alert-info" style="margin-top: 1.5rem;">
                        <span class="alert-icon">APP</span>
                        <div>
                            <strong>Per usare nell'app GenMemo:</strong><br>
                            1. Apri l'app GenMemo sul tuo telefono<br>
                            2. Vai su "Aggiungi Pacchetto"<br>
                            3. Inserisci il codice: <code style="font-weight: bold;"><?= $package['uuid'] ?></code>
                        </div>
                    </div>
                </div>

                <!-- Preview Questions -->
                <?php if ($packageJson && !empty($packageJson['questions'])): ?>
                    <div style="margin-top: 2rem;">
                        <h3 class="feature-title" style="margin-bottom: 1rem;">
                            Anteprima Domande (prime 5)
                        </h3>

                        <?php
                        $previewQuestions = array_slice($packageJson['questions'], 0, 5);
                        foreach ($previewQuestions as $i => $q):
                        ?>
                            <div class="question-card">
                                <div class="question-header">
                                    <span class="question-number">Domanda <?= $i + 1 ?></span>
                                </div>
                                <p style="color: var(--text-main); margin-bottom: 1rem;">
                                    <?= e($q['question'] ?? '') ?>
                                </p>

                                <?php if (!empty($q['answers'])): ?>
                                    <div style="display: flex; flex-direction: column; gap: 0.5rem;">
                                        <?php foreach ($q['answers'] as $answer): ?>
                                            <div style="
                                                padding: 0.5rem 1rem;
                                                border-radius: 8px;
                                                background: <?= ($answer['correct'] ?? false) ? 'rgba(34, 197, 94, 0.15)' : 'rgba(0,0,0,0.2)' ?>;
                                                border: 1px solid <?= ($answer['correct'] ?? false) ? 'rgba(34, 197, 94, 0.3)' : 'var(--border-subtle)' ?>;
                                                color: <?= ($answer['correct'] ?? false) ? '#22c55e' : 'var(--text-muted)' ?>;
                                                font-size: 0.9rem;
                                            ">
                                                <?= ($answer['correct'] ?? false) ? 'OK ' : '' ?>
                                                <?= e($answer['text'] ?? '') ?>
                                            </div>
                                        <?php endforeach; ?>
                                    </div>
                                <?php endif; ?>
                            </div>
                        <?php endforeach; ?>

                        <?php if (count($packageJson['questions']) > 5): ?>
                            <p style="text-align: center; color: var(--text-muted); margin-top: 1rem;">
                                ... e altre <?= count($packageJson['questions']) - 5 ?> domande
                            </p>
                        <?php endif; ?>
                    </div>
                <?php endif; ?>

                <!-- Owner Actions -->
                <?php if ($user && $package['user_id'] == $user['id']): ?>
                    <div style="margin-top: 2rem; padding-top: 2rem; border-top: 1px solid var(--border-subtle);">
                        <h3 class="feature-title">Azioni Proprietario</h3>
                        <div style="display: flex; gap: 1rem; flex-wrap: wrap; margin-top: 1rem;">
                            <a href="edit-package.php?id=<?= $package['uuid'] ?>" class="btn-secondary">
                                Modifica
                            </a>
                            <?php if ($package['status'] === 'draft'): ?>
                                <a href="publish.php?package=<?= $package['uuid'] ?>" class="btn-primary">
                                    Pubblica
                                </a>
                            <?php endif; ?>
                        </div>
                    </div>
                <?php endif; ?>
            </section>

            <!-- Footer -->
            <footer class="footer">
                <p>GenMemo &copy; <?= date('Y') ?> - Powered by GenGeCo</p>
            </footer>
        </div>
    </div>

    <script>
        function copyCode(code) {
            navigator.clipboard.writeText(code).then(() => {
                alert('Codice copiato: ' + code);
            });
        }
    </script>
</body>
</html>
