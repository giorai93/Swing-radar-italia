# Swing Radar Italia 2.1 — codice sorgente

## Novità 2.1 — ciclo di vita dei recuperi controtrend

- Le **Zone** distinguono `OSSERVAZIONE`, `RIMBALZO`, `CONFERMATO` (conferma tecnica sperimentale) e `INVALIDATO`, con `SCADUTO` per le idee ferme oltre 12 sedute. Non sono ordini e non è dimostrato un vantaggio statistico.
- **Rimbalzo:** minimo sotto il supporto originario e chiusura di nuovo almeno 0,5% sopra, nella parte medio-alta della candela, con RSI(2) non superiore a 30. Rai Way il 30 ottobre 2026 è l'esempio di *falso breakdown potenziale*, non un breakout momentum.
- **Conferma tecnica:** dopo il rimbalzo, chiusura almeno 0,2% sopra il massimo del giorno prima, posizione di chiusura almeno al 60% della candela e volume almeno pari alla media delle 20 sedute precedenti. Non è un segnale statisticamente validato.
- **Invalidazione:** un minimo inferiore o uguale allo stop iniziale oppure una chiusura oltre lo 0,5% sotto il supporto iniziale. Le regole sono rigiocate solo sui prezzi disponibili giorno per giorno, senza guardare il futuro.
- Quando il prezzo viola lo stop di una vecchia zona ma recupera in chiusura **un nuovo supporto almeno l'1% più basso**, si apre un episodio distinto con nuovi livelli. Non si trasforma retroattivamente l'episodio invalidato in un successo. È rilevante sul caso Rai Way del 30/10.
- Stop, supporto e target restano fissati alla prima osservazione per evitare di spostare retroattivamente i livelli. Lo stop iniziale è sempre sotto il minimo della candela di osservazione con margine basato su ATR. La fascia di monitoraggio non viene chiamata fascia d'ingresso, e i target non sono automaticamente vere resistenze.
- È possibile filtrare le Zone per stato e periodo. Il valore 4/4 indica solo il numero di filtri soddisfatti al primo avvistamento, **non una probabilità di successo**.
- Aggiornamento Android `2.1` (`versionCode=12`), stesso package e firma; una nuova scansione completa aggiorna i cicli di vita senza cancellare dati, diario o impostazioni.

## Novità 2.0 — aggiornamento EOD e qualità delle quotazioni

- Corretto il ritardo di giornata dovuto a **JobScheduler**: ad ogni riapertura l'app confronta l'ultima candela disponibile con la seduta feriale attesa in fuso Europe/Rome. Se manca e l'ultimo tentativo non è stato eseguito negli ultimi 120 minuti, avvia una nuova scansione. Una verifica periodica (60 s) rileva anche il passaggio delle 18:15 mentre l'app rimane aperta.
- La prima apertura dopo l'aggiornamento 2.0 lancia una scansione completa indipendentemente dal vecchio report. Il comando manuale **Scansiona Milano** e il job in background scaricano nuovi dati ignorando la cache di 6 ore; una cache recente rimane possibile per altri usi non forzati.
- L'interfaccia distingue chiaramente una **scansione eseguita** da un **feed aggiornato**: mostra data e ora di scansione (Roma), ultimo giorno di prezzi, seduta feriale attesa e un avviso rosso se l'ultima seduta è arretrata. Uno zero nei setup su un giorno arretrato non viene spacciato per assenza di opportunità sull'ultima seduta attesa.
- Diagnostica ticker con ultimo giorno EOD e conteggio dei simboli con dati indietro rispetto alla seduta feriale attesa. Le festività e i ritardi del provider possono richiedere ulteriori verifiche.
- Verifica indipendente il 30/10/2026: Yahoo Chart di ENI.MI restituisce anche la barra del 29/10; la visualizzazione del 28/10 in una scansione precedente è coerente con un aggiornamento non rieseguito. La seduta del 30 resta esclusa fino alle 18:15 italiane perché intraday.
- Non è un feed live: TradingView durante la seduta mostra prezzi diversi dalla candela EOD precedente. L'aggiornamento non cambia le regole Forza/Debolezza né introduce segnali fittizi.
- Versione Android `2.0` (`versionCode=11`), stessa firma e database; aggiornabile sopra 1.1–1.9 senza cancellare diario, grafici e parametri.

## Novità 1.9 — niente doppioni operativi

- **Una scheda per idea operativa**: i rilevamenti consecutivi (entro 7 giorni di calendario) dello stesso ticker e tipo FORZA/DEBOLEZZA vengono consolidati in una scheda, usando i livelli dell'ultimo rilevamento. Una nota mostra numero e date delle osservazioni. Dopo la chiusura del precedente setup può iniziare una nuova idea.
- **Storico conservato**: i dati grezzi e il diario nel database non vengono eliminati. Anche dopo l'aggiornamento i rilievi restano consultabili internamente.
- **Statistiche senza trade sovrapposti sul medesimo titolo/direzione**: viene simulato il primo segnale cronologico; le ripetizioni generate mentre quel trade è ancora aperto non diventano operazioni indipendenti. Una nuova idea dopo l'uscita viene simulata normalmente. Le ripetizioni escluse sono conteggiate separatamente.
- **Niente notifiche duplicate per conferme consecutive**: se nei precedenti 7 giorni risulta un'idea della stessa direzione ancora aperta al giorno del nuovo segnale, il nuovo rilievo viene archiviato ma non inviato come ulteriore notifica. La distinzione si basa sullo stato EOD teorico, non su posizioni realmente acquistate.
- Numeri versione Android aggiornati a `versionName=1.9`, `versionCode=10`. Package e chiave rimangono identici; l'installazione sopra 1.1–1.8 mantiene dati, posizioni e impostazioni.

APK nativo Android sviluppato per uso personale e ricerca su segnali EOD della Borsa Italiana.

## Novità versione 1.8

- Nuova scheda **Statistiche**, accessibile dal pulsante nella schermata iniziale oppure dal **Diario**. Confronto quantitativo dei soli segnali FORZA e DEBOLEZZA già memorizzati, con segmenti Large/Mid/Small (e lista base) e audit dei singoli casi. Le zone di osservazione non sono trattate come ordini.
- **Simulazione prudente e riproducibile**: apertura LONG alla prima apertura successiva al segnale, purché il prezzo non sia già oltre stop/target; vendita interamente al primo target 1, stop oppure dopo N sedute. Stop prioritario quando l'intervallo della stessa candela tocca sia stop sia target; i gap oltre stop/target sono eseguiti all'apertura e non al livello teorico.
- Input configurabili e salvati nelle preferenze Android: importo massimo per ordine (1.000 € predefiniti), commissione per lato (2,95 €), slippage per lato (5 basis point = 0,05%) e durata 5, 10 o 20 sedute. Numero di azioni intero, commissione di acquisto compresa nel budget. Si tratta di ipotesi, non di costi verificati del proprio broker.
- Indicatori calcolati **solo sugli esiti chiusi**: numero di simulazioni, win rate *netto*, rendimento medio in multipli di rischio `R`, profit factor monetario e proxy di drawdown sequenziale a rischio convenzionale 1% per segnale (non MaxDD di portafoglio). Distinzione esplicita fra chiusi, in corso e non eseguibili.
- Percentuali di primo target / primo stop / uscita per tempo. Il target 2 è misurato su un **percorso alternativo ipotetico** e non è cumulabile con un'uscita al target 1.
- Rendimenti medi teorici a 5, 10 e 20 sedute per mantenimento *senza stop*, dopo costi; le osservazioni non ancora complete sono escluse da ciascun rispettivo orizzonte.
- Nessun server e nessun dato inventato: lo studio legge le stesse candele EOD già salvate sul telefono. Un segnale viene escluso o marcato "in corso" quando mancano dati per simularlo.
- **Limiti importanti**: questo non è un backtest storico completo/out-of-sample. Segnali recenti recuperati retrospettivamente, ticker ancora quotati oggi, possibili eventi societari, sovrapposizioni fra trade e limiti del feed rendono le statistiche *esplorative*, non una dimostrazione di profitto.

## Novità versione 1.7

- Il pulsante **📈 Grafico** appare nelle schede dei setup confermati, in formazione, recupero e momentum. Il grafico funziona **offline** sui dati OHLC già memorizzati nel database Android. Se manca lo storico, invita a eseguire una scansione; non inventa prezzi e non richiede chiavi/API aggiuntive.
- Grafici giornalieri a **candele giapponesi o barre OHLC** e volume, periodi 1/3/6 mesi e 1/2 anni, zoom temporale, navigazione sulle sedute precedenti e lettura dei valori toccando una barra.
- **SMA20, SMA50, SMA200**, resistenza pari al massimo delle 20 sedute *precedenti* e supporto pari al minimo delle 120 sedute *precedenti*. Le medie utilizzano lo storico antecedente alla finestra visibile; i livelli di supporto e resistenza vengono ricalcolati sulla finestra selezionata senza guardare candele successive.
- Indicatori orizzontali **entrata, stop, target 1/2 e zona di osservazione**, quando il setup dispone di questi valori. Se il livello è lontano dai prezzi visibili viene indicato come "fuori scala", per evitare di schiacciare le candele. La data del segnale è marcata sul grafico quando inclusa nella finestra.
- I grafici escludono una barra corrente non ancora consolidata prima delle 18:15 italiane e chiariscono che lo storico è una cache EOD, non un feed in tempo reale.
- Non è stato aggiunto alcun segnale "da comprare" automaticamente: i livelli del grafico sono **ipotesi tecniche**; le operazioni vere restano quelle immesse manualmente nel diario.

## Novità versione 1.6

- **Momentum + eventi** nelle schede Radar e Zone: rileva su EOD un'accelerazione giornaliera ≥3,5%, oppure ≥1,5% con rendimento a 2 sedute ≥6%; richiede volume ≥1,15× mediana 20 sedute, chiusura nella parte alta della candela (≥60%) e liquidità sufficiente. NON richiede SMA50 > SMA200. Sono candidati da verificare, non segnali d'acquisto.
- Le date di trimestrali attese o pubblicate si associano ai titoli del radar. Per osservazioni storiche l'associazione all'evento è retrospettiva, quindi non è una previsione né una validazione del rendimento.
- In **Dati** c'è la diagnostica con ricerca del ticker (ad esempio `MAIRE`), per vedere se è stato analizzato o se il feed ha avuto problemi e quali filtri non sono superati.
- **Stato dei setup teorici**: attivo, target 1/2 toccato, stop toccato, scaduto dopo 20 sedute. Verificato a posteriori dalle barre giornaliere dopo il setup; se stop e target sono entrambi toccati nella stessa candela si presume in modo conservativo lo stop per primo. Non è una simulazione dell'esecuzione effettiva.
- Filtri per gli stati dei setup, separati dai filtri Forza/Debolezza. Il diario ora separa le **posizioni reali registrate manualmente** in aperte, chiuse e tutte. Nessuna posizione viene aperta o chiusa automaticamente nell'app o presso il broker.
- **Caso MAIRE 26–27/10/2026**: il titolo era nell'universo italiano, ma le vecchie regole escludevano accelerazioni in assenza del trend SMA50 > SMA200. Il 27 ottobre MAIRE ha pubblicato i risultati dei 9 mesi; la watch Momentum è pensata per evidenziare episodi simili, qualora prezzi e calendario siano raggiungibili.

## Versione 1.5 (mantenuta)

- Nuova scheda **Zone**: osservazione del recupero controtrend, indipendente dalle due strategie esistenti. Non diventa un segnale confermato senza ulteriori validazioni.
- Per le zone usa solo informazioni disponibili alla data della candela: prezzo sotto SMA200, calo a 60 sedute, vicinanza al supporto precedente a 120 sedute, RSI(2), struttura della candela e turnover. Stop e target sono indicativi (supporto/ATR), non i livelli scelti discrezionalmente da Websim.
- Scansione delle ultime 35 sedute: conserva l'ultima zona idonea per ciascun titolo anche quando il prezzo successivamente cambia. Le date delle trimestrali visualizzate accanto allo **storico** possono essere note solo oggi e sono marcate esplicitamente per evitare di farle passare per backtest predittivo.
- Calendario informativo delle trimestrali previste nei prossimi 14 giorni e di quelle riportate negli ultimi 5; un solo batch TradingView, fino a 12 ore di cache. **Le date possono essere stime del provider** e non equivalgono a conferme degli emittenti.
- Notifiche Android (massimo una per società/data) se una zona attuale supera tutti e 4 i filtri aggiuntivi e i risultati sono previsti entro 3 giorni. Messaggio esplicito: osservazione, non segnale d'acquisto.
- Regressione indipendente su dati Yahoo del 29/10/2026: 128 società italiane, 57 zone recenti, 2 dati indisponibili; Stellantis aveva zona idonea il **27 ottobre** a 3,71€ vicino al supporto 3,50€. Nessun rendimento futuro è stato utilizzato per scegliere le zone.

## Funzionalità mantenute dalla 1.4

- **Universo dinamico italiano:** ogni 24h prova a scaricare gli azionari quotati su Milano; conserva sempre gli 84 ticker `.MI` della lista base e, se il feed non è raggiungibile, usa l'ultimo universo riuscito o la lista base.
- **Mid/Small Cap:** fasce di capitalizzazione *indicative* (Large ≥10 mld €, Mid 2–10 mld €, Small 100 mln–2 mld €) con pre-filtro di turnover medio giornaliero minimo (500k € per Large/Mid; 250k € per Small). Le fasce non equivalgono agli indici FTSE. Evitate le microcap sotto 100 mln €.
- Il filtro di turnover storico a 20 sedute resta 500k € per Large/Mid e per titoli della lista base di classe sconosciuta, 250k € per i nuovi Small. Storico, segnalazioni, diario e firma sono preservati dall'aggiornamento.
- Nel menu **Dati** vengono mostrati fonte, data, composizione e mancati aggiornamenti del listino. Nelle schede si mostra la fascia di mercato.
- La scoperta dinamica usa un endpoint screener non documentato, quindi può cambiare o essere bloccata; non si dichiara copertura completa né affidabilità garantita.

## Funzionalità mantenute dalla 1.3
- Recupero retrospettivo di tutte le segnalazioni reali degli ultimi 35 giorni di negoziazione: ogni barra viene valutata solo con le barre precedenti, senza dati futuri. I setup sono identificati come storici con la loro data originale, non come ingressi attuali.
- Interfaccia con scelta tra "Storico 45 giorni" (predefinito) e "Ultima seduta". Il conteggio rispetta il periodo scelto.
- Setup confermati: FORZA e DEBOLEZZA; soglie uguali alla versione 1.0.
- Titoli in formazione separati per direzione (fino a 20 per categoria), con conteggio delle condizioni superate e cause esatte dell'esclusione.
- I filtri Forza/Debolezza agiscono sia sui setup confermati sia sui candidati in formazione.
- Sezione **Bilanci**: P/E, P/B, ROE, crescita dei ricavi, margine netto, debito/equity e confronto relativo entro settore dove il campione è sufficiente.
- Fondamentali: un'unica richiesta batch HTTPS allo screener TradingView, aggiornamento ogni 12 ore; se il feed non risponde, mostra l'errore e non inventa valori. Dati non utilizzati come filtro vincolante.
- Le candele giornaliere parziali della sessione corrente vengono escluse fino alle 18:15 ora italiana. Se una cache è stata scaricata prima della chiusura, la scansione dopo la chiusura forza il nuovo download.
- La nuova versione avvia una scansione al primo lancio dopo l'aggiornamento, preservando lo storico e il diario della 1.1/1.2.
- Report dei ticker senza dati sufficienti e degli errori HTTP/DNS.
- Due tentativi in caso di errore DNS; stop dopo tre ticker consecutivi senza DNS.
- Storico fino a 45 giorni nella vista principale, con filtro opzionale per ultima seduta.
- SQLite locale per prezzi, segnali e diario; notifiche locali; job quotidiano Android (differibile dal sistema operativo).

## Limiti

- Feed Yahoo e TradingView non ufficiali per uso API: possono generare HTTP 403/429, anomalie o dati non aggiornati; i loro termini di utilizzo possono limitarne la distribuzione.
- Tutti i candidati e i livelli di prezzo sono euristici. Le regole non sono validate su uno storico italiano fuori campione.
- Non fa trading automatico, non fornisce raccomandazioni finanziarie.
- Non include intelligenza artificiale. I fondamentali provengono da un feed esterno, non dai documenti dell'emittente. La copertura può essere incompleta e gli ETF/microcap sono esclusi dalla scoperta dinamica.
- **Stellantis (`STLAM.MI`):** già incluso nel paniere base. Il 28/10/2026 aveva prezzo ~4,28 €, SMA200 ~5,67 €, SMA50 ~4,22 €; violava i filtri di trend delle strategie FORZA/DEBOLEZZA. Anche nella 1.5 il recupero sotto SMA200 resta watch-only, non una conferma di acquisto.

## Installazione

L'APK 2.0 usa **lo stesso package e la stessa chiave di firma dalla 1.1 alla 1.9**: `it.swingradar.italia.v11`.
Si installa sopra la 1.1–1.9 senza cancellare il suo database e diario. La prima APK 1.0 (`it.swingradar.app`) è un'altra app;
il suo diario resta separato e non viene trasferito.

## Build

Dipendenze Android SDK Platform 35, Build Tools 35.0.0, OpenJDK 17 (`java`, `javac`, `keytool`) e zip.
Lo script `build_apk.sh` assume la piattaforma in `../android_build_v11/android-35/android.jar`, i build tools in
`../android_build_v11/android-15/` e usa il compilatore integrato nel JDK.

Il file `build/local-signing-key.jks` è una **chiave privata di firma**, conservata solo nell'ambiente di compilazione
per permettere gli aggiornamenti di questo package Android. **Non è presente nell'archivio dei sorgenti scaricabile.**
Non pubblicare, non condividere e non committare la chiave su repository pubblici.
Questo progetto sperimentale usa una password di sviluppo nello script. Per distribuzione pubblica occorrono un keystore protetto
e un processo di rilascio dedicato.

## Test verificati

- `node tests/ui_smoke.js`: filtro radar, schede separate, metriche finanziarie, ricerca MAIRE, watch eventi, stati teorici e diario aperte/chiuse.
- `node tests/universe_smoke.js`: verifica delle 128 società distinte, incluso MAIRE e STLAM.
- `node tests/chart_smoke.js`: verifica che il grafico OHLC/candele funzioni con dati locali, supporti/resistenze, livelli setup, cambio periodo e scorrimento storico.
- `node tests/stats_smoke.js`: verifica primo ingresso dopo il segnale, costi e slippage, priorità dello stop, gap, target 1 contro target 2 alternativo, time exit, in corso, segnali deduplicati, confronto per direzione/segmento e navigazione alla vista Statistiche.
- `bash build_apk.sh`: compilazione, packaging e verifica firma APK v2/v3.
- È stato verificato dall'ambiente di sviluppo che il feed batch restituisce metriche effettive per diversi ticker italiani (ENI, CPR, ISP). La risposta può essere diversa dal telefono dell'utente.
- Il controllo indipendente `python3 tests/live_history_probe.py` del 29/10/2026 ha recuperato 82/84 ticker via Yahoo e contato **75 setup EOD storici** nel periodo degli ultimi 45 giorni, con le medesime condizioni FORZA/DEBOLEZZA. Questi segnali sono storici e non dimostrano rendimento o edge.
- **Non è stato eseguito un test fisico sul dispositivo Android dell'utente.**
