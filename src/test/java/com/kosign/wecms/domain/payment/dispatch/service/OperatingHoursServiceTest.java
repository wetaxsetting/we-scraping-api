package com.kosign.wecms.domain.payment.dispatch.service;

import com.kosign.wecms.domain.payment.dispatch.entity.TbBatchScheduleDtl;
import com.kosign.wecms.domain.payment.dispatch.entity.TbBatchScheduleInfo;
import com.kosign.wecms.domain.payment.dispatch.repository.TbBatchScheduleDtlRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbBatchScheduleInfoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OperatingHoursService} — the legacy {@code EXE_DIV}
 * operating-hours gate. Uses the package-private {@code nowHms} overloads with a
 * fixed {@code "120000"} so the wall-clock comparison is deterministic.
 */
@ExtendWith(MockitoExtension.class)
class OperatingHoursServiceTest {

    private static final String NOW = "120000"; // 12:00:00
    private static final String BANK = "970457";
    private static final String NAPAS_KEY = "NAPAS_REAL_TIME_BATCH";
    private static final String BATCH1_KEY = "FILE_DEPOSIT_TRANSFER_BATCH1";

    @Mock TbBatchScheduleInfoRepository infoRepository;
    @Mock TbBatchScheduleDtlRepository dtlRepository;
    @InjectMocks OperatingHoursService service;

    private TbBatchScheduleInfo info(String start, String end, String antWhereUseYn) {
        TbBatchScheduleInfo i = new TbBatchScheduleInfo();
        i.setBatchStartTm(start);
        i.setBatchEndTm(end);
        i.setAntWhereUseYn(antWhereUseYn);
        return i;
    }

    private TbBatchScheduleDtl dtl(String start, String end) {
        TbBatchScheduleDtl d = new TbBatchScheduleDtl();
        d.setBatchStartTm(start);
        d.setBatchEndTm(end);
        return d;
    }

    private void infoReturns(TbBatchScheduleInfo i) {
        lenient().when(infoRepository.findByBatchIdAndOutBankCd(eq(NAPAS_KEY), eq(BANK)))
                .thenReturn(Optional.ofNullable(i));
    }

    // ── INFO-level ────────────────────────────────────────────────────────────

    @Test
    void infoMissing_isNoCriteria() {
        infoReturns(null);
        assertThat(service.evaluateBulk(BANK, Set.of(), NOW)).isEqualTo(OperatingHoursDecision.NO_CRITERIA);
        assertThat(service.evaluateRealtime(BANK, "NAPAS", NOW)).isEqualTo(OperatingHoursDecision.NO_CRITERIA);
        verify(dtlRepository, never()).findByBatchIdAndOutBankCdAndAntDivCd(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void infoAfterEnd_isCutoff() {
        infoReturns(info("000000", "010000", "Y")); // end 01:00 < now 12:00 → E
        assertThat(service.evaluateBulk(BANK, Set.of("EBANK"), NOW)).isEqualTo(OperatingHoursDecision.CUTOFF);
        assertThat(service.evaluateRealtime(BANK, "NAPAS", NOW)).isEqualTo(OperatingHoursDecision.CUTOFF);
    }

    @Test
    void infoBeforeStart_isSkip() {
        infoReturns(info("130000", "235959", "Y")); // start 13:00 > now 12:00 → N
        assertThat(service.evaluateBulk(BANK, Set.of("EBANK"), NOW)).isEqualTo(OperatingHoursDecision.SKIP);
        assertThat(service.evaluateRealtime(BANK, "NAPAS", NOW)).isEqualTo(OperatingHoursDecision.SKIP);
    }

    @Test
    void infoInWindow_antWhereUseNotY_isDispatch() {
        infoReturns(info("000000", "235959", "N")); // Y window, other-bank gate off
        assertThat(service.evaluateBulk(BANK, Set.of("EBANK"), NOW)).isEqualTo(OperatingHoursDecision.DISPATCH);
        assertThat(service.evaluateRealtime(BANK, "NAPAS", NOW)).isEqualTo(OperatingHoursDecision.DISPATCH);
    }

    // ── DTL-level ───────────────────────────────────────────────────────────

    @Test
    void batch1_dtlMissing_isDispatch() {
        infoReturns(info("000000", "235959", "Y"));
        when(dtlRepository.findByBatchIdAndOutBankCdAndAntDivCd(BATCH1_KEY, BANK, "EBANK"))
                .thenReturn(Optional.empty());
        assertThat(service.evaluateBulk(BANK, Set.of("EBANK"), NOW)).isEqualTo(OperatingHoursDecision.DISPATCH);
    }

    @Test
    void napas_dtlMissing_isNoCriteria() {
        infoReturns(info("000000", "235959", "Y"));
        when(dtlRepository.findByBatchIdAndOutBankCdAndAntDivCd(NAPAS_KEY, BANK, "NAPAS"))
                .thenReturn(Optional.empty());
        assertThat(service.evaluateRealtime(BANK, "NAPAS", NOW)).isEqualTo(OperatingHoursDecision.NO_CRITERIA);
    }

    @Test
    void dtlAfterEnd_isCutoff() {
        infoReturns(info("000000", "235959", "Y"));
        when(dtlRepository.findByBatchIdAndOutBankCdAndAntDivCd(BATCH1_KEY, BANK, "EBANK"))
                .thenReturn(Optional.of(dtl("000000", "010000"))); // end < now → E
        assertThat(service.evaluateBulk(BANK, Set.of("EBANK"), NOW)).isEqualTo(OperatingHoursDecision.CUTOFF);
    }

    @Test
    void dtlBeforeStart_isSkip() {
        infoReturns(info("000000", "235959", "Y"));
        when(dtlRepository.findByBatchIdAndOutBankCdAndAntDivCd(NAPAS_KEY, BANK, "NAPAS"))
                .thenReturn(Optional.of(dtl("130000", "235959"))); // start > now → N
        assertThat(service.evaluateRealtime(BANK, "NAPAS", NOW)).isEqualTo(OperatingHoursDecision.SKIP);
    }

    @Test
    void dtlInWindow_isDispatch() {
        infoReturns(info("000000", "235959", "Y"));
        when(dtlRepository.findByBatchIdAndOutBankCdAndAntDivCd(BATCH1_KEY, BANK, "EBANK"))
                .thenReturn(Optional.of(dtl("000000", "235959"))); // in window → Y
        assertThat(service.evaluateBulk(BANK, Set.of("EBANK"), NOW)).isEqualTo(OperatingHoursDecision.DISPATCH);
    }

    // ── legacy NAPAS INTER short-circuit + BATCH1 channel precedence ──────────

    @Test
    void napas_interNetwork_skipsDtl_isDispatch() {
        infoReturns(info("000000", "235959", "Y"));
        assertThat(service.evaluateRealtime(BANK, "INTER", NOW)).isEqualTo(OperatingHoursDecision.DISPATCH);
        verify(dtlRepository, never()).findByBatchIdAndOutBankCdAndAntDivCd(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void batch1_nBeatsE_isSkip() {
        infoReturns(info("000000", "235959", "Y"));
        lenient().when(dtlRepository.findByBatchIdAndOutBankCdAndAntDivCd(BATCH1_KEY, BANK, "EBANK"))
                .thenReturn(Optional.of(dtl("000000", "010000"))); // E
        lenient().when(dtlRepository.findByBatchIdAndOutBankCdAndAntDivCd(BATCH1_KEY, BANK, "VCB"))
                .thenReturn(Optional.of(dtl("130000", "235959"))); // N
        // any N present → SKIP regardless of an E on another channel
        assertThat(service.evaluateBulk(BANK, new java.util.LinkedHashSet<>(java.util.List.of("EBANK", "VCB")), NOW))
                .isEqualTo(OperatingHoursDecision.SKIP);
    }

    @Test
    void batch1_antWhereUseY_noChannels_isSkip() {
        infoReturns(info("000000", "235959", "Y")); // window open, other-bank gate on
        // No recognised channel present → legacy never sets exeYN=true → silent skip.
        assertThat(service.evaluateBulk(BANK, Set.of(), NOW)).isEqualTo(OperatingHoursDecision.SKIP);
    }
}
