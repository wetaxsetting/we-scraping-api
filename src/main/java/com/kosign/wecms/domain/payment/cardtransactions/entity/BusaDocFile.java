package com.kosign.wecms.domain.payment.cardtransactions.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Receipt / e-Invoice attachment row — mirrors legacy {@code BUSA_DOC_FILE}
 * column-for-column (audit finding G3/G5). One row per uploaded file, stored
 * under {@code <storage-root>/<biz_key>/card-transactions/<save_file_nm>} on
 * the filesystem.
 *
 * <p>PK {@code (BIZ_KEY, DOC_ID, FILE_ID)}; {@code FILE_ID} is the
 * per-{@code (BIZ_KEY, DOC_ID)} MAX+1 counter minted by
 * {@code wb_get_next_val('busa_doc_file','file_id', biz_key, doc_id)}.
 *
 * <p>Read by the legacy IDO {@code BUSA_DOC_MSTR_R034} {@code REC_1} aggregate;
 * written by {@code prch_0005_001_c001}. {@code FILE_SIZE} is legacy
 * {@code VARCHAR(100)} (byte count as text). Audit columns are legacy
 * {@code VARCHAR(14)} UTC — {@link com.kosign.wecms.common.entity.BaseEntity}
 * is intentionally not extended.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "BUSA_DOC_FILE")
@IdClass(BusaDocFileId.class)
public class BusaDocFile {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "DOC_ID", length = 50, nullable = false)
    private String docId;

    @Id
    @Column(name = "FILE_ID", length = 100, nullable = false)
    private String fileId;

    /** Original filename from the upload (before server rename). */
    @Column(name = "ORG_FILE_NM", length = 500)
    private String orgFileNm;

    /** Server-renamed filename ({@code UUID + .<ext>}). */
    @Column(name = "SAVE_FILE_NM", length = 500)
    private String saveFileNm;

    /** Byte count stored as TEXT (legacy quirk, NOT a numeric column). */
    @Column(name = "FILE_SIZE", length = 100)
    private String fileSize;

    @Column(name = "REG_ID", length = 100)
    private String regId;

    /** {@code yyyyMMddHHmmss} UTC. */
    @Column(name = "REG_DTM", length = 14)
    private String regDtm;
}
