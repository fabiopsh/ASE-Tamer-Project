#!/bin/sh
# Regenerates docs/openapi/ (DOC-1): one spec per service plus the merged gateway spec.
#   scripts/export-openapi.sh
# Needs Java 21+, Maven and Node.js. Each service writes target/openapi/openapi.yaml at build time
# (quarkus.smallrye-openapi.store-schema-directory).
set -eu
ROOT=$(cd "$(dirname "$0")/.." && pwd)
# cd instead of -f: the Quarkus plugin rejects a path with quotes (e.g. an apostrophe) in the command line
(cd "$ROOT/backend" && mvn -B -q package -DskipTests)
mkdir -p "$ROOT/docs/openapi"
for svc in auth players monsters teams encounters history; do
    cp "$ROOT/backend/$svc/target/openapi/openapi.yaml" "$ROOT/docs/openapi/$svc.yaml"
    echo "docs/openapi/$svc.yaml"
done
cd "$ROOT/scripts/openapi"
npm ci --silent
node build-gateway-openapi.mjs
