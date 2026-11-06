#!/bin/sh
# Copies the local CA certificate out of the "secrets" volume, so that curl, Bruno and the
# browser can verify https://localhost:8443 (INFRA-8). Run after the first `docker compose up`.
#   scripts/export-ca.sh [output file]     default: ./tamer-ca.crt
set -eu
OUT="${1:-tamer-ca.crt}"
PROJECT="${COMPOSE_PROJECT_NAME:-tamer}"
docker run --rm --network none -v "${PROJECT}_secrets:/s:ro" --entrypoint cat \
    tamer/init-secrets:dev /s/gateway/ca.crt > "$OUT"
echo "CA certificate written to $OUT"
echo "Example: curl --cacert $OUT https://localhost:8443/health"
