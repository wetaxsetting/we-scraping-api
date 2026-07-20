package com.kosign.wecms.domain.payment.cardtransactions.service;

import com.kosign.wecms.domain.banking.entity.BankInfm;
import com.kosign.wecms.domain.banking.repository.BankInfmDetlRepository;
import com.kosign.wecms.domain.banking.repository.BankInfmRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Bank display-name resolver for the <b>Card Transactions feature only</b>.
 *
 * <p>Card screens show the <b>short</b> bank name ({@code "Shinhan VN"}) rather
 * than the long {@code bank_infm.rprs_bank_nm} ({@code "Shinhan(Vietnam)"}).
 * The short name is {@code bank_infm_detl.bank_detl_info_cont} where
 * {@code bank_detl_info_cd = '10'} and today is inside the row's
 * {@code [start_date, end_date]} window — the legacy
 * {@code wb_get_bank_nm(bank_cd, 'S')} source.
 *
 * <p><b>Deliberate divergence from legacy card IDOs</b> (PO-approved,
 * card-feature only, 2026-07-17): the legacy {@code CARD_MSTR_R001/R003/R004}
 * IDOs display the <em>long</em> name via {@code wb_get_bank_nm(BANK_CD)}. This
 * is a display-only preference — no data-migration impact. Other modules
 * (Account Inquiry, etc.) keep the long name via their own resolvers.
 *
 * <p>Fallback chain: short name → long name ({@code rprs_bank_nm}) → the raw
 * bank code, so a bank without a short-name row still renders something.
 */
@Component
@RequiredArgsConstructor
public class CardBankNameResolver {

    /** {@code bank_infm_detl} code for the short display name. */
    private static final String SHORT_NAME_INFO_CD = "10";
    private static final DateTimeFormatter DT_8 = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final BankInfmDetlRepository bankInfmDetlRepo;
    private final BankInfmRepository bankInfmRepo;

    /** Resolve the card-feature bank display name (short → long → code). */
    public String resolve(String bankCd) {
        if (bankCd == null || bankCd.isBlank()) return bankCd;

        String today = OffsetDateTime.now(ZoneOffset.UTC).format(DT_8);
        List<String> shortNames = bankInfmDetlRepo.findEffectiveContents(bankCd, SHORT_NAME_INFO_CD, today);
        if (!shortNames.isEmpty()) {
            String first = shortNames.get(0);
            if (first != null && !first.isBlank()) return first;
        }

        return bankInfmRepo.findById(bankCd)
                .map(BankInfm::getRprsBankNm)
                .filter(nm -> nm != null && !nm.isBlank())
                .orElse(bankCd);
    }
}
