# Manuale documentale Stockbee — quattro metodi

Versione 0.1 · verifica delle fonti al 1 novembre 2026 · mercato di origine: USA.

Questo documento separa quanto pubblicato da Pradeep Bonde dalle scelte ancora necessarie per una replica. Non è un manuale ufficiale, una raccomandazione di trading o una strategia già validata. Una pubblicazione recente non aggiorna automaticamente tutte le regole precedenti. Nessun accesso all'area membri e nessuna lettura completa dei video è stato effettuato.

**Esito:** possiamo preparare scanner e schede di valutazione. Nessuno dei quattro metodi ha ancora una specifica completa e verificata per un backtest dichiarato fedele. Le parti mancanti non sono sostituite con parametri arbitrari.

## Legenda e tracciabilità

- **D — Documentato:** presente in una fonte diretta, con data e ambito. Non significa ancora valido oggi per ogni variante.
- **Q — Discrezionale:** il criterio esiste, ma la fonte non fornisce una definizione numerica sufficiente.
- **M — Mancante:** non accertato nelle fonti esaminate; non significa che l'autore non lo abbia mai pubblicato.
- **P — Proposta nostra:** struttura dei dati o procedura di verifica; non è una regola Stockbee.

Le fonti S01–S12 sono elencate in fondo. Ogni operazione futura deve conservare versione delle regole e fonti utilizzate. Le date delle pubblicazioni vanno distinte dalle date degli esempi e dalla data della nostra consultazione.

## Quadro dei quattro metodi

| Metodo | Selezione accertata | Entrata accertata | Uscita completa | Prontezza |
|---|---|---|---|---|
| Momentum Burst | Pattern qualitativo di espansione dopo consolidazione [S01] | Ricerca del movimento iniziale; trigger esatto da versionare | No: indicazione storica di parziale e trailing [S03] | Scanner assistito; replica automatica incompleta |
| Anticipation | Due scanner storici e revisione grafica [S02] | Esempio di livelli preparati e ingresso all'avvio della rottura [S04] | No | Watchlist assistita; replay intraday da definire |
| EP classico | Cambiamento sostanziale, sorpresa, precedente scarso interesse [S06,S08] | Modalità dipendente dal caso; regola univoca non accertata | No | Ricerca dei catalizzatori; backtest fedele bloccato |
| EP 9 milioni | Volume >8,9 milioni e classificazione dei candidati [S08] | Trigger esecutivo completo non accertato | No | Scanner della soglia; nessuna equivalenza con un ordine |

## 1. Momentum Burst

**D, selezione — S01, 12 luglio 2016.** Il pattern riguarda un'espansione del range dopo una consolidazione ordinata. Il volume aumenta rispetto al giorno precedente; la candela precedente è stretta o negativa; la chiusura del breakout è vicina ai massimi. L'autore considera anche regolarità del trend precedente e assenza di ampie oscillazioni nella base.

**Q.** “Ordinato”, “stretto”, “vicino ai massimi” e “linearità” non sono una formula completamente specificata. Il nostro precedente limite del 75% della candela e la mediana a dieci giorni erano traduzioni nostre. Non diventano regole ufficiali.

**D storico, gestione — S03.** In un commento del 26 aprile 2020 a un articolo del 2014, Bonde indica tre giorni di mantenimento, poi realizzo parziale e stop sulla parte restante. La quota, il calcolo dello stop e le eccezioni non sono definiti. Non equivale a uscire interamente alla quinta seduta, né a un target 3R.

**Fondamentali.** Le fonti qui usate non impongono uno stesso filtro fondamentale a ogni Momentum Burst. Un catalizzatore può essere rilevante, ma non si può imporre il filtro EP dei ricavi a tutti i burst.

**M prima della replica:** variante precisa (4%, dollar breakout, range expansion), trigger e tipo di ordine, tolleranza al gap, stop iniziale, quantità, regole del parziale, trailing, uscita in caso di mancata progressione e filtro di contesto applicabile. Servono barre intraday se l'entrata avviene durante l'espansione: la chiusura giornaliera non può essere utilizzata prima che sia nota.

## 2. Anticipation

**D storico, scanner — S02, 14 agosto 2014.** Universo dichiarato: US common stocks. Prima selezione con prezzo/minimo 252 sedute >=1,8 e minimo volume delle tre sedute precedenti >=100.000; seconda con SMA7/SMA65 >1,05 e minimo volume delle tre sedute precedenti >100.000. In entrambe, variazione giornaliera fra -1% e +1%. Le liste vengono unite e poi esaminate visivamente. Prima di implementare la formula TC2000 si deve verificare l'unità di volume della piattaforma/versione.

**Q, revisione — S02.** Consolidazione/pullback di 3–10 giorni, range e volumi contenuti, trend regolare, niente recente ribasso del 4% e niente tre chiusure positive consecutive. Un post del settembre 2024 richiama anche due o tre giornate strette [S11]: non basta per concludere che ogni condizione storica sia stata sostituita.

**D, esempio di entrata — S04, 3 aprile 2017.** Su VIAV l'autore prepara un intervallo d'ingresso 10,80–10,95 dollari se parte la rottura e uno stop iniziale a 10,50. Descrive possibili aggiornamenti in base ai minimi intraday. Sono livelli di quell'esempio, non offset universali. L'appartenenza alla watchlist non è un ordine di acquisto all'apertura successiva.

**Fondamentali.** Si possono ricercare i titoli della watchlist; non è accertato un requisito universale di crescita o sorpresa per questa famiglia.

**M prima della replica:** costruzione oggettiva del pivot, validità temporale dell'ordine, gestione degli ingressi anticipati rispetto a quelli sulla rottura, buffer e tipo di ordine, gap oltre il livello, stop e aggiornamenti, uscita e dimensionamento. La sequenza dei prezzi richiede dati intraday coerenti con l'orario del segnale.

## 3. Episodic Pivot classico

**D, selezione — S06 (10 luglio 2023), S08 (slide 2024).** Occorre un evento che cambi le aspettative; nei casi legati ai risultati, sorpresa su utili o vendite e aspettative precedenti contenute sono elementi centrali. Le slide distinguono il classico da altre varianti e descrivono contesto di scarso interesse per il titolo e caratteristiche societarie. Non basta un gap.

**D, variante di selezione growth — S07, 1 settembre 2025.** Uno scanner cerca società costituite o quotate negli ultimi dieci anni, capitalizzazione inferiore a 11 miliardi di dollari e due trimestri con crescita dei ricavi almeno del 39%. Il testo introduttivo cita invece meno di 10 miliardi. La differenza resta aperta. “Costituita oppure quotata” non va ridotto silenziosamente a “IPO”. La definizione esatta dei campi MarketSurge, compresa la base temporale della crescita, deve essere confermata prima di tradurli in codice.

**Q.** Rilevanza dell'evento, precedente scarso interesse, novità della crescita, sostenibilità e aspettative del mercato richiedono evidenza e giudizio. Il solo bilancio annuale non consente di ricostruire tutte queste condizioni.

**D, entrate ritardate — S06,S10.** L'autore considera opportunità dopo un pullback/consolidazione successivo alla notizia e pubblica scanner growth/turnaround dedicati. Non si deve mescolare il risultato di queste entrate con quello dell'ingresso nel giorno dell'annuncio.

**M prima della replica:** criterio esatto di sorpresa per la variante prescelta, timestamp del consenso, filtro di neglect, modalità d'ingresso, stop, parziali, trailing, scadenza del catalizzatore, dimensionamento. Senza archivio degli annunci e dei dati disponibili all'epoca, i candidati prezzo-volume restano candidati tecnici, non EP confermati.

## 4. EP 9 milioni

**D — S08.** Le slide riportano volume >8,9 milioni di azioni come ricerca iniziale, poi distinguono catalizzatori reali, storie di mercato, titoli istituzionali e reazioni ritardate. Non tutte le varianti richiedono una sorpresa sugli utili.

**D recente — S09, 31 luglio 2026.** Il processo pre-market parte da movimenti e volumi, ordina i candidati e prosegue con ricerca tramite servizi informativi. Nella pagina compaiono anche scanner EP9 in immagini. Il fatto che una lista sia ordinata per volume non specifica automaticamente prezzo d'ingresso, volume proiettato o volume totale della seduta.

**M.** Verificare se e quando il volume è cumulativo della sessione, pre-market o proiettato per la specifica variante; confermare universo e unità. Non leggere il volume finale della giornata per simulare un acquisto nella stessa mattina. Non trattare la soglia 8,9 milioni come prova di un evento fondamentale.

**Italia.** Sostituire la soglia con RVOL o controvalore crea una variante nostra, da nominare e testare separatamente. Anche la soglia originale va verificata con volumi realmente comparabili.

**M prima della replica:** classificazione dei candidati, trigger, stop, uscite, parziali, condizioni di esclusione e dimensionamento. L'aggiunta precedente di breakout a venti sedute era nostra.

## Rischio e contesto: regole trasversali da non confondere

**D — S05, 26 novembre 2023.** Quando Bonde parla di rischio del 2,5% o meno in quel post, chiarisce che si riferisce alla distanza tra entrata e stop rispetto al prezzo; non al 2,5% del conto. È una preferenza descritta in quel contesto, non un obbligo accertato per ogni EP.

**D — S11, settembre 2024.** Descrive prese di profitto parziali e adattamento al contesto attraverso Market Monitor/Situational Awareness; afferma inoltre di avere sviluppato alternative agli EP classici a causa dei grandi gap iniziali. Questo mostra evoluzione del metodo, ma non fornisce tutti i parametri di gestione.

**M.** Soglia operativa di breadth/regime per ciascuna famiglia, rischio sul conto, esposizione totale e gestione di titoli correlati. Non inventare tali valori e attribuirli all'autore. Il vecchio flusso del 2014 contiene un avviso dell'autore del 2021 sulla sua mancata corrispondenza al trading corrente [S12].

## Scheda fondamentale e del catalizzatore — progetto dati P

Questa è la nostra specifica di raccolta, non un nuovo punteggio Stockbee. Ogni valore deve avere unità, periodo, fonte, timestamp di pubblicazione e momento in cui era disponibile al sistema.

| Campo | Evidenza richiesta | Trattamento se manca |
|---|---|---|
| Identità | Emittente, ISIN/LEI se disponibile, mercato e ticker valido alla data | Non unire società solo per ticker |
| Annuncio | URL emittente/archivio, data e ora con fuso, titolo e tipo evento | Evento non verificato |
| Ricavi | Valore, valuta, periodo e confronto omogeneo | Crescita non calcolabile |
| EPS | Diluito/base, riportato/rettificato, periodo e comparabile | Non mescolare definizioni |
| Consenso precedente | Fonte, data rilevazione e metrica confrontabile | Sorpresa non misurabile |
| Guidance | Precedente e nuova, data di entrambe, range e metrica | Nessuna classificazione inventata |
| Margini | Numeratore/denominatore coerenti, ricorrenza e rettifiche | Analisi incompleta |
| Crescita | Distinguere annuale, trimestrale YoY, sequenziale e organica | Non sostituire una con l'altra |
| Neglect | Storico prezzo/volume e dati di copertura/partecipazione disponibili allora | Valutazione manuale dichiarata |
| Reazione | Prezzi e volumi con sessione, venue e orario | Nessuna conferma tecnica |
| Valutazione | Evento confermato, da rivedere o scartato; motivazione e revisore | Resta da rivedere |

Se il periodo precedente ha ricavi zero o EPS negativi/quasi zero, non assegnare automaticamente percentuali di crescita interpretabili come quelle su una base positiva. Separare turnaround da crescita ordinaria. Il confronto con consenso è diverso dal confronto con l'anno precedente. Un risultato pubblicato dopo la chiusura diventa utilizzabile soltanto dopo quella pubblicazione.

Bilanci ESEF: utili per lo storico annuale e la provenienza dei fatti. Per gli EP servono anche comunicati e risultati infrannuali; il consenso storico richiede una fonte separata. Un dato contabile rettificato oggi non è automaticamente quello conosciuto al momento del segnale. I punteggi Quality/Growth/Valuation del progetto non sostituiscono la ricerca del catalizzatore.

## Verifica della replica prima di nuovi rendimenti — procedura P

1. Fissare una variante e una versione delle fonti per ogni metodo; non mescolare anni per completare artificialmente le regole.
2. Registrare ogni campo operativo mancante. Il registro JSON allegato mantiene disabilitato il backtest fedele finché questi campi non sono risolti.
3. Preparare esempi didattici dell'autore e casi scartati. VIAV [S04] è un esempio per verificare la preparazione dei livelli; non è un trade rieseguito da noi.
4. Ricostruire ogni caso usando dati disponibili in quel momento, distinguendo conferma del pattern da riempimento reale dell'ordine.
5. Solo dopo questa verifica, congelare regole e universo. Misurare tutti i segnali del periodo, non soltanto gli esempi vincenti pubblicati dall'autore.
6. Simulare un conto con disponibilità limitata e posizioni concorrenti, costi, gap e operazioni societarie. Stabilire prima del test come trattare dati mancanti e titoli delistati.
7. Tenere separate replica USA e adattamento italiano. Il 2026 non viene consumato nel frattempo come campione finale.

### Domande da risolvere nelle fonti successive

- Momentum: quale scanner/versione, entrata intraday o a chiusura, regola di stop, percentuale del parziale e trailing?
- Anticipation: come viene scelto il pivot, quando si anticipa senza rottura e quando si attende, gestione del fallimento e uscita?
- EP classico: quali combinazioni di crescita/sorpresa/neglect per ciascuna variante, entrata nel giorno evento oppure ritardata, gestione diversa?
- EP9: quale sessione e misura del volume, categorizzazione dei titoli, ingresso e uscita per categoria?
- Tutti: regole attuali di esposizione, rischio sul conto e contesto di mercato?

Non serve chiedere all'utente di inventare queste regole. Le prossime fonti da verificare sono i video ufficiali con timestamp, i dettagli delle immagini degli scanner e gli eventuali materiali membri ottenuti legittimamente. I PDF della comunità possono contenere metodi di altri autori: per esempio la SiP Trading Guide consultata è firmata “Bones”, quindi non è attribuita automaticamente a Pradeep Bonde.

## Stato dei vecchi backtest

`study.py`, `PROTOCOL.md`, `RESULTS.md` e `results/` restano l'archivio dell'esperimento precedente. Le medie e i Profit Factor non sono cancellati, ma non descrivono la redditività delle strategie ufficiali. Target 3R, stop a 0,1 ATR sotto il minimo, entrata generalizzata al prossimo open e uscita integrale a 5/20 giorni non sono una specifica verificata Stockbee.

In questa revisione non sono stati eseguiti nuovi backtest, nuovi test di profittabilità o build APK. È stata completata la verifica documentale qui descritta; non è stata completata la replica operativa.

## Fonti primarie e copertura della lettura

| ID | Fonte | Data e lettura |
|---|---|---|
| S01 | [Guidelines to find good momentum burst setups](https://stockbee.blogspot.com/2016/07/guidelines-to-find-good-momentum-burst.html) | 12/07/2016; testo esaminato nella ricerca precedente |
| S02 | [How I generate my breakout anticipation watchlist](https://stockbee.blogspot.com/2014/08/how-i-generate-my-breakout-anticipation.html) | 14/08/2014; testo/formule e commenti esaminati |
| S03 | [Swing trading using momentum bursts](https://stockbee.blogspot.com/2014/01/swing-trading-using-momentum-bursts.html) | Articolo 2014; risposta autore 26/04/2020 letta nell'indice; apertura diretta intermittentemente bloccata |
| S04 | [How to get in to Anticipation setups quickly](https://stockbee.blogspot.com/2017/04/how-to-get-in-to-anticipation-setups.html?m=0) | 03/04/2017; testo e commenti esaminati |
| S05 | [How I calculate risk on a trade](https://stockbee.blogspot.com/2023/11/how-i-calculate-risk-on-trade.html) | 26/11/2023; testo esaminato |
| S06 | [Earnings Episodic Pivots season is starting](https://stockbee.blogspot.com/2023/07/earnings-episodic-pivots-season-is.html) | 10/07/2023; testo esaminato |
| S07 | [Find the young Episodic Pivots](https://stockbee.blogspot.com/2025/09/find-young-episodic-pivots.html) | 01/09/2025; testo e risposta dell'autore esaminati |
| S08 | [Trade Factory, PDF](https://impact.traders4acause.org/wp-content/uploads/2024/11/02-Pradeep-Trade-Factory-2.pdf) | Cartella evento 2024; testo del PDF esaminato; data esatta non accertata |
| S09 | [How to Find and Research Pre-Market Winners](https://stockbee.blogspot.com/2026/07/how-to-find-and-research-pre-market.html) | 31/07/2026; testo esaminato; formule nelle immagini da verificare |
| S10 | [Scans Irun in Marketsurge to find Delayed Reactions EP](https://stockbee.blogspot.com/2025/09/scans-irun-in-marketsurge-to-find.html) | 01/09/2025; testo esaminato; immagini individuate, criteri completi non trascritti/validati |
| S11 | [Archivio settembre 2024](https://stockbee.blogspot.com/2024/09/) | Testi indicizzati su problemi di trading e anticipation del 26/09; regole numeriche di uscita incomplete |
| S12 | [My swing trading Process Flow](https://stockbee.blogspot.com/2014/07/my-swing-trading-process-flow.html) | 30/07/2014; avviso autore 18/09/2021 letto nella ricerca precedente |

Questo elenco è la base di evidenza del manuale, non la dichiarazione di avere letto tutto il materiale esistente.
