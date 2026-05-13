package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.dto.ExchangeRateBankItem;
import com.kosign.wecms.domain.banking.dto.ExchangeRateListResponse;
import com.kosign.wecms.domain.banking.dto.ExchangeRateRow;
import com.kosign.wecms.domain.banking.entity.BankExchRateInfm;
import com.kosign.wecms.domain.banking.repository.BankExchRateInfmRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Exchange Rate Inquiry — read path.
 *
 * <p>Data is written by batch {@code EXCH_0001_001} (Coocon Shinhan API) and
 * read here for the frontend Exchange Rate Inquiry page
 * ({@code ExchangeRateInquiryPage.tsx}).</p>
 *
 * <p>JEX reference: {@code bcs.jex.wabooks.exch.EXCH_0001_001} (write path).
 * No JEX view service exists for the read path — the page consumes the stored
 * rows directly via this endpoint.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExchangeRateService {

    /** Shinhan Bank code — the only source currently populated by the batch. */
    static final String DEFAULT_BANK_CD = "11704088";

    /** Vietnam timezone — matches the calendar day the Coocon batch writes for REF_DATE. */
    private static final ZoneId VIETNAM_TZ = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final DateTimeFormatter DATE8 = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final BankExchRateInfmRepository exchRateRepo;
    private final BankCdResolver bankCdResolver;

    /**
     * Returns one exchange rate row per currency for the authenticated tenant on
     * the requested reference date.  When the batch has run twice on the same day
     * (morning + afternoon), only the afternoon run (highest EXCH_SEQ_NO) is
     * returned — matching the JEX IDO R002 COALESCE preference for SEQ_NO='2'.
     *
     * @param refDate  YYYYMMDD; {@code null} or blank → today (UTC+7 / Vietnam)
     * @param bankCd   bank code; {@code null} or blank → {@link #DEFAULT_BANK_CD}
     */
    public ExchangeRateListResponse list(String refDate, String bankCd) {
        String bizKey       = TenantContext.currentBizKey();
        String resolvedDate = resolveDate(refDate);
        String resolvedBank = (bankCd != null && !bankCd.isBlank()) ? bankCd : DEFAULT_BANK_CD;

        log.debug("exchange-rates list: bizKey={} bank={} date={}", bizKey, resolvedBank, resolvedDate);

        List<BankExchRateInfm> rows = exchRateRepo
                .findLatestPerCurrencyByBizKeyAndBankCdAndRefDate(bizKey, resolvedBank, resolvedDate);

        // When no explicit date was requested and today has no data (batch hasn't run yet),
        // fall back to the most recent date that has rows — keeps the page useful in dev/UAT.
        if (rows.isEmpty() && refDate == null) {
            String latestDate = exchRateRepo.findLatestRefDate(bizKey, resolvedBank).orElse(null);
            if (latestDate != null && !latestDate.equals(resolvedDate)) {
                log.debug("exchange-rates fallback to latest available date: {}", latestDate);
                resolvedDate = latestDate;
                rows = exchRateRepo
                        .findLatestPerCurrencyByBizKeyAndBankCdAndRefDate(bizKey, resolvedBank, resolvedDate);
            }
        }

        String bankNm = bankCdResolver.resolveBankNm(resolvedBank);

        List<ExchangeRateRow> rateRows = rows.stream()
                .map(this::toRow)
                .toList();

        return new ExchangeRateListResponse(resolvedDate, resolvedBank, bankNm, rateRows);
    }

    /**
     * Returns the distinct bank codes (with display names) that have exchange
     * rate data for the authenticated tenant — used to populate the bank selector.
     */
    public List<ExchangeRateBankItem> listBanks() {
        String bizKey = TenantContext.currentBizKey();

        log.debug("exchange-rates banks: bizKey={}", bizKey);

        return exchRateRepo.findDistinctBankCdByBizKey(bizKey).stream()
                .map(code -> new ExchangeRateBankItem(code, bankCdResolver.resolveBankNm(code)))
                .toList();
    }

    // ── Mappers ──────────────────────────────────────────────────────────────

    private ExchangeRateRow toRow(BankExchRateInfm e) {
        return new ExchangeRateRow(
                e.getCcyCd(),
                e.getRefCcyCd(),
                e.getBasicRate(),
                e.getBasicTran(),
                e.getBuyAmt(),
                e.getSellAmt(),
                e.getTranBuyAmt(),
                e.getTranSellAmt(),
                e.getStandPoint(),
                e.getLastUpdateDate(),
                e.getLastUpdateTime(),
                e.getExchRateRegDtm()
        );
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Returns today in YYYYMMDD using Vietnam time (UTC+7) when the caller passes
     * nothing.  The Coocon batch writes REF_DATE on the Vietnam calendar day, so
     * "today" must match that same timezone to avoid a one-day mismatch after midnight
     * UTC but before midnight ICT.
     */
    private String resolveDate(String refDate) {
        if (refDate != null && !refDate.isBlank()) {
            return refDate;
        }
        return LocalDate.now(VIETNAM_TZ).format(DATE8);
    }
}
