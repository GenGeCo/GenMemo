# GenMemo - Roadmap Sviluppo

## Fase 1: Setup Progetto Base
- [x] Configurare build.gradle con dipendenze (Compose, Room, Navigation)
- [x] Creare struttura cartelle progetto
- [x] Configurare tema Material3 (colori, typography)
- [x] Setup navigazione base

## Fase 2: Database e Models
- [x] Creare entity Category
- [x] Creare entity MemoryItem
- [x] Creare AppDatabase
- [x] Creare CategoryDao
- [x] Creare MemoryItemDao
- [x] Creare Repository

## Fase 3: Schermata Home
- [x] Layout Home con due pulsanti grandi (Memorizza/Ripassa)
- [x] Design moderno con animazioni
- [x] Statistiche rapide (elementi totali, da ripassare)

## Fase 4: Schermata Memorizza
- [x] Tab/Toggle per tipo (Immagine/Domanda)
- [x] Form inserimento immagine
  - [x] Seleziona da galleria
  - [x] Scatta foto
  - [x] Ridimensionamento automatico
  - [x] Campo nome/risposta
- [x] Form inserimento domanda/risposta
- [x] Selettore categoria
- [x] Pulsante salva con feedback

## Fase 5: Sistema Spaced Repetition
- [x] Implementare SpacedRepetitionEngine
  - [x] Calcolo decadimento giornaliero
  - [x] Aggiornamento punteggio (corretta/sbagliata)
  - [x] Algoritmo selezione domande pesato
  - [x] Tracking giorni corretti distinti
- [x] Applicare decadimento all'avvio app

## Fase 6: Schermata Ripassa (Setup)
- [x] Selezione tipo (Immagini/Domande/Tutto)
- [x] Selezione categoria (dropdown)
- [x] Slider numero domande (10-100)
- [x] Pulsante avvia sessione
- [x] Messaggio se non ci sono elementi

## Fase 7: Schermata Quiz (Sessione Ripasso)
- [x] Layout quiz immagine (mostra immagine, input risposta)
- [x] Layout quiz domanda (mostra domanda, input risposta)
- [x] Logica verifica risposta
- [x] Feedback visivo (corretto verde, sbagliato rosso + risposta)
- [x] Progresso sessione (3/20)
- [x] Schermata riepilogo fine sessione

## Fase 8: Impostazioni
- [x] Gestione categorie
  - [x] Lista categorie
  - [x] Aggiungi categoria
  - [x] Elimina categoria (con conferma)
- [x] Gestione elementi memorizzati
  - [x] Lista elementi con filtri
  - [x] Visualizza dettagli elemento
  - [x] Elimina elemento (con conferma)
- [ ] Slider domande per sessione default (nella setup ripasso)

## Fase 9: Polish e Ottimizzazioni
- [x] Animazioni transizioni
- [x] Gestione errori e stati vuoti
- [x] Tema scuro (automatico con sistema)
- [ ] Ottimizzazione performance (se necessario)
- [ ] Test su diversi dispositivi

## Fase 10: Extra (Opzionale - Futuro)
- [ ] Esporta/Importa dati
- [ ] Widget statistiche
- [ ] Notifiche promemoria ripasso
- [ ] Grafici progressi

---

## Struttura File Creati

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
│   │   ├── ReviewSetupScreen.kt
│   │   ├── ReviewSessionScreen.kt
│   │   ├── SettingsScreen.kt
│   │   ├── ManageCategoriesScreen.kt
│   │   └── ManageItemsScreen.kt
│   └── navigation/
│       └── NavGraph.kt
└── util/
    ├── ImageUtils.kt
    └── SpacedRepetitionEngine.kt
```

## Dipendenze Utilizzate
- Jetpack Compose + Material3
- Room Database + KSP
- Navigation Compose
- Coil (immagini)
- ExifInterface (rotazione foto)
