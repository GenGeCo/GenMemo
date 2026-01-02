# GenMemo - Guida al Deploy

## Deploy Automatico con GitHub Actions

### 1. Crea Repository su GitHub
1. Vai su https://github.com/new
2. Nome: `GenMemo_web`
3. Visibilita: Private
4. Non inizializzare con README

### 2. Configura i Secrets
Vai in Settings > Secrets and variables > Actions e aggiungi:

| Secret | Valore |
|--------|--------|
| `FTP_SERVER` | ftp.netsons.com |
| `FTP_USERNAME` | Il tuo username FTP |
| `FTP_PASSWORD` | La tua password FTP |
| `CONFIG_PHP` | Il contenuto completo di config.php |

### 3. Push del Codice
```bash
git remote add origin https://github.com/TUO_USERNAME/GenMemo_web.git
git add .
git commit -m "Initial commit"
git push -u origin main
```

Il deploy avverra automaticamente ad ogni push su main.

---

## Requisiti Server
- PHP 7.4+ (consigliato 8.0+)
- MariaDB 10.6+
- Apache con mod_rewrite

## Configurazione Netsons

### 1. Crea il Database
1. Accedi a cPanel
2. Vai su "Database MySQL"
3. Crea un nuovo database (es: `tuousername_genmemo`)
4. Crea un utente database con password sicura
5. Associa l'utente al database con tutti i privilegi

### 2. Importa lo Schema
1. Vai su phpMyAdmin
2. Seleziona il database creato
3. Clicca "Importa"
4. Carica il file `install/schema.sql`

### 3. Configura l'Applicazione
Modifica `includes/config.php`:

```php
// Database
define('DB_HOST', 'localhost');
define('DB_NAME', 'tuousername_genmemo');
define('DB_USER', 'tuousername_dbuser');
define('DB_PASS', 'la_tua_password_sicura');

// Cloudflare R2 (opzionale per ora)
define('R2_ACCOUNT_ID', 'il_tuo_account_id');
define('R2_ACCESS_KEY_ID', 'la_tua_access_key');
define('R2_SECRET_ACCESS_KEY', 'la_tua_secret_key');
define('R2_BUCKET_NAME', 'genmemo');
define('R2_PUBLIC_URL', 'https://pub-xxxxx.r2.dev');

// URL del sito
define('SITE_URL', 'https://www.gruppogea.net/genmemo');
```

### 4. Carica i File
1. Usa FileZilla o il File Manager di cPanel
2. Carica tutti i file in `/public_html/genmemo/`
3. Assicurati che la cartella `uploads/` abbia permessi 755

### 5. Permessi Cartelle
```
chmod 755 uploads/
```

### 6. Test
1. Visita https://www.gruppogea.net/genmemo
2. Prova a registrarti
3. Prova a creare un pacchetto

## Struttura File

```
genmemo/
├── api/                    # API endpoints
│   ├── download.php       # Download pacchetto (web)
│   ├── get-package.php    # API per app Android
│   ├── import-json.php    # Import JSON da AI
│   ├── list-packages.php  # Lista pacchetti per app
│   └── save-questions.php # Salva domande manuali
├── assets/
│   ├── images/            # Immagini statiche
│   └── js/                # JavaScript
├── includes/
│   ├── auth.php           # Autenticazione
│   ├── config.php         # Configurazione (DA MODIFICARE!)
│   ├── db.php             # Connessione database
│   ├── functions.php      # Helper functions
│   └── init.php           # Inizializzazione
├── install/
│   └── schema.sql         # Schema database
├── uploads/               # File caricati (JSON pacchetti)
├── .htaccess              # Configurazione Apache
├── create.php             # Wizard creazione pacchetto
├── index.php              # Homepage
├── login.php              # Login
├── logout.php             # Logout
├── my-packages.php        # I miei pacchetti
├── package.php            # Dettaglio pacchetto
├── packages.php           # Lista pubblica
├── publish.php            # Pubblica pacchetto
├── register.php           # Registrazione
└── styles.css             # Stili CSS
```

## API per App Android

### Ottenere un pacchetto
```
GET /api/get-package.php?code=PACKAGE_UUID

Response: JSON del pacchetto completo
```

### Lista pacchetti pubblici
```
GET /api/list-packages.php
GET /api/list-packages.php?search=inglese
GET /api/list-packages.php?page=2&limit=20

Response:
{
  "success": true,
  "data": {
    "packages": [...],
    "pagination": {...}
  }
}
```

## Cloudflare R2 (Fase 2)

Per abilitare upload di immagini e audio su R2:

1. Crea un bucket R2 su Cloudflare
2. Crea API token con permessi R2
3. Configura le credenziali in `config.php`
4. Il sistema caricherà automaticamente i media su R2

## Troubleshooting

### Errore "Database connection failed"
- Verifica le credenziali in `config.php`
- Verifica che il database esista
- Verifica che l'utente abbia i permessi

### Errore 500
- Controlla i log di errore in cPanel
- Verifica i permessi delle cartelle
- Verifica la sintassi PHP

### Upload non funziona
- Verifica permessi cartella `uploads/` (755)
- Verifica limiti PHP (upload_max_filesize)

## Contatti
GenGeCo - www.gruppogea.net
