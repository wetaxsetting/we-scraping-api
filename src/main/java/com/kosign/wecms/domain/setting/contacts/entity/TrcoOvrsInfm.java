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
 * Settings &gt; Contacts &mdash; overseas beneficiary row (Overseas
 * contact only). Mirrors legacy {@code TRCO_OVRS_INFM} column-for-column
 * for the Sprint subset.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "TRCO_OVRS_INFM")
@IdClass(TrcoOvrsInfmId.class)
public class TrcoOvrsInfm {

    @Id
    @Column(name = "TRCO_KEY", length = 20, nullable = false)
    private String trcoKey;

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "TRCO_OVRS_KEY", length = 20, nullable = false)
    private String trcoOvrsKey;

    @Column(name = "BENE_NAME", length = 200)
    private String beneName;

    /** SWIFT BIC code. */
    @Column(name = "BIC_CODE", length = 50)
    private String bicCode;

    @Column(name = "BENE_ACCT_NO", length = 100)
    private String beneAcctNo;

    @Column(name = "BENE_ADDR", length = 500)
    private String beneAddr;

    @Column(name = "BENE_BANK_CODE", length = 20)
    private String beneBankCode;

    @Column(name = "BENE_BANK_NAME", length = 200)
    private String beneBankName;

    @Column(name = "REG_ID", length = 100, nullable = false)
    private String regId;

    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;

    @Column(name = "MOD_ID", length = 100, nullable = false)
    private String modId;

    @Column(name = "MOD_DTM", length = 14, nullable = false)
    private String modDtm;
}
