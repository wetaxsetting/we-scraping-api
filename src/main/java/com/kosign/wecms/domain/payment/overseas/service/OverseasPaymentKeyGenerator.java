package com.kosign.wecms.domain.payment.overseas.service;

import com.kosign.wecms.domain.payment.overseas.repository.OverseasPaymentKeySeqRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

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
}
