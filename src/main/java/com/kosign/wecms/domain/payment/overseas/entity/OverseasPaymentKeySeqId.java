package com.kosign.wecms.domain.payment.overseas.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

public class OverseasPaymentKeySeqId implements Serializable {

    private String bizKey;
    private LocalDate keyDt;

    public OverseasPaymentKeySeqId() {}
    public OverseasPaymentKeySeqId(String bizKey, LocalDate keyDt) {
        this.bizKey = bizKey;
        this.keyDt  = keyDt;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OverseasPaymentKeySeqId that)) return false;
        return Objects.equals(bizKey, that.bizKey) && Objects.equals(keyDt, that.keyDt);
    }

    @Override public int hashCode() { return Objects.hash(bizKey, keyDt); }
}
