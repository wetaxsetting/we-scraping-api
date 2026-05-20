package com.kosign.wecms.domain.operation.dto;

/**
 * Wire response for {@code POST /api/v1/addon/operation-status/inquire}.
 * Shape mirrors the popup fields the user sees: Tax ID + Customer Name
 * + Address + raw GDT status string + the canonical 3-state status code
 * + the GDT "as-of" timestamp (legacy {@code SEARCHDATETIME}).
 *
 * <p>The inquire popup does not persist the row — the user can review
 * the data and click Save to upsert into {@code OPRT_STTS_INFO} (a
 * separate endpoint in a later MR), or close the dialog and discard.
 */
public record InquireOperationStatusResponse(
        String taxCode,
        String companyNm,
        String companyStatus,
        String statusCd,
        String officeAdd,
        String representativeFirstName,
        String searchDatetime
) { }
