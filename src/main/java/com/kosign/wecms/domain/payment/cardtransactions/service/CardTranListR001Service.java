package com.kosign.wecms.domain.payment.cardtransactions.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.repository.BankInfmDetlPeriodLimitRepository;
import com.kosign.wecms.domain.banking.repository.BankInfmDetlPeriodLimitRepository.PeriodRow;
import com.kosign.wecms.domain.payment.cardtransactions.dto.CardTranListPeriodLimitRequest;
import com.kosign.wecms.domain.payment.cardtransactions.dto.CardTranListPeriodLimitResponse;
import com.kosign.wecms.domain.payment.cardtransactions.dto.CardTranListPeriodLimitResponse.Row;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Port of legacy
 * {@code WECMS/web/WEB-INF/action/jex/wabooks/nxisas/nxisas_cardTranList_r001_act.jsp}
 * &mdash; per-bank period-limit lookup feeding the Update Cards
 * popup's date-range validation.
 *
 * <p>Wire shape mirrors legacy exactly: caller posts
 * {@code REC_IN = [{bank_cd, bank_cert_seq}]}; service queries
 * {@code BANK_INFM_DETL} where {@code BANK_DETL_INFO_CD IN ('26', '27')}
 * joined through {@code BANK_CERT_INFO.NXISAS_GB_SEQ_NO}; returns
 * {@code REC_OUT = [{bank_cd, bank_cert_seq, cont1, cont2}]} with
 * one row per input entry.
 *
 * <p>When the refactor's {@code BANK_INFM_DETL} carries no period
 * config for a bank yet (refactor's seed deliberately doesn't include
 * codes {@code '26'} / {@code '27'} yet &mdash; the DEV snapshot for
 * those rows isn't imported), the service falls back to the legacy
 * default ({@code cont1="90", cont2="days"}) which is also the
 * fallback the popup's TS module used when the IDO returned an empty
 * REC_OUT.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CardTranListR001Service {

    /** Legacy default (popup's {@code _limitPeriodCnt = 90 days}) when
     *  no per-bank row exists. */
    private static final String DEFAULT_CONT1 = "90";
    private static final String DEFAULT_CONT2 = "days";

    private final BankInfmDetlPeriodLimitRepository periodRepo;

    @Transactional(readOnly = true)
    public CardTranListPeriodLimitResponse fetch(CardTranListPeriodLimitRequest req) {
        if (req == null || req.recIn() == null || req.recIn().isEmpty()) {
            throw new BusinessException("WBE_VALIDATION");
        }
        String bizKey = TenantContext.currentBizKey();
        if (bizKey == null || bizKey.isBlank()) {
            throw new BusinessException("WBE_VALIDATION");
        }

        log.info("---------------------------- card-tran-list-r001 :: START ----------------------------");
        log.info("card-tran-list-r001 :: bizKey={} input.size={}", bizKey, req.recIn().size());

        List<Row> rows = new ArrayList<>(req.recIn().size());
        for (CardTranListPeriodLimitRequest.Input in : req.recIn()) {
            // Bean-validation (`@NotNull` on the record components)
            // is wired at the controller boundary, but the static
            // analyzer can't see across that boundary. Defensive
            // null-checks here keep the service callable from
            // unvalidated sources (tests, batch jobs) and satisfy
            // the @NonNull contract on the repository method.
            String     bankCd      = in.bankCd();
            BigDecimal bankCertSeq = in.bankCertSeq();
            if (bankCd == null || bankCd.isBlank() || bankCertSeq == null) {
                throw new BusinessException("WBE_VALIDATION");
            }
            PeriodRow p = periodRepo.findByBankAndCert(bizKey, bankCd, bankCertSeq);
            String cont1 = (p == null || p.cont1() == null) ? DEFAULT_CONT1 : p.cont1();
            String cont2 = (p == null || p.cont2() == null) ? DEFAULT_CONT2 : p.cont2();
            rows.add(new Row(bankCd, bankCertSeq, cont1, cont2));
            log.info("card-tran-list-r001 ::   bankCd={} bankCertSeq={} cont1={} cont2={}{}",
                    bankCd, bankCertSeq, cont1, cont2,
                    p == null ? " (default)" : "");
        }
        log.info("---------------------------- card-tran-list-r001 :: DONE ----------------------------");

        return new CardTranListPeriodLimitResponse(rows);
    }
}
