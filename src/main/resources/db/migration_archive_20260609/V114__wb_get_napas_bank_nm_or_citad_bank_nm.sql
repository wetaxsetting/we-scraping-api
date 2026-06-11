-- ============================================================================
-- V86__wb_get_napas_bank_nm_or_citad_bank_nm.sql
--
-- Settings > Contacts: port the legacy `wb_get_napas_bank_nm_or_citad_bank_nm`
-- function from DEV verbatim (OQ-7 decided 2026-05-12). The refactor's
-- contacts list / detail paths use this function instead of
-- `BankCdResolver` for bank-name resolution — preserves parity with the
-- legacy IDO `TRCO_INFM_R016.xml:63` shape.
--
-- Function body copied VERBATIM from JEX DEV (`wecmsvndev.wb_get_napas_bank_nm_or_citad_bank_nm`,
-- captured via the read-only `postgres_dev` MCP on 2026-05-13).
--
-- DEV sample invocations + returns (recorded for parity verification):
--   wb_get_napas_bank_nm_or_citad_bank_nm('970424', 'en') -> 'Shinhan Bank'
--   wb_get_napas_bank_nm_or_citad_bank_nm('970424', 'vi') -> 'ShinhanBank'
--   wb_get_napas_bank_nm_or_citad_bank_nm('970424', 'ko') -> NULL (no KO branch)
--   wb_get_napas_bank_nm_or_citad_bank_nm(NULL,     'en') -> NULL (guard)
--
-- Source table: `tb_firm_bank_code` — already exists locally from V59
-- (firm-banking-history source tables). The function therefore lands
-- with no additional schema dependency.
--
-- IMPORTANT: DEV's body recognises only two locale codes — 'en' and
-- 'vi'/'vn'. Korean and Khmer fall through to NULL. The refactor accepts
-- this verbatim; the frontend's missing-name fallback handles it.
--
-- Retirement plan (per `wecms-api/CLAUDE.md` "Surviving legacy
-- functions"): drop the function and replace
-- `LegacyFunctionRepository.resolveNapasOrCitadBankName` with a
-- `BankCdResolver` call once the NAPAS + CITAD code space is fully
-- reconciled inside `BANK_INFM`. No fixed deadline.
--
-- Idempotent: CREATE OR REPLACE FUNCTION.
-- ============================================================================

CREATE OR REPLACE FUNCTION wb_get_napas_bank_nm_or_citad_bank_nm(
    _bank_cd character varying,
    _lang_cd character varying
)
RETURNS character varying
LANGUAGE plpgsql
AS $function$
DECLARE
    bankNm varchar;
BEGIN
    IF _bank_cd IS NOT NULL AND _bank_cd <> '' THEN
        IF _lang_cd = 'en' THEN
            SELECT eng_abrvat_nm INTO bankNm
            FROM tb_firm_bank_code
            WHERE bank_cd = _bank_cd;
        ELSIF _lang_cd IN ('vi', 'vn') THEN
            SELECT bank_nm INTO bankNm
            FROM tb_firm_bank_code
            WHERE bank_cd = _bank_cd;
        END IF;
    END IF;

    RETURN bankNm;
END;
$function$;
