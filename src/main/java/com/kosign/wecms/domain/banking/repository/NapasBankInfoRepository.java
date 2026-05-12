package com.kosign.wecms.domain.banking.repository;

import com.kosign.wecms.domain.banking.entity.NapasBankInfo;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Read-only access to the global {@code NAPAS_BANK_INFO} dictionary.
 * Used by the firm-banking-settings Create dialog's Withdrawal
 * Account autocomplete to bridge the public NAPAS code (sent by the
 * client as {@code out_bank_cd}) to the internal {@code BANK_CD}
 * used by {@code ACCT_MSTR} — mirrors JEX
 * {@code comm_1004_001_act.jsp:56-66}.
 *
 * <p>Spring Data resolves PK lookup via the inherited
 * {@code findById(napasBankCd)}; no extra finder is needed today.
 */
public interface NapasBankInfoRepository extends JpaRepository<NapasBankInfo, String> {
}
