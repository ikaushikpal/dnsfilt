# Multi-stage Dockerfile for Angular 18 SSR Application
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM node:20-alpine
WORKDIR /app
COPY --from=builder /app/dist/dnsfilt-ui ./dist/dnsfilt-ui
EXPOSE 80
ENV PORT=80
CMD ["node", "dist/dnsfilt-ui/server/server.mjs"]
