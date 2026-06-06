package com.kosign.wecms.domain.auth.repository;

import com.kosign.wecms.domain.auth.entity.UserBusiConnInfm;
import com.kosign.wecms.domain.auth.entity.UserBusiConnInfmId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserBusiConnInfmRepository extends JpaRepository<UserBusiConnInfm, UserBusiConnInfmId> {

    /**
     * Locate the single active link for a user within a specific tenant.
     * Used by /auth/login (finalize step, when the caller supplied a
     * biz_key) and by /auth/me (when the JWT already carries the
     * resolved biz_key). The BIZ_KEY here is the one resolved from the
     * link table or the JWT — never a wildcard, never a value the
     * frontend can spoof.
     */
    Optional<UserBusiConnInfm> findByUserIdAndBizKeyAndDelYn(
            String userId, String bizKey, String delYn);

    /**
     * Every active company link for a user across the local tenant set.
     * Drives the company-chooser branch of /auth/login: when this returns
     * exactly one row, the login auto-picks that company; when it returns
     * two or more, the response carries {@code companies_available} and
     * the frontend asks the user to choose.
     *
     * <p>The result must NOT be trusted as a tenant filter — the caller
     * still has to ensure the BIZ_KEY ultimately bound to the JWT exists
     * in this list, never echoing the frontend's payload directly into
     * the token.
     */
    List<UserBusiConnInfm> findAllByUserIdAndDelYn(String userId, String delYn);

    /**
     * Bulk lookup: every active link in the supplied {@code userIds} set
     * that belongs to the given tenant. Used by features that take a
     * list of approver / signer user ids from the caller (Approval Line,
     * future approval features) and need to confirm in a single round-
     * trip that every id has a current tenant link.
     *
     * <p>The caller compares the returned list size against the input
     * set size; missing ids are the validation failure (refactor codes
     * the failure via the consumer's domain-specific error code).
     */
    List<UserBusiConnInfm> findAllByUserIdInAndBizKeyAndDelYn(
            Collection<String> userIds, String bizKey, String delYn);

    /**
     * Active-user count for a tenant. Available for tenant-scoped
     * aggregates / display counts. Note: there is <b>no</b> plan cap on
     * user creation — neither legacy JEX nor the refactor enforces one
     * (see {@code UserManagementService.create}), so this is not a
     * creation gate.
     */
    long countByBizKeyAndDelYn(String bizKey, String delYn);

    /**
     * Tenant-user picker projection — mirrors the legacy
     * {@code comm_0192_001_r001} (Inquiry Approver) IDO at
     * {@code IDO.USER_BUSI_CONN_INFM_R004.xml:24-62}. Joins
     * {@code USER_INFM} on {@code USER_ID} so position +
     * department + name come from {@code USER_INFM} while
     * tenant scope + soft-delete + image flag come from
     * {@code USER_BUSI_CONN_INFM}.
     *
     * <p>Filters:
     * <ul>
     *   <li>{@code biz_key = :bizKey} (tenant scope)</li>
     *   <li>{@code del_yn = 'N'} (active links only)</li>
     *   <li>{@code LOWER(USER_NM) LIKE LOWER(CONCAT('%', :q, '%'))}
     *       when {@code :q} is non-empty &mdash; same semantic as
     *       legacy {@code ui.user_nm ilike '%' || ? || '%'}.</li>
     * </ul>
     *
     * <p>Order: {@code USER_NM ASC, USER_ID ASC} &mdash; the picker
     * is name-sorted, tie-broken on id for deterministic ordering
     * across rows that share a name.
     *
     * <p>Returns a {@link TenantUserProjection} interface
     * projection &mdash; Hibernate emits the SELECT clause with
     * exactly the seven columns the picker uses, avoiding the
     * cost of materialising whole entity graphs for what is
     * effectively a read-only view.
     */
    @Query(
            "SELECT u.userId       AS userId, "
                    + "u.userNm   AS userNm, "
                    + "u.posiNm   AS posiNm, "
                    + "u.deptNm   AS deptNm, "
                    + "c.userImg  AS userImg "
                    + "FROM UserBusiConnInfm c "
                    + "JOIN UserInfm u ON u.userId = c.userId "
                    + "WHERE c.bizKey = :bizKey "
                    + "  AND c.delYn  = 'N' "
                    + "  AND (:q IS NULL OR :q = '' "
                    + "       OR LOWER(u.userNm) LIKE LOWER(CONCAT('%', :q, '%'))) "
                    + "ORDER BY u.userNm ASC, u.userId ASC")
    List<TenantUserProjection> findTenantUserPicker(
            @Param("bizKey") String bizKey,
            @Param("q")      String q);

    /**
     * Read-only projection consumed by the {@code GET /api/v1/users}
     * controller. Hibernate proxies it from the JPQL alias names
     * declared in {@link #findTenantUserPicker}. {@code userImg} is
     * exposed so the service can decide whether to emit a
     * {@code user_img_url} string &mdash; the value itself never
     * reaches the wire.
     */
    interface TenantUserProjection {
        String getUserId();
        String getUserNm();
        String getPosiNm();
        String getDeptNm();
        String getUserImg();
    }

    /**
     * Full user list for the Settings &gt; Users management panel —
     * joins {@code USER_INFM} so a single round trip returns every
     * field the panel renders (email + phone + position + department +
     * avatar). Ordered by {@code REG_DTM DESC, USER_ID ASC} so the
     * newest user lands at the top of the list.
     *
     * <p>Distinct from {@link #findTenantUserPicker} — the picker is
     * a narrow approver-search projection; this one is the management
     * grid. Same join shape, more columns.
     */
    @Query(
            "SELECT u.userId   AS userId, "
                    + "u.userNm   AS userNm, "
                    + "u.eml      AS eml, "
                    + "u.clphNo   AS clphNo, "
                    + "u.posiNm   AS posiNm, "
                    + "u.deptNm   AS deptNm, "
                    + "c.userImg  AS userImg "
                    + "FROM UserBusiConnInfm c "
                    + "JOIN UserInfm u ON u.userId = c.userId "
                    + "WHERE c.bizKey = :bizKey "
                    + "  AND c.delYn  = 'N' "
                    + "ORDER BY c.regDtm DESC, u.userId ASC")
    List<TenantUserManagementProjection> findTenantUsersForManagement(
            @Param("bizKey") String bizKey);

    /**
     * Wider projection used by the Settings &gt; Users management
     * grid. Carries every column the panel renders so the controller
     * can build the response in one pass with no per-row second
     * lookup.
     */
    interface TenantUserManagementProjection {
        String getUserId();
        String getUserNm();
        String getEml();
        String getClphNo();
        String getPosiNm();
        String getDeptNm();
        String getUserImg();
    }
}
