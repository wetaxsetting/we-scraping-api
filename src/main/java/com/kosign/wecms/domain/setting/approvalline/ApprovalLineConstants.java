package com.kosign.wecms.domain.setting.approvalline;

/**
 * Feature-wide constants for the Settings &gt; Approval Line refactor.
 *
 * <p>The single source of truth for the kebab-case slug identifier
 * across every refactor surface — REST URI, log tag, future
 * View Setting {@code WEB_SVC_ID}, future audit-log {@code WSVC_ID}, etc.
 * Defined once here per the workspace
 * <a href="../../../../../../../../CLAUDE.md">CLAUDE.md</a>
 * §"Refactor identifier naming" rule: legacy JEX WSVC ids
 * ({@code comm_0222_001} et al.) are never used as identifiers in
 * the new system — only as JEX file:line citations.
 *
 * <p>Reference: legacy JEX source is {@code comm_0222_*} under
 * {@code WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/} +
 * {@code WECMS DEV/WECMS/src/wabooks/entity/ApprLineInfm*.java} /
 * {@code ApprLineInfmDetl*.java}. Refactor feature spec lives at
 * {@code docs/refactor-wecms/features/approval-line.md}.
 */
public final class ApprovalLineConstants {

    /** Kebab-case slug — the canonical identifier for this feature
     *  across REST URIs, log tags, View Setting screen ids (if/when
     *  adopted), and any future audit-log writer. */
    public static final String SLUG = "approval-line";

    /** Hard cap on approvers per line. Mirrors legacy WBW1164 in
     *  {@code comm_0222_002_c001_act.jsp:90} /
     *  {@code comm_0222_003_u001_act.jsp:125}. Pinned at the DB level
     *  too via {@code APPRVR_DISP_ORD NUMERIC(1,0)}. */
    public static final int MAX_APPROVERS = 5;

    private ApprovalLineConstants() { /* no instances */ }
}
