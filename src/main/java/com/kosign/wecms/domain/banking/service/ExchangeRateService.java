package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.web.SortDirection;
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
import java.util.ArrayList;
import java.util.Comparator;
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
    private final ExchangeRateBatchService exchangeRateBatchService;

    /**
     * Returns one exchange rate row per currency for the requested reference date,
     * across ALL tenants. Exchange rates are global bank data — no biz_key filter.
     *
     * <p>When {@code seqNo} is supplied (e.g. "1" or "2") only rows for that exact
     * batch round are returned — matching the JEX r001 exact-SEQ_NO filter.</p>
     *
     * <p>When {@code seqNo} is omitted, the latest batch round (highest SEQ_NO)
     * per currency pair is returned via DISTINCT ON.</p>
     *
     * <p>When {@code sortField} and {@code sortDirection} are supplied the result
     * rows are sorted in-memory after mapping. Unknown / blank {@code sortField}
     * values fall back to the default alphabetical order by display currency.</p>
     *
     * @param refDate        YYYYMMDD; {@code null} or blank → today (UTC+7 / Vietnam)
     * @param bankCd         bank code; {@code null} or blank → {@link #DEFAULT_BANK_CD}
     * @param seqNo          batch sequence number ("1", "2", …); {@code null} or blank → latest
     * @param sortField      FIELD_ID to sort by (CURRENCY | CASH_BUY | CASH_SELL | TT_BUY | TT_SELL)
     * @param sortDirection  ASC | DESC; {@code null} or blank → ASC
     */
    public ExchangeRateListResponse list(String refDate, String bankCd, String seqNo,
                                         String sortField, String sortDirection) {
        String resolvedDate = resolveDate(refDate);
        String resolvedBank = (bankCd != null && !bankCd.isBlank()) ? bankCd : DEFAULT_BANK_CD;
        boolean hasSeqNo    = seqNo != null && !seqNo.isBlank();

        log.debug("exchange-rates list: bank={} date={} seqNo={}", resolvedBank, resolvedDate, seqNo);

        List<BankExchRateInfm> rows = fetchRows(resolvedBank, resolvedDate, hasSeqNo ? seqNo : null);

        // When no explicit date was requested and today has no data, trigger the batch
        // synchronously to fetch from Coocon API (on-demand loading for the first caller).
        // runOnDemand() is @Transactional(REQUIRES_NEW) — it suspends this read-only
        // outer transaction and runs the per-bank persistence loops in a fresh writable
        // transaction. Without REQUIRES_NEW the inner UPSERTs would join the read-only
        // outer tx and fail under strict JDBC drivers.
        if (rows.isEmpty() && refDate == null) {
            log.info("No exchange rates found for today. Triggering on-demand fetch from Coocon API.");
            try {
                exchangeRateBatchService.runOnDemand();
                rows = fetchRows(resolvedBank, resolvedDate, hasSeqNo ? seqNo : null);
            } catch (Exception e) {
                log.error("On-demand exchange rate fetch failed", e);
            }

            // If still empty (e.g. Coocon is down or no rates yet), fall back to the most recent date
            if (rows.isEmpty()) {
                String latestDate = exchRateRepo.findLatestRefDateByBankCd(resolvedBank).orElse(null);
                if (latestDate != null && !latestDate.equals(resolvedDate)) {
                    log.debug("exchange-rates fallback to latest available date: {}", latestDate);
                    resolvedDate = latestDate;
                    rows = fetchRows(resolvedBank, resolvedDate, hasSeqNo ? seqNo : null);
                }
            }
        }

        String bankNm = bankCdResolver.resolveBankNm(resolvedBank);

        List<ExchangeRateRow> rateRows = new ArrayList<>(rows.stream()
                .map(this::toRow)
                .toList());

        applySort(rateRows, sortField, sortDirection);

        List<com.kosign.wecms.domain.banking.dto.ExchangeRateRoundItem> rounds =
                exchRateRepo.findDistinctRoundsByBankCdAndRefDate(resolvedBank, resolvedDate).stream()
                        .map(row -> new com.kosign.wecms.domain.banking.dto.ExchangeRateRoundItem(
                                (String) row[0],
                                (String) row[1]
                        ))
                        .toList();

        return new ExchangeRateListResponse(resolvedDate, resolvedBank, bankNm, rateRows, rounds);
    }

    /**
     * Returns the distinct bank codes (with display names) that have exchange
     * rate data for the authenticated tenant — used to populate the bank selector.
     */
    public List<ExchangeRateBankItem> listBanks() {
        log.debug("exchange-rates banks");

        return exchRateRepo.findDistinctBankCd().stream()
                .map(code -> new ExchangeRateBankItem(code, bankCdResolver.resolveBankNm(code)))
                .toList();
    }

    /**
     * Returns the distinct display-currency codes across ALL tenants — used to
     * populate the full currency list on the frontend so every row appears even
     * when no rates exist for the selected date/bank.
     */
    public List<String> listCurrencies() {
        log.debug("exchange-rates currencies");

        return exchRateRepo.findDistinctDisplayCurrency();
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
     * Delegates to the correct repository query based on whether a {@code seqNo}
     * filter was requested.  {@code null} = no filter (DISTINCT ON latest round).
     */
    private List<BankExchRateInfm> fetchRows(String bankCd, String refDate, String seqNo) {
        if (seqNo != null) {
            return exchRateRepo.findLatestPerCurrencyByBankCdAndRefDateAndSeqNo(bankCd, refDate, seqNo);
        }
        return exchRateRepo.findLatestPerCurrencyByBankCdAndRefDate(bankCd, refDate);
    }

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

    /**
     * Sorts {@code rows} in-place using the whitelist-validated field + direction.
     * Unknown / blank {@code sortField} leaves the list in its DB-returned order
     * (alphabetical by currency via the native query's {@code ORDER BY ccy_cd ASC}).
     */
    private void applySort(List<ExchangeRateRow> rows, String sortField, String sortDirection) {
        ExchangeRateSortField field = ExchangeRateSortField.parse(sortField);
        if (field == null) return;

        SortDirection dir = SortDirection.parse(sortDirection);
        Comparator<ExchangeRateRow> cmp = field.comparator();
        if (dir == SortDirection.DESC) {
            cmp = cmp.reversed();
        }
        rows.sort(cmp);
    }
}
