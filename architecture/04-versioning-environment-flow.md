# API Versioning, Environment & Maker-Checker Architecture

## Data Model

```
API Family (api_group_id)
 |
 +-- Version v1.0.0 (apis table, version_status: RETIRED)
 +-- Version v2.0.0 (apis table, version_status: DEPRECATED)
 |    +-- deprecated_message: "Migrate to v2.1.0"
 |    +-- successor_version_id: -> v2.1.0
 +-- Version v2.1.0 (apis table, version_status: ACTIVE)
 |    +-- Deployed to DEV  (api_deployments, auth_enforcement: RELAXED)
 |    +-- Deployed to UAT  (api_deployments, auth_enforcement: STANDARD)
 |    +-- Deployed to PROD (api_deployments, auth_enforcement: STRICT)
 |    +-- Subscriptions scoped per environment
 |    +-- API keys scoped per environment
 +-- Version v3.0.0 (apis table, version_status: DRAFT)
      +-- In development, not yet submitted for review
```

## Three Independent Dimensions

```
                  +-----------+
                  |  Version  |  v1.0, v2.0, v2.1, v3.0
                  +-----------+
                       |
          +------------+------------+
          |                         |
    +-----------+            +-----------+
    |  Status   |            |Environment|
    +-----------+            +-----------+
    DRAFT                    DEV
    IN_REVIEW                UAT
    ACTIVE                   STAGING
    DEPRECATED               PROD
    RETIRED
```

## API Lifecycle (status field)

```
DRAFT --> IN_REVIEW --> PUBLISHED --> DEPRECATED --> RETIRED
```

- DRAFT: Publisher configuring routes, policies, auth
- IN_REVIEW: Submitted, awaiting maker-checker approval
- PUBLISHED: Live in catalog, versions can be created
- DEPRECATED: Still works, no new subscriptions
- RETIRED: Turned off, 410 Gone

## Version Lifecycle (version_status field)

```
DRAFT --> IN_REVIEW --> ACTIVE --> DEPRECATED --> RETIRED
```

- Only applies to PUBLISHED APIs
- Max 5 non-RETIRED versions per API family
- Each version has own routes, deployments, subscriptions

## Environment-Scoped Resources

```
+-------------------+------------------+------------------+
|                   |   DEV            |   PROD           |
+-------------------+------------------+------------------+
| Base URL          | dev.api.gw.local | api.gw.local     |
| Upstream          | svc-dev:8090     | svc-prod:8090    |
| API Keys          | dev_acme_xxx     | live_acme_xxx    |
| Subscriptions     | Auto-approved    | Needs approval   |
| Auth Enforcement  | RELAXED          | STRICT           |
| Rate Limits       | 1000 req/min     | 100 req/min      |
+-------------------+------------------+------------------+

Cross-environment key usage = REJECTED
```

## Maker-Checker Approval Flow

```
Sensitivity: LOW      1 level   (API_PUBLISHER_ADMIN)
Sensitivity: MEDIUM   2 levels  (+ COMPLIANCE_OFFICER)
Sensitivity: HIGH     3 levels  (+ PLATFORM_ADMIN)
Sensitivity: CRITICAL 3 levels  + 24h cooldown

Rules:
- Submitter != Approver at every level
- Each level = different person
- Permission-based: version:review_l1, l2, l3
```

### Payment API (HIGH sensitivity) Example

```
Paula (API_PUBLISHER)
  |
  | creates v3.0.0 (DRAFT)
  | edits routes, policies
  | clicks "Submit for Review"
  v
[IN_REVIEW]
  |
  | Level 1: Rick (API_PUBLISHER_ADMIN) -- version:review_l1
  | Reviews technical correctness
  | Approves
  v
  | Level 2: Clara (COMPLIANCE_OFFICER) -- version:review_l2
  | Reviews compliance, data classification
  | Approves
  v
  | Level 3: Admin (PLATFORM_ADMIN) -- version:review_l3
  | Final sign-off
  | Approves
  v
[ACTIVE] -- version live, developers can subscribe
```

## Developer Subscribe Flow

```
Developer browses catalog
  |
  v
Sees "Payment API" with versions: v3.0.0 (recommended), v2.1.0
  |
  | Step 1: Pick version (v3.0.0)
  | Step 2: Pick environment (DEV / PROD)
  | Step 3: Pick plan (Free / Pro / Enterprise)
  | Step 4: Pick application
  v
DEV subscription: auto-approved, key generated (dev_acme_xxx)
PROD subscription: creates approval request, status = PENDING
  |
  | Admin approves
  v
PROD key generated (live_acme_xxx)
```

## Environment Promotion Pipeline

```
DEV ---------> UAT ----------> STAGING -------> PROD
 |              |                |                |
 auto          L1 approval    L1+L2 (if HIGH)  Full chain
               (Rick)         (Rick+Clara)      (Rick+Clara+Admin)
```

## REST API Endpoints

### Version Management
```
POST   /v1/versions                      Create new version (clone from source)
GET    /v1/versions?apiGroupId=xxx       List all versions for API group
POST   /v1/versions/{id}/submit          Submit DRAFT for review
POST   /v1/versions/review/{approvalId}  Approve/reject at current level
POST   /v1/versions/{id}/deprecate       Deprecate ACTIVE version
POST   /v1/versions/{id}/retire          Retire version
GET    /v1/versions/{id}/approval-chain   Get approval levels
```

### Environment-Scoped Subscriptions
```
POST   /v1/subscriptions                 { applicationId, apiId, planId, environmentSlug }
                                         DEV: auto-approved
                                         PROD: creates approval request
```

### Environment-Scoped API Keys
```
POST   /v1/applications/{id}/api-keys    { name, environmentSlug }
                                         Key prefix: dev_, uat_, stg_, live_
```

## Database Schema Changes

### apis table (new columns)
- api_group_id UUID -- links versions of same API
- api_group_name VARCHAR(255)
- sensitivity VARCHAR(10) -- LOW, MEDIUM, HIGH, CRITICAL
- version_status VARCHAR(20) -- DRAFT, IN_REVIEW, ACTIVE, DEPRECATED, RETIRED
- deprecated_message TEXT
- successor_version_id UUID

### subscriptions table (new column)
- environment_slug VARCHAR(50) NOT NULL

### api_keys table (new column)
- environment_slug VARCHAR(50) NOT NULL

### approval_chains table (new)
- id, api_id, level, required_permission, description

### approval_requests table (new columns)
- approval_level, submitted_by, current_level, max_level, cooldown_until

### api_deployments table (new columns)
- upstream_overrides JSONB
- rate_limit_override JSONB
- auth_enforcement VARCHAR(20)

## Roles & Permissions

| Role                 | Key Permissions                                        |
|----------------------|--------------------------------------------------------|
| API_PUBLISHER        | version:create, version:submit                         |
| API_PUBLISHER_ADMIN  | version:review_l1, version:deprecate                   |
| COMPLIANCE_OFFICER   | version:review_l2, api:review_compliance               |
| PLATFORM_ADMIN       | version:review_l3, version:retire                      |
| RELEASE_MANAGER      | version:review_l1, all env deploys/promotes             |
| DEVELOPER            | api:read, subscription:create, application:create      |
