package com.kosign.wecms.domain.setting.contacts.repository;

import com.kosign.wecms.domain.setting.contacts.entity.TrcoAcctInfo;
import com.kosign.wecms.domain.setting.contacts.entity.TrcoAcctInfoId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

/**
 * Spring Data repository for {@link TrcoAcctInfo}. Tenant-scoped on
 * every read / write.
 */
public interface TrcoAcctInfoRepository
        extends JpaRepository<TrcoAcctInfo, TrcoAcctInfoId> {

    List<TrcoAcctInfo> findByBizKeyAndTrcoKey(String bizKey, String trcoKey);

    List<TrcoAcctInfo> findByBizKeyAndTrcoKeyIn(
            String bizKey, Collection<String> trcoKeys);

    /**
     * Tenant-scoped lookup of every {@code TRCO_ACCT_INFO} row that
     * matches a single {@code (BIZ_KEY, TRCO_KEY, ACCT_NO)} triple.
     * Returns a list because the legacy PK is
     * {@code (BIZ_KEY, TRCO_KEY, TRCO_ACCT_KEY)} &mdash; the same
     * account number may appear under multiple {@code TRCO_ACCT_KEY}
     * rows with different bank codes (one NAPAS, one CITAD). Callers
     * pick the row whose bank code matches the user's Excel input.
     *
     * <p>Mirrors legacy {@code PymnHelper.ckDepositNo} (lines 1138-1166)
     * &mdash; the query used by the payment-upload parser to verify
     * that the deposit account number entered against a contact in the
     * Excel template is actually registered under that contact in
     * {@code TRCO_ACCT_INFO}, and to read {@code FINC_MDA_OWNER_NM} for
     * the CITAD recipient-name cross-fill.
     */
    List<TrcoAcctInfo> findByBizKeyAndTrcoKeyAndAcctNo(
            String bizKey, String trcoKey, String acctNo);

    @Transactional
    long deleteByBizKeyAndTrcoKeyAndTrcoAcctKey(
            String bizKey, String trcoKey, String trcoAcctKey);
}
