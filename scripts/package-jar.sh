#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DIST_ROOT="${ROOT_DIR}/dist"
VERSION="$(sed -n '/<artifactId>dsh-java<\/artifactId>/{n;s/.*<version>\(.*\)<\/version>.*/\1/p;}' "${ROOT_DIR}/pom.xml")"
PACKAGE_NAME="dsh-java-jar"
PACKAGE_DIR="${DIST_ROOT}/${PACKAGE_NAME}"

APP_JAR="$(find "${ROOT_DIR}/dsh-java-app/target" -maxdepth 1 -type f -name 'dsh-java-app-*.jar' ! -name '*sources.jar' ! -name '*javadoc.jar' | sort | tail -1)"

if [[ -z "${APP_JAR}" ]]; then
  (cd "${ROOT_DIR}" && mvn clean package -DskipTests)
  APP_JAR="$(find "${ROOT_DIR}/dsh-java-app/target" -maxdepth 1 -type f -name 'dsh-java-app-*.jar' ! -name '*sources.jar' ! -name '*javadoc.jar' | sort | tail -1)"
fi

rm -rf "${PACKAGE_DIR}"
mkdir -p "${PACKAGE_DIR}"
cp "${APP_JAR}" "${PACKAGE_DIR}/dsh-java.jar"

cat > "${PACKAGE_DIR}/start.sh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec java -jar dsh-java.jar --spring.profiles.active=standalone "$@"
EOF

cat > "${PACKAGE_DIR}/start.bat" <<'EOF'
@echo off
setlocal
cd /d "%~dp0"
java -jar dsh-java.jar --spring.profiles.active=standalone %*
endlocal
EOF

cat > "${PACKAGE_DIR}/README.txt" <<EOF
dsh-java ${VERSION} jar-only package

Requirements:
- Java 17+

Start:
  ./start.sh

Windows:
  start.bat

Console:
  http://localhost:8090/

No Docker or MySQL is required.
EOF

chmod +x "${PACKAGE_DIR}/start.sh"

rm -f "${DIST_ROOT}/${PACKAGE_NAME}-${VERSION}.zip"
(cd "${DIST_ROOT}" && zip -qr "${PACKAGE_NAME}-${VERSION}.zip" "${PACKAGE_NAME}")

echo "Package: ${PACKAGE_DIR}"
echo "Archive: ${DIST_ROOT}/${PACKAGE_NAME}-${VERSION}.zip"
