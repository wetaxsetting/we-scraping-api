package com.kosign.wecms.domain.setting.bankaccount.repository;

import com.kosign.wecms.domain.setting.bankaccount.entity.AcctAuthInfo;
import com.kosign.wecms.domain.setting.bankaccount.entity.AcctAuthInfoId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Per-account permission roster. Tenant-scoped via every method —
 * {@code BIZ_KEY} is the leading filter on every query, never a wildcard.
 *
 * <p>Two read flows:
 * <ul>
 *   <li>The bank-account list endpoint counts active permissions per
 *       account (per {@code AUTH_TYPE}) via
 *       {@link #countActiveByAccount(String, String, String, String)}
 *       and reads the first selected user's name via
 *       {@link #findFirstActiveUserName(String, String, String, String)}.
 *       Both filter {@code USE_YN='Y'} so soft-revoked rows do not
 *       inflate the "+ N more" labels.</li>
 *   <li>The Permission dialog GET loads the full toggleable roster
 *       (every {@code USER_INFM} row in the tenant) via the
 *       {@code BankAccountPermissionRepository} projection; the active-
 *       state flag for each user comes from
 *       {@link #findAllByAccount(String, String, String, String)} — one
 *       row per existing permission, regardless of {@code USE_YN}.</li>
 * </ul>
 */
public interface AcctAuthInfoRepository
        extends JpaRepository<AcctAuthInfo, AcctAuthInfoId> {

    /** All permission rows (active + soft-revoked) for one (account, kind).
     *  Used by the Save path to compute the grant/revoke diff. */
    @Query("""
            SELECT a FROM AcctAuthInfo a
             WHERE a.bizKey   = :bizKey
               AND a.bankCd   = :bankCd
               AND a.acctNo   = :acctNo
               AND a.authType = :authType
            """)
    List<AcctAuthInfo> findAllByAccount(
            @Param("bizKey")   String bizKey,
            @Param("bankCd")   String bankCd,
            @Param("acctNo")   String acctNo,
            @Param("authType") String authType);

    /** Active-only count for the bank-account list's per-row aggregate.
     *
     *  <p>Excludes admins — mirrors legacy {@code IDO.ACCT_MSTR_R012.xml}
     *  {@code REC_1}/{@code REC_2} ({@code NOT EXISTS BUSI_ADMN_INFM}). Admins
     *  are implicitly authorized and never counted as explicit grantees in the
     *  grid summary, consistent with the permission popup roster. */
    @Query("""
            SELECT COUNT(a) FROM AcctAuthInfo a
             WHERE a.bizKey   = :bizKey
               AND a.bankCd   = :bankCd
               AND a.acctNo   = :acctNo
               AND a.authType = :authType
               AND a.useYn    = 'Y'
               AND NOT EXISTS (
                   SELECT 1 FROM com.kosign.wecms.domain.auth.entity.BusiAdmnInfm b
                    WHERE b.bizKey = a.bizKey AND b.userId = a.userId)
            """)
    long countActiveByAccount(
            @Param("bizKey")   String bizKey,
            @Param("bankCd")   String bankCd,
            @Param("acctNo")   String acctNo,
            @Param("authType") String authType);

    /** Display name of the first active grantee for the row label
     *  ("<name> 외 N명" / "<name> + N more"). Ordered by REG_DTM
     *  (the earliest grantee) so the label is stable across requests.
     *  Returns {@code null} when no active permission exists.
     *
     *  <p>Excludes admins — same {@code NOT EXISTS BUSI_ADMN_INFM} clause as
     *  {@link #countActiveByAccount} so an admin-only account falls back to the
     *  placeholder text (legacy {@code REC_1}/{@code REC_2} parity). */
    @Query("""
            SELECT u.userNm
              FROM AcctAuthInfo a, com.kosign.wecms.domain.auth.entity.UserInfm u
             WHERE a.bizKey   = :bizKey
               AND a.bankCd   = :bankCd
               AND a.acctNo   = :acctNo
               AND a.authType = :authType
               AND a.useYn    = 'Y'
               AND u.userId   = a.userId
               AND NOT EXISTS (
                   SELECT 1 FROM com.kosign.wecms.domain.auth.entity.BusiAdmnInfm b
                    WHERE b.bizKey = a.bizKey AND b.userId = a.userId)
             ORDER BY a.regDtm ASC
            """)
    List<String> findFirstActiveUserName(
            @Param("bizKey")   String bizKey,
            @Param("bankCd")   String bankCd,
            @Param("acctNo")   String acctNo,
            @Param("authType") String authType);

    /** Batched per-(account, auth_type) active grantee COUNT for the whole
     *  tenant — the list-endpoint aggregate in one query instead of a
     *  per-row {@link #countActiveByAccount}. Identical active + non-admin
     *  filter; grouped so each row's count equals the per-account count. */
    @Query("""
            SELECT a.bankCd AS bankCd, a.acctNo AS acctNo, a.authType AS authType, COUNT(a) AS cnt
              FROM AcctAuthInfo a
             WHERE a.bizKey = :bizKey
               AND a.useYn  = 'Y'
               AND NOT EXISTS (
                   SELECT 1 FROM com.kosign.wecms.domain.auth.entity.BusiAdmnInfm b
                    WHERE b.bizKey = a.bizKey AND b.userId = a.userId)
             GROUP BY a.bankCd, a.acctNo, a.authType
            """)
    List<PermissionCount> countActiveByTenant(@Param("bizKey") String bizKey);

    /** Batched active non-admin grantee display names for the whole tenant,
     *  ordered so the FIRST row per (account, auth_type) is the earliest-
     *  REG_DTM grantee — the head name for the list label. Same join + filter
     *  as {@link #findFirstActiveUserName}; the extra leading ORDER BY keys
     *  only group the rows and do not change the per-group earliest-REG_DTM
     *  pick. */
    @Query("""
            SELECT a.bankCd AS bankCd, a.acctNo AS acctNo, a.authType AS authType, u.userNm AS userNm
              FROM AcctAuthInfo a, com.kosign.wecms.domain.auth.entity.UserInfm u
             WHERE a.bizKey = :bizKey
               AND a.useYn  = 'Y'
               AND u.userId = a.userId
               AND NOT EXISTS (
                   SELECT 1 FROM com.kosign.wecms.domain.auth.entity.BusiAdmnInfm b
                    WHERE b.bizKey = a.bizKey AND b.userId = a.userId)
             ORDER BY a.bankCd ASC, a.acctNo ASC, a.authType ASC, a.regDtm ASC
            """)
    List<PermissionFirstName> findActiveGranteesByTenant(@Param("bizKey") String bizKey);

    /** Projection — one row per (account, auth_type) with the active count. */
    interface PermissionCount {
        String getBankCd();
        String getAcctNo();
        String getAuthType();
        long getCnt();
    }

    /** Projection — active grantee names, earliest REG_DTM first per group. */
    interface PermissionFirstName {
        String getBankCd();
        String getAcctNo();
        String getAuthType();
        String getUserNm();
    }

    /** All active permission rows the user holds inside the tenant
     *  (any account, any AUTH_TYPE) — used by Own Account Transfer to
     *  project source-account authority in a single round-trip instead
     *  of per-account N+1 lookups. */
    List<AcctAuthInfo> findByBizKeyAndUserIdAndUseYn(
            String bizKey, String userId, String useYn);

    /** Per-account authority existence check — used by the Transaction
     *  History {@code AccountAccessGuard} (audit finding #29) to enforce
     *  that a non-admin may only read/act on accounts they hold an active
     *  grant for. Constrained on {@code BIZ_KEY} first (tenant scope). */
    boolean existsByBizKeyAndUserIdAndBankCdAndAcctNoAndAuthTypeAndUseYn(
            String bizKey, String userId, String bankCd, String acctNo, String authType, String useYn);

    /** Soft-revoke every active permission a user holds across all
     *  accounts in the tenant. Called when Settings &gt; Users unlinks
     *  the user so a removed member cannot retain account-level
     *  authority that banking/payment authorization paths still read.
     *  Mirrors legacy {@code comm_0190_003_d001} →
     *  {@code AcctAuthInfoDAO.revokePermissionsByBizKeyAndUserId}
     *  (IDO {@code AcctAuthInfo_U002}): set {@code USE_YN='N'} plus
     *  {@code MOD_ID}/{@code MOD_DTM}, never a hard delete, so audit
     *  history survives. Returns the number of rows revoked. */
    @Modifying
    @Query("""
            UPDATE AcctAuthInfo a
               SET a.useYn = 'N', a.modId = :modId, a.modDtm = :modDtm
             WHERE a.bizKey = :bizKey
               AND a.userId = :userId
               AND a.useYn  = 'Y'
            """)
    int revokeAllByBizKeyAndUserId(
            @Param("bizKey") String bizKey,
            @Param("userId") String userId,
            @Param("modId")  String modId,
            @Param("modDtm") String modDtm);
}
