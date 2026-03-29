#!/bin/bash
# ============================================
#   API Gateway Platform — Database Setup
#   Run this ONCE before first startup
# ============================================

set -e

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_USER="${DB_USERNAME:-postgres}"
DB_PASS="${DB_PASSWORD:-postgres}"
DB_NAME="${DB_NAME:-gateway}"

export PGPASSWORD="$DB_PASS"

GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

echo ""
echo "============================================"
echo "  Database Setup"
echo "============================================"
echo "  Host: $DB_HOST:$DB_PORT"
echo "  User: $DB_USER"
echo "  DB:   $DB_NAME"
echo ""

# Create database if not exists
echo -n "  Creating database '$DB_NAME'... "
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -tc "SELECT 1 FROM pg_database WHERE datname = '$DB_NAME'" | grep -q 1 && {
    echo -e "${GREEN}already exists${NC}"
} || {
    psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -c "CREATE DATABASE $DB_NAME;"
    echo -e "${GREEN}created${NC}"
}

# Create schemas
echo "  Creating schemas..."
for schema in identity gateway analytics audit notification; do
    echo -n "    $schema... "
    psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c "CREATE SCHEMA IF NOT EXISTS $schema;" > /dev/null 2>&1
    echo -e "${GREEN}OK${NC}"
done

echo ""
echo -e "  ${GREEN}Database setup complete!${NC}"
echo ""

# ── RabbitMQ vhost setup ─────────────────────────────────

RABBIT_USER="${RABBITMQ_USERNAME:-guest}"
RABBIT_VHOST="${RABBITMQ_VHOST:-/cem}"

echo "============================================"
echo "  RabbitMQ Setup"
echo "============================================"
echo "  User:  $RABBIT_USER"
echo "  VHost: $RABBIT_VHOST"
echo ""

if command -v rabbitmqctl &> /dev/null; then
    echo -n "  Creating vhost '$RABBIT_VHOST'... "
    rabbitmqctl add_vhost "$RABBIT_VHOST" 2>/dev/null && echo -e "${GREEN}created${NC}" || echo -e "${GREEN}already exists${NC}"

    echo -n "  Setting permissions for '$RABBIT_USER'... "
    rabbitmqctl set_permissions -p "$RABBIT_VHOST" "$RABBIT_USER" ".*" ".*" ".*" 2>/dev/null && echo -e "${GREEN}OK${NC}" || echo -e "${RED}failed${NC}"
else
    echo -e "  ${RED}rabbitmqctl not found — set up vhost manually:${NC}"
    echo "    rabbitmqctl add_vhost $RABBIT_VHOST"
    echo "    rabbitmqctl set_permissions -p $RABBIT_VHOST $RABBIT_USER '.*' '.*' '.*'"
fi

echo ""
echo "============================================"
echo -e "  ${GREEN}Setup complete! Run ./build-all.sh next.${NC}"
echo "============================================"
