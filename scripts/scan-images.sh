#!/bin/sh
# Scans every image of docker-compose.yml with Trivy (CI-2, same check as the "images" CI job).
# Prints OK or VULN per image and exits 1 if any image has CRITICAL/HIGH vulnerabilities.
#   docker compose build          # first, so that the images exist
#   scripts/scan-images.sh
set -u
# Git Bash on Windows rewrites "/path" arguments; keep them as they are for docker
export MSYS_NO_PATHCONV=1
cd "$(dirname "$0")/.."
TRIVY="aquasec/trivy:0.76.0"
status=0
for image in $(docker compose config --images | sort -u); do
    if docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v tamer-trivy-cache:/root/.cache \
        "$TRIVY" image --skip-version-check --scanners vuln --severity CRITICAL,HIGH --ignore-unfixed \
        --exit-code 1 "$image"; then
        echo "OK   $image"
    else
        echo "VULN $image (or Trivy error: read the output above)"
        status=1
    fi
done
exit $status
