# Waterwall

**A full-featured, open-source API Gateway Management Platform**

<!-- Badges -->
<!--
[![Build Status](https://img.shields.io/github/actions/workflow/status/your-org/waterwall/ci.yml?branch=main)](https://github.com/your-org/waterwall/actions)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-green.svg)](https://spring.io/projects/spring-boot)
[![Next.js](https://img.shields.io/badge/Next.js-14-black.svg)](https://nextjs.org/)
-->

Waterwall is a production-grade API gateway platform for publishing, securing, monitoring, and monetizing APIs. It provides a complete API lifecycle management solution with an admin portal, a developer portal, real-time analytics, and a high-performance request proxy -- comparable to platforms like WSO2 API Manager and Kong.

---

## Features

### API Lifecycle Management
- Create, version, publish, deprecate, and retire APIs
- API governance with linting and OpenAPI spec validation
- SDK generation for multiple languages
- Try-It console for interactive API testing

### Security
- Multiple authentication types: JWT, API Key, Basic Auth, mTLS, OAuth2
- WAF rules, IP filtering, and geo-blocking
- Role-based access control (RBAC)

### Traffic Control
- Rate limiting with token bucket, sliding window, and strict DB-backed strategies
- Circuit breaker for upstream fault tolerance
- Request proxying with distributed tracing

### Analytics and Monitoring
- Real-time dashboards with SSE streaming
- SLA monitoring with automated breach detection
- Custom report builder with CSV export
- Alerting via email, webhook, and Slack

### Monetization
- Pricing plans and subscription management
- Paystack payment integration
- Usage-based billing

### Notifications
- Asynchronous event processing via RabbitMQ
- Email, webhook, and Slack delivery channels

---

## Architecture

### Platform Overview

![Platform Overview Architecture](https://raw.githubusercontent.com/DevLink-Tech-Academy/waterwall-api-gateway/main/architecture/platform-overview.svg)

### Identity Service

![Identity Service Architecture](https://raw.githubusercontent.com/DevLink-Tech-Academy/waterwall-api-gateway/main/architecture/02-identity-service-detail.svg)

### API Protection & Auth Flows

![API Protection and Authentication Flows](https://raw.githubusercontent.com/DevLink-Tech-Academy/waterwall-api-gateway/main/architecture/03-api-protection-auth-flows.svg)

### Versioning & Environment Flow

![API Versioning and Environment Promotion](https://raw.githubusercontent.com/DevLink-Tech-Academy/waterwall-api-gateway/main/architecture/04-versioning-environment-flow.svg)

> Open [`architecture/architecture-diagrams.html`](architecture/architecture-diagrams.html) locally in a browser for an interactive view with navigation.

### Service Responsibilities

| Service | Port | Description |
|---|---|---|
| **gateway-runtime** | 8080 | Request proxy, auth enforcement, rate limiting, WAF, circuit breaker, distributed tracing |
| **identity-service** | 8081 | User registration/login, JWT/OAuth2 tokens, RBAC, API key management |
| **management-api** | 8082 | API lifecycle (CRUD, versioning, publish), subscriptions, pricing plans, SDK generation, governance |
| **analytics-service** | 8083 | Real-time dashboards, SLA monitoring, custom report builder, SSE streaming, alerting |
| **notification-service** | 8084 | Email, webhook, Slack notifications via RabbitMQ event processing |
| **gateway-portal** | 3000 | Developer portal — API catalog, Try-It console, subscriptions, billing, docs |
| **gateway-admin** | 3001 | Admin portal — API management, analytics, monetization, SLA, user management |

### Shared Libraries

| Module | Purpose |
|---|---|
| `common-dto` | Shared DTOs, global exception handler, API error responses |
| `common-logging` | Centralized logging, trace ID propagation |
| `common-events` | RabbitMQ event definitions and config |
| `common-cache` | Two-tier caching (Caffeine L1 + RabbitMQ invalidation) |
| `common-auth` | JWT authentication filter, shared security utilities |

### API Versioning and Governance

Waterwall supports full API version lifecycle with maker-checker approval:

```
DRAFT ──> IN_REVIEW ──> PUBLISHED ──> DEPRECATED ──> RETIRED
```

- **Multi-level approval** based on API sensitivity (LOW → 1 level, CRITICAL → 3 levels + cooldown)
- **Environment-scoped deployments** (DEV, UAT, STAGING, PROD) with per-environment auth enforcement
- **Environment-scoped API keys and subscriptions** — cross-environment usage is rejected

See [`architecture/04-versioning-environment-flow.md`](architecture/04-versioning-environment-flow.md) for the full data model and flow diagrams.

### Authentication Flows

The gateway supports four authentication types, enforced at the runtime proxy layer:

| Auth Type | Header | Use Case |
|---|---|---|
| **JWT Bearer** | `Authorization: Bearer <token>` | Web/mobile apps, SSO |
| **API Key** | `X-API-Key: <key>` | Server-to-server, IoT |
| **Basic Auth** | `Authorization: Basic <b64>` | Legacy integrations |
| **mTLS** | Client certificate | High-security B2B |

### Database Schema

PostgreSQL with four schemas, managed by Liquibase migrations:

| Schema | Tables | Purpose |
|---|---|---|
| `identity` | users, roles, permissions, sessions, api_keys | Authentication and authorization |
| `gateway` | apis, routes, plans, subscriptions, applications, rate_limits | API lifecycle and traffic management |
| `analytics` | request_logs, metrics_1m/1h/1d, alert_rules, sla_configs | Observability and reporting |
| `notification` | templates, channels, delivery_log | Notification management |

---

## Quick Start

### Prerequisites

- **Docker** and **Docker Compose** v2+
- **Git**

### 1. Clone the repository

```bash
git clone https://github.com/your-org/waterwall.git
cd waterwall
```

### 2. Configure environment

```bash
cp deploy/docker/.env.example deploy/docker/.env
# Edit .env with your preferred settings
```

### 3. Start all services

```bash
cd deploy/docker
docker compose up -d
```

### 4. Access the platform

| Service | URL |
|---|---|
| Developer Portal | http://localhost:3000 |
| Admin Portal | http://localhost:3001 |
| Gateway Runtime | http://localhost:8080 |
| RabbitMQ Management | http://localhost:15672 |

---

## Development Setup

### Prerequisites

- **Java 21** (Eclipse Temurin or GraalVM recommended)
- **Maven 3.9+**
- **Node.js 20+** and **npm 10+**
- **PostgreSQL 16**
- **RabbitMQ 3.13+**

### Backend

Build all backend services from the project root:

```bash
./mvnw clean install
```

Run individual services:

```bash
# Identity Service
./mvnw -pl identity-service spring-boot:run

# Management API
./mvnw -pl management-api spring-boot:run

# Gateway Runtime
./mvnw -pl gateway-runtime spring-boot:run

# Analytics Service
./mvnw -pl analytics-service spring-boot:run

# Notification Service
./mvnw -pl notification-service spring-boot:run
```

### Frontend

Install dependencies from the project root (npm workspaces):

```bash
npm install
```

Run the portals in development mode:

```bash
npm run dev:portal   # Developer portal on :3000
npm run dev:admin    # Admin portal on :3001
```

---

## Configuration

All services are configured through environment variables. The table below lists the primary variables used across services.

| Variable | Default | Description |
|---|---|---|
| `GATEWAY_ENV` | `dev` | Environment profile (`dev`, `staging`, `prod`) |
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `gateway` | PostgreSQL database name |
| `DB_USERNAME` | `gateway` | PostgreSQL username |
| `DB_PASSWORD` | `gateway` | PostgreSQL password |
| `RABBITMQ_HOST` | `localhost` | RabbitMQ host |
| `RABBITMQ_PORT` | `5672` | RabbitMQ AMQP port |
| `RABBITMQ_USERNAME` | `guest` | RabbitMQ username |
| `RABBITMQ_PASSWORD` | `guest` | RabbitMQ password |
| `RABBITMQ_VHOST` | `/cem` | RabbitMQ virtual host |
| `JWT_ISSUER_URI` | `http://localhost:8081` | JWT token issuer URI |
| `SMTP_HOST` | `mailhog` | SMTP server host |
| `SMTP_PORT` | `1025` | SMTP server port |
| `IDENTITY_SERVICE_URL` | `http://localhost:8081` | Internal URL for the identity service |
| `NEXT_PUBLIC_API_URL` | `http://localhost:8082` | Management API URL (frontend) |
| `NEXT_PUBLIC_IDENTITY_URL` | `http://localhost:8081` | Identity service URL (frontend) |
| `NEXT_PUBLIC_GATEWAY_URL` | `http://localhost:8080` | Gateway runtime URL (frontend) |

Database schemas (`identity`, `gateway`, `analytics`, `notification`) are created automatically via the init script and managed with **Liquibase** migrations.

---

## API Documentation

Each backend service exposes health and info endpoints via Spring Boot Actuator:

```
GET /actuator/health/liveness
GET /actuator/health/readiness
```

The Management API and Identity Service expose REST endpoints for API lifecycle operations, authentication, subscriptions, and more. Full OpenAPI documentation is available at each service's `/v3/api-docs` endpoint when running in development mode.

---

## Contributing

Contributions are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines on how to submit issues, feature requests, and pull requests.

---

## License

This project is licensed under the **Apache License 2.0**. See the [LICENSE](LICENSE) file for details.
