package com.kosign.wecms.domain.setting.logincredentials.entity;

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
 * GDT (General Department of Taxation) credentials master — sibling of
 * {@link com.kosign.wecms.domain.banking.bankcredentials.entity.BankCertInfo}.
 * One row per (BIZ_KEY, SEQ_NO); the legacy JEX Login Credentials list
 * UNIONs this table with BANK_CERT_INFO and surfaces a single "GDT" row
 * per tenant alongside one row per configured bank.
 *
 * <p>Maps the V118 schema column-for-column. Audit columns are the
 * legacy VARCHAR(14) / VARCHAR(100) strings (yyyyMMddHHmmss) — entity
 * does NOT extend {@link com.kosign.wecms.common.entity.BaseEntity},
 * same precedent as {@code BankCertInfo}.
 *
 * <p>{@code CERT_INFO_DETL_CON} holds a legacy-compatible AES-256-CBC
 * (pgcrypto {@code encrypt()}) base64 ciphertext of the password, produced
 * by
 * {@link com.kosign.wecms.domain.banking.bankcredentials.service.BankCertCryptoService}
 * — byte-identical to the legacy JEX {@code public.encrypt()/decrypt()} DB
 * functions. Bank and GDT credentials share one crypto key so the same
 * service handles both column populations.
 *
 * <p>(Superseded note: the retired refactor AES-GCM cipher is NOT used here
 * — do not reintroduce it while the DB is shared with JEX.)
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "GDT_CERT_INFO")
@IdClass(GdtCertInfoId.class)
public class GdtCertInfo {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "SEQ_NO", nullable = false, precision = 10, scale = 0)
    private BigDecimal seqNo;

    /** Legacy {@code GdtCertInfoDv} discriminator. JEX is {@code varchar(1)}
     *  with all live rows {@code '1'} (verified on JEX 2026-06-09). */
    @Column(name = "CERT_INFO_DV", length = 1)
    private String certInfoDv;

    /** Plain-text login id (legacy stores plain). */
    @Column(name = "CERT_INFO_CON", length = 2000)
    private String certInfoCon;

    /** Legacy-compatible AES-256-CBC (pgcrypto {@code encrypt()}) base64 ciphertext of the password. */
    @Column(name = "CERT_INFO_DETL_CON", length = 2000)
    private String certInfoDetlCon;

    /** Secondary slot retained for legacy parity (some GDT adapters
     *  encode a second factor here). Unused by the refactor today. */
    @Column(name = "CERT_INFO_DETL_CON2", length = 2000)
    private String certInfoDetlCon2;

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
