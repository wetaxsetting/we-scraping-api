package com.kosign.wecms.domain.payment.dispatch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Firm Banking gateway audit log — minimal projection for the
 * Phase 2 Result detail surface.
 *
 * <p>Source table {@code TB_FIRM_TRAN} (V59) carries 30+ columns; this
 * entity maps only the four needed to resolve the API-path
 * {@code RS_CD} / {@code RS_MSG} on the Result detail page. Other
 * columns participate in the unified Firm Banking History view but are
 * not used by the dispatch-result resolver and stay unmapped (Hibernate
 * {@code validate} ignores DB columns not declared on the entity).
 *
 * <p>The PK on the local schema is {@code (TRAN_DT, BIZ_KEY, TRAN_SEQ)}
 * — three columns, matching the lookup the
 * {@link com.kosign.wecms.domain.payment.dispatch.service.DispatchResultResolver}
 * needs (no OUT_BANK_CD in the join chain).
 */
@Entity
@Table(name = "TB_FIRM_TRAN")
@IdClass(TbFirmTranId.class)
@Getter
@Setter
@NoArgsConstructor
public class TbFirmTran {

    @Id
    @Column(name = "TRAN_DT", length = 8, nullable = false)
    private String tranDt;

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "TRAN_SEQ", length = 10, nullable = false)
    private String tranSeq;

    /** Bank response code (e.g. {@code "0000"} = success, others = error
     *  reason). 4-char varchar on DEV. Populated by the bank-side daemon
     *  after the API call resolves. */
    @Column(name = "RS_CD", length = 4)
    private String rsCd;

    /** Human-readable bank response message. Nullable on DEV — the
     *  File path never populates this column. */
    @Column(name = "RS_MSG", length = 200)
    private String rsMsg;
}
