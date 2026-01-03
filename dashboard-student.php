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

// Get study sessions for this student on this package (last 30 days)
$studySessions = db()->fetchAll(
    "SELECT * FROM study_sessions
     WHERE user_id = ? AND package_id = ? AND started_at >= DATE_SUB(NOW(), INTERVAL 30 DAY)
     ORDER BY started_at DESC",
    [$studentId, $package['id']]
);

// Get daily stats for this student on this package (last 14 days)
$dailyStats = db()->fetchAll(
    "SELECT * FROM daily_study_stats
     WHERE user_id = ? AND package_id = ? AND study_date >= DATE_SUB(CURDATE(), INTERVAL 14 DAY)
     ORDER BY study_date DESC",
    [$studentId, $package['id']]
);

// Calculate session stats
$totalStudyTime = array_sum(array_column($studySessions, 'duration_seconds'));
$avgSessionDuration = count($studySessions) > 0 ? $totalStudyTime / count($studySessions) : 0;

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
                <div style="display: flex; align-items: center; gap: 0.5rem; margin-bottom: 0.75rem;">
                    <div class="user-avatar" style="width: 36px; height: 36px; font-size: 0.9rem;">
                        <?= strtoupper(substr($student['username'], 0, 1)) ?>
                    </div>
                    <div>
                        <h1 style="margin: 0; font-size: 1rem; color: var(--text-main);">
                            <?= e($student['username']) ?>
                        </h1>
                        <p style="margin: 0; color: var(--text-muted); font-size: 0.7rem;">
                            <?= e($student['email']) ?>
                        </p>
                    </div>
                </div>

                <!-- Stats Overview -->
                <div style="display: grid; grid-template-columns: repeat(4, 1fr); gap: 0.4rem; margin-bottom: 0.75rem;">
                    <div class="feature-card" style="text-align: center; padding: 0.4rem;">
                        <div style="font-size: 1rem; font-weight: 700; color: var(--accent);">
                            <?= $progress['best_score'] ?? 0 ?>%
                        </div>
                        <div style="color: var(--text-muted); font-size: 0.6rem;">Score</div>
                    </div>

                    <div class="feature-card" style="text-align: center; padding: 0.4rem;">
                        <div style="font-size: 1rem; font-weight: 700; color: var(--accent);">
                            <?= $progress['attempts'] ?? 0 ?>
                        </div>
                        <div style="color: var(--text-muted); font-size: 0.6rem;">Tentativi</div>
                    </div>

                    <div class="feature-card" style="text-align: center; padding: 0.4rem;">
                        <div style="font-size: 1rem; font-weight: 700; color: var(--accent);">
                            <?= formatTime($progress['total_time_spent'] ?? 0) ?>
                        </div>
                        <div style="color: var(--text-muted); font-size: 0.6rem;">Tempo</div>
                    </div>

                    <div class="feature-card" style="text-align: center; padding: 0.4rem;">
                        <div style="font-size: 1rem; font-weight: 700; color: #22c55e;">
                            <?= $masteredQuestions ?>/<?= $totalQuestions ?>
                        </div>
                        <div style="color: var(--text-muted); font-size: 0.6rem;">Padronegg.</div>
                    </div>
                </div>

                <!-- Progress Summary -->
                <div class="feature-card" style="margin-bottom: 0.75rem; padding: 0.5rem;">
                    <div style="display: flex; gap: 0.25rem; height: 12px; border-radius: 3px; overflow: hidden; margin-bottom: 0.4rem;">
                        <?php
                        $masteredPct = $totalQuestions > 0 ? ($masteredQuestions / $totalQuestions) * 100 : 0;
                        $learningPct = $totalQuestions > 0 ? (($answeredQuestions - $masteredQuestions - $strugglingQuestions) / $totalQuestions) * 100 : 0;
                        $strugglingPct = $totalQuestions > 0 ? ($strugglingQuestions / $totalQuestions) * 100 : 0;
                        $notStartedPct = 100 - $masteredPct - $learningPct - $strugglingPct;
                        ?>
                        <div style="background: #22c55e; width: <?= $masteredPct ?>%;" title="Padroneggiati"></div>
                        <div style="background: #eab308; width: <?= $learningPct ?>%;" title="Apprendimento"></div>
                        <div style="background: #f97316; width: <?= $strugglingPct ?>%;" title="Difficolta"></div>
                        <div style="background: #374151; width: <?= $notStartedPct ?>%;" title="Non iniziati"></div>
                    </div>
                    <div style="display: flex; gap: 0.6rem; flex-wrap: wrap; font-size: 0.6rem;">
                        <span style="display: flex; align-items: center; gap: 0.2rem;"><span style="width: 8px; height: 8px; background: #22c55e; border-radius: 1px;"></span><?= $masteredQuestions ?> ok</span>
                        <span style="display: flex; align-items: center; gap: 0.2rem;"><span style="width: 8px; height: 8px; background: #eab308; border-radius: 1px;"></span>apprend.</span>
                        <span style="display: flex; align-items: center; gap: 0.2rem;"><span style="width: 8px; height: 8px; background: #f97316; border-radius: 1px;"></span><?= $strugglingQuestions ?> diff.</span>
                        <span style="display: flex; align-items: center; gap: 0.2rem;"><span style="width: 8px; height: 8px; background: #374151; border-radius: 1px;"></span>non init.</span>
                    </div>
                </div>

                <!-- Study Sessions History -->
                <?php if (!empty($dailyStats)): ?>
                <div class="feature-card" style="margin-bottom: 0.75rem; padding: 0.5rem;">
                    <h4 style="margin: 0 0 0.4rem; font-size: 0.75rem; color: var(--text-muted);">Attivita 14gg</h4>
                    <div style="overflow-x: auto; font-size: 0.65rem;">
                        <table style="width: 100%; border-collapse: collapse;">
                            <thead>
                                <tr style="border-bottom: 1px solid var(--border-subtle);">
                                    <th style="text-align: left; padding: 0.2rem; color: var(--text-muted);">Data</th>
                                    <th style="text-align: center; padding: 0.2rem; color: var(--text-muted);">N</th>
                                    <th style="text-align: center; padding: 0.2rem; color: var(--text-muted);">Dur.</th>
                                    <th style="text-align: center; padding: 0.2rem; color: var(--text-muted);">D</th>
                                    <th style="text-align: center; padding: 0.2rem; color: var(--text-muted);">%</th>
                                    <th style="text-align: center; padding: 0.2rem; color: var(--text-muted);">Orario</th>
                                </tr>
                            </thead>
                            <tbody>
                                <?php foreach ($dailyStats as $day): ?>
                                    <tr style="border-bottom: 1px solid var(--border-subtle);">
                                        <td style="padding: 0.2rem; color: var(--text-main);">
                                            <?= date('d/m', strtotime($day['study_date'])) ?>
                                        </td>
                                        <td style="text-align: center; padding: 0.2rem; color: var(--accent);">
                                            <?= $day['total_sessions'] ?>
                                        </td>
                                        <td style="text-align: center; padding: 0.2rem; color: var(--text-main);">
                                            <?= formatTime($day['total_duration_seconds']) ?>
                                        </td>
                                        <td style="text-align: center; padding: 0.2rem; color: var(--text-main);">
                                            <?= $day['total_questions'] ?>
                                        </td>
                                        <td style="text-align: center; padding: 0.2rem;">
                                            <?php
                                            $accuracy = $day['total_questions'] > 0
                                                ? round(($day['total_correct'] / $day['total_questions']) * 100)
                                                : 0;
                                            $color = $accuracy >= 70 ? '#22c55e' : ($accuracy >= 50 ? '#eab308' : '#ef4444');
                                            ?>
                                            <span style="color: <?= $color ?>; font-weight: 600;"><?= $accuracy ?>%</span>
                                        </td>
                                        <td style="text-align: center; padding: 0.2rem; color: var(--text-muted);">
                                            <?= $day['first_session_at'] ? substr($day['first_session_at'], 0, 5) : '' ?><?= ($day['first_session_at'] && $day['last_session_at'] && $day['first_session_at'] !== $day['last_session_at']) ? '-' . substr($day['last_session_at'], 0, 5) : '' ?>
                                        </td>
                                    </tr>
                                <?php endforeach; ?>
                            </tbody>
                        </table>
                    </div>
                    <?php if (count($dailyStats) > 0): ?>
                        <div style="margin-top: 0.3rem; padding-top: 0.3rem; border-top: 1px solid var(--border-subtle); display: flex; gap: 0.8rem; flex-wrap: wrap; font-size: 0.6rem; color: var(--text-muted);">
                            <span><?= count($dailyStats) ?>gg</span>
                            <span>Tot: <?= formatTime(array_sum(array_column($dailyStats, 'total_duration_seconds'))) ?></span>
                            <span>Media: <?= formatTime(round(array_sum(array_column($dailyStats, 'total_duration_seconds')) / count($dailyStats))) ?>/gg</span>
                        </div>
                    <?php endif; ?>
                </div>
                <?php endif; ?>

                <!-- Recent Sessions Detail -->
                <?php if (!empty($studySessions)): ?>
                <div class="feature-card" style="margin-bottom: 0.75rem; padding: 0.5rem;">
                    <h4 style="margin: 0 0 0.4rem; font-size: 0.75rem; color: var(--text-muted);">Sessioni recenti</h4>
                    <div style="display: flex; flex-direction: column; gap: 0.2rem; font-size: 0.65rem;">
                        <?php foreach (array_slice($studySessions, 0, 8) as $session): ?>
                            <div style="display: flex; justify-content: space-between; align-items: center; padding: 0.25rem 0.3rem; background: var(--bg-tertiary); border-radius: 3px; flex-wrap: wrap; gap: 0.2rem;">
                                <span style="color: var(--text-main);">
                                    <?= date('d/m', strtotime($session['started_at'])) ?>
                                    <span style="color: var(--text-muted);">
                                        <?= date('H:i', strtotime($session['started_at'])) ?><?php if ($session['ended_at']): ?>-<?= date('H:i', strtotime($session['ended_at'])) ?><?php endif; ?>
                                    </span>
                                </span>
                                <span style="display: flex; gap: 0.4rem; align-items: center;">
                                    <span style="color: var(--text-muted);"><?= formatTime($session['duration_seconds']) ?></span>
                                    <span style="color: var(--text-muted);"><?= $session['questions_answered'] ?>q</span>
                                    <?php if ($session['questions_answered'] > 0):
                                        $sessAccuracy = round(($session['correct_answers'] / $session['questions_answered']) * 100);
                                        $sessColor = $sessAccuracy >= 70 ? '#22c55e' : ($sessAccuracy >= 50 ? '#eab308' : '#ef4444');
                                    ?>
                                        <span style="color: <?= $sessColor ?>; font-weight: 600;"><?= $sessAccuracy ?>%</span>
                                    <?php endif; ?>
                                    <span style="font-size: 0.55rem; padding: 0.1rem 0.25rem; border-radius: 2px; background: <?= $session['status'] === 'completed' ? '#22c55e22' : ($session['status'] === 'active' ? '#3b82f622' : '#ef444422') ?>; color: <?= $session['status'] === 'completed' ? '#22c55e' : ($session['status'] === 'active' ? '#3b82f6' : '#ef4444') ?>;">
                                        <?= $session['status'] === 'completed' ? 'OK' : ($session['status'] === 'active' ? 'LIVE' : 'X') ?>
                                    </span>
                                </span>
                            </div>
                        <?php endforeach; ?>
                    </div>
                    <?php if (count($studySessions) > 8): ?>
                        <p style="text-align: center; color: var(--text-muted); margin: 0.3rem 0 0; font-size: 0.55rem;">+<?= count($studySessions) - 8 ?> altre</p>
                    <?php endif; ?>
                </div>
                <?php endif; ?>

                <!-- Question by Question Progress -->
                <h4 style="margin: 0.5rem 0 0.3rem; color: var(--text-muted); font-size: 0.75rem;">Domande</h4>

                <?php if (empty($questions)): ?>
                    <div class="alert alert-info" style="padding: 0.3rem; font-size: 0.7rem;">
                        <span class="alert-icon" style="font-size: 0.6rem;">i</span>
                        <div>Impossibile caricare le domande.</div>
                    </div>
                <?php else: ?>
                    <div class="questions-progress-list" style="font-size: 0.65rem;">
                        <?php foreach ($questions as $idx => $q):
                            $qProgress = $progressByIndex[$idx] ?? null;
                            $score = $qProgress['score'] ?? 0;
                            $streak = $qProgress['streak'] ?? 0;
                            $nextReview = $qProgress['next_review_date'] ?? null;
                        ?>
                            <div style="background: var(--bg-secondary); border-radius: 4px; padding: 0.3rem 0.4rem; margin-bottom: 0.25rem; border-left: 3px solid <?= getScoreColor($score) ?>;">
                                <div style="display: flex; justify-content: space-between; align-items: center; gap: 0.3rem;">
                                    <div style="flex: 1; min-width: 0;">
                                        <span style="font-weight: 600; color: var(--accent);">D<?= $idx + 1 ?></span>
                                        <span style="color: var(--text-main); margin-left: 0.3rem; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; display: inline-block; max-width: 70%; vertical-align: middle;">
                                            <?= e(mb_substr($q['question'] ?? '', 0, 60)) ?><?= mb_strlen($q['question'] ?? '') > 60 ? '...' : '' ?>
                                        </span>
                                    </div>
                                    <div style="display: flex; align-items: center; gap: 0.3rem; flex-shrink: 0;">
                                        <span style="font-weight: 700; color: <?= getScoreColor($score) ?>;"><?= $score ?>/5</span>
                                        <?php if ($streak > 0): ?><span style="color: var(--text-muted);">x<?= $streak ?></span><?php endif; ?>
                                    </div>
                                </div>
                            </div>
                        <?php endforeach; ?>
                    </div>
                <?php endif; ?>

                <div style="margin-top: 0.75rem; display: flex; gap: 0.5rem;">
                    <a href="dashboard-package.php?id=<?= $package['uuid'] ?>" class="btn-ghost" style="padding: 0.25rem 0.5rem; font-size: 0.65rem;">← Studenti</a>
                    <a href="dashboard.php" class="btn-ghost" style="padding: 0.25rem 0.5rem; font-size: 0.65rem;">← Dashboard</a>
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
