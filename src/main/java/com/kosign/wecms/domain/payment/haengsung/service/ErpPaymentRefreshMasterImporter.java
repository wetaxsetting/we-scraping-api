package com.kosign.wecms.domain.payment.haengsung.service;

import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.domain.payment.common.LegacyTextNormalizer;
import com.kosign.wecms.domain.payment.dispatch.file.VietnameseText;
import com.kosign.wecms.domain.payment.entity.OvrsPymnSumr;
import com.kosign.wecms.domain.payment.entity.OvrsPymnSumrDetl;
import com.kosign.wecms.domain.payment.haengsung.oracle.OracleTransDetail;
import com.kosign.wecms.domain.payment.haengsung.oracle.OracleTransMaster;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrDetl;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrDetlRepository;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrRepository;
import com.kosign.wecms.domain.payment.internaltransfer.service.ScdulPymnTypeCd;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnDtlsRepository;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnSumrRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Imports ONE HAENGSUNG Oracle {@code TB_TRNS_MAST} master (+ its
 * {@code TB_TRNS_DETAIL} lines) into the WeCMS payment draft tables, as a single
 * primary-datasource transaction (the per-master unit of work,
 * {@code haengsung-payment-refresh} §8). Separated from
 * {@link ErpPaymentRefreshService} so the {@code @Transactional} boundary applies
 * per master (a Spring proxy call), letting the orchestrator roll back one master
 * and continue with the next.
 *
 * <p>Strategy = the legacy {@code ErpDataRefreshHelper} model: <b>upsert + dedup +
 * approval-lock</b> (NOT the batch's insert-only copy — §9A / M-9). Never sets
 * {@code APPR_SEQ_NO} (imports land as drafts, §6.4). Oracle receive write-back is
 * NOT done here — the orchestrator does it after this transaction commits (no XA).
 *
 * <p>Routing by {@code FILE_GB} (§6.1): {@code AF1}→Overseas
 * ({@code OVRS_PYMN_SUMR(_DETL)}); {@code AC2}→Payroll, {@code FILE_NM E*}→Expense,
 * else Vendor (all {@code SCDUL_PYMN_SUMR(_DETL)}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "haengsung.oracle", name = "enabled", havingValue = "true")
public class ErpPaymentRefreshMasterImporter {

    private static final String DRAFT_NOT_TRANSFERRED = "N";
    private static final String ERP_PROC_NOT_COMPLETE = "N";
    private static final String OVRS_PYMN_TYPE_CD = "01"; // legacy pymn_0007_002_c001_act.jsp:139

    private final ScdulPymnSumrRepository scdulSumrRepo;
    private final ScdulPymnSumrDetlRepository scdulDetlRepo;
    private final OvrsPymnSumrRepository ovrsSumrRepo;
    private final OvrsPymnDtlsRepository ovrsDetlRepo;
    private final LegacyFunctionRepository legacyFunctions;

    /**
     * @return outcome (IMPORTED with detail count, or SKIPPED_APPROVAL_LOCK).
     *         Throws on any persistence failure so the surrounding transaction
     *         rolls back and the orchestrator records the master as failed.
     */
    @Transactional
    public MasterImportOutcome importMaster(String bizKey, String userId, String nowVn,
                                            OracleTransMaster master, List<OracleTransDetail> details) {
        if (isOverseas(master.fileGb())) {
            return importOverseas(bizKey, userId, nowVn, master, details);
        }
        return importScheduled(bizKey, userId, nowVn, master, details, deriveScheduledTypeCd(master));
    }

    /* ============================ Scheduled (Vendor / Expense / Payroll) ============================ */

    private MasterImportOutcome importScheduled(String bizKey, String userId, String nowVn,
                                                OracleTransMaster master, List<OracleTransDetail> details,
                                                String typeCd) {
        var existing = scdulSumrRepo.findFirstByBizKeyAndErpFileNumAndErpFileDate(
                bizKey, master.fileNum(), master.fileDate());

        if (existing.isPresent() && isNotBlank(existing.get().getApprSeqNo())) {
            return MasterImportOutcome.approvalLocked(); // §6.4 — never overwrite an in-approval batch
        }

        String resolvedCcy = resolveCcy(details);
        ScdulPymnSumr sumr = existing.orElseGet(ScdulPymnSumr::new);
        boolean isNew = existing.isEmpty();
        if (isNew) {
            sumr.setBizKey(bizKey);
            sumr.setScdulPymnNo(legacyFunctions.nextScdulPymnNo(bizKey));
            sumr.setRegId(userId);
            sumr.setRegDtm(nowVn);
        }
        sumr.setScdulPymnTypeCd(typeCd);
        sumr.setOutBankCd(nullToEmpty(master.outBankCd()));
        sumr.setWdrlActNo(nullToEmpty(master.outAcctNo()));
        sumr.setCcyCd(resolvedCcy);
        sumr.setTitl(sanitizeTitle(master.fileNm()));
        sumr.setTotalTranAmt(zeroIfNull(master.regiAmt()));
        sumr.setTotTrnsCnt(zeroIfNull(master.regiCnt()));
        sumr.setTrnfYn(DRAFT_NOT_TRANSFERRED);
        sumr.setErpProcCmplYn(ERP_PROC_NOT_COMPLETE);
        sumr.setErpFileDate(master.fileDate());
        sumr.setErpFileNum(master.fileNum());
        sumr.setModId(userId);
        sumr.setModDtm(nowVn);
        // Flush the summary up front so a concurrent SCDUL_PYMN_NO MAX+1 race
        // surfaces as a DataIntegrityViolationException here (orchestrator retries).
        scdulSumrRepo.saveAndFlush(sumr);

        String scdulPymnNo = sumr.getScdulPymnNo();
        Map<String, ScdulPymnSumrDetl> existingByErpSeq = new HashMap<>();
        if (!isNew) {
            for (ScdulPymnSumrDetl d : scdulDetlRepo.findByBizKeyAndScdulPymnNo(bizKey, scdulPymnNo, Sort.unsorted())) {
                String key = seqKey(d.getErpFileSeq());
                if (key != null) existingByErpSeq.put(key, d);
            }
        }

        long[] detlCursor = { Long.MIN_VALUE }; // lazily seeded on first NEW line
        for (OracleTransDetail src : details) {
            String key = seqKey(src.fileSeq());
            ScdulPymnSumrDetl found = (key != null) ? existingByErpSeq.get(key) : null;
            boolean newDetail = (found == null);
            ScdulPymnSumrDetl detl = newDetail ? new ScdulPymnSumrDetl() : found;
            if (newDetail) {
                detl.setBizKey(bizKey);
                detl.setScdulPymnNo(scdulPymnNo);
                detl.setScdulPymnDetlNo(Long.toString(nextScdulDetlNo(bizKey, detlCursor)));
                detl.setRegId(userId);
                detl.setRegDtm(nowVn);
            }
            detl.setTranAmt(zeroIfNull(src.trAmt()));
            detl.setTrnfCcyCd(blankToDefault(src.isoCode(), resolvedCcy));
            detl.setFincMdaNo(nullToEmpty(stripSpaces(src.trAcctNo())));
            detl.setTarBankCd(stripSpaces(src.trAcctBankCd()));
            detl.setRcptNm(stripTone(sanitize(src.acetNm())));
            detl.setAntDivCd(mapAntDivCd(src.antDivCd()));
            String memo = sanitize(src.jeokyo());
            detl.setTranUsag(memo);
            detl.setTarAcctRmrk(memo);
            detl.setWdrlActRmrk(memo);
            detl.setErpFileDate(master.fileDate());
            detl.setErpFileNum(master.fileNum());
            detl.setErpFileSeq(src.fileSeq());
            detl.setErpAttribute2(src.attribute2());
            detl.setModId(userId);
            detl.setModDtm(nowVn);
            scdulDetlRepo.save(detl);
        }
        return MasterImportOutcome.imported(details.size());
    }

    /* ================================== Overseas (FILE_GB = AF1) ================================== */

    private MasterImportOutcome importOverseas(String bizKey, String userId, String nowVn,
                                               OracleTransMaster master, List<OracleTransDetail> details) {
        var existing = ovrsSumrRepo.findFirstByBizKeyAndErpFileNumAndErpFileDate(
                bizKey, master.fileNum(), master.fileDate());

        if (existing.isPresent() && isNotBlank(existing.get().getApprSeqNo())) {
            return MasterImportOutcome.approvalLocked();
        }

        String resolvedCcy = resolveCcy(details);
        OvrsPymnSumr sumr = existing.orElseGet(OvrsPymnSumr::new);
        boolean isNew = existing.isEmpty();
        if (isNew) {
            sumr.setBizKey(bizKey);
            sumr.setOvrsPymnNo(legacyFunctions.nextOvrsPymnNo(bizKey));
            sumr.setRegId(userId);
            sumr.setRegDtm(nowVn);
        }
        sumr.setOvrsPymnTypeCd(OVRS_PYMN_TYPE_CD);
        sumr.setTitl(sanitizeTitle(master.fileNm()));
        sumr.setCcyCd(resolvedCcy);
        sumr.setTotalTranAmt(zeroIfNull(master.regiAmt()));
        sumr.setTotTrnsCnt(zeroIfNull(master.regiCnt()).intValueExact());
        sumr.setOutBankCd(nullToEmpty(master.outBankCd()));
        sumr.setWdrlActNo(nullToEmpty(master.outAcctNo()));
        sumr.setTrnfYn(DRAFT_NOT_TRANSFERRED);
        sumr.setErpProcCmplYn(ERP_PROC_NOT_COMPLETE);
        sumr.setErpFileDate(master.fileDate());
        sumr.setErpFileNum(master.fileNum());
        sumr.setModId(userId);
        sumr.setModDtm(nowVn);
        ovrsSumrRepo.saveAndFlush(sumr);

        String ovrsPymnNo = sumr.getOvrsPymnNo();
        Map<String, OvrsPymnSumrDetl> existingByErpSeq = new HashMap<>();
        if (!isNew) {
            for (OvrsPymnSumrDetl d : ovrsDetlRepo.findByBizKeyAndOvrsPymnNoOrderByOvrsPymnDetlNoAsc(bizKey, ovrsPymnNo)) {
                String key = seqKey(d.getErpFileSeq());
                if (key != null) existingByErpSeq.put(key, d);
            }
        }

        long[] detlCursor = { Long.MIN_VALUE };
        for (OracleTransDetail src : details) {
            String key = seqKey(src.fileSeq());
            OvrsPymnSumrDetl found = (key != null) ? existingByErpSeq.get(key) : null;
            boolean newDetail = (found == null);
            OvrsPymnSumrDetl detl = newDetail ? new OvrsPymnSumrDetl() : found;
            if (newDetail) {
                detl.setBizKey(bizKey);
                detl.setOvrsPymnNo(ovrsPymnNo);
                detl.setOvrsPymnDetlNo(Long.toString(nextOvrsDetlNo(bizKey, detlCursor)));
                detl.setRegId(userId);
                detl.setRegDtm(nowVn);
            }
            detl.setTrnfAmt(zeroIfNull(src.trAmt()));
            detl.setCcyCd(blankToDefault(src.isoCode(), resolvedCcy)); // maps TRNF_CCY_CD
            detl.setBnfcNm(nullToEmpty(stripTone(sanitize(src.acetNm()))));
            detl.setBnfcBankNm(nullToEmpty(sanitize(src.trAcctBankNm())));
            detl.setBnfcBankCd(nullToEmpty(stripSpaces(src.trAcctBankCd())));
            detl.setBicCd(nullToEmpty(stripSpaces(src.trAcctBankCd())));
            detl.setBnfcAcctNo(nullToEmpty(stripSpaces(src.trAcctNo())));
            detl.setBnfcAddr(nullToEmpty(sanitize(src.acetAddr())));
            detl.setFeePayerCd(mapFeePayer(src.foriFeeMan()));
            detl.setMemo(nullToEmpty(sanitize(src.jeokyo())));
            detl.setTrcoKey("");      // contact-less ERP import (legacy parity, see OverseasPaymentExcelService)
            detl.setHandTrcoKey("");
            detl.setErpFileDate(master.fileDate());
            detl.setErpFileNum(master.fileNum());
            detl.setErpFileSeq(src.fileSeq());
            detl.setErpAttribute2(src.attribute2());
            detl.setModId(userId);
            detl.setModDtm(nowVn);
            ovrsDetlRepo.save(detl);
        }
        return MasterImportOutcome.imported(details.size());
    }

    /* ===================================== Routing / mapping ===================================== */

    static boolean isOverseas(String fileGb) {
        return "AF1".equalsIgnoreCase(trim(fileGb));
    }

    /** AC2→Payroll('00'); FILE_NM starts E/e→Expense('02') (M-2 unresolved); else Vendor('01'). */
    static String deriveScheduledTypeCd(OracleTransMaster master) {
        if ("AC2".equalsIgnoreCase(trim(master.fileGb()))) {
            return ScdulPymnTypeCd.PAYROLL.code();
        }
        String nm = trim(master.fileNm());
        if (nm != null && (nm.startsWith("E") || nm.startsWith("e"))) {
            // M-2 (UNRESOLVED): sample data shows no E-prefixed FILE_NM, so this
            // branch may never fire under real data. Kept verbatim to legacy
            // pending PO/ERP confirmation — spec §17 M-2.
            return ScdulPymnTypeCd.EXPENSE.code();
        }
        return ScdulPymnTypeCd.VENDOR.code();
    }

    /** FORI_FEE_MAN → FEE_PAYER_CD: 1→B (beneficiary/payee), 2→O (other), blank/3→S (sender). */
    static String mapFeePayer(String foriFeeMan) {
        String v = trim(foriFeeMan);
        if ("1".equals(v)) return "B";
        if ("2".equals(v)) return "O";
        return "S";
    }

    /**
     * ANT_DIV_CD mapping. M-3/M-12 UNRESOLVED — the two legacy import copies
     * disagree on {@code 'T'} (INTER vs NAPAS) and the Oracle doc lists no
     * {@code 'T'}. Until the PO/ERP confirms, the raw Oracle value is carried
     * through verbatim (it is a single char and fits the {@code VARCHAR(5)}
     * column); no lossy remap is invented. See spec §17 M-3 / §26 M-12.
     */
    static String mapAntDivCd(String raw) {
        return trim(raw);
    }

    /* -------- detail-number allocation (seed-once MAX+1 + Java increment) -------- */

    private long nextScdulDetlNo(String bizKey, long[] cursor) {
        if (cursor[0] == Long.MIN_VALUE) {
            cursor[0] = Long.parseLong(legacyFunctions.nextScdulPymnDetlNo(bizKey)) - 1;
        }
        return ++cursor[0];
    }

    private long nextOvrsDetlNo(String bizKey, long[] cursor) {
        if (cursor[0] == Long.MIN_VALUE) {
            cursor[0] = Long.parseLong(legacyFunctions.nextOvrsPymnDetlNo(bizKey)) - 1;
        }
        return ++cursor[0];
    }

    /* ========================================= helpers ========================================= */

    private static String resolveCcy(List<OracleTransDetail> details) {
        for (OracleTransDetail d : details) {
            if (isNotBlank(d.isoCode())) return d.isoCode().trim();
        }
        return "VND";
    }

    private static String seqKey(BigDecimal seq) {
        return seq == null ? null : seq.toBigInteger().toString();
    }

    private static BigDecimal zeroIfNull(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String stripSpaces(String s) {
        return s == null ? null : s.replace(" ", "");
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String blankToDefault(String s, String def) {
        return isNotBlank(s) ? s.trim() : def;
    }

    /**
     * Fold every Unicode space separator ({@code \p{Zs}}, incl. NBSP U+00A0) to ASCII {@code 0x20}.
     *
     * <p><b>Missed port, not an addition.</b> Legacy applies
     * {@code wabooks.commons.StringUtils.sanitize:118-124} to every ERP-sourced text field on this
     * exact path — {@code ErpDataRefreshHelper:373,424} ({@code FILE_NM}→{@code TITL}),
     * {@code :510,674} ({@code JEOKYO}→remark/memo), {@code :544,633} ({@code ACET_NM}),
     * {@code :614} ({@code ACET_ADDR}), {@code :615} ({@code TR_ACCT_BANK_NM}); the batch copy
     * {@code ERP_0100_002:313,329,358,359} does the same. Legacy also sanitizes a second time in the
     * entity setters ({@code ScdulPymnSumrDetl.setRcptNm:220 / setTranUsag:259 / setTarAcctRmrk:272 /
     * setWdrlAcctRmrk:285}, {@code OvrsPymnSumrDetl.setBnfcNm / setBnfcAddr / setMemo}), so an
     * ERP-imported NBSP can never reach the bank file. The refactor has no setter-level equivalent —
     * {@link LegacyTextNormalizer#normalize} covers the Excel-upload and API save paths only — so
     * without this call the ERP import was the one entry surface that let a {@code \p{Zs}} character
     * through to {@code TB_PAY_FILE_DATA} and on to the wire.
     *
     * <p>NBSP is invisible in every UI and log, is outside the 134-code-point tone table, and is
     * outside {@code hasVietnameseCharacters}' ranges — so tone-stripping never touches it and it
     * reaches {@code FixedLengthCodec} as two bytes. Correcting it silently here is what legacy does;
     * reporting it would name a character the operator cannot see.
     */
    private static String sanitize(String s) {
        return LegacyTextNormalizer.sanitizeSpaces(s);
    }

    /** Trim + 100-char cap for {@code TITL}, with the legacy {@link #sanitize} pass first. */
    private static String sanitizeTitle(String s) {
        if (s == null) return "";
        String t = sanitize(s).trim();
        return t.length() > 100 ? t.substring(0, 100) : t;
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    /**
     * Vietnamese tone stripping for ERP-imported names, via the <b>single</b> legacy mapping table
     * ({@link VietnameseText#replaceTone}) that every other dispatch path uses.
     *
     * <p>Legacy applies the same table here — {@code ErpDataRefreshHelper:546-549} (scheduled) and
     * {@code :635-638} (overseas), both {@code sanitize} → {@code hasVietnameseCharacters} →
     * {@code StringUtils.replaceTone}. This method drops the legacy {@code hasVietnameseCharacters}
     * gate deliberately, for the reason recorded as <b>D-L</b> on {@link VietnameseText}: the gate's
     * three ranges exclude {@code U+01A0-U+01B0} ({@code ƠơƯư}), which its own table maps, so a name
     * like {@code Sơn} or {@code Tư} would pass through unstripped.
     *
     * <p><b>This replaced an NFD-based implementation</b> ({@code Normalizer.NFD} + {@code \p{M}} strip
     * + explicit {@code đ/Đ}). The two agree across the entire Vietnamese repertoire — including
     * {@code Đ/đ}, which the NFD version special-cased because {@code U+0110/U+0111} have no canonical
     * decomposition, and including {@code Ơơ Ưư}, whose horn <i>is</i> a combining mark ({@code U+031B})
     * and therefore does decompose. They diverge only on <b>non-Vietnamese Latin</b>: NFD stripped
     * {@code Müller→Muller} and {@code Škoda→Skoda}, whereas the table leaves both unchanged because
     * neither character is among its 134 entries.
     *
     * <p>That divergence is resolved in favour of the table on the project's legacy-first rule: the
     * table is what legacy dispatches, and one tone implementation is better than two that agree
     * "mostly". The consequence is real and is <b>not</b> silently absorbed — an ERP counterparty name
     * carrying non-Vietnamese Latin diacritics now reaches {@code TB_PAY_FILE_DATA} (and the wire) with
     * those characters intact, exactly as legacy sends them. That is the same defect family as D-L
     * (pitfall ④: the bank refuses non-ASCII), it predates this change on every other entry path, and
     * it belongs to the wire-charset decision — not to this importer alone.
     *
     * <p>Null-safe: {@code null} in, {@code null} out (legacy leaves a blank {@code ACET_NM} untouched
     * and stores it as-is). {@link VietnameseText#replaceTone} maps {@code null} to {@code ""}, which
     * would turn a legacy {@code NULL} {@code RCPT_NM} into an empty string, so the guard stays.
     */
    private static String stripTone(String s) {
        return s == null ? null : VietnameseText.replaceTone(s);
    }
}
