# Nginx gateway (INFRA-5). Runs as the non-root user of the unprivileged image (uid 101).
FROM nginxinc/nginx-unprivileged:1.30.6-alpine3.24
# Security fixes of Alpine packages newer than the base image build (CI-2, Trivy image scan)
USER root
RUN apk upgrade --no-cache
# The image runs envsubst on /etc/nginx/templates/*.template at start (only for defined variables)
COPY templates/ /etc/nginx/templates/
COPY snippets/ /etc/nginx/snippets/
# Rate limit of /api/auth/{token,register,logout} and general /api/* (docker-compose.test.yml raises them for Locust)
ENV LOGIN_RATE_LIMIT=10r/m \
    LOGIN_RATE_BURST=5 \
    API_RATE_LIMIT=120r/m \
    API_RATE_BURST=60
# Browser origins allowed by CORS (anchored regex; override in compose for another frontend host)
ENV CORS_ALLOWED_ORIGINS="^http://localhost:(8080|5173|4173)\$"
# Same user as the base image, stated explicitly (CI-2, Trivy DS-0002)
USER 101
EXPOSE 8443
