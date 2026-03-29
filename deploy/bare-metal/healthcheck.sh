#!/bin/bash
# Health check all gateway services
# Usage: ./healthcheck.sh

SERVICES=(
    "identity-service:8081"
    "management-api:8082"
    "gateway-runtime:8080"
    "analytics-service:8083"
    "notification-service:8084"
    "gateway-portal:3000"
    "gateway-admin:3001"
)

echo "=== API Gateway Platform Health Check ==="
echo "Date: $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
echo ""

ALL_HEALTHY=true

for SERVICE in "${SERVICES[@]}"; do
    NAME="${SERVICE%%:*}"
    PORT="${SERVICE##*:}"

    # Use /actuator/health/liveness for Spring Boot, / for Next.js
    if [[ "$PORT" == "3000" || "$PORT" == "3001" ]]; then
        URL="http://127.0.0.1:${PORT}/"
    else
        URL="http://127.0.0.1:${PORT}/actuator/health/liveness"
    fi

    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 5 "$URL" 2>/dev/null)

    if [[ "$HTTP_CODE" == "200" ]]; then
        echo "  [OK]   ${NAME} (port ${PORT})"
    else
        echo "  [FAIL] ${NAME} (port ${PORT}) - HTTP ${HTTP_CODE}"
        ALL_HEALTHY=false
    fi
done

echo ""
if $ALL_HEALTHY; then
    echo "Status: ALL SERVICES HEALTHY"
    exit 0
else
    echo "Status: ONE OR MORE SERVICES UNHEALTHY"
    exit 1
fi
