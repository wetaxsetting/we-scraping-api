package com.kosign.wecms.domain.banking.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Query-contract guard for {@code AcctMstrRepository.clearBankCertSeq} — the bulk UPDATE that
 * drops an account's credential pointer when the credential is deleted.
 *
 * <p><b>Why a query-text guard rather than a {@code @DataJpaTest}:</b> this project ships no
 * Testcontainers and no embedded database (see {@code CLAUDE.md} §"Not currently in
 * build.gradle"), so the UPDATE cannot be executed and its affected-row count observed. The
 * properties that actually matter are static and assertable from the annotation, which is the
 * idiom the repo already uses ({@code DispatchClaimQueryContractTest},
 * {@code FirmFileDataExecutionResultQueryContractTest}).
 *
 * <p><b>What breaks without this.</b> Three separate regressions are silent otherwise:
 * <ol>
 *   <li><b>Dropping {@code @Modifying}</b> — Spring Data would refuse to execute the UPDATE at
 *       all, the linkage would stay dangling, and {@code BankCredentialServiceDeleteLinkageTest}
 *       would still pass because it stubs the call.</li>
 *   <li><b>Dropping the {@code biz_key} predicate</b> — the delete would unlink accounts across
 *       EVERY tenant, against the live JEX DB. This is the dangerous one; nothing else in the
 *       suite would notice.</li>
 *   <li><b>Widening past {@code bankCertSeq}</b> (e.g. matching on bank instead of seq) — the
 *       delete would unlink accounts that belong to a credential that still exists.</li>
 * </ol>
 *
 * @see com.kosign.wecms.domain.banking.bankcredentials.service.BankCredentialService#delete
 */
class ClearBankCertSeqQueryContractTest {

    private static Method clearMethod() throws NoSuchMethodException {
        return AcctMstrRepository.class.getMethod(
                "clearBankCertSeq", String.class, Long.class, String.class, String.class);
    }

    private static String queryText() throws NoSuchMethodException {
        Query q = clearMethod().getAnnotation(Query.class);
        assertThat(q).as("clearBankCertSeq must carry an explicit @Query").isNotNull();
        return collapseWhitespace(q.value());
    }

    /** Collapse every run of whitespace to a single space so the assertions below
     *  survive re-indentation of the JPQL text block. */
    private static String collapseWhitespace(String raw) {
        final StringBuilder sb = new StringBuilder(raw.length());
        boolean pendingSpace = false;
        for (int i = 0; i < raw.length(); i++) {
            final char c = raw.charAt(i);
            if (Character.isWhitespace(c)) {
                pendingSpace = sb.length() > 0;
                continue;
            }
            if (pendingSpace) {
                sb.append(String.valueOf((char) 32));
                pendingSpace = false;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    @Test
    @DisplayName("clearBankCertSeq is @Modifying — without it Spring Data never runs the UPDATE")
    void isModifying() throws Exception {
        assertThat(clearMethod().getAnnotation(Modifying.class))
                .as("clearBankCertSeq must be @Modifying; otherwise the linkage stays dangling "
                        + "and the delete silently does half its job")
                .isNotNull();
    }

    @Test
    @DisplayName("clearBankCertSeq returns the unlinked-row count so the caller can log it")
    void returnsAffectedRowCount() throws Exception {
        assertThat(clearMethod().getReturnType()).isEqualTo(int.class);
    }

    @Test
    @DisplayName("clearBankCertSeq is a single UPDATE that nulls BANK_CERT_SEQ")
    void updatesBankCertSeqToNull() throws Exception {
        String jpql = queryText().toLowerCase();
        assertThat(jpql).startsWith("update acctmstr");
        assertThat(jpql)
                .as("the pointer must be set to NULL — not to 0, not to another seq")
                .contains("set a.bankcertseq = null");
    }

    @Test
    @DisplayName("clearBankCertSeq is tenant-scoped — a missing biz_key predicate would unlink every tenant")
    void isTenantScoped() throws Exception {
        assertThat(queryText().toLowerCase())
                .as("BIZ_KEY is mandatory on every tenant-scoped query (CLAUDE.md § Repositories); "
                        + "without it, deleting one company's credential would unlink another "
                        + "company's accounts in the live JEX DB")
                .contains("a.bizkey = :bizkey");
    }

    @Test
    @DisplayName("clearBankCertSeq targets exactly the deleted credential's seq")
    void isScopedToTheDeletedSeq() throws Exception {
        assertThat(queryText().toLowerCase())
                .as("only accounts pointing at the deleted SEQ_NO may be unlinked")
                .contains("a.bankcertseq = :bankcertseq");
    }

    @Test
    @DisplayName("clearBankCertSeq stamps MOD_ID / MOD_DTM — bulk JPQL bypasses @PreUpdate")
    void stampsAuditColumns() throws Exception {
        String jpql = queryText().toLowerCase();
        assertThat(jpql).contains("a.modid = :userid");
        assertThat(jpql).contains("a.moddtm = :nowdtm");
    }
}
