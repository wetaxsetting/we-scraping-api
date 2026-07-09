package com.kosign.wecms.domain.payment.dispatch.repository;

/**
 * Read-only aggregate projection over {@code TB_PAY_FILE_DATA} for one
 * bulk file, mirroring legacy IDO {@code TB_PAY_FILE_DATA_R005}
 * ({@code WECMS_BATCH_ETC/xml/service/IDO/IDO.TB_PAY_FILE_DATA_R005.xml:24-47}).
 * Drives the BATCH1 pre-flight network/currency validation
 * ({@code BulkTransferValidator}).
 *
 * <p>Counts are grouped by {@code ANT_DIV_CD} (network: E=EBANK, V=VCB,
 * C=CITAD, N=NAPAS, S=SWIFT) and by the concatenated transfer+remit currency
 * ({@code TRSC_CCY_CD || REMIT_CCY_CD}). {@code swiftOuterCnt} is the legacy
 * {@code SWIFT_OUTER_CNT} / {@code FAIL_CHNL_CNT} column (the two are
 * byte-identical expressions in R005 — rows whose {@code ANT_DIV_CD} is
 * neither {@code 'S'} nor blank).
 */
public interface BulkValidationCounts {
    Long getEbankCnt();
    Long getEbankOuterCnt();
    Long getVcbCnt();
    Long getVcbOuterCnt();
    Long getNapasCnt();
    Long getSwiftOuterCnt();
    /** Distinct {@code TRSC_CCY_CD || REMIT_CCY_CD} values — &gt;1 means a currency mix. */
    Long getCcyCnt();
    /** Rows where {@code TRSC_CCY_CD <> REMIT_CCY_CD} (currency-exchange transfer). */
    Long getExchangeTranCnt();
}
