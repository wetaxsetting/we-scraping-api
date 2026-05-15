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
     * Duplicate-{@code TRCO_TAX_ID} check &mdash; per-tenant unique
     * contract. Mirrors legacy {@code TrcoHelper.validateTrcoTaxDuplicate}.
     */
    boolean existsByBizKeyAndTrcoTaxId(String bizKey, String trcoTaxId);

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
