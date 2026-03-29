-- ============================================================
-- SEED DATA FOR ALL SERVICES
-- Run with: psql -h localhost -U postgres -d gateway -f seed-all.sql
-- ============================================================

BEGIN;

-- ============================================================
-- 1. IDENTITY SCHEMA: Organizations
-- ============================================================
INSERT INTO identity.organizations (id, name, slug, description, domain, status, billing_contact_email, technical_contact_email, created_at, updated_at)
VALUES
  ('10000000-0000-0000-0000-000000000001', 'Acme Corp', 'acme-corp', 'Global technology company building next-gen payment APIs', 'acme-corp.com', 'ACTIVE', 'billing@acme-corp.com', 'tech@acme-corp.com', now(), now()),
  ('10000000-0000-0000-0000-000000000002', 'Globex Industries', 'globex', 'Leading provider of logistics and supply chain APIs', 'globex.io', 'ACTIVE', 'billing@globex.io', 'devops@globex.io', now(), now()),
  ('10000000-0000-0000-0000-000000000003', 'Initech Solutions', 'initech', 'Enterprise integration and data transformation services', 'initech.dev', 'ACTIVE', 'accounts@initech.dev', 'platform@initech.dev', now(), now())
ON CONFLICT DO NOTHING;

-- ============================================================
-- 2. IDENTITY SCHEMA: Users (password: password123 for all)
-- ============================================================
INSERT INTO identity.users (id, email, password_hash, status, email_verified, password_changed_at, created_at, updated_at)
VALUES
  ('20000000-0000-0000-0000-000000000001', 'alice@acme-corp.com',    '{argon2id}$argon2id$v=19$m=16384,t=2,p=1$Y2hhbmdlbWVzYWx0$kR8RQwSB3J5FhGvHkGPYMg', 'ACTIVE', true,  now(), now(), now()),
  ('20000000-0000-0000-0000-000000000002', 'bob@acme-corp.com',      '{argon2id}$argon2id$v=19$m=16384,t=2,p=1$Y2hhbmdlbWVzYWx0$kR8RQwSB3J5FhGvHkGPYMg', 'ACTIVE', true,  now(), now(), now()),
  ('20000000-0000-0000-0000-000000000003', 'carol@globex.io',        '{argon2id}$argon2id$v=19$m=16384,t=2,p=1$Y2hhbmdlbWVzYWx0$kR8RQwSB3J5FhGvHkGPYMg', 'ACTIVE', true,  now(), now(), now()),
  ('20000000-0000-0000-0000-000000000004', 'dave@globex.io',         '{argon2id}$argon2id$v=19$m=16384,t=2,p=1$Y2hhbmdlbWVzYWx0$kR8RQwSB3J5FhGvHkGPYMg', 'ACTIVE', true,  now(), now(), now()),
  ('20000000-0000-0000-0000-000000000005', 'eve@initech.dev',        '{argon2id}$argon2id$v=19$m=16384,t=2,p=1$Y2hhbmdlbWVzYWx0$kR8RQwSB3J5FhGvHkGPYMg', 'ACTIVE', true,  now(), now(), now()),
  ('20000000-0000-0000-0000-000000000006', 'frank@initech.dev',      '{argon2id}$argon2id$v=19$m=16384,t=2,p=1$Y2hhbmdlbWVzYWx0$kR8RQwSB3J5FhGvHkGPYMg', 'ACTIVE', false, now(), now(), now()),
  ('20000000-0000-0000-0000-000000000007', 'grace@example.com',      '{argon2id}$argon2id$v=19$m=16384,t=2,p=1$Y2hhbmdlbWVzYWx0$kR8RQwSB3J5FhGvHkGPYMg', 'PENDING_VERIFICATION', false, now(), now(), now()),
  ('20000000-0000-0000-0000-000000000008', 'heidi@example.com',      '{argon2id}$argon2id$v=19$m=16384,t=2,p=1$Y2hhbmdlbWVzYWx0$kR8RQwSB3J5FhGvHkGPYMg', 'ACTIVE', true,  now(), now(), now())
ON CONFLICT (email) DO NOTHING;

-- ============================================================
-- 3. IDENTITY SCHEMA: User Profiles
-- ============================================================
INSERT INTO identity.user_profiles (id, user_id, display_name, phone, timezone, language, job_title, department, bio)
VALUES
  (gen_random_uuid(), '20000000-0000-0000-0000-000000000001', 'Alice Chen',    '+1-555-0101',      'America/New_York',    'en', 'API Publisher Lead',    'Engineering',   'Leads the payments API team at Acme'),
  (gen_random_uuid(), '20000000-0000-0000-0000-000000000002', 'Bob Martinez',  '+1-555-0102',      'America/Chicago',     'en', 'Senior Developer',      'Engineering',   'Full-stack developer focused on API integrations'),
  (gen_random_uuid(), '20000000-0000-0000-0000-000000000003', 'Carol Nguyen',  '+44-20-7946-0958', 'Europe/London',       'en', 'Platform Admin',        'DevOps',        'Manages Globex API platform infrastructure'),
  (gen_random_uuid(), '20000000-0000-0000-0000-000000000004', 'Dave Wilson',   '+1-555-0104',      'America/Los_Angeles', 'en', 'Developer',             'Engineering',   'Backend services developer at Globex'),
  (gen_random_uuid(), '20000000-0000-0000-0000-000000000005', 'Eve Tanaka',    '+81-3-1234-5678',  'Asia/Tokyo',          'ja', 'Integration Architect', 'Architecture',  'Designs enterprise integration patterns at Initech'),
  (gen_random_uuid(), '20000000-0000-0000-0000-000000000006', 'Frank Mueller', '+49-30-12345678',  'Europe/Berlin',       'de', 'Junior Developer',      'Engineering',   'New to the API platform team'),
  (gen_random_uuid(), '20000000-0000-0000-0000-000000000007', 'Grace Lee',     NULL,               'Asia/Seoul',          'ko', 'Freelance Developer',   NULL,            'Exploring the API marketplace'),
  (gen_random_uuid(), '20000000-0000-0000-0000-000000000008', 'Heidi Olsen',   '+47-22-12-34-56',  'Europe/Oslo',         'en', 'QA Engineer',           'Quality',       'API testing and quality assurance')
ON CONFLICT (user_id) DO NOTHING;

-- ============================================================
-- 4. IDENTITY SCHEMA: Org Memberships
-- ============================================================
INSERT INTO identity.org_members (id, user_id, org_id, org_role, joined_at)
VALUES
  (gen_random_uuid(), '20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'OWNER',  now()),
  (gen_random_uuid(), '20000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', 'MEMBER', now()),
  (gen_random_uuid(), '20000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000002', 'OWNER',  now()),
  (gen_random_uuid(), '20000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000002', 'MEMBER', now()),
  (gen_random_uuid(), '20000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000003', 'OWNER',  now()),
  (gen_random_uuid(), '20000000-0000-0000-0000-000000000006', '10000000-0000-0000-0000-000000000003', 'MEMBER', now()),
  (gen_random_uuid(), '20000000-0000-0000-0000-000000000008', '10000000-0000-0000-0000-000000000001', 'MEMBER', now())
ON CONFLICT DO NOTHING;

-- ============================================================
-- 5. IDENTITY SCHEMA: Role Assignments
-- ============================================================
INSERT INTO identity.role_assignments (id, user_id, role_id, scope_type, scope_id, assigned_by, assigned_at)
VALUES
  (gen_random_uuid(), '20000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000005', 'ORG',    '10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000099', now()),
  (gen_random_uuid(), '20000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000006', 'ORG',    '10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000099', now()),
  (gen_random_uuid(), '20000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000002', 'GLOBAL', NULL, '00000000-0000-0000-0000-000000000099', now()),
  (gen_random_uuid(), '20000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000009', 'GLOBAL', NULL, '00000000-0000-0000-0000-000000000099', now()),
  (gen_random_uuid(), '20000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000005', 'ORG',    '10000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000099', now()),
  (gen_random_uuid(), '20000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000009', 'GLOBAL', NULL, '00000000-0000-0000-0000-000000000099', now()),
  (gen_random_uuid(), '20000000-0000-0000-0000-000000000007', '00000000-0000-0000-0000-000000000010', 'GLOBAL', NULL, '00000000-0000-0000-0000-000000000099', now()),
  (gen_random_uuid(), '20000000-0000-0000-0000-000000000008', '00000000-0000-0000-0000-000000000004', 'GLOBAL', NULL, '00000000-0000-0000-0000-000000000099', now())
ON CONFLICT DO NOTHING;

-- ============================================================
-- 6. IDENTITY SCHEMA: Applications
-- ============================================================
INSERT INTO identity.applications (id, name, description, user_id, org_id, callback_urls, status, created_at, updated_at)
VALUES
  ('30000000-0000-0000-0000-000000000001', 'Acme Payment Gateway',      'Production payment processing app',          '20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', '["https://payments.acme-corp.com/callback"]',  'ACTIVE', now(), now()),
  ('30000000-0000-0000-0000-000000000002', 'Acme Mobile App',           'Mobile companion app for Acme services',     '20000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', '["https://m.acme-corp.com/oauth/callback"]',   'ACTIVE', now(), now()),
  ('30000000-0000-0000-0000-000000000003', 'Globex Shipping Tracker',   'Real-time shipment tracking application',    '20000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000002', '["https://track.globex.io/auth"]',             'ACTIVE', now(), now()),
  ('30000000-0000-0000-0000-000000000004', 'Globex Analytics Dashboard', 'Internal analytics and reporting tool',     '20000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000002', '["https://analytics.globex.io/callback"]',     'ACTIVE', now(), now()),
  ('30000000-0000-0000-0000-000000000005', 'Initech ETL Pipeline',      'Data extraction and transformation service', '20000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000003', '["https://etl.initech.dev/hook"]',             'ACTIVE', now(), now()),
  ('30000000-0000-0000-0000-000000000006', 'Dave Test App',             'Personal sandbox application',               '20000000-0000-0000-0000-000000000004', NULL, '["http://localhost:3000/callback"]', 'ACTIVE', now(), now())
ON CONFLICT DO NOTHING;

-- ============================================================
-- 7. IDENTITY SCHEMA: API Keys
-- ============================================================
INSERT INTO identity.api_keys (id, application_id, name, key_prefix, key_hash, status, created_at)
VALUES
  (gen_random_uuid(), '30000000-0000-0000-0000-000000000001', 'Production Key',   'live_acme_', 'a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f60001', 'ACTIVE', now()),
  (gen_random_uuid(), '30000000-0000-0000-0000-000000000002', 'Mobile Key',       'live_acme_', 'a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f60002', 'ACTIVE', now()),
  (gen_random_uuid(), '30000000-0000-0000-0000-000000000003', 'Tracking Service', 'live_glbx_', 'a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f60003', 'ACTIVE', now()),
  (gen_random_uuid(), '30000000-0000-0000-0000-000000000004', 'Analytics Key',    'live_glbx_', 'a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f60004', 'ACTIVE', now()),
  (gen_random_uuid(), '30000000-0000-0000-0000-000000000005', 'ETL Pipeline Key', 'live_init_', 'a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f60005', 'ACTIVE', now()),
  (gen_random_uuid(), '30000000-0000-0000-0000-000000000006', 'Dev Sandbox Key',  'test_dave_', 'a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f60006', 'ACTIVE', now())
ON CONFLICT DO NOTHING;

-- ============================================================
-- 8. GATEWAY SCHEMA: Environments
-- ============================================================
INSERT INTO gateway.environments (id, name, slug, description, config, created_at)
VALUES
  ('40000000-0000-0000-0000-000000000001', 'Development',  'dev',     'Development environment for testing',    '{"baseUrl":"https://dev.gateway.local","debug":true}',    now()),
  ('40000000-0000-0000-0000-000000000002', 'UAT',          'uat',     'User acceptance testing environment',    '{"baseUrl":"https://uat.gateway.local","debug":false}',   now()),
  ('40000000-0000-0000-0000-000000000003', 'Staging',      'staging', 'Pre-production staging environment',     '{"baseUrl":"https://staging.gateway.local","debug":false}', now()),
  ('40000000-0000-0000-0000-000000000004', 'Production',   'prod',    'Live production environment',            '{"baseUrl":"https://api.gateway.local","debug":false}',   now())
ON CONFLICT (slug) DO NOTHING;

-- ============================================================
-- 9. GATEWAY SCHEMA: Plans
-- ============================================================
INSERT INTO gateway.plans (id, name, description, rate_limits, quota, enforcement, status, created_at, updated_at)
VALUES
  ('50000000-0000-0000-0000-000000000001', 'Free',       'Free tier with basic rate limits',                       '{"requestsPerMinute":60,"requestsPerDay":10000}',    '{"monthlyRequests":100000}',   'HARD', 'ACTIVE', now(), now()),
  ('50000000-0000-0000-0000-000000000002', 'Starter',    'Starter plan for small projects and prototyping',        '{"requestsPerMinute":300,"requestsPerDay":100000}',   '{"monthlyRequests":1000000}',  'HARD', 'ACTIVE', now(), now()),
  ('50000000-0000-0000-0000-000000000003', 'Pro',        'Professional plan for production workloads',             '{"requestsPerMinute":1000,"requestsPerDay":500000}',  '{"monthlyRequests":10000000}', 'SOFT', 'ACTIVE', now(), now()),
  ('50000000-0000-0000-0000-000000000004', 'Enterprise', 'Unlimited plan with SLA guarantees and priority support', '{"requestsPerMinute":5000,"requestsPerDay":null}',   '{"monthlyRequests":null}',     'NONE', 'ACTIVE', now(), now())
ON CONFLICT (name) DO NOTHING;

-- ============================================================
-- 10. GATEWAY SCHEMA: APIs
-- ============================================================
INSERT INTO gateway.apis (id, name, version, description, status, visibility, protocol_type, tags, category, org_id, auth_mode, allow_anonymous, created_by, created_at, updated_at)
VALUES
  ('60000000-0000-0000-0000-000000000001', 'Payment Processing API',  'v2.1.0', 'Process credit card payments, refunds, and chargebacks',         'PUBLISHED',   'PUBLIC',     'REST', '["payments","fintech","transactions"]',   'Finance',       '10000000-0000-0000-0000-000000000001', 'REQUIRED', false, '20000000-0000-0000-0000-000000000001', now(), now()),
  ('60000000-0000-0000-0000-000000000002', 'Shipment Tracking API',   'v1.3.0', 'Real-time shipment tracking with webhooks for status updates',   'PUBLISHED',   'PUBLIC',     'REST', '["logistics","tracking","shipping"]',    'Logistics',     '10000000-0000-0000-0000-000000000002', 'REQUIRED', false, '20000000-0000-0000-0000-000000000003', now(), now()),
  ('60000000-0000-0000-0000-000000000003', 'Data Transform API',      'v1.0.0', 'Transform data between formats: CSV, JSON, XML, Parquet',        'PUBLISHED',   'RESTRICTED', 'REST', '["data","etl","transformation"]',        'Data',          '10000000-0000-0000-0000-000000000003', 'REQUIRED', false, '20000000-0000-0000-0000-000000000005', now(), now()),
  ('60000000-0000-0000-0000-000000000004', 'Weather Forecast API',    'v3.0.0', 'Global weather data and forecasts powered by satellite imagery', 'PUBLISHED',   'PUBLIC',     'REST', '["weather","forecast","geospatial"]',    'Weather',       '10000000-0000-0000-0000-000000000001', 'ANY',      true,  '20000000-0000-0000-0000-000000000001', now(), now()),
  ('60000000-0000-0000-0000-000000000005', 'User Analytics API',      'v1.2.0', 'Aggregate user behavior analytics and funnel analysis',          'PUBLISHED',   'PRIVATE',    'REST', '["analytics","users","metrics"]',        'Analytics',     '10000000-0000-0000-0000-000000000002', 'REQUIRED', false, '20000000-0000-0000-0000-000000000003', now(), now()),
  ('60000000-0000-0000-0000-000000000006', 'Notification Hub API',    'v2.0.0', 'Multi-channel notifications: email, SMS, push, and webhooks',    'DRAFT',       'PUBLIC',     'REST', '["notifications","messaging","alerts"]', 'Communication', '10000000-0000-0000-0000-000000000001', 'REQUIRED', false, '20000000-0000-0000-0000-000000000002', now(), now()),
  ('60000000-0000-0000-0000-000000000007', 'Legacy Order API',        'v1.0.0', 'Deprecated order management system - migrate to Payment API v2', 'DEPRECATED',  'PUBLIC',     'REST', '["orders","legacy"]',                    'Finance',       '10000000-0000-0000-0000-000000000001', 'REQUIRED', false, '20000000-0000-0000-0000-000000000001', now(), now())
ON CONFLICT DO NOTHING;

-- ============================================================
-- 11. GATEWAY SCHEMA: Routes
-- ============================================================
INSERT INTO gateway.routes (id, api_id, path, method, upstream_url, auth_types, priority, strip_prefix, enabled, created_at)
VALUES
  -- Payment API
  (gen_random_uuid(), '60000000-0000-0000-0000-000000000001', '/v2/payments',             'POST', 'http://payment-service:8090/payments',              '["API_KEY","OAUTH2"]', 10, true, true, now()),
  (gen_random_uuid(), '60000000-0000-0000-0000-000000000001', '/v2/payments/{id}',        'GET',  'http://payment-service:8090/payments/{id}',         '["API_KEY","OAUTH2"]', 10, true, true, now()),
  (gen_random_uuid(), '60000000-0000-0000-0000-000000000001', '/v2/payments/{id}/refund', 'POST', 'http://payment-service:8090/payments/{id}/refund',  '["OAUTH2"]',           10, true, true, now()),
  (gen_random_uuid(), '60000000-0000-0000-0000-000000000001', '/v2/payments',             'GET',  'http://payment-service:8090/payments',              '["API_KEY","OAUTH2"]', 10, true, true, now()),
  -- Shipment Tracking API
  (gen_random_uuid(), '60000000-0000-0000-0000-000000000002', '/v1/shipments',            'POST', 'http://shipping-service:8091/shipments',            '["API_KEY"]',          10, true, true, now()),
  (gen_random_uuid(), '60000000-0000-0000-0000-000000000002', '/v1/shipments/{id}',       'GET',  'http://shipping-service:8091/shipments/{id}',       '["API_KEY"]',          10, true, true, now()),
  (gen_random_uuid(), '60000000-0000-0000-0000-000000000002', '/v1/shipments/{id}/track', 'GET',  'http://shipping-service:8091/shipments/{id}/track', '["API_KEY"]',          10, true, true, now()),
  (gen_random_uuid(), '60000000-0000-0000-0000-000000000002', '/v1/webhooks',             'POST', 'http://shipping-service:8091/webhooks',             '["API_KEY","OAUTH2"]', 10, true, true, now()),
  -- Data Transform API
  (gen_random_uuid(), '60000000-0000-0000-0000-000000000003', '/v1/transform',            'POST', 'http://transform-service:8092/transform',           '["API_KEY"]',          10, true, true, now()),
  (gen_random_uuid(), '60000000-0000-0000-0000-000000000003', '/v1/transform/batch',      'POST', 'http://transform-service:8092/transform/batch',     '["API_KEY","OAUTH2"]', 10, true, true, now()),
  (gen_random_uuid(), '60000000-0000-0000-0000-000000000003', '/v1/schemas',              'GET',  'http://transform-service:8092/schemas',             '["API_KEY"]',          10, true, true, now()),
  -- Weather API
  (gen_random_uuid(), '60000000-0000-0000-0000-000000000004', '/v3/weather/current',      'GET',  'http://weather-service:8093/current',               '["API_KEY"]',          10, true, true, now()),
  (gen_random_uuid(), '60000000-0000-0000-0000-000000000004', '/v3/weather/forecast',     'GET',  'http://weather-service:8093/forecast',              '["API_KEY"]',          10, true, true, now()),
  (gen_random_uuid(), '60000000-0000-0000-0000-000000000004', '/v3/weather/historical',   'GET',  'http://weather-service:8093/historical',            '["API_KEY"]',          10, true, true, now()),
  -- User Analytics API
  (gen_random_uuid(), '60000000-0000-0000-0000-000000000005', '/v1/analytics/events',     'POST', 'http://analytics-backend:8094/events',              '["API_KEY","OAUTH2"]', 10, true, true, now()),
  (gen_random_uuid(), '60000000-0000-0000-0000-000000000005', '/v1/analytics/funnels',    'GET',  'http://analytics-backend:8094/funnels',             '["OAUTH2"]',           10, true, true, now()),
  (gen_random_uuid(), '60000000-0000-0000-0000-000000000005', '/v1/analytics/reports',    'GET',  'http://analytics-backend:8094/reports',             '["OAUTH2"]',           10, true, true, now())
ON CONFLICT DO NOTHING;

-- ============================================================
-- 12. GATEWAY SCHEMA: Subscriptions
-- ============================================================
INSERT INTO gateway.subscriptions (id, application_id, api_id, plan_id, status, approved_at, created_at)
VALUES
  (gen_random_uuid(), '30000000-0000-0000-0000-000000000001', '60000000-0000-0000-0000-000000000001', '50000000-0000-0000-0000-000000000004', 'ACTIVE',  now(), now()),
  (gen_random_uuid(), '30000000-0000-0000-0000-000000000002', '60000000-0000-0000-0000-000000000004', '50000000-0000-0000-0000-000000000002', 'ACTIVE',  now(), now()),
  (gen_random_uuid(), '30000000-0000-0000-0000-000000000003', '60000000-0000-0000-0000-000000000002', '50000000-0000-0000-0000-000000000003', 'ACTIVE',  now(), now()),
  (gen_random_uuid(), '30000000-0000-0000-0000-000000000004', '60000000-0000-0000-0000-000000000005', '50000000-0000-0000-0000-000000000003', 'ACTIVE',  now(), now()),
  (gen_random_uuid(), '30000000-0000-0000-0000-000000000005', '60000000-0000-0000-0000-000000000003', '50000000-0000-0000-0000-000000000003', 'ACTIVE',  now(), now()),
  (gen_random_uuid(), '30000000-0000-0000-0000-000000000006', '60000000-0000-0000-0000-000000000004', '50000000-0000-0000-0000-000000000001', 'ACTIVE',  now(), now()),
  (gen_random_uuid(), '30000000-0000-0000-0000-000000000006', '60000000-0000-0000-0000-000000000001', '50000000-0000-0000-0000-000000000001', 'PENDING', now(), now()),
  (gen_random_uuid(), '30000000-0000-0000-0000-000000000001', '60000000-0000-0000-0000-000000000002', '50000000-0000-0000-0000-000000000004', 'ACTIVE',  now(), now())
ON CONFLICT (application_id, api_id) DO NOTHING;

-- ============================================================
-- 13. GATEWAY SCHEMA: API Deployments
-- ============================================================
INSERT INTO gateway.api_deployments (id, api_id, environment_slug, status, deployed_by, deployed_at)
VALUES
  (gen_random_uuid(), '60000000-0000-0000-0000-000000000001', 'dev',     'DEPLOYED', '20000000-0000-0000-0000-000000000001', now() - interval '30 days'),
  (gen_random_uuid(), '60000000-0000-0000-0000-000000000001', 'uat',     'DEPLOYED', '20000000-0000-0000-0000-000000000001', now() - interval '20 days'),
  (gen_random_uuid(), '60000000-0000-0000-0000-000000000001', 'staging', 'DEPLOYED', '20000000-0000-0000-0000-000000000001', now() - interval '10 days'),
  (gen_random_uuid(), '60000000-0000-0000-0000-000000000001', 'prod',    'DEPLOYED', '20000000-0000-0000-0000-000000000001', now() - interval '5 days'),
  (gen_random_uuid(), '60000000-0000-0000-0000-000000000002', 'dev',     'DEPLOYED', '20000000-0000-0000-0000-000000000003', now() - interval '15 days'),
  (gen_random_uuid(), '60000000-0000-0000-0000-000000000002', 'prod',    'DEPLOYED', '20000000-0000-0000-0000-000000000003', now() - interval '7 days'),
  (gen_random_uuid(), '60000000-0000-0000-0000-000000000003', 'dev',     'DEPLOYED', '20000000-0000-0000-0000-000000000005', now() - interval '10 days'),
  (gen_random_uuid(), '60000000-0000-0000-0000-000000000004', 'dev',     'DEPLOYED', '20000000-0000-0000-0000-000000000001', now() - interval '25 days'),
  (gen_random_uuid(), '60000000-0000-0000-0000-000000000004', 'prod',    'DEPLOYED', '20000000-0000-0000-0000-000000000001', now() - interval '12 days'),
  (gen_random_uuid(), '60000000-0000-0000-0000-000000000006', 'dev',     'DEPLOYED', '20000000-0000-0000-0000-000000000002', now() - interval '3 days')
ON CONFLICT DO NOTHING;

-- ============================================================
-- 14. GATEWAY SCHEMA: Policy Attachments
-- ============================================================
INSERT INTO gateway.policy_attachments (id, policy_id, api_id, route_id, scope, priority)
SELECT gen_random_uuid(), p.id, '60000000-0000-0000-0000-000000000001'::uuid, NULL, 'API', 1
FROM gateway.policies p WHERE p.name = 'Standard Rate Limit'
ON CONFLICT DO NOTHING;

INSERT INTO gateway.policy_attachments (id, policy_id, api_id, route_id, scope, priority)
SELECT gen_random_uuid(), p.id, '60000000-0000-0000-0000-000000000002'::uuid, NULL, 'API', 1
FROM gateway.policies p WHERE p.name = 'Standard Rate Limit'
ON CONFLICT DO NOTHING;

INSERT INTO gateway.policy_attachments (id, policy_id, api_id, route_id, scope, priority)
SELECT gen_random_uuid(), p.id, '60000000-0000-0000-0000-000000000003'::uuid, NULL, 'API', 1
FROM gateway.policies p WHERE p.name = 'Strict Rate Limit'
ON CONFLICT DO NOTHING;

INSERT INTO gateway.policy_attachments (id, policy_id, api_id, route_id, scope, priority)
SELECT gen_random_uuid(), p.id, '60000000-0000-0000-0000-000000000004'::uuid, NULL, 'API', 1
FROM gateway.policies p WHERE p.name = 'Public CORS'
ON CONFLICT DO NOTHING;

INSERT INTO gateway.policy_attachments (id, policy_id, api_id, route_id, scope, priority)
SELECT gen_random_uuid(), p.id, '60000000-0000-0000-0000-000000000004'::uuid, NULL, 'API', 2
FROM gateway.policies p WHERE p.name = 'Response Cache 5m'
ON CONFLICT DO NOTHING;

-- ============================================================
-- 15. GATEWAY SCHEMA: Gateway Nodes
-- ============================================================
INSERT INTO gateway.gateway_nodes (id, hostname, ip_address, port, config_version, status, last_heartbeat, registered_at)
VALUES
  (gen_random_uuid(), 'gw-node-01.gateway.local', '10.0.1.10', 8080, 42, 'UP',       now() - interval '30 seconds', now() - interval '90 days'),
  (gen_random_uuid(), 'gw-node-02.gateway.local', '10.0.1.11', 8080, 42, 'UP',       now() - interval '15 seconds', now() - interval '90 days'),
  (gen_random_uuid(), 'gw-node-03.gateway.local', '10.0.1.12', 8080, 41, 'DEGRADED', now() - interval '5 minutes',  now() - interval '60 days')
ON CONFLICT (hostname) DO NOTHING;

-- ============================================================
-- 16. GATEWAY SCHEMA: Regions
-- ============================================================
INSERT INTO gateway.regions (id, name, slug, endpoint_url, data_residency_zone, status, created_at)
VALUES
  (gen_random_uuid(), 'US East',      'us-east-1',  'https://us-east-1.api.gateway.local',  'US',   'OPERATIONAL', now()),
  (gen_random_uuid(), 'EU West',      'eu-west-1',  'https://eu-west-1.api.gateway.local',  'EU',   'OPERATIONAL', now()),
  (gen_random_uuid(), 'Asia Pacific', 'ap-south-1', 'https://ap-south-1.api.gateway.local', 'APAC', 'OPERATIONAL', now())
ON CONFLICT (slug) DO NOTHING;

-- ============================================================
-- 17. ANALYTICS SCHEMA: Alert Rules
-- ============================================================
INSERT INTO analytics.alert_rules (id, name, metric, condition, threshold, window_minutes, api_id, enabled, channels, created_at)
VALUES
  (gen_random_uuid(), 'High Error Rate - Payment API',  'error_rate',       'GT', 5.0,  5,  '60000000-0000-0000-0000-000000000001', true, '["email","slack"]',            now()),
  (gen_random_uuid(), 'High Latency - Payment API',     'latency_p99',      'GT', 2000, 5,  '60000000-0000-0000-0000-000000000001', true, '["email","slack"]',            now()),
  (gen_random_uuid(), 'High Error Rate - Shipping API', 'error_rate',       'GT', 10.0, 5,  '60000000-0000-0000-0000-000000000002', true, '["email"]',                    now()),
  (gen_random_uuid(), 'Traffic Spike - Weather API',    'requests_per_min', 'GT', 5000, 1,  '60000000-0000-0000-0000-000000000004', true, '["slack"]',                    now()),
  (gen_random_uuid(), 'Global Error Rate',              'error_rate',       'GT', 3.0,  10, NULL,                                   true, '["email","slack","webhook"]',  now()),
  (gen_random_uuid(), 'Global High Latency',            'latency_p99',      'GT', 5000, 5,  NULL,                                   true, '["email","slack"]',            now())
ON CONFLICT DO NOTHING;

-- ============================================================
-- 18. ANALYTICS SCHEMA: Daily Metrics (14 days)
-- ============================================================
INSERT INTO analytics.metrics_1d (api_id, window_start, request_count, error_count, latency_sum_ms, latency_max_ms)
SELECT
  api_id,
  d::date AS window_start,
  base_req + (random() * variance)::int AS request_count,
  ((base_req + (random() * variance)::int) * err_rate)::int AS error_count,
  ((base_req + (random() * variance)::int) * avg_lat)::bigint AS latency_sum_ms,
  (avg_lat * (2 + random()))::int AS latency_max_ms
FROM (
  VALUES
    ('60000000-0000-0000-0000-000000000001'::uuid, 45000, 15000, 0.012, 85),
    ('60000000-0000-0000-0000-000000000002'::uuid, 22000, 8000,  0.008, 120),
    ('60000000-0000-0000-0000-000000000003'::uuid, 8000,  3000,  0.015, 250),
    ('60000000-0000-0000-0000-000000000004'::uuid, 95000, 30000, 0.003, 45),
    ('60000000-0000-0000-0000-000000000005'::uuid, 12000, 5000,  0.005, 150)
) AS params(api_id, base_req, variance, err_rate, avg_lat)
CROSS JOIN generate_series(current_date - 13, current_date, '1 day') AS d
ON CONFLICT (api_id, window_start) DO NOTHING;

-- ============================================================
-- 19. ANALYTICS SCHEMA: Hourly Metrics (24h for Payment API)
-- ============================================================
INSERT INTO analytics.metrics_1h (api_id, window_start, request_count, error_count, latency_sum_ms, latency_max_ms)
SELECT
  '60000000-0000-0000-0000-000000000001'::uuid,
  h,
  1500 + (random() * 1000)::int,
  (1500 * 0.012 + (random() * 10))::int,
  ((1500 + (random() * 1000)::int) * 85)::bigint,
  (85 * (2 + random()))::int
FROM generate_series(now() - interval '23 hours', now(), '1 hour') AS h
ON CONFLICT (api_id, window_start) DO NOTHING;

-- ============================================================
-- 20. ANALYTICS SCHEMA: Sample Request Logs
-- ============================================================
INSERT INTO analytics.request_logs (trace_id, api_id, consumer_id, application_id, method, path, status_code, latency_ms, request_size, response_size, auth_type, client_ip, user_agent, gateway_node, created_at)
SELECT
  'trace-' || lpad(g::text, 6, '0'),
  api_id,
  consumer_id,
  app_id,
  method,
  path,
  CASE WHEN random() < 0.90 THEN 200 WHEN random() < 0.5 THEN 400 ELSE 500 END,
  (30 + random() * 300)::int,
  (100 + random() * 5000)::bigint,
  (200 + random() * 50000)::bigint,
  auth_type,
  '10.0.' || (random() * 255)::int || '.' || (random() * 255)::int,
  'GatewaySDK/2.1 (' || platform || ')',
  'gw-node-0' || (1 + (random() * 2)::int) || '.gateway.local',
  now() - (random() * interval '24 hours')
FROM generate_series(1, 100) AS g
CROSS JOIN LATERAL (
  SELECT * FROM (VALUES
    ('60000000-0000-0000-0000-000000000001'::uuid, '20000000-0000-0000-0000-000000000001'::uuid, '30000000-0000-0000-0000-000000000001'::uuid, 'POST', '/v2/payments',        'API_KEY', 'Java/17'),
    ('60000000-0000-0000-0000-000000000001'::uuid, '20000000-0000-0000-0000-000000000001'::uuid, '30000000-0000-0000-0000-000000000001'::uuid, 'GET',  '/v2/payments/123',    'OAUTH2',  'Java/17'),
    ('60000000-0000-0000-0000-000000000002'::uuid, '20000000-0000-0000-0000-000000000003'::uuid, '30000000-0000-0000-0000-000000000003'::uuid, 'GET',  '/v1/shipments/track', 'API_KEY', 'Python/3.11'),
    ('60000000-0000-0000-0000-000000000004'::uuid, '20000000-0000-0000-0000-000000000004'::uuid, '30000000-0000-0000-0000-000000000006'::uuid, 'GET',  '/v3/weather/current', 'API_KEY', 'curl/8.1'),
    ('60000000-0000-0000-0000-000000000005'::uuid, '20000000-0000-0000-0000-000000000003'::uuid, '30000000-0000-0000-0000-000000000004'::uuid, 'POST', '/v1/analytics/events','OAUTH2',  'Node/20')
  ) AS t(api_id, consumer_id, app_id, method, path, auth_type, platform)
  ORDER BY random() LIMIT 1
) AS req
ON CONFLICT DO NOTHING;

-- ============================================================
-- 21. NOTIFICATION SCHEMA: Preferences
-- ============================================================
INSERT INTO notification.notification_preferences (id, user_id, email_enabled, in_app_enabled, webhook_enabled, muted_event_types)
VALUES
  (gen_random_uuid(), '20000000-0000-0000-0000-000000000001', true,  true, true,  '[]'),
  (gen_random_uuid(), '20000000-0000-0000-0000-000000000002', true,  true, false, '[]'),
  (gen_random_uuid(), '20000000-0000-0000-0000-000000000003', true,  true, true,  '["API_DEPRECATED"]'),
  (gen_random_uuid(), '20000000-0000-0000-0000-000000000004', false, true, false, '["SUBSCRIPTION_APPROVED","API_DEPRECATED"]'),
  (gen_random_uuid(), '20000000-0000-0000-0000-000000000005', true,  true, true,  '[]'),
  (gen_random_uuid(), '20000000-0000-0000-0000-000000000008', true,  true, false, '[]')
ON CONFLICT (user_id) DO NOTHING;

-- ============================================================
-- 22. NOTIFICATION SCHEMA: Webhook Endpoints
-- ============================================================
INSERT INTO notification.webhook_endpoints (id, user_id, url, secret, active, created_at)
VALUES
  (gen_random_uuid(), '20000000-0000-0000-0000-000000000001', 'https://hooks.acme-corp.com/gateway-events', 'whsec_acme_prod_abc123',    true, now()),
  (gen_random_uuid(), '20000000-0000-0000-0000-000000000003', 'https://hooks.globex.io/api-notifications',  'whsec_globex_prod_def456',  true, now()),
  (gen_random_uuid(), '20000000-0000-0000-0000-000000000005', 'https://webhooks.initech.dev/gateway',       'whsec_initech_prod_ghi789', true, now())
ON CONFLICT DO NOTHING;

-- ============================================================
-- 23. NOTIFICATION SCHEMA: Sample Notifications
-- ============================================================
INSERT INTO notification.notifications (user_id, title, body, type, read, created_at)
VALUES
  ('20000000-0000-0000-0000-000000000001', 'API Published Successfully',         'Payment Processing API v2.1.0 has been published to production.',                     'INFO',    true,  now() - interval '5 days'),
  ('20000000-0000-0000-0000-000000000001', 'New Subscription Request',           'Dave Test App has requested access to Payment Processing API on the Free plan.',       'INFO',    false, now() - interval '2 hours'),
  ('20000000-0000-0000-0000-000000000001', 'Rate Limit Alert',                   'Payment Processing API exceeded 80% of rate limit quota in the last hour.',            'WARNING', false, now() - interval '30 minutes'),
  ('20000000-0000-0000-0000-000000000002', 'Notification Hub API Draft Created', 'Your API draft has been saved. Complete the setup to publish it.',                     'INFO',    true,  now() - interval '3 days'),
  ('20000000-0000-0000-0000-000000000002', 'Deployment Successful',             'Notification Hub API deployed to dev environment.',                                    'INFO',    true,  now() - interval '3 days'),
  ('20000000-0000-0000-0000-000000000003', 'Gateway Node Degraded',             'gw-node-03.gateway.local is reporting degraded status. Last heartbeat 5 minutes ago.', 'WARNING', false, now() - interval '5 minutes'),
  ('20000000-0000-0000-0000-000000000003', 'Shipment Tracking API Published',   'Shipment Tracking API v1.3.0 deployed to production successfully.',                    'INFO',    true,  now() - interval '7 days'),
  ('20000000-0000-0000-0000-000000000003', 'New Subscription Approved',         'Acme Payment Gateway subscribed to Shipment Tracking API (Enterprise plan).',          'INFO',    true,  now() - interval '6 days'),
  ('20000000-0000-0000-0000-000000000004', 'Subscription Approved',             'Your subscription to Weather Forecast API (Free plan) has been approved.',             'INFO',    true,  now() - interval '10 days'),
  ('20000000-0000-0000-0000-000000000004', 'Subscription Pending',              'Your subscription to Payment Processing API is pending approval.',                     'INFO',    false, now() - interval '2 hours'),
  ('20000000-0000-0000-0000-000000000004', 'API Key Rotated',                   'Your Dev Sandbox Key has been rotated. Update your application configuration.',        'ALERT',   false, now() - interval '1 hour'),
  ('20000000-0000-0000-0000-000000000005', 'Data Transform API Published',      'Data Transform API v1.0.0 is now live in the developer portal.',                       'INFO',    true,  now() - interval '10 days'),
  ('20000000-0000-0000-0000-000000000005', 'High Error Rate Detected',          'Data Transform API error rate reached 3.2% in the last 5 minutes.',                    'ERROR',   false, now() - interval '45 minutes'),
  ('20000000-0000-0000-0000-000000000008', 'Weekly Audit Summary',              'Platform audit summary: 1,247 API calls, 3 policy changes, 2 new subscriptions.',     'INFO',    false, now() - interval '1 day'),
  ('20000000-0000-0000-0000-000000000008', 'Compliance Check Passed',           'Monthly SOC2 compliance scan completed with no findings.',                             'INFO',    true,  now() - interval '3 days')
ON CONFLICT DO NOTHING;

COMMIT;
