#!/bin/bash
# ==============================================================================
# Script: deploy-backend.sh
# Target Service: dnsfilt-admin-backend (Spring Boot + Angular UI Console)
# Architecture: OCI ARM64 Compute VM
# Usage: ./deploy-backend.sh [TAG] [DOCKER_USER]
# ==============================================================================

set -euo pipefail

TAG="${1:-latest}"
DOCKER_USER="${2:-${DOCKERHUB_USERNAME:-ikaushikpal}}"
IMAGE="${DOCKER_USER}/dnsfilt-admin-backend:${TAG}"
CONTAINER_NAME="dnsfilt-admin-backend"
PORT="9090"
ENV_FILE="${ENV_FILE:-/opt/platform/dnsfilt/dnsfilt-admin-backend/.env}"
LOG_DIR="${LOG_DIR_LOCAL:-/var/log/dnsfilt/dnsfilt-admin-backend}"

# 1. Use sudo docker to access rootful containers and system ports
if command -v sudo >/dev/null 2>&1; then
    DOCKER="sudo docker"
elif command -v docker >/dev/null 2>&1; then
    DOCKER="docker"
else
    DOCKER="podman"
fi

echo "================================================================="
echo "🚀 [CD] Deploying ${CONTAINER_NAME} (${IMAGE})"
echo "🐳 Docker Engine: ${DOCKER}"
echo "📄 Env File: ${ENV_FILE}"
echo "📁 Log Dir: ${LOG_DIR}"
echo "================================================================="

# Ensure log directory exists
sudo mkdir -p "${LOG_DIR}" 2>/dev/null || mkdir -p "${LOG_DIR}" 2>/dev/null || true

# 2. Pull latest image first so downtime is minimized
echo "📥 Pulling image ${IMAGE}..."
$DOCKER pull "${IMAGE}"

# 3. Stop running containers (both rootful and rootless to guarantee port release)
PREV_IMAGE=""
if $DOCKER ps -a --format '{{.Names}}' 2>/dev/null | grep -Fqx "${CONTAINER_NAME}"; then
    PREV_IMAGE=$($DOCKER inspect --format '{{.Config.Image}}' "${CONTAINER_NAME}" 2>/dev/null || true)
    echo "⏸️  Gracefully stopping existing ${CONTAINER_NAME} (allowing 25s for cleanup)..."
    $DOCKER stop --time 25 "${CONTAINER_NAME}" || true
    $DOCKER rm -f "${CONTAINER_NAME}" || true
fi

# Cleanup any lingering rootless container instance with same name
if command -v docker >/dev/null 2>&1 && [ "$DOCKER" != "docker" ]; then
    if docker ps -a --format '{{.Names}}' 2>/dev/null | grep -Fqx "${CONTAINER_NAME}"; then
        echo "🧹 Cleaning up rootless ${CONTAINER_NAME}..."
        docker stop --time 10 "${CONTAINER_NAME}" 2>/dev/null || true
        docker rm -f "${CONTAINER_NAME}" 2>/dev/null || true
    fi
fi

# Short cooldown to allow kernel socket release
sleep 2

# 4. Build env-file argument safely
ENV_ARG=""
if [ -f "${ENV_FILE}" ]; then
    ENV_ARG="--env-file ${ENV_FILE}"
else
    echo "⚠️ Warning: ${ENV_FILE} not found. Running with default environment."
fi

# 5. Start new container
echo "▶️  Starting new ${CONTAINER_NAME} on port ${PORT}..."
$DOCKER run -d \
    --name "${CONTAINER_NAME}" \
    --restart unless-stopped \
    -p "${PORT}:9090" \
    --add-host kafka-server:host-gateway \
    --add-host host.docker.internal:host-gateway \
    --add-host host.containers.internal:host-gateway \
    -v "${LOG_DIR}:/app/logs" \
    ${ENV_ARG} \
    --memory 500M \
    "${IMAGE}"

# 6. Verification Check (Allow up to 30 seconds for Spring Boot startup)
echo "🔍 Verifying ${CONTAINER_NAME} health..."
HEALTHY=false
for i in $(seq 1 15); do
    sleep 2
    IS_RUNNING=$($DOCKER ps --format '{{.Names}}' 2>/dev/null | grep -Fqx "${CONTAINER_NAME}" && echo "yes" || echo "no")
    if [ "$IS_RUNNING" = "yes" ]; then
        HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "http://127.0.0.1:${PORT}/actuator/health" 2>/dev/null || curl -s -o /dev/null -w "%{http_code}" "http://127.0.0.1:${PORT}/api/auth/validate" 2>/dev/null || true)
        if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "401" ] || [ "$HTTP_CODE" = "403" ]; then
            HEALTHY=true
            echo "✅ Backend is healthy! HTTP Response: $HTTP_CODE"
            break
        fi
    else
        echo "❌ Container exited unexpectedly."
        break
    fi
    echo "⏳ Initializing (attempt $i/15)..."
done

# 7. Rollback on failure
if [ "$HEALTHY" = false ]; then
    echo "🚨 Deployment verification failed! Logs:"
    $DOCKER logs --tail 40 "${CONTAINER_NAME}" || true
    if [ -n "$PREV_IMAGE" ]; then
        echo "🔄 Rolling back to: ${PREV_IMAGE}"
        $DOCKER stop --time 10 "${CONTAINER_NAME}" || true
        $DOCKER rm -f "${CONTAINER_NAME}" || true
        $DOCKER run -d \
            --name "${CONTAINER_NAME}" \
            --restart unless-stopped \
            -p "${PORT}:9090" \
            --add-host kafka-server:host-gateway \
            --add-host host.docker.internal:host-gateway \
            --add-host host.containers.internal:host-gateway \
            -v "${LOG_DIR}:/app/logs" \
            ${ENV_ARG} \
            --memory 500M \
            "${PREV_IMAGE}"
    fi
    exit 1
fi

echo "🎉 ${CONTAINER_NAME} (${TAG}) deployed successfully!"
