# ─── Stage 1: Install dependencies ────────────────────────────────────────────
FROM node:20-alpine AS deps
WORKDIR /app

# Install libc compat for native binaries (e.g. SWC compiler on Alpine)
RUN apk add --no-cache libc6-compat

COPY package.json package-lock.json* ./
RUN npm ci --frozen-lockfile

# ─── Stage 2: Build ────────────────────────────────────────────────────────────
FROM node:20-alpine AS builder
WORKDIR /app

# NEXT_PUBLIC_* variables are baked into the JS bundle at build time.
# The browser always connects to the gateway via its published host port.
ARG NEXT_PUBLIC_API_URL=http://localhost:8080
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL

# Disable Next.js telemetry inside CI/Docker
ENV NEXT_TELEMETRY_DISABLED=1

COPY --from=deps /app/node_modules ./node_modules
COPY . .

# Production build — uses build:docker (no --turbopack; standard Webpack is stable for production)
RUN npm run build:docker

# ─── Stage 3: Production runtime ───────────────────────────────────────────────
FROM node:20-alpine AS runner
WORKDIR /app

ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
# Listen on all interfaces so Docker port binding works
ENV HOSTNAME=0.0.0.0
ENV PORT=3000

RUN addgroup --system --gid 1001 nodejs \
 && adduser  --system --uid 1001 nextjs

# standalone output: a minimal server — no node_modules copy needed
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
# Static assets (JS chunks, CSS) served by the standalone server
COPY --from=builder --chown=nextjs:nodejs /app/.next/static   ./.next/static
# Public folder (favicon, images, etc.)
COPY --from=builder --chown=nextjs:nodejs /app/public         ./public

USER nextjs

EXPOSE 3000

# next.config.ts `output: "standalone"` produces server.js at the root
CMD ["node", "server.js"]
