# PostgreSQL for the six service databases (INFRA-3), hardened for the Trivy image scan (CI-2).
FROM postgres:17.11-alpine3.24
# gosu is used by docker-entrypoint.sh only when the container starts as root, to drop to the
# postgres user. Our containers already start as postgres (user 70:70 in docker-compose.yml), so it
# is never executed; it is removed because its Go runtime carries known CVEs.
USER root
RUN apk upgrade --no-cache && rm -f /usr/local/bin/gosu
USER 70:70
