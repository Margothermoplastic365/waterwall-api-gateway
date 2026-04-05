#!/usr/bin/env bash
# =============================================================================
# Waterwall API Gateway — Start Script (PM2)
# Starts infrastructure and all services using PM2.
# Assumes setup.sh has already run.
#
# Usage:
#   ./start.sh              # start everything
#   ./start.sh --build      # rebuild backend before starting
# =============================================================================

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

log()   { echo -e "${GREEN}[✓]${NC} $1"; }
warn()  { echo -e "${YELLOW}[!]${NC} $1"; }
err()   { echo -e "${RED}[✗]${NC} $1"; exit 1; }
step()  { echo -e "\n${CYAN}==> $1${NC}"; }

REBUILD=false

for arg in "$@"; do
  case "$arg" in
    --build) REBUILD=true ;;
  esac
done

# Resolve project root (where this script lives)
PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJECT_ROOT"
export PROJECT_ROOT

# -----------------------------------------------
# 1. Verify prerequisites exist
# -----------------------------------------------
step "Verifying environment"

for cmd in java mvn node npm docker pm2; do
  command -v "$cmd" &>/dev/null || err "$cmd not found — run setup.sh first"
done

# Check jars exist (unless --build)
if [[ "$REBUILD" == false ]]; then
  for jar in identity-service management-api gateway-runtime analytics-service notification-service; do
    [[ -f "$jar/target/${jar}-1.0.0-SNAPSHOT.jar" ]] || { warn "JARs not found — rebuilding..."; REBUILD=true; break; }
  done
fi

# Check ecosystem file exists
[[ -f "$PROJECT_ROOT/ecosystem.config.js" ]] || err "ecosystem.config.js not found — run setup.sh first"

log "Environment OK"

# -----------------------------------------------
# 2. Rebuild if requested
# -----------------------------------------------
if [[ "$REBUILD" == true ]]; then
  step "Building backend services"
  mvn clean install -DskipTests -q
  log "Build complete"
fi

# -----------------------------------------------
# 3. Start infrastructure
# -----------------------------------------------
step "Starting infrastructure (PostgreSQL + RabbitMQ)"

cd "$PROJECT_ROOT/deploy/docker"

# Ensure init-schemas.sql exists
if [[ ! -f "init-schemas.sql" ]]; then
  cat > init-schemas.sql << 'SQLEOF'
CREATE SCHEMA IF NOT EXISTS identity;
CREATE SCHEMA IF NOT EXISTS gateway;
CREATE SCHEMA IF NOT EXISTS analytics;
CREATE SCHEMA IF NOT EXISTS audit;
CREATE SCHEMA IF NOT EXISTS notification;
SQLEOF
fi

docker compose up -d postgres rabbitmq 2>/dev/null

wait_for_service() {
  local name=$1
  local check_cmd=$2
  local max_wait=90

  echo -n "  Waiting for $name... "
  for i in $(seq 1 $max_wait); do
    if eval "$check_cmd" &>/dev/null; then
      echo -e "${GREEN}ready (${i}s)${NC}"
      return 0
    fi
    sleep 1
  done

  echo -e "${YELLOW}timeout${NC}"
  warn "$name not ready after ${max_wait}s — restarting container..."
  docker compose restart "$name" 2>/dev/null || true
  sleep 3

  echo -n "  Retrying $name... "
  for i in $(seq 1 60); do
    if eval "$check_cmd" &>/dev/null; then
      echo -e "${GREEN}ready (${i}s)${NC}"
      return 0
    fi
    sleep 1
  done

  echo -e "${RED}failed${NC}"
  warn "$name did not start. Check: docker compose logs $name"
  warn "Continuing anyway — the service may start shortly..."
  return 0
}

wait_for_service "postgres" "docker compose exec -T postgres pg_isready -U postgres"
wait_for_service "rabbitmq" "docker compose exec -T rabbitmq rabbitmqctl status"

cd "$PROJECT_ROOT"

# -----------------------------------------------
# 4. Stop existing PM2 processes
# -----------------------------------------------
step "Starting services with PM2"

mkdir -p logs
pm2 delete all 2>/dev/null || true

# -----------------------------------------------
# 5. Start identity-service first (other services depend on it)
# -----------------------------------------------
pm2 start "$PROJECT_ROOT/ecosystem.config.js" --only identity-service

echo -n "  Waiting for identity-service... "
for i in $(seq 1 90); do
  if curl -sf "http://localhost:8081/actuator/health/liveness" &>/dev/null; then
    echo -e "${GREEN}ready${NC}"
    break
  fi
  if [[ $i -eq 90 ]]; then
    echo -e "${RED}timeout${NC}"
    warn "identity-service did not start within 90s — check: pm2 logs identity-service"
  fi
  sleep 1
done

# -----------------------------------------------
# 6. Start remaining services
# -----------------------------------------------
pm2 start "$PROJECT_ROOT/ecosystem.config.js" --only management-api
pm2 start "$PROJECT_ROOT/ecosystem.config.js" --only gateway-runtime
pm2 start "$PROJECT_ROOT/ecosystem.config.js" --only analytics-service
pm2 start "$PROJECT_ROOT/ecosystem.config.js" --only notification-service

echo -n "  Waiting for backend services... "
for i in $(seq 1 60); do
  READY=true
  for port in 8082 8080 8083 8084; do
    if ! curl -sf "http://localhost:${port}/actuator/health/liveness" &>/dev/null; then
      READY=false
      break
    fi
  done
  if [[ "$READY" == true ]]; then
    echo -e "${GREEN}all ready${NC}"
    break
  fi
  if [[ $i -eq 60 ]]; then
    echo -e "${YELLOW}some services still starting${NC}"
    warn "Check status with: pm2 status"
  fi
  sleep 1
done

# -----------------------------------------------
# 7. Start frontends
# -----------------------------------------------
mkdir -p logs

# Build if .next dirs don't exist
if [[ ! -d "gateway-portal/.next" || ! -d "gateway-admin/.next" ]]; then
  step "Building frontends"
  export NEXT_PUBLIC_API_URL="http://localhost:8082"
  export NEXT_PUBLIC_IDENTITY_URL="http://localhost:8081"
  export NEXT_PUBLIC_GATEWAY_URL="http://localhost:8080"
  export NEXT_PUBLIC_ANALYTICS_URL="http://localhost:8083"
  if ! npm run build:all 2>"$PROJECT_ROOT/logs/frontend-build.log"; then
    warn "Frontend build failed — falling back to dev mode"
    sed -i 's|"next start -p 3000"|"next dev -p 3000"|' "$PROJECT_ROOT/ecosystem.config.js"
    sed -i 's|"next start -p 3001"|"next dev -p 3001"|' "$PROJECT_ROOT/ecosystem.config.js"
  fi
fi

pm2 start "$PROJECT_ROOT/ecosystem.config.js" --only gateway-portal
pm2 start "$PROJECT_ROOT/ecosystem.config.js" --only gateway-admin

pm2 save

# -----------------------------------------------
# 8. Summary
# -----------------------------------------------
step "Waterwall API Gateway is running (managed by PM2)"

echo ""
echo "  Backend:"
echo "    Gateway Runtime       http://localhost:8080"
echo "    Identity Service      http://localhost:8081"
echo "    Management API        http://localhost:8082"
echo "    Analytics Service     http://localhost:8083"
echo "    Notification Service  http://localhost:8084"
echo ""
echo "  Frontends:"
echo "    Developer Portal      http://localhost:3000"
echo "    Admin Portal          http://localhost:3001"
echo ""
echo "  Infrastructure:"
echo "    RabbitMQ Management   http://localhost:15672  (guest/guest)"
echo ""
echo "  Login:"
echo "    admin@gateway.local / changeme"
echo "    alice@acme-corp.com / password123"
echo ""
echo "  PM2 Commands:"
echo "    pm2 status                  # view all services"
echo "    pm2 logs <service-name>     # tail logs"
echo "    pm2 restart all             # restart everything"
echo "    pm2 stop all                # stop everything"
echo "    pm2 monit                   # monitoring dashboard"
echo ""
echo "  Logs: $PROJECT_ROOT/logs/"
echo ""

pm2 status
