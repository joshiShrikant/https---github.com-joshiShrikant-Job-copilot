# ==============================================================================
# EuroJob Copilot Frontend - Multi-stage Production Dockerfile
# ==============================================================================

# Stage 1: Build Angular Standalone Application
FROM node:22-alpine AS builder
WORKDIR /app

ENV NG_CLI_ANALYTICS=false

COPY package*.json ./
RUN npm ci --legacy-peer-deps

COPY . .
RUN npm run build -- --configuration=production

# Stage 2: Serve with Nginx
FROM nginx:1.27-alpine

# Remove default Nginx configuration and apply custom SPA + proxy config
RUN rm /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy compiled Angular browser bundle
COPY --from=builder /app/dist/frontend/browser /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget -q --spider http://127.0.0.1:80/ || exit 1

CMD ["nginx", "-g", "daemon off;"]

