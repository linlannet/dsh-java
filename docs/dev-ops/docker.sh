#!/usr/bin/env bash
set -euo pipefail

HARNESS_CONTAINER="dsh-java-web"
MALL_CONTAINER="2d-weekend-mall"

HARNESS_IMAGE="registry.cn-hangzhou.aliyuncs.com/linlannet/dsh-java:0.1.6.4"
MALL_IMAGE="registry.cn-hangzhou.aliyuncs.net/linlan/dsh-java-plugins/dsh-java-mall:0.1.1"

MALL_SERVICE_TOKEN="${MALL_SERVICE_TOKEN:-mall-internal-demo-token}"

echo "删除旧容器..."
docker rm -f "$MALL_CONTAINER" "$HARNESS_CONTAINER" >/dev/null 2>&1 || true

echo "拉取镜像..."
docker pull "$HARNESS_IMAGE"
docker pull "$MALL_IMAGE"

echo "启动 Harness..."
docker run -d \
  --name "$HARNESS_CONTAINER" \
  --restart unless-stopped \
  --network host \
  -v dsh-web-data:/app/data \
  -v dsh-web-storage:/app/storage \
  -v dsh-web-plugins:/app/plugins \
  "$HARNESS_IMAGE" >/dev/null

echo "启动 Mall..."
docker run -d \
  --name "$MALL_CONTAINER" \
  --restart unless-stopped \
  --network host \
  -e MALL_ASSISTANT_HARNESS_BASE_URL=http://127.0.0.1:8090 \
  -e MALL_SERVICE_TOKEN="$MALL_SERVICE_TOKEN" \
  "$MALL_IMAGE" >/dev/null

echo "等待 Harness 启动..."
READY=false

for _ in $(seq 1 60); do
  if curl -fsS http://127.0.0.1:8090/api/harness/plugins >/dev/null 2>&1; then
    READY=true
    break
  fi
  sleep 2
done

if [[ "$READY" != true ]]; then
  echo "Harness 启动失败，请查看日志："
  echo "docker logs dsh-java-web"
  exit 1
fi

echo "检查商城插件..."
if curl -fsS http://127.0.0.1:8090/api/harness/plugins 2>/dev/null | grep -q "dsh-java-mall-plugin"; then
  echo "自动配置商城插件..."

  curl -fsS -X POST \
    http://127.0.0.1:8090/api/harness/plugins/dsh-java-mall-plugin/config \
    -H "Content-Type: application/json" \
    -d "{\"configs\":[{\"key\":\"mall.base-url\",\"value\":\"http://127.0.0.1:18080\"},{\"key\":\"mall.service-token\",\"value\":\"$MALL_SERVICE_TOKEN\"}]}" \
    >/dev/null

  curl -fsS -X POST \
    http://127.0.0.1:8090/api/harness/plugins/dsh-java-mall-plugin/disable \
    >/dev/null 2>&1 || true

  curl -fsS -X POST \
    http://127.0.0.1:8090/api/harness/plugins/activate \
    -H "Content-Type: application/json" \
    -d '{"pluginId":"dsh-java-mall-plugin"}' \
    >/dev/null
fi

echo
echo "部署完成："
echo "Harness: http://127.0.0.1:8090"
echo "Mall:    http://127.0.0.1:18080"
echo
echo "现在打开 Harness："
echo "设置 -> 插件管理 -> 下载后选择 JAR (https://github.com/linlannet/dsh-java-plugins/dsh-java-mall/blob/main/docs/mall-agent-plugin-1.0.0-SNAPSHOT.jar)-> 安装并启用"
echo
echo "商城插件地址：http://127.0.0.1:18080"
echo "商城服务令牌：$MALL_SERVICE_TOKEN"