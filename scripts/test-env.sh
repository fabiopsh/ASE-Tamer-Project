#!/bin/sh
# Prepares the API tests (TEST-1): copies the local CA and the "tests" client secret out of
# the secrets volume into .test-secrets/ and docs/tests/bruno/.env (both gitignored).
# Run after: docker compose -f docker-compose.yml -f docker-compose.test.yml up -d --build --wait
set -eu
ROOT=$(cd "$(dirname "$0")/.." && pwd)
PROJECT="${COMPOSE_PROJECT_NAME:-tamer}"
read_secret() {
    docker run --rm --network none -v "${PROJECT}_secrets:/s:ro" --entrypoint cat tamer/init-secrets:dev "/s/$1"
}
mkdir -p "$ROOT/.test-secrets"
read_secret tests/ca.crt > "$ROOT/.test-secrets/ca.crt"
SECRET=$(read_secret tests/client.env | sed -n 's/^TAMER_TEST_CLIENT_SECRET=//p')
{
    echo "TESTS_CLIENT_SECRET=$SECRET"
    echo "CA_PATH=$ROOT/.test-secrets/ca.crt"
} > "$ROOT/docs/tests/bruno/.env"
echo "Wrote .test-secrets/ca.crt and docs/tests/bruno/.env"
