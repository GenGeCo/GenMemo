<?php
require_once __DIR__ . '/includes/init.php';

$user = Auth::user();

// Get public packages
$search = trim($_GET['q'] ?? '');
$page = max(1, (int)($_GET['page'] ?? 1));
$perPage = 12;
$offset = ($page - 1) * $perPage;

// Build query
$whereClause = "WHERE p.status = 'published' AND p.is_public = 1";
$params = [];

if ($search) {
    $whereClause .= " AND (p.name LIKE ? OR p.description LIKE ? OR p.topic LIKE ?)";
    $searchTerm = "%$search%";
    $params = [$searchTerm, $searchTerm, $searchTerm];
}

// Get total count
$countSql = "SELECT COUNT(*) as total FROM packages p $whereClause";
$totalResult = db()->fetch($countSql, $params);
$total = $totalResult['total'] ?? 0;
$totalPages = ceil($total / $perPage);

// Get packages
$sql = "SELECT p.*, u.username as author_name
        FROM packages p
        JOIN users u ON p.user_id = u.id
        $whereClause
        ORDER BY p.created_at DESC
        LIMIT $perPage OFFSET $offset";
$packages = db()->fetchAll($sql, $params);
?>
<!DOCTYPE html>
<html lang="it">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Pacchetti Pubblici - GenMemo</title>
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
                    <a href="packages.php" class="nav-link active">Pacchetti</a>
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

            <!-- Main Content -->
            <section class="section">
                <h1 class="section-title">Pacchetti Pubblici</h1>
                <p class="section-subtitle">Esplora e scarica pacchetti quiz creati dalla community</p>

                <!-- Search -->
                <form method="GET" action="" class="search-box">
                    <input
                        type="text"
                        name="q"
                        class="form-input search-input"
                        placeholder="Cerca pacchetti..."
                        value="<?= e($search) ?>"
                    >
                    <button type="submit" class="btn-primary btn-small">Cerca</button>
                </form>

                <?php if (empty($packages)): ?>
                    <div class="empty-state">
                        <div class="empty-state-icon">?</div>
                        <h3 class="empty-state-title">Nessun pacchetto trovato</h3>
                        <p class="empty-state-text">
                            <?php if ($search): ?>
                                Nessun risultato per "<?= e($search) ?>". Prova con altri termini.
                            <?php else: ?>
                                Non ci sono ancora pacchetti pubblici. Sii il primo a crearne uno!
                            <?php endif; ?>
                        </p>
                        <?php if ($user): ?>
                            <a href="create.php" class="btn-primary">Crea un Pacchetto</a>
                        <?php endif; ?>
                    </div>
                <?php else: ?>
                    <div class="grid-3">
                        <?php foreach ($packages as $pkg): ?>
                            <?php
                            $questionTypes = json_decode($pkg['question_types'], true) ?? [];
                            $answerTypes = json_decode($pkg['answer_types'], true) ?? [];
                            $allTypes = array_unique(array_merge($questionTypes, $answerTypes));
                            ?>
                            <div class="package-card" onclick="window.location='package.php?id=<?= $pkg['uuid'] ?>'">
                                <div class="package-header">
                                    <h3 class="package-title"><?= e($pkg['name']) ?></h3>
                                    <div class="package-meta">
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

                                <p class="package-description">
                                    <?= e(substr($pkg['description'] ?? 'Nessuna descrizione', 0, 100)) ?>
                                    <?php if (strlen($pkg['description'] ?? '') > 100): ?>...<?php endif; ?>
                                </p>

                                <div class="package-stats">
                                    <span class="package-stat">
                                        <span class="package-stat-icon">?</span>
                                        <?= $pkg['total_questions'] ?> domande
                                    </span>
                                    <span class="package-stat">
                                        <span class="package-stat-icon">@</span>
                                        <?= e($pkg['author_name']) ?>
                                    </span>
                                    <span class="package-stat">
                                        <span class="package-stat-icon">DL</span>
                                        <?= $pkg['download_count'] ?>
                                    </span>
                                </div>
                            </div>
                        <?php endforeach; ?>
                    </div>

                    <!-- Pagination -->
                    <?php if ($totalPages > 1): ?>
                        <div style="display: flex; justify-content: center; gap: 0.5rem; margin-top: 2rem;">
                            <?php if ($page > 1): ?>
                                <a href="?page=<?= $page - 1 ?>&q=<?= urlencode($search) ?>" class="btn-ghost btn-small">Prev</a>
                            <?php endif; ?>

                            <span style="padding: 0.5rem 1rem; color: var(--text-muted);">
                                Pagina <?= $page ?> di <?= $totalPages ?>
                            </span>

                            <?php if ($page < $totalPages): ?>
                                <a href="?page=<?= $page + 1 ?>&q=<?= urlencode($search) ?>" class="btn-ghost btn-small">Next</a>
                            <?php endif; ?>
                        </div>
                    <?php endif; ?>
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
