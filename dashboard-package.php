<?php
/**
 * Dashboard - Dettaglio pacchetto con lista studenti
 */
require_once __DIR__ . '/includes/init.php';

Auth::requireLogin();
$user = Auth::user();

$packageId = $_GET['id'] ?? '';

// Get package (must be owned by user)
$package = db()->fetch(
    "SELECT * FROM packages WHERE uuid = ? AND user_id = ?",
    [$packageId, $user['id']]
);

if (!$package) {
    header('Location: dashboard.php');
    exit;
}

// Get users who have progress on this package (from user_progress OR user_question_progress)
$students = db()->fetchAll(
    "SELECT
        u.id,
        u.username,
        u.email,
        up.last_score,
        up.best_score,
        up.attempts,
        up.total_time_spent,
        COALESCE(up.last_played_at, uqp_stats.last_activity) as last_played_at,
        COALESCE(uqp_stats.questions_answered, 0) as questions_answered,
        uqp_stats.avg_score
     FROM users u
     LEFT JOIN user_progress up ON u.id = up.user_id AND up.package_id = ?
     LEFT JOIN (
        SELECT
            user_id,
            COUNT(*) as questions_answered,
            AVG(score) as avg_score,
            MAX(updated_at) as last_activity
        FROM user_question_progress
        WHERE package_id = ?
        GROUP BY user_id
     ) uqp_stats ON u.id = uqp_stats.user_id
     WHERE up.package_id = ? OR uqp_stats.user_id IS NOT NULL
     ORDER BY COALESCE(up.last_played_at, uqp_stats.last_activity) DESC",
    [$package['id'], $package['id'], $package['id']]
);

// Also get users who downloaded but may not have progress yet
$downloaders = db()->fetchAll(
    "SELECT DISTINCT
        u.id,
        u.username,
        u.email,
        MAX(pd.downloaded_at) as last_download
     FROM users u
     INNER JOIN package_downloads pd ON u.id = pd.user_id
     WHERE pd.package_id = ?
     GROUP BY u.id, u.username, u.email
     ORDER BY last_download DESC",
    [$package['id']]
);

// Merge: students with progress + downloaders without progress
$studentIds = array_column($students, 'id');
foreach ($downloaders as $dl) {
    if (!in_array($dl['id'], $studentIds)) {
        $students[] = [
            'id' => $dl['id'],
            'username' => $dl['username'],
            'email' => $dl['email'],
            'last_score' => null,
            'best_score' => null,
            'attempts' => 0,
            'total_time_spent' => 0,
            'last_played_at' => null,
            'questions_answered' => 0,
            'avg_score' => null,
            'only_downloaded' => true,
            'last_download' => $dl['last_download']
        ];
    }
}

// Calculate package stats
$totalStudents = count($students);
$activeStudents = count(array_filter($students, fn($s) => ($s['attempts'] ?? 0) > 0));
$avgBestScore = 0;
if ($activeStudents > 0) {
    $scores = array_filter(array_column($students, 'best_score'), fn($s) => $s !== null);
    $avgBestScore = count($scores) > 0 ? round(array_sum($scores) / count($scores)) : 0;
}

// Get session statistics by type
$sessionStats = db()->fetch(
    "SELECT
        COUNT(*) as total_sessions,
        SUM(CASE WHEN session_type = 'quiz' OR session_type IS NULL THEN 1 ELSE 0 END) as quiz_sessions,
        SUM(CASE WHEN session_type = 'review' THEN 1 ELSE 0 END) as review_sessions,
        SUM(CASE WHEN session_type = 'infinite' THEN 1 ELSE 0 END) as infinite_sessions,
        SUM(rounds_completed) as total_rounds,
        MAX(best_streak) as overall_best_streak,
        SUM(duration_seconds) as total_study_time
     FROM study_sessions
     WHERE package_id = ? AND status = 'completed'",
    [$package['id']]
);

function formatTime($seconds) {
    if (!$seconds) return '-';
    if ($seconds < 60) return $seconds . 's';
    if ($seconds < 3600) return floor($seconds / 60) . 'm ' . ($seconds % 60) . 's';
    return floor($seconds / 3600) . 'h ' . floor(($seconds % 3600) / 60) . 'm';
}
?>
<!DOCTYPE html>
<html lang="it">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Dashboard - <?= e($package['name']) ?> - GenMemo</title>
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

            <!-- Breadcrumb -->
            <div style="margin-bottom: 1rem;">
                <a href="dashboard.php" style="color: var(--text-muted); text-decoration: none;">
                    Dashboard
                </a>
                <span style="color: var(--text-muted);"> / </span>
                <span style="color: var(--text-main);"><?= e($package['name']) ?></span>
            </div>

            <!-- Package Info -->
            <section class="section">
                <h1 class="section-title"><?= e($package['name']) ?></h1>
                <p class="section-subtitle">
                    <?= $package['total_questions'] ?> domande
                    <?php if ($package['topic']): ?>
                        - <?= e($package['topic']) ?>
                    <?php endif; ?>
                </p>

                <!-- Stats Overview -->
                <div style="display: grid; grid-template-columns: repeat(3, 1fr); gap: 0.75rem; margin-bottom: 1rem;">
                    <div class="feature-card" style="text-align: center; padding: 0.75rem;">
                        <div style="font-size: 1.5rem; font-weight: 700; color: var(--accent);">
                            <?= $totalStudents ?>
                        </div>
                        <div style="color: var(--text-muted); font-size: 0.85rem;">Studenti</div>
                    </div>

                    <div class="feature-card" style="text-align: center; padding: 0.75rem;">
                        <div style="font-size: 1.5rem; font-weight: 700; color: var(--accent);">
                            <?= $activeStudents ?>
                        </div>
                        <div style="color: var(--text-muted); font-size: 0.85rem;">Attivi</div>
                    </div>

                    <div class="feature-card" style="text-align: center; padding: 0.75rem;">
                        <div style="font-size: 1.5rem; font-weight: 700; color: var(--accent);">
                            <?= $avgBestScore ?>%
                        </div>
                        <div style="color: var(--text-muted); font-size: 0.85rem;">Score Medio</div>
                    </div>
                </div>

                <!-- Session Type Stats -->
                <?php if (($sessionStats['total_sessions'] ?? 0) > 0): ?>
                <div class="feature-card" style="padding: 0.75rem; margin-bottom: 1rem;">
                    <h4 style="margin: 0 0 0.5rem; color: var(--text-secondary); font-size: 0.9rem;">Sessioni di Studio</h4>
                    <div style="display: flex; gap: 0.5rem; flex-wrap: wrap; margin-bottom: 0.5rem;">
                        <span style="font-size: 0.8rem; padding: 0.3rem 0.6rem; border-radius: 4px; background: #3b82f620; color: #3b82f6;">
                            ▶ Quiz: <?= $sessionStats['quiz_sessions'] ?? 0 ?>
                        </span>
                        <?php if (($sessionStats['review_sessions'] ?? 0) > 0): ?>
                        <span style="font-size: 0.8rem; padding: 0.3rem 0.6rem; border-radius: 4px; background: #f5920020; color: #f59200;">
                            ↻ Ripasso: <?= $sessionStats['review_sessions'] ?>
                        </span>
                        <?php endif; ?>
                        <?php if (($sessionStats['infinite_sessions'] ?? 0) > 0): ?>
                        <span style="font-size: 0.8rem; padding: 0.3rem 0.6rem; border-radius: 4px; background: #8b5cf620; color: #8b5cf6;">
                            ∞ Infinito: <?= $sessionStats['infinite_sessions'] ?>
                        </span>
                        <?php endif; ?>
                    </div>
                    <div style="display: flex; gap: 1rem; flex-wrap: wrap; font-size: 0.8rem; color: var(--text-muted);">
                        <span>Totale: <strong><?= $sessionStats['total_sessions'] ?></strong> sessioni</span>
                        <span>Tempo: <strong><?= formatTime($sessionStats['total_study_time'] ?? 0) ?></strong></span>
                        <?php if (($sessionStats['infinite_sessions'] ?? 0) > 0): ?>
                        <span style="color: #8b5cf6;">Rounds: <strong><?= $sessionStats['total_rounds'] ?? 0 ?></strong></span>
                        <span style="color: #22c55e;">Best Streak: <strong><?= $sessionStats['overall_best_streak'] ?? 0 ?></strong></span>
                        <?php endif; ?>
                    </div>
                </div>
                <?php endif; ?>

                <!-- Students List -->
                <h3 style="margin-bottom: 0.5rem; color: var(--text-secondary);">Studenti</h3>

                <?php if (empty($students)): ?>
                    <div class="alert alert-info">
                        <span class="alert-icon">i</span>
                        <div>Nessuno ha ancora scaricato o usato questo pacchetto.</div>
                    </div>
                <?php else: ?>
                    <div class="table-container" style="overflow-x: auto;">
                        <table style="width: 100%; border-collapse: collapse; font-size: 0.85rem;">
                            <thead>
                                <tr style="border-bottom: 1px solid var(--border-subtle);">
                                    <th style="text-align: left; padding: 0.4rem 0.5rem; color: var(--text-muted);">Studente</th>
                                    <th style="text-align: center; padding: 0.4rem 0.5rem; color: var(--text-muted);">Score</th>
                                    <th style="text-align: center; padding: 0.4rem 0.5rem; color: var(--text-muted);">Best</th>
                                    <th style="text-align: center; padding: 0.4rem 0.5rem; color: var(--text-muted);">N.</th>
                                    <th style="text-align: center; padding: 0.4rem 0.5rem; color: var(--text-muted);">Tempo</th>
                                    <th style="text-align: center; padding: 0.4rem 0.5rem; color: var(--text-muted);">Attivita</th>
                                    <th style="text-align: center; padding: 0.4rem 0.5rem; color: var(--text-muted);"></th>
                                </tr>
                            </thead>
                            <tbody>
                                <?php foreach ($students as $student): ?>
                                    <tr style="border-bottom: 1px solid var(--border-subtle);">
                                        <td style="padding: 0.4rem 0.5rem;">
                                            <div style="display: flex; align-items: center; gap: 0.4rem;">
                                                <div class="user-avatar" style="width: 26px; height: 26px; font-size: 0.7rem; flex-shrink: 0;">
                                                    <?= strtoupper(substr($student['username'], 0, 1)) ?>
                                                </div>
                                                <span style="font-weight: 500; color: var(--text-main);">
                                                    <?= e($student['username']) ?>
                                                </span>
                                            </div>
                                        </td>
                                        <td style="text-align: center; padding: 0.4rem 0.5rem;">
                                            <?php if ($student['last_score'] !== null): ?>
                                                <span style="color: <?= $student['last_score'] >= 70 ? '#22c55e' : ($student['last_score'] >= 50 ? '#eab308' : '#ef4444') ?>; font-weight: 600;">
                                                    <?= $student['last_score'] ?>%
                                                </span>
                                            <?php else: ?>
                                                <span style="color: var(--text-muted);">-</span>
                                            <?php endif; ?>
                                        </td>
                                        <td style="text-align: center; padding: 0.4rem 0.5rem;">
                                            <?php if ($student['best_score'] !== null): ?>
                                                <span style="color: <?= $student['best_score'] >= 70 ? '#22c55e' : ($student['best_score'] >= 50 ? '#eab308' : '#ef4444') ?>; font-weight: 600;">
                                                    <?= $student['best_score'] ?>%
                                                </span>
                                            <?php else: ?>
                                                <span style="color: var(--text-muted);">-</span>
                                            <?php endif; ?>
                                        </td>
                                        <td style="text-align: center; padding: 0.4rem 0.5rem; color: var(--text-main);">
                                            <?= $student['attempts'] ?? 0 ?>
                                        </td>
                                        <td style="text-align: center; padding: 0.4rem 0.5rem; color: var(--text-muted);">
                                            <?= formatTime($student['total_time_spent'] ?? 0) ?>
                                        </td>
                                        <td style="text-align: center; padding: 0.4rem 0.5rem; color: var(--text-muted);">
                                            <?php if (!empty($student['last_played_at'])): ?>
                                                <?= timeAgo($student['last_played_at']) ?>
                                            <?php elseif (!empty($student['last_download'])): ?>
                                                <span style="font-size: 0.8rem;">download</span>
                                            <?php else: ?>
                                                -
                                            <?php endif; ?>
                                        </td>
                                        <td style="text-align: center; padding: 0.4rem 0.5rem;">
                                            <a href="dashboard-student.php?package=<?= $package['uuid'] ?>&user=<?= $student['id'] ?>"
                                               class="btn-secondary btn-small">
                                                Dettagli
                                            </a>
                                        </td>
                                    </tr>
                                <?php endforeach; ?>
                            </tbody>
                        </table>
                    </div>
                <?php endif; ?>

                <div style="margin-top: 1.5rem;">
                    <a href="dashboard.php" class="btn-ghost">← Dashboard</a>
                </div>
            </section>

            <!-- Footer -->
            <footer class="footer">
                <p>GenMemo &copy; <?= date('Y') ?> - Powered by GenGeCo</p>
            </footer>
        </div>
    </div>
</body>
</html>
