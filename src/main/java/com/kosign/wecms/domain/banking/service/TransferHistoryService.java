package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.dto.TransferHistoryResponse;
import com.kosign.wecms.domain.banking.dto.TransferHistoryResponse.CurrencyTotal;
import com.kosign.wecms.domain.banking.dto.TransferRow;
import com.kosign.wecms.domain.banking.entity.NapasRemitTrnsHstr;
import com.kosign.wecms.domain.banking.repository.NapasRemitTrnsHstrRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Transfer History (Banking → Internal Transfer → History) service.
 * Mirrors legacy {@code trns_0003_001_r001} — reads NAPAS_REMIT_TRNS_HSTR.
 *
 * Status mapping mirrors legacy {@code TRNS_STTS}: '00000000' = success,
 * anything else = failed. The wire-level {@code STATUS} field collapses
 * this to "success" / "failed" for the static UI.
 */
@Service
@RequiredArgsConstructor
public class TransferHistoryService {

    private final NapasRemitTrnsHstrRepository repo;
    private final BankCdResolver bankResolver;

    @Transactional(readOnly = true)
    public TransferHistoryResponse list(String fromDt, String toDt, String statusFilter) {
        final String bizKey = TenantContext.currentBizKey();

        List<NapasRemitTrnsHstr> rows = resolve(bizKey, fromDt, toDt, statusFilter);

        // Currency totals for the footer.
        Map<String, CurrencyTotal> totals = new LinkedHashMap<>();
        for (NapasRemitTrnsHstr r : rows) {
            String ccy = r.getCcyCd() != null ? r.getCcyCd().trim() : "";
            BigDecimal amt = r.getTrscAmt() != null ? r.getTrscAmt() : BigDecimal.ZERO;
            CurrencyTotal current = totals.get(ccy);
            BigDecimal accum = current == null ? amt : new BigDecimal(current.amount()).add(amt);
            int count = (current == null ? 0 : current.count()) + 1;
            totals.put(ccy, new CurrencyTotal(ccy, count, AmountFormatter.wire(accum)));
        }

        List<TransferRow> mapped = rows.stream().map(this::toRow).toList();
        return new TransferHistoryResponse(mapped, mapped.size(), List.copyOf(totals.values()));
    }

    private List<NapasRemitTrnsHstr> resolve(String bizKey, String fromDt, String toDt, String statusFilter) {
        boolean hasRange = fromDt != null && !fromDt.isBlank() && toDt != null && !toDt.isBlank();
        // REG_DTM is YYYYMMDDhhmmss; pad date params to range bounds.
        String fromDtm = hasRange ? padFrom(fromDt) : null;
        String toDtm   = hasRange ? padTo(toDt)     : null;

        if (statusFilter == null || statusFilter.isBlank() || "ALL".equalsIgnoreCase(statusFilter)) {
            return hasRange
                    ? repo.findByBizKeyAndRegDtmBetweenOrderByRegDtmDesc(bizKey, fromDtm, toDtm)
                    : repo.findByBizKeyOrderByRegDtmDesc(bizKey);
        }
        if ("SUCCESS".equalsIgnoreCase(statusFilter)) {
            return hasRange
                    ? repo.findByBizKeyAndTrnsSttsAndRegDtmBetweenOrderByRegDtmDesc(bizKey, "00000000", fromDtm, toDtm)
                    : repo.findByBizKeyOrderByRegDtmDesc(bizKey).stream()
                            .filter(r -> "00000000".equals(r.getTrnsStts())).toList();
        }
        // FAILED: status NOT '00000000'
        return hasRange
                ? repo.findByBizKeyAndTrnsSttsNotAndRegDtmBetweenOrderByRegDtmDesc(bizKey, "00000000", fromDtm, toDtm)
                : repo.findByBizKeyOrderByRegDtmDesc(bizKey).stream()
                        .filter(r -> r.getTrnsStts() != null && !"00000000".equals(r.getTrnsStts())).toList();
    }

    private TransferRow toRow(NapasRemitTrnsHstr r) {
        String regDt = r.getRegDtm() != null && r.getRegDtm().length() >= 8 ? r.getRegDtm().substring(0, 8) : "";
        String regTm = r.getRegDtm() != null && r.getRegDtm().length() >= 14 ? r.getRegDtm().substring(8, 14) : "";
        String status = "00000000".equals(r.getTrnsStts()) ? "success" : "failed";
        return new TransferRow(
                String.valueOf(r.getSeqNo()),
                regDt,
                regTm,
                r.getOutBankCode(),
                bankResolver.resolveBankNm(r.getOutBankCode()),
                r.getOutAcctNo(),
                r.getCcyCd() != null ? r.getCcyCd().trim() : "",
                r.getInBankCode(),
                bankResolver.resolveBankNm(r.getInBankCode()),
                r.getInAcctNo(),
                r.getCcyCd() != null ? r.getCcyCd().trim() : "",
                r.getCcyCd() != null ? r.getCcyCd().trim() : "",
                AmountFormatter.wire(r.getTrscAmt()),
                AmountFormatter.wire(r.getFee()),
                r.getRmrk() != null ? r.getRmrk() : "",
                r.getTrnsStts(),
                r.getTrnsMsg(),
                status
        );
    }

    private static String padFrom(String yyyymmdd) { return yyyymmdd + "000000"; }
    private static String padTo(String yyyymmdd)   { return yyyymmdd + "235959"; }
}
