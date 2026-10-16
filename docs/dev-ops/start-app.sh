#!/usr/bin/env bash
set -euo pipefail

# 非 docker compose 一键启动脚本
# 等价来源：docs/dev-ops/docker-compose-app.yml
# 使用方式：
#   chmod +x start-app.sh
#   ./start-app.sh
#
# 可选环境变量：
#   LLM_API_KEY=你的key ./start-app.sh

APP_NAME="dsh-java"
IMAGE="registry.cn-hangzhou.aliyuncs.com/linlannet/dsh-java:0.2.1"
NETWORK_NAME="my-network"
SERVER_PORT="8090"
LLM_API_KEY="${LLM_API_KEY:-${DEEPSEEK_API_KEY:-sk-local}}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}"

echo "[1/5] 准备数据目录..."
mkdir -p data plugins .dsh workspaces

echo "[2/5] 准备 Docker 网络：${NETWORK_NAME}"
if ! docker network inspect "${NETWORK_NAME}" >/dev/null 2>&1; then
  docker network create --driver bridge "${NETWORK_NAME}" >/dev/null
fi

echo "[3/5] 清理旧容器：${APP_NAME}"
if docker ps -a --format '{{.Names}}' | grep -qx "${APP_NAME}"; then
  docker rm -f "${APP_NAME}" >/dev/null
fi

echo "[4/5] 启动容器：${APP_NAME}"
docker run -d \
  --name "${APP_NAME}" \
  --restart on-failure \
  --network "${NETWORK_NAME}" \
  -p "${SERVER_PORT}:${SERVER_PORT}" \
  -e TZ=PRC \
  -e SERVER_PORT="${SERVER_PORT}" \
  -e LLM_API_KEY="${LLM_API_KEY}" \
  -e HARNESS_LLM_DEEPSEEK_BASE_URL="https://apis.itedus.cn/v1" \
  -e HARNESS_LLM_DEEPSEEK_DEFAULT_MODEL="Deepseek-V4.0-Pro" \
  -v "${SCRIPT_DIR}/data:/app/data" \
  -v "${SCRIPT_DIR}/plugins:/app/plugins" \
  -v "${SCRIPT_DIR}/.dsh:/root/.dsh" \
  -v "${SCRIPT_DIR}/workspaces:/app/workspaces" \
  --log-driver json-file \
  --log-opt max-size=10m \
  --log-opt max-file=3 \
  "${IMAGE}"

echo "[5/5] 启动结果："
docker ps --filter "name=${APP_NAME}" --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'

echo ""
echo "启动完成。访问地址：http://127.0.0.1:${SERVER_PORT}"
echo "查看日志：docker logs -f ${APP_NAME}"
echo "停止服务：docker rm -f ${APP_NAME}"
