package com.kosign.wecms.domain.einvoice.entity;

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
 * Counterparty activity-history row &mdash; {@code TRCO_ACTV_HSTR}. One
 * row per scraped invoice when the e-Invoice detail-save auto-registers
 * the counterparty (legacy {@code nxisas_einvoice_c002_act.jsp:883-909}).
 *
 * <p>The table belongs to the {@code TRCO_*} family (Contacts), but
 * Contacts never consumes it; the e-Invoice flow is its first and only
 * consumer, so the entity lives in the {@code einvoice} domain per the
 * "port when the consumer lands" package rule.
 *
 * <p><b>Primary key</b>: composite {@code (BIZ_KEY, ACTV_SEQ)} &mdash;
 * {@code ACTV_SEQ} is generated per-tenant (MAX+1) in
 * {@code EInvoiceTrcoAutoRegistrar}. The legacy JEX {@code TrcoActvHstrPk}
 * carries {@code ACTV_SEQ} alone; the refactor adds {@code BIZ_KEY} to the
 * PK for tenant safety (see the V239 migration header &mdash; DEV PK shape
 * to be confirmed when the {@code postgres} MCP is reachable).
 *
 * <p><b>Sprint subset</b>: maps only the columns the auto-registrar
 * writes. The V239 schema carries the full DEV-parity column set;
 * Hibernate {@code validate} ignores the unmapped ones
 * ({@code TRCO_FLNM} / {@code BANK_CD} / {@code ACCT_NO} / {@code CONT} /
 * {@code NOTE} / {@code RCV_NM} / {@code RSLT_CONT} / {@code TRSC_DT}),
 * which are nullable so inserts succeed without them.
 *
 * <p>Audit columns use the legacy {@code VARCHAR(14)} {@code yyyyMMddHHmmss}
 * shape (same precedent as {@code TrcoInfm} / {@code ErpTrnsHstr}), NOT the
 * refactor {@code BaseEntity} {@code OffsetDateTime} shape. The service
 * stamps them explicitly.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "TRCO_ACTV_HSTR")
@IdClass(TrcoActvHstrId.class)
public class TrcoActvHstr {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    /** Per-tenant activity sequence (MAX+1, generated in the service). */
    @Id
    @Column(name = "ACTV_SEQ", length = 40, nullable = false)
    private String actvSeq;

    @Column(name = "USER_ID", length = 100, nullable = false)
    private String userId;

    @Column(name = "TRCO_KEY", length = 20, nullable = false)
    private String trcoKey;

    @Column(name = "TRCO_CMNM", length = 200, nullable = false)
    private String trcoCmnm;

    @Column(name = "TRCO_CMENM", length = 200)
    private String trcoCmenm;

    /** Dedup key &mdash; the master {@code DOC_ID} (legacy {@code apiApvKey}). */
    @Column(name = "API_APV_KEY", length = 50)
    private String apiApvKey;

    /** Activity division: {@code 'S'} (sale) for SALES, {@code 'B'} (buy)
     *  for EXPENSE &mdash; legacy {@code TrcoActvDvCd}. */
    @Column(name = "DV_CD", length = 10)
    private String dvCd;

    /** Task code: {@code 'S003'} for SALES, {@code 'B003'} for EXPENSE
     *  &mdash; legacy {@code TrcoActvTaskCd}. */
    @Column(name = "TASK_CD", length = 10)
    private String taskCd;

    @Column(name = "CCY_CD", length = 3)
    private String ccyCd;

    @Column(name = "ACTV_AMT")
    private BigDecimal actvAmt;

    /** Processing status &mdash; legacy DAO defaults to {@code 'U'} (updated). */
    @Column(name = "PCSN_STTS", length = 1)
    private String pcsnStts;

    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;

    /** Result status &mdash; legacy DAO defaults to {@code 'C'} (created). */
    @Column(name = "RSLT_CD", length = 1)
    private String rsltCd;

    @Column(name = "MOD_ID", length = 100)
    private String modId;

    @Column(name = "MOD_DTM", length = 14)
    private String modDtm;

    /** Contact-confirmed flag &mdash; legacy DAO defaults to {@code '1'} (NO). */
    @Column(name = "TRCO_CNFM_YN", length = 1)
    private String trcoCnfmYn;
}
