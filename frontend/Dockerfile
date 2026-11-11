# Frontend (US-28): Vite build, then static files served by Nginx over HTTPS.
# The gateway proxies every non-API path here, so the app and the API share one origin
# (https://localhost:8443) and the browser accepts a single certificate.

FROM node:22.20.0-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
# Same origin as the gateway: API calls use relative URLs (/api/..., /images/...)
ENV VITE_GATEWAY_URL=/
RUN npm run build

# Runs as the non-root user of the unprivileged image (uid 101), like the gateway.
FROM nginxinc/nginx-unprivileged:1.30.6-alpine3.24
# Security fixes of Alpine packages newer than the base image build (CI-2, Trivy image scan)
USER root
RUN apk upgrade --no-cache
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
USER 101
EXPOSE 8443
