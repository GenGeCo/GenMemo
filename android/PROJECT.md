# GenMemo - App di Memorizzazione con Spaced Repetition

## Descrizione
App Android per memorizzare cose attraverso ripetizione spaziata. Supporta immagini con nome e domande/risposte, organizzate in categorie.

## Filosofia
- **L'utente decide, l'app suggerisce** - niente pressioni o sensi di colpa
- Giornata no? Fai 10 domande o zero, va bene
- Giornata sì? Ripassa tutto quello che vuoi
- L'app premia la costanza ma non punisce le pause

## Funzionalità Principali

### 1. Schermata Home
- Design moderno stile React/Material Design
- Due pulsanti grandi: **MEMORIZZA** e **RIPASSA**
- Statistiche rapide: "Hai X elementi da ripassare oggi"

### 2. Modalità MEMORIZZA
Permette di inserire nuovi elementi da memorizzare:

#### Immagini con Nome
- Scatta foto o seleziona dalla galleria
- Inserisci il nome/risposta associata
- Assegna categoria (OBBLIGATORIA)
- Immagine ridimensionata automaticamente (max 800x800px, qualità 80%)

#### Domande e Risposte
- Inserisci domanda
- Inserisci risposta
- Assegna categoria (OBBLIGATORIA)

### 3. Modalità RIPASSA
- Selezione tipo: Immagini, Domande, o Tutto
- Selezione categoria: specifica o tutte
- Selezione numero domande: 10-100 (slider) - l'utente decide quanto fare
- Quiz con feedback immediato

#### Logica Quiz
- Mostra immagine → utente scrive risposta
- Mostra domanda → utente scrive risposta
- Confronto SIMILE (case-insensitive, trim, tollera piccoli errori)
- Feedback: corretto/sbagliato con risposta giusta

### 4. Sistema di Apprendimento (Spaced Repetition Ibrido)

Basato su ricerche scientifiche (SM-2/Leitner) adattato per semplicità.

#### Dati per ogni elemento
| Campo | Descrizione |
|-------|-------------|
| score | 0-100, quanto lo sai |
| interval | Giorni tra un ripasso e l'altro |
| nextReviewDate | Data prossimo ripasso consigliato |
| streak | Risposte corrette consecutive |
| correctDays | Giorni diversi con risposta corretta |

#### Risposta CORRETTA
```
score += 5 + (streak * 2)    // bonus per streak
interval = interval * 2.5    // prossimo ripasso più lontano
streak += 1
correctDays += 1 (se giorno diverso da ultimo)
nextReviewDate = oggi + interval
```

#### Risposta SBAGLIATA
```
score -= 15
interval = 1                 // torna domani
streak = 0
nextReviewDate = domani
```

#### Decadimento (solo se non ripassa quando doveva)
```
giorniMancati = oggi - nextReviewDate
if (giorniMancati > 0) {
    decadimento = giorniMancati * (1 + giorniMancati/10)  // accelera
    score -= decadimento
    score = max(score, 30)   // non scende sotto 30
}
```

#### Selezione Domande (priorità)
1. **URGENTI**: nextReviewDate <= oggi (dovevi ripassarle!)
2. **DEBOLI**: score < 50 (le sai male)
3. **RESTO**: random pesato (peso = 100 - score)

#### Criterio "Padroneggiato" (score = 100)
- Richiede risposte corrette in almeno 10 giorni DIVERSI
- Score 100 = quasi mai chiesto (ma può decadere se non ripassa)

### 5. Gestione Categorie (Impostazioni)
- Crea nuova categoria (OBBLIGATORIA per ogni elemento)
- Modifica categoria
- Elimina categoria (con conferma, sposta elementi a "Generale")
- Lista categorie esistenti
- Categoria default "Generale" sempre presente

### 6. Gestione Elementi (Impostazioni)
- Lista tutti gli elementi memorizzati
- Filtra per tipo (immagini/domande)
- Filtra per categoria
- Elimina elemento (con conferma)
- Visualizza statistiche elemento (punteggio, streak, giorni praticati)

## Architettura Tecnica

### Stack Tecnologico
- **Linguaggio**: Kotlin
- **UI**: Jetpack Compose (stile moderno React-like)
- **Database**: Room (SQLite)
- **Architettura**: MVVM
- **DI**: Hilt (opzionale, può essere semplificato)

### Struttura Database

#### Tabella: categories
| Campo | Tipo | Note |
|-------|------|------|
| id | Long | PK, autoincrement |
| name | String | Unique |
| color | Int | Colore badge (opzionale) |

#### Tabella: memory_items
| Campo | Tipo | Note |
|-------|------|------|
| id | Long | PK, autoincrement |
| type | String | "IMAGE" o "QUESTION" |
| question | String | Path immagine o testo domanda |
| answer | String | Risposta corretta |
| categoryId | Long? | FK nullable |
| score | Int | 0-100, default 0 |
| correctDays | Int | Giorni diversi con risposta corretta |
| lastPracticeDate | Long | Timestamp ultimo ripasso |
| lastCorrectDate | Long | Timestamp ultima risposta corretta |
| createdAt | Long | Timestamp creazione |

### Struttura Progetto
```
app/src/main/java/com/example/genmemo/
├── MainActivity.kt
├── GenMemoApplication.kt
├── data/
│   ├── database/
│   │   ├── AppDatabase.kt
│   │   ├── MemoryItemDao.kt
│   │   └── CategoryDao.kt
│   ├── model/
│   │   ├── MemoryItem.kt
│   │   └── Category.kt
│   └── repository/
│       └── MemoryRepository.kt
├── ui/
│   ├── theme/
│   │   ├── Color.kt
│   │   ├── Theme.kt
│   │   └── Type.kt
│   ├── screens/
│   │   ├── HomeScreen.kt
│   │   ├── MemorizeScreen.kt
│   │   ├── ReviewScreen.kt
│   │   ├── ReviewSessionScreen.kt
│   │   └── SettingsScreen.kt
│   ├── components/
│   │   ├── CategoryChip.kt
│   │   ├── MemoryCard.kt
│   │   └── QuizCard.kt
│   └── navigation/
│       └── NavGraph.kt
├── viewmodel/
│   ├── HomeViewModel.kt
│   ├── MemorizeViewModel.kt
│   ├── ReviewViewModel.kt
│   └── SettingsViewModel.kt
└── util/
    ├── ImageUtils.kt
    └── SpacedRepetitionEngine.kt
```

## Configurazioni Utente
- Numero domande per sessione: 10-100 (default 20)
- Tema: Chiaro/Scuro/Sistema

## Note Implementative
- Immagini salvate in storage interno app (no permessi speciali)
- Ridimensionamento immagini: max 800x800, JPEG 80%
- Confronto risposte: case-insensitive, trim spazi
- Backup: possibile esportazione database (futuro)
