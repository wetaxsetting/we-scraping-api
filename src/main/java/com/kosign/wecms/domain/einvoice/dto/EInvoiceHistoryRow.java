package com.kosign.wecms.domain.einvoice.dto;

/**
 * One e-Invoice "Latest Inquiry History" row — a past GDT scrape call
 * read back from {@code EXT_TRAN_CALL_HIS}. Mirrors the legacy
 * {@code nxisas_einvhis_r001} REC element (after its {@code normalizeResult}
 * step). Wire shape lowercase snake_case via the global Jackson strategy.
 *
 * @param regDtm     14-digit inquiry timestamp ({@code yyyyMMddHHmmss})
 * @param type       {@code SALES} / {@code EXPENSE} / {@code ""} (derived from {@code SVC_ID})
 * @param svcId      raw {@code SVC_ID} (e.g. {@code GDT_INQUIRY_SALES})
 * @param startDate  queried range start ({@code yyyyMMdd})
 * @param endDate    queried range end ({@code yyyyMMdd})
 * @param result     {@code SUCCESS} when {@code RSLT_CD = '00000000'}, else {@code FAIL}
 * @param rsltCd     raw engine result code
 * @param errMessage human-readable failure reason (legacy extracts
 *                   {@code Output.ErrorMessage} from a JSON {@code ERR_CONT})
 */
public record EInvoiceHistoryRow(
        String regDtm,
        String type,
        String svcId,
        String startDate,
        String endDate,
        String result,
        String rsltCd,
        String errMessage
) { }
