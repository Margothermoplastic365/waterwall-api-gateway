-- ============================================================
-- SEED: New Roles, Permissions, and Test Users
-- For: Versioning, Maker-Checker, Environment-Scoped Auth
-- ============================================================

BEGIN;

-- ============================================================
-- 1. NEW ROLES
-- ============================================================
INSERT INTO identity.roles (id, name, description, scope_type, is_system, created_at)
VALUES
  ('00000000-0000-0000-0000-000000000011', 'COMPLIANCE_OFFICER', 'Reviews APIs for compliance and regulatory requirements. Level 2 approval authority.', 'GLOBAL', true, now()),
  ('00000000-0000-0000-0000-000000000012', 'RELEASE_MANAGER',    'Manages version releases and environment promotions. Coordinates deployment pipeline.', 'GLOBAL', true, now())
ON CONFLICT (name) DO NOTHING;

-- ============================================================
-- 2. NEW PERMISSIONS
-- ============================================================

-- Version lifecycle permissions
INSERT INTO identity.permissions (id, resource, action, description)
VALUES
  (gen_random_uuid(), 'version', 'create',     'Create a new version of a published API'),
  (gen_random_uuid(), 'version', 'submit',     'Submit a version for review (maker)'),
  (gen_random_uuid(), 'version', 'review_l1',  'Approve/reject version at Level 1 (technical review)'),
  (gen_random_uuid(), 'version', 'review_l2',  'Approve/reject version at Level 2 (compliance review)'),
  (gen_random_uuid(), 'version', 'review_l3',  'Approve/reject version at Level 3 (platform sign-off)'),
  (gen_random_uuid(), 'version', 'deprecate',  'Mark a version as deprecated'),
  (gen_random_uuid(), 'version', 'retire',     'Retire a version and disable its routes'),

  -- API compliance
  (gen_random_uuid(), 'api', 'review_compliance', 'Review API for compliance and data classification'),

  -- Environment-specific deploy permissions
  (gen_random_uuid(), 'environment', 'staging:deploy', 'Deploy or promote APIs to staging environment'),
  (gen_random_uuid(), 'environment', 'prod:deploy',    'Deploy or promote APIs to production environment')
ON CONFLICT (resource, action) DO NOTHING;

-- ============================================================
-- 3. ASSIGN PERMISSIONS TO ROLES
-- ============================================================

-- SUPER_ADMIN gets all new permissions
INSERT INTO identity.role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000001', p.id
FROM identity.permissions p
WHERE (p.resource = 'version' AND p.action IN ('create','submit','review_l1','review_l2','review_l3','deprecate','retire'))
   OR (p.resource = 'api' AND p.action = 'review_compliance')
   OR (p.resource = 'environment' AND p.action IN ('staging:deploy','prod:deploy'))
ON CONFLICT DO NOTHING;

-- PLATFORM_ADMIN: L3 review, retire, prod deploy
INSERT INTO identity.role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000002', p.id
FROM identity.permissions p
WHERE (p.resource = 'version' AND p.action IN ('review_l3','deprecate','retire'))
   OR (p.resource = 'environment' AND p.action IN ('staging:deploy','prod:deploy'))
ON CONFLICT DO NOTHING;

-- API_PUBLISHER_ADMIN: L1 review, publish, deprecate
INSERT INTO identity.role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000005', p.id
FROM identity.permissions p
WHERE (p.resource = 'version' AND p.action IN ('create','submit','review_l1','deprecate'))
ON CONFLICT DO NOTHING;

-- API_PUBLISHER: create versions, submit for review
INSERT INTO identity.role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000006', p.id
FROM identity.permissions p
WHERE (p.resource = 'version' AND p.action IN ('create','submit'))
ON CONFLICT DO NOTHING;

-- COMPLIANCE_OFFICER: L2 review, compliance review
INSERT INTO identity.role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000011', p.id
FROM identity.permissions p
WHERE (p.resource = 'version' AND p.action = 'review_l2')
   OR (p.resource = 'api' AND p.action = 'review_compliance')
   OR (p.resource = 'audit' AND p.action = 'read')
   OR (p.resource = 'api' AND p.action = 'read')
ON CONFLICT DO NOTHING;

-- RELEASE_MANAGER: version create/submit, L1 review, env deploys
INSERT INTO identity.role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000012', p.id
FROM identity.permissions p
WHERE (p.resource = 'version' AND p.action IN ('create','submit','review_l1','deprecate'))
   OR (p.resource = 'environment' AND p.action IN ('create','dev:deploy','uat:deploy','staging:deploy','prod:deploy','promote','rollback','compare'))
   OR (p.resource = 'api' AND p.action IN ('read','publish'))
ON CONFLICT DO NOTHING;

-- OPERATIONS_ADMIN: env deploys
INSERT INTO identity.role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000003', p.id
FROM identity.permissions p
WHERE (p.resource = 'environment' AND p.action IN ('staging:deploy','prod:deploy'))
ON CONFLICT DO NOTHING;

-- AUDITOR: read compliance
INSERT INTO identity.role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000004', p.id
FROM identity.permissions p
WHERE (p.resource = 'api' AND p.action = 'review_compliance')
ON CONFLICT DO NOTHING;

-- ============================================================
-- 4. TEST USERS (password: password123 for all)
-- ============================================================
-- Argon2 hash of "password123" (same as existing seed)
-- Using the same hash from seed-005

INSERT INTO identity.users (id, email, password_hash, status, email_verified, password_changed_at, created_at, updated_at)
VALUES
  -- Compliance Officer
  ('20000000-0000-0000-0000-000000000010', 'compliance@gateway.local',
   '{argon2id}$argon2id$v=19$m=16384,t=2,p=1$Y2hhbmdlbWVzYWx0$kR8RQwSB3J5FhGvHkGPYMg',
   'ACTIVE', true, now(), now(), now()),

  -- Release Manager
  ('20000000-0000-0000-0000-000000000011', 'release@gateway.local',
   '{argon2id}$argon2id$v=19$m=16384,t=2,p=1$Y2hhbmdlbWVzYWx0$kR8RQwSB3J5FhGvHkGPYMg',
   'ACTIVE', true, now(), now(), now()),

  -- API Publisher (maker — creates and submits)
  ('20000000-0000-0000-0000-000000000012', 'publisher@gateway.local',
   '{argon2id}$argon2id$v=19$m=16384,t=2,p=1$Y2hhbmdlbWVzYWx0$kR8RQwSB3J5FhGvHkGPYMg',
   'ACTIVE', true, now(), now(), now()),

  -- API Publisher Admin (checker — reviews and approves L1)
  ('20000000-0000-0000-0000-000000000013', 'reviewer@gateway.local',
   '{argon2id}$argon2id$v=19$m=16384,t=2,p=1$Y2hhbmdlbWVzYWx0$kR8RQwSB3J5FhGvHkGPYMg',
   'ACTIVE', true, now(), now(), now()),

  -- Developer (consumer)
  ('20000000-0000-0000-0000-000000000014', 'developer@gateway.local',
   '{argon2id}$argon2id$v=19$m=16384,t=2,p=1$Y2hhbmdlbWVzYWx0$kR8RQwSB3J5FhGvHkGPYMg',
   'ACTIVE', true, now(), now(), now())
ON CONFLICT (email) DO NOTHING;

-- User Profiles
INSERT INTO identity.user_profiles (id, user_id, display_name, timezone, language, job_title, department)
VALUES
  (gen_random_uuid(), '20000000-0000-0000-0000-000000000010', 'Clara Compliance',  'America/New_York', 'en', 'Compliance Officer',      'Legal & Compliance'),
  (gen_random_uuid(), '20000000-0000-0000-0000-000000000011', 'Ray Release',       'Europe/London',    'en', 'Release Manager',         'Platform Engineering'),
  (gen_random_uuid(), '20000000-0000-0000-0000-000000000012', 'Paula Publisher',    'America/Chicago',  'en', 'API Publisher',           'API Team'),
  (gen_random_uuid(), '20000000-0000-0000-0000-000000000013', 'Rick Reviewer',      'Asia/Tokyo',       'en', 'Senior API Publisher',   'API Team'),
  (gen_random_uuid(), '20000000-0000-0000-0000-000000000014', 'Dev Developer',      'America/New_York', 'en', 'Software Developer',     'Engineering')
ON CONFLICT (user_id) DO NOTHING;

-- ============================================================
-- 5. ROLE ASSIGNMENTS
-- ============================================================
INSERT INTO identity.role_assignments (id, user_id, role_id, scope_type, scope_id, assigned_by, assigned_at)
VALUES
  -- Clara: COMPLIANCE_OFFICER (global)
  (gen_random_uuid(), '20000000-0000-0000-0000-000000000010', '00000000-0000-0000-0000-000000000011', 'GLOBAL', NULL, '00000000-0000-0000-0000-000000000099', now()),

  -- Ray: RELEASE_MANAGER (global)
  (gen_random_uuid(), '20000000-0000-0000-0000-000000000011', '00000000-0000-0000-0000-000000000012', 'GLOBAL', NULL, '00000000-0000-0000-0000-000000000099', now()),

  -- Paula: API_PUBLISHER (org-scoped to Acme)
  (gen_random_uuid(), '20000000-0000-0000-0000-000000000012', '00000000-0000-0000-0000-000000000006', 'ORG', '10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000099', now()),

  -- Rick: API_PUBLISHER_ADMIN (org-scoped to Acme)
  (gen_random_uuid(), '20000000-0000-0000-0000-000000000013', '00000000-0000-0000-0000-000000000005', 'ORG', '10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000099', now()),

  -- Dev: DEVELOPER (global)
  (gen_random_uuid(), '20000000-0000-0000-0000-000000000014', '00000000-0000-0000-0000-000000000009', 'GLOBAL', NULL, '00000000-0000-0000-0000-000000000099', now())
ON CONFLICT DO NOTHING;

-- Add new users to Acme org
INSERT INTO identity.org_members (id, user_id, org_id, org_role, joined_at)
VALUES
  (gen_random_uuid(), '20000000-0000-0000-0000-000000000010', '10000000-0000-0000-0000-000000000001', 'MEMBER', now()),
  (gen_random_uuid(), '20000000-0000-0000-0000-000000000011', '10000000-0000-0000-0000-000000000001', 'MEMBER', now()),
  (gen_random_uuid(), '20000000-0000-0000-0000-000000000012', '10000000-0000-0000-0000-000000000001', 'MEMBER', now()),
  (gen_random_uuid(), '20000000-0000-0000-0000-000000000013', '10000000-0000-0000-0000-000000000001', 'MEMBER', now()),
  (gen_random_uuid(), '20000000-0000-0000-0000-000000000014', '10000000-0000-0000-0000-000000000001', 'MEMBER', now())
ON CONFLICT DO NOTHING;

COMMIT;
