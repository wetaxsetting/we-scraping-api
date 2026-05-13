package com.kosign.wecms.domain.payment.approvalbox.service;

/**
 * Approval Box tab discriminator codes from
 * {@code EXPT_RESOL_MSTR.APPR_TYPE_CD}. Four legacy values drive the
 * Approval Box tabs at {@code resl_0001_001_view_osmc.jsp:268-294}:
 *
 * <ul>
 *   <li>{@code 00} — Pending (no approver has acted yet).</li>
 *   <li>{@code 03} — In Progress (at least one approver acted, more
 *       remain).</li>
 *   <li>{@code 01} — Approved (every step approved; workflow finalised).</li>
 *   <li>{@code 02} — Rejected (one step rejected; workflow finalised).</li>
 * </ul>
 *
 * <p>The refactor exposes a stable derived status on the wire
 * ({@code "P"} / {@code "I"} / {@code "C"} / {@code "R"}) computed in
 * {@link ApprovalBoxService} per {@code discovery.md} §10 OQ-3
 * option (a) — no new {@code wb_*} helper.
 */
public final class ApprTypeCd {

    public static final String PENDING     = "00";
    public static final String APPROVED    = "01";
    public static final String REJECTED    = "02";
    public static final String IN_PROGRESS = "03";

    private ApprTypeCd() { }
}
