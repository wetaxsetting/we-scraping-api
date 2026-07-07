package com.kosign.wecms.domain.payment.cardtransactions.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite key for {@link BusaDocFile} — mirrors the legacy
 * {@code BUSA_DOC_FILE} primary key {@code (BIZ_KEY, DOC_ID, FILE_ID)}.
 * {@code FILE_ID} is the per-{@code (BIZ_KEY, DOC_ID)} numeric text token
 * allocated by {@code wb_get_next_val('busa_doc_file','file_id', biz_key, doc_id)}.
 */
public class BusaDocFileId implements Serializable {

    private String bizKey;
    private String docId;
    private String fileId;

    public BusaDocFileId() {
    }

    public BusaDocFileId(String bizKey, String docId, String fileId) {
        this.bizKey = bizKey;
        this.docId = docId;
        this.fileId = fileId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BusaDocFileId that)) return false;
        return Objects.equals(bizKey, that.bizKey)
                && Objects.equals(docId, that.docId)
                && Objects.equals(fileId, that.fileId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bizKey, docId, fileId);
    }
}
