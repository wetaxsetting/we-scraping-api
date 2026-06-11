package com.kosign.wecms.domain.setting.contacts.repository;

import com.kosign.wecms.domain.setting.contacts.entity.TrcoInfm;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for {@link TrcoInfm}.
 *
 * <p><b>Tenant scoping</b>: every method that returns rows constrains
 * on {@code BIZ_KEY}. Even though the entity's PK is global
 * ({@code TRCO_KEY} alone), the API contract scopes every read /
 * write to the caller's tenant via
 * {@code TenantContext.currentBizKey()}; cross-tenant lookups by
 * {@code TRCO_KEY} alone are NOT exposed.
 *
 * <p>The {@link #search} query mirrors the legacy
 * {@code comm_0184_001_r001_act.jsp:38-128} dynamic WHERE clause:
 * managed-only ({@code HAND_TRCO_KEY IS NOT NULL}), non-trashed
 * ({@code TRCO_STTS} null or != {@code 'C'}), optional {@code TRCO_TYPE}
 * filter, optional ILIKE keyword across {@code TRCO_CMNM} /
 * {@code TRCO_CMENM} / {@code HAND_TRCO_KEY}. Group-filter
 * ({@code GRP_CD}) is intentionally NOT supported &mdash; OQ-1
 * resolved 2026-05-12.
 */
public interface TrcoInfmRepository extends JpaRepository<TrcoInfm, String> {

    /**
     * Tenant-scoped find. Mirrors the API's "wrong tenant collapses to
     * not-found" pattern &mdash; callers translate the empty Optional to
     * {@code WBE_CONTACTS_NOT_FOUND}.
     */
    Optional<TrcoInfm> findByTrcoKeyAndBizKey(String trcoKey, String bizKey);

    /**
     * Batch-fetch active contacts by a list of user-supplied beneficiary
     * codes ({@code HAND_TRCO_KEY}). Used by the overseas batch-payment
     * Excel preview to resolve all unique codes in one round-trip.
     * Silently omits codes that don't exist or are soft-deleted.
     */
    @Query("""
        SELECT t FROM TrcoInfm t
        WHERE t.bizKey = :bizKey
          AND t.handTrcoKey IN :handTrcoKeys
          AND (t.trcoStts IS NULL OR t.trcoStts <> 'C')
        """)
    List<TrcoInfm> findByBeneficiaryCodes(
            @Param("bizKey") String bizKey,
            @Param("handTrcoKeys") Collection<String> handTrcoKeys);

    /**
     * Duplicate-{@code HAND_TRCO_KEY} check &mdash; per-tenant unique
     * contract enforced in {@code ContactsValidator}. Mirrors legacy
     * {@code TrcoHelper.validateTrcoHandCdDuplicate}.
     */
    boolean existsByBizKeyAndHandTrcoKey(String bizKey, String handTrcoKey);

    /**
     * Tenant-scoped lookup by {@code HAND_TRCO_KEY} (the user-facing
     * contact code that appears on the Vendor / Expense / Payroll
     * Excel templates). Excludes soft-deleted rows
     * ({@code TRCO_STTS = 'C'}).
     *
     * <p>Mirrors legacy {@code PymnHelper.ckContactCode} (lines
     * 1183-1227) — the query used by the payment-upload parser to
     * resolve each line's recipient against the tenant's Contacts
     * master. Used to populate {@code TRCO_KEY} / {@code TRCO_CMNM} /
     * {@code TRCO_CMENM} on each parsed line.
     */
    @Query("""
        SELECT t FROM TrcoInfm t
        WHERE t.bizKey = :bizKey
          AND t.handTrcoKey = :handTrcoKey
          AND (t.trcoStts IS NULL OR t.trcoStts <> 'C')
        """)
    Optional<TrcoInfm> findActiveByBizKeyAndHandTrcoKey(
            @Param("bizKey") String bizKey,
            @Param("handTrcoKey") String handTrcoKey);

    /**
     * Tenant-scoped lookup of an <em>overseas</em> contact by
     * {@code HAND_TRCO_KEY}. Mirrors legacy
     * {@code pymn_0007_003_r001_act.jsp:128-134} which filters on
     * {@code biz_key + hand_trco_key + trco_type = 'O' +
     * (trco_stts <> 'C' OR NULL)} before reading the overseas
     * beneficiary detail row via {@code TRCO_OVRS_INFM}.
     *
     * <p>Distinct from {@link #findActiveByBizKeyAndHandTrcoKey} (used by
     * Vendor / Expense / Payroll) — that one accepts any
     * {@code TRCO_TYPE}; overseas-batch upload must reject Local-type
     * contacts so a mis-typed code never resolves to a domestic
     * recipient.
     */
    @Query("""
        SELECT t FROM TrcoInfm t
        WHERE t.bizKey = :bizKey
          AND t.handTrcoKey = :handTrcoKey
          AND t.trcoType = 'O'
          AND (t.trcoStts IS NULL OR t.trcoStts <> 'C')
        """)
    Optional<TrcoInfm> findActiveOverseasByBizKeyAndHandTrcoKey(
            @Param("bizKey") String bizKey,
            @Param("handTrcoKey") String handTrcoKey);

    /**
     * Duplicate-{@code TRCO_TAX_ID} check &mdash; per-tenant unique
     * contract. Mirrors legacy {@code TrcoHelper.validateTrcoTaxDuplicate}.
     */
    boolean existsByBizKeyAndTrcoTaxId(String bizKey, String trcoTaxId);

    /**
     * e-Invoice counterparty auto-registration lookup. Mirrors legacy
     * {@code nxisas_einvoice_c002_act.jsp:340-346}: a contact is matched
     * (and reused, never re-created) on the
     * {@code (BIZ_KEY, TRCO_TAX_ID, TRSC_CCY_CD)} triple. {@code findFirst}
     * because DEV does not constrain that triple unique; the first match
     * wins, exactly as the legacy {@code findByDynamic} does.
     */
    Optional<TrcoInfm> findFirstByBizKeyAndTrcoTaxIdAndTrscCcyCd(
            String bizKey, String trcoTaxId, String trscCcyCd);

    /**
     * Nickname-collision guard for the auto-registrar. The
     * {@code trco_infm_uk01} UNIQUE({@code BIZ_KEY}, {@code TRCO_NICK_NM})
     * constraint forbids a duplicate nickname within a tenant; the
     * registrar appends a tax-id (or key) suffix until this returns
     * {@code false}. Mirrors the intent of legacy
     * {@code TrcoHelper.isDuplicatedTrcoNickNm} (the
     * {@code SYS_TRCO_INFM_DIS_NM} system-display-name normalisation is
     * out of refactor scope &mdash; that table is not in the PROD active list).
     */
    boolean existsByBizKeyAndTrcoNickNm(String bizKey, String trcoNickNm);

    /**
     * Bulk-exists check for {@code HAND_TRCO_KEY} &mdash; returns the
     * subset of {@code keys} that already point to an <em>active</em>
     * (non-trashed) contact in the tenant. Mirrors legacy
     * {@code comm_0184_007_r001_act.jsp:203-217 getDynamic0}, which
     * filters {@code trco_stts <> 'C'} so a code reused after trash is
     * permitted.
     *
     * <p>Used by the Excel-upload preflight endpoint so the preview
     * table can flag DB duplicates before the user clicks Upload.
     */
    @Query("""
        SELECT t.handTrcoKey FROM TrcoInfm t
        WHERE t.bizKey = :bizKey
          AND t.handTrcoKey IN :keys
          AND (t.trcoStts IS NULL OR t.trcoStts <> 'C')
        """)
    List<String> findExistingActiveHandTrcoKeys(
            @Param("bizKey") String bizKey,
            @Param("keys") Collection<String> keys);

    /**
     * Bulk-exists check for {@code TRCO_TAX_ID} &mdash; returns the
     * subset of {@code taxIds} already in use by an active contact in
     * the tenant. Mirrors legacy
     * {@code comm_0184_007_r001_act.jsp:219-233 getDynamic1}.
     */
    @Query("""
        SELECT t.trcoTaxId FROM TrcoInfm t
        WHERE t.bizKey = :bizKey
          AND t.trcoTaxId IS NOT NULL
          AND t.trcoTaxId IN :taxIds
          AND (t.trcoStts IS NULL OR t.trcoStts <> 'C')
        """)
    List<String> findExistingActiveTrcoTaxIds(
            @Param("bizKey") String bizKey,
            @Param("taxIds") Collection<String> taxIds);

    /**
     * Paged + filtered list. Returns only managed
     * ({@code HAND_TRCO_KEY IS NOT NULL}) non-trashed
     * ({@code TRCO_STTS} null or != {@code 'C'}) rows for the tenant.
     *
     * @param bizKey     tenant scope &mdash; required, never wildcarded
     * @param trcoType   {@code 'L'} / {@code 'O'} or {@code null} (no filter)
     * @param keyword    lower-case ILIKE pattern with the {@code %&hellip;%}
     *                   wrapper already applied by the caller (uses
     *                   {@code !} as the escape char per the rest of
     *                   the codebase), or {@code null} for no filter
     */
    @Query("""
        SELECT t FROM TrcoInfm t
        WHERE t.bizKey = :bizKey
          AND t.handTrcoKey IS NOT NULL
          AND (t.trcoStts IS NULL OR t.trcoStts <> 'C')
          AND (:trcoType IS NULL OR t.trcoType = :trcoType)
          AND (:keyword IS NULL OR
               LOWER(t.trcoCmnm)    LIKE :keyword ESCAPE '!' OR
               LOWER(t.trcoCmenm)   LIKE :keyword ESCAPE '!' OR
               LOWER(t.handTrcoKey) LIKE :keyword ESCAPE '!')
        """)
    Page<TrcoInfm> search(
            @Param("bizKey") String bizKey,
            @Param("trcoType") String trcoType,
            @Param("keyword") String keyword,
            Pageable pageable);
}
