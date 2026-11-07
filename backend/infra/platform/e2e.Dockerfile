FROM docker:cli AS cli
FROM mcr.microsoft.com/playwright:v1.64.1-noble
COPY --from=cli /usr/local/bin/docker /usr/local/bin/docker
WORKDIR /workspace/frontend
COPY frontend/package*.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ .
COPY backend/infra/platform/run-e2e.mjs /workspace/backend/infra/platform/run-e2e.mjs
CMD ["node", "/workspace/backend/infra/platform/run-e2e.mjs"]