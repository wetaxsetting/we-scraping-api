package com.kosign.wecms.domain.payment.overseas.service;

import com.kosign.wecms.domain.payment.overseas.repository.OverseasPaymentKeySeqRepository;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnDtlsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Java-side generator for {@code OVRS_PYMN_SUMR.OVRS_PYMN_NO}.
 * Mirrors {@code InternalTransferKeyGenerator} exactly — same format,
 * same REQUIRES_NEW propagation, independent sequence table.
 *
 * <h2>Format</h2>
 * <pre>{@code OVRS_PYMN_NO = <YYYYMMDD><6-digit-tenant-day-seq>
 * e.g. 20260513000001 }</pre>
 *
 * <p>{@code YYYYMMDD} is Vietnam-local date (UTC+7).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OverseasPaymentKeyGenerator {

    static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final OverseasPaymentKeySeqRepository keySeqRepository;
    private final OvrsPymnDtlsRepository dtlsRepository;
    private final Clock overseasPaymentClock;

    /**
     * Allocate the next {@code OVRS_PYMN_NO} for the given tenant.
     * Runs in its own transaction ({@code REQUIRES_NEW}) so the sequence
     * advance commits even if the outer business transaction rolls back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String nextOvrsPymnNo(String bizKey) {
        if (bizKey == null || bizKey.isBlank()) throw new IllegalArgumentException("bizKey is required");
        LocalDate keyDt = LocalDate.now(overseasPaymentClock.withZone(VIETNAM_ZONE));
        int seq = keySeqRepository.allocateNext(bizKey, keyDt);
        String key = format(keyDt, seq);
        log.debug("feature=overseas-payment allocated ovrs_pymn_no={} bizKey={} keyDt={} seq={}",
                key, bizKey, keyDt, seq);
        return key;
    }

    static String format(LocalDate keyDt, int seq) {
        if (seq < 1 || seq > 999_999) {
            throw new IllegalStateException("overseas-payment key sequence out of range (1..999999): " + seq);
        }
        return String.format("%04d%02d%02d%06d",
                keyDt.getYear(), keyDt.getMonthValue(), keyDt.getDayOfMonth(), seq);
    }

    /**
     * Allocate {@code count} consecutive {@code OVRS_PYMN_DETL_NO} values for a
     * tenant — per-tenant numeric MAX+1, rendered as plain (non-padded) decimal
     * strings. Legacy parity with
     * {@code wb_get_next_val('ovrs_pymn_sumr_detl','ovrs_pymn_detl_no', BIZ_KEY)}
     * (OVR-G1 Phase A; see overseas-detail-no-generation-audit.md).
     *
     * <p>NOT derived from {@code OVRS_PYMN_NO} and NOT the per-batch
     * {@code TRN_SEQ} — the value must be unique within the tenant because the
     * banking-match path uses it ALONE as {@code PAY_TRNS_ID} + the
     * {@code ACCT_TRNS_HSTR} reverse stamp.
     *
     * <p>Runs in the caller's transaction (no {@code REQUIRES_NEW}) so the
     * {@code MAX} read reflects rows committed by other transactions; the whole
     * batch is allocated from one read, so there is no intra-batch collision.
     * Cross-batch concurrency is guarded by the unique index
     * {@code ux_ovrs_pymn_dtls_biz_detl_no} (a concurrent collision rolls the
     * upload back). Values are ≤ 15 chars for any realistic per-tenant volume,
     * fitting {@code OVRS_PYMN_DTLS.OVRS_PYMN_DETL_NO VARCHAR(15)},
     * {@code ACCT_TRNS_HSTR.OVRS_PYMN_DETL_NO VARCHAR(20)} and
     * {@code BUSI_MATCH_LOG.PAY_TRNS_ID VARCHAR(50)}.
     */
    public List<String> nextOvrsPymnDetlNos(String bizKey, int count) {
        if (bizKey == null || bizKey.isBlank()) throw new IllegalArgumentException("bizKey is required");
        if (count <= 0) return List.of();
        long base = dtlsRepository.maxNumericDetlNo(bizKey) + 1L;
        List<String> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(Long.toString(base + i));
        }
        log.debug("feature=overseas-payment allocated ovrs_pymn_detl_no base={} count={} bizKey={}",
                base, count, bizKey);
        return out;
    }
}
