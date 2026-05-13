package com.kosign.wecms.domain.setting.contacts.dto;

/**
 * One attachment row returned by the contacts file endpoints.
 * Maps {@link com.kosign.wecms.domain.setting.contacts.entity.TrcoInfoFile}
 * onto the wire shape the frontend list / table render against.
 *
 * <p>{@code save_file_nm} is intentionally NOT exposed — the server-side
 * UUID filename never leaves the backend. The frontend addresses each
 * attachment by {@code file_id} and downloads via the
 * {@code /api/v1/settings/contacts/{trcoKey}/files/{fileId}} endpoint
 * which composes the path on the server.
 */
public record ContactFileRow(
        String fileId,
        String orgFileNm,
        String fileSize,
        String regDtm,
        String regNm,
        String trcoInfmTitl
) { }
