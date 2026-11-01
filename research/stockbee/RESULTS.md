# Primo confronto Stockbee — Italia, 2021–2025

Eseguito il 1 novembre 2026 su 124 ticker della cache Yahoo esistente. Studio esplorativo di singole operazioni da 1.000 euro, non un portafoglio e non una replica certificata. 2026 non utilizzato. Nessuna ottimizzazione delle soglie.

Costi della tabella: 2,95 euro per ordine più 15 punti base per lato. Ingresso dopo la chiusura del segnale, alla successiva apertura. Otto test di regressione eseguiti e superati.

| Famiglia | Operazioni chiuse | Win rate | Risultato medio su 1.000 € | Profit Factor |
|---|---:|---:|---:|---:|
| Momentum Burst operativo | 757 | 40.55% | -7.03 € | 0.691 |
| Anticipation, acquisto alla prossima apertura | 4915 | 25.84% | -9.18 € | 0.377 |
| EP classico: SOLO candidati prezzo-volume | 30 | 56.67% | 39.11 € | 2.572 |
| EP9, soglia originale + filtri sperimentali | 370 | 37.84% | 0.38 € | 1.016 |
| EP9, variante italiana RVOL | 335 | 43.28% | 1.12 € | 1.035 |

Profit Factor = somma dei profitti netti / valore assoluto delle perdite nette. Sopra 1 indica un saldo positivo nel campione, non significatività statistica o rendimento futuro.

## Interpretazione

- Momentum: PF senza costi 1,103; con commissioni e 15 bp 0,691. Il piccolo vantaggio lordo di questa definizione non copre i costi.
- Anticipation: PF già sotto 1 senza costi (0,960). È il test del nostro acquisto alla prossima apertura e stop stretto; non misura la selezione discrezionale né una conferma intraday.
- EP9: PF 1,016 originale e 1,035 adattato a 15 bp; a 30 bp diventano rispettivamente 0,897 e 0,944. Nessun margine robusto ai costi in questa prova.
- Candidati EP: 30 chiusi, uno aperto a fine campione, 24 ticker. Mancano i catalizzatori storici: il vero EP classico NON è stato testato. Eliminando a posteriori SFL, TPRO e SPM restano 25 operazioni, PF 1,189 e media +5,64 euro. Questa è una diagnosi di concentrazione, non una nuova strategia.

## Cosa resta incompleto

Universo storico/dei delisting incompleto; volumi e operazioni societarie non certificati; nessuna verifica completa dei catalizzatori; niente Market Monitor ricostruito; selezione grafica discrezionale assente. Le operazioni su titoli diversi possono sovrapporsi: niente CAGR o drawdown di portafoglio deducibile da questa tabella. Nessun aggiornamento dei segnali Android o nuova build APK in questa prova.

Per EP9 e candidati EP lo stop/target e durata sono ipotesi comuni della ricerca, non regole ufficiali Stockbee. Consultare PROTOCOL.md. Il JSON include risultati per anno, costo e titoli SOL, GVS, Stellantis, Reply, Technogym e Maire. Il file CSV registra ogni tentativo e gli esiti mancanti.
