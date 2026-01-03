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
                <h1 style="font-size: 1.1rem; margin: 0 0 0.25rem;">Dashboard</h1>
                <p style="margin: 0 0 0.75rem; color: var(--text-muted); font-size: 0.7rem;">Statistiche pacchetti</p>

                <!-- Stats Overview -->
                <div style="display: grid; grid-template-columns: repeat(3, 1fr); gap: 0.5rem; margin-bottom: 0.75rem;">
                    <div class="feature-card" style="text-align: center; padding: 0.5rem;">
                        <div style="font-size: 1.25rem; font-weight: 700; color: var(--accent);">
                            <?= $totalStats['total_packages'] ?? 0 ?>
                        </div>
                        <div style="color: var(--text-muted); font-size: 0.65rem;">Pacchetti</div>
                    </div>

                    <div class="feature-card" style="text-align: center; padding: 0.5rem;">
                        <div style="font-size: 1.25rem; font-weight: 700; color: var(--accent);">
                            <?= $totalStats['total_downloads'] ?? 0 ?>
                        </div>
                        <div style="color: var(--text-muted); font-size: 0.65rem;">Download</div>
                    </div>

                    <div class="feature-card" style="text-align: center; padding: 0.5rem;">
                        <div style="font-size: 1.25rem; font-weight: 700; color: var(--accent);">
                            <?= $totalStats['total_users'] ?? 0 ?>
                        </div>
                        <div style="color: var(--text-muted); font-size: 0.65rem;">Studenti</div>
                    </div>
                </div>

                <!-- Package List -->
                <h3 style="margin: 0 0 0.4rem; color: var(--text-muted); font-size: 0.8rem;">I Tuoi Pacchetti</h3>

                <?php if (empty($packages)): ?>
                    <div class="alert alert-info" style="padding: 0.4rem; font-size: 0.75rem;">
                        <span class="alert-icon" style="font-size: 0.65rem;">i</span>
                        <div>
                            Nessun pacchetto. <a href="create.php" style="color: var(--accent);">Crea</a>
                        </div>
                    </div>
                <?php else: ?>
                    <div class="packages-list" style="gap: 0.4rem;">
                        <?php foreach ($packages as $pkg): ?>
                            <a href="dashboard-package.php?id=<?= $pkg['uuid'] ?>" class="package-card-link" style="text-decoration: none;">
                                <div class="feature-card" style="cursor: pointer; padding: 0.5rem; transition: transform 0.2s, box-shadow 0.2s;">
                                    <div style="display: flex; justify-content: space-between; align-items: center; gap: 0.5rem;">
                                        <div style="min-width: 0; flex: 1;">
                                            <h4 style="margin: 0; color: var(--text-main); font-size: 0.8rem; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;"><?= e($pkg['name']) ?></h4>
                                            <div style="color: var(--text-muted); font-size: 0.65rem; display: flex; gap: 0.6rem; margin-top: 0.15rem;">
                                                <span><?= $pkg['total_questions'] ?>q</span>
                                                <span><strong style="color: var(--accent);"><?= $pkg['unique_users'] ?></strong> studenti</span>
                                                <span><strong style="color: var(--accent);"><?= $pkg['total_downloads'] ?></strong> dl</span>
                                            </div>
                                        </div>
                                        <span class="package-badge <?= $pkg['status'] === 'published' ? 'badge-text' : 'badge-audio' ?>" style="font-size: 0.55rem; padding: 0.15rem 0.35rem;">
                                            <?= $pkg['status'] === 'published' ? 'PUB' : 'BOZZA' ?>
                                        </span>
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
