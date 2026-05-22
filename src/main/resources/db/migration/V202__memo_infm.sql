-- =====================================================================
-- V202 — MEMO_INFM
-- =====================================================================
-- Polymorphic memo / note table. One row per memo, attached to ANY
-- parent record via {@code (WEB_SVC_ID, REF_NO[, REF_NO2..REF_NO9])}.
--
-- First consumer in the refactor: Card Transactions row icon
-- (legacy {@code card_0001_001.ts:1242-1276}). Future consumers
-- (Banking inquiry pages, Internal Transfer, Project / szon) attach
-- by passing their own {@code WEB_SVC_ID} + parent id(s).
--
-- Schema column-for-column with DEV (verified via postgres_dev MCP
-- 2026-05-22). 18 columns; PK is the legacy 3-tuple
-- {@code (BIZ_KEY, WEB_SVC_ID, SEQ_NO)}.
--
-- COLOR is the per-memo category-color tag (legacy {@code wabooks.enums.ColorCd}:
-- {@code white / blue / green / yellow / orange / red / gray / purple}).
-- VARCHAR(6) is the DEV cap — enough for the longest value
-- ({@code yellow} / {@code orange} / {@code purple}).
--
-- REF_NO2..REF_NO9 stay mapped + nullable so future Memoable parents
-- with composite ids (e.g. Banking transactions keyed by
-- {@code (BANK_CD, ACCT_NO, TRSC_DT, ACCT_SEQ_NO)}) can attach without
-- a follow-up migration. Card Transactions uses only REF_NO
-- ({@code CARD_SEQ_NO}).
-- =====================================================================

CREATE TABLE IF NOT EXISTS MEMO_INFM (
    BIZ_KEY      VARCHAR(20)   NOT NULL,
    WEB_SVC_ID   VARCHAR(50)   NOT NULL,
    SEQ_NO       NUMERIC(10,0) NOT NULL,
    MEMO         VARCHAR(200),
    COLOR        VARCHAR(6),
    REF_NO       VARCHAR(50)   NOT NULL,
    REF_NO2      VARCHAR(50),
    REF_NO3      VARCHAR(50),
    REF_NO4      VARCHAR(50),
    REF_NO5      VARCHAR(50),
    REF_NO6      VARCHAR(50),
    REF_NO7      VARCHAR(50),
    REF_NO8      VARCHAR(50),
    REF_NO9      VARCHAR(50),
    REG_ID       VARCHAR(100)  NOT NULL,
    REG_DTM      VARCHAR(14)   NOT NULL,
    MOD_ID       VARCHAR(100)  NOT NULL,
    MOD_DTM      VARCHAR(14)   NOT NULL,
    CONSTRAINT pk_memo_infm PRIMARY KEY (BIZ_KEY, WEB_SVC_ID, SEQ_NO)
);

-- Helper index — the only access pattern for the side-panel list is
-- "give me every memo for this (tenant, page, parent id)". Default PK
-- index doesn't cover REF_NO so the listing query would otherwise
-- scan-and-filter; this matches DEV's own ix on the same triple.
CREATE INDEX IF NOT EXISTS ix_memo_infm_biz_svc_ref
    ON MEMO_INFM (BIZ_KEY, WEB_SVC_ID, REF_NO);

-- Per-tenant per-page sequence allocator. The refactor pattern is
-- MAX(SEQ_NO)+1 inside a transaction with a one-shot retry on PK
-- collision; no `wb_get_next_val` branch added (legacy uses the same
-- mechanic via `SeqHelper.getNextVal('memo_infm','seq_no',BIZ_KEY,WEB_SVC_ID)`
-- but the refactor avoids extending the legacy function — see
-- workspace CLAUDE.md "DB Function / Legacy Function Policy").
