package com.kosign.wecms.domain.payment.approvalbox.service;

/**
 * Approver-row status codes from
 * {@code EXPT_RESOL_APPRVR_INFM.APPRVR_STTS}. Five-state machine taken
 * verbatim from the legacy frontend hover-card at
 * {@code WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/resl/resl_0001_001_osmc.ts:566-589}:
 *
 * <ul>
 *   <li>{@code P} — pending; approver's turn has come but no action yet.</li>
 *   <li>{@code A} — approved; approver acted favourably.</li>
 *   <li>{@code R} — rejected; approver acted unfavourably.</li>
 *   <li>{@code W} — unable (waiting); not the approver's turn yet.</li>
 *   <li>{@code S} — unable (stopped); approver blocked by an upstream
 *       rejection.</li>
 * </ul>
 *
 * <p>The Approve / Reject endpoints only fire when the current
 * approver's row is {@code P} (their turn). Acting twice is rejected
 * with {@code WBE2052}. The status field is a stable identifier on the
 * wire — the frontend resolves the visible label via i18n.
 */
public final class ApprvrStts {

    public static final String PENDING  = "P";
    public static final String APPROVED = "A";
    public static final String REJECTED = "R";
    /** Waiting — not yet this approver's turn. */
    public static final String WAITING  = "W";
    /** Stopped — upstream approver rejected. */
    public static final String STOPPED  = "S";

    private ApprvrStts() { }
}
