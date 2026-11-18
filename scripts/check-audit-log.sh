#!/usr/bin/env bash
# Audit log check of SEC-3/SEC-4 (docs/plan/13-threat-model.md) against the running stack.
# Makes a few requests through the gateway, then reads the gateway and auth container logs and
# checks that every request left one JSON line with the expected outcome, that auth recorded
# who logged in, and that no password, token or plain username was written anywhere.
#
#   docker compose up -d --build --wait            # or with -f docker-compose.test.yml
#   scripts/export-ca.sh                            # ./tamer-ca.crt
#   scripts/check-audit-log.sh [gateway-url] [ca-file]
#
# COMPOSE overrides the compose command (default: "docker compose").
# Needs bash, curl, jq and docker. Exit code 1 if any check fails.
set -uo pipefail
export MSYS_NO_PATHCONV=1

GW="${1:-https://localhost:8443}"
CA="${2:-tamer-ca.crt}"
COMPOSE="${COMPOSE:-docker compose}"
[ -f "$CA" ] || { echo "CA file not found: $CA (run scripts/export-ca.sh)"; exit 2; }
command -v jq >/dev/null || { echo "jq is required"; exit 2; }

PASS=0; FAIL=0
ok() { PASS=$((PASS + 1)); printf '  \033[32mPASS\033[0m %s\n' "$1"; }
ko() { FAIL=$((FAIL + 1)); printf '  \033[31mFAIL\033[0m %s\n' "$1"; }

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

# call NAME METHOD PATH [curl args...] -> $TMP/NAME.status, .body, .rid (X-Request-Id of the gateway)
call() {
    local name=$1 method=$2 path=$3; shift 3
    curl -s --cacert "$CA" -X "$method" -D "$TMP/$name.headers" -o "$TMP/$name.body" \
        -w '%{http_code}' "$@" "$GW$path" > "$TMP/$name.status"
    tr -d '\r' < "$TMP/$name.headers" | sed -n 's/^[Xx]-[Rr]equest-[Ii]d: *//p' | tail -1 > "$TMP/$name.rid"
}
status() { cat "$TMP/$1.status"; }
rid() { cat "$TMP/$1.rid"; }

SUFFIX=$(LC_ALL=C tr -dc 'a-f0-9' </dev/urandom | head -c 10)
USER_NAME="audit_$SUFFIX"
PASSWORD="audit-pass-$SUFFIX"
WRONG="wrong-pass-$SUFFIX"
SINCE=$(date -u +%Y-%m-%dT%H:%M:%SZ)

echo "Audit log check against $GW"
call health   GET  /health
call noauth   GET  /api/players/me
call register POST /api/auth/register -H 'Content-Type: application/json' \
    --data-binary "{\"username\":\"$USER_NAME\",\"password\":\"$PASSWORD\"}"
call badlogin POST /api/auth/token --data-urlencode grant_type=password \
    --data-urlencode "username=$USER_NAME" --data-urlencode "password=$WRONG"
call login    POST /api/auth/token --data-urlencode grant_type=password \
    --data-urlencode "username=$USER_NAME" --data-urlencode "password=$PASSWORD"
TOKEN=$(jq -r '.access_token // empty' "$TMP/login.body")
PLAYER_ID=$(jq -r '.player_id // empty' "$TMP/register.body")
call me       GET  /api/players/me -H "Authorization: Bearer $TOKEN"

printf '\nRequests: health %s, no token %s, register %s, wrong login %s, login %s, profile %s\n' \
    "$(status health)" "$(status noauth)" "$(status register)" "$(status badlogin)" "$(status login)" "$(status me)"
if [ "$(status register)" != 201 ] || [ "$(status login)" != 200 ] || [ -z "$TOKEN" ]; then
    echo "The stack did not answer as expected (rate limit? run with docker-compose.test.yml or wait a minute)"
    exit 2
fi

sleep 2   # the gateway writes the line after the response
$COMPOSE logs --no-color --no-log-prefix --since "$SINCE" gateway auth > "$TMP/all.log" 2>&1
grep -E '^\{"ts":' "$TMP/all.log" > "$TMP/audit.log" || true

printf '\nFormat\n'
if [ -s "$TMP/audit.log" ]; then ok "$(wc -l < "$TMP/audit.log" | tr -d ' ') audit lines found"
else ko "no audit line in the gateway and auth logs"; fi
BAD=0
while IFS= read -r line; do
    jq -e 'type == "object" and has("ts") and has("event") and has("user") and has("outcome")' \
        >/dev/null 2>&1 <<<"$line" || { BAD=$((BAD + 1)); echo "    not a valid audit line: ${line:0:160}"; }
done < "$TMP/audit.log"
[ "$BAD" = 0 ] && ok "every audit line is one JSON object with ts, event, user, outcome" \
    || ko "$BAD audit lines are not valid"

# line EVENT RID -> the audit lines of that event for that request id
line() { jq -c --arg e "$1" --arg r "$2" 'select(.event == $e and .request_id == $r)' "$TMP/audit.log"; }
# expect_line DESCRIPTION EVENT RID JQ-CONDITION
expect_line() {
    local found count
    found=$(line "$2" "$3")
    count=$(grep -c . <<<"$found")
    if [ "$count" != 1 ]; then ko "$1: expected one line, found $count"; return; fi
    if jq -e "$4" >/dev/null <<<"$found"; then ok "$1"; else ko "$1: $found"; fi
}

printf '\nGateway (SEC-4): one line per request\n'
expect_line "request without token → denied, 401" request "$(rid noauth)" \
    '.outcome == "denied" and .status == "401" and .method == "GET" and .path == "/api/players/me" and .user == "-"'
expect_line "wrong login → failure, 400" request "$(rid badlogin)" \
    '.outcome == "failure" and .status == "400" and .path == "/api/auth/token"'
expect_line "profile with a valid token → success, 200" request "$(rid me)" \
    '.outcome == "success" and .status == "200"'
if [ -z "$(line request "$(rid health)")" ] && ! jq -e 'select(.path == "/health")' "$TMP/audit.log" >/dev/null 2>&1; then
    ok "health checks are not recorded"
else ko "a /health request was recorded"; fi

printf '\nAuth (SEC-3): who, with the same request id as the gateway line\n'
expect_line "registration → success with the player_id" register "$(rid register)" \
    ".outcome == \"success\" and .user == \"$PLAYER_ID\""
expect_line "wrong password → denied with the username blind index" login "$(rid badlogin)" \
    '.outcome == "denied" and (.user | test("^[0-9a-f]{64}$")) and .reason == "invalid_grant"'
expect_line "correct password → success with the player_id" login "$(rid login)" \
    ".outcome == \"success\" and .user == \"$PLAYER_ID\""

printf '\nNever recorded (whole gateway and auth logs)\n'
for secret in "$PASSWORD:the password" "$WRONG:the wrong password" "$TOKEN:the access token" \
              "$USER_NAME:the plain username (P-27)" "Bearer:an Authorization header"; do
    value=${secret%%:*}; what=${secret#*:}
    if grep -qF -- "$value" "$TMP/all.log"; then ko "$what appears in the logs"; else ok "$what is not in the logs"; fi
done

printf '\n%d passed, %d failed\n' "$PASS" "$FAIL"
[ "$FAIL" = 0 ]
