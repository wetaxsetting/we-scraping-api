package com.kosign.wecms.domain.banking.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Composite primary key for {@link BankExchRateInfm}.
 * Mirrors {@code wabooks.entity.BankExchRateInfmPk} from JEX DEV.
 *
 * PK columns: BIZ_KEY + BANK_CD + REF_DATE + CCY_CD + EXCH_SEQ_NO
 * EXCH_SEQ_NO is generated via {@code wb_get_next_val('bank_exch_rate_infm','exch_seq_no')}
 * (registered in V25 migration).
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BankExchRateInfmId implements Serializable {

    private String bizKey;
    private String bankCd;
    /** YYYYMMDD varchar — matches JEX REF_DATE storage. */
    private String refDate;
    private String ccyCd;
    private String exchSeqNo;
}
