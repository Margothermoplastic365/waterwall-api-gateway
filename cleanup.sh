#!/usr/bin/env bash
# =============================================================================
# Waterwall API Gateway — Cleanup Script
# Stops all services, removes containers/volumes, and cleans up the project.
# Run this before a fresh setup.sh
#
# Usage:
#   sudo ./cleanup.sh           # full cleanup (keeps repo)
#   sudo ./cleanup.sh --all     # full cleanup + delete repo
# =============================================================================

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

log()   { echo -e "${GREEN}[✓]${NC} $1"; }
warn()  { echo -e "${YELLOW}[!]${NC} $1"; }
step()  { echo -e "\n${CYAN}==> $1${NC}"; }

DELETE_REPO=false
for arg in "$@"; do
  case "$arg" in
    --all) DELETE_REPO=true ;;
  esac
done

REPO_DIR="$HOME/waterwall-api-gateway"

# -----------------------------------------------
# 1. Stop PM2 processes
# -----------------------------------------------
step "Stopping PM2 processes"

if command -v pm2 &>/dev/null; then
  pm2 stop all 2>/dev/null || true
  pm2 delete all 2>/dev/null || true
  pm2 kill 2>/dev/null || true
  pm2 unstartup 2>/dev/null || true
  log "PM2 processes stopped and removed"
else
  warn "PM2 not found — skipping"
fi

# -----------------------------------------------
# 2. Kill any leftover Java/Node processes
# -----------------------------------------------
step "Killing leftover service processes"

# Kill Java services (Spring Boot jars)
pkill -f "identity-service-1.0.0-SNAPSHOT.jar" 2>/dev/null || true
pkill -f "management-api-1.0.0-SNAPSHOT.jar" 2>/dev/null || true
pkill -f "gateway-runtime-1.0.0-SNAPSHOT.jar" 2>/dev/null || true
pkill -f "analytics-service-1.0.0-SNAPSHOT.jar" 2>/dev/null || true
pkill -f "notification-service-1.0.0-SNAPSHOT.jar" 2>/dev/null || true

# Kill Next.js frontends
pkill -f "next start -p 3000" 2>/dev/null || true
pkill -f "next start -p 3001" 2>/dev/null || true
pkill -f "next dev.*3000" 2>/dev/null || true
pkill -f "next dev.*3001" 2>/dev/null || true

log "Service processes killed"

# -----------------------------------------------
# 3. Stop and remove Docker containers + volumes
# -----------------------------------------------
step "Stopping Docker containers and removing volumes"

if command -v docker &>/dev/null; then
  if [[ -d "$REPO_DIR/deploy/docker" ]]; then
    cd "$REPO_DIR/deploy/docker"
    docker compose down -v 2>/dev/null || true
    cd "$HOME"
  fi

  # Remove any orphan waterwall containers
  docker ps -a --filter "name=gateway-" -q 2>/dev/null | xargs -r docker rm -f 2>/dev/null || true

  # Remove waterwall volumes
  docker volume ls -q --filter "name=docker_postgres-data" 2>/dev/null | xargs -r docker volume rm 2>/dev/null || true
  docker volume ls -q --filter "name=docker_rabbitmq-data" 2>/dev/null | xargs -r docker volume rm 2>/dev/null || true

  log "Docker containers and volumes removed"
else
  warn "Docker not found — skipping"
fi

# -----------------------------------------------
# 4. Clean build artifacts
# -----------------------------------------------
step "Cleaning build artifacts"

if [[ -d "$REPO_DIR" ]]; then
  # Maven target directories
  rm -rf "$REPO_DIR"/*/target 2>/dev/null || true
  rm -rf "$REPO_DIR"/common/*/target 2>/dev/null || true

  # Next.js build directories
  rm -rf "$REPO_DIR/gateway-portal/.next" 2>/dev/null || true
  rm -rf "$REPO_DIR/gateway-admin/.next" 2>/dev/null || true

  # Node modules
  rm -rf "$REPO_DIR/node_modules" 2>/dev/null || true
  rm -rf "$REPO_DIR/gateway-portal/node_modules" 2>/dev/null || true
  rm -rf "$REPO_DIR/gateway-admin/node_modules" 2>/dev/null || true

  # Logs
  rm -rf "$REPO_DIR/logs" 2>/dev/null || true

  # PM2 ecosystem file
  rm -f "$REPO_DIR/ecosystem.config.js" 2>/dev/null || true

  log "Build artifacts cleaned"
else
  warn "Repo directory not found — skipping"
fi

# -----------------------------------------------
# 5. Delete repo if --all
# -----------------------------------------------
if [[ "$DELETE_REPO" == true ]]; then
  step "Deleting repository"
  rm -rf "$REPO_DIR"
  rm -f "$HOME/setup.sh"
  log "Repository and setup script deleted"
else
  log "Repository kept at $REPO_DIR (use --all to delete)"
fi

# -----------------------------------------------
# 6. Summary
# -----------------------------------------------
step "Cleanup complete"

echo ""
echo "  What was cleaned:"
echo "    - PM2 processes stopped and removed"
echo "    - Java and Node service processes killed"
echo "    - Docker containers stopped, volumes deleted (DB data wiped)"
echo "    - Maven build artifacts removed"
echo "    - Next.js builds removed"
echo "    - node_modules removed"
echo "    - Log files removed"
echo ""
if [[ "$DELETE_REPO" == true ]]; then
  echo "  Repository deleted. To start fresh:"
  echo "    curl -fsSL https://raw.githubusercontent.com/DevLink-Tech-Academy/waterwall-api-gateway/main/setup.sh -o setup.sh"
  echo "    chmod +x setup.sh"
  echo "    sudo ./setup.sh"
else
  echo "  To start fresh:"
  echo "    cd $REPO_DIR && git pull && sudo ./setup.sh --no-clone"
fi
echo ""
