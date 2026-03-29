#!/bin/bash
# API Gateway Platform - Bare Metal Installation Script
# Usage: sudo ./install.sh
set -e

echo "=== API Gateway Platform - Installation ==="

# 1. Create system user
if ! id "gateway" &>/dev/null; then
    echo "Creating gateway system user..."
    useradd --system --no-create-home --shell /usr/sbin/nologin gateway
fi

# 2. Create directories
echo "Creating directories..."
mkdir -p /opt/gateway/bin
mkdir -p /opt/gateway/bin/previous
mkdir -p /opt/gateway/config/identity-service
mkdir -p /opt/gateway/config/management-api
mkdir -p /opt/gateway/config/gateway-runtime
mkdir -p /opt/gateway/config/analytics-service
mkdir -p /opt/gateway/config/notification-service
mkdir -p /opt/gateway/portal
mkdir -p /opt/gateway/admin
mkdir -p /opt/gateway/logs
mkdir -p /etc/gateway

# 3. Copy JAR files
echo "Copying JAR files..."
cp identity-service/target/*.jar /opt/gateway/bin/identity-service.jar
cp management-api/target/*.jar /opt/gateway/bin/management-api.jar
cp gateway-runtime/target/*.jar /opt/gateway/bin/gateway-runtime.jar
cp analytics-service/target/*.jar /opt/gateway/bin/analytics-service.jar
cp notification-service/target/*.jar /opt/gateway/bin/notification-service.jar

# 4. Copy Next.js builds
echo "Copying frontend builds..."
cp -r gateway-portal/.next/standalone/* /opt/gateway/portal/
cp -r gateway-admin/.next/standalone/* /opt/gateway/admin/

# 5. Copy environment file template
if [ ! -f /etc/gateway/.env ]; then
    echo "Creating environment file..."
    cp deploy/docker/.env.example /etc/gateway/.env
    chmod 600 /etc/gateway/.env
    chown gateway:gateway /etc/gateway/.env
    echo "IMPORTANT: Edit /etc/gateway/.env with your production values!"
fi

# 6. Install systemd services
echo "Installing systemd services..."
cp deploy/bare-metal/systemd/*.service /etc/systemd/system/
systemctl daemon-reload

# 7. Set ownership
chown -R gateway:gateway /opt/gateway

# 8. Enable services
echo "Enabling services..."
SERVICES="identity-service management-api gateway-runtime analytics-service notification-service gateway-portal gateway-admin"
for svc in $SERVICES; do
    systemctl enable "$svc"
done

# 9. Start services (in order)
echo "Starting services..."
systemctl start identity-service
echo "Waiting for identity-service..."
sleep 10

systemctl start management-api
systemctl start gateway-runtime
systemctl start analytics-service
systemctl start notification-service

echo "Waiting for backend services..."
sleep 5

systemctl start gateway-portal
systemctl start gateway-admin

# 10. Verify
echo ""
echo "Verifying health..."
sleep 10
bash deploy/bare-metal/healthcheck.sh

echo ""
echo "=== Installation Complete ==="
echo "Services: systemctl status {service-name}"
echo "Logs:     journalctl -u {service-name} -f"
echo "Config:   /etc/gateway/.env"
