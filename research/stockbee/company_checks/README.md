# Verifica su documenti reali: NVIDIA

Eseguita il 1 novembre 2026. Caso USA scelto per controllare l'acquisizione dei fondamentali, non per selezionare un trade vincente. Riguarda Q1 e Q2 **fiscali 2026**, pubblicati rispettivamente il 28 maggio e 27 agosto **2025**; non usa risultati del calendario 2026 riservato alla futura validazione.

## Risultati dell'acquisizione

Scaricati i due comunicati dal sito Investor Relations NVIDIA. Il parser distingue le tabelle GAAP e non-GAAP, verifica intestazioni e periodi, conserva URL e hash SHA256. Sei coppie di valori sono state confrontate con le tabelle dell'emittente: ricavi ed EPS GAAP per entrambi i trimestri, EPS non-GAAP per entrambi.

| Dato pubblicato | Q1 FY26 | Q2 FY26 |
|---|---:|---:|
| Ricavi, milioni USD | 44.062 | 46.743 |
| Ricavi comparabili anno precedente, milioni USD | 26.044 | 30.040 |
| Crescita ricavi YoY, arrotondata dall'emittente | 69% | 56% |
| EPS diluito GAAP, USD | 0,76 | 1,08 |
| EPS diluito non-GAAP, USD | 0,81 | 1,05 |
| Crescita EPS GAAP YoY, arrotondata | 27% | 61% |
| Crescita EPS non-GAAP YoY, arrotondata | 33% | 54% |

La crescita ricavi calcolata dai valori pubblicati è 69,183% e 55,603%; la loro media è 62,393%. È un calcolo nostro, non un dato MarketSurge certificato. L'EPS rettificato ulteriormente per componenti H20 non viene sostituito all'EPS non-GAAP principale.

Fonti: [Q1 originale](https://investor.nvidia.com/news/press-release-details/2025/NVIDIA-Announces-Financial-Results-for-First-Quarter-Fiscal-2026/default.aspx), [Q2 originale](https://investor.nvidia.com/news/press-release-details/2025/NVIDIA-Announces-Financial-Results-for-Second-Quarter-Fiscal-2026/default.aspx).

## Cosa dimostra e cosa manca

I dati di crescita sono stati estratti da documenti reali. Il controllo del parser ha inizialmente rilevato un errore su righe vuote HTML; corretto e rieseguito con successo. Non è un estrattore universale: struttura inattesa o periodi diversi causano errore.

Lo scanner restituisce **unknown** per i tre profili: mancano campi di mercato e definizioni vendor certificate. Le pagine consultate forniscono la data, non un timestamp esatto di pubblicazione. Il caso conserva timestamp nulli: non simula disponibilità intraday. Non sono stati inventati prezzo, volume medio, consenso o valori annuali.

Nessun segnale operativo, backtest, ordine o rendimento è stato prodotto. La verifica reale è sul percorso documento → valori → controllo di completezza. I nove test sintetici del valutatore sono stati rieseguiti e superati; non sono prove di redditività.

## Riprodurre

Richiede Python, lxml e il modulo fondamentale nella stessa cartella. I comunicati integrali non sono distribuiti nel repository; vengono recuperati dagli URL originali.

```
python research/stockbee/verify_nvidia_sources.py --refresh
```

Output: `nvda_source_audit.json` con fatti, metadati e controlli; `nvda_incomplete_input.json` con il caso incompleto, esplicitamente non utilizzabile come segnale. Il confronto numerico usa gli importi arrotondati pubblicati dall'emittente e non certifica le convenzioni contabili di MarketSurge.
