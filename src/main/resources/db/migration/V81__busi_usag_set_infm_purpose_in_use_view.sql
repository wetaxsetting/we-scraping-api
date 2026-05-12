-- ============================================================================
-- V81__busi_usag_set_infm_purpose_in_use_view.sql
--
-- Settings > Purpose: `v_purpose_in_use` projection. Backend LEFT JOINs
-- this view to derive the `used_in_transactions` wire field on every
-- list response, AND consults it inside the delete pre-check (OQ-5).
--
-- Phase-2 branch coverage:
--   (a) An active level-1 purpose has at least one active child
--       referencing it via parent_use_usag_cd. Covers the legacy
--       `hasActiveChildren` semantics plus the "parent is in use
--       because its child is in use" branch of OQ-5.
--   (b) An active payment / transfer / expense row references the
--       purpose. NOT INCLUDED in V81 — branches land per consumer
--       table as the consumer entities port into the refactor.
--       Candidates (per feature spec OQ-5): PYMN_*, TRNS_HSTR_INFM,
--       SCDUL_TRNF_*, SCDUL_PYMN_*, OVRS_PYMN_INFM, EXPS_INFM.
--       Adding a new branch: `CREATE OR REPLACE VIEW` with another
--       `UNION ALL` arm — additive, no schema change.
--
-- The service queries the view as `(biz_key, use_usag_cd) IN (...)` —
-- works the same regardless of how many branches contribute. Today's
-- branch (a) is enough to enforce the legacy "delete blocked by
-- children" guarantee; branch (b) tightens it to the full OQ-5 scope
-- when consumer tables arrive.
-- ============================================================================

CREATE OR REPLACE VIEW v_purpose_in_use AS
SELECT DISTINCT biz_key, use_usag_cd
FROM (
  -- (a) Parent-child reference inside busi_usag_set_infm itself.
  SELECT biz_key, parent_use_usag_cd AS use_usag_cd
  FROM busi_usag_set_infm
  WHERE parent_use_usag_cd IS NOT NULL AND use_yn = 'Y'

  -- (b) Consumer-table references — branches added per consumer
  --     table when that consumer entity lands in the refactor. Each
  --     branch must:
  --       (i)  filter on biz_key (multi-tenant scope);
  --       (ii) filter on the consumer's "active" predicate (e.g.
  --            status NOT IN ('CANCELLED','DELETED'), del_yn = 'N',
  --            etc. — Phase 2 owner picks the right predicate per
  --            consumer table).
  --     Template:
  --       UNION ALL
  --       SELECT biz_key, use_usag_cd FROM <consumer_table>
  --       WHERE use_usag_cd IS NOT NULL
  --         AND <consumer_table active filter>
) t;
