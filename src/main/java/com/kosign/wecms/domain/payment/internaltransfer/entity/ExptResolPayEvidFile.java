package com.kosign.wecms.domain.payment.internaltransfer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One <em>payment-evidence</em> attachment registered against an approval
 * (legacy {@code EXPT_RESOL_PAY_EVID_FILE}). Read-only mapping for the
 * Approval Details popup — the refactor never writes this table; the rows
 * are produced by the live JEX payment flows.
 *
 * <p>Legacy parity: the JEX attachment query {@code IDO.EXPT_RESOL_FILE_R002}
 * returns the <strong>UNION</strong> of {@code EXPT_RESOL_FILE} and
 * {@code EXPT_RESOL_PAY_EVID_FILE}, discriminated by a synthesized
 * {@code FILE_TYPE} column. The refactor previously read only
 * {@code EXPT_RESOL_FILE}; this entity closes GAP-ATT-1 so payment-evidence
 * files appear in the popup too.
 *
 * <p>Column shape mirrors {@code EXPT_RESOL_FILE} EXCEPT the PK id column,
 * which is {@code EVID_FILE_ID} (verified against {@code wecmsvndev} on
 * 2026-06-13), not {@code FILE_ID}.
 */
@Entity
@Table(name = "EXPT_RESOL_PAY_EVID_FILE")
@IdClass(ExptResolPayEvidFileId.class)
@Getter
@Setter
@NoArgsConstructor
public class ExptResolPayEvidFile {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "APPR_SEQ_NO", length = 15, nullable = false)
    private String apprSeqNo;

    /** Per-row evidence-file sequence within the approval — legacy id
     *  column {@code EVID_FILE_ID} (distinct from {@code EXPT_RESOL_FILE.FILE_ID}). */
    @Id
    @Column(name = "EVID_FILE_ID", length = 100, nullable = false)
    private String evidFileId;

    @Column(name = "ORG_FILE_NM", length = 500, nullable = false)
    private String orgFileNm;

    @Column(name = "SAVE_FILE_NM", length = 500, nullable = false)
    private String saveFileNm;

    @Column(name = "NOTE", length = 255)
    private String note;

    @Column(name = "REG_ID", length = 100, nullable = false)
    private String regId;

    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;

    @Column(name = "REG_NM", length = 100, nullable = false)
    private String regNm;

    /** Byte count as a decimal string — legacy storage type
     *  ({@code VARCHAR}). Null when unknown. */
    @Column(name = "FILE_SIZE", length = 100)
    private String fileSize;
}
