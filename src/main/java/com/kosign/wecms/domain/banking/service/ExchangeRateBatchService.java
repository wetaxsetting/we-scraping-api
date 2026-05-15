package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.domain.auth.entity.BusiInfm;
import com.kosign.wecms.domain.auth.repository.BusiInfmRepository;
import com.kosign.wecms.domain.banking.client.CooconApiClient;
import com.kosign.wecms.domain.banking.dto.coocon.CooconExchangeRateResponse;
import com.kosign.wecms.domain.banking.entity.BankExchRateInfm;
import com.kosign.wecms.domain.banking.repository.BankExchRateInfmRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateBatchService {

    private final BusiInfmRepository busiInfmRepository;
    private final BankExchRateInfmRepository bankExchRateInfmRepository;
    private final CooconApiClient cooconApiClient;

    private static final String SYSTEM_USER = "SYSTEM_BATCH";
    private static final ZoneId VIETNAM_TZ = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE8  = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DTM_14 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * Mirrors the JEX c002 BANK_CONFIGS.
     * Shinhan: SEQ_NO 1-2 (COMMON). Woori: SEQ_NO 1-15 (CORPORATE).
     */
    private record BankConfig(String bankCd, String module, String category, int maxSeqNo) {}

    private static final List<BankConfig> BANK_CONFIGS = List.of(
            new BankConfig("11704088", "GLOBAL/GBL/BANK_Shinhan", "COMMON",     2),
            new BankConfig("11704020", "GLOBAL/GBL/BANK_Woori",   "CORPORATE", 15)
    );

    /**
     * Replaces the JEX EXCH_0001_001 batch.
     * Runs at 8:00 AM, 1:00 PM, and 6:00 PM every day (Vietnam time).
     * Also runs once automatically at application startup.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 0 8,13,18 * * ?")
    @Transactional
    public void fetchAndSaveExchangeRates() {
        String inqDate = LocalDate.now(VIETNAM_TZ).format(DATE8);
        String nowDtm  = java.time.LocalDateTime.now(ZoneId.of("UTC")).format(DTM_14);

        log.info("Starting Exchange Rate Batch for date: {}", inqDate);

        List<BusiInfm> activeTenants = busiInfmRepository.findAll()
                .stream()
                .filter(t -> !"Y".equals(t.getDelYn()))
                .toList();

        int totalInserted = 0;
        int totalUpdated  = 0;
        int totalErrors   = 0;

        for (BankConfig bank : BANK_CONFIGS) {
            log.info("Processing bank: {} ({}), SEQ_NO 1-{}", bank.bankCd(), bank.category(), bank.maxSeqNo());

            for (int seqInt = 1; seqInt <= bank.maxSeqNo(); seqInt++) {
                String seqNo = String.valueOf(seqInt);

                Optional<CooconExchangeRateResponse> responseOpt =
                        cooconApiClient.fetchExchangeRates(inqDate, seqNo, bank.module(), bank.category());

                if (responseOpt.isEmpty()
                        || responseOpt.get().getResultList() == null
                        || responseOpt.get().getResultList().isEmpty()) {
                    log.debug("No response for bank={} seqNo={} — stopping seqNo loop for this bank.", bank.bankCd(), seqNo);
                    break; // no more seq_nos for this bank today
                }
  
                // Use first ResultItem only to check the error code / break signal.
                // Then iterate ALL ResultItems so currencies spread across multiple
                // ResultList entries are not silently dropped.
                CooconExchangeRateResponse.ResultItem firstItem = responseOpt.get().getResultList().get(0);
                if (firstItem == null || firstItem.getOutput() == null) {
                    log.warn("Invalid output format for bank={} seqNo={}", bank.bankCd(), seqNo);
                    break;
                }

                String firstErrorCode = firstItem.getOutput().getErrorCode();
                if (!"00000000".equals(firstErrorCode)) {
                    log.info("No data for bank={} seqNo={} (errorCode={}) — likely end of available rounds.",
                            bank.bankCd(), seqNo, firstErrorCode);
                    break; // Woori returns a non-success code when seqNo exceeds available rounds
                }

                int ratesSavedThisRound = 0;
                for (CooconExchangeRateResponse.ResultItem resultItem : responseOpt.get().getResultList()) {
                    if (resultItem == null || resultItem.getOutput() == null
                            || resultItem.getOutput().getResult() == null) {
                        continue;
                    }
                    if (!"00000000".equals(resultItem.getOutput().getErrorCode())) {
                        continue;
                    }

                    List<CooconExchangeRateResponse.ForeignExchange> rates =
                            resultItem.getOutput().getResult().getForeignExchange();
                    if (rates == null || rates.isEmpty()) {
                        continue;
                    }

                    String standPoint = resultItem.getOutput().getResult().getStandPoint();

                    for (BusiInfm tenant : activeTenants) {
                        for (CooconExchangeRateResponse.ForeignExchange rate : rates) {
                            String ccyCd = rate.getBaseCurrency();
                            try {
                                Optional<BankExchRateInfm> existing = bankExchRateInfmRepository
                                        .findByBizKeyAndBankCdAndRefDateAndCcyCdAndSeqNo(
                                                tenant.getBizKey(), bank.bankCd(), inqDate, ccyCd, seqNo);

                                BankExchRateInfm entity;
                                if (existing.isPresent()) {
                                    entity = existing.get();
                                    entity.setModDtm(nowDtm);
                                    entity.setModId(SYSTEM_USER);
                                    totalUpdated++;
                                } else {
                                    entity = new BankExchRateInfm();
                                    entity.setBizKey(tenant.getBizKey());
                                    entity.setBankCd(bank.bankCd());
                                    entity.setRefDate(inqDate);
                                    entity.setCcyCd(ccyCd);
                                    entity.setSeqNo(seqNo);
                                    entity.setExchSeqNo(bankExchRateInfmRepository.findNextExchSeqNo(tenant.getBizKey()));
                                    entity.setRegDtm(nowDtm);
                                    entity.setRegUser(SYSTEM_USER);
                                    totalInserted++;
                                }

                                entity.setRefCcyCd(rate.getCounterCurrency());
                                entity.setTranSellAmt(parseDecimal(rate.getTransferSell()));
                                entity.setTranBuyAmt(parseDecimal(rate.getTransferBuy()));
                                entity.setBuyAmt(parseDecimal(rate.getBuy()));
                                entity.setSellAmt(parseDecimal(rate.getSell()));
                                entity.setBasicRate(parseDecimal(rate.getBasicRate()));
                                entity.setBasicTran(parseDecimal(rate.getBasicTransfer()));
                                entity.setSrchDt(inqDate);
                                entity.setClassExch(bank.category());
                                entity.setModule(bank.module());
                                entity.setStandPoint(standPoint);
                                entity.setLastUpdateDate(rate.getBaseDate());
                                entity.setLastUpdateTime(rate.getBaseTime());
                                entity.setExchRateRegDtm(rate.getBaseDate() + rate.getBaseTime());

                                bankExchRateInfmRepository.save(entity);
                                ratesSavedThisRound++;

                            } catch (Exception e) {
                                log.error("Error saving rate for bank={} seqNo={} ccy={}: {}",
                                        bank.bankCd(), seqNo, ccyCd, e.getMessage());
                                totalErrors++;
                            }
                        }
                    }
                }

                if (ratesSavedThisRound == 0) {
                    log.info("Empty rate list for bank={} seqNo={}", bank.bankCd(), seqNo);
                    break;
                }

                log.info("bank={} seqNo={} — {} saves across {} tenants",
                        bank.bankCd(), seqNo, ratesSavedThisRound, activeTenants.size());
            }
        }

        log.info("Exchange Rate Batch completed. inserted={} updated={} errors={}", totalInserted, totalUpdated, totalErrors);
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.trim().replaceAll(",", ""));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
