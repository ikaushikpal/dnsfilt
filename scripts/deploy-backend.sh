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

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
EMAIL_REPORTER="${SCRIPT_DIR}/send_deploy_email.py"

notify_email() {
    local status="$1"
    local error_msg="${2:-}"
    if [ -f "${EMAIL_REPORTER}" ] && command -v python3 >/dev/null 2>&1; then
        python3 "${EMAIL_REPORTER}" "${CONTAINER_NAME}" "${status}" "${TAG}" "${PORT}" "${error_msg}" || true
    fi
}

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

# 3. Unconditionally stop & remove any existing container to guarantee name and port release
echo "⏸️  Stopping & removing any previous ${CONTAINER_NAME} instance..."
PREV_IMAGE=$($DOCKER inspect --format '{{.Config.Image}}' "${CONTAINER_NAME}" 2>/dev/null || true)
$DOCKER stop --time 25 "${CONTAINER_NAME}" 2>/dev/null || true
$DOCKER rm -f "${CONTAINER_NAME}" 2>/dev/null || true
docker stop --time 10 "${CONTAINER_NAME}" 2>/dev/null || true
docker rm -f "${CONTAINER_NAME}" 2>/dev/null || true

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

# 6. Verification Check (Allow up to 60 seconds for Spring Boot + Oracle ATP initialization)
echo "🔍 Verifying ${CONTAINER_NAME} health..."
HEALTHY=false
for i in $(seq 1 30); do
    sleep 2
    STATUS=$($DOCKER inspect --format '{{.State.Status}}' "${CONTAINER_NAME}" 2>/dev/null || echo "unknown")
    
    if [ "$STATUS" = "running" ]; then
        HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "http://127.0.0.1:${PORT}/actuator/health" 2>/dev/null || curl -s -o /dev/null -w "%{http_code}" "http://127.0.0.1:${PORT}/api/auth/validate" 2>/dev/null || curl -s -o /dev/null -w "%{http_code}" "http://127.0.0.1:${PORT}/" 2>/dev/null || true)
        if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "401" ] || [ "$HTTP_CODE" = "403" ] || [ "$HTTP_CODE" = "302" ]; then
            HEALTHY=true
            echo "✅ Backend is healthy and accepting traffic! HTTP Response: $HTTP_CODE"
            break
        fi
    elif [ "$STATUS" = "exited" ] || [ "$STATUS" = "dead" ]; then
        echo "❌ Container process terminated with status: ${STATUS}"
        break
    fi
    echo "⏳ Initializing Spring Boot & database (attempt $i/30, status: ${STATUS})..."
done

# 7. Rollback on failure
if [ "$HEALTHY" = false ]; then
    echo "🚨 Deployment verification failed! Logs:"
    CONTAINER_LOGS=$($DOCKER logs --tail 30 "${CONTAINER_NAME}" 2>&1 || true)
    echo "${CONTAINER_LOGS}"
    
    notify_email "FAILED" "Container health verification failed on port ${PORT}.\nRecent Logs:\n${CONTAINER_LOGS}"

    if [ -n "$PREV_IMAGE" ]; then
        echo "🔄 Rolling back to: ${PREV_IMAGE}"
        $DOCKER stop --time 10 "${CONTAINER_NAME}" 2>/dev/null || true
        $DOCKER rm -f "${CONTAINER_NAME}" 2>/dev/null || true
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
notify_email "SUCCESS"
