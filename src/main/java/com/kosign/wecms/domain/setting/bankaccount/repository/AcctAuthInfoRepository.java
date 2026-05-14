package com.kosign.wecms.domain.setting.bankaccount.repository;

import com.kosign.wecms.domain.setting.bankaccount.entity.AcctAuthInfo;
import com.kosign.wecms.domain.setting.bankaccount.entity.AcctAuthInfoId;
import org.springframework.data.jpa.repository.JpaRepository;
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

    /** Active-only count for the bank-account list's per-row aggregate. */
    @Query("""
            SELECT COUNT(a) FROM AcctAuthInfo a
             WHERE a.bizKey   = :bizKey
               AND a.bankCd   = :bankCd
               AND a.acctNo   = :acctNo
               AND a.authType = :authType
               AND a.useYn    = 'Y'
            """)
    long countActiveByAccount(
            @Param("bizKey")   String bizKey,
            @Param("bankCd")   String bankCd,
            @Param("acctNo")   String acctNo,
            @Param("authType") String authType);

    /** Display name of the first active grantee for the row label
     *  ("<name> 외 N명" / "<name> + N more"). Ordered by REG_DTM
     *  (the earliest grantee) so the label is stable across requests.
     *  Returns {@code null} when no active permission exists. */
    @Query("""
            SELECT u.userNm
              FROM AcctAuthInfo a, com.kosign.wecms.domain.auth.entity.UserInfm u
             WHERE a.bizKey   = :bizKey
               AND a.bankCd   = :bankCd
               AND a.acctNo   = :acctNo
               AND a.authType = :authType
               AND a.useYn    = 'Y'
               AND u.userId   = a.userId
             ORDER BY a.regDtm ASC
            """)
    List<String> findFirstActiveUserName(
            @Param("bizKey")   String bizKey,
            @Param("bankCd")   String bankCd,
            @Param("acctNo")   String acctNo,
            @Param("authType") String authType);
}
