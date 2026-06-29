package com.kosign.wecms.domain.payment.haengsung.oracle;

import java.math.BigDecimal;

/**
 * One {@code TB_TRNS_DETAIL} row read from the HAENGSUNG Oracle ERP DB
 * (physical {@code FILE_*} / {@code TR_*} block — {@code haengsung-payment-refresh}
 * §14.2, M-1). Read filter is {@code END_GB = '0'}, so every row carried here is
 * a not-yet-imported line. Only the subset the import maps is selected; result /
 * fee / overseas-remittance columns the import does not consume are omitted.
 */
public record OracleTransDetail(
        BigDecimal fileSeq,         // PK part — line sequence → ERP_FILE_SEQ
        BigDecimal trAmt,           // line amount → TRAN_AMT / TRNF_AMT
        String     isoCode,         // currency → TRNF_CCY_CD
        String     sendGbn,         // T in-house / O other-bank / S overseas
        String     antDivCd,        // interbank network (N/C/E/V/S; T disputed — M-3/M-12)
        String     acetNm,          // payee name → RCPT_NM / BNFC_NM (tone stripped)
        String     trAcctNo,        // payee account → FINC_MDA_NO / BNFC_ACCT_NO (spaces stripped)
        String     trAcctBankCd,    // payee bank / BIC → TAR_BANK_CD / BNFC_BANK_BIC+CD (spaces stripped)
        String     trAcctBankNm,    // payee bank name → BNFC_BANK_NAME (overseas)
        String     acetAddr,        // payee address → BNFC_ADDR (overseas)
        String     foriFeeMan,      // fee bearer 1/2 → FEE_PAYER_CD (blank/3→S,1→B,2→O)
        String     jeokyo,          // memo → TRAN_USAG/TAR_ACCT_RMRK/WDRL_ACCT_RMRK or MEMO
        String     attribute2       // ERP attribute → ERP_ATTRIBUTE2
) { }
