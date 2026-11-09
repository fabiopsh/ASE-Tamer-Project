#!/usr/bin/env bash
# Self-attack checklist of SEC-1 (docs/plan/11-ci-security.md) against the running stack.
# Every check is an attack that must FAIL against our system; the script prints PASS when it does.
#
#   docker compose up -d --build --wait          # normal compose: the brute-force check needs the real rate limit
#   scripts/export-ca.sh                          # ./tamer-ca.crt
#   scripts/security-check.sh [gateway-url] [ca-file]
#
# Needs bash, curl and jq. Exit code 1 if any check fails.
set -uo pipefail
export MSYS_NO_PATHCONV=1

GW="${1:-https://localhost:8443}"
CA="${2:-tamer-ca.crt}"
[ -f "$CA" ] || { echo "CA file not found: $CA (run scripts/export-ca.sh)"; exit 2; }
command -v jq >/dev/null || { echo "jq is required"; exit 2; }

PASS=0; FAIL=0; WARN=0
ok()   { PASS=$((PASS + 1)); printf '  \033[32mPASS\033[0m %s\n' "$1"; }
ko()   { FAIL=$((FAIL + 1)); printf '  \033[31mFAIL\033[0m %s\n' "$1"; }
warn() { WARN=$((WARN + 1)); printf '  \033[33mWARN\033[0m %s\n' "$1"; }
section() { printf '\n%s\n' "$1"; }

# req METHOD PATH [TOKEN] [JSON BODY] -> sets $STATUS and $BODY
req() {
    local method=$1 path=$2 token=${3:-} body=${4:-} out
    local args=(-s --cacert "$CA" -X "$method" -w '\n%{http_code}' "$GW$path")
    [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
    [ -n "$body" ] && args+=(-H 'Content-Type: application/json' --data-binary "$body")
    out=$(curl "${args[@]}")
    STATUS=${out##*$'\n'}
    BODY=${out%$'\n'*}
}
expect() {  # expect "description" allowed-status...
    local name=$1; shift
    for s in "$@"; do [ "$STATUS" = "$s" ] && { ok "$name → $STATUS"; return; }; done
    ko "$name → $STATUS (expected $*) ${BODY:0:160}"
}
no_leak() {  # the error body must not reveal internals
    if grep -qiE 'exception|org\.hibernate|psql|sql state|syntax error|stack|\.java:' <<<"$BODY"; then
        ko "$1 leaks internals: ${BODY:0:160}"
    else ok "$1 has no internal details"; fi
}

# A player with starters and a team; prints "token|team_id|owned_id|run_id"
new_player() {
    local user="sec_$(LC_ALL=C tr -dc 'a-f0-9' </dev/urandom | head -c 10)" token pool starters
    req POST /api/auth/register "" "{\"username\":\"$user\",\"password\":\"secret-pass-1\"}"
    token=$(curl -s --cacert "$CA" "$GW/api/auth/token" -d grant_type=password -d "username=$user" \
        -d password=secret-pass-1 | jq -r .access_token)
    req GET /api/players/me/starters "$token"
    pool=$(jq -c '.pool[0:3]' <<<"$BODY")
    req POST /api/players/me/starters "$token" "{\"monster_ids\":$pool}"
    starters=$BODY
    req POST /api/teams "$token" "{\"name\":\"Sec\",\"members\":$(jq -c '[.starters[].owned_id]' <<<"$starters")}"
    printf '%s|%s|%s|%s\n' "$token" "$(jq -r .team_id <<<"$BODY")" \
        "$(jq -r '.starters[0].owned_id' <<<"$starters")" "$(jq -r .run_id <<<"$starters")"
}

echo "Self-attack against $GW"
IFS='|' read -r TOKEN_A TEAM_A OWNED_A RUN_A <<<"$(new_player)"
IFS='|' read -r TOKEN_B TEAM_B OWNED_B RUN_B <<<"$(new_player)"
[ -n "$TOKEN_A" ] && [ "$TOKEN_A" != null ] && [ "$TEAM_A" != null ] \
    || { echo "Setup failed: cannot register players (is the stack up? rate limit hit? retry in a minute)"; exit 2; }

section "1. SQL injection in string and query parameters → 400/404, no error leakage"
for path in "/api/monsters?rarity=common'%20OR%20'1'='1" "/api/monsters?element=fire%27%3B%20DROP%20TABLE%20monsters%3B--" \
            "/api/history?outcome=victory'%20OR%201=1--" "/api/teams/1'%20OR%20'1'='1" "/api/collection/x'%20UNION%20SELECT%201--"; do
    req GET "$path" "$TOKEN_A"; expect "GET ${path:0:60}" 400 404; no_leak "GET ${path:0:40}"
done
req POST /api/teams "$TOKEN_A" "{\"name\":\"x'); DROP TABLE teams;--\",\"members\":[\"$OWNED_A\"]}"
expect "team name with SQL is stored as plain data" 201 400
STATUS=$(curl -s -o /dev/null -w '%{http_code}' --cacert "$CA" "$GW/api/auth/token" -d grant_type=password \
    --data-urlencode "username=admin' OR '1'='1" -d password=x)
BODY=""; expect "login with SQL in username" 400 401

section "2. JWT attacks → 401"
b64() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }
PAYLOAD=$(cut -d. -f2 <<<"$TOKEN_A")
NONE="$(printf '{"alg":"none","typ":"JWT"}' | b64).$PAYLOAD."
req GET /api/players/me "$NONE"; expect "alg:none token" 401
SIG=$(cut -d. -f3 <<<"$TOKEN_A"); FLIP=$( [ "${SIG:0:1}" = A ] && echo B || echo A )
req GET /api/players/me "$(cut -d. -f1-2 <<<"$TOKEN_A").$FLIP${SIG:1}"; expect "wrong signature" 401
FORGED=$(jq -c '.sub="00000000-0000-4000-8000-000000000000"' <<<"$(tr '_-' '/+' <<<"$PAYLOAD"==== | openssl base64 -d -A 2>/dev/null)" | b64)
req GET /api/players/me "$(cut -d. -f1 <<<"$TOKEN_A").$FORGED.$SIG"; expect "payload changed (other sub), old signature" 401
req GET /api/players/me "not.a.jwt"; expect "garbage token" 401
req GET /api/players/me; expect "no token" 401
echo "  info expired / wrong issuer / wrong audience: TokenVerificationTest (JUnit, needs the signing key)"

section "3. IDOR: player B reads player A's resources → 404"
req GET "/api/teams/$TEAM_A" "$TOKEN_B"; expect "foreign team_id" 404
req PUT "/api/teams/$TEAM_A" "$TOKEN_B" "{\"name\":\"Mine\",\"members\":[\"$OWNED_B\"]}"; expect "update foreign team" 404
req DELETE "/api/teams/$TEAM_A" "$TOKEN_B"; expect "delete foreign team" 404
req GET "/api/collection/$OWNED_A" "$TOKEN_B"; expect "foreign owned_id" 404
req GET "/api/runs/$RUN_A" "$TOKEN_B"; expect "foreign run_id" 404
req POST /api/teams "$TOKEN_B" "{\"name\":\"Steal\",\"members\":[\"$OWNED_A\"]}"; expect "team with a foreign monster" 400
req POST /api/battles "$TOKEN_B" "{\"template_id\":11,\"team_id\":\"$TEAM_A\"}"; expect "battle with a foreign team" 404
req POST /api/battles "$TOKEN_A" "{\"template_id\":11,\"team_id\":\"$TEAM_A\"}"; ENC_A=$(jq -r .encounter_id <<<"$BODY")
for _ in $(seq 1 100); do
    req POST /api/battles/current/actions "$TOKEN_A" '{"type":"attack"}'
    [ "$(jq -r .status <<<"$BODY")" = finished ] && break
done
req GET "/api/history/$ENC_A" "$TOKEN_B"; expect "foreign encounter_id" 404

section "4. Internal and management endpoints through the gateway → 404"
for path in /internal/players /internal/monsters /internal/auth/token /q/health /q/openapi /q/metrics /q/dev-ui; do
    req GET "$path" "$TOKEN_A"; expect "GET $path" 404
done
req POST /internal/history "$TOKEN_A" '{}'; expect "POST /internal/history" 404

section "5. Forged battle results → 400 (unknown fields rejected)"
req POST /api/battles "$TOKEN_A" "{\"template_id\":11,\"team_id\":\"$TEAM_A\"}"
req POST /api/battles/current/actions "$TOKEN_A" '{"type":"attack","outcome":"victory"}'; expect "action with outcome" 400
req POST /api/battles/current/actions "$TOKEN_A" '{"type":"attack","hp":9999}'; expect "action with hp" 400
req POST /api/battles/current/actions "$TOKEN_A" '{"type":"win"}'; expect "unknown action type" 400

section "6. Concurrency: two actions at once → consistent turn counter"
req GET /api/battles/current "$TOKEN_A"; TURN0=$(jq -r .turn <<<"$BODY")
if [ "$(jq -r .status <<<"$BODY")" = in_progress ]; then
    for i in 1 2; do
        curl -s -o /dev/null -w '%{http_code}\n' --cacert "$CA" -X POST -H "Authorization: Bearer $TOKEN_A" \
            -H 'Content-Type: application/json' -d '{"type":"attack"}' "$GW/api/battles/current/actions" &
    done > /tmp/tamer-sec-$$; wait
    CODES=$(sort /tmp/tamer-sec-$$ | tr '\n' ' '); rm -f /tmp/tamer-sec-$$
    req GET /api/battles/current "$TOKEN_A"
    if [ "$STATUS" = 404 ]; then ok "parallel actions ($CODES): battle finished, history decides"; else
        TURN1=$(jq -r .turn <<<"$BODY"); OKS=$(grep -o 200 <<<"$CODES" | wc -l | tr -d ' ')
        [ $((TURN1 - TURN0)) -eq "$OKS" ] && ok "parallel actions ($CODES): turn $TURN0 → $TURN1, one round per 200" \
            || ko "parallel actions ($CODES): turn $TURN0 → $TURN1"
    fi
else warn "battle already finished, concurrency check skipped"; fi
USER_C="sec_$(LC_ALL=C tr -dc 'a-f0-9' </dev/urandom | head -c 10)"
req POST /api/auth/register "" "{\"username\":\"$USER_C\",\"password\":\"secret-pass-1\"}"
TOKEN_C=$(curl -s --cacert "$CA" "$GW/api/auth/token" -d grant_type=password -d "username=$USER_C" -d password=secret-pass-1 | jq -r .access_token)
req GET /api/players/me/starters "$TOKEN_C"; POOL_C=$(jq -c '.pool[0:3]' <<<"$BODY")
for i in 1 2; do
    curl -s -o /dev/null -w '%{http_code}\n' --cacert "$CA" -X POST -H "Authorization: Bearer $TOKEN_C" \
        -H 'Content-Type: application/json' -d "{\"monster_ids\":$POOL_C}" "$GW/api/players/me/starters" &
done > /tmp/tamer-sec-$$; wait
CODES=$(sort /tmp/tamer-sec-$$ | tr '\n' ' '); rm -f /tmp/tamer-sec-$$
req GET /api/collection "$TOKEN_C"; COUNT=$(jq '.items | length' <<<"$BODY")
[ "$COUNT" = 3 ] && ok "two starter choices at once ($CODES): exactly 3 monsters" || ko "two starter choices ($CODES): $COUNT monsters"

section "7. Oversized body → 413"
BIG=$(head -c 20000 </dev/zero | tr '\0' 'a')
req POST /api/teams "$TOKEN_A" "{\"name\":\"$BIG\",\"members\":[]}"; expect "20 KB body" 413

section "8. XSS payloads are stored and returned as plain text"
req POST /api/teams "$TOKEN_B" "{\"name\":\"<script>alert(1)</script>\",\"members\":[\"$OWNED_B\"]}"
if [ "$STATUS" = 201 ] || [ "$STATUS" = 400 ]; then ok "team name with <script> → $STATUS (JSON, never HTML)"; else ko "team name with <script> → $STATUS"; fi
CT=$(curl -s -o /dev/null -w '%{content_type}' --cacert "$CA" -H "Authorization: Bearer $TOKEN_B" "$GW/api/teams")
[[ "$CT" == application/json* ]] && ok "API content type is $CT" || ko "API content type is $CT"
NOSNIFF=$(curl -s -D - -o /dev/null --cacert "$CA" "$GW/health" | grep -ci '^x-content-type-options: nosniff')
[ "$NOSNIFF" -ge 1 ] && ok "X-Content-Type-Options: nosniff" || ko "missing X-Content-Type-Options"
ROOT=$(cd "$(dirname "$0")/.." && pwd)
if [ -d "$ROOT/frontend/src" ]; then
    grep -rqE 'dangerouslySetInnerHTML|\.innerHTML|eval\(' "$ROOT/frontend/src" && ko "frontend renders raw HTML" \
        || ok "frontend has no dangerouslySetInnerHTML / innerHTML / eval (React escapes text)"
fi

section "9. TLS"
CODE=$(curl -s -o /dev/null -w '%{http_code}' "${GW/https:/http:}/health" || true)
[ "$CODE" != 200 ] && ok "plain HTTP to the gateway refused (status ${CODE:-none})" || ko "plain HTTP answered 200"
curl -s -o /dev/null --cacert "$CA" --tls-max 1.1 "$GW/health" 2>/dev/null && ko "TLS 1.1 accepted" || ok "TLS 1.0/1.1 refused"
curl -s -o /dev/null "$GW/health" 2>/dev/null && ko "certificate accepted without the local CA" \
    || ok "certificate not trusted without the local CA (self-signed CA, as expected)"
if [ -d "$ROOT/backend" ]; then
    grep -rqiE 'trust-all|trustAll|verify-host *= *false|hostname-verification-algorithm *= *NONE' \
        "$ROOT"/backend/*/src/main/resources "$ROOT/gateway" 2>/dev/null && ko "trust-all found in configuration" \
        || ok "no trust-all / disabled hostname verification in service and gateway configuration"
fi

section "10. Brute force on the token endpoint → 429"
CODES=""
for _ in $(seq 1 25); do
    CODES+=" $(curl -s -o /dev/null -w '%{http_code}' --cacert "$CA" "$GW/api/auth/token" \
        -d grant_type=password -d username=nobody_here -d password=wrong-pass-1)"
done
if grep -q 429 <<<"$CODES"; then ok "429 after $(tr ' ' '\n' <<<"$CODES" | grep -c 400) failed attempts"
else warn "no 429 in 25 attempts: is docker-compose.test.yml (raised limit) active?"; fi

section "Replay of internal reward/xp calls"
echo "  info covered by Bruno services/monsters/internal/08-post-reward-idempotent, 10-post-xp-idempotent (tests client)"

printf '\nResult: %d passed, %d failed, %d warnings\n' "$PASS" "$FAIL" "$WARN"
[ "$FAIL" -eq 0 ]
