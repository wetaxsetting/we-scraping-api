package com.kosign.wecms.domain.setting.contacts.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite-PK id for {@link TrcoInfoFile} — mirrors the legacy
 * {@code TRCO_INFO_FILE} primary key shape {@code (TRCO_KEY, FILE_ID)}.
 * Both columns are {@code VARCHAR}; the file id is the numeric text
 * token allocated by the legacy {@code wb_get_next_val(...)} branch
 * (V99), per-TRCO_KEY MAX+1.
 */
public class TrcoInfoFileId implements Serializable {

    private String trcoKey;
    private String fileId;

    public TrcoInfoFileId() {
    }

    public TrcoInfoFileId(String trcoKey, String fileId) {
        this.trcoKey = trcoKey;
        this.fileId = fileId;
    }

    public String getTrcoKey() { return trcoKey; }
    public void setTrcoKey(String trcoKey) { this.trcoKey = trcoKey; }

    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TrcoInfoFileId other)) return false;
        return Objects.equals(trcoKey, other.trcoKey)
            && Objects.equals(fileId, other.fileId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(trcoKey, fileId);
    }
}
