package com.kosign.wecms.domain.setting.purposesettings.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Wire body for {@code POST /api/v1/settings/purpose-settings/excel/import}.
 * The frontend forwards back the rows it received from the preview
 * endpoint after the user confirms; the server re-validates everything
 * for race-safety (between Browse → Upload another user may have
 * inserted a conflicting row in the DB).
 *
 * <p>Purpose doesn't expose an Append/Replace toggle — the legacy
 * {@code comm_0187_004_c001} flow is pure-append, mirroring the
 * single-tenant master-list semantic. The refactor preserves that
 * (no `upldOptn` field; every upload simply inserts new rows).
 */
public record PurposeExcelImportRequest(
        @NotNull
        @NotEmpty
        List<Row> rows
) {
    /** One row from the preview the frontend forwarded back. */
    public record Row(
            String handUseUsagCd,
            String useUsagNm,
            String useUsagCont,
            String parentUseUsagCd
    ) { }
}
