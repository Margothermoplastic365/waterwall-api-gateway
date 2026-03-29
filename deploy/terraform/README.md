# API Gateway Terraform Provider

This document describes the Terraform resources that map to the API Gateway management-api endpoints.
A dedicated Terraform provider (`terraform-provider-apigateway`) would wrap these REST calls.

## Provider Configuration

```hcl
provider "apigateway" {
  base_url = "https://gateway-mgmt.example.com"
  api_key  = var.gateway_api_key
  # Or use OAuth2 token
  # token = var.gateway_oauth_token
}
```

## Resources

### apigateway_api

Manages API definitions. Maps to `POST/PUT/DELETE /v1/apis`.

```hcl
resource "apigateway_api" "user_service" {
  name         = "User Service API"
  version      = "2.0.0"
  description  = "Manages user accounts and profiles"
  protocol     = "REST"
  visibility   = "PUBLIC"
  category     = "identity"
  tags         = ["users", "identity", "auth"]
}
```

| Argument       | Type         | Required | Description                        |
|----------------|--------------|----------|------------------------------------|
| name           | string       | yes      | API display name                   |
| version        | string       | no       | Semantic version (default: 1.0.0)  |
| description    | string       | no       | Human-readable description         |
| protocol       | string       | no       | REST, GRAPHQL, GRPC, SOAP, etc.    |
| visibility     | string       | no       | PUBLIC or PRIVATE                  |
| category       | string       | no       | Categorization tag                 |
| tags           | list(string) | no       | Searchable tags                    |

### apigateway_route

Manages API routes. Maps to `POST/PUT/DELETE /v1/routes`.

```hcl
resource "apigateway_route" "user_route" {
  api_id       = apigateway_api.user_service.id
  path         = "/users/**"
  upstream_url = "http://user-service:8080"
  methods      = ["GET", "POST", "PUT", "DELETE"]
  strip_prefix = true
}
```

| Argument       | Type         | Required | Description                        |
|----------------|--------------|----------|------------------------------------|
| api_id         | string (UUID)| yes      | Parent API identifier              |
| path           | string       | yes      | Route path pattern                 |
| upstream_url   | string       | yes      | Backend service URL                |
| methods        | list(string) | no       | Allowed HTTP methods               |
| strip_prefix   | bool         | no       | Strip matched prefix before proxy  |

### apigateway_policy

Manages gateway policies. Maps to `POST/PUT/DELETE /v1/policies`.

```hcl
resource "apigateway_policy" "rate_limit" {
  name = "Standard Rate Limit"
  type = "RATE_LIMIT"
  config = jsonencode({
    requests_per_second = 100
    burst_allowance     = 20
  })
}

resource "apigateway_policy_attachment" "user_rate_limit" {
  policy_id = apigateway_policy.rate_limit.id
  api_id    = apigateway_api.user_service.id
}
```

| Argument | Type   | Required | Description                                    |
|----------|--------|----------|------------------------------------------------|
| name     | string | yes      | Policy name                                    |
| type     | string | yes      | RATE_LIMIT, AUTH, TRANSFORM, CORS, IP_FILTER, etc. |
| config   | string | yes      | JSON-encoded policy configuration              |

### apigateway_plan

Manages subscription plans. Maps to `POST/PUT/DELETE /v1/plans`.

```hcl
resource "apigateway_plan" "pro_plan" {
  api_id            = apigateway_api.user_service.id
  name              = "Professional"
  rate_limit_rpm    = 5000
  rate_limit_rpd    = 500000
  requires_approval = false
}
```

| Argument          | Type         | Required | Description                      |
|-------------------|--------------|----------|----------------------------------|
| api_id            | string (UUID)| yes      | Parent API identifier            |
| name              | string       | yes      | Plan display name                |
| rate_limit_rpm    | number       | no       | Requests per minute limit        |
| rate_limit_rpd    | number       | no       | Requests per day limit           |
| requires_approval | bool         | no       | Manual approval required         |

### apigateway_environment

Manages deployment environments. Maps to `POST/PUT/DELETE /v1/environments`.

```hcl
resource "apigateway_environment" "production" {
  name        = "production"
  description = "Production environment"
  base_url    = "https://api.example.com"
}
```

## Data Sources

### apigateway_api (data)

```hcl
data "apigateway_api" "existing" {
  name = "User Service API"
}
```

### apigateway_apis (data)

```hcl
data "apigateway_apis" "all_rest" {
  filter {
    protocol = "REST"
    status   = "PUBLISHED"
  }
}
```

## Import

All resources support `terraform import`:

```bash
terraform import apigateway_api.user_service <api-uuid>
terraform import apigateway_route.user_route <route-uuid>
terraform import apigateway_policy.rate_limit <policy-uuid>
terraform import apigateway_plan.pro_plan <plan-uuid>
```

## API Endpoints Used

| Resource                      | Create          | Read            | Update          | Delete            |
|-------------------------------|-----------------|-----------------|-----------------|-------------------|
| apigateway_api                | POST /v1/apis   | GET /v1/apis/:id| PUT /v1/apis/:id| DELETE /v1/apis/:id|
| apigateway_route              | POST /v1/routes | GET /v1/routes/:id| PUT /v1/routes/:id| DELETE /v1/routes/:id|
| apigateway_policy             | POST /v1/policies| GET /v1/policies/:id| PUT /v1/policies/:id| DELETE /v1/policies/:id|
| apigateway_plan               | POST /v1/plans  | GET /v1/plans/:id| PUT /v1/plans/:id| DELETE /v1/plans/:id|
| apigateway_policy_attachment  | POST /v1/policies/:id/attach| -   | -               | DELETE /v1/policies/:id/detach|
| apigateway_environment        | POST /v1/environments| GET /v1/environments/:id| PUT /v1/environments/:id| DELETE /v1/environments/:id|
