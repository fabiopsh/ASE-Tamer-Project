# Guida per chi implementa un servizio backend

1. Leggi `docs/architettura/api-contract.md`: è il contratto. Endpoint, campi JSON (snake_case), codici HTTP vanno rispettati alla lettera.
2. Il servizio `backend/auth/` è l'implementazione di riferimento. Copia da lì e adatta:
   - `pom.xml` (cambia `artifactId`; aggiungi/togli dipendenze; `quarkus-smallrye-jwt-build` e `jbcrypt` servono solo ad auth),
   - `Dockerfile`, `.dockerignore`,
   - il package `common/` (`ApiException`, `ErrorResponse`, `ErrorMappers`, `InternalTokenFilter`) cambiando il nome del package,
   - lo stile di `application.properties` (path OpenAPI/swagger con il nome del servizio),
   - lo stile del codice: record per i DTO raggruppati in una classe `*Dtos`, entità Panache con campi pubblici e `@Table`/`@Column` snake_case, REST client con `@ClientHeaderParam` per il token interno.
3. Package base: `it.unipi.ase.tamers.<svc>`.
4. Endpoint pubblici `@RolesAllowed("player")` e `player_id = UUID.fromString(jwt.getSubject())`. Endpoint interni sotto `/internal/...` con `@PermitAll` (il filtro controlla il token).
5. Logica di dominio in classi pure (senza CDI/DB) coperte da unit test JUnit 5. Niente `@QuarkusTest`.
6. Chiamate verso altri servizi: un'interfaccia MicroProfile REST Client per servizio; in caso di errore del servizio a valle lancia `ApiException.upstream(...)` (503). Per i 404 a valle, gestisci `WebApplicationException` leggendo `getResponse().getStatus()`.
7. Build e test (dalla root del repo, Git Bash):
   ```bash
   cd backend/<svc> && MSYS_NO_PATHCONV=1 docker run --rm -v "$(pwd -W):/src" -v ase-m2:/root/.m2 -w /src maven:3.9-eclipse-temurin-21 mvn -B -q package
   ```
   Deve terminare senza errori e con i test verdi (`target/surefire-reports`). Il volume `ase-m2` contiene già le dipendenze Quarkus.
8. Aggiorna il `README.md` del servizio (italiano, breve): responsabilità, entità, endpoint, regole, test.
