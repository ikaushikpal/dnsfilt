#!/bin/bash
# ==============================================================================
# Script: deploy-analytics.sh
# Target Service: dnsfilt-analytics (Kafka Telemetry Ingestion Engine)
# Architecture: OCI ARM64 Compute VM
# Usage: ./deploy-analytics.sh [TAG] [DOCKER_USER]
# ==============================================================================

set -euo pipefail

TAG="${1:-latest}"
DOCKER_USER="${2:-${DOCKERHUB_USERNAME:-ikaushikpal}}"
IMAGE="${DOCKER_USER}/dnsfilt-analytics:${TAG}"
CONTAINER_NAME="dnsfilt-analytics"
PORT="9091"
ENV_FILE="${ENV_FILE:-/opt/platform/dnsfilt/dnsfilt-analytics/.env}"
LOG_DIR="${LOG_DIR_LOCAL:-/var/log/dnsfilt/dnsfilt-analytics}"

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

# 2. Pull latest image first
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
    -p "${PORT}:9091" \
    --add-host kafka-server:host-gateway \
    --add-host host.docker.internal:host-gateway \
    -v "${LOG_DIR}:/app/logs:z" \
    ${ENV_ARG} \
    "${IMAGE}"

# 6. Verification Check
echo "🔍 Verifying ${CONTAINER_NAME} runtime state..."
HEALTHY=false
for i in $(seq 1 20); do
    sleep 2
    STATUS=$($DOCKER inspect --format '{{.State.Status}}' "${CONTAINER_NAME}" 2>/dev/null || echo "unknown")
    
    if [ "$STATUS" = "running" ]; then
        if [ "$i" -ge 4 ]; then
            HEALTHY=true
            echo "✅ Analytics container running stably."
            break
        fi
    elif [ "$STATUS" = "exited" ] || [ "$STATUS" = "dead" ]; then
        echo "❌ Container process terminated with status: ${STATUS}"
        break
    fi
    echo "⏳ Initializing telemetry consumer (attempt $i/20, status: ${STATUS})..."
done

# 7. Rollback on failure
if [ "$HEALTHY" = false ]; then
    echo "🚨 Deployment verification failed! Logs:"
    CONTAINER_LOGS=$($DOCKER logs --tail 30 "${CONTAINER_NAME}" 2>&1 || true)
    echo "${CONTAINER_LOGS}"
    
    notify_email "FAILED" "Analytics container health verification failed on port ${PORT}.\nRecent Logs:\n${CONTAINER_LOGS}"

    if [ -n "$PREV_IMAGE" ]; then
        echo "🔄 Rolling back to: ${PREV_IMAGE}"
        $DOCKER stop --time 10 "${CONTAINER_NAME}" 2>/dev/null || true
        $DOCKER rm -f "${CONTAINER_NAME}" 2>/dev/null || true
        $DOCKER run -d \
            --name "${CONTAINER_NAME}" \
            --restart unless-stopped \
            -p "${PORT}:9091" \
            --add-host kafka-server:host-gateway \
            --add-host host.docker.internal:host-gateway \
            -v "${LOG_DIR}:/app/logs:z" \
            ${ENV_ARG} \
            "${PREV_IMAGE}"
    fi
    exit 1
fi

echo "🎉 ${CONTAINER_NAME} (${TAG}) deployed successfully!"
notify_email "SUCCESS"
