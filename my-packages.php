<?php
require_once __DIR__ . '/includes/init.php';

Auth::requireLogin();
$user = Auth::user();

// Get user's packages
$packages = db()->fetchAll(
    "SELECT * FROM packages WHERE user_id = ? ORDER BY created_at DESC",
    [$user['id']]
);
?>
<!DOCTYPE html>
<html lang="it">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>I Miei Pacchetti - GenMemo</title>
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
                    <a href="my-packages.php" class="nav-link active">I Miei Pacchetti</a>
                </nav>

                <div class="user-menu">
                    <div class="user-info">
                        <div class="user-avatar"><?= strtoupper(substr($user['username'], 0, 1)) ?></div>
                        <span><?= e($user['username']) ?></span>
                    </div>
                    <a href="logout.php" class="btn-ghost btn-small">Esci</a>
                </div>
            </header>

            <!-- Main Content -->
            <section class="section">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 2rem;">
                    <div>
                        <h1 class="section-title" style="margin: 0; justify-content: flex-start;">I Miei Pacchetti</h1>
                        <p style="color: var(--text-muted); margin-top: 0.5rem;"><?= count($packages) ?> pacchetti</p>
                    </div>
                    <a href="create.php" class="btn-primary">
                        + Nuovo Pacchetto
                    </a>
                </div>

                <?php if (empty($packages)): ?>
                    <div class="empty-state">
                        <div class="empty-state-icon">O</div>
                        <h3 class="empty-state-title">Nessun pacchetto</h3>
                        <p class="empty-state-text">
                            Non hai ancora creato nessun pacchetto. Inizia ora!
                        </p>
                        <a href="create.php" class="btn-primary">Crea il Primo Pacchetto</a>
                    </div>
                <?php else: ?>
                    <div class="grid-3">
                        <?php foreach ($packages as $pkg): ?>
                            <?php
                            $questionTypes = json_decode($pkg['question_types'], true) ?? [];
                            $answerTypes = json_decode($pkg['answer_types'], true) ?? [];
                            $allTypes = array_unique(array_merge($questionTypes, $answerTypes));

                            $statusLabels = [
                                'draft' => ['label' => 'Bozza', 'class' => 'badge-audio'],
                                'published' => ['label' => 'Pubblicato', 'class' => 'badge-image'],
                                'archived' => ['label' => 'Archiviato', 'class' => 'badge-text']
                            ];
                            $status = $statusLabels[$pkg['status']] ?? $statusLabels['draft'];
                            ?>
                            <div class="package-card">
                                <div class="package-header">
                                    <h3 class="package-title"><?= e($pkg['name']) ?></h3>
                                    <span class="package-badge <?= $status['class'] ?>"><?= $status['label'] ?></span>
                                </div>

                                <div class="package-meta" style="margin-bottom: 1rem;">
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

                                <p class="package-description">
                                    <?= e(substr($pkg['description'] ?? 'Nessuna descrizione', 0, 80)) ?>
                                    <?php if (strlen($pkg['description'] ?? '') > 80): ?>...<?php endif; ?>
                                </p>

                                <div class="package-stats">
                                    <span class="package-stat">
                                        <span class="package-stat-icon">?</span>
                                        <?= $pkg['total_questions'] ?> domande
                                    </span>
                                    <span class="package-stat">
                                        <span class="package-stat-icon">DL</span>
                                        <?= $pkg['download_count'] ?>
                                    </span>
                                </div>

                                <div style="display: flex; gap: 0.5rem; margin-top: 1rem;">
                                    <?php if ($pkg['status'] === 'draft'): ?>
                                        <a href="create.php?step=2&package=<?= $pkg['uuid'] ?>" class="btn-primary btn-small" style="flex: 1; justify-content: center;">
                                            Continua
                                        </a>
                                    <?php else: ?>
                                        <a href="package.php?id=<?= $pkg['uuid'] ?>" class="btn-secondary btn-small" style="flex: 1; justify-content: center;">
                                            Visualizza
                                        </a>
                                    <?php endif; ?>
                                    <a href="edit-package.php?id=<?= $pkg['uuid'] ?>" class="btn-ghost btn-small">
                                        Modifica
                                    </a>
                                </div>
                            </div>
                        <?php endforeach; ?>
                    </div>
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
