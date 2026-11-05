# Primo esperimento eseguito — 1 novembre 2026

> Verifica successiva: il v1 è superato come evidenza di performance. La selezione richiedeva prezzi futuri disponibili e il filtro nominale di 1 euro usava prezzi retrorettificati. Vedere `validation/RESULTS.md` per correzioni, portafoglio vincolato e limiti ancora aperti. I dati v1 restano archiviati per tracciabilità.
121 ticker con storico sufficiente su 128 richiesti. Valutazione cronologica 2021–2025: 59 periodi, 590 selezioni per metodo top-ten. Parametri fissati prima di vedere i risultati. Nessuna ottimizzazione successiva. Il 2026 non è stato valutato.

| Metodo | Media netta per operazione (21 sedute) | Profit factor | Operazioni positive |
|---|---:|---:|---:|
| Acquisto dopo ribasso a 5 sedute | +1,18% | 1,35 | 51,5% |
| Momentum a 60 sedute | +0,58% | 1,16 | 48,1% |
| ML gradient boosting | +0,36% | 1,10 | 47,3% |
| ML regressione Ridge | −0,46% | 0,89 | 46,3% |

Costi ipotizzati inclusi: 0,69% per operazione su 1.000 euro per posizione. Non sono rendimenti annui o di un portafoglio con capitale totale 1.000 euro. Il confronto equiponderato su tutti gli eleggibili produce +0,62% medio per operazione, ma usa più posizioni/capitale nominale dei metodi top-ten.

**Non emerge un vantaggio del ML.** La regola di rimbalzo è la migliore osservata in questa prima prova, ma non è validata: la media del 2025 è +5,11%, contro −2,17% nel 2022 e +0,04% nel 2024. Il suo vantaggio medio per periodo sul momentum è +0,61 punti percentuali; intervallo bootstrap abbinato 95% da −0,47 a +1,78 punti, comprende zero. Il periodo comune, non la singola posizione correlata, è l'unità di ricampionamento.

Cinque test di integrità passati; seconda esecuzione dal dataset salvato riproduce medie e profit factor entro 1e-10. Questi test controllano il software, non certificano i dati finanziari o il profitto futuro.

Sono esclusi BPSO e ILTY (404), ICOP/NXT/SYS (storico insufficiente), OROX/TNXT (nessuna barra valida nel periodo ammesso). Lo storico odierno e l'esclusione dei prezzi mancanti possono favorire artificialmente i risultati. Mancano universo storico comprensivo dei delisting, dati disponibili all'epoca certificati e fondamentali storici. Non è una replica del backtest stop/target Android e non va confrontato direttamente con il suo R medio.

## Verifica dei risultati

- `snapshots/report.json`: metriche complete per anno, confini training/test, confronti abbinati e dettagli sui quattro ticker citati (non operazioni Websim).
- `snapshots/coverage.json`: URL, date di acquisizione, hash delle risposte originali e fallimenti.
- `snapshots/samples.csv.gz`: esempi di mercato e caratteristiche usati nel training e nella verifica; sufficiente per riprodurre il fit, non per verificare da zero tutti i prezzi OHLC originali.
- `snapshots/oos_trades.csv.gz`: selezioni effettive dei modelli fuori campione.
- Le risposte originali restano nella cache locale della prima esecuzione; non sono archiviate nel repository. Un nuovo download può differire per revisioni del provider.

Comando di riproduzione senza rete:

```sh
OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1 python project/research/italy_ml.py --replay project/research/snapshots/samples.csv.gz --out /tmp/ml-replay
```

Passo successivo: universo storico e audit dei prezzi, portafoglio con capitale vincolato e sensibilità ai costi, quindi verifica finale sul periodo 2026 riservato. Non mettere in produzione il metodo migliore di questa tabella scegliendolo a posteriori.
