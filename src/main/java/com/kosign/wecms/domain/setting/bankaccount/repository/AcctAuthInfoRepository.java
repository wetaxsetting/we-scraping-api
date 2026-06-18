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
