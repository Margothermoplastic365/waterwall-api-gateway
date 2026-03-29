# API Gateway CLI (`gw`)

Command-line interface for interacting with the API Gateway platform.
The CLI wraps management-api REST calls for developer convenience.

## Installation

```bash
# Download binary (future)
curl -sSL https://gateway.example.com/cli/install.sh | bash

# Or via npm
npm install -g @apigateway/cli

# Or build from source
cd cli/
go build -o gw ./cmd/gw
```

## Configuration

```bash
# First-time setup
gw login --url https://gateway-mgmt.example.com --token <your-token>

# Or configure via environment variables
export GW_API_URL=https://gateway-mgmt.example.com
export GW_API_KEY=your-api-key
```

Configuration is stored in `~/.gw/config.yaml`.

## Commands

### `gw catalog` -- Browse API Catalog

```bash
# List all published APIs
gw catalog list

# Search APIs
gw catalog search "user"

# Get API details
gw catalog info <api-id>

# Get API spec (OpenAPI)
gw catalog spec <api-id> --output spec.yaml
```

**API calls:** `GET /v1/ide/catalog`, `GET /v1/ide/spec/{apiId}`

### `gw subscribe` -- Manage Subscriptions

```bash
# List available plans for an API
gw subscribe plans <api-id>

# Subscribe to an API plan
gw subscribe create --api <api-id> --plan <plan-id>

# List my subscriptions
gw subscribe list

# Cancel a subscription
gw subscribe cancel <subscription-id>
```

**API calls:** `GET /v1/plans?apiId=`, `POST /v1/subscriptions`, `GET /v1/subscriptions`, `DELETE /v1/subscriptions/{id}`

### `gw keys` -- API Key Management

```bash
# List my API keys
gw keys list

# Create a new API key
gw keys create --name "my-app-key" --subscription <subscription-id>

# Revoke an API key
gw keys revoke <key-id>

# Rotate an API key
gw keys rotate <key-id>
```

**API calls:** Maps to identity-service key management endpoints.

### `gw sdk` -- SDK Generation

```bash
# List supported languages
gw sdk languages

# Generate SDK for an API
gw sdk generate <api-id> --language python --output ./sdk/

# Generate SDK for all subscribed APIs
gw sdk generate-all --language javascript --output ./sdks/

# Supported languages: curl, javascript, python, postman, java
```

**API calls:** `GET /v1/sdks/languages`, `POST /v1/sdks/generate`, `GET /v1/sdks/download/{apiId}`

### `gw test` -- API Testing

```bash
# Quick health check on an API
gw test ping <api-id>

# Run a predefined test suite
gw test run <api-id> --suite smoke

# Test a specific endpoint
gw test endpoint <api-id> --method GET --path /users

# Validate API spec
gw test lint <api-id>

# Lint a local spec file
gw test lint --file ./openapi.yaml
```

**API calls:** `POST /v1/ide/lint`, `GET /v1/ide/health`

### `gw sandbox` -- Developer Sandbox

```bash
# Create a sandbox environment
gw sandbox create

# Get sandbox status
gw sandbox status

# Reset sandbox (clear all data)
gw sandbox reset

# Delete sandbox
gw sandbox delete

# Use sandbox for requests (sets environment)
gw sandbox use
```

**API calls:** `POST /v1/sandbox`, `GET /v1/sandbox`, `POST /v1/sandbox/reset`, `DELETE /v1/sandbox`

### `gw changelog` -- Version Changelogs

```bash
# List changelogs for an API
gw changelog list <api-id>

# Generate changelog between versions
gw changelog generate <api-id> --from v1 --to v2

# View breaking changes
gw changelog breaking <api-id> --version v2

# Get migration guide
gw changelog migration-guide <api-id> --from v1 --to v2
```

**API calls:** `GET /v1/changelogs/{apiId}`, `POST /v1/changelogs/{apiId}/generate`, `GET /v1/changelogs/{apiId}/breaking-changes`, `GET /v1/changelogs/{apiId}/migration-guide`

## Global Flags

| Flag              | Description                          |
|-------------------|--------------------------------------|
| `--url`           | API Gateway management URL           |
| `--token`         | Authentication token                 |
| `--output`, `-o`  | Output format: json, yaml, table     |
| `--verbose`, `-v` | Enable verbose logging               |
| `--quiet`, `-q`   | Suppress non-essential output        |
| `--profile`       | Use named config profile             |

## Exit Codes

| Code | Meaning                    |
|------|----------------------------|
| 0    | Success                    |
| 1    | General error              |
| 2    | Authentication failure     |
| 3    | Resource not found         |
| 4    | Validation error           |
| 5    | Network/connectivity error |

## Examples

```bash
# Full workflow: find API, subscribe, generate SDK, test
gw catalog search "payments"
gw subscribe create --api <api-id> --plan <plan-id>
gw keys create --name "my-payment-key" --subscription <sub-id>
gw sdk generate <api-id> --language python --output ./payment-sdk/
gw test ping <api-id>
gw sandbox create
gw test run <api-id> --suite integration
```
