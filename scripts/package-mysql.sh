#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DIST_ROOT="${ROOT_DIR}/dist"
VERSION="$(sed -n '/<artifactId>dsh-java<\/artifactId>/{n;s/.*<version>\(.*\)<\/version>.*/\1/p;}' "${ROOT_DIR}/pom.xml")"
PACKAGE_NAME="dsh-java-local"
PACKAGE_DIR="${DIST_ROOT}/${PACKAGE_NAME}"
APP_JAR=""

usage() {
  cat <<'EOF'
Usage: scripts/package-local.sh [--skip-build] [--zip]

Options:
  --skip-build   Reuse the existing app JAR and plugin JAR.
  --zip          Create a distributable ZIP after assembling the package.
  -h, --help     Show this help.

The package starts with the inner mysql database.
EOF
}

SKIP_BUILD=false
CREATE_ZIP=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-build) SKIP_BUILD=true ;;
    --zip) CREATE_ZIP=true ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 1 ;;
  esac
  shift
done

if ! command -v mvn >/dev/null 2>&1; then
  echo "Maven 3.9+ is required. Install it or put 'mvn' on PATH." >&2
  exit 1
fi

if [[ "${SKIP_BUILD}" != true ]]; then
  (cd "${ROOT_DIR}" && mvn clean package -DskipTests)
fi

APP_JAR="$(find "${ROOT_DIR}/dsh-java-app/target" -maxdepth 1 -type f -name 'dsh-java-app-*.jar' ! -name '*sources.jar' ! -name '*javadoc.jar' | sort | tail -1)"
PLUGIN_JAR="${ROOT_DIR}/plugins/sample-tools-plugin-1.0.0.jar"

if [[ -z "${APP_JAR}" ]]; then
  echo "App JAR not found. Run the script without --skip-build." >&2
  exit 1
fi

if [[ ! -f "${PLUGIN_JAR}" ]]; then
  echo "Sample plugin JAR not found: ${PLUGIN_JAR}" >&2
  exit 1
fi

rm -rf "${PACKAGE_DIR}"
mkdir -p "${PACKAGE_DIR}/bin" "${PACKAGE_DIR}/plugins" "${PACKAGE_DIR}/skills" "${PACKAGE_DIR}/workspaces"

cp "${APP_JAR}" "${PACKAGE_DIR}/dsh-java.jar"
cp "${PLUGIN_JAR}" "${PACKAGE_DIR}/plugins/sample-tools-plugin-1.0.0.jar"
cp -R "${ROOT_DIR}/plugins/dsh-demo-plugin" "${PACKAGE_DIR}/plugins/dsh-demo-plugin"
cp -R "${ROOT_DIR}/skills/." "${PACKAGE_DIR}/skills/"
cp "${ROOT_DIR}/scripts/install-plugin.sh" "${PACKAGE_DIR}/bin/install-plugin.sh"

cat > "${PACKAGE_DIR}/start.sh" <<'EOF'
#!/usr/bin/env bash

set -euo pipefail

cd "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

JAVA_BIN="${JAVA_HOME:+${JAVA_HOME}/bin/java}"
JAVA_BIN="${JAVA_BIN:-java}"

exec "${JAVA_BIN}" ${DSH_JAVA_OPTS:-} \
  -jar dsh-java.jar \
  "$@"
EOF

cat > "${PACKAGE_DIR}/start.bat" <<'EOF'
@echo off
setlocal
cd /d "%~dp0"
java %DSH_JAVA_OPTS% -jar dsh-java.jar %*
endlocal
EOF

cat > "${PACKAGE_DIR}/README.txt" <<EOF
dsh-java ${VERSION} local package

Requirements:
- Java 17+

Start:
  ./start.sh

Windows:
  start.bat

Console:
  http://localhost:8090/

Notes:
- Uses mysql.
- No Docker or MySQL is required.
- Set LLM_API_KEY before starting if your model endpoint requires it.
- Optional JVM settings can be passed through DSH_JAVA_OPTS.
EOF

chmod +x "${PACKAGE_DIR}/start.sh" "${PACKAGE_DIR}/bin/install-plugin.sh"

if [[ "${CREATE_ZIP}" == true ]]; then
  if ! command -v zip >/dev/null 2>&1; then
    echo "zip command not found; package directory was created but ZIP was skipped." >&2
    exit 1
  fi
  rm -f "${DIST_ROOT}/${PACKAGE_NAME}-${VERSION}.zip"
  (cd "${DIST_ROOT}" && zip -qr "${PACKAGE_NAME}-${VERSION}.zip" "${PACKAGE_NAME}")
  echo "Package: ${PACKAGE_DIR}"
  echo "Archive: ${DIST_ROOT}/${PACKAGE_NAME}-${VERSION}.zip"
else
  echo "Package: ${PACKAGE_DIR}"
fi
