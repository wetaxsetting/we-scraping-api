package com.kosign.wecms.domain.payment.dispatch.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Query-contract guard for the 01302 per-line result lookup (audit <b>H-1</b>).
 *
 * <p><b>Why a query-text guard rather than a {@code @DataJpaTest}:</b> the project ships no
 * Testcontainers and no embedded database (see the "Not currently in build.gradle" note in
 * {@code CLAUDE.md}), so no test in this suite ever executes JPQL — a malformed or semantically
 * wrong query would only surface at application startup. The property that matters here is
 * nonetheless static and assertable from the annotation: the detail lookup must match
 * {@code FIRM_FILE_NM} with the leading 2 characters stripped, never by equality. Same idiom as
 * {@link DispatchClaimQueryContractTest}.
 *
 * <p><b>What breaks without this.</b> {@code TB_FIRM_FILE_DATA} stores the name WeCMS <em>sent</em>
 * ({@code S_…}); the 01302 callback carries the name the bank <em>returned</em> ({@code R_…}). Legacy
 * bridges the two by stripping the prefix on both sides of the join
 * ({@code IDO.TB_FIRM_FILE_DATA_U001.xml:34}). If this query is ever "simplified" back to an
 * equality match, the lookup silently returns zero rows on every real callback: no
 * {@code PROC_RSLT_DIV} / {@code RS_CD} reaches {@code TB_FIRM_FILE_DATA}, none reaches
 * {@code TB_PAY_FILE_DATA} (its write is chained off this lookup via {@code ORG_SEQ}), and the file
 * still advances to {@code PROC_STEP='3'} — so the Payment Result screen shows blank per-line
 * outcomes with nothing anywhere reporting a failure.
 */
class FirmFileDataExecutionResultQueryContractTest {

    private static String queryText() throws NoSuchMethodException {
        Method m = TbFirmFileDataRepository.class.getMethod(
                "findForExecutionResult", String.class, String.class, String.class);
        Query q = m.getAnnotation(Query.class);
        assertThat(q)
                .as("findForExecutionResult must be an explicit @Query — a derived method name "
                        + "cannot express substring(FIRM_FILE_NM, 3)")
                .isNotNull();
        return q.value().replaceAll("\\s+", " ");
    }

    @Test
    @DisplayName("H-1: the 01302 detail lookup strips the leading 2 chars on BOTH sides of the name match")
    void matchesFirmFileNmWithPrefixStripped() throws Exception {
        String jpql = queryText();

        // Stored side (S_) and callback side (R_) are BOTH stripped — legacy U001:34.
        assertThat(jpql)
                .as("must strip the stored FIRM_FILE_NM prefix")
                .contains("substring(d.firmFileNm, 3)");
        assertThat(jpql)
                .as("must strip the callback filename prefix too — stripping only one side "
                        + "still compares 'S_…' against '…' and matches nothing")
                .contains("substring(:fileNm, 3)");

        // And must NOT fall back to equality on the full name (the H-1 defect).
        assertThat(jpql)
                .as("an exact FIRM_FILE_NM equality is the H-1 defect — it returns zero rows "
                        + "for every real R_-prefixed callback")
                .doesNotContain("d.firmFileNm = :fileNm")
                .doesNotContain("d.firmFileNm = :firmFileNm");
    }

    @Test
    @DisplayName("H-1: scoped by TRAN_DT + FILE_SEQ, mirroring legacy U001's A-side join keys")
    void isScopedByLegacyJoinKeys() throws Exception {
        String jpql = queryText();

        assertThat(jpql).contains("d.tranDt = :tranDt");
        // FILE_SEQ is the 3-digit round; legacy joins A.FILE_SEQ = B.FILE_SEQ.
        assertThat(jpql).contains("d.fileSeq = :fileSeq");
        // Deterministic line order — the caller indexes by SEQ, and callers downstream of an
        // unordered result would be order-dependent for no reason.
        assertThat(jpql).contains("order by d.seq asc");
    }

    @Test
    @DisplayName("The stranded sweep excludes a BLANK PROC_DTTM explicitly, not via the retention floor")
    void strandedSweepExcludesBlankProcDttm() throws Exception {
        Method m = TbFirmFileMstRepository.class.getMethod(
                "findStrandedBulkExecutionFiles", String.class, String.class, String.class, String.class,
                org.springframework.data.domain.Pageable.class);
        String jpql = m.getAnnotation(Query.class).value().replaceAll("\\s+", " ");

        // A blank PROC_DTTM sorts before every 14-digit timestamp, so it satisfies `< cutoff`. Today it
        // is excluded only because it also fails `>= floor` — accidental protection. JEX DEV holds 2
        // such rows. The repository Javadoc contemplates dropping the floor for index reasons; the
        // moment that happens, every blank row would alert on every tick, forever.
        assertThat(jpql)
                .as("blank PROC_DTTM must be excluded on its own terms, not as a side effect of the floor")
                .contains("f.procDttm <> ''");
        assertThat(jpql).contains("f.procDttm IS NOT NULL");

        // Prove the coupling the explicit predicate removes: with the floor gone, '' still passes
        // the null check and the cutoff comparison.
        String blank = "";
        assertThat(blank).isNotNull();
        assertThat(blank.compareTo("20260729174500") < 0)
                .as("'' < cutoff lexicographically — this is why the null check alone is not enough")
                .isTrue();
    }

    @Test
    @DisplayName("The exact-name finder still exists but is documented as sent-name-only")
    void exactNameFinderIsStillAvailableForSentNameCallers() throws Exception {
        // Kept deliberately: it is correct for any caller that already holds the SENT name.
        // Its Javadoc carries the do-not-use-with-callback-names warning; this assertion just
        // pins that removing/renaming it is a conscious decision, not an accident.
        Method exact = TbFirmFileDataRepository.class.getMethod(
                "findByTranDtAndFileSeqNoAndFirmFileNmOrderBySeqAsc",
                String.class, String.class, String.class);
        assertThat(exact.getAnnotation(Query.class))
                .as("derived-name finder — must stay derived, no @Query")
                .isNull();
    }
}
