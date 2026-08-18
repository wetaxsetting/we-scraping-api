-- WeCMS Manual Database Change
-- Date: 2026-03-31
-- Source: migrated from Claude Code (AI)/sql/IMP_RSLT_DTTM_pymn_0010_002.sql
-- Related record: TBD
-- Type: DML
-- Execution: MANUAL
-- Target environments: DEV, UAT and PROD — the script is sectioned per environment
-- Executor: DBA TEAM
-- Rollback: ROLLBACK REQUIRES DBA REVIEW — no rollback documented in the original script
-- Verification: VERIFICATION CHECK REQUIRED — the original script carries no executable pre/post-check query
--
-- DESTRUCTIVE / DATA-RISK CHANGE — DBA REVIEW REQUIRED
--
-- Migrated 2026-08-17 into the governed wecms-api SQL location. The original file
-- remains unchanged in the workspace archive; nothing below this line was edited.
-- =============================================================================

-- ============================================================
-- IMP: Display "Result Date/Time" column on Payment Result screens
-- Screens: pymn_0010_002, pymn_0002_002, pymn_0004_002, pymn_0006_002, pymn_0008_002
-- Date: 2026-03-31
-- Updated: 2026-04-07
-- ============================================================


-- ████████████████████████████████████████████████████████████
-- ██  SECTION 1: DEV                                       ██
-- ████████████████████████████████████████████████████████████


-- ============================================================
-- DEV: pymn_0010_002 (Internal Transfer > Payment Result Detail)
-- ============================================================

-- table1: Shift columns from disp_ord >= 7 up by 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'pymn_0010_002'
AND dym_area_id = 'table1'
AND disp_ord >= 7
AND field_id != 'ETC';

-- table1: Insert RSLT_DTTM at disp_ord=7
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont
) VALUES (
    'pymn_0010_002', 'RSLT_DTTM', 'Result Date/Time', 7, 'Y', 'Y', '130px', 'table1', 'Y', NULL
);

-- table2: Shift columns from disp_ord >= 7 up by 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'pymn_0010_002'
AND dym_area_id = 'table2'
AND disp_ord >= 7
AND field_id != 'ETC_2';

-- table2: Insert RSLT_DTTM_2 at disp_ord=7
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont
) VALUES (
    'pymn_0010_002', 'RSLT_DTTM_2', 'Result Date/Time', 7, 'Y', 'Y', '130px', 'table2', 'Y', NULL
);

-- Delete user customized view settings
DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0010_002';


-- ============================================================
-- DEV: pymn_0002_002 (Vendor Payment > Payment Result Detail)
-- ============================================================

-- table1: Shift columns from disp_ord >= 7 up by 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'pymn_0002_002'
AND dym_area_id = 'table1'
AND disp_ord >= 7
AND field_id != 'ETC';

-- table1: Insert RSLT_DTTM at disp_ord=7
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont
) VALUES (
    'pymn_0002_002', 'RSLT_DTTM', 'Result Date/Time', 7, 'Y', 'Y', '130px', 'table1', 'Y', NULL
);

-- table2: Shift columns from disp_ord >= 7 up by 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'pymn_0002_002'
AND dym_area_id = 'table2'
AND disp_ord >= 7
AND field_id != 'ETC_2';

-- table2: Insert RSLT_DTTM_2 at disp_ord=7
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont
) VALUES (
    'pymn_0002_002', 'RSLT_DTTM_2', 'Result Date/Time', 7, 'Y', 'Y', '130px', 'table2', 'Y', NULL
);

-- Delete user customized view settings
DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0002_002';


-- ============================================================
-- DEV: pymn_0004_002 (Expense Payment > Payment Result Detail)
-- ============================================================

-- table1: Shift columns from disp_ord >= 7 up by 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'pymn_0004_002'
AND dym_area_id = 'table1'
AND disp_ord >= 7
AND field_id != 'ETC';

-- table1: Insert RSLT_DTTM at disp_ord=7
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont
) VALUES (
    'pymn_0004_002', 'RSLT_DTTM', 'Result Date/Time', 7, 'Y', 'Y', '130px', 'table1', 'Y', NULL
);

-- table2: Shift columns from disp_ord >= 7 up by 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'pymn_0004_002'
AND dym_area_id = 'table2'
AND disp_ord >= 7
AND field_id != 'ETC_2';

-- table2: Insert RSLT_DTTM_2 at disp_ord=7
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont
) VALUES (
    'pymn_0004_002', 'RSLT_DTTM_2', 'Result Date/Time', 7, 'Y', 'Y', '130px', 'table2', 'Y', NULL
);

-- Delete user customized view settings
DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0004_002';


-- ============================================================
-- DEV: pymn_0006_002 (Payroll Payment > Payment Result Detail)
-- Note: No ETC column on table1, no ETC_2 on table2
-- ============================================================

-- table1: Shift columns from disp_ord >= 7 up by 1 (CHART_ACCT_CD at 9 → 10)
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'pymn_0006_002'
AND dym_area_id = 'table1'
AND disp_ord >= 7;

-- table1: Insert RSLT_DTTM at disp_ord=7
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont
) VALUES (
    'pymn_0006_002', 'RSLT_DTTM', 'Result Date/Time', 7, 'Y', 'Y', '130px', 'table1', 'Y', NULL
);

-- table2: Insert RSLT_DTTM_2 at disp_ord=7 (nothing to shift, RS_CD_2 is last at 6)
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont
) VALUES (
    'pymn_0006_002', 'RSLT_DTTM_2', 'Result Date/Time', 7, 'Y', 'Y', '130px', 'table2', 'Y', NULL
);

-- Delete user customized view settings
DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0006_002';


-- ============================================================
-- DEV: pymn_0008_002 (Overseas Payment > Payment Result Detail)
-- Note: Status column is PROC_STAT (not RS_CD). File route only.
-- ============================================================

-- table1: Shift columns from disp_ord >= 12 up by 1 (BANK_TRAN_ID 12→13)
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'pymn_0008_002'
AND dym_area_id = 'table1'
AND disp_ord >= 12
AND field_id != 'ETC';

-- table1: Insert RSLT_DTTM at disp_ord=12 (after PROC_STAT at 11)
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont
) VALUES (
    'pymn_0008_002', 'RSLT_DTTM', 'Result Date/Time', 12, 'Y', 'Y', '130px', 'table1', 'Y', NULL
);

-- table2: Shift columns from disp_ord >= 11 up by 1 (BANK_TRAN_ID_2 11→12)
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'pymn_0008_002'
AND dym_area_id = 'table2'
AND disp_ord >= 11
AND field_id != 'ETC_2';

-- table2: Insert RSLT_DTTM_2 at disp_ord=11 (after PROC_STAT_2 at 10)
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont
) VALUES (
    'pymn_0008_002', 'RSLT_DTTM_2', 'Result Date/Time', 11, 'Y', 'Y', '130px', 'table2', 'Y', NULL
);

-- Delete user customized view settings
DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0008_002';


-- ████████████████████████████████████████████████████████████
-- ██  SECTION 2: UAT                                       ██
-- ████████████████████████████████████████████████████████████


-- ============================================================
-- UAT: pymn_0010_002 (Internal Transfer > Payment Result Detail)
-- table1: RS_CD=6, CHART_ACCT_CD=7, NOTE=8, DOC_NO=9, BANK_TRAN_ID=10, ETC=99
-- table2: RS_CD_2=6, NOTE_2=7, DOC_NO_2=8, BANK_TRAN_ID_2=9, ETC_2=99
-- ============================================================

-- table1: Shift columns from disp_ord >= 7 up by 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'pymn_0010_002'
AND dym_area_id = 'table1'
AND disp_ord >= 7
AND field_id != 'ETC';

-- table1: Insert RSLT_DTTM at disp_ord=7
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont
) VALUES (
    'pymn_0010_002', 'RSLT_DTTM', 'Result Date/Time', 7, 'Y', 'Y', '130px', 'table1', 'Y', NULL
);

-- table2: Shift columns from disp_ord >= 7 up by 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'pymn_0010_002'
AND dym_area_id = 'table2'
AND disp_ord >= 7
AND field_id != 'ETC_2';

-- table2: Insert RSLT_DTTM_2 at disp_ord=7
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont
) VALUES (
    'pymn_0010_002', 'RSLT_DTTM_2', 'Result Date/Time', 7, 'Y', 'Y', '130px', 'table2', 'Y', NULL
);

-- Delete user customized view settings
DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0010_002';


-- ============================================================
-- UAT: pymn_0002_002 (Vendor Payment > Payment Result Detail)
-- table1: RS_CD=6, CHART_ACCT_CD=7, NOTE=8, DOC_NO=9, BANK_TRAN_ID=10, ETC=99
-- table2: RS_CD_2=6, NOTE_2=7, DOC_NO_2=8, BANK_TRAN_ID_2=9, ETC_2=99
-- ============================================================

-- table1: Shift columns from disp_ord >= 7 up by 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'pymn_0002_002'
AND dym_area_id = 'table1'
AND disp_ord >= 7
AND field_id != 'ETC';

-- table1: Insert RSLT_DTTM at disp_ord=7
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont
) VALUES (
    'pymn_0002_002', 'RSLT_DTTM', 'Result Date/Time', 7, 'Y', 'Y', '130px', 'table1', 'Y', NULL
);

-- table2: Shift columns from disp_ord >= 7 up by 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'pymn_0002_002'
AND dym_area_id = 'table2'
AND disp_ord >= 7
AND field_id != 'ETC_2';

-- table2: Insert RSLT_DTTM_2 at disp_ord=7
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont
) VALUES (
    'pymn_0002_002', 'RSLT_DTTM_2', 'Result Date/Time', 7, 'Y', 'Y', '130px', 'table2', 'Y', NULL
);

-- Delete user customized view settings
DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0002_002';


-- ============================================================
-- UAT: pymn_0004_002 (Expense Payment > Payment Result Detail)
-- table1: RS_CD=6, CHART_ACCT_CD=7, NOTE=8, DOC_NO=9, BANK_TRAN_ID=10, ETC=99
-- table2: RS_CD_2=6, NOTE_2=7, DOC_NO_2=8, BANK_TRAN_ID_2=9, ETC_2=99
-- ============================================================

-- table1: Shift columns from disp_ord >= 7 up by 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'pymn_0004_002'
AND dym_area_id = 'table1'
AND disp_ord >= 7
AND field_id != 'ETC';

-- table1: Insert RSLT_DTTM at disp_ord=7
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont
) VALUES (
    'pymn_0004_002', 'RSLT_DTTM', 'Result Date/Time', 7, 'Y', 'Y', '130px', 'table1', 'Y', NULL
);

-- table2: Shift columns from disp_ord >= 7 up by 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'pymn_0004_002'
AND dym_area_id = 'table2'
AND disp_ord >= 7
AND field_id != 'ETC_2';

-- table2: Insert RSLT_DTTM_2 at disp_ord=7
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont
) VALUES (
    'pymn_0004_002', 'RSLT_DTTM_2', 'Result Date/Time', 7, 'Y', 'Y', '130px', 'table2', 'Y', NULL
);

-- Delete user customized view settings
DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0004_002';


-- ============================================================
-- UAT: pymn_0006_002 (Payroll Payment > Payment Result Detail)
-- table1: RS_CD=6, CHART_ACCT_CD=9 (no ETC)
-- table2: RS_CD_2=6, ETC_2=99 (nothing between 7-98)
-- ============================================================

-- table1: Shift columns from disp_ord >= 7 up by 1 (CHART_ACCT_CD 9→10)
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'pymn_0006_002'
AND dym_area_id = 'table1'
AND disp_ord >= 7;

-- table1: Insert RSLT_DTTM at disp_ord=7
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont
) VALUES (
    'pymn_0006_002', 'RSLT_DTTM', 'Result Date/Time', 7, 'Y', 'Y', '130px', 'table1', 'Y', NULL
);

-- table2: Insert RSLT_DTTM_2 at disp_ord=7 (nothing to shift, RS_CD_2 at 6 is last before ETC_2 at 99)
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont
) VALUES (
    'pymn_0006_002', 'RSLT_DTTM_2', 'Result Date/Time', 7, 'Y', 'Y', '130px', 'table2', 'Y', NULL
);

-- Delete user customized view settings
DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0006_002';


-- ============================================================
-- UAT: pymn_0008_002 (Overseas Payment > Payment Result Detail)
-- table1: PROC_STAT=11, BANK_TRAN_ID=12, ETC=99
-- table2: PROC_STAT_2=10, BANK_TRAN_ID_2=11, ETC_2=99
-- ============================================================

-- table1: Shift columns from disp_ord >= 12 up by 1 (BANK_TRAN_ID 12→13)
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'pymn_0008_002'
AND dym_area_id = 'table1'
AND disp_ord >= 12
AND field_id != 'ETC';

-- table1: Insert RSLT_DTTM at disp_ord=12 (after PROC_STAT at 11)
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont
) VALUES (
    'pymn_0008_002', 'RSLT_DTTM', 'Result Date/Time', 12, 'Y', 'Y', '130px', 'table1', 'Y', NULL
);

-- table2: Shift columns from disp_ord >= 11 up by 1 (BANK_TRAN_ID_2 11→12)
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'pymn_0008_002'
AND dym_area_id = 'table2'
AND disp_ord >= 11
AND field_id != 'ETC_2';

-- table2: Insert RSLT_DTTM_2 at disp_ord=11 (after PROC_STAT_2 at 10)
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont
) VALUES (
    'pymn_0008_002', 'RSLT_DTTM_2', 'Result Date/Time', 11, 'Y', 'Y', '130px', 'table2', 'Y', NULL
);

-- Delete user customized view settings
DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0008_002';


-- ████████████████████████████████████████████████████████████
-- ██  SECTION 3: PROD (CMC + Haengsung)                    ██
-- ████████████████████████████████████████████████████████████


-- ============================================================
-- PROD: pymn_0010_002 (Internal Transfer > Payment Result Detail)
-- table1: RS_CD=6, CHART_ACCT_CD=7, NOTE=8, DOC_NO=9, BANK_TRAN_ID=10, ETC=99
-- table2: RS_CD_2=6, NOTE_2=7, DOC_NO_2=8, BANK_TRAN_ID_2=9, ETC_2=99
-- ============================================================

-- table1: Shift columns from disp_ord >= 7 up by 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'pymn_0010_002'
AND dym_area_id = 'table1'
AND disp_ord >= 7
AND field_id != 'ETC';

-- table1: Insert RSLT_DTTM at disp_ord=7
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont
) VALUES (
    'pymn_0010_002', 'RSLT_DTTM', 'Result Date/Time', 7, 'Y', 'Y', '130px', 'table1', 'Y', NULL
);

-- table2: Shift columns from disp_ord >= 7 up by 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'pymn_0010_002'
AND dym_area_id = 'table2'
AND disp_ord >= 7
AND field_id != 'ETC_2';

-- table2: Insert RSLT_DTTM_2 at disp_ord=7
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont
) VALUES (
    'pymn_0010_002', 'RSLT_DTTM_2', 'Result Date/Time', 7, 'Y', 'Y', '130px', 'table2', 'Y', NULL
);

-- Delete user customized view settings
DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0010_002';


-- ============================================================
-- PROD: pymn_0002_002 (Vendor Payment > Payment Result Detail)
-- table1: RS_CD=6, CHART_ACCT_CD=7, NOTE=8, DOC_NO=9, BANK_TRAN_ID=10, ETC=99
-- table2: RS_CD_2=6, NOTE_2=7, DOC_NO_2=8, BANK_TRAN_ID_2=9, ETC_2=99
-- ============================================================

-- table1: Shift columns from disp_ord >= 7 up by 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'pymn_0002_002'
AND dym_area_id = 'table1'
AND disp_ord >= 7
AND field_id != 'ETC';

-- table1: Insert RSLT_DTTM at disp_ord=7
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont
) VALUES (
    'pymn_0002_002', 'RSLT_DTTM', 'Result Date/Time', 7, 'Y', 'Y', '130px', 'table1', 'Y', NULL
);

-- table2: Shift columns from disp_ord >= 7 up by 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'pymn_0002_002'
AND dym_area_id = 'table2'
AND disp_ord >= 7
AND field_id != 'ETC_2';

-- table2: Insert RSLT_DTTM_2 at disp_ord=7
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont
) VALUES (
    'pymn_0002_002', 'RSLT_DTTM_2', 'Result Date/Time', 7, 'Y', 'Y', '130px', 'table2', 'Y', NULL
);

-- Delete user customized view settings
DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0002_002';


-- ============================================================
-- PROD: pymn_0004_002 (Expense Payment > Payment Result Detail)
-- table1: RS_CD=6, CHART_ACCT_CD=7, NOTE=8, DOC_NO=9, BANK_TRAN_ID=10, ETC=99
-- table2: RS_CD_2=6, NOTE_2=7, DOC_NO_2=8, BANK_TRAN_ID_2=9, ETC_2=99
-- ============================================================

-- table1: Shift columns from disp_ord >= 7 up by 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'pymn_0004_002'
AND dym_area_id = 'table1'
AND disp_ord >= 7
AND field_id != 'ETC';

-- table1: Insert RSLT_DTTM at disp_ord=7
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont
) VALUES (
    'pymn_0004_002', 'RSLT_DTTM', 'Result Date/Time', 7, 'Y', 'Y', '130px', 'table1', 'Y', NULL
);

-- table2: Shift columns from disp_ord >= 7 up by 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'pymn_0004_002'
AND dym_area_id = 'table2'
AND disp_ord >= 7
AND field_id != 'ETC_2';

-- table2: Insert RSLT_DTTM_2 at disp_ord=7
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont
) VALUES (
    'pymn_0004_002', 'RSLT_DTTM_2', 'Result Date/Time', 7, 'Y', 'Y', '130px', 'table2', 'Y', NULL
);

-- Delete user customized view settings
DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0004_002';


-- ============================================================
-- PROD: pymn_0006_002 (Payroll Payment > Payment Result Detail)
-- table1: RS_CD=6, CHART_ACCT_CD=9 (no ETC)
-- table2: RS_CD_2=6, ETC_2=99
-- ============================================================

-- table1: Shift columns from disp_ord >= 7 up by 1 (CHART_ACCT_CD 9→10)
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'pymn_0006_002'
AND dym_area_id = 'table1'
AND disp_ord >= 7;

-- table1: Insert RSLT_DTTM at disp_ord=7
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont
) VALUES (
    'pymn_0006_002', 'RSLT_DTTM', 'Result Date/Time', 7, 'Y', 'Y', '130px', 'table1', 'Y', NULL
);

-- table2: Insert RSLT_DTTM_2 at disp_ord=7 (nothing to shift, RS_CD_2 at 6 is last before ETC_2 at 99)
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont
) VALUES (
    'pymn_0006_002', 'RSLT_DTTM_2', 'Result Date/Time', 7, 'Y', 'Y', '130px', 'table2', 'Y', NULL
);

-- Delete user customized view settings
DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0006_002';


-- ============================================================
-- PROD: pymn_0008_002 (Overseas Payment > Payment Result Detail)
-- table1: PROC_STAT=11, BANK_TRAN_ID=12, ETC=99
-- table2: PROC_STAT_2=10, BANK_TRAN_ID_2=11, ETC_2=99
-- ============================================================

-- table1: Shift columns from disp_ord >= 12 up by 1 (BANK_TRAN_ID 12→13)
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'pymn_0008_002'
AND dym_area_id = 'table1'
AND disp_ord >= 12
AND field_id != 'ETC';

-- table1: Insert RSLT_DTTM at disp_ord=12 (after PROC_STAT at 11)
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont
) VALUES (
    'pymn_0008_002', 'RSLT_DTTM', 'Result Date/Time', 12, 'Y', 'Y', '130px', 'table1', 'Y', NULL
);

-- table2: Shift columns from disp_ord >= 11 up by 1 (BANK_TRAN_ID_2 11→12)
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'pymn_0008_002'
AND dym_area_id = 'table2'
AND disp_ord >= 11
AND field_id != 'ETC_2';

-- table2: Insert RSLT_DTTM_2 at disp_ord=11 (after PROC_STAT_2 at 10)
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont
) VALUES (
    'pymn_0008_002', 'RSLT_DTTM_2', 'Result Date/Time', 11, 'Y', 'Y', '130px', 'table2', 'Y', NULL
);

-- Delete user customized view settings
DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0008_002';


-- ████████████████████████████████████████████████████████████
-- ██  SECTION 4: Haengsung UAT                             ██
-- ████████████████████████████████████████████████████████████


-- ============================================================
-- Haengsung UAT: pymn_0010_002 (Internal Transfer > Payment Result Detail)
-- table1: RS_CD=6, CHART_ACCT_CD=7, NOTE=8, DOC_NO=9, BANK_TRAN_ID=10, ETC=99
-- table2: RS_CD_2=6, NOTE_2=7, DOC_NO_2=8, BANK_TRAN_ID_2=9, ETC_2=99
-- ============================================================

-- table1: Shift columns from disp_ord >= 7 up by 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'pymn_0010_002'
AND dym_area_id = 'table1'
AND disp_ord >= 7
AND field_id != 'ETC';

-- table1: Insert RSLT_DTTM at disp_ord=7
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont
) VALUES (
    'pymn_0010_002', 'RSLT_DTTM', 'Result Date/Time', 7, 'Y', 'Y', '130px', 'table1', 'Y', NULL
);

-- table2: Shift columns from disp_ord >= 7 up by 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'pymn_0010_002'
AND dym_area_id = 'table2'
AND disp_ord >= 7
AND field_id != 'ETC_2';

-- table2: Insert RSLT_DTTM_2 at disp_ord=7
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont
) VALUES (
    'pymn_0010_002', 'RSLT_DTTM_2', 'Result Date/Time', 7, 'Y', 'Y', '130px', 'table2', 'Y', NULL
);

-- Delete user customized view settings
DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0010_002';


-- ============================================================
-- Haengsung UAT: pymn_0002_002 (Vendor Payment > Payment Result Detail)
-- table1: RS_CD=6, CHART_ACCT_CD=7, NOTE=8, DOC_NO=9, BANK_TRAN_ID=10, ETC=99
-- table2: RS_CD_2=6, NOTE_2=7, DOC_NO_2=8, BANK_TRAN_ID_2=9, ETC_2=99
-- ============================================================

-- table1: Shift columns from disp_ord >= 7 up by 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'pymn_0002_002'
AND dym_area_id = 'table1'
AND disp_ord >= 7
AND field_id != 'ETC';

-- table1: Insert RSLT_DTTM at disp_ord=7
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont
) VALUES (
    'pymn_0002_002', 'RSLT_DTTM', 'Result Date/Time', 7, 'Y', 'Y', '130px', 'table1', 'Y', NULL
);

-- table2: Shift columns from disp_ord >= 7 up by 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'pymn_0002_002'
AND dym_area_id = 'table2'
AND disp_ord >= 7
AND field_id != 'ETC_2';

-- table2: Insert RSLT_DTTM_2 at disp_ord=7
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont
) VALUES (
    'pymn_0002_002', 'RSLT_DTTM_2', 'Result Date/Time', 7, 'Y', 'Y', '130px', 'table2', 'Y', NULL
);

-- Delete user customized view settings
DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0002_002';


-- ============================================================
-- Haengsung UAT: pymn_0004_002 (Expense Payment > Payment Result Detail)
-- table1: RS_CD=6, CHART_ACCT_CD=7, NOTE=8, DOC_NO=9, BANK_TRAN_ID=10, ETC=99
-- table2: RS_CD_2=6, NOTE_2=7, DOC_NO_2=8, BANK_TRAN_ID_2=9, ETC_2=99
-- ============================================================

-- table1: Shift columns from disp_ord >= 7 up by 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'pymn_0004_002'
AND dym_area_id = 'table1'
AND disp_ord >= 7
AND field_id != 'ETC';

-- table1: Insert RSLT_DTTM at disp_ord=7
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont
) VALUES (
    'pymn_0004_002', 'RSLT_DTTM', 'Result Date/Time', 7, 'Y', 'Y', '130px', 'table1', 'Y', NULL
);

-- table2: Shift columns from disp_ord >= 7 up by 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'pymn_0004_002'
AND dym_area_id = 'table2'
AND disp_ord >= 7
AND field_id != 'ETC_2';

-- table2: Insert RSLT_DTTM_2 at disp_ord=7
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont
) VALUES (
    'pymn_0004_002', 'RSLT_DTTM_2', 'Result Date/Time', 7, 'Y', 'Y', '130px', 'table2', 'Y', NULL
);

-- Delete user customized view settings
DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0004_002';


-- ============================================================
-- Haengsung UAT: pymn_0006_002 (Payroll Payment > Payment Result Detail)
-- table1: TRAN_USAG=5, FEE=6(hidden), RS_CD=7, CHART_ACCT_CD=3 (no ETC, no BANK_TRAN_ID)
-- table2: TRAN_USAG_2=5, FEE_2=6(hidden), RS_CD_2=7, ETC_2=99 (no BANK_TRAN_ID_2)
-- ============================================================

-- table1: Insert RSLT_DTTM at disp_ord=8 (after RS_CD at 7, nothing to shift)
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont
) VALUES (
    'pymn_0006_002', 'RSLT_DTTM', 'Result Date/Time', 8, 'Y', 'Y', '130px', 'table1', 'Y', NULL
);

-- table2: Insert RSLT_DTTM_2 at disp_ord=8 (after RS_CD_2 at 7, nothing to shift before ETC_2 at 99)
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont
) VALUES (
    'pymn_0006_002', 'RSLT_DTTM_2', 'Result Date/Time', 8, 'Y', 'Y', '130px', 'table2', 'Y', NULL
);

-- Delete user customized view settings
DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0006_002';


-- pymn_0008_002 — SKIP (RSLT_DTTM already exists at 11 on both tables in Haengsung UAT)


-- ████████████████████████████████████████████████████████████
-- ██  SECTION 5: Haengsung PROD                            ██
-- ████████████████████████████████████████████████████████████


-- ============================================================
-- Haengsung PROD: pymn_0010_002 (Internal Transfer > Payment Result Detail)
-- table1: RS_CD=6, CHART_ACCT_CD=7, NOTE=8, DOC_NO=9, BANK_TRAN_ID=10, ETC=99
-- table2: RS_CD_2=6, NOTE_2=7, DOC_NO_2=8, BANK_TRAN_ID_2=9, ETC_2=99
-- ============================================================

-- table1: Shift columns from disp_ord >= 7 up by 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'pymn_0010_002'
AND dym_area_id = 'table1'
AND disp_ord >= 7
AND field_id != 'ETC';

-- table1: Insert RSLT_DTTM at disp_ord=7
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont
) VALUES (
    'pymn_0010_002', 'RSLT_DTTM', 'Result Date/Time', 7, 'Y', 'Y', '130px', 'table1', 'Y', NULL
);

-- table2: Shift columns from disp_ord >= 7 up by 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'pymn_0010_002'
AND dym_area_id = 'table2'
AND disp_ord >= 7
AND field_id != 'ETC_2';

-- table2: Insert RSLT_DTTM_2 at disp_ord=7
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont
) VALUES (
    'pymn_0010_002', 'RSLT_DTTM_2', 'Result Date/Time', 7, 'Y', 'Y', '130px', 'table2', 'Y', NULL
);

-- Delete user customized view settings
DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0010_002';


-- ============================================================
-- Haengsung PROD: pymn_0002_002 (Vendor Payment > Payment Result Detail)
-- table1: RS_CD=6, CHART_ACCT_CD=7, NOTE=8, DOC_NO=9, BANK_TRAN_ID=10, ETC=99
-- table2: RS_CD_2=6, NOTE_2=7, DOC_NO_2=8, BANK_TRAN_ID_2=9, ETC_2=99
-- ============================================================

-- table1: Shift columns from disp_ord >= 7 up by 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'pymn_0002_002'
AND dym_area_id = 'table1'
AND disp_ord >= 7
AND field_id != 'ETC';

-- table1: Insert RSLT_DTTM at disp_ord=7
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont
) VALUES (
    'pymn_0002_002', 'RSLT_DTTM', 'Result Date/Time', 7, 'Y', 'Y', '130px', 'table1', 'Y', NULL
);

-- table2: Shift columns from disp_ord >= 7 up by 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'pymn_0002_002'
AND dym_area_id = 'table2'
AND disp_ord >= 7
AND field_id != 'ETC_2';

-- table2: Insert RSLT_DTTM_2 at disp_ord=7
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont
) VALUES (
    'pymn_0002_002', 'RSLT_DTTM_2', 'Result Date/Time', 7, 'Y', 'Y', '130px', 'table2', 'Y', NULL
);

-- Delete user customized view settings
DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0002_002';


-- ============================================================
-- Haengsung PROD: pymn_0004_002 (Expense Payment > Payment Result Detail)
-- table1: RS_CD=6, CHART_ACCT_CD=7, NOTE=8, DOC_NO=9, BANK_TRAN_ID=10, ETC=99
-- table2: RS_CD_2=6, NOTE_2=7, DOC_NO_2=8, BANK_TRAN_ID_2=9, ETC_2=99
-- ============================================================

-- table1: Shift columns from disp_ord >= 7 up by 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'pymn_0004_002'
AND dym_area_id = 'table1'
AND disp_ord >= 7
AND field_id != 'ETC';

-- table1: Insert RSLT_DTTM at disp_ord=7
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont
) VALUES (
    'pymn_0004_002', 'RSLT_DTTM', 'Result Date/Time', 7, 'Y', 'Y', '130px', 'table1', 'Y', NULL
);

-- table2: Shift columns from disp_ord >= 7 up by 1
UPDATE sys_scr_option_wecms
SET disp_ord = disp_ord + 1
WHERE web_svc_id = 'pymn_0004_002'
AND dym_area_id = 'table2'
AND disp_ord >= 7
AND field_id != 'ETC_2';

-- table2: Insert RSLT_DTTM_2 at disp_ord=7
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont
) VALUES (
    'pymn_0004_002', 'RSLT_DTTM_2', 'Result Date/Time', 7, 'Y', 'Y', '130px', 'table2', 'Y', NULL
);

-- Delete user customized view settings
DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0004_002';


-- ============================================================
-- Haengsung PROD: pymn_0006_002 (Payroll Payment > Payment Result Detail)
-- table1: TRAN_USAG=5, FEE=6(hidden), RS_CD=7, CHART_ACCT_CD=3 (no ETC, no BANK_TRAN_ID)
-- table2: TRAN_USAG_2=5, FEE_2=6(hidden), RS_CD_2=7, ETC_2=99 (no BANK_TRAN_ID_2)
-- ============================================================

-- table1: Insert RSLT_DTTM at disp_ord=8 (after RS_CD at 7, nothing to shift)
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont
) VALUES (
    'pymn_0006_002', 'RSLT_DTTM', 'Result Date/Time', 8, 'Y', 'Y', '130px', 'table1', 'Y', NULL
);

-- table2: Insert RSLT_DTTM_2 at disp_ord=8 (after RS_CD_2 at 7, nothing to shift before ETC_2 at 99)
INSERT INTO sys_scr_option_wecms (
    web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont
) VALUES (
    'pymn_0006_002', 'RSLT_DTTM_2', 'Result Date/Time', 8, 'Y', 'Y', '130px', 'table2', 'Y', NULL
);

-- Delete user customized view settings
DELETE FROM user_scr_option_wecms WHERE web_svc_id = 'pymn_0006_002';


-- pymn_0008_002 — SKIP (RSLT_DTTM already exists at 11 on both tables in Haengsung PROD)
