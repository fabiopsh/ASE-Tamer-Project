# Test di Monster Tamers

Test black-box contro il gateway `https://localhost:8443`, secondo il contratto in `docs/architettura/api-contract.md`.
Il certificato è self-signed: i test disattivano la verifica TLS.

## Esecuzione

Dalla radice del repo, con il backend avviato (`docker compose up`):

```bash
# Test funzionali (pytest)
pip install -r tests/api/requirements.txt
pytest tests/api -v

# Altro host/porta
BASE_URL=https://localhost:8443 pytest tests/api -v

# Test di carico (Locust, interfaccia web su http://localhost:8089)
pip install -r tests/performance/requirements.txt
locust -f tests/performance/locustfile.py --host https://localhost:8443

# Locust headless: 50 utenti, 5 al secondo, per 1 minuto
locust -f tests/performance/locustfile.py --host https://localhost:8443 --headless -u 50 -r 5 -t 1m
```

## Cosa copre ogni file

| File | User story | Contenuto |
|---|---|---|
| `tests/api/test_auth.py` | US-01, US-02, US-05 | Registrazione, duplicati, validazioni, login, errore generico, blocco dopo 5 tentativi, JWT manomesso, `/auth/me` |
| `tests/api/test_players.py` | US-03, US-04, US-06 | Profilo, starter pool (10 common stabili), scelta dei 3 starter, 409 alla seconda scelta, PATCH di nome e avatar |
| `tests/api/test_monsters.py` | US-09, US-10, US-11, US-12 | Catalogo paginato (100), filtri, ricerca, dettaglio, abilità (28), elementi, immagini pubbliche, collezione per giocatore, ownership, nessun endpoint per aggiungere mostri |
| `tests/api/test_teams.py` | US-14, US-15, US-16 | Creazione, lista, modifica, eliminazione, validazione, ownership, limite di 5 team |
| `tests/api/test_encounters_history.py` | US-17 a US-20, US-23 a US-25, US-12 | Bacheca stage 1, battaglia e log, XP, cattura, offerte completate, storico, catena di hash, servizi interni non esposti |
| `tests/performance/locustfile.py` | US-09, US-11, US-17, US-23 | Carico su catalogo, collezione, bacheca, battaglie (peso basso) e storico |

## Note

- Ogni test crea i propri utenti. Fa eccezione la fixture `reference` (dati di sola lettura del catalogo), letta una volta sola per sessione.
- I test sulle battaglie sono casuali: quelli che cercano una cattura o una vittoria ripetono l'encounter e saltano se l'esito non arriva.
- Il gateway limita `/api/auth/`: se con molti test compaiono 429 durante la registrazione, aspettare un minuto e rilanciare.
