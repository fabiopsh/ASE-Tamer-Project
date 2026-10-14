# auth

Registrazione, login e token JWT. È anche l'implementazione di riferimento per gli altri servizi (vedi `docs/architettura/backend-guide.md`).

- **Entità:** `Accounts` (database `auth`)
- **User stories:** US-01, US-02, US-05

## Endpoint

| Metodo | Path (dal gateway: `/api/auth/...`) | Auth | Descrizione |
|---|---|---|---|
| POST | `/auth/register` | no | crea account e profilo giocatore (chiama `players`) |
| POST | `/auth/login` | no | restituisce un JWT RS256 valido 1 h |
| GET | `/auth/me` | JWT | dati dell'account |

## Sicurezza

- Password salvate solo come hash **bcrypt** (costo 12, salt incluso).
- Login errato: stesso messaggio per utente inesistente e password sbagliata, con confronto bcrypt anche nel primo caso (niente differenze di tempo).
- Dopo 5 tentativi falliti l'account è bloccato 5 minuti (429); il gateway limita anche la frequenza delle richieste su `/api/auth/`.
- JWT firmato con chiave privata RSA presente solo nel volume di questo servizio; gli altri servizi hanno solo la chiave pubblica.
- Se la creazione del profilo in `players` fallisce, la transazione viene annullata: nessun account senza profilo.

## Test

`LoginGuardTest`: blocco dopo N tentativi, scadenza del blocco, reset dopo login riuscito, hash con salt.
