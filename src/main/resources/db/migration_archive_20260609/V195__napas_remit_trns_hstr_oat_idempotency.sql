-- ============================================================================
-- V179__napas_remit_trns_hstr_oat_idempotency.sql
--
-- Adds the Own Account Transfer idempotency key to NAPAS_REMIT_TRNS_HSTR.
--
-- Originally authored as V163; renumbered to V179 on 2026-05-18 after a
-- V163 number collision was discovered against the shared dev DB
-- (`V163__overseas_payment_result_detail_view_setting_corrective` already
-- in `flyway_schema_history`, applied 2026-05-18T03:23Z by a parallel
-- branch). The shared DB's max applied version is V178; V179 is the next
-- free slot. Nothing about the SQL changed — only the file's V-number.
--
-- The scraping-result intake endpoint
--   POST /api/v1/banking/own-account-transfer/scraping-result
-- accepts a client-generated idempotency key (UUID v4) and rejects a
-- second POST with the same key for the same tenant — protects against
-- double-submits when the user reloads / retries the composer after a
-- network hiccup during the engine call.
--
-- The unique constraint is partial: only NOT-NULL values are unique,
-- so historical rows written by other transfer flows (legacy
-- NAPAS_REMIT_TRNS_HSTR consumers, internal-transfer dispatch, etc.)
-- coexist with this column being NULL.
--
-- Idempotent: ALTER TABLE ADD COLUMN IF NOT EXISTS + CREATE UNIQUE
-- INDEX IF NOT EXISTS.
-- ============================================================================

ALTER TABLE NAPAS_REMIT_TRNS_HSTR
    ADD COLUMN IF NOT EXISTS OAT_IDEMPOTENCY_KEY VARCHAR(64);

COMMENT ON COLUMN NAPAS_REMIT_TRNS_HSTR.OAT_IDEMPOTENCY_KEY IS
    'Client-supplied idempotency key (UUID) for the Own Account Transfer scraping-result intake. Unique per tenant when not null. Older rows from other transfer flows carry NULL.';

CREATE UNIQUE INDEX IF NOT EXISTS uq_napas_remit_trns_hstr_oat_idemp
    ON NAPAS_REMIT_TRNS_HSTR (BIZ_KEY, OAT_IDEMPOTENCY_KEY)
    WHERE OAT_IDEMPOTENCY_KEY IS NOT NULL;
