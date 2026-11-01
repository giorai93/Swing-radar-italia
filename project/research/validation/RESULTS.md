# Verifica v2 eseguita — 1 novembre 2026

**Esito: ricerca positiva solo sul capitale maggiore, non validata e non pronta per l'app. Il test finale 2026 NON è stato eseguito.**

Sono stati corretti due problemi del primo esperimento: la scelta non richiede più un prezzo futuro di uscita e la soglia nominale di 1 euro viene applicata dopo la ricostruzione degli split. Esempio TIM al 2 gennaio 2024: la cache mostra 2,993 euro retrorettificati; la ricostruzione dà 0,2993 euro nominali e quindi il titolo non passa la soglia. Il raggruppamento 1:10 è documentato dal comunicato TIM citato in `nominal_price_audit.json`. Questo non certifica tutte le altre rettifiche societarie del feed.

## Simulazione con capitale limitato

Dati disponibili prima del 2026: 124 ticker con almeno una barra, eleggibilità verificata volta per volta con almeno 250 sedute di indicatori. 4.947 candidati ai segnali, 60 ribilanciamenti, 1.271 sedute di calendario 2021–2025. L'ultimo periodo è liquidato alla chiusura dell'ultima seduta del campione, anche se inferiore a 21 sedute. Nessuna leva o versamento aggiuntivo, acquisti in azioni intere, massimo dieci posizioni. Dividendi lordi tenuti in riserva non reinvestibile per mancanza delle date di pagamento. Commissioni base 2,95 euro per ordine e slippage/spread modellato 5 punti base per lato.

| Metodo | Capitale iniziale | Patrimonio finale simulato | Totale 2021–2025 | CAGR | Massimo calo su chiusure giornaliere |
|---|---:|---:|---:|---:|---:|
| Rimbalzo 5 sedute | 1.000,00 € | 83,86 € | -91,61% | -39,17% | -92,44% |
| Momentum 60 sedute | 1.000,00 € | 99,62 € | -90,04% | -37,04% | -90,92% |
| Rimbalzo 5 sedute | 10.000,00 € | 21.630,24 € | 116,30% | 16,74% | -37,37% |
| Momentum 60 sedute | 10.000,00 € | 10.867,18 € | 8,67% | 1,68% | -45,24% |

Per i valori esatti e non arrotondati fa fede `report.json`. Sono risultati esplorativi, non rendimenti realizzati o attesi. Il risultato su 1.000 euro mostra l'insostenibilità della combinazione dieci posizioni/ordini piccoli/costi fissi: 20 ordini da 2,95 euro costano 59 euro a rotazione, pari al 5,9% del capitale iniziale. Il capitale eroso impedisce successivi acquisti; nessun denaro viene aggiunto per continuare artificialmente il test.

## Costi e concentrazione

Rimbalzo su 10.000 euro:

| Costo ipotizzato | Rendimento complessivo | Massimo calo |
|---|---:|---:|
| Senza commissioni e slippage, solo diagnosi | +203,43% | −33,29% |
| 2,95 euro/ordine e 5 bp/lato | +116,30% | −37,37% |
| 2,95 euro/ordine e 15 bp/lato | +89,92% | −38,76% |
| 2,95 euro/ordine e 30 bp/lato | +54,51% | −40,95% |

Caso base: 600 operazioni chiuse, 3.540 euro di commissioni e 765,29 euro di slippage cumulati. Rendimento per anno: 2021 +43,94%; 2022 −27,82%; 2023 +24,15%; 2024 +0,60%; 2025 +66,69%.

Escludendo a posteriori i tre maggiori contributori (AVIO, Stellantis, CRL) senza rimpiazzi e lasciando gli slot in cassa, il totale scende a +52,29% e il CAGR a +8,80%; massimo calo −37,80%. È una sensibilità alla concentrazione, non un metodo da ottimizzare né un test indipendente. Anche il risultato base supera il riferimento di rischio del 30% di drawdown.

## Qualità e copertura

- Archivio ufficiale Borsa Italiana: https://www.borsaitaliana.it/borsaitaliana/statistiche/statistiche-storiche/revoche.en.pdf . Il documento consultato arriva al 30 dicembre 2024; non costituisce l'universo completo per data.
- Verificati nomi/date di otto revoche e provati i candidati simboli Yahoo per Atlantia, Autogrill, Cerved, Tod's, Saras, UnipolSai, Salcef, IMA: otto risposte HTTP 404. Un errore su un simbolo non dimostra l'inesistenza dei dati presso ogni fonte. Nessun prezzo sintetico o riempimento è stato aggiunto.
- Fonte alternativa documentata, ma non scaricata/verificata per Milano: https://eodhd.com/financial-apis/delisted-stock-companies-data-2 . Richiede accesso API e verifica dell'identità e della copertura degli strumenti. Nessun acquisto di dati effettuato.
- Nel portafoglio rimbalzo base: 16 giornate-posizione valorizzate con ultima quotazione disponibile, nessuna posizione irrisolta a fine test. I prezzi obsoleti possono attenuare il drawdown osservato.
- Le date di pagamento dei dividendi, i diritti/aumenti di capitale, i corrispettivi dei delisting e l'universo storico non sono certificati. La gestione delle frazioni da raggruppamenti è approssimata. Il test resta prima delle imposte, senza costi di impatto/liquidità verificati su book.
- Verifica campione Fincantieri: rapporto 1:10 e data 17 giugno 2024 confermati dall'emittente, che documenta anche la liquidazione delle frazioni e un successivo aumento di capitale: https://www.fincantieri.com/it/newsroom/news-e-comunicati-stampa/2024/raggruppamento-azionario . Non equivale alla verifica di tutti gli eventi.

## Riproducibilità e blocco 2026

15 test locali superati: selezione indipendente dalle barre future, ingresso mancante senza rimpiazzo, mancata uscita mantenuta, azioni intere, riconciliazione contabile, costi, split, dividendi contati una volta, rifiuto del 2026. Il programma salva ordini, operazioni e patrimonio giornaliero per ogni scenario; lo snapshot contiene prezzi e candidati usati, fonti con hash e calendario.

```sh
python -m unittest discover -s project/research -p 'test_*.py'
python project/research/portfolio_validation.py --out /tmp/portfolio-replay
```

Il blocco del test finale è esplicito in `report.json`: universo storico incompleto e operazioni societarie non verificate integralmente. Il 2026 non viene consumato per dare un'apparenza di validazione a dati ancora incompleti. Nessuna modifica dei segnali Android.
