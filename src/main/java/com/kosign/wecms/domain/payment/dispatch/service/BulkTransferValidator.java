package com.kosign.wecms.domain.payment.dispatch.service;

import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMst;
import com.kosign.wecms.domain.payment.dispatch.repository.BulkValidationCounts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * BATCH1 pre-flight network/currency validation — a port of the "vrfi" block
 * in legacy {@code FILE_DEPOSIT_TRANSFER_BATCH1.java:113-266}, driven by the
 * {@code TB_PAY_FILE_DATA_R005} aggregate ({@link BulkValidationCounts}).
 *
 * <p>Returns the legacy validation message (stored verbatim into
 * {@code TB_PAY_FILE_MST.RS_MSG}) when a file is not dispatchable, or
 * {@link Optional#empty()} when the file passes. The whole file is the unit —
 * a single failing condition rejects the entire file (legacy marks the header
 * {@code PROC_STAT='3'} and skips it).
 *
 * <p>Overseas files are identified by {@code FUND_TP='S'}
 * ({@code FundTp.OVERSEAS_REMITTANCE_SHINHAN}), matching the legacy header
 * discriminator at {@code :146}. (The {@code OverseasDispatchExecutor} now
 * writes {@code 'S'}, so this branch routes correctly.)
 *
 * <p><b>Scope:</b> this is the network/currency gate only. The legacy
 * operating-hours / cut-off gate ({@code :269-447}, {@code TB_BATCH_SCHEDULE_*})
 * is intentionally NOT ported (Mr. Kang: DB is 24-hour, inert; pending PO).
 *
 * <p><b>Fidelity note:</b> legacy checks {@code swiftOutCnt>0} then
 * {@code failChnlCnt>0} for overseas (:148, :153). In {@code R005} those two
 * columns are byte-identical expressions, so the first check always decides;
 * the second is unreachable and its distinct message never fires. Reproduced
 * as the single {@code swiftOuterCnt>0} check below — observable behavior
 * (which file is rejected) is identical to legacy.
 */
@Service
public class BulkTransferValidator {

    /** {@code FundTp.OVERSEAS_REMITTANCE_SHINHAN} — overseas header discriminator. */
    private static final String FUND_TP_OVERSEAS = "S";

    private static final String MSG_OVRS_ANT_DIV =
            "ANT_DIV_CD value not available for overseas remittance transactions was included.";
    private static final String MSG_CCY_MIX = "Unable to mix remittance currency code.";
    private static final String MSG_EBANK_BANK = "It is a bank that cannot use the EBank network.";
    private static final String MSG_VCB_BANK = "It is a bank that cannot use the VCB network.";
    private static final String MSG_NETWORK_MIX =
            "Domestic foreign currency remittance cannot be mixed with other bank codes.";
    private static final String MSG_WOORI_NAPAS = "No Napas transactions at Woori Bank.";
    private static final String MSG_WOORI_EXCHANGE =
            "We can't do currency exchange transfer transactions at Woori Bank.";

    /** Shinhan / Woori firm-banking out-bank codes (legacy literals {@code "970424"} /
     *  {@code "970457"} at {@code :168/:186/:204}); config-overridable, same defaults
     *  as the dispatch executors. */
    @Value("${wecms.internal-transfer.routing.shinhan-bank-cd:970424}")
    private String shinhanBankCd;
    @Value("${wecms.internal-transfer.routing.woori-bank-cd:970457}")
    private String wooriBankCd;

    /**
     * Validate one bulk file. Returns the legacy {@code RS_MSG} on failure,
     * empty when dispatchable.
     */
    public Optional<String> validate(TbPayFileMst pay, BulkValidationCounts c) {
        String fundTp  = pay.getFundTp();
        String outBank = pay.getOutBankCd();

        long ebank     = nz(c.getEbankCnt());
        long ebankOut  = nz(c.getEbankOuterCnt());
        long vcb       = nz(c.getVcbCnt());
        long vcbOut    = nz(c.getVcbOuterCnt());
        long napas     = nz(c.getNapasCnt());
        long swiftOut  = nz(c.getSwiftOuterCnt());
        long ccy       = nz(c.getCcyCnt());
        long exchange  = nz(c.getExchangeTranCnt());

        if (FUND_TP_OVERSEAS.equals(fundTp)) {
            // Overseas (legacy :146-162): only SWIFT, single currency.
            if (swiftOut > 0) return Optional.of(MSG_OVRS_ANT_DIV);
            if (ccy > 1)      return Optional.of(MSG_CCY_MIX);
        } else {
            // Domestic / other (legacy :164-201).
            if (ebank > 0) {
                if (!equalsBank(outBank, shinhanBankCd)) return Optional.of(MSG_EBANK_BANK);
                if (ebankOut > 0)                        return Optional.of(MSG_NETWORK_MIX);
                if (ccy > 1)                             return Optional.of(MSG_CCY_MIX);
            } else if (vcb > 0) {
                if (!equalsBank(outBank, wooriBankCd))    return Optional.of(MSG_VCB_BANK);
                if (vcbOut > 0)                          return Optional.of(MSG_NETWORK_MIX);
                if (ccy > 1)                             return Optional.of(MSG_CCY_MIX);
            }
        }

        // Woori-only prohibitions (legacy :204-216) — run only when still valid.
        if (equalsBank(outBank, wooriBankCd)) {
            if (napas > 0)    return Optional.of(MSG_WOORI_NAPAS);
            if (exchange > 0) return Optional.of(MSG_WOORI_EXCHANGE);
        }

        return Optional.empty();
    }

    private static boolean equalsBank(String a, String b) {
        return a != null && a.equals(b);
    }

    private static long nz(Long v) {
        return v == null ? 0L : v;
    }
}
