package com.kosign.wecms.domain.payment.haengsung.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.auth.entity.BusiInfm;
import com.kosign.wecms.domain.auth.repository.BusiInfmRepository;
import com.kosign.wecms.domain.payment.haengsung.HaengsungPaymentType;
import com.kosign.wecms.domain.payment.haengsung.dto.HaengsungRefreshRequest;
import com.kosign.wecms.domain.payment.haengsung.dto.HaengsungRefreshResult;
import com.kosign.wecms.domain.payment.haengsung.oracle.HaengsungErpOracleRepository;
import com.kosign.wecms.domain.payment.haengsung.oracle.OracleTransDetail;
import com.kosign.wecms.domain.payment.haengsung.oracle.OracleTransMaster;
import com.kosign.wecms.domain.setting.erptransactionhistory.service.ErpTrnsHistoryWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ErpPaymentRefreshServiceTest {

    private static final String BIZ = "VN_VNM0004_000014";
    private static final String TAX = "0301234567";

    @Mock HaengsungErpOracleRepository oracle;
    @Mock ErpPaymentRefreshMasterImporter importer;
    @Mock BusiInfmRepository busiInfmRepository;
    @Mock ErpTrnsHistoryWriter auditWriter;

    private ErpPaymentRefreshService service() {
        return new ErpPaymentRefreshService(oracle, importer, busiInfmRepository, auditWriter);
    }

    @AfterEach void tearDown() { TenantContext.clear(); }

    private void loginAs(String prdtType) {
        TenantContext.set("tester", "tester", BIZ, LangCd.EN, TenantContext.AUTH_GR_USER, prdtType);
    }

    private BusiInfm haengsungBusi(String taxId) {
        BusiInfm b = new BusiInfm();
        b.setBizKey(BIZ); b.setPrdtType("H"); b.setDelYn("N"); b.setTaxId(taxId);
        return b;
    }

    private OracleTransMaster master(String fileNum, String erpRcvFlag) {
        return new OracleTransMaster("20260628", fileNum, "AB0", "vendor-001", TAX,
                "11704088", "123", new BigDecimal("1"), new BigDecimal("100"), erpRcvFlag);
    }

    private OracleTransDetail detail(int seq) {
        return new OracleTransDetail(new BigDecimal(seq), new BigDecimal("100"), "VND", "O", "N",
                "A", "1", "9704", "Bank", "addr", "2", "memo", "a2");
    }

    private HaengsungRefreshRequest req() { return new HaengsungRefreshRequest("ALL", "20260628"); }

    /* ---------------- product-type guard ---------------- */

    @Test void nonHaengsung_isBlocked_andOracleNeverTouched() {
        loginAs("F");
        assertThatThrownBy(() -> service().refresh(req()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo("WBE_HS_REFRESH_NOT_HAENGSUNG"));
        verifyNoInteractions(oracle, importer);
    }

    @Test void haengsungJwtButTenantRowMissing_isBlocked() {
        loginAs("H");
        when(busiInfmRepository.findById(BIZ)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().refresh(req()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo("WBE_HS_REFRESH_NOT_HAENGSUNG"));
        verifyNoInteractions(oracle);
    }

    @Test void blankTaxId_isBlocked() {
        loginAs("H");
        when(busiInfmRepository.findById(BIZ)).thenReturn(Optional.of(haengsungBusi("  ")));
        assertThatThrownBy(() -> service().refresh(req()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo("WBE_HS_REFRESH_NO_TAX_ID"));
        verifyNoInteractions(oracle);
    }

    /* ---------------- counts + per-master failure continues ---------------- */

    @Test void counts_importSkipFail_andWriteBackOnlyForImported() {
        loginAs("H");
        when(busiInfmRepository.findById(BIZ)).thenReturn(Optional.of(haengsungBusi(TAX)));

        OracleTransMaster m1 = master("00000001", "N");   // imported
        OracleTransMaster m2 = master("00000002", "N");   // no details → skip
        OracleTransMaster m3 = master("00000003", "N");   // importer throws → failed
        when(oracle.findMasters(eq(TAX), eq("20260628"), eq("20260628"), eq(HaengsungPaymentType.ALL)))
                .thenReturn(List.of(m1, m2, m3));

        when(oracle.findDetails("20260628", "00000001")).thenReturn(List.of(detail(1), detail(2)));
        when(oracle.findDetails("20260628", "00000002")).thenReturn(List.of());
        when(oracle.findDetails("20260628", "00000003")).thenReturn(List.of(detail(1)));

        when(importer.importMaster(eq(BIZ), anyString(), anyString(), eq(m1), any()))
                .thenReturn(MasterImportOutcome.imported(2));
        when(importer.importMaster(eq(BIZ), anyString(), anyString(), eq(m3), any()))
                .thenThrow(new RuntimeException("boom"));

        HaengsungRefreshResult r = service().refresh(req());

        assertThat(r.mastersFound()).isEqualTo(3);
        assertThat(r.imported()).isEqualTo(1);
        assertThat(r.skipped()).isEqualTo(1);
        assertThat(r.failed()).isEqualTo(1);
        assertThat(r.detailsImported()).isEqualTo(2);

        // write-back + audit only for the imported master m1
        verify(oracle, times(1)).markMasterReceived(eq("20260628"), eq("00000001"), anyString());
        verify(oracle, times(2)).markDetailReceived(eq("20260628"), eq("00000001"), any(), anyString());
        verify(oracle, never()).markMasterReceived(eq("20260628"), eq("00000003"), anyString());
        verify(auditWriter, times(1)).recordImportedBatch(anyString(), eq("20260628-00000001"));
    }

    /* ---------------- approval-lock counted as skipped, no write-back ---------------- */

    @Test void approvalLocked_countedSkipped_noWriteBackNoAudit() {
        loginAs("H");
        when(busiInfmRepository.findById(BIZ)).thenReturn(Optional.of(haengsungBusi(TAX)));
        OracleTransMaster m1 = master("00000001", "N");
        when(oracle.findMasters(any(), any(), any(), any())).thenReturn(List.of(m1));
        when(oracle.findDetails("20260628", "00000001")).thenReturn(List.of(detail(1)));
        when(importer.importMaster(any(), any(), any(), eq(m1), any()))
                .thenReturn(MasterImportOutcome.approvalLocked());

        HaengsungRefreshResult r = service().refresh(req());

        assertThat(r.imported()).isZero();
        assertThat(r.skipped()).isEqualTo(1);
        verify(oracle, never()).markMasterReceived(anyString(), anyString(), anyString());
        verify(oracle, never()).markDetailReceived(anyString(), anyString(), any(), anyString());
        verifyNoInteractions(auditWriter);
    }

    /* ---------------- master already received → no master write-back, details still stamped ---------------- */

    @Test void alreadyReceivedMaster_skipsMasterWriteBack_butStampsDetails() {
        loginAs("H");
        when(busiInfmRepository.findById(BIZ)).thenReturn(Optional.of(haengsungBusi(TAX)));
        OracleTransMaster m1 = master("00000009", "Y");   // already received
        when(oracle.findMasters(any(), any(), any(), any())).thenReturn(List.of(m1));
        when(oracle.findDetails("20260628", "00000009")).thenReturn(List.of(detail(1)));
        when(importer.importMaster(any(), any(), any(), eq(m1), any()))
                .thenReturn(MasterImportOutcome.imported(1));

        HaengsungRefreshResult r = service().refresh(req());

        assertThat(r.imported()).isEqualTo(1);
        verify(oracle, never()).markMasterReceived(anyString(), anyString(), anyString());
        verify(oracle, times(1)).markDetailReceived(eq("20260628"), eq("00000009"), any(), anyString());
    }
}
