#!/bin/sh
# Generates, once, the JWT key pair and the self-signed TLS certificate into Docker volumes.
# Existing files are kept, so tokens and certificates survive restarts.
set -eu
apk add --no-cache openssl >/dev/null

if [ ! -f /jwt-private/jwt-private.pem ]; then
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out /jwt-private/jwt-private.pem 2>/dev/null
  openssl rsa -in /jwt-private/jwt-private.pem -pubout -out /jwt-public/jwt-public.pem 2>/dev/null
  echo "generated JWT key pair"
fi

if [ ! -f /tls/tls.crt ]; then
  openssl req -x509 -newkey rsa:2048 -nodes -days 825 -subj "/CN=localhost" \
    -addext "subjectAltName=DNS:localhost,IP:127.0.0.1" \
    -keyout /tls/tls.key -out /tls/tls.crt 2>/dev/null
  echo "generated TLS certificate"
fi

# Services run as non-root users; each volume is mounted only where it is needed.
chmod 644 /jwt-private/jwt-private.pem /jwt-public/jwt-public.pem /tls/tls.crt /tls/tls.key
