#!/bin/bash
# ============================================
#   API Gateway Platform — Start All Services
# ============================================

set -e

export JAVA_HOME="${JAVA_HOME:-C:/java/jdk-21.0.1}"
export PATH="$JAVA_HOME/bin:$PATH"

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJECT_DIR"

LOG_DIR="$PROJECT_DIR/logs"
mkdir -p "$LOG_DIR"

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo ""
echo "============================================"
echo "  API Gateway Platform — Starting Services"
echo "============================================"
echo ""
echo "  Project:  $PROJECT_DIR"
echo "  Java:     $(java -version 2>&1 | head -1)"
echo "  Node:     $(node -v 2>/dev/null || echo 'not found')"
echo ""

# ── Helper functions ──────────────────────────────────────

wait_for_health() {
    local name="$1"
    local url="$2"
    local max_wait="${3:-60}"
    local elapsed=0

    printf "  Waiting for %s to be healthy..." "$name"
    while [ $elapsed -lt $max_wait ]; do
        if curl -sf "$url" > /dev/null 2>&1; then
            echo -e " ${GREEN}OK${NC} (${elapsed}s)"
            return 0
        fi
        sleep 2
        elapsed=$((elapsed + 2))
        printf "."
    done
    echo -e " ${RED}TIMEOUT${NC} after ${max_wait}s"
    return 1
}

start_java_service() {
    local name="$1"
    local jar="$2"
    local port="$3"

    if [ ! -f "$jar" ]; then
        echo -e "  ${RED}ERROR: $jar not found. Run 'mvn package -DskipTests' first.${NC}"
        return 1
    fi

    echo -e "  ${YELLOW}Starting $name (port $port)...${NC}"
    nohup java -jar "$jar" > "$LOG_DIR/$name.log" 2>&1 &
    echo $! > "$LOG_DIR/$name.pid"
    echo "    PID: $(cat "$LOG_DIR/$name.pid") | Log: $LOG_DIR/$name.log"
}

start_node_app() {
    local name="$1"
    local dir="$2"
    local port="$3"

    if [ ! -d "$dir/.next" ]; then
        echo -e "  ${RED}ERROR: $dir/.next not found. Run 'cd $dir && npx next build' first.${NC}"
        return 1
    fi

    echo -e "  ${YELLOW}Starting $name (port $port)...${NC}"
    cd "$dir"
    nohup npx next start -p "$port" > "$LOG_DIR/$name.log" 2>&1 &
    echo $! > "$LOG_DIR/$name.pid"
    echo "    PID: $(cat "$LOG_DIR/$name.pid") | Log: $LOG_DIR/$name.log"
    cd "$PROJECT_DIR"
}

# ── Pre-flight checks ────────────────────────────────────

echo "── Pre-flight checks ──"

# Check Java
if ! java -version > /dev/null 2>&1; then
    echo -e "${RED}Java not found. Set JAVA_HOME.${NC}"
    exit 1
fi
echo -e "  Java:       ${GREEN}OK${NC}"

# Check PostgreSQL
if pg_isready -U postgres > /dev/null 2>&1; then
    echo -e "  PostgreSQL: ${GREEN}OK${NC}"
else
    echo -e "  PostgreSQL: ${RED}NOT RUNNING${NC} — start PostgreSQL first"
    exit 1
fi

# Check RabbitMQ
if curl -sf http://localhost:15672 > /dev/null 2>&1 || rabbitmqctl status > /dev/null 2>&1; then
    echo -e "  RabbitMQ:   ${GREEN}OK${NC}"
else
    echo -e "  RabbitMQ:   ${YELLOW}UNKNOWN${NC} — may not be running"
fi

# Check JARs exist
if [ ! -f "identity-service/target/identity-service-1.0.0-SNAPSHOT.jar" ]; then
    echo -e "  JARs:       ${RED}NOT BUILT${NC}"
    echo ""
    echo "  Building now... (mvn package -DskipTests -B)"
    mvn package -DskipTests -B -q
    echo -e "  JARs:       ${GREEN}BUILT${NC}"
else
    echo -e "  JARs:       ${GREEN}OK${NC}"
fi

echo ""

# ── Start services in order ──────────────────────────────

echo "── Starting backend services ──"
echo ""

# 1. Identity Service (must start first)
start_java_service "identity-service" \
    "identity-service/target/identity-service-1.0.0-SNAPSHOT.jar" 8081
wait_for_health "Identity Service" "http://localhost:8081/actuator/health/liveness" 60

# 2. Management API
start_java_service "management-api" \
    "management-api/target/management-api-1.0.0-SNAPSHOT.jar" 8082
wait_for_health "Management API" "http://localhost:8082/actuator/health/liveness" 45

# 3. Gateway Runtime
start_java_service "gateway-runtime" \
    "gateway-runtime/target/gateway-runtime-1.0.0-SNAPSHOT.jar" 8080
wait_for_health "Gateway Runtime" "http://localhost:8080/actuator/health/liveness" 45

# 4. Analytics Service
start_java_service "analytics-service" \
    "analytics-service/target/analytics-service-1.0.0-SNAPSHOT.jar" 8083
wait_for_health "Analytics Service" "http://localhost:8083/actuator/health/liveness" 30

# 5. Notification Service
start_java_service "notification-service" \
    "notification-service/target/notification-service-1.0.0-SNAPSHOT.jar" 8084
wait_for_health "Notification Service" "http://localhost:8084/actuator/health/liveness" 30

echo ""
echo "── Starting frontend apps ──"
echo ""

# 6. Developer Portal
start_node_app "gateway-portal" "gateway-portal" 3000
sleep 3

# 7. Admin Dashboard
start_node_app "gateway-admin" "gateway-admin" 3001
sleep 3

# ── Summary ──────────────────────────────────────────────

echo ""
echo "============================================"
echo -e "  ${GREEN}All services started!${NC}"
echo "============================================"
echo ""
echo "  Portal:      http://localhost:3000"
echo "  Admin:       http://localhost:3001"
echo "  Gateway:     http://localhost:8080"
echo "  Gateway gRPC:localhost:9090"
echo "  Identity:    http://localhost:8081"
echo "  Management:  http://localhost:8082"
echo "  Analytics:   http://localhost:8083"
echo "  Notification:http://localhost:8084"
echo ""
echo "  Login:       admin@gateway.local / changeme"
echo ""
echo "  RabbitMQ:    amqp://guest:guest@localhost:5672/cem"
echo "  Database:    postgres://postgres:postgres@localhost:5432/gateway"
echo ""
echo "  Logs:        $LOG_DIR/"
echo "  PIDs:        $LOG_DIR/*.pid"
echo ""
echo "  Stop all:    ./stop-all.sh"
echo "============================================"
