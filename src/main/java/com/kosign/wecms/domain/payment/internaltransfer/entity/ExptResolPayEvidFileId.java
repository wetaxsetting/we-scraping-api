package com.kosign.wecms.domain.payment.internaltransfer.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite key for {@link ExptResolPayEvidFile} — mirrors the legacy
 * {@code (BIZ_KEY, APPR_SEQ_NO, EVID_FILE_ID)} primary key. Note the id
 * column is {@code EVID_FILE_ID} (NOT {@code FILE_ID} as in
 * {@link ExptResolFile}) — the two attachment tables use different id
 * column names, which is why the combined attachment list must carry a
 * {@code fileType} discriminator so a download can route to the right
 * table. Java requires a {@code Serializable} no-arg + equals/hashCode
 * for {@code @IdClass} composite PKs.
 */
public class ExptResolPayEvidFileId implements Serializable {

    private String bizKey;
    private String apprSeqNo;
    private String evidFileId;

    public ExptResolPayEvidFileId() {}

    public ExptResolPayEvidFileId(String bizKey, String apprSeqNo, String evidFileId) {
        this.bizKey     = bizKey;
        this.apprSeqNo  = apprSeqNo;
        this.evidFileId = evidFileId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExptResolPayEvidFileId other)) return false;
        return Objects.equals(bizKey, other.bizKey)
            && Objects.equals(apprSeqNo, other.apprSeqNo)
            && Objects.equals(evidFileId, other.evidFileId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bizKey, apprSeqNo, evidFileId);
    }
}
