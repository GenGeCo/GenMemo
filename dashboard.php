<?php
/**
 * Dashboard Autore - Statistiche utilizzo pacchetti
 */
require_once __DIR__ . '/includes/init.php';

Auth::requireLogin();
$user = Auth::user();

// Get user's packages with stats
$packages = db()->fetchAll(
    "SELECT
        p.*,
        COUNT(DISTINCT pd.user_id) as unique_users,
        COUNT(pd.id) as total_downloads
     FROM packages p
     LEFT JOIN package_downloads pd ON p.id = pd.package_id
     WHERE p.user_id = ?
     GROUP BY p.id
     ORDER BY p.created_at DESC",
    [$user['id']]
);

// Get total stats
$totalStats = db()->fetch(
    "SELECT
        COUNT(DISTINCT p.id) as total_packages,
        COALESCE(SUM(p.download_count), 0) as total_downloads,
        COUNT(DISTINCT pd.user_id) as total_users
     FROM packages p
     LEFT JOIN package_downloads pd ON p.id = pd.package_id
     WHERE p.user_id = ?",
    [$user['id']]
);
?>
<!DOCTYPE html>
<html lang="it">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Dashboard - GenMemo</title>
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
                    <a href="my-packages.php" class="nav-link">I Miei</a>
                    <a href="dashboard.php" class="nav-link active">Dashboard</a>
                </nav>

                <div class="user-menu">
                    <div class="user-info">
                        <div class="user-avatar"><?= strtoupper(substr($user['username'], 0, 1)) ?></div>
                        <span><?= e($user['username']) ?></span>
                    </div>
                    <a href="logout.php" class="btn-ghost btn-small">Esci</a>
                </div>
            </header>

            <!-- Dashboard Content -->
            <section class="section">
                <h1 class="section-title">Dashboard Autore</h1>
                <p class="section-subtitle">Monitora l'utilizzo dei tuoi pacchetti</p>

                <!-- Stats Overview -->
                <div class="grid-3" style="margin-bottom: 2rem;">
                    <div class="feature-card" style="text-align: center;">
                        <span class="feature-icon">PKG</span>
                        <div style="font-size: 2.5rem; font-weight: 700; color: var(--accent);">
                            <?= $totalStats['total_packages'] ?? 0 ?>
                        </div>
                        <div style="color: var(--text-muted);">Pacchetti Creati</div>
                    </div>

                    <div class="feature-card" style="text-align: center;">
                        <span class="feature-icon">DL</span>
                        <div style="font-size: 2.5rem; font-weight: 700; color: var(--accent);">
                            <?= $totalStats['total_downloads'] ?? 0 ?>
                        </div>
                        <div style="color: var(--text-muted);">Download Totali</div>
                    </div>

                    <div class="feature-card" style="text-align: center;">
                        <span class="feature-icon">USR</span>
                        <div style="font-size: 2.5rem; font-weight: 700; color: var(--accent);">
                            <?= $totalStats['total_users'] ?? 0 ?>
                        </div>
                        <div style="color: var(--text-muted);">Studenti Unici</div>
                    </div>
                </div>

                <!-- Package List -->
                <h2 style="margin-bottom: 1rem; color: var(--text-secondary);">I Tuoi Pacchetti</h2>

                <?php if (empty($packages)): ?>
                    <div class="alert alert-info">
                        <span class="alert-icon">i</span>
                        <div>
                            Non hai ancora creato pacchetti.
                            <a href="create.php" style="color: var(--accent);">Crea il tuo primo pacchetto</a>
                        </div>
                    </div>
                <?php else: ?>
                    <div class="packages-list">
                        <?php foreach ($packages as $pkg): ?>
                            <a href="dashboard-package.php?id=<?= $pkg['uuid'] ?>" class="package-card-link" style="text-decoration: none;">
                                <div class="feature-card" style="cursor: pointer; transition: transform 0.2s, box-shadow 0.2s;">
                                    <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 1rem;">
                                        <div>
                                            <h3 style="margin: 0; color: var(--text-main);"><?= e($pkg['name']) ?></h3>
                                            <p style="margin: 0.25rem 0 0; color: var(--text-muted); font-size: 0.85rem;">
                                                <?= $pkg['total_questions'] ?> domande
                                                <?php if ($pkg['topic']): ?>
                                                    - <?= e($pkg['topic']) ?>
                                                <?php endif; ?>
                                            </p>
                                        </div>
                                        <span class="package-badge <?= $pkg['status'] === 'published' ? 'badge-text' : 'badge-audio' ?>">
                                            <?= $pkg['status'] === 'published' ? 'Pubblicato' : 'Bozza' ?>
                                        </span>
                                    </div>

                                    <div style="display: flex; gap: 2rem; color: var(--text-muted); font-size: 0.9rem;">
                                        <div>
                                            <strong style="color: var(--accent);"><?= $pkg['unique_users'] ?></strong> studenti
                                        </div>
                                        <div>
                                            <strong style="color: var(--accent);"><?= $pkg['total_downloads'] ?></strong> download
                                        </div>
                                        <div>
                                            <?= $pkg['is_public'] ? 'Pubblico' : 'Privato' ?>
                                        </div>
                                    </div>
                                </div>
                            </a>
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

    <style>
        .package-card-link:hover .feature-card {
            transform: translateY(-2px);
            box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
        }
        .packages-list {
            display: flex;
            flex-direction: column;
            gap: 1rem;
        }
    </style>
</body>
</html>
