-- =============================================================================
-- V146: Widen SCDUL_PYMN_SUMR_CHK01 to allow Payroll's legacy
-- SCDUL_PYMN_TYPE_CD = '00'.
--
-- V131's CHECK constraint allowed only ('01','02','03','04','05','06')
-- — covering Vendor/Expense/Internal Transfer plus three reserved slots
-- — but legacy `wabooks.enums.ScdulPymnTypeCd` defines `PAYROLL = "00"`
-- (verbatim, see WECMS/src/wabooks/enums/ScdulPymnTypeCd.java:7), so
-- inserts from the Payroll port land with type code `'00'` and fail the
-- existing CHECK with:
--
--   ERROR: new row for relation "scdul_pymn_sumr" violates check
--   constraint "scdul_pymn_sumr_chk01"
--
-- Drop + recreate the constraint to include `'00'`. The remainder of
-- the allowed set stays unchanged so Vendor/Expense/Internal continue
-- to write through the same predicate, and the '04'..'06' reserve
-- stays available for future modules.
-- =============================================================================

ALTER TABLE SCDUL_PYMN_SUMR
    DROP CONSTRAINT IF EXISTS SCDUL_PYMN_SUMR_CHK01;

ALTER TABLE SCDUL_PYMN_SUMR
    ADD  CONSTRAINT SCDUL_PYMN_SUMR_CHK01
    CHECK (SCDUL_PYMN_TYPE_CD IN ('00','01','02','03','04','05','06'));
