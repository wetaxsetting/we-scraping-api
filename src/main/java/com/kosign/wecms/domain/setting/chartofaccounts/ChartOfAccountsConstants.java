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

    /** Maximum hierarchy depth permitted. PO requirement baked into
     *  legacy {@code IDO.CHART_ACCT_INFM_R002.xml:62-99}'s {@code
     *  CHART_ACCT_LEVEL} CASE. Refactor enforces at write time too. */
    public static final int MAX_HIERARCHY_DEPTH = 4;

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
