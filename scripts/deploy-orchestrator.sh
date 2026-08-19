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
ENV_FILE="/opt/platform/dnsfilt/dnsfilt-orchestrator/.env"
LOG_DIR="/var/log/dnsfilt/dnsfilt-orchestrator"

echo "================================================================="
echo "🚀 [CD] Deploying ${CONTAINER_NAME} (${IMAGE})"
echo "================================================================="

# Ensure directories exist
sudo mkdir -p "${LOG_DIR}" /opt/platform/dnsfilt/dnsfilt-orchestrator/data

# 1. Pull latest image first
echo "📥 Pulling ${IMAGE}..."
sudo docker pull "${IMAGE}"

# 2. Graceful stop previous instance (allowing 25s for reconciler thread cooldown)
PREV_IMAGE=""
if sudo docker ps -a --format '{{.Names}}' | grep -Eq "^${CONTAINER_NAME}\$"; then
    PREV_IMAGE=$(sudo docker inspect --format '{{.Config.Image}}' "${CONTAINER_NAME}" || true)
    echo "⏸️  Gracefully stopping ${CONTAINER_NAME} (allowing 25s for state cleanup)..."
    sudo docker stop --time 25 "${CONTAINER_NAME}" || true
    sudo docker rm "${CONTAINER_NAME}" || true
fi

# 3. Start new container with Docker socket & HAProxy config mounts
echo "▶️  Starting new ${CONTAINER_NAME}..."
sudo docker run -d \
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
    --env-file "${ENV_FILE}" \
    "${IMAGE}"

# 4. Verification Check
echo "🔍 Verifying ${CONTAINER_NAME} runtime state..."
HEALTHY=false
for i in $(seq 1 12); do
    sleep 2
    if sudo docker ps --format '{{.Names}}' | grep -Eq "^${CONTAINER_NAME}\$"; then
        STATUS=$(sudo docker inspect --format '{{.State.Status}}' "${CONTAINER_NAME}" || true)
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
    sudo docker logs --tail 30 "${CONTAINER_NAME}" || true
    if [ -n "$PREV_IMAGE" ]; then
        echo "🔄 Rolling back to: ${PREV_IMAGE}"
        sudo docker stop --time 10 "${CONTAINER_NAME}" || true
        sudo docker rm "${CONTAINER_NAME}" || true
        sudo docker run -d \
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
            --env-file "${ENV_FILE}" \
            "${PREV_IMAGE}"
    fi
    exit 1
fi

echo "🎉 ${CONTAINER_NAME} (${TAG}) deployed successfully!"
