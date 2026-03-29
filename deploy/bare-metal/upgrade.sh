#!/bin/bash
# API Gateway Platform - Rolling Upgrade Script
# Usage: sudo ./upgrade.sh
set -e

echo "=== API Gateway Platform - Rolling Upgrade ==="

# 1. Backup current JARs
echo "Backing up current JARs..."
cp /opt/gateway/bin/*.jar /opt/gateway/bin/previous/ 2>/dev/null || true

# 2. Copy new JARs
echo "Deploying new JARs..."
cp identity-service/target/*.jar /opt/gateway/bin/identity-service.jar
cp management-api/target/*.jar /opt/gateway/bin/management-api.jar
cp gateway-runtime/target/*.jar /opt/gateway/bin/gateway-runtime.jar
cp analytics-service/target/*.jar /opt/gateway/bin/analytics-service.jar
cp notification-service/target/*.jar /opt/gateway/bin/notification-service.jar

# 3. Copy new frontend builds
echo "Deploying frontend builds..."
cp -r gateway-portal/.next/standalone/* /opt/gateway/portal/
cp -r gateway-admin/.next/standalone/* /opt/gateway/admin/

# 4. Set ownership
chown -R gateway:gateway /opt/gateway

# 5. Rolling restart (one at a time, verify health)
SERVICES="identity-service management-api gateway-runtime analytics-service notification-service gateway-portal gateway-admin"

for svc in $SERVICES; do
    echo "Restarting $svc..."
    systemctl restart "$svc"

    # Wait for health
    PORT=""
    case $svc in
        identity-service)       PORT=8081 ;;
        management-api)         PORT=8082 ;;
        gateway-runtime)        PORT=8080 ;;
        analytics-service)      PORT=8083 ;;
        notification-service)   PORT=8084 ;;
        gateway-portal)         PORT=3000 ;;
        gateway-admin)          PORT=3001 ;;
    esac

    echo "  Waiting for $svc on port $PORT..."
    for i in $(seq 1 30); do
        if [[ "$PORT" == "3000" || "$PORT" == "3001" ]]; then
            URL="http://127.0.0.1:${PORT}/"
        else
            URL="http://127.0.0.1:${PORT}/actuator/health/liveness"
        fi

        HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 3 "$URL" 2>/dev/null)
        if [[ "$HTTP_CODE" == "200" ]]; then
            echo "  [OK] $svc is healthy"
            break
        fi
        sleep 2
    done

    if [[ "$HTTP_CODE" != "200" ]]; then
        echo "  [FAIL] $svc did not become healthy. Rolling back..."
        cp /opt/gateway/bin/previous/*.jar /opt/gateway/bin/
        systemctl restart "$svc"
        echo "  Rollback complete. Aborting upgrade."
        exit 1
    fi
done

echo ""
echo "=== Upgrade Complete ==="
bash deploy/bare-metal/healthcheck.sh
