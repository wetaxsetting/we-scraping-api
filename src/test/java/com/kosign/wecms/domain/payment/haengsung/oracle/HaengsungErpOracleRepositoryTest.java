package com.kosign.wecms.domain.payment.haengsung.oracle;

import com.kosign.wecms.domain.payment.haengsung.HaengsungPaymentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HaengsungErpOracleRepositoryTest {

    @Mock JdbcTemplate jdbc;

    private HaengsungErpOracleRepository repo() { return new HaengsungErpOracleRepository(jdbc); }

    @SuppressWarnings("unchecked")
    private String captureMasterSql() {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), any(), any(), any());
        return sql.getValue();
    }

    /* ---------------- read filter + payment-type predicates ---------------- */

    @Test void findMasters_vendor_buildsBaseFilterAndVendorPredicate_withParams() {
        repo().findMasters("0301234567", "20260601", "20260628", HaengsungPaymentType.VENDOR);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> args = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).query(sql.capture(), org.mockito.ArgumentMatchers.<RowMapper<OracleTransMaster>>any(),
                args.capture(), args.capture(), args.capture());

        assertThat(sql.getValue())
                .contains("FROM TB_TRNS_MAST")
                .contains("LAST_STATUS = '00'")
                .contains("BIZ_NO = ?")
                .contains("FILE_DATE BETWEEN ? AND ?")
                .contains("FILE_GB NOT IN ('AF1','AC2')")
                .contains("FILE_NM NOT LIKE 'E%'");
        assertThat(args.getAllValues()).containsExactly("0301234567", "20260601", "20260628");
    }

    @Test void findMasters_overseas_usesAf1() {
        repo().findMasters("t", "20260628", "20260628", HaengsungPaymentType.OVERSEAS);
        assertThat(captureMasterSql()).contains("FILE_GB = 'AF1'");
    }

    @Test void findMasters_payroll_usesAc2() {
        repo().findMasters("t", "20260628", "20260628", HaengsungPaymentType.PAYROLL);
        assertThat(captureMasterSql()).contains("FILE_GB = 'AC2'");
    }

    @Test void findMasters_expense_usesFileNmLike_M2() {
        repo().findMasters("t", "20260628", "20260628", HaengsungPaymentType.EXPENSE);
        assertThat(captureMasterSql()).contains("FILE_NM LIKE 'E%'");
    }

    @Test void findMasters_all_hasNoFileGbNarrowing() {
        repo().findMasters("t", "20260628", "20260628", HaengsungPaymentType.ALL);
        String sql = captureMasterSql();
        assertThat(sql).doesNotContain("FILE_GB =");
        assertThat(sql).doesNotContain("FILE_NM LIKE");
    }

    @Test void findDetails_filtersEndGbZero() {
        repo().findDetails("20260628", "00000001");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), org.mockito.ArgumentMatchers.<RowMapper<OracleTransDetail>>any(),
                any(), any());
        assertThat(sql.getValue())
                .contains("FROM TB_TRNS_DETAIL")
                .contains("END_GB = '0'");
    }

    /* ---------------- write-back: only receive columns, never LAST_STATUS / PROC_STS ---------------- */

    @Test void markMasterReceived_setsReceiveFlag_neverLastStatusOrEndGb() {
        repo().markMasterReceived("20260628", "00000001", "20260628120000");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), any(), any(), any(), any(), any(), any());
        String s = sql.getValue();
        assertThat(s)
                .contains("UPDATE TB_TRNS_MAST")
                .contains("ERP_RCV_FLAG = 'Y'")
                .contains("ERP_RCV_FLAG_USR")
                .contains("IHB_UDT_USR");
        assertThat(s).doesNotContain("LAST_STATUS");
        assertThat(s).doesNotContain("END_GB");
        assertThat(s).doesNotContain("PROC_STS");
    }

    @Test void markDetailReceived_setsEndGb2AndFlag_neverProcStsOrLastStatus() {
        repo().markDetailReceived("20260628", "00000001", new BigDecimal("1"), "20260628120000");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        String s = sql.getValue();
        assertThat(s)
                .contains("UPDATE TB_TRNS_DETAIL")
                .contains("END_GB = '2'")
                .contains("FLAG = 'Y'")
                .contains("IF_SND_ID")
                .contains("IHB_UDT_USR");
        assertThat(s).doesNotContain("PROC_STS");
        assertThat(s).doesNotContain("LAST_STATUS");
    }

    /* ---------------- approval-status write-back (Step 4) ---------------- */

    @Test void updateMasterApprovalStatus_setsLastStatusAndAuditOnly_noDispatchColumns() {
        repo().updateMasterApprovalStatus("20260628", "00000001", "20", "20260628120000");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), any(), any(), any(), any(), any());
        String s = sql.getValue();
        assertThat(s)
                .contains("UPDATE TB_TRNS_MAST")
                .contains("LAST_STATUS = ?")
                .contains("IHB_UDT_USR = ?")   // audit fields updated
                .contains("IHB_UDT_DT = ?");
        // never receive-flag (import) or dispatch-result columns, and no END_GB/PROC_STS on a non-reject status
        assertThat(s).doesNotContain("ERP_RCV_FLAG");
        assertThat(s).doesNotContain("PROC_STS");
        assertThat(s).doesNotContain("PROC_STS_MSG");
        assertThat(s).doesNotContain("RS_CD");
        assertThat(s).doesNotContain("NOR_");
        assertThat(s).doesNotContain("ERR_");
        assertThat(s).doesNotContain("END_GB");
    }

    @Test void updateMasterRejected_closesBatch_withBulCountsAndAudit() {
        repo().updateMasterRejected("20260628", "00000001", "20260628120000",
                new BigDecimal("2"), new BigDecimal("3000"));
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), any(), any(), any(), any(), any(), any(), any());
        String s = sql.getValue();
        assertThat(s)
                .contains("LAST_STATUS = '32'")
                .contains("END_GB = '1'")
                .contains("END_DATETIME = ?")
                .contains("BUL_CNT = ?")
                .contains("BUL_AMT = ?")
                .contains("IHB_UDT_USR = ?");
        // reject closes the master but does NOT write dispatch-result columns
        assertThat(s).doesNotContain("PROC_STS");
        assertThat(s).doesNotContain("RS_CD");
        assertThat(s).doesNotContain("NOR_");
    }

    @Test void markDetailRejected_setsProcStsXxxxAndEndGb3_notDispatchResultMsg() {
        repo().markDetailRejected("20260628", "00000001", new BigDecimal("1"),
                "20260628120000", "20260628");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), any(), any(), any(), any(), any());
        String s = sql.getValue();
        assertThat(s)
                .contains("UPDATE TB_TRNS_DETAIL")
                .contains("PROC_STS = 'XXXX'")   // reject marker (spec §6) — not a dispatch result
                .contains("END_GB = '3'")
                .contains("IHB_UDT_DT = ?")
                .contains("TR_DATE = ?");
        assertThat(s).doesNotContain("PROC_STS_MSG");  // dispatch-result column never touched
        assertThat(s).doesNotContain("RS_CD");
        assertThat(s).doesNotContain("LAST_STATUS");
    }
}
