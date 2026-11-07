FROM node:24-alpine AS build
WORKDIR /app
COPY landing/package*.json ./
RUN npm ci --no-audit --no-fund
COPY landing/ .
ARG VITE_APP_URL=http://127.0.0.1:14200
ENV VITE_APP_URL=${VITE_APP_URL}
RUN npm run build
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
