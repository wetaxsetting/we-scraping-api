package com.kosign.wecms.domain.banking.repository;

import com.kosign.wecms.domain.banking.entity.NapasBankInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Read-only access to the global {@code NAPAS_BANK_INFO} dictionary.
 * Used by:
 * <ul>
 *   <li>The firm-banking-settings Create dialog's Withdrawal Account
 *       autocomplete to bridge the public NAPAS code (sent by the
 *       client as {@code out_bank_cd}) to the internal
 *       {@code BANK_CD} used by {@code ACCT_MSTR} — mirrors JEX
 *       {@code comm_1004_001_act.jsp:56-66}.</li>
 *   <li>Own Account Transfer's target-accounts projection: enriches
 *       each own-account row with the NAPAS code the engine needs
 *       for cross-bank routing.</li>
 * </ul>
 */
public interface NapasBankInfoRepository extends JpaRepository<NapasBankInfo, String> {

    /** Reverse lookup: internal {@code BANK_CD} → NAPAS dictionary
     *  row. Used to enrich Own Account Transfer's target-accounts
     *  response with the engine-side routing code. The dictionary is
     *  small (a handful of rows) so a derived method is fine; if it
     *  ever grows beyond a couple of dozen rows, swap to a cached
     *  Map.
     *
     *  <p><b>{@code BANK_CD} is NOT unique</b> — a bank with several
     *  NAPAS-registered branches carries one row per branch (IBK VN
     *  {@code 11704003} → {@code 970455} Hanoi / {@code 970456} HCM;
     *  likewise KB {@code 11704013}, KEB Hana {@code 11704080},
     *  VPBank {@code 11704102}). The lowest {@code NAPAS_BANK_CD}
     *  wins, mirroring legacy {@code IDO.ACCT_MSTR_R008.xml}
     *  (the IDO behind {@code trns_0001_005_r001}) which resolves the
     *  same column with
     *  {@code ORDER BY NBI.NAPAS_BANK_CD ASC LIMIT 1}. A plain
     *  {@code findByBankCd} returning {@code Optional} throws
     *  {@code NonUniqueResultException} for those four banks. */
    Optional<NapasBankInfo> findFirstByBankCdOrderByNapasBankCdAsc(String bankCd);
}
