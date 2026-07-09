package com.kosign.wecms.domain.payment.cardtransactions.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite key for {@link SysDocIdGen} — {@code (BIZ_KEY, DOC_TYPE, REQ_DATE)}.
 * One row per tenant + document-type + UTC day; the row's {@code DOC_ID} holds
 * the latest issued id for that day (legacy per-day document numbering).
 */
public class SysDocIdGenId implements Serializable {

    private String bizKey;
    private String docType;
    private String reqDate;

    public SysDocIdGenId() {
    }

    public SysDocIdGenId(String bizKey, String docType, String reqDate) {
        this.bizKey = bizKey;
        this.docType = docType;
        this.reqDate = reqDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SysDocIdGenId that)) return false;
        return Objects.equals(bizKey, that.bizKey)
                && Objects.equals(docType, that.docType)
                && Objects.equals(reqDate, that.reqDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bizKey, docType, reqDate);
    }
}
