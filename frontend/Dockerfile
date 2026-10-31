# ============================================================
# Stage 1: Build - Node.js + Vite
# ============================================================
FROM node:20-alpine AS builder

WORKDIR /app

# Copy package files and install dependencies first (layer caching)
COPY package.json package-lock.json ./
RUN npm ci --silent

# Copy source code and build
COPY . .
RUN npm run build

# ============================================================
# Stage 2: Run - Nginx (serve static files + reverse proxy)
# ============================================================
FROM nginx:1.27-alpine

# Remove default nginx config and replace with ours
RUN rm /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy built static files from builder stage
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
