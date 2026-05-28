package com.kosign.wecms.domain.payment.internaltransfer.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite key for {@link ExptResolFile} — mirrors the legacy
 * {@code (BIZ_KEY, APPR_SEQ_NO, FILE_ID)} primary key. Java requires
 * a {@code Serializable} no-arg + equals/hashCode for
 * {@code @IdClass} composite PKs.
 */
public class ExptResolFileId implements Serializable {

    private String bizKey;
    private String apprSeqNo;
    private String fileId;

    public ExptResolFileId() {}

    public ExptResolFileId(String bizKey, String apprSeqNo, String fileId) {
        this.bizKey    = bizKey;
        this.apprSeqNo = apprSeqNo;
        this.fileId    = fileId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExptResolFileId other)) return false;
        return Objects.equals(bizKey, other.bizKey)
            && Objects.equals(apprSeqNo, other.apprSeqNo)
            && Objects.equals(fileId, other.fileId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bizKey, apprSeqNo, fileId);
    }
}
