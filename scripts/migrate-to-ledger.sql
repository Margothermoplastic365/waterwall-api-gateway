-- =============================================================================
-- MIGRATE wallet_transactions TO ledger_entries
-- Run after deploying the ledger schema (050-create-ledger.yaml)
-- =============================================================================

-- Step 1: Migrate existing CREDIT transactions
INSERT INTO gateway.ledger_entries (wallet_id, entry_type, category, amount, currency, reference, description, billing_period, running_balance, created_at)
SELECT
    wt.wallet_id,
    'CREDIT',
    'TOP_UP',
    wt.amount,
    wt.currency,
    wt.reference,
    wt.description,
    to_char(wt.created_at, 'YYYY-MM'),
    wt.balance_after,
    wt.created_at
FROM gateway.wallet_transactions wt
WHERE wt.type = 'CREDIT'
ON CONFLICT DO NOTHING;

-- Step 2: Migrate existing DEBIT transactions
INSERT INTO gateway.ledger_entries (wallet_id, entry_type, category, amount, currency, reference, description, billing_period, running_balance, created_at)
SELECT
    wt.wallet_id,
    'DEBIT',
    'USAGE_CHARGE',
    wt.amount,
    wt.currency,
    wt.reference,
    wt.description,
    to_char(wt.created_at, 'YYYY-MM'),
    wt.balance_after,
    wt.created_at
FROM gateway.wallet_transactions wt
WHERE wt.type = 'DEBIT'
ON CONFLICT DO NOTHING;

-- Step 3: Create Balance B/F for current period based on current wallet balance
INSERT INTO gateway.ledger_entries (wallet_id, entry_type, category, amount, currency, reference, description, billing_period, running_balance, created_at)
SELECT
    w.id,
    'BALANCE_BF',
    'PERIOD_OPEN',
    w.balance,
    w.currency,
    'BF-' || left(w.id::text, 8) || '-' || to_char(now(), 'YYYY-MM'),
    'Balance brought forward (migrated from wallet_transactions)',
    to_char(now(), 'YYYY-MM'),
    w.balance,
    date_trunc('month', now())
FROM gateway.wallets w
WHERE NOT EXISTS (
    SELECT 1 FROM gateway.ledger_entries le
    WHERE le.wallet_id = w.id AND le.entry_type = 'BALANCE_BF' AND le.billing_period = to_char(now(), 'YYYY-MM')
);

-- Step 4: Update wallet current_period
UPDATE gateway.wallets SET current_period = to_char(now(), 'YYYY-MM') WHERE current_period IS NULL;

-- Verify
SELECT 'Ledger entries migrated:' as info, count(*) as total FROM gateway.ledger_entries;
SELECT entry_type, count(*) as cnt FROM gateway.ledger_entries GROUP BY entry_type ORDER BY entry_type;
