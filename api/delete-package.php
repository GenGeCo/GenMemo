<?php
/**
 * API per eliminare un pacchetto
 * POST /api/delete-package.php
 *
 * Richiede autenticazione (sessione o token)
 */
require_once __DIR__ . '/../includes/init.php';

header('Content-Type: application/json');

// Solo POST
if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    jsonResponse(['error' => 'Method not allowed'], 405);
}

// Verifica autenticazione
$userId = Auth::userId();

if (!$userId) {
    jsonResponse(['error' => 'Non autorizzato'], 401);
}

// Verifica CSRF per richieste da form
$csrfToken = $_POST['csrf_token'] ?? '';
if (!empty($csrfToken) && !Auth::verifyCsrf($csrfToken)) {
    jsonResponse(['error' => 'Token CSRF non valido'], 403);
}

$packageUuid = $_POST['package_uuid'] ?? '';

if (empty($packageUuid)) {
    jsonResponse(['error' => 'UUID pacchetto mancante'], 400);
}

// Verifica che il pacchetto appartenga all'utente
$package = db()->fetch(
    "SELECT * FROM packages WHERE uuid = ? AND user_id = ?",
    [$packageUuid, $userId]
);

if (!$package) {
    jsonResponse(['error' => 'Pacchetto non trovato o non autorizzato'], 404);
}

try {
    $pdo = db()->getConnection();
    $pdo->beginTransaction();

    // Elimina dati correlati (ignora errori se tabelle non esistono)
    $tablesToClean = [
        'user_progress',
        'user_question_progress',
        'package_downloads',
        'study_sessions'
    ];

    foreach ($tablesToClean as $table) {
        try {
            db()->query("DELETE FROM {$table} WHERE package_id = ?", [$package['id']]);
        } catch (Exception $e) {
            // Tabella potrebbe non esistere, ignora
        }
    }

    // Elimina il file JSON se esiste
    if ($package['json_file_key']) {
        $jsonPath = __DIR__ . '/../uploads/' . $package['json_file_key'];
        if (file_exists($jsonPath)) {
            @unlink($jsonPath);
        }
    }

    // Elimina il pacchetto
    db()->query("DELETE FROM packages WHERE id = ?", [$package['id']]);

    $pdo->commit();

    jsonResponse([
        'success' => true,
        'message' => 'Pacchetto eliminato con successo'
    ]);

} catch (Exception $e) {
    if (isset($pdo)) {
        $pdo->rollback();
    }
    jsonResponse(['error' => 'Errore durante l\'eliminazione: ' . $e->getMessage()], 500);
}
