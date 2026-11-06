# Nginx gateway (INFRA-5). Runs as the non-root user of the unprivileged image (uid 101).
FROM nginxinc/nginx-unprivileged:1.30.6-alpine3.24
# The image runs envsubst on /etc/nginx/templates/*.template at start (only for defined variables)
COPY templates/ /etc/nginx/templates/
COPY snippets/ /etc/nginx/snippets/
ENV AUTH_RATE_LIMIT=10r/m \
    AUTH_RATE_BURST=5
EXPOSE 8443
