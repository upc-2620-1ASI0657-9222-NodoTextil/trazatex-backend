# FE-M10: etiquetas fijas (antes node:24-alpine y nginx:alpine, que cambian solas). Subir de versión es un cambio consciente.
FROM node:24.11.0-alpine3.22 AS build
WORKDIR /app
ENV NODE_OPTIONS="--max-old-space-size=1024"
COPY frontend/package*.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ .
RUN npm run build
FROM nginx:1.28.0-alpine
COPY backend/infra/platform/web.nginx.conf /etc/nginx/conf.d/default.conf
COPY backend/infra/platform/web.security-headers.conf /etc/nginx/web-security-headers.conf
COPY --from=build /app/dist/trazatex_webapp/browser /usr/share/nginx/html
