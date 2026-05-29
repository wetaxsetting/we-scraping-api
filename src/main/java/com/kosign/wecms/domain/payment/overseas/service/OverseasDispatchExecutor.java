package com.kosign.wecms.domain.payment.overseas.service;

import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileData;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMst;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileDataRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchKeyGenerator;
import com.kosign.wecms.domain.payment.entity.OvrsPymnDtls;
import com.kosign.wecms.domain.payment.entity.OvrsPymnSumr;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.FirmBankingSetting;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.FirmBankingSettingId;
import com.kosign.wecms.domain.setting.firmbankingsettings.repository.FirmBankingSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Shared dispatch executor for Overseas Payment batches. Mirrors
 * {@link com.kosign.wecms.domain.payment.dispatch.service.TransferDispatchExecutor}
 * but speaks the {@code OVRS_PYMN_*} schema (different table family
 * from {@code SCDUL_PYMN_*}) and always uses the File path — Overseas
 * remittances go through the SWIFT / international wire rail, never the
 * domestic NAPAS API path.
 *
 * <p>Per row in the batch:
 * <ol>
 *   <li>Insert one {@link TbPayFileMst} header (PROC_STAT='9' pending,
 *       FUND_TP='9' overseas remittance, TRAN_TP='1', BANK_PRTN_ID placeholder).</li>
 *   <li>Insert one {@link TbPayFileData} per detail line. Beneficiary
 *       fields are mapped from {@link OvrsPymnDtls}: {@code BIC_CD} →
 *       {@code TAR_BANK_CD}, {@code BNFC_ACCT_NO} → {@code FINC_MDA_NO},
 *       {@code BNFC_NM} → {@code FINC_MDA_OWNER_NM}, {@code TRNF_AMT} →
 *       {@code TRAN_AMT}, {@code CCY_CD} → {@code TRSC_CCY_CD}.</li>
 *   <li>Stamp shadow keys on the master ({@code PAY_TRAN_DT} +
 *       {@code PAY_FILE_SEQ_NO}) so the Result-detail IDO query can join
 *       {@code OVRS_PYMN_SUMR ⋈ TB_PAY_FILE_MST}.</li>
 *   <li>Stamp shadow keys on each detail ({@code PAY_TRAN_DT} +
 *       {@code PAY_TRAN_SEQ} = {@code TB_PAY_FILE_DATA.SEQ} as varchar).</li>
 * </ol>
 *
 * <p>Status values are conservative: every row goes in with
 * {@code PROC_STAT='9'} (pending). The live JEX bank-integration daemon
 * flips them to '0' / '3' on bank callback; refactor R&D doesn't touch
 * that flip path.
 *
 * <p>Mirrors legacy {@code pymn_0007_001_c001_act.jsp:100-456}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OverseasDispatchExecutor {

    private static final DateTimeFormatter DT8   = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DTM14 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** Vietnam-local zone — same window the cut-off-time validator uses. */
    private static final ZoneId VN_TZ = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Status pinned on every freshly-written dispatch row. */
    private static final String PROC_STAT_PENDING = "9";

    /** {@code TB_PAY_FILE_DATA.PROC_RSLT_DIV} initial state. */
    private static final String PROC_RSLT_DIV_UNPROCESSED = "N";

    /** {@code FUND_TP='9'} — overseas remittance. Mirrors the DEV
     *  {@code tb_pay_file_mst} distribution: every cross-currency batch
     *  (USD↔VND) carries this value, verified against 28 dispatched
     *  overseas-flavored rows on JEX DEV. */
    private static final String FUND_TP_OVERSEAS = "9";

    /** {@code TRAN_TP='1'} — DEV's actual stored value. The
     *  SCDUL_PYMN executor writes {@code 'D'} (enum {@code TranTpF.DEPOSIT})
     *  which diverges from DEV; the Overseas path aligns with DEV from
     *  the start. */
    private static final String TRAN_TP_DISPATCH = "1";

    /** {@code TB_PAY_FILE_DATA.FINC_MDA_DIV='A'} — account-number target.
     *  Same value the SCDUL_PYMN executor uses. */
    private static final String FINC_MDA_DIV_ACCOUNT = "A";

    /** {@code BANK_CHNL_DIV='S'} — legacy {@code BankChnlDiv.OVERSEAS_REMITTANCE_SHINHAN}.
     *  Verified against DEV: 60+ overseas dispatch rows carry this value. */
    private static final String BANK_CHNL_DIV_OVERSEAS = "S";

    /** {@code ANT_DIV_CD='S'} — legacy {@code AntDivCdT.SWIFT}. Overseas
     *  always goes through the SWIFT rail. Verified against DEV. */
    private static final String ANT_DIV_CD_SWIFT = "S";

    /** Shinhan firm-banking out-bank code (legacy parity for the
     *  bank-specific {@code TRSC_CCY_CD} branch). When the batch's
     *  {@code OUT_BANK_CD} matches this value, the dispatch line's
     *  {@code TRSC_CCY_CD} is sourced from the master's
     *  {@code WDRL_CCY_CD} (withdrawal account currency). Mirrors
     *  {@code pymn_0007_001_c001_act.jsp:341-348}. */
    private static final String OUT_BANK_CD_SHINHAN = "970424";

    /** Woori firm-banking out-bank code — the dispatch line's
     *  {@code TRSC_CCY_CD} is sourced from the line's own
     *  {@code TRNF_CCY_CD} (transfer currency) for this bank. */
    private static final String OUT_BANK_CD_WOORI = "970457";

    /** {@code BANK_PRTN_ID} fallback — used only when
     *  {@code TB_FIRM_CONF.BANK_PRTN_ID} can't be resolved for the
     *  tenant's withdrawal account (rare; bank-side daemon would
     *  fail downstream anyway). Mirrors the SCDUL_PYMN executor's
     *  pragmatic choice — the dispatch row still lands so refactor R&D
     *  flows when local test data lacks a firm-banking row. */
    private static final String BANK_PRTN_ID_FALLBACK = "DEFAULT";

    /** Minimum BIC length needed to extract the 2-char country code
     *  (chars 5-6 of an 11-char SWIFT BIC, 0-indexed 4-5). Shorter
     *  values skip the country-code stamp. */
    private static final int BIC_MIN_LEN_FOR_COUNTRY = 6;

    /** Maps firm-banking out-bank codes to the NAPAS bank code under
     *  which the originator's withdrawal account is registered in
     *  {@code ACCT_MSTR}. Legacy mirror:
     *  {@code pymn_0007_001_c001_act.jsp:376-381}.
     *  <ul>
     *    <li>{@code 970424} (Shinhan firm-banking) → {@code 11704088} (Shinhan NAPAS)</li>
     *    <li>{@code 970457} (Woori firm-banking)   → {@code 11704020} (Woori NAPAS)</li>
     *  </ul>
     *  Other out-bank codes yield {@code null} → SENDER_NM stays null,
     *  matching legacy where the unmapped path also produced a null name. */
    private static String napasCodeForOutBank(String outBankCd) {
        if (OUT_BANK_CD_SHINHAN.equals(outBankCd)) return "11704088";
        if (OUT_BANK_CD_WOORI.equals(outBankCd))   return "11704020";
        return null;
    }

    private final TbPayFileMstRepository       tbPayFileMstRepository;
    private final TbPayFileDataRepository      tbPayFileDataRepository;
    private final DispatchKeyGenerator         keyGenerator;
    private final Clock                        clock;
    /** Resolves {@code TB_FIRM_CONF.BANK_PRTN_ID} for the master's
     *  withdrawal account. Required for legacy parity — without it the
     *  bank daemon routes against the wrong partner id. */
    private final FirmBankingSettingRepository firmBankingSettingRepository;
    /** Resolves the originator's name from {@code ACCT_MSTR.ACCT_NM}.
     *  Surfaces on the SWIFT MT103 field 50K (Ordering Customer). */
    private final AcctMstrRepository           acctMstrRepository;

    /**
     * Insert TB_PAY_FILE_MST + TB_PAY_FILE_DATA rows for one Overseas
     * batch + stamp the shadow keys back on the master + detail rows.
     * Must run inside the caller's transaction (caller's
     * {@code TRNF_YN='Y'} stamp and the dispatch inserts commit or
     * roll back together).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void execute(OvrsPymnSumr master, List<OvrsPymnDtls> lines) {
        String bizKey = master.getBizKey();
        LocalDateTime nowVn = LocalDateTime.now(clock.withZone(VN_TZ));
        String tranDt = nowVn.format(DT8);
        String dttm14 = nowVn.format(DTM14);

        // 1. Header row.
        String fileSeqNo = keyGenerator.nextFileSeqNo(bizKey, tranDt);

        // Resolve BANK_PRTN_ID from TB_FIRM_CONF — mirrors legacy
        // pymn_0007_001_c001_act.jsp:293-298, 316. Falls back to
        // "DEFAULT" only when the firm-banking row is missing (rare;
        // logged warn so the operator can seed the missing config).
        String bankPrtnId = resolveBankPrtnId(bizKey, master.getOutBankCd(), master.getWdrlActNo());

        TbPayFileMst mst = new TbPayFileMst();
        mst.setTranDt(tranDt);
        mst.setBizKey(bizKey);
        mst.setFileSeqNo(fileSeqNo);
        mst.setOutBankCd(master.getOutBankCd());
        mst.setWdrlActNo(master.getWdrlActNo());
        mst.setBankPrtnId(bankPrtnId);
        mst.setFundTp(FUND_TP_OVERSEAS);
        mst.setTranTp(TRAN_TP_DISPATCH);
        mst.setProcStat(PROC_STAT_PENDING);
        mst.setReqCnt(master.getTotTrnsCnt() == null
                ? null : new BigDecimal(master.getTotTrnsCnt()));
        mst.setReqAmt(master.getTotalTranAmt());
        mst.setRegDttm(dttm14);

        tbPayFileMstRepository.save(mst);

        // Stamp the master's shadow keys so the Result-detail IDO query
        // can join OVRS_PYMN_SUMR → TB_PAY_FILE_MST via
        // (master.PAY_TRAN_DT, master.PAY_FILE_SEQ_NO). Legacy parity
        // per pymn_0007_001_c001_act.jsp:179-401.
        master.setPayTranDt(tranDt);
        master.setPayFileSeqNo(fileSeqNo);

        // 2. Detail rows. SEQ starts at 1 and increments per line.
        // Field mapping mirrors pymn_0007_001_c001_act.jsp:326-399.
        String outBankCd = master.getOutBankCd();
        // Resolve SENDER_NM once per batch — same value on every line
        // (legacy looks it up per-line but the inputs don't change).
        // Mirrors pymn_0007_001_c001_act.jsp:375-384.
        String senderNm = resolveSenderNm(bizKey, outBankCd, master.getWdrlActNo());
        BigDecimal seq = BigDecimal.ONE;
        for (OvrsPymnDtls line : lines) {
            TbPayFileData data = new TbPayFileData();
            data.setTranDt(tranDt);
            data.setBizKey(bizKey);
            data.setFileSeqNo(fileSeqNo);
            data.setSeq(seq);

            // ANT_DIV_CD='S' — Overseas always goes through SWIFT.
            data.setAntDivCd(ANT_DIV_CD_SWIFT);

            // TRSC_CCY_CD — bank-specific (legacy lines 341-348).
            //   Shinhan (970424) → master.WDRL_CCY_CD
            //   Woori   (970457) → line.CCY_CD (the per-line transfer ccy)
            //   Else → fall back to line.CCY_CD (defensive — keeps
            //          the dispatch alive when a new firm-banking bank
            //          is added without an explicit branch here).
            String trscCcyCd;
            if (OUT_BANK_CD_SHINHAN.equals(outBankCd)) {
                trscCcyCd = master.getWdrlCcyCd();
                if (trscCcyCd == null || trscCcyCd.isBlank()) {
                    trscCcyCd = line.getCcyCd();
                }
            } else {
                trscCcyCd = line.getCcyCd();
            }
            data.setTrscCcyCd(trscCcyCd);

            // BIC code identifies the beneficiary bank on the SWIFT rail.
            data.setTarBankCd(line.getBicCd());
            data.setFincMdaDiv(FINC_MDA_DIV_ACCOUNT);
            data.setFincMdaNo(line.getBnfcAcctNo());
            data.setTranAmt(line.getTrnfAmt());

            // BANK_CHNL_DIV='S' — legacy BankChnlDiv.OVERSEAS_REMITTANCE_SHINHAN.
            // Verified against DEV: every overseas-remittance dispatch row
            // carries this value (not 'O' / OTHER_BANK_TRANSFER).
            data.setBankChnlDiv(BANK_CHNL_DIV_OVERSEAS);

            data.setFincMdaOwnerNm(resolveOwnerNm(line));
            // Originator's name on the SWIFT MT103 (field 50K). Sourced
            // from ACCT_MSTR.ACCT_NM via the NAPAS-code map; nullable
            // when the lookup misses, matching legacy.
            data.setSenderNm(senderNm);
            // MEMO copies into BOTH remark slots — legacy parity (lines 358-359).
            // The dispatch daemon picks the one its bank-side adapter cares
            // about; the refactor's job is just to keep both populated.
            data.setWdrlActRmrk(line.getMemo());
            data.setTarAcctRmrk(line.getMemo());

            // Beneficiary address — surfaces on the SWIFT MT103 message
            // (legacy redmine #42564). Nullable on the column; we copy
            // through unconditionally because legacy does.
            data.setRcptUseAddr(line.getBnfcAddr());

            // Fee burden translation: B/O/S → BEN/OUR/SHA. Anything else
            // ships as null (legacy parity — no `else` branch).
            data.setFeeBrdnClsCd(mapFeeBurden(line.getFeePayerCd()));

            // Recipient bank country code — chars 5-6 of the BIC
            // (0-indexed 4-5). SHBKKRSEXXX → "KR".
            String countryCd = extractCountryCd(line.getBicCd());
            data.setRcptBankCntryCd(countryCd);

            // REMIT_CCY_CD always comes from the line's own TRNF_CCY_CD
            // (= ccy_cd on the refactor's OvrsPymnDtls — overseas batches
            // carry a single transfer currency per line, no master-level
            // override). Legacy line 399.
            data.setRemitCcyCd(line.getCcyCd());

            data.setProcRsltDiv(PROC_RSLT_DIV_UNPROCESSED);

            tbPayFileDataRepository.save(data);

            // Stamp shadow keys on the detail. Mirrors the SCDUL_PYMN
            // executor's File-path stamping pattern — pay_tran_seq
            // stores the file-data SEQ as varchar so the Result-detail
            // join can compare it to a string column.
            line.setPayTranDt(tranDt);
            line.setPayTranSeq(seq.toPlainString());

            seq = seq.add(BigDecimal.ONE);
        }

        log.info("feature=overseas-payment dispatch path=FILE bizKey={} ovrsPymnNo={} lines={} tranDt={} fileSeqNo={}",
                bizKey, master.getOvrsPymnNo(), lines.size(), tranDt, fileSeqNo);
    }

    /**
     * Map the {@code FEE_PAYER_CD} single-character code to the 3-char
     * SWIFT fee burden classification. Legacy mirror of
     * {@code pymn_0007_001_c001_act.jsp:366-372}.
     *
     * <ul>
     *   <li>{@code B} → {@code BEN} (beneficiary pays)</li>
     *   <li>{@code O} → {@code OUR} (sender pays)</li>
     *   <li>{@code S} → {@code SHA} (shared)</li>
     *   <li>anything else → {@code null} (legacy doesn't have an else branch)</li>
     * </ul>
     */
    private static String mapFeeBurden(String feePayerCd) {
        if (feePayerCd == null) return null;
        return switch (feePayerCd.trim()) {
            case "B" -> "BEN";
            case "O" -> "OUR";
            case "S" -> "SHA";
            default  -> null;
        };
    }

    /**
     * Extract the recipient bank's country code from the SWIFT BIC.
     * BIC layout: 4 chars bank + 2 chars country + 2 chars location +
     * optional 3 chars branch (e.g. {@code SHBKKRSEXXX} → {@code KR}).
     * Returns {@code null} when the BIC is too short to carry the
     * country segment.
     */
    private static String extractCountryCd(String bic) {
        if (bic == null) return null;
        String trimmed = bic.trim();
        if (trimmed.length() < BIC_MIN_LEN_FOR_COUNTRY) return null;
        return trimmed.substring(4, 6);
    }

    /**
     * Resolve the recipient's owner name for the NOT-NULL
     * {@code FINC_MDA_OWNER_NM} column. Prefers {@code BNFC_NM} from the
     * detail line; falls back to the beneficiary account number when the
     * Excel parser produced no name (defensive — keeps the insert alive).
     */
    private static String resolveOwnerNm(OvrsPymnDtls line) {
        String bnfcNm = line.getBnfcNm();
        if (bnfcNm != null && !bnfcNm.isBlank()) {
            return bnfcNm;
        }
        String acctNo = line.getBnfcAcctNo();
        return acctNo != null && !acctNo.isBlank() ? acctNo : "UNKNOWN";
    }

    /**
     * Resolve {@code BANK_PRTN_ID} from {@code TB_FIRM_CONF} keyed by
     * {@code (BIZ_KEY, OUT_BANK_CD, WDRL_ACT_NO)}. Mirrors legacy
     * {@code pymn_0007_001_c001_act.jsp:293-298, 316} where the value is
     * read from the per-tenant firm-banking config row and stamped onto
     * the dispatch header so the bank-side daemon can route correctly.
     *
     * <p>Returns {@link #BANK_PRTN_ID_FALLBACK} ({@code "DEFAULT"}) when
     * the firm-banking config row is missing. Legacy would NPE in that
     * case — we log warn + fall back so refactor R&D can dispatch
     * without first seeding {@code TB_FIRM_CONF}; the daemon never runs
     * locally anyway.
     */
    private String resolveBankPrtnId(String bizKey, String outBankCd, String wdrlActNo) {
        if (outBankCd == null || wdrlActNo == null) {
            log.warn("feature=overseas-payment dispatch: outBankCd or wdrlActNo is null; bankPrtnId falls back to '{}'",
                    BANK_PRTN_ID_FALLBACK);
            return BANK_PRTN_ID_FALLBACK;
        }
        FirmBankingSettingId id = new FirmBankingSettingId(bizKey, outBankCd, wdrlActNo);
        Optional<FirmBankingSetting> conf = firmBankingSettingRepository.findById(id);
        if (conf.isEmpty()) {
            log.warn("feature=overseas-payment dispatch: TB_FIRM_CONF row missing for bizKey={} outBankCd={} wdrlActNo={}; bankPrtnId falls back to '{}'",
                    bizKey, outBankCd, wdrlActNo, BANK_PRTN_ID_FALLBACK);
            return BANK_PRTN_ID_FALLBACK;
        }
        String bankPrtnId = conf.get().getBankPrtnId();
        return (bankPrtnId == null || bankPrtnId.isBlank())
                ? BANK_PRTN_ID_FALLBACK
                : bankPrtnId;
    }

    /**
     * Resolve the originator's name from {@code ACCT_MSTR.ACCT_NM}.
     * Mirrors legacy {@code pymn_0007_001_c001_act.jsp:68-96, 375-384}:
     * <ol>
     *   <li>Map the firm-banking {@code OUT_BANK_CD} to the matching
     *       NAPAS bank code via {@link #napasCodeForOutBank}.</li>
     *   <li>Look up {@code ACCT_MSTR} by
     *       {@code (BIZ_KEY, BANK_CD = napasCode, ACCT_NO = wdrlActNo)}.</li>
     *   <li>Return {@code ACCT_NM} — or {@code null} when the lookup
     *       misses or the column is blank (legacy returns null in those
     *       cases too).</li>
     * </ol>
     */
    private String resolveSenderNm(String bizKey, String outBankCd, String wdrlActNo) {
        String napasCode = napasCodeForOutBank(outBankCd);
        if (napasCode == null || wdrlActNo == null) {
            return null;
        }
        Optional<AcctMstr> acct = acctMstrRepository
                .findByBizKeyAndBankCdAndAcctNo(bizKey, napasCode, wdrlActNo);
        if (acct.isEmpty()) {
            return null;
        }
        String acctNm = acct.get().getAcctNm();
        return (acctNm == null || acctNm.isBlank()) ? null : acctNm;
    }
}
