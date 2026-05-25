package com.kosign.wecms.domain.setting.chartofaccounts;

import java.util.Set;

/**
 * Settings &gt; Chart of Accounts &mdash; cross-class constants.
 * Centralised here so the service, validator, and tree builder all
 * reference the same values.
 */
public final class ChartOfAccountsConstants {

    private ChartOfAccountsConstants() { }

    /** Refactor slug used in REST URI / package / log tag /
     *  {@code AUDIT_LOGS_INFM.WSVC_ID}. NEVER use the legacy
     *  {@code comm_0223_001} id outside Javadoc citations. */
    public static final String WSVC_SLUG = "chart-of-accounts";

    /** Audit-log identifier for {@code AUDIT_LOGS_INFM.REF_NO}
     *  (the entity-name column). Mirrors firm-banking-settings /
     *  contacts precedent of carrying the UPPER_SNAKE table name. */
    public static final String AUDIT_REF_NO = "CHART_ACCT_INFM";

    /** Maximum hierarchy depth permitted.
     *
     *  <p><b>Business rule (PO directive 2026-05-25):</b> only level 2
     *  and level 3 are user-creatable; level 1 is reserved for the
     *  three seeded roots ({@link #RESTRICTED_CODES}: ACC-01 / ACC-02 /
     *  ACC-03). Total depth cap is therefore <b>3</b> &mdash; tighter
     *  than the legacy {@code IDO.CHART_ACCT_INFM_R002.xml:62-99}
     *  4-level CASE projection AND the legacy upload's max-5 guard
     *  ({@code comm_0223_002_r001_act.jsp:401}).
     *
     *  <p>The depth check in {@code ChartOfAccountsService.create} +
     *  {@link com.kosign.wecms.domain.setting.chartofaccounts.service.ChartOfAccountsValidator#assertDepthCap}
     *  reject any write that would push a leaf beyond this cap with
     *  {@code WBE_CHART_OF_ACCOUNTS_DEPTH_EXCEEDED}. The companion
     *  parent-required check ({@code WBE_CHART_OF_ACCOUNTS_PARENT_REQUIRED})
     *  blocks the level-1 case so the same write path covers
     *  "no level 1, max level 3" together. */
    public static final int MAX_HIERARCHY_DEPTH = 3;

    /** System-managed root codes that cannot be edited / deleted via
     *  the public CRUD endpoints. Legacy parity — same set hard-coded
     *  in {@code comm_0223_001_u001_act.jsp:213} and
     *  {@code comm_0223_001_d001_act.jsp:172}. Frontend list rows
     *  carry a derived {@code restricted: true} flag for the same set
     *  so the Edit / Delete buttons can be disabled client-side. */
    public static final Set<String> RESTRICTED_CODES = Set.of("ACC-01", "ACC-02", "ACC-03");

    /** Default ID generator suffix on the soft-delete archive rename
     *  scheme. Legacy parity — {@code comm_0223_001_d001_act.jsp:125}
     *  ({@code prefix(0..4) + last5(seq_no) + 'D'}). */
    public static final String ARCHIVE_CODE_SUFFIX = "D";

    /** Active soft-delete flag value. */
    public static final String DEL_YN_ACTIVE = "N";

    /** Deleted soft-delete flag value. */
    public static final String DEL_YN_DELETED = "Y";

    /** Active status code. */
    public static final String STTS_ACTIVE = "A";

    /** Inactive status code. */
    public static final String STTS_INACTIVE = "I";

    /** i18n key prefix for {@code restricted.<lowercased_code>} display
     *  labels — used by the frontend to localize REVENUE / EXPENSES /
     *  INVESTMENT names. Backend ships the wire row with an
     *  {@code restricted: true} flag; the frontend resolves the label. */
    public static final String RESTRICTED_LABEL_KEY_PREFIX = "settings.chart_of_accounts.restricted.";
}
