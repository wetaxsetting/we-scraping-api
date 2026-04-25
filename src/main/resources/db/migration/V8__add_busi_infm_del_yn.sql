-- =======================================================================
-- V8__add_busi_infm_del_yn.sql
--
-- Adds the legacy DEL_YN column to BUSI_INFM. V1 omitted it and V7
-- missed it. Required so Hibernate schema-validate passes against the
-- BusiInfm entity and so the column matches wecmsvndevdb for direct copy.
-- =======================================================================

ALTER TABLE BUSI_INFM ADD COLUMN DEL_YN VARCHAR(1);

-- Backfill the one seeded row (VN_VNM0004_000001) to 'N' so existing
-- queries that expect del_yn='N' semantics keep working.
UPDATE BUSI_INFM SET DEL_YN = 'N' WHERE DEL_YN IS NULL;
