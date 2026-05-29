package com.kosign.wecms.domain.payment.dispatch.entity;

import java.io.Serializable;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Composite PK for {@link TbFirmTran} — matches the local schema's
 * {@code PRIMARY KEY (TRAN_DT, BIZ_KEY, TRAN_SEQ)} (V59).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TbFirmTranId implements Serializable {

    private String tranDt;
    private String bizKey;
    private String tranSeq;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TbFirmTranId other)) return false;
        return Objects.equals(tranDt, other.tranDt)
                && Objects.equals(bizKey, other.bizKey)
                && Objects.equals(tranSeq, other.tranSeq);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tranDt, bizKey, tranSeq);
    }
}
