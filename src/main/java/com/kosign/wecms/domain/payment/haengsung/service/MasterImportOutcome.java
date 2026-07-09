package com.kosign.wecms.domain.payment.haengsung.service;

/**
 * Result of importing one HAENGSUNG Oracle {@code TB_TRNS_MAST} master into
 * WeCMS. Returned by {@link ErpPaymentRefreshMasterImporter#importMaster}.
 */
public record MasterImportOutcome(Status status, int detailsImported) {

    public enum Status {
        /** Summary + details inserted/updated; eligible for Oracle receive write-back. */
        IMPORTED,
        /** Existing summary already carries an {@code APPR_SEQ_NO} — whole master left untouched. */
        SKIPPED_APPROVAL_LOCK
    }

    public static MasterImportOutcome imported(int details) {
        return new MasterImportOutcome(Status.IMPORTED, details);
    }

    public static MasterImportOutcome approvalLocked() {
        return new MasterImportOutcome(Status.SKIPPED_APPROVAL_LOCK, 0);
    }
}
