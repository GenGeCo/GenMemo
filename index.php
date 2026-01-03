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
            <section class="hero" style="padding: 2rem 0;">
                <div class="hero-content">
                    <h1 class="hero-title" style="margin-bottom: 0.75rem;">
                        Crea <span class="hero-highlight">Pacchetti Quiz</span><br>
                        Impara Ovunque
                    </h1>

                    <p class="hero-subtitle" style="margin-bottom: 1rem;">
                        Crea pacchetti di domande con <strong>testo, audio e immagini</strong>.<br>
                        Usa l'<strong>assistenza AI</strong> per generare quiz su qualsiasi argomento.
                    </p>

                    <div class="hero-actions" style="gap: 0.75rem;">
                        <?php if ($user): ?>
                            <a href="create.php" class="btn-primary">
                                <span>+</span> Crea Pacchetto
                            </a>
                        <?php else: ?>
                            <a href="register.php" class="btn-primary">
                                <span>+</span> Inizia Gratis
                            </a>
                        <?php endif; ?>
                        <a href="packages.php" class="btn-ghost">
                            Sfoglia Pacchetti
                        </a>
                    </div>
                </div>
            </section>

            <!-- Features Section -->
            <section class="section" style="padding: 1.5rem 0;">
                <h2 class="section-title" style="margin-bottom: 0.5rem;">Come Funziona</h2>

                <div class="grid-3" style="gap: 0.75rem;">
                    <div class="feature-card" style="padding: 1rem;">
                        <span class="feature-icon" style="width: 32px; height: 32px; font-size: 0.9rem;">1</span>
                        <h3 class="feature-title">Configura il Pacchetto</h3>
                        <p class="feature-text">
                            Scegli il tipo di domande e risposte: testo, audio, immagini o una combinazione.
                        </p>
                    </div>

                    <div class="feature-card" style="padding: 1rem;">
                        <span class="feature-icon" style="width: 32px; height: 32px; font-size: 0.9rem;">2</span>
                        <h3 class="feature-title">Crea le Domande</h3>
                        <p class="feature-text">
                            Inserisci manualmente o usa l'assistenza AI con ChatGPT, Gemini, Claude...
                        </p>
                    </div>

                    <div class="feature-card" style="padding: 1rem;">
                        <span class="feature-icon" style="width: 32px; height: 32px; font-size: 0.9rem;">3</span>
                        <h3 class="feature-title">Aggiungi Media</h3>
                        <p class="feature-text">
                            Carica immagini e audio. Il wizard ti guida domanda per domanda.
                        </p>
                    </div>
                </div>
            </section>

            <!-- Package Types -->
            <section class="section" style="padding: 1.5rem 0;">
                <h2 class="section-title" style="margin-bottom: 0.5rem;">Esempi di Utilizzo</h2>

                <div class="grid-2" style="gap: 0.75rem;">
                    <div class="feature-card" style="padding: 1rem;">
                        <span class="feature-icon" style="width: 32px; height: 32px; font-size: 0.8rem;">EN</span>
                        <h3 class="feature-title">Lingue Straniere</h3>
                        <p class="feature-text">
                            Vocabolario con audio pronuncia e immagini. Inglese, spagnolo, tedesco...
                        </p>
                    </div>

                    <div class="feature-card" style="padding: 1rem;">
                        <span class="feature-icon" style="width: 32px; height: 32px; font-size: 0.8rem;">GEO</span>
                        <h3 class="feature-title">Geografia</h3>
                        <p class="feature-text">
                            Regioni, capitali, bandiere. Quiz con immagini per memorizzare visivamente.
                        </p>
                    </div>

                    <div class="feature-card" style="padding: 1rem;">
                        <span class="feature-icon" style="width: 32px; height: 32px; font-size: 0.8rem;">HIS</span>
                        <h3 class="feature-title">Storia</h3>
                        <p class="feature-text">
                            Date, personaggi, eventi. Associa volti ai nomi dei grandi della storia.
                        </p>
                    </div>

                    <div class="feature-card" style="padding: 1rem;">
                        <span class="feature-icon" style="width: 32px; height: 32px; font-size: 0.8rem;">SCI</span>
                        <h3 class="feature-title">Scienze</h3>
                        <p class="feature-text">
                            Formule, elementi, anatomia. Diagrammi e schemi per quiz visivi.
                        </p>
                    </div>
                </div>
            </section>

            <!-- CTA Section -->
            <?php if (!$user): ?>
            <section class="section" style="text-align: center; padding: 1.5rem 0;">
                <h2 class="section-title" style="margin-bottom: 0.5rem;">Pronto a Creare?</h2>
                <div class="hero-actions">
                    <a href="register.php" class="btn-primary">Registrati Gratis</a>
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
