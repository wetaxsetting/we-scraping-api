package com.kosign.wecms.domain.banking.bankcredentials.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Bank credentials master used by the NXISAS scraping integration.
 * Mirrors legacy JEX {@code wabooks.entity.BankCertInfo} and the
 * {@code BANK_CERT_INFO} table column-for-column (V86 migration).
 *
 * <p>Composite PK {@code (BIZ_KEY, SEQ_NO)} matches the legacy
 * {@code BankCertInfoPk} (which itself extends {@code BusiInfmPk}).
 * Tenant scoping rides on the PK — every repository query reads
 * {@code bizKey} from {@link com.kosign.wecms.common.tenant.TenantContext}.
 *
 * <p>{@code CERT_INFO_DETL_CON}, {@code _CON2}, {@code _CON3} hold
 * the bank-side credential payload. Legacy JEX encrypts via Coocon
 * SEED-CBC ({@code ISASSeedCBC}, packaged inside the
 * {@code isas1.0.jar}) wrapped by a PostgreSQL {@code encrypt(?)} DB
 * function. The refactor switches to JCE AES-GCM in
 * {@link com.kosign.wecms.domain.banking.bankcredentials.service.BankCertCryptoService},
 * so the column carries an AES-GCM base64 ciphertext blob rather than
 * the legacy SEED-CBC payload. Column shape stays VARCHAR(2000) so a
 * future data-migration path stays open.
 *
 * <p>Audit columns are VARCHAR(14)/VARCHAR(100) strings (legacy JEX
 * shape, mirroring {@code ERP_TRNS_HSTR} from V71). The entity does
 * NOT extend {@link com.kosign.wecms.common.entity.BaseEntity} because
 * {@code BaseEntity} assumes {@code OffsetDateTime} audit columns.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "BANK_CERT_INFO")
@IdClass(BankCertInfoId.class)
public class BankCertInfo {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "SEQ_NO", nullable = false, precision = 10, scale = 0)
    private BigDecimal seqNo;

    @Column(name = "BANK_CD", length = 20)
    private String bankCd;

    /** Discriminator: bank vs personal vs corporate (legacy
     *  {@code BankCertInfoDv}). Refactor only writes the bank value
     *  ({@code "1"}) — JEX {@code cert_info_dv} is {@code varchar(1)} and
     *  all live rows carry {@code '1'} (verified on JEX 2026-06-09);
     *  personal-banking is being removed from JEX per
     *  {@code docs/jex/features/remove-vas-column-and-personal-banking.md}. */
    @Column(name = "CERT_INFO_DV", length = 1)
    private String certInfoDv;

    /** Plain-text username (legacy stores plain). */
    @Column(name = "CERT_INFO_CON", length = 2000)
    private String certInfoCon;

    /** AES-GCM ciphertext (base64) of the password. */
    @Column(name = "CERT_INFO_DETL_CON", length = 2000)
    private String certInfoDetlCon;

    /** Secondary encrypted slot (legacy used it for the second factor
     *  on some bank adapters). Unused by the VCB spike — kept for
     *  schema parity. */
    @Column(name = "CERT_INFO_DETL_CON2", length = 2000)
    private String certInfoDetlCon2;

    /** Tertiary encrypted slot. Added in legacy 2024.1.0.4; unused
     *  by the VCB spike. */
    @Column(name = "CERT_INFO_DETL_CON3", length = 2000)
    private String certInfoDetlCon3;

    /** Last account-list-inquiry status: free text from the gateway
     *  (typically a status code like {@code OK} / {@code FAIL}). */
    @Column(name = "ACCT_LIST_INQ_STTS", length = 20)
    private String acctListInqStts;

    /** Last account-list-inquiry message (gateway error text on
     *  failure). */
    @Column(name = "ACCT_LIST_INQ_MSG", length = 255)
    private String acctListInqMsg;

    /** Last account-list-inquiry date — legacy {@code yyyyMMdd}. */
    @Column(name = "LST_ACCT_LIST_INQ_DT", length = 8)
    private String lstAcctListInqDt;

    /** Cross-reference to a separate {@code NXISAS_GB_SEQ_NO} sequence
     *  some legacy banks need. Unused by the VCB spike. */
    @Column(name = "NXISAS_GB_SEQ_NO", precision = 10, scale = 0)
    private BigDecimal nxisasGbSeqNo;

    @Column(name = "REG_ID", length = 100, updatable = false)
    private String regId;

    /** Legacy {@code yyyyMMddHHmmss}. */
    @Column(name = "REG_DTM", length = 14, updatable = false)
    private String regDtm;

    @Column(name = "MOD_ID", length = 100)
    private String modId;

    @Column(name = "MOD_DTM", length = 14)
    private String modDtm;
}
