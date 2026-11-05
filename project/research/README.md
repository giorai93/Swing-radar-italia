# Ricerca ML sul mercato italiano — protocollo v1

> Verifica successiva: il v1 è superato come evidenza di performance. La selezione richiedeva prezzi futuri disponibili e il filtro nominale di 1 euro usava prezzi retrorettificati. Vedere `validation/RESULTS.md` per correzioni, portafoglio vincolato e limiti ancora aperti. I dati v1 restano archiviati per tracciabilità.
Esperimento esplorativo separato dai segnali Android. Non replica Websim e non ottimizza le quattro strategie dell'app. Studia una domanda più semplice: si possono selezionare dieci titoli con rendimento successivo migliore delle alternative elementari?

## Protocollo fissato prima dei risultati

- Universo: unione dei due elenchi italiani del progetto; viene pubblicata la copertura di ogni ticker, anche fallito. Non equivale a tutti i titoli storicamente quotati.
- Prezzi: Yahoo giornalieri, dieci anni disponibili. Cache originale e hash SHA256 in coverage.json. Il campo cache_mtime_utc documenta l'acquisizione locale, non la pubblicazione originaria del dato.
- Ultima data ammessa: 31 dicembre 2025. Il 2026 resta riservato a una successiva verifica; non si modifica il modello dopo averlo valutato sul 2026.
- Caratteristiche: rendimenti a 5/20/60/120/250 sedute, volatilità, distanza dalla media 200, volume relativo, ampiezza a 20 sedute. Nessun ticker come predittore; modello comune alle società. Nessun bilancio odierno applicato retroattivamente.
- Liquidità: mediana del controvalore a 20 giorni almeno 500.000 euro; prezzo almeno 1 euro alla data del segnale.
- Ogni 21 sedute ENI: classifica alla chiusura, ingresso alla successiva apertura, uscita e nuova entrata dopo 21 sedute. Nessuno stop/target. Periodi non sovrapposti.
- Modelli: regressione Ridge e gradient boosting poco profondo, parametri fissi nel codice. Confronti: universo equiponderato, dieci migliori per momentum 60 sedute, dieci peggiori per rendimento 5 sedute (ipotesi di rimbalzo, non mean reversion già validata).
- Training crescente e valutazioni annuali 2021–2025. Sono ammessi al training solo esempi con uscita precedente al primo gennaio dell'anno test. Nessuno split casuale tra righe dello stesso periodo. Standardizzazione appresa esclusivamente nel training.
- Importo teorico 1.000 euro **per posizione**, costi 2,95 euro per lato e slippage 5 punti base per lato. Dieci posizioni implicano circa 10.000 euro. Rendimenti approssimati con quantità frazionarie e costo fisso dello 0,69%; non simulazione eseguibile con capitale totale 1.000 euro.
- Output: media per operazione, frequenza positiva, profit factor, risultati annuali e intervalli bootstrap sui periodi interi. Nessun rendimento annualizzato o drawdown di portafoglio dichiarato sulla sola base di queste statistiche.

## Limiti che impediscono una validazione definitiva

L'universo odierno contiene sopravvissuti; mancano società delistate e composizione storica. I titoli senza prezzo in entrata/uscita sono esclusi, con possibile distorsione favorevole sui fallimenti/sospensioni. Le aperture rettificate sono una proxy di rendimento totale, non movimenti di cassa dividendi verificati. Dati corretti oggi dal provider possono differire da quelli disponibili allora. Universo equiponderato e top-ten hanno diverso numero di posizioni e capitale nominale. I dati non incorporano spread effettivo, impatto, fiscalità o fondamentali storici. I periodi non sono necessariamente indipendenti: il bootstrap non elimina questi limiti.

Una media positiva non dimostra un vantaggio ML: occorre battere le alternative su più anni, non dipendere da pochi titoli, verificare robustezza dei costi e poi il periodo riservato. Non scegliere per ogni titolo la strategia migliore guardando tutto lo storico.

## Esecuzione

Dalla radice repository:

```sh
python -m pip install -r project/research/requirements.txt
python -m unittest discover -s project/research -p 'test_*.py'
OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1 python project/research/italy_ml.py --out project/research/output
```

`output/raw` conserva risposte originali; `coverage.json` tutte le fonti e gli errori; `samples.csv` gli esempi; `oos_trades.csv` le selezioni fuori campione; `report.json` i risultati. Con la stessa cache il test è ripetibile. Un nuovo download può modificare lo storico e il suo intervallo di dieci anni.

Riferimento metodologico: https://scikit-learn.org/stable/modules/generated/sklearn.model_selection.TimeSeriesSplit.html . Il codice applica split annuali comuni a tutti i titoli e rimuove gli esiti che oltrepassano il confine temporale, invece di dividere righe individuali.
