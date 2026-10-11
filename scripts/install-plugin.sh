#!/usr/bin/env bash
# install-plugin.sh — 一键安装并运行 dsh-java Java Native 插件
# 读取 JAR 内 META-INF/plugin.yaml 元数据，调用宿主 REST API 完成安装+运行。
set -euo pipefail

HOST="${PLUGIN_HOST:-http://localhost:8090}"
JAR=""
PLUGIN_ID=""
DISPLAY_NAME=""
PLUGIN_VERSION=""
ENTRYPOINT=""

usage() {
  echo "Usage: $(basename "$0") --jar <path> [--id <plugin-id>] [--name <display-name>] [--version <v>] [--host <url>] [--entrypoint <entry>]"
  echo ""
  echo "Options:"
  echo "  --jar          Required. Path to the plugin JAR file."
  echo "  --id           Plugin ID. Auto-read from plugin.yaml if omitted."
  echo "  --name         Display name. Auto-read from plugin.yaml if omitted."
  echo "  --version      Plugin version. Auto-read from plugin.yaml if omitted."
  echo "  --host         Harness base URL. Default: http://localhost:8090"
  echo "  --entrypoint   JAR file name in plugins dir. Defaults to JAR basename."
  echo ""
  echo "Environment:"
  echo "  PLUGIN_HOST    Override harness base URL."
  exit 1
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --jar) JAR="$2"; shift 2 ;;
    --id) PLUGIN_ID="$2"; shift 2 ;;
    --name) DISPLAY_NAME="$2"; shift 2 ;;
    --version) PLUGIN_VERSION="$2"; shift 2 ;;
    --host) HOST="$2"; shift 2 ;;
    --entrypoint) ENTRYPOINT="$2"; shift 2 ;;
    -h|--help) usage ;;
    *) echo "Unknown option: $1"; usage ;;
  esac
done

if [[ -z "$JAR" ]]; then echo "Error: --jar is required."; usage; fi
if [[ ! -f "$JAR" ]]; then echo "Error: JAR not found: $JAR"; exit 1; fi

# Normalize JAR path
JAR="$(cd "$(dirname "$JAR")" && pwd)/$(basename "$JAR")"

# Default entrypoint is JAR basename
if [[ -z "$ENTRYPOINT" ]]; then
  ENTRYPOINT="$(basename "$JAR")"
fi

# Extract plugin.yaml from JAR
MANIFEST_TMP=$(mktemp)
trap 'rm -f "$MANIFEST_TMP"' EXIT
unzip -p "$JAR" META-INF/plugin.yaml > "$MANIFEST_TMP" 2>/dev/null || {
  echo "Error: Cannot read META-INF/plugin.yaml from $JAR"; exit 1
}

# Extract fields using grep/sed (no Python dependency)
read_field() {
  grep "^$1:" "$MANIFEST_TMP" | head -1 | sed "s/^$1:[[:space:]]*//" | sed 's/[[:space:]]*$//'
}

if [[ -z "$PLUGIN_ID" ]]; then
  PLUGIN_ID=$(read_field "id")
fi
if [[ -z "$DISPLAY_NAME" ]]; then
  DISPLAY_NAME=$(read_field "name")
fi
if [[ -z "$PLUGIN_VERSION" ]]; then
  PLUGIN_VERSION=$(read_field "version")
fi

if [[ -z "$PLUGIN_ID" ]]; then echo "Error: Cannot determine plugin ID."; exit 1; fi
if [[ -z "$DISPLAY_NAME" ]]; then DISPLAY_NAME="$PLUGIN_ID"; fi
if [[ -z "$PLUGIN_VERSION" ]]; then PLUGIN_VERSION="1.0.0"; fi

json_escape() {
  printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' | awk '{printf "%s\\n", $0}' | sed -e 's/\\n$//'
}

echo "▸ Plugin:    $PLUGIN_ID v$PLUGIN_VERSION"
echo "▸ Name:      $DISPLAY_NAME"
echo "▸ JAR:       $JAR"
echo "▸ Host:      $HOST"
echo ""

# Install
echo "→ Installing..."
INSTALL_PAYLOAD=$(printf '{"pluginId":"%s","displayName":"%s","pluginVersion":"%s","runtimeType":"JAVA_NATIVE","sourcePath":"%s","entrypoint":"%s"}' \
  "$(json_escape "$PLUGIN_ID")" "$(json_escape "$DISPLAY_NAME")" "$(json_escape "$PLUGIN_VERSION")" \
  "$(json_escape "$JAR")" "$(json_escape "$ENTRYPOINT")")

INSTALL_RESPONSE=$(curl -sS -X POST "$HOST/api/harness/plugins/install" \
  -H "Content-Type: application/json" \
  -d "$INSTALL_PAYLOAD" 2>&1) || {
  echo "Error: Cannot reach harness at $HOST"; exit 1
}
echo "  $INSTALL_RESPONSE"
echo ""

# Run
echo "→ Running..."
RUN_PAYLOAD=$(printf '{"pluginId":"%s"}' "$PLUGIN_ID")
RUN_RESPONSE=$(curl -sS -X POST "$HOST/api/harness/plugins/run" \
  -H "Content-Type: application/json" \
  -d "$RUN_PAYLOAD" 2>&1)
echo "  $RUN_RESPONSE"
echo ""

echo "✓ Plugin installed and running. Agent tools: plugin__${PLUGIN_ID}__*"
