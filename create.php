<?php
require_once __DIR__ . '/includes/init.php';

Auth::requireLogin();
$user = Auth::user();

// Current step
$step = (int) ($_GET['step'] ?? 1);
$packageId = $_GET['package'] ?? null;

// Handle form submission
$error = null;
$package = null;

if ($packageId) {
    $package = db()->fetch(
        "SELECT * FROM packages WHERE uuid = ? AND user_id = ?",
        [$packageId, $user['id']]
    );
}

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    if (!Auth::verifyCsrf($_POST['csrf_token'] ?? '')) {
        $error = 'Invalid request. Please try again.';
    } else {
        $action = $_POST['action'] ?? '';

        // Step 1: Package settings
        if ($action === 'save_settings') {
            $name = trim($_POST['name'] ?? '');
            $description = trim($_POST['description'] ?? '');
            $topic = trim($_POST['topic'] ?? '');
            $questionTypes = $_POST['question_types'] ?? ['text'];
            $answerTypes = $_POST['answer_types'] ?? ['text'];
            $totalQuestions = max(1, min(100, (int) ($_POST['total_questions'] ?? 10)));
            $ttsLang = $_POST['tts_lang'] ?? 'it-IT';

            if (empty($name)) {
                $error = 'Il nome del pacchetto e obbligatorio.';
            } else {
                $uuid = randomString(16);
                $packageData = [
                    'user_id' => $user['id'],
                    'uuid' => $uuid,
                    'name' => $name,
                    'description' => $description,
                    'topic' => $topic,
                    'question_types' => json_encode($questionTypes),
                    'answer_types' => json_encode($answerTypes),
                    'total_questions' => $totalQuestions,
                    'tts_lang' => $ttsLang,
                    'status' => 'draft',
                    'created_at' => date('Y-m-d H:i:s')
                ];

                if ($package) {
                    // Update existing
                    db()->update('packages', $packageData, 'id = ?', [$package['id']]);
                } else {
                    // Create new
                    db()->insert('packages', $packageData);
                }

                header("Location: create.php?step=2&package=$uuid");
                exit;
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
    <title>Crea Pacchetto - GenMemo</title>
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
                    <a href="create.php" class="nav-link active">Crea</a>
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

            <!-- Wizard Steps -->
            <div class="wizard-steps">
                <div class="wizard-step <?= $step >= 1 ? 'active' : '' ?> <?= $step > 1 ? 'completed' : '' ?>">
                    <span class="wizard-step-number"><?= $step > 1 ? 'OK' : '1' ?></span>
                    <span class="wizard-step-label">Configurazione</span>
                </div>
                <div class="wizard-connector <?= $step > 1 ? 'completed' : '' ?>"></div>
                <div class="wizard-step <?= $step >= 2 ? 'active' : '' ?> <?= $step > 2 ? 'completed' : '' ?>">
                    <span class="wizard-step-number"><?= $step > 2 ? 'OK' : '2' ?></span>
                    <span class="wizard-step-label">Metodo</span>
                </div>
                <div class="wizard-connector <?= $step > 2 ? 'completed' : '' ?>"></div>
                <div class="wizard-step <?= $step >= 3 ? 'active' : '' ?> <?= $step > 3 ? 'completed' : '' ?>">
                    <span class="wizard-step-number"><?= $step > 3 ? 'OK' : '3' ?></span>
                    <span class="wizard-step-label">Domande</span>
                </div>
                <div class="wizard-connector <?= $step > 3 ? 'completed' : '' ?>"></div>
                <div class="wizard-step <?= $step >= 4 ? 'active' : '' ?>">
                    <span class="wizard-step-number">4</span>
                    <span class="wizard-step-label">Media</span>
                </div>
            </div>

            <!-- Step Content -->
            <section class="section">
                <?php if ($error): ?>
                    <div class="alert alert-error">
                        <span class="alert-icon">!</span>
                        <?= e($error) ?>
                    </div>
                <?php endif; ?>

                <?php if ($step === 1): ?>
                    <!-- STEP 1: Package Configuration -->
                    <h2 class="section-title">Configura il Pacchetto</h2>
                    <p class="section-subtitle">Definisci le impostazioni base del tuo pacchetto quiz</p>

                    <form method="POST" action="" style="max-width: 600px; margin: 0 auto;">
                        <input type="hidden" name="csrf_token" value="<?= Auth::csrfToken() ?>">
                        <input type="hidden" name="action" value="save_settings">

                        <div class="form-group">
                            <label class="form-label" for="name">Nome del Pacchetto *</label>
                            <input
                                type="text"
                                id="name"
                                name="name"
                                class="form-input"
                                placeholder="Es: Inglese Base - Vocabolario"
                                value="<?= e($package['name'] ?? '') ?>"
                                required
                            >
                        </div>

                        <div class="form-group">
                            <label class="form-label" for="description">Descrizione</label>
                            <textarea
                                id="description"
                                name="description"
                                class="form-input form-textarea"
                                placeholder="Descrivi il contenuto del pacchetto..."
                            ><?= e($package['description'] ?? '') ?></textarea>
                        </div>

                        <div class="form-group">
                            <label class="form-label" for="topic">Argomento</label>
                            <input
                                type="text"
                                id="topic"
                                name="topic"
                                class="form-input"
                                placeholder="Es: Inglese, Geografia, Storia dei Papi..."
                                value="<?= e($package['topic'] ?? '') ?>"
                            >
                            <p class="form-hint">Usato per la generazione AI e la ricerca</p>
                        </div>

                        <div class="form-group">
                            <label class="form-label">Tipo di Domande</label>
                            <p class="form-hint">Seleziona uno o piu tipi</p>
                            <div class="checkbox-group">
                                <?php
                                $savedQTypes = $package ? json_decode($package['question_types'], true) : ['text'];
                                ?>
                                <label class="checkbox-item">
                                    <input type="checkbox" name="question_types[]" value="text" class="checkbox-input" <?= in_array('text', $savedQTypes) ? 'checked' : '' ?>>
                                    <span class="checkbox-label">Testo</span>
                                </label>
                                <label class="checkbox-item">
                                    <input type="checkbox" name="question_types[]" value="tts" class="checkbox-input" <?= in_array('tts', $savedQTypes) ? 'checked' : '' ?> onchange="toggleTtsLang()">
                                    <span class="checkbox-label">Audio (TTS)</span>
                                </label>
                                <label class="checkbox-item">
                                    <input type="checkbox" name="question_types[]" value="image" class="checkbox-input" <?= in_array('image', $savedQTypes) ? 'checked' : '' ?>>
                                    <span class="checkbox-label">Immagini</span>
                                </label>
                            </div>
                        </div>

                        <div class="form-group">
                            <label class="form-label">Tipo di Risposte</label>
                            <p class="form-hint">Seleziona uno o piu tipi</p>
                            <div class="checkbox-group">
                                <?php
                                $savedATypes = $package ? json_decode($package['answer_types'], true) : ['text'];
                                ?>
                                <label class="checkbox-item">
                                    <input type="checkbox" name="answer_types[]" value="text" class="checkbox-input" <?= in_array('text', $savedATypes) ? 'checked' : '' ?>>
                                    <span class="checkbox-label">Testo</span>
                                </label>
                                <label class="checkbox-item">
                                    <input type="checkbox" name="answer_types[]" value="tts" class="checkbox-input" <?= in_array('tts', $savedATypes) ? 'checked' : '' ?> onchange="toggleTtsLang()">
                                    <span class="checkbox-label">Audio (TTS)</span>
                                </label>
                                <label class="checkbox-item">
                                    <input type="checkbox" name="answer_types[]" value="image" class="checkbox-input" <?= in_array('image', $savedATypes) ? 'checked' : '' ?>>
                                    <span class="checkbox-label">Immagini</span>
                                </label>
                            </div>
                        </div>

                        <div class="form-group" id="tts-lang-group" style="display: none;">
                            <label class="form-label" for="tts_lang">Lingua Text-to-Speech</label>
                            <?php $savedTtsLang = $package['tts_lang'] ?? 'it-IT'; ?>
                            <select id="tts_lang" name="tts_lang" class="form-input">
                                <optgroup label="Europee">
                                    <option value="it-IT" <?= $savedTtsLang === 'it-IT' ? 'selected' : '' ?>>Italiano</option>
                                    <option value="en-US" <?= $savedTtsLang === 'en-US' ? 'selected' : '' ?>>English (US)</option>
                                    <option value="en-GB" <?= $savedTtsLang === 'en-GB' ? 'selected' : '' ?>>English (UK)</option>
                                    <option value="es-ES" <?= $savedTtsLang === 'es-ES' ? 'selected' : '' ?>>Espanol</option>
                                    <option value="fr-FR" <?= $savedTtsLang === 'fr-FR' ? 'selected' : '' ?>>Francais</option>
                                    <option value="de-DE" <?= $savedTtsLang === 'de-DE' ? 'selected' : '' ?>>Deutsch</option>
                                    <option value="pt-PT" <?= $savedTtsLang === 'pt-PT' ? 'selected' : '' ?>>Portugues</option>
                                    <option value="pt-BR" <?= $savedTtsLang === 'pt-BR' ? 'selected' : '' ?>>Portugues (Brasil)</option>
                                    <option value="nl-NL" <?= $savedTtsLang === 'nl-NL' ? 'selected' : '' ?>>Nederlands</option>
                                    <option value="pl-PL" <?= $savedTtsLang === 'pl-PL' ? 'selected' : '' ?>>Polski</option>
                                    <option value="ru-RU" <?= $savedTtsLang === 'ru-RU' ? 'selected' : '' ?>>Русский</option>
                                    <option value="uk-UA" <?= $savedTtsLang === 'uk-UA' ? 'selected' : '' ?>>Українська</option>
                                    <option value="cs-CZ" <?= $savedTtsLang === 'cs-CZ' ? 'selected' : '' ?>>Cestina</option>
                                    <option value="el-GR" <?= $savedTtsLang === 'el-GR' ? 'selected' : '' ?>>Ελληνικά</option>
                                    <option value="ro-RO" <?= $savedTtsLang === 'ro-RO' ? 'selected' : '' ?>>Romana</option>
                                    <option value="hu-HU" <?= $savedTtsLang === 'hu-HU' ? 'selected' : '' ?>>Magyar</option>
                                    <option value="sv-SE" <?= $savedTtsLang === 'sv-SE' ? 'selected' : '' ?>>Svenska</option>
                                    <option value="da-DK" <?= $savedTtsLang === 'da-DK' ? 'selected' : '' ?>>Dansk</option>
                                    <option value="fi-FI" <?= $savedTtsLang === 'fi-FI' ? 'selected' : '' ?>>Suomi</option>
                                    <option value="no-NO" <?= $savedTtsLang === 'no-NO' ? 'selected' : '' ?>>Norsk</option>
                                </optgroup>
                                <optgroup label="Asiatiche">
                                    <option value="ja-JP" <?= $savedTtsLang === 'ja-JP' ? 'selected' : '' ?>>日本語</option>
                                    <option value="zh-CN" <?= $savedTtsLang === 'zh-CN' ? 'selected' : '' ?>>中文 (简体)</option>
                                    <option value="zh-TW" <?= $savedTtsLang === 'zh-TW' ? 'selected' : '' ?>>中文 (繁體)</option>
                                    <option value="ko-KR" <?= $savedTtsLang === 'ko-KR' ? 'selected' : '' ?>>한국어</option>
                                    <option value="hi-IN" <?= $savedTtsLang === 'hi-IN' ? 'selected' : '' ?>>हिन्दी</option>
                                    <option value="th-TH" <?= $savedTtsLang === 'th-TH' ? 'selected' : '' ?>>ไทย</option>
                                    <option value="vi-VN" <?= $savedTtsLang === 'vi-VN' ? 'selected' : '' ?>>Tieng Viet</option>
                                    <option value="id-ID" <?= $savedTtsLang === 'id-ID' ? 'selected' : '' ?>>Bahasa Indonesia</option>
                                </optgroup>
                                <optgroup label="Altre">
                                    <option value="ar-SA" <?= $savedTtsLang === 'ar-SA' ? 'selected' : '' ?>>العربية</option>
                                    <option value="he-IL" <?= $savedTtsLang === 'he-IL' ? 'selected' : '' ?>>עברית</option>
                                    <option value="tr-TR" <?= $savedTtsLang === 'tr-TR' ? 'selected' : '' ?>>Turkce</option>
                                </optgroup>
                            </select>
                            <p class="form-hint">La lingua usata per leggere il testo ad alta voce</p>
                        </div>

                        <script>
                        function toggleTtsLang() {
                            const qTts = document.querySelector('input[name="question_types[]"][value="tts"]');
                            const aTts = document.querySelector('input[name="answer_types[]"][value="tts"]');
                            const langGroup = document.getElementById('tts-lang-group');
                            langGroup.style.display = (qTts.checked || aTts.checked) ? 'block' : 'none';
                        }
                        // Check on page load
                        document.addEventListener('DOMContentLoaded', toggleTtsLang);
                        </script>

                        <div class="form-group">
                            <label class="form-label" for="total_questions">Numero di Domande</label>
                            <input
                                type="number"
                                id="total_questions"
                                name="total_questions"
                                class="form-input"
                                min="1"
                                max="100"
                                value="<?= e($package['total_questions'] ?? 20) ?>"
                            >
                            <p class="form-hint">Minimo 1, massimo 100 domande</p>
                        </div>

                        <div style="display: flex; gap: 1rem; margin-top: 2rem;">
                            <a href="my-packages.php" class="btn-ghost" style="flex: 1; justify-content: center;">Annulla</a>
                            <button type="submit" class="btn-primary" style="flex: 2; justify-content: center;">
                                Continua
                            </button>
                        </div>
                    </form>

                <?php elseif ($step === 2 && $package): ?>
                    <!-- STEP 2: Choose Method -->
                    <h2 class="section-title">Scegli il Metodo</h2>
                    <p class="section-subtitle">Come vuoi creare le domande per "<?= e($package['name']) ?>"?</p>

                    <div class="grid-2" style="max-width: 800px; margin: 0 auto;">
                        <div class="feature-card" style="cursor: pointer;" onclick="window.location='create.php?step=3&package=<?= $package['uuid'] ?>&method=manual'">
                            <span class="feature-icon">M</span>
                            <h3 class="feature-title">Inserimento Manuale</h3>
                            <p class="feature-text">
                                Inserisci tu stesso ogni domanda e risposta.
                                Ideale se hai gia preparato il contenuto.
                            </p>
                            <button class="btn-secondary" style="width: 100%; margin-top: 1rem; justify-content: center;">
                                Inserisci Manualmente
                            </button>
                        </div>

                        <div class="feature-card" style="cursor: pointer;" onclick="window.location='create.php?step=3&package=<?= $package['uuid'] ?>&method=ai'">
                            <span class="feature-icon">AI</span>
                            <h3 class="feature-title">Assistenza AI</h3>
                            <p class="feature-text">
                                Ti generiamo un prompt da dare alla tua AI preferita
                                (ChatGPT, Gemini, Claude...).
                            </p>
                            <button class="btn-primary" style="width: 100%; margin-top: 1rem; justify-content: center;">
                                Usa Assistenza AI
                            </button>
                        </div>
                    </div>

                    <div style="text-align: center; margin-top: 2rem;">
                        <a href="create.php?step=1&package=<?= $package['uuid'] ?>" class="btn-ghost">
                            Torna Indietro
                        </a>
                    </div>

                <?php elseif ($step === 3 && $package): ?>
                    <?php
                    $method = $_GET['method'] ?? 'ai';
                    $questionTypes = json_decode($package['question_types'], true);
                    $answerTypes = json_decode($package['answer_types'], true);
                    ?>

                    <?php if ($method === 'ai'): ?>
                        <!-- STEP 3: AI Prompt -->
                        <h2 class="section-title">Assistenza AI</h2>
                        <p class="section-subtitle">Copia questo prompt e incollalo nella tua AI preferita</p>

                        <?php
                        $aiPrompt = generateAiPrompt([
                            'question_types' => $questionTypes,
                            'answer_types' => $answerTypes,
                            'num_questions' => $package['total_questions'],
                            'topic' => $package['topic'] ?: $package['name'],
                            'language' => 'italiano',
                            'tts_lang' => $package['tts_lang'] ?? 'it-IT'
                        ]);
                        ?>

                        <div class="prompt-box">
                            <div class="prompt-header">
                                <span class="prompt-title">Prompt per AI</span>
                                <button class="btn-primary btn-copy" onclick="copyPrompt()">Copia</button>
                            </div>
                            <div class="prompt-content" id="ai-prompt"><?= e($aiPrompt) ?></div>
                        </div>

                        <div class="alert alert-info">
                            <span class="alert-icon">i</span>
                            <div>
                                <strong>Istruzioni:</strong>
                                <ol style="margin: 0.5rem 0 0; padding-left: 1.5rem;">
                                    <li>Copia il prompt sopra</li>
                                    <li>Incollalo in ChatGPT, Gemini, Claude o la tua AI preferita</li>
                                    <li>Copia il JSON generato dall'AI</li>
                                    <li>Incollalo nel campo qui sotto</li>
                                </ol>
                            </div>
                        </div>

                        <form method="POST" action="api/import-json.php" style="max-width: 800px; margin: 2rem auto 0;">
                            <input type="hidden" name="csrf_token" value="<?= Auth::csrfToken() ?>">
                            <input type="hidden" name="package_id" value="<?= $package['uuid'] ?>">

                            <div class="form-group">
                                <label class="form-label" for="json_content">Incolla il JSON generato dall'AI</label>
                                <textarea
                                    id="json_content"
                                    name="json_content"
                                    class="form-input form-textarea"
                                    style="min-height: 200px; font-family: monospace;"
                                    placeholder='{"questions": [...]}'
                                    required
                                ></textarea>
                            </div>

                            <div style="display: flex; gap: 1rem;">
                                <a href="create.php?step=2&package=<?= $package['uuid'] ?>" class="btn-ghost" style="flex: 1; justify-content: center;">
                                    Indietro
                                </a>
                                <button type="submit" class="btn-primary" style="flex: 2; justify-content: center;">
                                    Importa e Continua
                                </button>
                            </div>
                        </form>

                    <?php else: ?>
                        <!-- STEP 3: Manual Entry -->
                        <h2 class="section-title">Inserimento Manuale</h2>
                        <p class="section-subtitle">Inserisci <?= $package['total_questions'] ?> domande per "<?= e($package['name']) ?>"</p>

                        <form method="POST" action="api/save-questions.php" id="manual-form" style="max-width: 800px; margin: 0 auto;">
                            <input type="hidden" name="csrf_token" value="<?= Auth::csrfToken() ?>">
                            <input type="hidden" name="package_id" value="<?= $package['uuid'] ?>">

                            <div id="questions-container">
                                <!-- Questions will be added here by JavaScript -->
                            </div>

                            <button type="button" class="btn-secondary" onclick="addQuestion()" style="width: 100%; margin-bottom: 2rem; justify-content: center;">
                                + Aggiungi Domanda
                            </button>

                            <div style="display: flex; gap: 1rem;">
                                <a href="create.php?step=2&package=<?= $package['uuid'] ?>" class="btn-ghost" style="flex: 1; justify-content: center;">
                                    Indietro
                                </a>
                                <button type="submit" class="btn-primary" style="flex: 2; justify-content: center;">
                                    Salva e Continua
                                </button>
                            </div>
                        </form>

                        <script>
                            let questionCount = 0;
                            const maxQuestions = <?= $package['total_questions'] ?>;
                            const questionTypes = <?= $package['question_types'] ?>;
                            const answerTypes = <?= $package['answer_types'] ?>;

                            function addQuestion() {
                                if (questionCount >= maxQuestions) {
                                    alert('Hai raggiunto il numero massimo di domande (' + maxQuestions + ')');
                                    return;
                                }

                                questionCount++;
                                const container = document.getElementById('questions-container');

                                const html = `
                                    <div class="question-card" id="question-${questionCount}">
                                        <div class="question-header">
                                            <span class="question-number">Domanda ${questionCount}</span>
                                            <div class="question-actions">
                                                <button type="button" class="btn-ghost btn-small" onclick="removeQuestion(${questionCount})">Rimuovi</button>
                                            </div>
                                        </div>

                                        <div class="form-group">
                                            <label class="form-label">Testo della domanda</label>
                                            <textarea name="questions[${questionCount}][question]" class="form-input" rows="2" required></textarea>
                                        </div>

                                        <div class="form-group">
                                            <label class="form-label">Risposta corretta</label>
                                            <input type="text" name="questions[${questionCount}][correct_answer]" class="form-input" required>
                                        </div>

                                        <div class="form-group">
                                            <label class="form-label">Risposte errate (una per riga)</label>
                                            <textarea name="questions[${questionCount}][wrong_answers]" class="form-input" rows="3" placeholder="Risposta errata 1&#10;Risposta errata 2&#10;Risposta errata 3" required></textarea>
                                        </div>
                                    </div>
                                `;

                                container.insertAdjacentHTML('beforeend', html);
                            }

                            function removeQuestion(id) {
                                document.getElementById('question-' + id).remove();
                            }

                            // Add first question automatically
                            addQuestion();
                        </script>
                    <?php endif; ?>

                <?php elseif ($step === 4 && $package): ?>
                    <!-- STEP 4: Media Upload -->
                    <?php
                    // Load package JSON to find placeholders
                    $jsonPath = __DIR__ . '/uploads/' . $package['json_file_key'];
                    $packageJson = null;
                    $placeholders = [];

                    if (file_exists($jsonPath)) {
                        $packageJson = json_decode(file_get_contents($jsonPath), true);

                        // Find all placeholders
                        $jsonString = json_encode($packageJson);
                        preg_match_all('/\[INSERIRE_(IMMAGINE|AUDIO)_[^\]]+\]/', $jsonString, $matches);
                        $placeholders = array_unique($matches[0]);
                    }

                    // Get already uploaded media
                    $uploadedMedia = db()->fetchAll(
                        "SELECT placeholder, file_key FROM package_media WHERE package_id = ?",
                        [$package['id']]
                    );
                    $uploadedPlaceholders = array_column($uploadedMedia, 'file_key', 'placeholder');
                    ?>

                    <h2 class="section-title">Aggiungi Media</h2>
                    <p class="section-subtitle">Carica immagini e audio per "<?= e($package['name']) ?>"</p>

                    <?php if (empty($placeholders)): ?>
                        <div class="alert alert-success">
                            <span class="alert-icon">OK</span>
                            <div>
                                Non ci sono media da caricare. Il pacchetto contiene solo testo.
                            </div>
                        </div>

                        <div style="text-align: center; margin-top: 2rem;">
                            <a href="publish.php?package=<?= $package['uuid'] ?>" class="btn-primary">
                                Pubblica Pacchetto
                            </a>
                        </div>
                    <?php else: ?>
                        <div class="alert alert-info">
                            <span class="alert-icon">i</span>
                            <div>
                                Hai <strong><?= count($placeholders) - count($uploadedPlaceholders) ?></strong> media da caricare.
                                Clicca su ogni placeholder per caricare il file corrispondente.
                            </div>
                        </div>

                        <div class="media-upload-list" style="max-width: 800px; margin: 0 auto;">
                            <?php foreach ($placeholders as $index => $placeholder): ?>
                                <?php
                                $isUploaded = isset($uploadedPlaceholders[$placeholder]);
                                $isImage = strpos($placeholder, 'IMMAGINE') !== false;
                                $mediaType = $isImage ? 'image' : 'audio';
                                $accept = $isImage ? 'image/jpeg,image/png,image/gif,image/webp' : 'audio/mpeg,audio/mp3,audio/wav,audio/ogg';
                                ?>
                                <div class="media-upload-item <?= $isUploaded ? 'uploaded' : '' ?>" id="media-item-<?= $index ?>">
                                    <div class="media-info">
                                        <span class="media-type-icon"><?= $isImage ? 'IMG' : 'AUD' ?></span>
                                        <div class="media-details">
                                            <span class="media-placeholder"><?= e($placeholder) ?></span>
                                            <span class="media-status" id="status-<?= $index ?>">
                                                <?= $isUploaded ? 'Caricato' : 'Da caricare' ?>
                                            </span>
                                        </div>
                                    </div>
                                    <div class="media-actions">
                                        <?php if ($isUploaded): ?>
                                            <span class="upload-done">OK</span>
                                        <?php else: ?>
                                            <input
                                                type="file"
                                                id="file-<?= $index ?>"
                                                accept="<?= $accept ?>"
                                                style="display: none;"
                                                onchange="uploadMedia(<?= $index ?>, '<?= e($placeholder) ?>', '<?= $mediaType ?>')"
                                            >
                                            <button
                                                type="button"
                                                class="btn-secondary btn-small"
                                                onclick="document.getElementById('file-<?= $index ?>').click()"
                                                id="btn-<?= $index ?>"
                                            >
                                                Carica <?= $isImage ? 'Immagine' : 'Audio' ?>
                                            </button>
                                        <?php endif; ?>
                                    </div>
                                </div>
                            <?php endforeach; ?>
                        </div>

                        <div style="display: flex; gap: 1rem; margin-top: 2rem; max-width: 800px; margin-left: auto; margin-right: auto;">
                            <a href="create.php?step=3&package=<?= $package['uuid'] ?>&method=ai" class="btn-ghost" style="flex: 1; justify-content: center;">
                                Indietro
                            </a>
                            <a href="publish.php?package=<?= $package['uuid'] ?>" class="btn-primary" style="flex: 2; justify-content: center;" id="btn-publish">
                                Pubblica Pacchetto
                            </a>
                        </div>

                        <p style="text-align: center; margin-top: 1rem; color: var(--text-muted);">
                            Puoi anche pubblicare ora e aggiungere i media in seguito.
                        </p>

                        <script>
                            const packageId = '<?= $package['uuid'] ?>';
                            const csrfToken = '<?= Auth::csrfToken() ?>';

                            async function uploadMedia(index, placeholder, mediaType) {
                                const fileInput = document.getElementById('file-' + index);
                                const file = fileInput.files[0];
                                if (!file) return;

                                const btn = document.getElementById('btn-' + index);
                                const status = document.getElementById('status-' + index);
                                const item = document.getElementById('media-item-' + index);

                                btn.disabled = true;
                                btn.textContent = 'Caricamento...';
                                status.textContent = 'Caricamento in corso...';

                                const formData = new FormData();
                                formData.append('file', file);
                                formData.append('package_id', packageId);
                                formData.append('placeholder', placeholder);
                                formData.append('csrf_token', csrfToken);

                                try {
                                    const response = await fetch('api/upload-media.php', {
                                        method: 'POST',
                                        body: formData
                                    });

                                    const result = await response.json();

                                    if (result.success) {
                                        item.classList.add('uploaded');
                                        status.textContent = 'Caricato';
                                        btn.outerHTML = '<span class="upload-done">OK</span>';
                                    } else {
                                        status.textContent = 'Errore: ' + result.error;
                                        btn.disabled = false;
                                        btn.textContent = mediaType === 'image' ? 'Carica Immagine' : 'Carica Audio';
                                    }
                                } catch (error) {
                                    status.textContent = 'Errore di connessione';
                                    btn.disabled = false;
                                    btn.textContent = mediaType === 'image' ? 'Carica Immagine' : 'Carica Audio';
                                }
                            }
                        </script>
                    <?php endif; ?>

                <?php else: ?>
                    <div class="alert alert-error">
                        <span class="alert-icon">!</span>
                        Pacchetto non trovato o accesso non autorizzato.
                    </div>
                    <div style="text-align: center; margin-top: 2rem;">
                        <a href="create.php" class="btn-primary">Crea Nuovo Pacchetto</a>
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
        function copyPrompt() {
            const prompt = document.getElementById('ai-prompt').textContent;
            navigator.clipboard.writeText(prompt).then(() => {
                const btn = document.querySelector('.btn-copy');
                btn.textContent = 'Copiato!';
                setTimeout(() => btn.textContent = 'Copia', 2000);
            });
        }
    </script>
</body>
</html>
