#!/bin/bash
# ============================================
#   API Gateway Platform — Stop All Services
# ============================================

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG_DIR="$PROJECT_DIR/logs"

GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

echo ""
echo "============================================"
echo "  API Gateway Platform — Stopping Services"
echo "============================================"
echo ""

SERVICES="gateway-admin gateway-portal notification-service analytics-service gateway-runtime management-api identity-service"

for svc in $SERVICES; do
    PID_FILE="$LOG_DIR/$svc.pid"
    if [ -f "$PID_FILE" ]; then
        PID=$(cat "$PID_FILE")
        if kill -0 "$PID" 2>/dev/null; then
            echo -e "  Stopping $svc (PID $PID)... "
            kill "$PID" 2>/dev/null
            # Wait up to 10 seconds for graceful shutdown
            for i in $(seq 1 10); do
                if ! kill -0 "$PID" 2>/dev/null; then
                    break
                fi
                sleep 1
            done
            # Force kill if still running
            if kill -0 "$PID" 2>/dev/null; then
                kill -9 "$PID" 2>/dev/null
                echo -e "    ${RED}Force killed${NC}"
            else
                echo -e "    ${GREEN}Stopped${NC}"
            fi
        else
            echo "  $svc (PID $PID) — already stopped"
        fi
        rm -f "$PID_FILE"
    else
        echo "  $svc — no PID file found"
    fi
done

echo ""
echo -e "  ${GREEN}All services stopped.${NC}"
echo ""

# Cleanup any orphaned Java processes on gateway ports
echo "── Checking for orphaned processes on gateway ports ──"
for port in 8080 8081 8082 8083 8084 3000 3001; do
    PID=$(lsof -ti :$port 2>/dev/null || netstat -tlnp 2>/dev/null | grep ":$port " | awk '{print $7}' | cut -d/ -f1)
    if [ -n "$PID" ]; then
        echo -e "  Port $port still in use by PID $PID — ${RED}kill manually if needed${NC}"
    fi
done

echo ""
echo "============================================"
