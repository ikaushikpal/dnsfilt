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

# Auto-detect docker or sudo docker
if docker ps >/dev/null 2>&1; then
    DOCKER="docker"
elif sudo docker ps >/dev/null 2>&1; then
    DOCKER="sudo docker"
else
    DOCKER="docker"
fi

echo "================================================================="
echo "🚀 [CD] Deploying ${CONTAINER_NAME} (${IMAGE})"
echo "🐳 Docker Command: ${DOCKER}"
echo "📄 Env File: ${ENV_FILE}"
echo "📁 Log Dir: ${LOG_DIR}"
echo "================================================================="

# Ensure directories exist
sudo mkdir -p "${LOG_DIR}" /opt/platform/dnsfilt/dnsfilt-orchestrator/data 2>/dev/null || mkdir -p "${LOG_DIR}" /opt/platform/dnsfilt/dnsfilt-orchestrator/data 2>/dev/null || true

# 1. Pull latest image first
echo "📥 Pulling image ${IMAGE}..."
$DOCKER pull "${IMAGE}"

# 2. Graceful stop previous instance (allowing 25s for reconciler thread cooldown)
PREV_IMAGE=""
CONTAINER_EXISTS=$($DOCKER ps -a --format '{{.Names}}' 2>/dev/null | grep -Fqx "${CONTAINER_NAME}" && echo "yes" || echo "no")

if [ "$CONTAINER_EXISTS" = "yes" ]; then
    PREV_IMAGE=$($DOCKER inspect --format '{{.Config.Image}}' "${CONTAINER_NAME}" 2>/dev/null || true)
    echo "⏸️  Gracefully stopping ${CONTAINER_NAME} (allowing 25s for state cleanup)..."
    $DOCKER stop --time 25 "${CONTAINER_NAME}" || true
    $DOCKER rm -f "${CONTAINER_NAME}" || true
fi

# Build env-file argument safely
ENV_ARG=""
if [ -f "${ENV_FILE}" ]; then
    ENV_ARG="--env-file ${ENV_FILE}"
else
    echo "⚠️ Warning: ${ENV_FILE} not found. Running with default container environment."
fi

# 3. Start new container with Docker socket & HAProxy config mounts
echo "▶️  Starting new ${CONTAINER_NAME}..."
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

# 4. Verification Check
echo "🔍 Verifying ${CONTAINER_NAME} runtime state..."
HEALTHY=false
for i in $(seq 1 12); do
    sleep 2
    IS_RUNNING=$($DOCKER ps --format '{{.Names}}' 2>/dev/null | grep -Fqx "${CONTAINER_NAME}" && echo "yes" || echo "no")
    if [ "$IS_RUNNING" = "yes" ]; then
        STATUS=$($DOCKER inspect --format '{{.State.Status}}' "${CONTAINER_NAME}" 2>/dev/null || true)
        if [ "$STATUS" = "running" ]; then
            HEALTHY=true
            echo "✅ Orchestrator container is running stably."
            break
        fi
    else
        echo "❌ Container exited unexpectedly."
        break
    fi
    echo "⏳ Initializing orchestrator reconciler (attempt $i/12)..."
done

# 5. Rollback on failure
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
