#!/bin/bash
# ==============================================================================
# Script: deploy-orchestrator.sh
# Target Service: dnsfilt-orchestrator (Dynamic Scaling & HAProxy Controller)
# Architecture: OCI ARM64 Compute VM
# Usage: ./deploy-orchestrator.sh [TAG] [DOCKER_USER]
# ==============================================================================

set -euo pipefail

TAG="${1:-latest}"
DOCKER_USER="${2:-${DOCKERHUB_USERNAME:-ikaushikpal}}"
IMAGE="${DOCKER_USER}/dnsfilt-orchestrator:${TAG}"
CONTAINER_NAME="dnsfilt-orchestrator"
PORT="9095"
ENV_FILE="${ENV_FILE:-/opt/platform/dnsfilt/dnsfilt-orchestrator/.env}"
LOG_DIR="${LOG_DIR_LOCAL:-/var/log/dnsfilt/dnsfilt-orchestrator}"

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

# Ensure directories exist
sudo mkdir -p "${LOG_DIR}" /opt/platform/dnsfilt/dnsfilt-orchestrator/data 2>/dev/null || mkdir -p "${LOG_DIR}" /opt/platform/dnsfilt/dnsfilt-orchestrator/data 2>/dev/null || true

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

# 5. Start new container with Docker socket & HAProxy config mounts
echo "▶️  Starting new ${CONTAINER_NAME} on port ${PORT}..."
$DOCKER run -d \
    --name "${CONTAINER_NAME}" \
    --restart unless-stopped \
    --privileged \
    -p "${PORT}:9095" \
    --add-host kafka-server:host-gateway \
    --add-host host.docker.internal:host-gateway \
    --add-host host.containers.internal:host-gateway \
    -v /var/run/docker.sock:/var/run/docker.sock:z \
    -v /etc/nginx/conf.d/dns_stream.conf:/etc/nginx/conf.d/dns_stream.conf:z \
    -v /opt/platform/dnsfilt/haproxy/haproxy.cfg:/etc/haproxy/haproxy.cfg:z \
    -v /opt/platform/dnsfilt/dnsfilt-orchestrator/data:/app/data:z \
    -v "${LOG_DIR}:/app/logs:z" \
    -v /opt/platform/dnsfilt/dnsfilt-resolver/.env:/opt/platform/dnsfilt/dnsfilt-resolver/.env:ro \
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
            echo "✅ Orchestrator container running stably."
            break
        fi
    elif [ "$STATUS" = "exited" ] || [ "$STATUS" = "dead" ]; then
        echo "❌ Container process terminated with status: ${STATUS}"
        break
    fi
    echo "⏳ Initializing orchestrator reconciler (attempt $i/20, status: ${STATUS})..."
done

# 7. Rollback on failure
if [ "$HEALTHY" = false ]; then
    echo "🚨 Deployment verification failed! Logs:"
    CONTAINER_LOGS=$($DOCKER logs --tail 30 "${CONTAINER_NAME}" 2>&1 || true)
    echo "${CONTAINER_LOGS}"
    
    notify_email "FAILED" "Orchestrator container health verification failed on port ${PORT}.\nRecent Logs:\n${CONTAINER_LOGS}"

    if [ -n "$PREV_IMAGE" ]; then
        echo "🔄 Rolling back to: ${PREV_IMAGE}"
        $DOCKER stop --time 10 "${CONTAINER_NAME}" || true
        $DOCKER rm -f "${CONTAINER_NAME}" || true
        $DOCKER run -d \
            --name "${CONTAINER_NAME}" \
            --restart unless-stopped \
            --privileged \
            -p "${PORT}:9095" \
            --add-host kafka-server:host-gateway \
            --add-host host.docker.internal:host-gateway \
            --add-host host.containers.internal:host-gateway \
            -v /var/run/docker.sock:/var/run/docker.sock:z \
            -v /etc/nginx/conf.d/dns_stream.conf:/etc/nginx/conf.d/dns_stream.conf:z \
            -v /opt/platform/dnsfilt/haproxy/haproxy.cfg:/etc/haproxy/haproxy.cfg:z \
            -v /opt/platform/dnsfilt/dnsfilt-orchestrator/data:/app/data:z \
            -v "${LOG_DIR}:/app/logs:z" \
            -v /opt/platform/dnsfilt/dnsfilt-resolver/.env:/opt/platform/dnsfilt/dnsfilt-resolver/.env:ro \
            ${ENV_ARG} \
            "${PREV_IMAGE}"
    fi
    exit 1
fi

echo "🎉 ${CONTAINER_NAME} (${TAG}) deployed successfully!"
notify_email "SUCCESS"
