# Analisi di sicurezza

Minacce considerate per Monster Tamers e contromisure implementate. Riferimenti: US-05 (dati dell'account), US-25 (salvataggi).

| # | Minaccia | Contromisura | Dove |
|---|---|---|---|
| S1 | Intercettazione del traffico (credenziali, token) | Solo HTTPS (TLS 1.2/1.3) sul gateway; la porta HTTP fa solo redirect; HSTS | `gateway/nginx.conf` |
| S2 | Furto del database delle password | bcrypt costo 12 con salt; mai password in chiaro né nei log | `backend/auth` |
| S3 | Brute force sul login | Blocco dell'account per 5 min dopo 5 errori (429) + rate limit per IP su `/api/auth/` | `LoginGuard`, nginx `limit_req` |
| S4 | Enumerazione degli username | Stesso messaggio e stesso costo bcrypt per utente inesistente e password errata | `AuthResource.login` |
| S5 | Token falsificati o riusati | JWT RS256, scadenza 1 h, issuer verificato; la chiave privata è montata solo nel servizio auth | `docker-compose.yml` (volumi `jwt-private` / `jwt-public`) |
| S6 | Accesso ai dati di un altro giocatore (IDOR) | Il `player_id` viene sempre dal JWT; risorse altrui → 403 | tutti i servizi |
| S7 | Chiamate dirette agli endpoint interni | `/internal/*` mai inoltrati dal gateway (404) e protetti da `X-Internal-Token` confrontato a tempo costante | `InternalTokenFilter`, nginx |
| S8 | Il client si assegna mostri o ricompense | Nessun endpoint pubblico per aggiungere mostri o XP; battaglie e ricompense calcolate solo dal backend | `monsters`, `encounters` |
| S9 | Injection (SQL, JSON malformato) | Query parametrizzate (Panache/Hibernate), Bean Validation su ogni DTO, limiti di lunghezza, `client_max_body_size` | tutti i servizi, nginx |
| S10 | XSS / clickjacking nel frontend | CSP restrittiva (`script-src 'self'`), `X-Frame-Options: DENY`, `nosniff`; il frontend costruisce l'HTML con il template `html` che fa escape di ogni valore | nginx, `frontend/js/ui.js` |
| S11 | Manomissione dello storico | Catena HMAC-SHA256 per giocatore (`prev_hash` + `sequence` + payload); `GET /history/verify` individua il primo record alterato | `backend/history` |
| S12 | Accesso trasversale ai database | Un database e un utente Postgres per servizio, nessun privilegio su `PUBLIC`; Postgres non esposto sull'host | `infra/postgres/init.sh` |
| S13 | Container compromesso | Servizi Java eseguiti con utente non root; immagini runtime JRE alpine minimali | `backend/*/Dockerfile` |
| S14 | Perdita dei salvataggi | Dati su volume Docker persistente `pgdata`; backup con `pg_dumpall` (vedi README principale) | compose |
| S15 | Segreti nel repository | Segreti in `.env` (ignorato da git); chiavi JWT e certificato TLS generati al primo avvio in volumi | `.env.example`, `infra/secrets/init.sh` |

## Limiti noti

- Certificato TLS self-signed: va sostituito con uno firmato da una CA per un uso reale.
- Nessuna revoca dei JWT prima della scadenza (logout lato client).
- Il token interno è un segreto condiviso; in produzione servirebbe mTLS tra i servizi.
- La catena di hash rileva modifiche e rimozioni intermedie, non la cancellazione dell'ultimo record.
- Il lock per giocatore di `encounters` è in memoria: vale con una sola istanza del servizio.
