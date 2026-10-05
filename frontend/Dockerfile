# SENTINEL v2 frontend — Angular 21 + nginx.
# Multi-stage: Node build -> nginx runtime (non-root on 8080).

# ---- Build stage ----
FROM node:24-alpine AS build
WORKDIR /app

# npm ci needs both files for a clean, reproducible install
# Pin npm to the version that generated package-lock.json (11.6.2).
# npm 11.19+ resolves optional deps differently and breaks npm ci.
RUN npm install -g npm@11.6.2 --no-audit --no-fund

COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .
RUN npm run build

# ---- Runtime stage ----
FROM nginx:alpine

# nginx.conf: /api -> backend:8080, SPA fallback, static caching
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Angular 21 output: dist/<project>/browser
COPY --from=build /app/dist/sentinel-ui/browser /usr/share/nginx/html

# Non-root: nginx user owns what it needs, listens on 8080 (see nginx.conf)
RUN chown -R nginx:nginx /usr/share/nginx/html \
    && chown -R nginx:nginx /var/cache/nginx \
    && chown -R nginx:nginx /var/log/nginx \
    && touch /var/run/nginx.pid && chown nginx:nginx /var/run/nginx.pid

USER nginx

EXPOSE 8080
