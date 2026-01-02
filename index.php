<?php
require_once __DIR__ . '/includes/init.php';

$user = Auth::user();
?>
<!DOCTYPE html>
<html lang="it">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>GenMemo - Crea e Condividi Pacchetti di Quiz</title>
    <meta name="description" content="Crea pacchetti di quiz personalizzati con testo, audio e immagini. Usa l'AI per generare domande o inseriscile manualmente.">
    <link rel="stylesheet" href="styles.css">
</head>
<body>
    <div class="page-shell">
        <div class="content-inner">
            <!-- Header -->
            <header>
                <div class="brand">
                    <div class="brand-logo">GM</div>
                    <div class="brand-text">
                        <div class="brand-text-title">GenMemo</div>
                        <div class="brand-text-sub">Quiz Package Creator</div>
                    </div>
                </div>

                <nav class="nav-links">
                    <a href="index.php" class="nav-link active">Home</a>
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

            <!-- Hero Section -->
            <section class="hero">
                <div class="hero-content">
                    <div class="eyebrow">
                        <span class="eyebrow-dot">?</span>
                        <span>Quiz Creator</span>
                    </div>

                    <h1 class="hero-title">
                        Crea <span class="hero-highlight">Pacchetti Quiz</span><br>
                        Impara Ovunque
                    </h1>

                    <p class="hero-subtitle">
                        Crea pacchetti di domande con <strong>testo, audio e immagini</strong>.<br>
                        Usa l'<strong>assistenza AI</strong> per generare quiz su qualsiasi argomento.
                    </p>

                    <div class="hero-actions">
                        <?php if ($user): ?>
                            <a href="create.php" class="btn-primary">
                                <span>+</span>
                                Crea Nuovo Pacchetto
                            </a>
                        <?php else: ?>
                            <a href="register.php" class="btn-primary">
                                <span>+</span>
                                Inizia Gratis
                            </a>
                        <?php endif; ?>
                        <a href="packages.php" class="btn-ghost">
                            Sfoglia Pacchetti
                        </a>
                    </div>
                </div>
            </section>

            <!-- Features Section -->
            <section class="section">
                <h2 class="section-title">
                    Come Funziona
                </h2>
                <p class="section-subtitle">
                    Tre semplici passaggi per creare il tuo pacchetto quiz
                </p>

                <div class="grid-3">
                    <div class="feature-card">
                        <span class="feature-icon">1</span>
                        <h3 class="feature-title">Configura il Pacchetto</h3>
                        <p class="feature-text">
                            Scegli il tipo di domande e risposte: testo, audio, immagini o una combinazione.
                            Definisci il numero di domande.
                        </p>
                    </div>

                    <div class="feature-card">
                        <span class="feature-icon">2</span>
                        <h3 class="feature-title">Crea le Domande</h3>
                        <p class="feature-text">
                            Inserisci manualmente o usa l'assistenza AI. Ti generiamo un prompt da dare
                            alla tua AI preferita (ChatGPT, Gemini, Claude...).
                        </p>
                    </div>

                    <div class="feature-card">
                        <span class="feature-icon">3</span>
                        <h3 class="feature-title">Aggiungi Media</h3>
                        <p class="feature-text">
                            Carica immagini e audio dove necessario. Il wizard ti guida domanda per domanda
                            per completare il pacchetto.
                        </p>
                    </div>
                </div>
            </section>

            <!-- Package Types -->
            <section class="section">
                <h2 class="section-title">
                    Esempi di Utilizzo
                </h2>

                <div class="grid-2">
                    <div class="feature-card">
                        <span class="feature-icon">EN</span>
                        <h3 class="feature-title">Lingue Straniere</h3>
                        <p class="feature-text">
                            Impara vocabolario con audio pronuncia e immagini associative.
                            Perfetto per inglese, spagnolo, tedesco...
                        </p>
                    </div>

                    <div class="feature-card">
                        <span class="feature-icon">IT</span>
                        <h3 class="feature-title">Geografia</h3>
                        <p class="feature-text">
                            Regioni, capitali, bandiere. Quiz con immagini per memorizzare
                            visivamente le informazioni.
                        </p>
                    </div>

                    <div class="feature-card">
                        <span class="feature-icon">HI</span>
                        <h3 class="feature-title">Storia</h3>
                        <p class="feature-text">
                            Date, personaggi, eventi. Associa volti ai nomi dei grandi
                            della storia con foto e descrizioni.
                        </p>
                    </div>

                    <div class="feature-card">
                        <span class="feature-icon">SC</span>
                        <h3 class="feature-title">Scienze</h3>
                        <p class="feature-text">
                            Formule, elementi, anatomia. Usa diagrammi e schemi
                            per quiz visivi interattivi.
                        </p>
                    </div>
                </div>
            </section>

            <!-- CTA Section -->
            <?php if (!$user): ?>
            <section class="section" style="text-align: center;">
                <h2 class="section-title">
                    Pronto a Creare?
                </h2>
                <p class="section-subtitle">
                    Registrati gratuitamente e inizia a creare i tuoi pacchetti quiz
                </p>
                <div class="hero-actions">
                    <a href="register.php" class="btn-primary">
                        Registrati Gratis
                    </a>
                </div>
            </section>
            <?php endif; ?>

            <!-- Footer -->
            <footer class="footer">
                <p>GenMemo &copy; <?= date('Y') ?> - Powered by GenGeCo</p>
            </footer>
        </div>
    </div>
</body>
</html>
