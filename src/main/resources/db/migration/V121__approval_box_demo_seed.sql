-- ============================================================================
-- V121__approval_box_demo_seed.sql
--
-- Payment > Approval Box — demo seed for tenant VN_VNM0004_000001.
--
-- Why: V111 created the EXPT_RESOL_MSTR + EXPT_RESOL_APPRVR_INFM schema but
-- left both tables empty. The upstream Payment modules (Internal Transfer
-- requestApproval, Vendor / Expense / Payroll / Overseas) do NOT yet fan out
-- into EXPT_RESOL_MSTR (see InternalTransferService.requestApproval, Phase A
-- — stamp synthesised APPR_SEQ_NO on SCDUL_PYMN_SUMR only). Result: the
-- Approval Box page renders no rows on a fresh local DB.
--
-- This seed pulls the most-recent 20 rows from DEV's wecmsvndev.expt_resol_mstr
-- for tenant VN_VNM0004_000001 (appr_dv ∈ ('21','31','32','33','34','35'),
-- del_yn = 'N'), plus their 24-row approver chain, and stamps draft_date /
-- reg_dtm / mod_dtm to dates spread across the last 30 days from 2026-05-14
-- (today) so every row falls inside the page's default 30-day window. The
-- relative ordering and appr_type_cd distribution from DEV is preserved so
-- all four status tabs (Pending '00', Approved '01', Rejected '02',
-- In Progress '03') are populated.
--
-- Visibility — the rows where reg_id = 'lamine@gmail.com' are visible to
-- lamine without admin; the rows registered by other users are visible
-- because lamine is also an approver on them (the visibility filter is
-- regId = userId OR EXISTS approver row).
--
-- Idempotent via INSERT … ON CONFLICT DO NOTHING.
-- ============================================================================

INSERT INTO EXPT_RESOL_MSTR (
    BIZ_KEY, APPR_SEQ_NO, APPR_DV, APPR_STS, DRAFT_DATE, DRAFT_TIME,
    DRAFT_USER_ID, DRAFT_USER_NM, DRAFT_USER_SITE_CD, DRAFT_USER_SITE_NM,
    TITL, DOC_NO, DOC_NM, TOT_PAY_VND_AMT, TOT_PAY_USD_AMT, PAY_DATE,
    DEL_YN, HAND_REG_YN, REG_ID, REG_DTM, MOD_ID, MOD_DTM,
    APPR_BOX_USE_YN, APPR_BOX_DISP_CNT, APPR_DAT_DISP_YN, CMPN_LOGO_DISP_YN, REG_DISP_YN,
    CMPN_LOGO, USER_NM, PAY_END_YN, PAY_END_DTM, PAY_END_NM,
    TOT_PAY_KHR_AMT, PAY_YN, APPR_TYPE_CD, REG_CHNL, PC_MOD_YN
) VALUES
    -- Internal Transfer · Rejected · lamine
    ('VN_VNM0004_000001', '1540', '35', '2', '20260513', '075547',
     'lamine@gmail.com', 'lamine', 'VN_VNM0004_000001', 'CÔNG TY TNHH WEBCASH VIỆT NAM',
     'Test', 'VO_260513_002', 'Internal Transfer (WeCMS)', 0, 0, '20260513',
     'N', 'Y', 'lamine@gmail.com', '20260513075547', 'lamine@gmail.com', '20260513075547',
     'Y', '3', 'N', 'Y', 'Y',
     'File_1749540397981_2.png', 'lamine', 'Y', '20260513075556', 'lamine',
     100000.000000, 'N', '02', 'PC', 'Y'),
    -- Internal Transfer · Approved · lamine
    ('VN_VNM0004_000001', '1539', '35', '2', '20260512', '071347',
     'lamine@gmail.com', 'lamine', 'VN_VNM0004_000001', 'CÔNG TY TNHH WEBCASH VIỆT NAM',
     'Testing', 'VO_260512_001', 'Internal Transfer (WeCMS)', 0, 0, '20260512',
     'N', 'Y', 'lamine@gmail.com', '20260512071347', 'lamine@gmail.com', '20260512071347',
     'Y', '3', 'N', 'Y', 'Y',
     'File_1749540397981_2.png', 'lamine', 'Y', '20260512075058', 'lamine',
     100000.000000, 'N', '01', 'PC', 'Y'),
    -- Internal Transfer · Approved · lamine
    ('VN_VNM0004_000001', '1538', '35', '2', '20260510', '064741',
     'lamine@gmail.com', 'lamine', 'VN_VNM0004_000001', 'CÔNG TY TNHH WEBCASH VIỆT NAM',
     'Testing New feature', 'VO_260510_001', 'Internal Transfer (WeCMS)', 0, 0, '20260510',
     'N', 'Y', 'lamine@gmail.com', '20260510064741', 'lamine@gmail.com', '20260510064741',
     'Y', '3', 'N', 'Y', 'Y',
     'File_1749540397981_2.png', 'lamine', 'Y', '20260510064817', 'lamine',
     10000.000000, 'N', '01', 'PC', 'Y'),
    -- Expense Payment · Approved · lamine
    ('VN_VNM0004_000001', '1537', '31', '2', '20260509', '093212',
     'lamine@gmail.com', 'lamine', 'VN_VNM0004_000001', 'CÔNG TY TNHH WEBCASH VIỆT NAM',
     'Testing', 'VO_260509_005', 'Expense Payment (WeCMs)', 0, 0, '20260509',
     'N', 'Y', 'lamine@gmail.com', '20260509093212', 'lamine@gmail.com', '20260509093212',
     'Y', '3', 'N', 'Y', 'Y',
     'File_1749540397981_2.png', 'lamine', 'Y', '20260509093221', 'lamine',
     1000.000000, 'N', '01', 'PC', 'Y'),
    -- Overseas Payment · Approved · lamine
    ('VN_VNM0004_000001', '1536', '34', '2', '20260508', '084948',
     'lamine@gmail.com', 'lamine', 'VN_VNM0004_000001', 'CÔNG TY TNHH WEBCASH VIỆT NAM',
     'Testing', 'VO_260508_004', 'Overseas Payment (WeCMs)', 0, 0, '20260508',
     'N', 'Y', 'lamine@gmail.com', '20260508084948', 'lamine@gmail.com', '20260508084948',
     'Y', '3', 'N', 'Y', 'Y',
     'File_1749540397981_2.png', 'lamine', 'Y', '20260508084958', 'lamine',
     10.000000, 'N', '01', 'PC', 'Y'),
    -- Payroll Payment · Approved · lamine
    ('VN_VNM0004_000001', '1535', '21', '2', '20260507', '084744',
     'lamine@gmail.com', 'lamine', 'VN_VNM0004_000001', 'CÔNG TY TNHH WEBCASH VIỆT NAM',
     'Testing', 'VO_260507_003', 'Payroll Payment (WeCMs)', 0, 0, '20260507',
     'N', 'Y', 'lamine@gmail.com', '20260507084744', 'lamine@gmail.com', '20260507084744',
     'Y', '3', 'N', 'Y', 'Y',
     'File_1749540397981_2.png', 'lamine', 'Y', '20260507084754', 'lamine',
     3000.000000, 'N', '01', 'PC', 'Y'),
    -- Vendor Payment · Approved · lamine
    ('VN_VNM0004_000001', '1534', '32', '2', '20260506', '084224',
     'lamine@gmail.com', 'lamine', 'VN_VNM0004_000001', 'CÔNG TY TNHH WEBCASH VIỆT NAM',
     'Testing', 'VO_260506_002', 'Vendor Payment (WeCMS)', 0, 0, '20260506',
     'N', 'Y', 'lamine@gmail.com', '20260506084224', 'lamine@gmail.com', '20260506084224',
     'Y', '3', 'N', 'Y', 'Y',
     'File_1749540397981_2.png', 'lamine', 'Y', '20260506084313', 'lamine',
     1000.000000, 'N', '01', 'PC', 'Y'),
    -- Internal Transfer · Approved · lamine
    ('VN_VNM0004_000001', '1533', '35', '2', '20260505', '073317',
     'lamine@gmail.com', 'lamine', 'VN_VNM0004_000001', 'CÔNG TY TNHH WEBCASH VIỆT NAM',
     '[IMP] Internal transfer - Adjust transmit with File for Shinhan',
     'VO_260505_001', 'Internal Transfer (WeCMS)', 0, 0, '20260505',
     'N', 'Y', 'lamine@gmail.com', '20260505073317', 'lamine@gmail.com', '20260505073317',
     'Y', '3', 'N', 'Y', 'Y',
     'File_1749540397981_2.png', 'lamine', 'Y', '20260505073329', 'lamine',
     100000.000000, 'N', '01', 'PC', 'Y'),
    -- Internal Transfer · Approved · lamine
    ('VN_VNM0004_000001', '1532', '35', '2', '20260504', '075735',
     'lamine@gmail.com', 'lamine', 'VN_VNM0004_000001', 'CÔNG TY TNHH WEBCASH VIỆT NAM',
     'WOORI USD -> VND (TRNF USD)', 'VO_260504_006', 'Internal Transfer (WeCMS)', 0, 0, '20260504',
     'N', 'Y', 'lamine@gmail.com', '20260504075735', 'lamine@gmail.com', '20260504075735',
     'Y', '3', 'N', 'Y', 'Y',
     'File_1749540397981_2.png', 'lamine', 'Y', '20260504075819', 'lamine',
     10.000000, 'N', '01', 'PC', 'Y'),
    -- Internal Transfer · Approved · lamine
    ('VN_VNM0004_000001', '1531', '35', '2', '20260503', '075542',
     'lamine@gmail.com', 'lamine', 'VN_VNM0004_000001', 'CÔNG TY TNHH WEBCASH VIỆT NAM',
     'WOORI USD -> VND (TRNF VND)', 'VO_260503_005', 'Internal Transfer (WeCMS)', 0, 0, '20260503',
     'N', 'Y', 'lamine@gmail.com', '20260503075542', 'lamine@gmail.com', '20260503075542',
     'Y', '3', 'N', 'Y', 'Y',
     'File_1749540397981_2.png', 'lamine', 'Y', '20260503075606', 'lamine',
     10000.000000, 'N', '01', 'PC', 'Y'),
    -- Internal Transfer · Approved · lamine
    ('VN_VNM0004_000001', '1530', '35', '2', '20260502', '074711',
     'lamine@gmail.com', 'lamine', 'VN_VNM0004_000001', 'CÔNG TY TNHH WEBCASH VIỆT NAM',
     'SHINHAN USD-> VND (TRNF USD)', 'VO_260502_004', 'Internal Transfer (WeCMS)', 0, 0, '20260502',
     'N', 'Y', 'lamine@gmail.com', '20260502074711', 'lamine@gmail.com', '20260502074711',
     'Y', '3', 'N', 'Y', 'Y',
     'File_1749540397981_2.png', 'lamine', 'Y', '20260502074727', 'lamine',
     10.000000, 'N', '01', 'PC', 'Y'),
    -- Internal Transfer · Approved · lamine
    ('VN_VNM0004_000001', '1529', '35', '2', '20260501', '074537',
     'lamine@gmail.com', 'lamine', 'VN_VNM0004_000001', 'CÔNG TY TNHH WEBCASH VIỆT NAM',
     'SHINHAN USD -> VND (TRNF VND)', 'VO_260501_003', 'Internal Transfer (WeCMS)', 0, 0, '20260501',
     'N', 'Y', 'lamine@gmail.com', '20260501074537', 'lamine@gmail.com', '20260501074537',
     'Y', '3', 'N', 'Y', 'Y',
     'File_1749540397981_2.png', 'lamine', 'Y', '20260501074552', 'lamine',
     10000.000000, 'N', '01', 'PC', 'Y'),
    -- Internal Transfer · Approved · lamine
    ('VN_VNM0004_000001', '1528', '35', '2', '20260430', '073445',
     'lamine@gmail.com', 'lamine', 'VN_VNM0004_000001', 'CÔNG TY TNHH WEBCASH VIỆT NAM',
     'SHINHAN VND -> USD (INPUT USD)', 'VO_260430_002', 'Internal Transfer (WeCMS)', 0, 0, '20260430',
     'N', 'Y', 'lamine@gmail.com', '20260430073445', 'lamine@gmail.com', '20260430073445',
     'Y', '3', 'N', 'Y', 'Y',
     'File_1749540397981_2.png', 'lamine', 'Y', '20260430073458', 'lamine',
     10.000000, 'N', '01', 'PC', 'Y'),
    -- Internal Transfer · Approved · lamine
    ('VN_VNM0004_000001', '1527', '35', '2', '20260429', '072658',
     'lamine@gmail.com', 'lamine', 'VN_VNM0004_000001', 'CÔNG TY TNHH WEBCASH VIỆT NAM',
     'SHINHAN VND -> USD (INPUT VND)', 'VO_260429_001', 'Internal Transfer (WeCMS)', 0, 0, '20260429',
     'N', 'Y', 'lamine@gmail.com', '20260429072658', 'lamine@gmail.com', '20260429072658',
     'Y', '3', 'N', 'Y', 'Y',
     'File_1749540397981_2.png', 'lamine', 'Y', '20260429072900', 'lamine',
     10000.000000, 'N', '01', 'PC', 'Y'),
    -- Overseas Payment · Pending · Chan Youvita  (lamine is NOT on this chain — won't show under non-admin lamine)
    ('VN_VNM0004_000001', '1526', '34', '2', '20260428', '090321',
     'chanyouvita.kosign@gmail.com', 'Chan Youvita', 'VN_VNM0004_000001', 'CÔNG TY TNHH WEBCASH VIỆT NAM',
     'Test A', 'VO_260428_007', 'Overseas Payment (WeCMs)', 0, 0, '20260428',
     'N', 'Y', 'chanyouvita.kosign@gmail.com', '20260428090321', 'chanyouvita.kosign@gmail.com', '20260428090321',
     'Y', '3', 'N', 'Y', 'Y',
     'File_1749540397981_2.png', 'Chan Youvita', 'N', NULL, NULL,
     10.000000, 'N', '00', 'PC', 'Y'),
    -- Payroll Payment · Pending · Chan Youvita
    ('VN_VNM0004_000001', '1525', '21', '2', '20260427', '090222',
     'chanyouvita.kosign@gmail.com', 'Chan Youvita', 'VN_VNM0004_000001', 'CÔNG TY TNHH WEBCASH VIỆT NAM',
     'Test A', 'VO_260427_006', 'Payroll Payment (WeCMs)', 0, 0, '20260427',
     'N', 'Y', 'chanyouvita.kosign@gmail.com', '20260427090222', 'chanyouvita.kosign@gmail.com', '20260427090222',
     'Y', '3', 'N', 'Y', 'Y',
     'File_1749540397981_2.png', 'Chan Youvita', 'N', NULL, NULL,
     24000028.000000, 'N', '00', 'PC', 'Y'),
    -- Internal Transfer · Pending · Chan Youvita
    ('VN_VNM0004_000001', '1522', '35', '2', '20260426', '084512',
     'chanyouvita.kosign@gmail.com', 'Chan Youvita', 'VN_VNM0004_000001', 'CÔNG TY TNHH WEBCASH VIỆT NAM',
     'Test A', 'VO_260426_003', 'Internal Transfer (WeCMS)', 0, 0, '20260426',
     'N', 'Y', 'chanyouvita.kosign@gmail.com', '20260426084512', 'chanyouvita.kosign@gmail.com', '20260426084512',
     'Y', '3', 'N', 'Y', 'Y',
     'File_1749540397981_2.png', 'Chan Youvita', 'N', NULL, NULL,
     10.000000, 'N', '00', 'PC', 'Y'),
    -- Vendor Payment · Pending · Arthur (lamine is the assigned approver — visibility passes)
    ('VN_VNM0004_000001', '1521', '32', '2', '20260425', '071155',
     'arthurmorgan@rdr2.com', 'Arthur', 'VN_VNM0004_000001', 'CÔNG TY TNHH WEBCASH VIỆT NAM',
     'Test', 'VO_260425_002', 'Vendor Payment (WeCMS)', 0, 0, '20260425',
     'N', 'Y', 'arthurmorgan@rdr2.com', '20260425071155', 'arthurmorgan@rdr2.com', '20260425071155',
     'Y', '3', 'N', 'Y', 'Y',
     'File_1749540397981_2.png', 'Arthur', 'N', NULL, NULL,
     100.000000, 'N', '00', 'PC', 'Y'),
    -- Vendor Payment · In Progress · Arthur (lamine already approved, Arthur still pending)
    ('VN_VNM0004_000001', '1520', '32', '2', '20260424', '064640',
     'arthurmorgan@rdr2.com', 'Arthur', 'VN_VNM0004_000001', 'CÔNG TY TNHH WEBCASH VIỆT NAM',
     'Test', 'VO_260424_001', 'Vendor Payment (WeCMS)', 0, 0, '20260424',
     'N', 'Y', 'arthurmorgan@rdr2.com', '20260424064640', 'lamine@gmail.com', '20260424064640',
     'Y', '3', 'N', 'Y', 'Y',
     'File_1749540397981_2.png', 'Arthur', 'N', NULL, NULL,
     10.000000, 'N', '03', 'PC', 'Y'),
    -- Internal Transfer · Approved · lamine
    ('VN_VNM0004_000001', '1519', '35', '2', '20260423', '043251',
     'lamine@gmail.com', 'lamine', 'VN_VNM0004_000001', 'CÔNG TY TNHH WEBCASH VIỆT NAM',
     'Testing Internal Transfer', 'VO_260423_002', 'Internal Transfer (WeCMS)', 0, 0, '20260423',
     'N', 'Y', 'lamine@gmail.com', '20260423043251', 'lamine@gmail.com', '20260423043251',
     'Y', '3', 'N', 'Y', 'Y',
     'File_1749540397981_2.png', 'lamine', 'Y', '20260423043301', 'lamine',
     1000.000000, 'N', '01', 'PC', 'Y')
ON CONFLICT (BIZ_KEY, APPR_SEQ_NO) DO NOTHING;

-- ----------------------------------------------------------------------------
-- Approver chain — one or two approvers per master row. Stamps mirror the
-- master's reg_dtm/mod_dtm. Status letters: A = approved, R = rejected,
-- P = pending (current actor's turn), W = waiting (downstream).
-- ----------------------------------------------------------------------------
INSERT INTO EXPT_RESOL_APPRVR_INFM (
    BIZ_KEY, APPR_SEQ_NO, APPRVR_ID, APPRVR_STTS, APPRVR_NM, APPR_DTM,
    REG_ID, REG_DTM, MOD_ID, MOD_DTM, READ_YN, APPRVR_LINE_NO, APPR_LINE_NO
) VALUES
    ('VN_VNM0004_000001', '1540', 'lamine@gmail.com', 'R', 'lamine', '20260513075556',
     'lamine@gmail.com', '20260513075547', 'lamine@gmail.com', '20260513075547', 'N', 1, 21),
    ('VN_VNM0004_000001', '1539', 'lamine@gmail.com', 'A', 'lamine', '20260512075058',
     'lamine@gmail.com', '20260512071347', 'lamine@gmail.com', '20260512071347', 'N', 1, 21),
    ('VN_VNM0004_000001', '1538', 'lamine@gmail.com', 'A', 'lamine', '20260510064817',
     'lamine@gmail.com', '20260510064741', 'lamine@gmail.com', '20260510064741', 'N', 1, 21),
    ('VN_VNM0004_000001', '1537', 'lamine@gmail.com', 'A', 'lamine', '20260509093221',
     'lamine@gmail.com', '20260509093212', 'lamine@gmail.com', '20260509093212', 'N', 1, 21),
    ('VN_VNM0004_000001', '1536', 'lamine@gmail.com', 'A', 'lamine', '20260508084958',
     'lamine@gmail.com', '20260508084948', 'lamine@gmail.com', '20260508084948', 'N', 1, 21),
    ('VN_VNM0004_000001', '1535', 'lamine@gmail.com', 'A', 'lamine', '20260507084754',
     'lamine@gmail.com', '20260507084744', 'lamine@gmail.com', '20260507084744', 'N', 1, 21),
    ('VN_VNM0004_000001', '1534', 'lamine@gmail.com', 'A', 'lamine', '20260506084313',
     'lamine@gmail.com', '20260506084224', 'lamine@gmail.com', '20260506084224', 'N', 1, 21),
    ('VN_VNM0004_000001', '1533', 'lamine@gmail.com', 'A', 'lamine', '20260505073329',
     'lamine@gmail.com', '20260505073317', 'lamine@gmail.com', '20260505073317', 'N', 1, 21),
    ('VN_VNM0004_000001', '1532', 'lamine@gmail.com', 'A', 'lamine', '20260504075819',
     'lamine@gmail.com', '20260504075735', 'lamine@gmail.com', '20260504075735', 'N', 1, 21),
    ('VN_VNM0004_000001', '1531', 'lamine@gmail.com', 'A', 'lamine', '20260503075606',
     'lamine@gmail.com', '20260503075542', 'lamine@gmail.com', '20260503075542', 'N', 1, 21),
    ('VN_VNM0004_000001', '1530', 'lamine@gmail.com', 'A', 'lamine', '20260502074727',
     'lamine@gmail.com', '20260502074711', 'lamine@gmail.com', '20260502074711', 'N', 1, 21),
    ('VN_VNM0004_000001', '1529', 'lamine@gmail.com', 'A', 'lamine', '20260501074552',
     'lamine@gmail.com', '20260501074537', 'lamine@gmail.com', '20260501074537', 'N', 1, 21),
    ('VN_VNM0004_000001', '1528', 'lamine@gmail.com', 'A', 'lamine', '20260430073458',
     'lamine@gmail.com', '20260430073445', 'lamine@gmail.com', '20260430073445', 'N', 1, 21),
    ('VN_VNM0004_000001', '1527', 'lamine@gmail.com', 'A', 'lamine', '20260429072900',
     'lamine@gmail.com', '20260429072658', 'lamine@gmail.com', '20260429072658', 'N', 1, 21),
    ('VN_VNM0004_000001', '1526', 'chanyouvita.kosign@gmail.com', 'P', 'Chan Youvita', NULL,
     'chanyouvita.kosign@gmail.com', '20260428090321', 'chanyouvita.kosign@gmail.com', '20260428090321', 'N', 1, 22),
    ('VN_VNM0004_000001', '1526', 'to@gmail.com', 'W', 'Tota', NULL,
     'chanyouvita.kosign@gmail.com', '20260428090321', 'chanyouvita.kosign@gmail.com', '20260428090321', 'N', 2, 22),
    ('VN_VNM0004_000001', '1525', 'chanyouvita.kosign@gmail.com', 'P', 'Chan Youvita', NULL,
     'chanyouvita.kosign@gmail.com', '20260427090222', 'chanyouvita.kosign@gmail.com', '20260427090222', 'N', 1, 22),
    ('VN_VNM0004_000001', '1525', 'to@gmail.com', 'W', 'Tota', NULL,
     'chanyouvita.kosign@gmail.com', '20260427090222', 'chanyouvita.kosign@gmail.com', '20260427090222', 'N', 2, 22),
    ('VN_VNM0004_000001', '1522', 'chanyouvita.kosign@gmail.com', 'P', 'Chan Youvita', NULL,
     'chanyouvita.kosign@gmail.com', '20260426084512', 'chanyouvita.kosign@gmail.com', '20260426084512', 'N', 1, 22),
    ('VN_VNM0004_000001', '1522', 'to@gmail.com', 'W', 'Tota', NULL,
     'chanyouvita.kosign@gmail.com', '20260426084512', 'chanyouvita.kosign@gmail.com', '20260426084512', 'N', 2, 22),
    ('VN_VNM0004_000001', '1521', 'lamine@gmail.com', 'P', 'lamine', NULL,
     'arthurmorgan@rdr2.com', '20260425071155', 'arthurmorgan@rdr2.com', '20260425071155', 'N', 1, 21),
    ('VN_VNM0004_000001', '1520', 'lamine@gmail.com', 'A', 'lamine', '20260424070422',
     'lamine@gmail.com', '20260424064640', 'lamine@gmail.com', '20260424064640', 'N', 1, 24),
    ('VN_VNM0004_000001', '1520', 'arthurmorgan@rdr2.com', 'P', 'Arthur', NULL,
     'lamine@gmail.com', '20260424064640', 'lamine@gmail.com', '20260424064640', 'N', 2, 24),
    ('VN_VNM0004_000001', '1519', 'lamine@gmail.com', 'A', 'lamine', '20260423043301',
     'lamine@gmail.com', '20260423043251', 'lamine@gmail.com', '20260423043251', 'N', 1, 21)
ON CONFLICT (BIZ_KEY, APPR_SEQ_NO, APPRVR_ID) DO NOTHING;
