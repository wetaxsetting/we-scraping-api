-- ============================================================================
-- V118__gdt_cert_info.sql
--
-- GDT (General Department of Taxation) credentials master — the
-- tax-portal sibling of BANK_CERT_INFO (V100). Stores one row per
-- (BIZ_KEY, SEQ_NO) and is queried alongside BANK_CERT_INFO by the
-- unified Login Credentials list endpoint (legacy `comm_0218_001_r001`
-- → refactor `GET /api/v1/login-credentials`).
--
-- Mirrors JEX legacy `GDT_CERT_INFO` column-for-column. Entity refs:
--   - WECMS DEV/WECMS/src/wabooks/entity/GdtCertInfo.java
--   - WECMS DEV/WECMS/src/wabooks/entity/GdtCertInfoPk.java
--
-- Shape decisions match V100__bank_cert_info.sql for consistency:
--   - BIZ_KEY VARCHAR(20) per the workspace-wide convention (V28 audit).
--   - SEQ_NO NUMERIC(10,0) — legacy PK shape.
--   - Audit columns are legacy VARCHAR(14) / VARCHAR(100) strings
--     (yyyyMMddHHmmss), not TIMESTAMPTZ. Entity will NOT extend
--     BaseEntity — same precedent as BANK_CERT_INFO.
--   - CERT_INFO_DV VARCHAR(20) carries the legacy GdtCertInfoDv code.
--   - CERT_INFO_DETL_CON holds the AES-GCM base64 ciphertext of the
--     password (Java-side `BankCertCryptoService` — the same service
--     encrypts both BANK_CERT_INFO and GDT_CERT_INFO payloads so the
--     two tables share one crypto key). Column shape stays VARCHAR(2000)
--     so a future legacy data-migration path stays open.
--
-- PROD Active Table List entry: GDT_CERT_INFO is present in the active
-- scope (see wecms-api/CLAUDE.md → "PROD DB Table Scope for
-- Refactoring"), so this migration is in-scope per the refactoring
-- enforcement rule.
--
-- DEV `wb_get_next_val('gdt_cert_info','seq_no', …)` was NOT ported —
-- the refactor allocates SEQ_NO in Java via a per-tenant MAX+1 lookup
-- inside GdtCredentialService.create, mirroring the existing
-- BankCredentialService.create pattern. No new legacy DB-function
-- branch is added; if a future legacy-interop deadline forces it, the
-- branch lands in its own migration alongside the call-site swap.
-- ============================================================================

CREATE TABLE IF NOT EXISTS GDT_CERT_INFO (
    BIZ_KEY              VARCHAR(20)   NOT NULL,
    SEQ_NO               NUMERIC(10,0) NOT NULL,
    CERT_INFO_DV         VARCHAR(20),
    CERT_INFO_CON        VARCHAR(2000),
    CERT_INFO_DETL_CON   VARCHAR(2000),
    CERT_INFO_DETL_CON2  VARCHAR(2000),
    REG_ID               VARCHAR(100),
    REG_DTM              VARCHAR(14),
    MOD_ID               VARCHAR(100),
    MOD_DTM              VARCHAR(14),
    CONSTRAINT GDT_CERT_INFO_PK PRIMARY KEY (BIZ_KEY, SEQ_NO)
);

-- The Login Credentials list endpoint reads GDT_CERT_INFO with a single
-- tenant filter (one or zero rows per tenant in practice). The PK
-- already covers (BIZ_KEY, SEQ_NO); no extra index is needed.
