# Swing Radar Italia 3.0 — revisione specifiche concordate
Versione ricostruita a partire dal progetto Android Java originale 2.1.

## Navigazione concordata
Radar (solo setup attivi), Strategie (4 movimenti: Forza/Breakout, Debolezza/Pullback, Zone/Recupero controtrend, Momentum/Accelerazione-eventi), Titoli (fondamentali prioritari), Diario, Storico. Diagnostica dati accessibile dall'intestazione.

## Analisi
Tre indicatori distinti: Qualità aziendale, Valutazione/Fair Value, Timing tecnico. Priorità di ricerca fondamentale 65% qualità + 35% valutazione, solo se i dati sono disponibili; nessuna finta probabilità. Fair Value su multipli settoriali P/E e P/B con intervallo indicativo, affidabilità bassa/media, campione minimo di 3 comparabili per multiplo. Nessun DCF senza dati necessari; nessun modello bancario/assicurativo improvvisato. N/D se insufficiente. Sono valutazioni indicative, non InvestingPro.

## Stato dei segnali
Separazione fra attivi, in osservazione, invalidati e archiviati. Il motore originale conserva le proprie regole tecniche e i grafici; non viene dichiarato un edge non dimostrato. Le sezioni Zone ed Eventi sono moduli di ricerca, non segnali eseguibili.

## Compilazione
SDK Android 35, Build Tools 35.0.0, Java 17; `bash build_apk.sh`. GitHub Actions già predisposto. La firma di aggiornamento richiede la chiave originale 2.1.
