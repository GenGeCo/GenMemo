<?php
/**
 * Dashboard - Dettaglio progressi singolo studente
 */
require_once __DIR__ . '/includes/init.php';

Auth::requireLogin();
$user = Auth::user();

$packageId = $_GET['package'] ?? '';
$studentId = (int)($_GET['user'] ?? 0);

// Get package (must be owned by logged-in user)
$package = db()->fetch(
    "SELECT * FROM packages WHERE uuid = ? AND user_id = ?",
    [$packageId, $user['id']]
);

if (!$package) {
    header('Location: dashboard.php');
    exit;
}

// Get student info
$student = db()->fetch(
    "SELECT id, username, email FROM users WHERE id = ?",
    [$studentId]
);

if (!$student) {
    header('Location: dashboard-package.php?id=' . $packageId);
    exit;
}

// Get overall progress for this package
$progress = db()->fetch(
    "SELECT * FROM user_progress WHERE user_id = ? AND package_id = ?",
    [$studentId, $package['id']]
);

// Get question-by-question progress
$questionProgress = db()->fetchAll(
    "SELECT * FROM user_question_progress
     WHERE user_id = ? AND package_id = ?
     ORDER BY question_index ASC",
    [$studentId, $package['id']]
);

// Load package JSON for question texts
$questions = [];
if ($package['json_file_key']) {
    $jsonPath = __DIR__ . '/uploads/' . $package['json_file_key'];
    if (file_exists($jsonPath)) {
        $packageJson = json_decode(file_get_contents($jsonPath), true);
        $questions = $packageJson['questions'] ?? [];
    }
}

// Map progress by question index
$progressByIndex = [];
foreach ($questionProgress as $qp) {
    $progressByIndex[$qp['question_index']] = $qp;
}

// Calculate stats
$totalQuestions = $package['total_questions'];
$answeredQuestions = count($questionProgress);
$masteredQuestions = count(array_filter($questionProgress, fn($q) => $q['score'] >= 4));
$strugglingQuestions = count(array_filter($questionProgress, fn($q) => $q['score'] <= 1 && $q['score'] > 0));

function formatTime($seconds) {
    if (!$seconds) return '-';
    if ($seconds < 60) return $seconds . 's';
    if ($seconds < 3600) return floor($seconds / 60) . 'm ' . ($seconds % 60) . 's';
    return floor($seconds / 3600) . 'h ' . floor(($seconds % 3600) / 60) . 'm';
}

function getScoreColor($score) {
    if ($score >= 4) return '#22c55e'; // Green - mastered
    if ($score >= 2) return '#eab308'; // Yellow - learning
    if ($score >= 1) return '#f97316'; // Orange - struggling
    return '#6b7280'; // Gray - not started
}

function getScoreLabel($score) {
    if ($score >= 5) return 'Padroneggiato';
    if ($score >= 4) return 'Quasi padroneggiato';
    if ($score >= 3) return 'In progresso';
    if ($score >= 2) return 'Apprendimento';
    if ($score >= 1) return 'Difficolta';
    return 'Non iniziato';
}
?>
<!DOCTYPE html>
<html lang="it">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Progressi <?= e($student['username']) ?> - GenMemo</title>
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
                <a href="dashboard.php" style="color: var(--text-muted); text-decoration: none;">Dashboard</a>
                <span style="color: var(--text-muted);"> / </span>
                <a href="dashboard-package.php?id=<?= $package['uuid'] ?>" style="color: var(--text-muted); text-decoration: none;">
                    <?= e($package['name']) ?>
                </a>
                <span style="color: var(--text-muted);"> / </span>
                <span style="color: var(--text-main);"><?= e($student['username']) ?></span>
            </div>

            <!-- Student Info -->
            <section class="section">
                <div style="display: flex; align-items: center; gap: 1rem; margin-bottom: 2rem;">
                    <div class="user-avatar" style="width: 64px; height: 64px; font-size: 1.5rem;">
                        <?= strtoupper(substr($student['username'], 0, 1)) ?>
                    </div>
                    <div>
                        <h1 class="section-title" style="margin: 0; justify-content: flex-start;">
                            <?= e($student['username']) ?>
                        </h1>
                        <p style="margin: 0.25rem 0 0; color: var(--text-muted);">
                            <?= e($student['email']) ?>
                        </p>
                    </div>
                </div>

                <!-- Stats Overview -->
                <div class="grid-4" style="margin-bottom: 2rem; display: grid; grid-template-columns: repeat(4, 1fr); gap: 1rem;">
                    <div class="feature-card" style="text-align: center; padding: 1rem;">
                        <div style="font-size: 1.5rem; font-weight: 700; color: var(--accent);">
                            <?= $progress['best_score'] ?? 0 ?>%
                        </div>
                        <div style="color: var(--text-muted); font-size: 0.85rem;">Miglior Score</div>
                    </div>

                    <div class="feature-card" style="text-align: center; padding: 1rem;">
                        <div style="font-size: 1.5rem; font-weight: 700; color: var(--accent);">
                            <?= $progress['attempts'] ?? 0 ?>
                        </div>
                        <div style="color: var(--text-muted); font-size: 0.85rem;">Tentativi</div>
                    </div>

                    <div class="feature-card" style="text-align: center; padding: 1rem;">
                        <div style="font-size: 1.5rem; font-weight: 700; color: var(--accent);">
                            <?= formatTime($progress['total_time_spent'] ?? 0) ?>
                        </div>
                        <div style="color: var(--text-muted); font-size: 0.85rem;">Tempo Studio</div>
                    </div>

                    <div class="feature-card" style="text-align: center; padding: 1rem;">
                        <div style="font-size: 1.5rem; font-weight: 700; color: #22c55e;">
                            <?= $masteredQuestions ?>/<?= $totalQuestions ?>
                        </div>
                        <div style="color: var(--text-muted); font-size: 0.85rem;">Padroneggiati</div>
                    </div>
                </div>

                <!-- Progress Summary -->
                <div class="feature-card" style="margin-bottom: 2rem;">
                    <h3 style="margin: 0 0 1rem;">Riepilogo Progressi</h3>

                    <div style="display: flex; gap: 0.5rem; height: 24px; border-radius: 4px; overflow: hidden; margin-bottom: 1rem;">
                        <?php
                        $masteredPct = $totalQuestions > 0 ? ($masteredQuestions / $totalQuestions) * 100 : 0;
                        $learningPct = $totalQuestions > 0 ? (($answeredQuestions - $masteredQuestions - $strugglingQuestions) / $totalQuestions) * 100 : 0;
                        $strugglingPct = $totalQuestions > 0 ? ($strugglingQuestions / $totalQuestions) * 100 : 0;
                        $notStartedPct = 100 - $masteredPct - $learningPct - $strugglingPct;
                        ?>
                        <div style="background: #22c55e; width: <?= $masteredPct ?>%; transition: width 0.3s;" title="Padroneggiati: <?= $masteredQuestions ?>"></div>
                        <div style="background: #eab308; width: <?= $learningPct ?>%; transition: width 0.3s;" title="In apprendimento"></div>
                        <div style="background: #f97316; width: <?= $strugglingPct ?>%; transition: width 0.3s;" title="Difficolta: <?= $strugglingQuestions ?>"></div>
                        <div style="background: #374151; width: <?= $notStartedPct ?>%; transition: width 0.3s;" title="Non iniziati"></div>
                    </div>

                    <div style="display: flex; gap: 1.5rem; flex-wrap: wrap; font-size: 0.85rem;">
                        <div style="display: flex; align-items: center; gap: 0.5rem;">
                            <span style="width: 12px; height: 12px; background: #22c55e; border-radius: 2px;"></span>
                            <span style="color: var(--text-muted);">Padroneggiati (<?= $masteredQuestions ?>)</span>
                        </div>
                        <div style="display: flex; align-items: center; gap: 0.5rem;">
                            <span style="width: 12px; height: 12px; background: #eab308; border-radius: 2px;"></span>
                            <span style="color: var(--text-muted);">In apprendimento</span>
                        </div>
                        <div style="display: flex; align-items: center; gap: 0.5rem;">
                            <span style="width: 12px; height: 12px; background: #f97316; border-radius: 2px;"></span>
                            <span style="color: var(--text-muted);">Difficolta (<?= $strugglingQuestions ?>)</span>
                        </div>
                        <div style="display: flex; align-items: center; gap: 0.5rem;">
                            <span style="width: 12px; height: 12px; background: #374151; border-radius: 2px;"></span>
                            <span style="color: var(--text-muted);">Non iniziati</span>
                        </div>
                    </div>
                </div>

                <!-- Question by Question Progress -->
                <h2 style="margin-bottom: 1rem; color: var(--text-secondary);">Dettaglio Domande</h2>

                <?php if (empty($questions)): ?>
                    <div class="alert alert-info">
                        <span class="alert-icon">i</span>
                        <div>Impossibile caricare le domande del pacchetto.</div>
                    </div>
                <?php else: ?>
                    <div class="questions-progress-list">
                        <?php foreach ($questions as $idx => $q):
                            $qProgress = $progressByIndex[$idx] ?? null;
                            $score = $qProgress['score'] ?? 0;
                            $streak = $qProgress['streak'] ?? 0;
                            $nextReview = $qProgress['next_review_date'] ?? null;
                        ?>
                            <div class="question-progress-card" style="
                                background: var(--bg-secondary);
                                border-radius: 8px;
                                padding: 1rem;
                                margin-bottom: 0.75rem;
                                border-left: 4px solid <?= getScoreColor($score) ?>;
                            ">
                                <div style="display: flex; justify-content: space-between; align-items: flex-start; gap: 1rem;">
                                    <div style="flex: 1;">
                                        <div style="display: flex; align-items: center; gap: 0.5rem; margin-bottom: 0.5rem;">
                                            <span style="font-weight: 600; color: var(--accent);">D<?= $idx + 1 ?></span>
                                            <span style="
                                                font-size: 0.75rem;
                                                padding: 0.15rem 0.5rem;
                                                border-radius: 4px;
                                                background: <?= getScoreColor($score) ?>22;
                                                color: <?= getScoreColor($score) ?>;
                                            "><?= getScoreLabel($score) ?></span>
                                        </div>
                                        <p style="margin: 0; color: var(--text-main); font-size: 0.9rem;">
                                            <?= e(mb_substr($q['question'] ?? '', 0, 150)) ?><?= mb_strlen($q['question'] ?? '') > 150 ? '...' : '' ?>
                                        </p>
                                    </div>
                                    <div style="text-align: right; min-width: 100px;">
                                        <div style="font-size: 1.25rem; font-weight: 700; color: <?= getScoreColor($score) ?>;">
                                            <?= $score ?>/5
                                        </div>
                                        <?php if ($streak > 0): ?>
                                            <div style="font-size: 0.75rem; color: var(--text-muted);">
                                                Serie: <?= $streak ?>
                                            </div>
                                        <?php endif; ?>
                                        <?php if ($nextReview): ?>
                                            <div style="font-size: 0.7rem; color: var(--text-muted); margin-top: 0.25rem;">
                                                Prossima: <?= date('d/m', strtotime($nextReview)) ?>
                                            </div>
                                        <?php endif; ?>
                                    </div>
                                </div>
                            </div>
                        <?php endforeach; ?>
                    </div>
                <?php endif; ?>

                <div style="margin-top: 2rem; display: flex; gap: 1rem;">
                    <a href="dashboard-package.php?id=<?= $package['uuid'] ?>" class="btn-ghost">
                        Torna ai Studenti
                    </a>
                    <a href="dashboard.php" class="btn-ghost">
                        Torna alla Dashboard
                    </a>
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
