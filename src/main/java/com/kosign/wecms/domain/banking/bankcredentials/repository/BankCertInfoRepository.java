package com.kosign.wecms.domain.banking.bankcredentials.repository;

import com.kosign.wecms.domain.banking.bankcredentials.entity.BankCertInfo;
import com.kosign.wecms.domain.banking.bankcredentials.entity.BankCertInfoId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface BankCertInfoRepository extends JpaRepository<BankCertInfo, BankCertInfoId> {

    List<BankCertInfo> findByBizKeyOrderBySeqNoAsc(String bizKey);

    Optional<BankCertInfo> findByBizKeyAndSeqNo(String bizKey, BigDecimal seqNo);

    /**
     * Find-or-create dedup key — mirrors the legacy
     * {@code fnsh_0101_001_c001:166-194} lookup that reuses an existing
     * {@code BANK_CERT_INFO} row instead of minting a duplicate. A bank
     * credential is uniquely identified within a tenant by
     * {@code (BANK_CD, CERT_INFO_CON, NXISAS_GB_SEQ_NO)}; re-registering
     * the same login (requester then approver, or a retried save) must
     * reuse this row so the same {@code CERT_INFO_CON} never renders
     * twice in the per-{@code BANK_CD} credentials popup.
     *
     * <p>{@code firstBy} + {@code OrderBySeqNoAsc} makes the result
     * deterministic if legacy data ever carried a pre-existing duplicate.
     */
    Optional<BankCertInfo> findFirstByBizKeyAndBankCdAndCertInfoConAndNxisasGbSeqNoOrderBySeqNoAsc(
            String bizKey, String bankCd, String certInfoCon, BigDecimal nxisasGbSeqNo);

    /** Next SEQ_NO for the tenant. Returns 0 if no rows exist, so the
     *  caller increments to 1. Bound to {@code BIZ_KEY} — the tenant's
     *  own seq space. */
    @Query("SELECT COALESCE(MAX(b.seqNo), 0) FROM BankCertInfo b WHERE b.bizKey = :bizKey")
    BigDecimal findMaxSeqNo(String bizKey);

    /** Count credentials at one bank within the tenant. Used by the
     *  Disconnect-credential path to decide whether to cascade-delete
     *  the registered {@code ACCT_MSTR} rows for that bank (only when
     *  this was the last remaining credential — preserves accounts
     *  linked by sibling credentials). */
    long countByBizKeyAndBankCd(String bizKey, String bankCd);

    /**
     * Tenant-scoped set of {@code BANK_CD} values that have at least one
     * non-blank credential row registered. Drives the Account Inquiry
     * "disconnected bank" flag: a row is connected when its
     * {@code ACCT_MSTR.BANK_CD} appears in this set, regardless of
     * whether {@code ACCT_MSTR.BANK_CERT_SEQ} happens to point at a
     * specific cert row.
     *
     * <p>Per-bank rather than per-FK is intentional. The Login
     * Credentials popup configures credentials at the bank level — once
     * a bank has a credential, every account at that bank is
     * connectable through it. The legacy {@code BANK_CERT_SEQ} FK on
     * {@code ACCT_MSTR} goes stale whenever the user adds/deletes
     * credentials (the new credential mints a fresh {@code SEQ_NO} but
     * existing accounts keep their old reference), so resolving by FK
     * produces false-negatives. Per-bank resolution sidesteps the
     * staleness entirely.
     *
     * <p>The legacy disconnected-bank check at
     * {@code fnsh_0005_001.ts:711} reads {@code CERT_INFO_CON} and
     * {@code CERT_INFO_DV} from {@code ACCT_MSTR} directly, but the
     * legacy IDO never selects those columns from that table, so the
     * legacy frontend sees them as {@code null} on every row and the
     * check is always-true. The refactor restores the intended
     * semantics by joining {@code BANK_CERT_INFO} via {@code BANK_CD}.
     */
    @Query("SELECT DISTINCT b.bankCd FROM BankCertInfo b " +
           "WHERE b.bizKey = :bizKey " +
           "  AND b.bankCd IS NOT NULL AND TRIM(b.bankCd) <> '' " +
           "  AND b.certInfoCon IS NOT NULL AND TRIM(b.certInfoCon) <> '' " +
           "  AND b.certInfoDv  IS NOT NULL AND TRIM(b.certInfoDv)  <> ''")
    List<String> findConnectedBankCds(String bizKey);

    /** Pick the first registered credential at one bank (legacy parity:
     *  multiple accounts at one bank share one credential row, so
     *  resolving by {@code (BIZ_KEY, BANK_CD)} is enough for the sync
     *  flow). Returns the lowest {@code SEQ_NO} so behaviour is
     *  deterministic when more than one credential exists at the same
     *  bank (rare; happens when a user re-registers after a password
     *  change before deleting the old row). */
    Optional<BankCertInfo> findFirstByBizKeyAndBankCdOrderBySeqNoAsc(String bizKey, String bankCd);
}
