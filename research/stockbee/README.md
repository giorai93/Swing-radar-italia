# Stockbee — stato della ricerca

**Documento di riferimento:** [Manuale documentale dei quattro metodi](MANUALE_STOCKBEE.md), versione 0.2 del 1 novembre 2026.

Il manuale distingue criteri dell’autore, elementi discrezionali e informazioni mancanti. Include la specifica proposta per fondamentali e catalizzatori, fonti datate e requisiti di verifica prima della replica.

[Registro delle lacune](rule_registry.json): tutti i metodi hanno `faithful_backtest_enabled: false`. Il registro è documentale: non modifica né blocca tecnicamente l’esecuzione del vecchio script.

## Esperimento precedente: varianti nostre

[Protocollo](PROTOCOL.md), [risultati](RESULTS.md), [codice](study.py) e [dati di audit](results/) rimangono disponibili per tracciabilità. **Non sono backtest delle strategie ufficiali di Stockbee.** In particolare, l’EP classico è una selezione tecnica priva di verifica dei catalizzatori e l’Anticipation compra all’apertura successiva senza il trigger documentato nell’esempio dell’autore.

Nella revisione documentale non sono stati eseguiti nuovi backtest o build APK. Nessun nuovo rendimento validato è disponibile.

## Componente fondamentale

[Valutatore](fundamental_screen.py) dei tre scanner Bucket 1/2, trascritti dalle immagini originali del settembre 2025. Richiede campi con definizione verificata e provenienza temporale: restituisce pass/fail/unknown, mai ordini. Nove test sintetici passati; nessuna validazione su aziende reali o nuova misura di redditività.
