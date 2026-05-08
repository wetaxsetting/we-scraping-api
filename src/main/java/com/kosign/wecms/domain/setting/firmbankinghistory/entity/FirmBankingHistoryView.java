package com.kosign.wecms.domain.setting.firmbankinghistory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

/**
 * Read-only projection of {@code V_FIRM_BANKING_HISTORY} (V60), the
 * five-leg unified history view that backs the Settings &gt; Firm
 * Banking History screen. Mirrors legacy {@code IDO.TB_FIRM_TRAN_R002}
 * column-for-column.
 *
 * <p>{@link Immutable @Immutable} flags the entity as read-only —
 * Hibernate skips dirty-checking and never flushes state changes. The
 * underlying PostgreSQL view is also un-updatable (not declared
 * {@code WITH UPDATABLE}), so this is defence-in-depth: the database
 * rejects writes even if a caller bypasses the entity layer.
 *
 * <p>Five legs feed this view, discriminated by {@link #svcType}:
 * <ul>
 *   <li>{@code F} — TB_FIRM_TRAN (Korean firm-banking gateway)</li>
 *   <li>{@code S} — NAPAS_REMIT_TRNS_HSTR + EXT_TRAN_CALL_HIS scraping</li>
 *   <li>{@code G} — NXSIAS_MODULE_TRAN_LOG_HIS GDT e-Invoice inquiry</li>
 *   <li>{@code B} — NXSIAS_MODULE_TRAN_LOG_HIS Vietnam Business inquiry</li>
 * </ul>
 *
 * <p>Tenant scoping is the caller's responsibility — every repository
 * method must constrain on {@code BIZ_KEY} from
 * {@link com.kosign.wecms.common.tenant.TenantContext#currentBizKey()}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Immutable
@Table(name = "V_FIRM_BANKING_HISTORY")
@IdClass(FirmBankingHistoryViewId.class)
public class FirmBankingHistoryView {

    /** Tenant key. Always part of the WHERE clause; never exposed on the wire. */
    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    /** VN-localized timestamp ({@code YYYYMMDDHHMMSS}). For the {@code F}
     *  leg this is {@code kr_to_vn_time(TRAN_DT || TRAN_TM)}; for every
     *  other leg it equals the source row's {@code REG_DTM}. Used as
     *  the date-range filter target via {@code LEFT(TARN_DTM, 8)
     *  BETWEEN ? AND ?}. */
    @Id
    @Column(name = "TARN_DTM", length = 14)
    private String tarnDtm;

    /** Source-leg discriminator: {@code F}/{@code S}/{@code G}/{@code B}. */
    @Id
    @Column(name = "SVC_TYPE", length = 1)
    private String svcType;

    /** Source-row sequence identifier (per-leg semantics differ:
     *  {@code TRAN_SEQ} for F, {@code SEQ_NO::varchar} for NAPAS/EXT_TRAN,
     *  {@code UUID} for NXSIAS). Free-form keyword search hits this
     *  column case-insensitively. */
    @Id
    @Column(name = "SEQ_NO", length = 50)
    private String seqNo;

    @Column(name = "SVC_NM", length = 50)
    private String svcNm;

    @Column(name = "DEP_BANK_CD", length = 200)
    private String depBankCd;

    @Column(name = "RSLT_CD", length = 20)
    private String rsltCd;

    @Column(name = "RSLT_MSG", length = 2000)
    private String rsltMsg;

    @Column(name = "JOB", length = 5000)
    private String job;

    @Column(name = "REQ_DATA", length = 1000)
    private String reqData;

    @Column(name = "VAN_ORG_CD", length = 20)
    private String vanOrgCd;
}
