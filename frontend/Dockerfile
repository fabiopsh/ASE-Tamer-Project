# Frontend (US-28): Vite build, then static files served by Nginx on port 8080.
# Independent of the backend: the browser calls the gateway (another origin) with CORS.
#   docker compose -f docker-compose.frontend.yml up -d --build

FROM node:22.20.0-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
# Public URL of the gateway, as the browser sees it. Also sets the CSP connect-src/img-src.
ARG VITE_GATEWAY_URL=https://localhost:8443
ENV VITE_GATEWAY_URL=$VITE_GATEWAY_URL
RUN npm run build

# Runs as the non-root user of the unprivileged image (uid 101), like the gateway.
FROM nginxinc/nginx-unprivileged:1.30.6-alpine3.24
# Security fixes of Alpine packages newer than the base image build (CI-2, Trivy image scan)
USER root
RUN apk upgrade --no-cache
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
USER 101
EXPOSE 8080
