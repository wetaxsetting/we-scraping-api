package com.kosign.wecms.domain.payment.dispatch.recipient;

import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmTran;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmTranId;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmTranRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FirmTranInquiryAuditService} (C1 audit lifecycle).
 * Verifies the C001 insert column mapping (TRAN_TP='201', PROC_STAT='6') and the
 * U002 coalesce semantics — including the parity guard that {@code ANT_DIV_CD} is
 * NOT written by the result update. Repository mocked → no DB.
 */
@ExtendWith(MockitoExtension.class)
class FirmTranInquiryAuditServiceTest {

    private static final String TRAN_DT = "20240101";
    private static final String BIZ_KEY = "VN_VNM0004_000001";
    private static final String SEQ = "4242";

    @Mock TbFirmTranRepository repository;
    @InjectMocks FirmTranInquiryAuditService service;

    private static RecipientLookupCommand cmd() {
        return new RecipientLookupCommand(
                "ORG000001", "970424", "PRTN1", "970436", "A", "12345678901111", "9988776655");
    }

    @Test
    void record_request_inserts_201_row_at_proc_stat_6_with_audit_columns() {
        service.recordRequest(TRAN_DT, BIZ_KEY, SEQ, "101010", "20240101101010", cmd());

        ArgumentCaptor<TbFirmTran> captor = ArgumentCaptor.forClass(TbFirmTran.class);
        verify(repository).saveAndFlush(captor.capture());
        TbFirmTran row = captor.getValue();
        assertThat(row.getTranDt()).isEqualTo(TRAN_DT);
        assertThat(row.getBizKey()).isEqualTo(BIZ_KEY);
        assertThat(row.getTranSeq()).isEqualTo(SEQ);
        assertThat(row.getTranTp()).isEqualTo("201");
        assertThat(row.getProcStat()).isEqualTo("6");
        assertThat(row.getTranTm()).isEqualTo("101010");
        assertThat(row.getRqDttm()).isEqualTo("20240101101010");
        assertThat(row.getVanOrgCd()).isEqualTo("ORG000001");
        assertThat(row.getOutBankCd()).isEqualTo("970424");
        assertThat(row.getBankPrtnId()).isEqualTo("PRTN1");
        assertThat(row.getWdrlActNo()).isEqualTo("9988776655");
        assertThat(row.getTarBankCd()).isEqualTo("970436");
        assertThat(row.getFincMdaDiv()).isEqualTo("A");
        assertThat(row.getFincMdaNo()).isEqualTo("12345678901111");
        assertThat(row.getFincMdaOwnerNm()).isNull(); // resolved by the U002 success update
    }

    @Test
    void record_result_success_updates_status_owner_and_leaves_ant_div_cd_untouched() {
        TbFirmTran existing = new TbFirmTran();
        existing.setTranDt(TRAN_DT);
        existing.setBizKey(BIZ_KEY);
        existing.setTranSeq(SEQ);
        existing.setProcStat("6");
        existing.setAntDivCd("NAPAS"); // legacy U002 omits ANT_DIV_CD — must stay
        when(repository.findById(new TbFirmTranId(TRAN_DT, BIZ_KEY, SEQ)))
                .thenReturn(Optional.of(existing));

        service.recordResult(TRAN_DT, BIZ_KEY, SEQ, "0", "0000", "정상", "  NGUYEN VAN A ");

        verify(repository).save(existing);
        assertThat(existing.getProcStat()).isEqualTo("0");
        assertThat(existing.getRsCd()).isEqualTo("0000");
        assertThat(existing.getRsMsg()).isEqualTo("정상");
        assertThat(existing.getFincMdaOwnerNm()).isEqualTo("NGUYEN VAN A"); // trimmed
        assertThat(existing.getRsDttm()).isNotBlank();
        assertThat(existing.getAntDivCd()).isEqualTo("NAPAS"); // parity guard
    }

    @Test
    void record_result_blank_owner_does_not_overwrite_existing_owner() {
        TbFirmTran existing = new TbFirmTran();
        existing.setProcStat("6");
        existing.setFincMdaOwnerNm("PRIOR NAME");
        when(repository.findById(any())).thenReturn(Optional.of(existing));

        service.recordResult(TRAN_DT, BIZ_KEY, SEQ, "0", "0000", "정상", "");

        // coalesce(nullif(:owner,''), owner) → blank leaves the prior value
        assertThat(existing.getFincMdaOwnerNm()).isEqualTo("PRIOR NAME");
    }

    @Test
    void record_result_fail_sets_proc_stat_3() {
        TbFirmTran existing = new TbFirmTran();
        when(repository.findById(any())).thenReturn(Optional.of(existing));

        service.recordResult(TRAN_DT, BIZ_KEY, SEQ, "3", "G001", "WBE_EFS_GATEWAY_ERROR", null);

        assertThat(existing.getProcStat()).isEqualTo("3");
        assertThat(existing.getRsCd()).isEqualTo("G001");
        assertThat(existing.getRsMsg()).isEqualTo("WBE_EFS_GATEWAY_ERROR");
    }

    @Test
    void record_result_missing_row_is_a_no_op() {
        when(repository.findById(any())).thenReturn(Optional.empty());

        service.recordResult(TRAN_DT, BIZ_KEY, SEQ, "3", "G001", "x", null);

        verify(repository, never()).save(any());
    }
}
