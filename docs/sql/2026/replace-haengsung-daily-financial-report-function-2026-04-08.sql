-- WeCMS Manual Database Change
-- Date: 2026-04-08
-- Source: migrated from Claude Code (AI)/sql/BUG-001_fix_account_type_label_20260408.sql
-- Related record: docs/bug-reports/BUG-001_haengsung_daily_financial_wrong_account_type_label.md (path as cited by the original script header)
-- Type: DDL
-- Execution: MANUAL
-- Target environments: TBD — original script does not name the environments
-- Executor: DBA TEAM
-- Rollback: ROLLBACK REQUIRES DBA REVIEW — restoring the prior function body requires the pre-change definition
-- Verification: pre/post-check queries are present in the original script below
-- SQL REVIEW REQUIRED — this script replaces an existing live DB function in place
--
-- Migrated 2026-08-17 into the governed wecms-api SQL location. The original file
-- remains unchanged in the workspace archive; nothing below this line was edited.
-- =============================================================================

-- ============================================================
-- BUG-001 — Fix: Fixed Deposit accounts show as "Loan account"
-- in Daily Financial report (rept_0017_001) for HAENGSUNG
-- ============================================================
-- Bug report : docs/bug-reports/BUG-001_haengsung_daily_financial_wrong_account_type_label.md
-- Author     : [developer name]
-- Date       : 2026-04-08
--
-- Problem:
--   In FN_GET_HAENGSUNG_DAILY_FINANCIAL_REPORT, acct_type_cd = '02'
--   (DEPOSIT = Fixed Term Deposit = 정기예적금) is incorrectly mapped
--   to 'Loan'. It should show as 'Fixed Deposit' (its own label).
--
-- Fix (minimal — only the wrong label):
--   In the filtered_accounts CTE, change the account_type_display CASE
--   so that '02' (Fixed Term Deposit) gets its own 'Fixed Deposit' label
--   instead of being grouped with '01' (Loan).
--
--   BEFORE (wrong):
--     WHEN am.acct_type_cd IN ('03', '04', '07') THEN 'Current'
--     WHEN am.acct_type_cd IN ('05', '06', '08') THEN 'Saving'
--     WHEN am.acct_type_cd IN ('01', '02') THEN 'Loan'
--
--   AFTER (correct):
--     WHEN am.acct_type_cd IN ('03', '04', '07') THEN 'Current'
--     WHEN am.acct_type_cd IN ('05', '06', '08') THEN 'Saving'
--     WHEN am.acct_type_cd = '02' THEN 'Fixed Deposit'
--     WHEN am.acct_type_cd = '01' THEN 'Loan'
--
--   Nothing else in the function is changed — WHERE filter, HEADER row,
--   Before Balance, Transactions, Sub Totals, and Currency Grand Totals
--   are identical to the current version.
--
-- Run on: DEV → UAT → PROD (same script)
-- Schema: wecmsvndev (DEV/UAT) | confirm schema name for PROD
--
-- IMPORTANT: This replaces the entire function definition.
--   1. Verify the function body below matches the current PROD version
--      before running. Get the current definition via:
--      SELECT pg_get_functiondef(oid) FROM pg_proc
--      WHERE proname = 'fn_get_haengsung_daily_financial_report';
--   2. Only the account_type_display CASE has been changed.
--      All other logic is identical to the current version.
-- ============================================================

CREATE OR REPLACE FUNCTION wecmsvndev.fn_get_haengsung_daily_financial_report(
    p_biz_key character varying,
    p_as_of_date character varying,
    p_account_filter character varying DEFAULT 'ALL'::character varying
)
RETURNS TABLE(
    row_type character varying, bank_account character varying,
    currency character varying, rept_cd character varying,
    pymn_cd character varying, description_english character varying,
    description_korean character varying, customer_supplier character varying,
    before_balance numeric, receipt numeric, payment numeric,
    balance numeric, remarks character varying,
    balance_company_actual numeric, account_number character varying,
    account_type_order integer, biz_key character varying,
    acct_seq_no character varying, transaction_date character varying,
    transaction_time character varying, transaction_seq integer,
    memo character varying, bank_cd_out character varying,
    bank_name_sort character varying
)
LANGUAGE plpgsql
AS $function$
DECLARE
    v_prev_date VARCHAR;
BEGIN
    -- Validate date format
    IF p_as_of_date !~ '^\d{8}$' THEN
        RAISE EXCEPTION 'Invalid date format. Expected YYYYMMDD, got: %', p_as_of_date;
    END IF;

    -- Calculate previous date
    v_prev_date := TO_CHAR(TO_DATE(p_as_of_date, 'YYYYMMDD') - INTERVAL '1 day', 'YYYYMMDD');

    RETURN QUERY
    SELECT * FROM (
        WITH filtered_accounts AS (
            SELECT
                am.biz_key,
                am.bank_cd,
                am.acct_no,
                am.acct_type_cd,
                am.ccy_cd,
                am.expi_dt,
                am.corc_bf_bal,
                am.bal,
                am.prn_seq,
                COALESCE(bi.rprs_bank_nm, 'Bank ' || am.bank_cd) AS display_bank_nm,
                -- ============================================================
                -- BUG-001 FIX: '02' (Fixed Term Deposit = 정기예적금) gets
                -- its own label 'Fixed Deposit' → displays as
                -- "Fixed Deposit account" in the report header.
                -- Previously was incorrectly grouped with '01' (Loan).
                -- ============================================================
                CASE
                    WHEN am.acct_type_cd IN ('03', '04', '07') THEN 'Current'
                    WHEN am.acct_type_cd IN ('05', '06', '08') THEN 'Saving'
                    WHEN am.acct_type_cd = '02' THEN 'Fixed Deposit'             -- BUG-001 FIX: was grouped under 'Loan'
                    WHEN am.acct_type_cd = '01' THEN 'Loan'
                    ELSE 'Unknown'
                END AS account_type_display,
                COALESCE(am.ccy_cd, 'VND') AS effective_currency,
                CASE
                    WHEN COALESCE(am.ccy_cd, 'VND') = 'VND' THEN 1
                    WHEN COALESCE(am.ccy_cd, 'VND') = 'USD' THEN 2
                    WHEN COALESCE(am.ccy_cd, 'VND') = 'KRW' THEN 3
                    ELSE 99
                END AS currency_order,
                COALESCE(bi.rprs_bank_nm, 'Bank ' || am.bank_cd) AS sort_bank_name,
                am.acct_no AS sort_account_no
            FROM wecmsvndev.acct_mstr am
            LEFT JOIN wecmsvndev.bank_infm bi
                ON am.bank_cd = bi.bank_cd
                AND p_as_of_date BETWEEN bi.start_date AND bi.end_date
            WHERE am.biz_key = p_biz_key
              AND am.actv_yn = 'Y'
              AND (
                    p_account_filter = 'ALL'
                 OR EXISTS (
                        SELECT 1
                        FROM jsonb_array_elements(p_account_filter::jsonb) AS filter_item
                        WHERE (filter_item->>'BANK_CD')::varchar = am.bank_cd
                          AND (filter_item->>'ACCT_NO')::varchar = am.acct_no
                    )
              )
              AND (
                    am.acct_type_cd IN ('03', '04', '07')
                    OR (
                        am.acct_type_cd IN ('05', '06', '08')
                        AND (am.expi_dt IS NULL
                             OR am.expi_dt = ''
                             OR am.expi_dt = '00000000'
                             OR TO_DATE(am.expi_dt, 'YYYYMMDD') >= CURRENT_DATE)
                        AND am.bal > 0
                    )
                    OR am.acct_type_cd NOT IN ('03', '04', '05', '06', '07', '08')
              )
        ),

        before_balance_calc AS (
            SELECT DISTINCT ON (fa.biz_key, fa.bank_cd, fa.acct_no)
                fa.biz_key,
                fa.bank_cd,
                fa.acct_no,
                COALESCE(
                    (SELECT rth2.bal
                     FROM wecmsvndev.rept_trns_hstr rth2
                     WHERE rth2.biz_key = fa.biz_key
                         AND rth2.bank_cd = fa.bank_cd
                         AND rth2.acct_no = fa.acct_no
                         AND rth2.trsc_dt < p_as_of_date
                     ORDER BY rth2.trsc_dt DESC,
                              COALESCE(rth2.trsc_tm, '000000') DESC,
                              rth2.acct_seq_no DESC
                     LIMIT 1),
                    fa.bal,
                    0
                ) AS before_balance
            FROM filtered_accounts fa
        ),

        daily_transactions AS (
            SELECT DISTINCT ON (
                rth.biz_key, rth.bank_cd, rth.acct_no,
                rth.trsc_dt, rth.trsc_amt, rth.inout_dv,
                COALESCE(rth.rmrk, ''), COALESCE(rth.rmrk2, ''),
                rth.acct_seq_no
            )
                rth.biz_key, rth.bank_cd, rth.acct_no, rth.ccy_cd,
                rth.trsc_dt, rth.trsc_tm, rth.acct_seq_no,
                rth.trsc_amt, rth.inout_dv, rth.rmrk, rth.rmrk2,
                rth.rept_cd, rth.pymn_cd, rth.desc_nm_en, rth.desc_nm_ko,
                rth.supp_rmrk, rth.memo, atrpi.rcv_pay_trco_nm,
                bbc.before_balance,
                CASE WHEN rth.inout_dv = '1' THEN rth.trsc_amt ELSE 0 END AS receipt_amt,
                CASE WHEN rth.inout_dv = '2' THEN rth.trsc_amt ELSE 0 END AS payment_amt,
                ROUND(
                    bbc.before_balance + SUM(
                        CASE
                            WHEN rth.inout_dv = '1' THEN rth.trsc_amt
                            WHEN rth.inout_dv = '2' THEN -rth.trsc_amt
                            ELSE 0
                        END
                    ) OVER (
                        PARTITION BY rth.biz_key, rth.bank_cd, rth.acct_no
                        ORDER BY rth.trsc_dt, COALESCE(rth.trsc_tm, '000000'), rth.acct_seq_no
                        ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
                    ), 2
                ) AS running_balance,
                ROW_NUMBER() OVER (
                    PARTITION BY rth.biz_key, rth.bank_cd, rth.acct_no
                    ORDER BY rth.trsc_dt, COALESCE(rth.trsc_tm, '000000'), rth.acct_seq_no
                )::integer AS trans_row_num
            FROM wecmsvndev.rept_trns_hstr rth
            INNER JOIN filtered_accounts fa
                ON rth.biz_key = fa.biz_key AND rth.bank_cd = fa.bank_cd AND rth.acct_no = fa.acct_no
            LEFT JOIN before_balance_calc bbc
                ON rth.biz_key = bbc.biz_key AND rth.bank_cd = bbc.bank_cd AND rth.acct_no = bbc.acct_no
            LEFT JOIN wecmsvndev.acct_trns_rcv_pay_infm atrpi
                ON rth.biz_key = atrpi.biz_key AND rth.bank_cd = atrpi.bank_cd
                AND rth.acct_seq_no = atrpi.acct_seq_no AND rth.trsc_dt = atrpi.trsc_dt
            WHERE rth.trsc_dt = p_as_of_date
                AND COALESCE(rth.hand_reg_yn, 'N') = 'N'
            ORDER BY
                rth.biz_key, rth.bank_cd, rth.acct_no,
                rth.trsc_dt, rth.trsc_amt, rth.inout_dv,
                COALESCE(rth.rmrk, ''), COALESCE(rth.rmrk2, ''),
                rth.acct_seq_no, COALESCE(rth.trsc_tm, '000000') DESC
        ),

        account_totals AS (
            SELECT
                fa.biz_key, fa.bank_cd, fa.acct_no,
                fa.effective_currency, fa.account_type_display, fa.expi_dt,
                COALESCE(bbc.before_balance, 0) AS total_before_balance,
                ROUND(COALESCE(SUM(dt.receipt_amt), 0), 2) AS total_receipt,
                ROUND(COALESCE(SUM(dt.payment_amt), 0), 2) AS total_payment,
                ROUND(
                    COALESCE(bbc.before_balance, 0) +
                    COALESCE(SUM(dt.receipt_amt), 0) -
                    COALESCE(SUM(dt.payment_amt), 0), 2
                ) AS final_balance
            FROM filtered_accounts fa
            LEFT JOIN before_balance_calc bbc
                ON fa.biz_key = bbc.biz_key AND fa.bank_cd = bbc.bank_cd AND fa.acct_no = bbc.acct_no
            LEFT JOIN daily_transactions dt
                ON fa.biz_key = dt.biz_key AND fa.bank_cd = dt.bank_cd AND fa.acct_no = dt.acct_no
            GROUP BY
                fa.biz_key, fa.bank_cd, fa.acct_no,
                fa.effective_currency, fa.account_type_display, fa.expi_dt, bbc.before_balance
        )

        -- 1. Account Header Rows
        SELECT
            'HEADER'::VARCHAR AS row_type,
            (fa.display_bank_nm || CHR(10) ||
            '(' || fa.account_type_display || ' account)' || CHR(10) ||
            CASE
                WHEN fa.account_type_display = 'Saving'
                     AND fa.expi_dt IS NOT NULL AND fa.expi_dt != ''
                     AND fa.expi_dt != '00000000'
                THEN 'Due date: ' || TO_CHAR(TO_DATE(fa.expi_dt, 'YYYYMMDD'), 'DD.Mon.YYYY')
                ELSE ''
            END)::VARCHAR AS bank_account,
            fa.effective_currency::VARCHAR AS currency,
            ''::VARCHAR AS rept_cd, ''::VARCHAR AS pymn_cd,
            ''::VARCHAR AS description_english, ''::VARCHAR AS description_korean,
            ''::VARCHAR AS customer_supplier,
            NULL::NUMERIC AS before_balance, NULL::NUMERIC AS receipt,
            NULL::NUMERIC AS payment, NULL::NUMERIC AS balance,
            ''::VARCHAR AS remarks, NULL::NUMERIC AS balance_company_actual,
            fa.acct_no::VARCHAR AS account_number,
            fa.currency_order AS account_type_order,
            fa.biz_key::VARCHAR AS biz_key, ''::VARCHAR AS acct_seq_no,
            ''::VARCHAR AS transaction_date, ''::VARCHAR AS transaction_time,
            0::integer AS transaction_seq, ''::VARCHAR AS memo,
            fa.bank_cd::VARCHAR AS bank_cd_out,
            fa.display_bank_nm::VARCHAR AS bank_name_sort
        FROM filtered_accounts fa

        UNION ALL

        -- 2. Before Balance Row
        SELECT
            'BEFORE_BALANCE'::VARCHAR AS row_type,
            fa.acct_no::VARCHAR AS bank_account,
            fa.effective_currency::VARCHAR AS currency,
            ''::VARCHAR AS rept_cd, ''::VARCHAR AS pymn_cd,
            'Before Balance'::VARCHAR AS description_english,
            '전일잔고'::VARCHAR AS description_korean,
            ''::VARCHAR AS customer_supplier,
            ROUND(COALESCE(bbc.before_balance, 0), 2) AS before_balance,
            NULL::NUMERIC AS receipt, NULL::NUMERIC AS payment, NULL::NUMERIC AS balance,
            ''::VARCHAR AS remarks, NULL::NUMERIC AS balance_company_actual,
            fa.acct_no::VARCHAR AS account_number,
            fa.currency_order AS account_type_order,
            fa.biz_key::VARCHAR AS biz_key, ''::VARCHAR AS acct_seq_no,
            ''::VARCHAR AS transaction_date, ''::VARCHAR AS transaction_time,
            1::integer AS transaction_seq, ''::VARCHAR AS memo,
            fa.bank_cd::VARCHAR AS bank_cd_out,
            fa.display_bank_nm::VARCHAR AS bank_name_sort
        FROM filtered_accounts fa
        LEFT JOIN before_balance_calc bbc
            ON fa.biz_key = bbc.biz_key AND fa.bank_cd = bbc.bank_cd AND fa.acct_no = bbc.acct_no

        UNION ALL

        -- 3. Transaction Rows
        SELECT
            'TRANSACTION'::VARCHAR AS row_type,
            ''::VARCHAR AS bank_account,
            COALESCE(dt.ccy_cd::VARCHAR, fa.effective_currency::VARCHAR) AS currency,
            COALESCE(dt.rept_cd, '')::VARCHAR AS rept_cd,
            COALESCE(dt.pymn_cd, '')::VARCHAR AS pymn_cd,
            COALESCE(
                NULLIF(dt.desc_nm_en, ''),
                CASE
                    WHEN dt.bank_cd = '11704020' THEN
                        CASE WHEN dt.inout_dv = '2' THEN dt.rmrk2 ELSE dt.rmrk END
                    ELSE
                        CASE
                            WHEN dt.rmrk2 IS NOT NULL AND dt.rmrk2 != ''
                                 AND dt.rmrk IS NOT NULL AND dt.rmrk != ''
                            THEN dt.rmrk2 || ' - ' || dt.rmrk
                            WHEN dt.rmrk2 IS NOT NULL AND dt.rmrk2 != ''
                                 AND (dt.rmrk IS NULL OR dt.rmrk = '')
                            THEN dt.rmrk2
                            ELSE dt.rmrk
                        END
                END, ''
            )::VARCHAR AS description_english,
            COALESCE(dt.desc_nm_ko, '')::VARCHAR AS description_korean,
            COALESCE(dt.rcv_pay_trco_nm, dt.supp_rmrk, '')::VARCHAR AS customer_supplier,
            NULL::NUMERIC AS before_balance,
            dt.receipt_amt AS receipt, dt.payment_amt AS payment,
            dt.running_balance AS balance,
            ''::VARCHAR AS remarks, NULL::NUMERIC AS balance_company_actual,
            fa.acct_no::VARCHAR AS account_number,
            fa.currency_order AS account_type_order,
            dt.biz_key::VARCHAR AS biz_key,
            dt.acct_seq_no::VARCHAR AS acct_seq_no,
            dt.trsc_dt::VARCHAR AS transaction_date,
            COALESCE(dt.trsc_tm, '')::VARCHAR AS transaction_time,
            (1000 + dt.trans_row_num)::integer AS transaction_seq,
            COALESCE(dt.memo, '')::VARCHAR AS memo,
            dt.bank_cd::VARCHAR AS bank_cd_out,
            fa.display_bank_nm::VARCHAR AS bank_name_sort
        FROM filtered_accounts fa
        INNER JOIN daily_transactions dt
            ON fa.biz_key = dt.biz_key AND fa.bank_cd = dt.bank_cd AND fa.acct_no = dt.acct_no

        UNION ALL

        -- 4. Account SUB TOTAL Row
        SELECT
            'ACCOUNT_TOTAL'::VARCHAR AS row_type,
            'SUB TOTAL'::VARCHAR AS bank_account,
            at.effective_currency::VARCHAR AS currency,
            ''::VARCHAR AS rept_cd, ''::VARCHAR AS pymn_cd,
            'SUB TOTAL'::VARCHAR AS description_english,
            '소계'::VARCHAR AS description_korean,
            ''::VARCHAR AS customer_supplier,
            at.total_before_balance AS before_balance,
            at.total_receipt AS receipt, at.total_payment AS payment,
            at.final_balance AS balance,
            ''::VARCHAR AS remarks,
            at.final_balance AS balance_company_actual,
            fa.acct_no::VARCHAR AS account_number,
            fa.currency_order AS account_type_order,
            fa.biz_key::VARCHAR AS biz_key, ''::VARCHAR AS acct_seq_no,
            ''::VARCHAR AS transaction_date, ''::VARCHAR AS transaction_time,
            99999::integer AS transaction_seq, ''::VARCHAR AS memo,
            fa.bank_cd::VARCHAR AS bank_cd_out,
            fa.display_bank_nm::VARCHAR AS bank_name_sort
        FROM filtered_accounts fa
        INNER JOIN account_totals at
            ON fa.biz_key = at.biz_key AND fa.bank_cd = at.bank_cd AND fa.acct_no = at.acct_no

        UNION ALL

        -- 5. Currency Grand Total (VND)
        SELECT
            'CURRENCY_TOTAL'::VARCHAR AS row_type, ''::VARCHAR AS bank_account,
            'VND'::VARCHAR AS currency,
            ''::VARCHAR AS rept_cd, ''::VARCHAR AS pymn_cd,
            'TOTAL'::VARCHAR AS description_english, '총계'::VARCHAR AS description_korean,
            ''::VARCHAR AS customer_supplier,
            ROUND(SUM(at.total_before_balance), 2) AS before_balance,
            ROUND(SUM(at.total_receipt), 2) AS receipt,
            ROUND(SUM(at.total_payment), 2) AS payment,
            ROUND(SUM(at.final_balance), 2) AS balance,
            ''::VARCHAR AS remarks,
            ROUND(SUM(at.final_balance), 2) AS balance_company_actual,
            ''::VARCHAR AS account_number, 999990 AS account_type_order,
            p_biz_key::VARCHAR AS biz_key, ''::VARCHAR AS acct_seq_no,
            ''::VARCHAR AS transaction_date, ''::VARCHAR AS transaction_time,
            999990 AS transaction_seq, ''::VARCHAR AS memo,
            ''::VARCHAR AS bank_cd_out, ''::VARCHAR AS bank_name_sort
        FROM account_totals at
        WHERE at.effective_currency = 'VND'
        HAVING COUNT(*) > 0

        UNION ALL

        -- 6. Currency Grand Total (USD)
        SELECT
            'CURRENCY_TOTAL'::VARCHAR AS row_type, ''::VARCHAR AS bank_account,
            'USD'::VARCHAR AS currency,
            ''::VARCHAR AS rept_cd, ''::VARCHAR AS pymn_cd,
            'TOTAL'::VARCHAR AS description_english, '총계'::VARCHAR AS description_korean,
            ''::VARCHAR AS customer_supplier,
            ROUND(SUM(at.total_before_balance), 2) AS before_balance,
            ROUND(SUM(at.total_receipt), 2) AS receipt,
            ROUND(SUM(at.total_payment), 2) AS payment,
            ROUND(SUM(at.final_balance), 2) AS balance,
            ''::VARCHAR AS remarks,
            ROUND(SUM(at.final_balance), 2) AS balance_company_actual,
            ''::VARCHAR AS account_number, 999991 AS account_type_order,
            p_biz_key::VARCHAR AS biz_key, ''::VARCHAR AS acct_seq_no,
            ''::VARCHAR AS transaction_date, ''::VARCHAR AS transaction_time,
            999991 AS transaction_seq, ''::VARCHAR AS memo,
            ''::VARCHAR AS bank_cd_out, ''::VARCHAR AS bank_name_sort
        FROM account_totals at
        WHERE at.effective_currency = 'USD'
        HAVING COUNT(*) > 0

        UNION ALL

        -- 7. Currency Grand Total (KRW)
        SELECT
            'CURRENCY_TOTAL'::VARCHAR AS row_type, ''::VARCHAR AS bank_account,
            'KRW'::VARCHAR AS currency,
            ''::VARCHAR AS rept_cd, ''::VARCHAR AS pymn_cd,
            'TOTAL'::VARCHAR AS description_english, '총계'::VARCHAR AS description_korean,
            ''::VARCHAR AS customer_supplier,
            ROUND(SUM(at.total_before_balance), 2) AS before_balance,
            ROUND(SUM(at.total_receipt), 2) AS receipt,
            ROUND(SUM(at.total_payment), 2) AS payment,
            ROUND(SUM(at.final_balance), 2) AS balance,
            ''::VARCHAR AS remarks,
            ROUND(SUM(at.final_balance), 2) AS balance_company_actual,
            ''::VARCHAR AS account_number, 999992 AS account_type_order,
            p_biz_key::VARCHAR AS biz_key, ''::VARCHAR AS acct_seq_no,
            ''::VARCHAR AS transaction_date, ''::VARCHAR AS transaction_time,
            999992 AS transaction_seq, ''::VARCHAR AS memo,
            ''::VARCHAR AS bank_cd_out, ''::VARCHAR AS bank_name_sort
        FROM account_totals at
        WHERE at.effective_currency = 'KRW'
        HAVING COUNT(*) > 0

    ) AS report_data
    ORDER BY account_type_order, bank_name_sort, account_number, transaction_seq;

END;
$function$;
