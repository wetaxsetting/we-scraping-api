package com.kosign.wecms.domain.setting.contacts.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Settings &gt; Contacts &mdash; per-contact attachment row.
 *
 * <p>Mirrors legacy {@code TRCO_INFO_FILE} column-for-column. Each row
 * represents one uploaded file stored under
 * {@code <storage-root>/<biz_key>/contacts/<save_file_nm>} on the
 * filesystem (refactor slug folder name, not the legacy
 * {@code trco_0004_001_u002} WSVC subfolder).
 *
 * <p><b>{@code file_size}</b> is intentionally {@code VARCHAR(100)} on
 * the wire and DB &mdash; the legacy column stores the byte count as a
 * text token, not an integer. Refactor preserves the shape for parity
 * with DEV and to avoid silent precision loss on extremely large
 * files; service-side validation caps uploads at 10 MB anyway.
 *
 * <p><b>{@code reg_nm}</b> is materialised at insert time from the
 * uploader's {@code USER_INFM.USER_NM} so the list endpoint never
 * needs a JOIN. Mirrors legacy {@code TrcoInfoFileDAO.create}.
 *
 * <p><b>Audit columns</b> stay legacy {@code VARCHAR(14)} timestamp
 * shape ({@code yyyyMMddHHmmss} UTC) &mdash; same as
 * {@link TrcoInfm}. {@link com.kosign.wecms.common.entity.BaseEntity}
 * is intentionally NOT extended (it ships {@code TIMESTAMPTZ}).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "TRCO_INFO_FILE")
@IdClass(TrcoInfoFileId.class)
public class TrcoInfoFile {

    @Id
    @Column(name = "TRCO_KEY", length = 20, nullable = false)
    private String trcoKey;

    /** Per-{@code TRCO_KEY} MAX+1 counter ("1", "2", ...). Allocated
     *  via the V99 {@code wb_get_next_val} branch on insert. */
    @Id
    @Column(name = "FILE_ID", length = 100, nullable = false)
    private String fileId;

    /** Original filename from the upload (before server rename). */
    @Column(name = "ORG_FILE_NM", length = 500, nullable = false)
    private String orgFileNm;

    /** Server-renamed filename ({@code UUID + .<ext>}) — addressable
     *  under {@code <storage-root>/<biz_key>/contacts/}. */
    @Column(name = "SAVE_FILE_NM", length = 500, nullable = false)
    private String saveFileNm;

    @Column(name = "REG_ID", length = 100, nullable = false)
    private String regId;

    /** {@code yyyyMMddHHmmss} UTC. */
    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;

    /** Uploader display name — materialised from {@code USER_INFM.USER_NM}
     *  at insert time so list reads skip the JOIN. */
    @Column(name = "REG_NM", length = 100, nullable = false)
    private String regNm;

    /** Byte count stored as TEXT (legacy quirk, NOT a numeric column). */
    @Column(name = "FILE_SIZE", length = 100)
    private String fileSize;

    /** Document title — DEV default {@code '기타문서'} ("Other
     *  document"). Service overrides on insert per legacy rule:
     *  {@code FILE_ID = "1"} → {@code "Business Registration"}; else
     *  → {@code "New document"}. */
    @Column(name = "TRCO_INFM_TITL", length = 50, nullable = false)
    private String trcoInfmTitl;

    @Column(name = "MOD_ID", length = 100, nullable = false)
    private String modId;

    @Column(name = "MOD_DTM", length = 14, nullable = false)
    private String modDtm;
}
