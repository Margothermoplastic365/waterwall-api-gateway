#!/bin/bash
# ============================================
#   API Gateway Platform — Build Everything
# ============================================

set -e

export JAVA_HOME="${JAVA_HOME:-C:/java/jdk-21.0.1}"
export PATH="$JAVA_HOME/bin:$PATH"

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJECT_DIR"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo ""
echo "============================================"
echo "  API Gateway Platform — Full Build"
echo "============================================"
echo ""

# ── Step 1: Build backend ────────────────────────────────

echo -e "${YELLOW}[1/3] Building backend (Maven)...${NC}"
mvn clean package -DskipTests -B
echo -e "${GREEN}[1/3] Backend build complete.${NC}"
echo ""

# ── Step 2: Install frontend dependencies ─────────────────

echo -e "${YELLOW}[2/3] Installing frontend dependencies...${NC}"
npm install
echo -e "${GREEN}[2/3] Dependencies installed.${NC}"
echo ""

# ── Step 3: Build frontend apps ──────────────────────────

echo -e "${YELLOW}[3/3] Building frontend apps...${NC}"

echo "  Building gateway-portal..."
cd gateway-portal && npx next build && cd "$PROJECT_DIR"

echo "  Building gateway-admin..."
cd gateway-admin && npx next build && cd "$PROJECT_DIR"

echo -e "${GREEN}[3/3] Frontend build complete.${NC}"
echo ""

# ── Summary ──────────────────────────────────────────────

echo "============================================"
echo -e "  ${GREEN}Build complete!${NC}"
echo "============================================"
echo ""
echo "  Backend JARs:"
for svc in identity-service management-api gateway-runtime analytics-service notification-service; do
    JAR="$svc/target/$svc-1.0.0-SNAPSHOT.jar"
    if [ -f "$JAR" ]; then
        SIZE=$(du -h "$JAR" | cut -f1)
        echo -e "    ${GREEN}$svc${NC} ($SIZE)"
    else
        echo -e "    ${RED}$svc — NOT FOUND${NC}"
    fi
done
echo ""
echo "  Frontend:"
echo -e "    ${GREEN}gateway-portal${NC} (.next built)"
echo -e "    ${GREEN}gateway-admin${NC}  (.next built)"
echo ""
echo "  Run ./start-all.sh to start all services."
echo "============================================"
