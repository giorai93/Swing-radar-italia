# Manuale documentale Stockbee — quattro metodi

Versione 0.2 · verifica delle fonti al 1 novembre 2026 · mercato di origine: USA.

Questo documento separa quanto pubblicato da Pradeep Bonde dalle scelte ancora necessarie per una replica. Non è un manuale ufficiale, una raccomandazione di trading o una strategia già validata. Una pubblicazione recente non aggiorna automaticamente tutte le regole precedenti. Nessun accesso all'area membri e nessuna lettura completa dei video è stato effettuato.

**Esito:** possiamo preparare scanner e schede di valutazione. Nessuno dei quattro metodi ha ancora una specifica completa e verificata per un backtest dichiarato fedele. Le parti mancanti non sono sostituite con parametri arbitrari. La versione 0.2 aggiunge uscite storiche, versioni dello stop EP e un valutatore separato degli scanner fondamentali.

## Legenda e tracciabilità

- **D — Documentato:** presente in una fonte diretta, con data e ambito. Non significa ancora valido oggi per ogni variante.
- **Q — Discrezionale:** il criterio esiste, ma la fonte non fornisce una definizione numerica sufficiente.
- **M — Mancante:** non accertato nelle fonti esaminate; non significa che l'autore non lo abbia mai pubblicato.
- **P — Proposta nostra:** struttura dei dati o procedura di verifica; non è una regola Stockbee.

Le fonti S01–S16 sono elencate in fondo. Ogni operazione futura deve conservare versione delle regole e fonti utilizzate. Le date delle pubblicazioni vanno distinte dalle date degli esempi e dalla data della nostra consultazione.

## Quadro dei quattro metodi

| Metodo | Selezione accertata | Entrata accertata | Uscita completa | Prontezza |
|---|---|---|---|---|
| Momentum Burst | Pattern qualitativo di espansione dopo consolidazione [S01] | Ricerca del movimento iniziale; trigger esatto da versionare | Parziali storici specificati; ambiguità esecutive residue [S13,S14] | Scanner assistito; replica automatica incompleta |
| Anticipation | Due scanner storici e revisione grafica [S02] | Esempio di livelli preparati e ingresso all'avvio della rottura [S04] | No | Watchlist assistita; replay intraday da definire |
| EP classico | Cambiamento sostanziale, sorpresa, precedente scarso interesse [S06,S08] | Modalità dipendente dal caso; regola univoca non accertata | No | Ricerca dei catalizzatori; backtest fedele bloccato |
| EP 9 milioni | Volume >8,9 milioni e classificazione dei candidati [S08] | Trigger esecutivo completo non accertato | No | Scanner della soglia; nessuna equivalenza con un ordine |

## 1. Momentum Burst

**D, selezione — S01, 12 luglio 2016.** Il pattern riguarda un'espansione del range dopo una consolidazione ordinata. Il volume aumenta rispetto al giorno precedente; la candela precedente è stretta o negativa; la chiusura del breakout è vicina ai massimi. L'autore considera anche regolarità del trend precedente e assenza di ampie oscillazioni nella base.

**Q.** “Ordinato”, “stretto”, “vicino ai massimi” e “linearità” non sono una formula completamente specificata. Il nostro precedente limite del 75% della candela e la mediana a dieci giorni erano traduzioni nostre. Non diventano regole ufficiali.

**D storico, gestione — S13,S14.** La ricerca successiva ha trovato articoli dedicati alle uscite nel 2018 e 2019. Specificano parziali di almeno metà posizione e condizioni aggiuntive. Si veda la sezione di aggiornamento: la precedente lacuna non significava assenza di documentazione pubblica. Restano decisioni esecutive e attualità da verificare.

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

**D, variante di selezione growth — S07, 1 settembre 2025 (con precisazione dalle immagini S10 sotto).** Uno scanner cerca società costituite o quotate negli ultimi dieci anni, capitalizzazione inferiore a 11 miliardi di dollari e due trimestri con crescita dei ricavi almeno del 39%. Il testo introduttivo cita invece meno di 10 miliardi. La differenza resta aperta. “Costituita oppure quotata” non va ridotto silenziosamente a “IPO”. La definizione esatta dei campi MarketSurge, compresa la base temporale della crescita, deve essere confermata prima di tradurli in codice.

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
| S10 | [Scans Irun in Marketsurge to find Delayed Reactions EP](https://stockbee.blogspot.com/2025/09/scans-irun-in-marketsurge-to-find.html) | 01/09/2025; testo e prime cinque immagini esaminati; campi trascritti sotto, intervalli data troncati e definizioni vendor ancora da confermare |
| S11 | [Archivio settembre 2024](https://stockbee.blogspot.com/2024/09/) | Testi indicizzati su problemi di trading e anticipation del 26/09; regole numeriche di uscita incomplete |
| S12 | [My swing trading Process Flow](https://stockbee.blogspot.com/2014/07/my-swing-trading-process-flow.html) | 30/07/2014; avviso autore 18/09/2021 letto nella ricerca precedente |

Questo elenco è la base di evidenza del manuale, non la dichiarazione di avere letto tutto il materiale esistente.

## Aggiornamento 0.2: lacune risolte e differenze da conservare

### Uscite swing documentate, versione 2018/2019

**S13, 27 luglio 2018:** stop al minimo della giornata d'ingresso oppure basato su metà range, secondo il punto d'entrata; preferenza per distanza inferiore al 4%, idealmente al 2%. Realizzo di almeno metà posizione alla chiusura del terzo/quinto giorno; metà anche su un rialzo dell'8% nel giorno d'ingresso o successivo. Protezione intraday a 25–50 centesimi dal massimo. Vendita su gap del 20% nei giorni indicati; eventuale resto gestito sui minimi giornalieri dopo il terzo giorno.

**S14, 12 luglio 2019:** ribadisce stop, parziale di almeno metà al terzo/quinto giorno, metà su rialzo rapido dell'8% e uscita su gap del 20%. Descrive un trailing senza fissare nuovamente l'offset. Prevede uscita se il titolo non progredisce entro tre/cinque giorni, con riferimento al dollar breakout per cinque giorni.

**Ambiguità ancora operative (nostra analisi):** quale minimo era già osservabile al momento dell'ordine; base di calcolo del gap e dell'8%; conteggio del giorno d'ingresso; priorità fra stop e parziale; quota esatta quando la fonte dice almeno metà; definizione di mancata progressione; scelta fra 25 e 50 centesimi. Non usare il minimo finale della giornata per una decisione mattutina. Queste sono versioni storiche, non l'attestazione delle regole attuali. Non estenderle automaticamente all'EP.

### Stop EP: due indicazioni storiche diverse

**S15, risposta autore del 5 gennaio 2022:** a una domanda sullo stop immediatamente dopo l'ingresso EP, indica 2,5% sotto l'entrata. È una risposta concreta, con ambito limitato; non certifica tutte le varianti successive.

**S16, risposta autore del 30 settembre 2010:** descrive ingressi pre-market/apertura, stop sul minimo dei due giorni precedenti e uscite in quattro parti con obiettivo del 20% o più. Non precisa tutti i livelli o la gestione delle quattro parti. Le due versioni non vengono fuse.

### Scanner fondamentali: immagini esaminate del 1 settembre 2025 [S10]

Trascrizione dei riepiloghi, non di un criterio soltanto selezionato nell'editor:

| Profilo | Filtri letti |
|---|---|
| Bucket 1, primo | Ricavi ultimo trimestre: crescita >=39%; media crescita ricavi 2Q >=39%; vendite annue >=25 milioni USD; volume medio 50 giorni >=100.000 azioni; prezzo >=1 USD |
| Bucket 1, secondo | Crescita ricavi ultimo trimestre >=99%; vendite annue >=25 milioni USD; volume medio 50 giorni >=100.000; prezzo >=1 USD |
| Bucket 2 | Crescita EPS ultimo trimestre e trimestre precedente ciascuna >=39%; crescita ricavi ultimo trimestre e media 2Q ciascuna >=20%; vendite annue >=25 milioni USD; volume medio 50 giorni >=200.000; prezzo >=3 USD |
| Bucket 0, due varianti | Filtri growth più capitalizzazione <=11 miliardi USD; date di costituzione/IPO: intervallo troncato nell'immagine, non ricostruito |

La media 2Q non richiede che entrambi i trimestri superino la soglia. Il periodo esatto di confronto dei campi va confermato nella definizione MarketSurge. L'etichetta di un filtro earnings surprise mostrata a sinistra non prova che sia un criterio attivo.

### Primo componente eseguibile: valutatore fondamentale

`fundamental_screen.py` valuta soltanto i tre profili Bucket 1/2 sopra; Bucket 0 resta sospeso per le date incomplete. Non seleziona un profilo in base ai rendimenti. Non usa i valori annuali ESEF al posto dei campi trimestrali richiesti.

Input: JSON con `issuer` e `facts`. Ogni campo contiene `value`, `period`, `source_url`, `published_at`, `available_at` e `definition_verified`. I timestamp richiedono fuso. Un valore futuro, privo di provenienza, non finito o con definizione non verificata resta sconosciuto. Un fallimento certo esclude dal profilo; se non ci sono fallimenti ma manca un campo il risultato è sconosciuto, non positivo. Una verifica positiva significa soltanto che i filtri del profilo passano.

```
python research/stockbee/fundamental_screen.py facts.json --as-of 2025-09-01T12:00:00Z
python -m unittest discover -s research/stockbee -p 'test_fundamental_screen.py' -v
```

Sono stati eseguiti e superati nove test sintetici: soglie, dati futuri/mancanti, provenienza, definizioni, numeri non validi e distinzione media/due trimestri. Nessuna azienda reale è stata dichiarata conforme e nessun nuovo backtest è stato eseguito. Il controllo dei timestamp non certifica da solo la correttezza delle fonti o la freschezza dei prezzi: resta un requisito dell'acquisizione dati.

### Nuove fonti

| ID | Fonte | Lettura |
|---|---|---|
| S13 | [My exit guidelines for momentum burst swing trades](https://stockbee.blogspot.com/2018/07/my-exit-guidelines-for-momentum-burst.html) | 27/07/2018; testo indicizzato esaminato; apertura web intermittentemente bloccata |
| S14 | [Exit guidelines for swing trades](https://stockbee.blogspot.com/2019/07/exit-guidelines-for-swing-trades.html) | 12/07/2019; testo indicizzato esaminato |
| S15 | [Always Use Stops](https://stockbee.blogspot.com/2022/01/always-use-stops.html) | Post 04/01/2022; risposta autore 05/01/2022 letta; video non visionato |
| S16 | [What are Episodic Pivots and how to find them](https://stockbee.blogspot.com/2010/02/what-are-episodic-pivots-and-how-to.html?m=0) | Post 12/02/2010; testo letto e risposta autore 30/09/2010 recuperata nell'indice |
