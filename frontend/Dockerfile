# syntax=docker/dockerfile:1

FROM node:26-alpine AS build
WORKDIR /app

# Sem isso, a CLI pode perguntar sobre analytics e travar o build não interativo
ENV NG_CLI_ANALYTICS=false

COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund

COPY . .
RUN npm run build -- --configuration production

FROM nginx:stable-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=3 \
    CMD wget -q --spider http://127.0.0.1/ || exit 1
