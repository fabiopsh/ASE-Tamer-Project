# frontend

Interfaccia web di Monster Tamers. Usa solo la REST API del backend (`/api/...`): nessuna logica di business qui (statistiche, danni, validazioni e ricompense arrivano sempre dalle API; il team viene validato con `POST /api/teams/validate`).

- **User stories:** US-28 (interfaccia), più quelle coperte da ciascuna vista (vedi sotto).

## Scelta tecnologica

HTML + CSS + JavaScript vanilla con moduli ES: **nessun framework, nessuna build, nessun npm**.

- Pochi file piccoli, leggibili e facili da revisionare.
- Servito come file statici dal gateway nginx su `/`, sulla stessa origine delle API (`/api`, `/images`): niente CORS, niente passaggi di build nel Dockerfile del gateway.
- Animazioni solo con CSS transitions/keyframes; i testi dinamici passano da un tagged template (`html`) che esegue l'escape (niente XSS dai nomi giocatore).
- Font da Google Fonts (Press Start 2P, VT323) con fallback monospace.

## Struttura

```
index.html          pagina unica
css/style.css       tema retro pixel, elementi/rarità come variabili CSS, animazioni, responsive
js/api.js           fetch con Bearer token (localStorage), errori {error,message,details}, 401 -> logout
js/router.js        router hash, nav, guardia login e redirect a #/starters
js/ui.js            template html, toast, modal, badge, card mostro, barre statistiche, paginazione
js/battle.js        vista battaglia e player dell'animazione (replay del log eventi)
js/<vista>.js       una vista per rotta: login, starters, home, collection, catalog, teams, encounters, history, profile
```

## Viste e user stories

| Rotta | Vista | User stories |
|---|---|---|
| `#/login` | login e registrazione (auto-login) | US-01, US-02, US-05 |
| `#/starters` | scelta di 3 starter dal pool di 10 | US-06, US-07 |
| `#/home` | dashboard: stage, progressi, avatar | US-03, US-04, US-28 |
| `#/collection` | collezione con filtri, ordinamento e dettaglio | US-09, US-13 |
| `#/catalog` | catalogo paginato, ricerca, dettaglio | US-10, US-11 |
| `#/teams` | creazione/modifica/eliminazione team, validazione live | US-14, US-15, US-16 |
| `#/encounters` | bacheca, scelta team, avvio encounter | US-17, US-18 |
| `#/battle/{run_id}` | replay animato della battaglia e ricompense | US-19, US-20, US-12 |
| `#/history` | storico, replay, verifica integrità | US-23, US-24, US-25 |
| `#/profile` | nome e avatar | US-04 |

Le corrispondenze con le user stories sono indicative: vanno allineate al backlog in `docs/`.

## Prova in locale

Servire la cartella con un qualunque server statico (es. `python -m http.server` da `frontend/`) dietro un reverse proxy che inoltri `/api` e `/images` ai servizi, oppure usare il gateway di `docker-compose`.
