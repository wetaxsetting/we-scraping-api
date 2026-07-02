package com.kosign.wecms.domain.operation.service;

import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.domain.auth.entity.BusiInfm;
import com.kosign.wecms.domain.auth.repository.BusiInfmRepository;
import com.kosign.wecms.domain.banking.synchistory.entity.ExtTranCallHis;
import com.kosign.wecms.domain.banking.synchistory.repository.ExtTranCallHisRepository;
import com.kosign.wecms.domain.einvoice.repository.BusaDocMstrRepository;
import com.kosign.wecms.domain.operation.client.OsmcGdtClient;
import com.kosign.wecms.domain.operation.client.OsmcGdtClient.OsmcGdtRegistration;
import com.kosign.wecms.domain.operation.client.OsmcGdtClient.OsmcGdtResult;
import com.kosign.wecms.domain.operation.entity.OprtSttsInfo;
import com.kosign.wecms.domain.operation.entity.OprtSttsInfoId;
import com.kosign.wecms.domain.operation.repository.OprtSttsInfoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Behaviour + parity tests for {@link OperationStatusBatchService} — the refactor of
 * legacy {@code OPRT_0001_001} (seed) / {@code OPRT_0010_001} (refresh).
 *
 * <p>Covers: seed inserts missing only / skips existing; refresh updates
 * {@code COMPANYSTATUS} only and never inserts; the F/M tenant filter; per-tenant
 * failure isolation; and the structural parity guard that the service has NO
 * notification / NXSIAS collaborator.
 */
@ExtendWith(MockitoExtension.class)
class OperationStatusBatchServiceTest {

    private static final String BIZ = "VN_VNM0004_000001";

    @Mock private BusiInfmRepository busiInfmRepository;
    @Mock private OprtSttsInfoRepository oprtSttsInfoRepo;
    @Mock private ExtTranCallHisRepository extTranCallHisRepo;
    @Mock private BusaDocMstrRepository busaDocMstrRepo;
    @Mock private OsmcGdtClient osmcClient;
    @Mock private LegacyFunctionRepository legacyFunctions;

    @InjectMocks private OperationStatusBatchService service;

    @BeforeEach
    void wireSelf() {
        // Per-tenant @Transactional methods are normally reached through the Spring
        // proxy field `self`; in a plain unit test we point it at the instance so the
        // cross-tenant loop can invoke them as ordinary calls.
        service.self = service;
    }

    private static OsmcGdtResult success(List<OsmcGdtRegistration> rows) {
        return new OsmcGdtResult("00000000", "", rows, List.of());
    }

    private static OsmcGdtRegistration reg(String taxCode, String status) {
        return new OsmcGdtRegistration(taxCode, "Company " + taxCode, status,
                "Addr " + taxCode, "Rep " + taxCode, "20250101000000");
    }

    // ── Seed: insert missing only / skip existing ──────────────────────────────

    @Test
    void seedTenant_insertsMissingRowsOnly_andSkipsExisting() {
        when(busaDocMstrRepo.findDistinctTrcoTaxIdByBizKeyAndHandRegYn(BIZ, "N"))
                .thenReturn(List.of("TAX_NEW", "TAX_EXISTING"));
        when(legacyFunctions.nextExtTranCallHisSeqNo(eq("BI09"), anyString())).thenReturn("5");
        when(osmcClient.lookup(anyList(), anyString()))
                .thenReturn(success(List.of(reg("TAX_NEW", "active"), reg("TAX_EXISTING", "active"))));
        when(oprtSttsInfoRepo.existsById(new OprtSttsInfoId(BIZ, "TAX_NEW"))).thenReturn(false);
        when(oprtSttsInfoRepo.existsById(new OprtSttsInfoId(BIZ, "TAX_EXISTING"))).thenReturn(true);
        when(busiInfmRepository.findById(BIZ)).thenReturn(Optional.of(busi(BIZ, "F")));

        int created = service.seedTenant(BIZ);

        assertThat(created).isEqualTo(1);
        ArgumentCaptor<OprtSttsInfo> saved = ArgumentCaptor.forClass(OprtSttsInfo.class);
        verify(oprtSttsInfoRepo, times(1)).save(saved.capture());
        // Only the missing tax code was inserted; the existing one was skipped.
        assertThat(saved.getValue().getTaxcode()).isEqualTo("TAX_NEW");
        assertThat(saved.getValue().getCompanystatus()).isEqualTo("active");
    }

    @Test
    void seedTenant_noInsert_whenAllTaxCodesAlreadyExist() {
        when(busaDocMstrRepo.findDistinctTrcoTaxIdByBizKeyAndHandRegYn(BIZ, "N"))
                .thenReturn(List.of("TAX_EXISTING"));
        when(legacyFunctions.nextExtTranCallHisSeqNo(eq("BI09"), anyString())).thenReturn("7");
        when(osmcClient.lookup(anyList(), anyString()))
                .thenReturn(success(List.of(reg("TAX_EXISTING", "active"))));
        when(oprtSttsInfoRepo.existsById(new OprtSttsInfoId(BIZ, "TAX_EXISTING"))).thenReturn(true);

        int created = service.seedTenant(BIZ);

        assertThat(created).isZero();
        verify(oprtSttsInfoRepo, never()).save(any());
    }

    // ── Refresh: update COMPANYSTATUS only / never insert ──────────────────────

    @Test
    void refreshTenant_updatesCompanyStatusOnly_preservingOtherColumns() {
        OprtSttsInfo existing = new OprtSttsInfo();
        existing.setBizKey(BIZ);
        existing.setTaxcode("TAX1");
        existing.setCompanyname("ORIGINAL NAME");
        existing.setCompanystatus("OLD STATUS");
        existing.setOfficeadd("ORIGINAL ADDR");
        existing.setRepresentativefirstname("ORIGINAL REP");
        existing.setSearchdatetime("20240101000000");
        existing.setModDtm("20240101000000");

        when(oprtSttsInfoRepo.findAllByBizKey(BIZ)).thenReturn(List.of(existing));
        when(legacyFunctions.nextExtTranCallHisSeqNo(eq("BI09"), anyString())).thenReturn("9");
        // GDT returns CHANGED everything — only status must be applied.
        when(osmcClient.lookup(anyList(), anyString())).thenReturn(success(List.of(
                new OsmcGdtRegistration("TAX1", "CHANGED NAME", "NEW STATUS",
                        "CHANGED ADDR", "CHANGED REP", "20250202000000"))));

        int updated = service.refreshTenant(BIZ);

        assertThat(updated).isEqualTo(1);
        verify(oprtSttsInfoRepo, times(1)).save(existing);
        assertThat(existing.getCompanystatus()).isEqualTo("NEW STATUS");      // updated
        assertThat(existing.getModDtm()).isNotEqualTo("20240101000000");      // updated
        assertThat(existing.getCompanyname()).isEqualTo("ORIGINAL NAME");     // preserved
        assertThat(existing.getOfficeadd()).isEqualTo("ORIGINAL ADDR");       // preserved
        assertThat(existing.getRepresentativefirstname()).isEqualTo("ORIGINAL REP"); // preserved
        assertThat(existing.getSearchdatetime()).isEqualTo("20240101000000"); // preserved
    }

    @Test
    void refreshTenant_neverInsertsMissingRows() {
        OprtSttsInfo existing = new OprtSttsInfo();
        existing.setBizKey(BIZ);
        existing.setTaxcode("TAX1");
        existing.setCompanystatus("OLD");

        when(oprtSttsInfoRepo.findAllByBizKey(BIZ)).thenReturn(List.of(existing));
        when(legacyFunctions.nextExtTranCallHisSeqNo(eq("BI09"), anyString())).thenReturn("11");
        // GDT returns a DIFFERENT, untracked tax code — must NOT be inserted, and the
        // tracked TAX1 (no detail) must NOT be touched.
        when(osmcClient.lookup(anyList(), anyString()))
                .thenReturn(success(List.of(reg("TAX_OTHER", "active"))));

        int updated = service.refreshTenant(BIZ);

        assertThat(updated).isZero();
        verify(oprtSttsInfoRepo, never()).save(any());
    }

    // ── Audit robustness: long OSMC failure code must not overflow RSLT_CD(10) ──

    @Test
    void refreshTenant_longOsmcErrorCode_isTruncatedTo10_soAuditRowSurvives() {
        OprtSttsInfo existing = new OprtSttsInfo();
        existing.setBizKey(BIZ);
        existing.setTaxcode("TAX1");
        existing.setCompanystatus("OLD");

        when(oprtSttsInfoRepo.findAllByBizKey(BIZ)).thenReturn(List.of(existing));
        when(legacyFunctions.nextExtTranCallHisSeqNo(eq("BI09"), anyString())).thenReturn("13");
        // GDT unreachable/misconfigured (SIT headless) → client returns a >10-char code.
        when(osmcClient.lookup(anyList(), anyString()))
                .thenReturn(new OsmcGdtResult("WBE_OSMC_NOT_CONFIGURED",
                        "OSMC bridge URL not configured", List.of(), List.of()));

        int updated = service.refreshTenant(BIZ);

        assertThat(updated).isZero();                 // failed lookup → no status update
        // The audit row must still be written with a RSLT_CD that fits VARCHAR(10),
        // so the per-tenant tx commits instead of silently rolling back.
        ArgumentCaptor<ExtTranCallHis> audit = ArgumentCaptor.forClass(ExtTranCallHis.class);
        verify(extTranCallHisRepo, org.mockito.Mockito.atLeastOnce()).save(audit.capture());
        assertThat(audit.getValue().getRsltCd()).hasSizeLessThanOrEqualTo(10);
        assertThat(audit.getValue().getRsltCd()).isEqualTo("WBE_OSMC_N");
    }

    // ── F/M tenant filter ──────────────────────────────────────────────────────

    @Test
    void refreshAllTenants_usesFmTenantFilter() {
        when(busiInfmRepository.findByDelYnAndBusiSttsAndPrdtTypeIn(
                eq("N"), eq("N"), eq(List.of("F", "M")))).thenReturn(List.of());

        service.refreshAllTenants();

        verify(busiInfmRepository).findByDelYnAndBusiSttsAndPrdtTypeIn("N", "N", List.of("F", "M"));
    }

    @Test
    void seedAllTenants_usesFmTenantFilter() {
        when(busiInfmRepository.findByDelYnAndBusiSttsAndPrdtTypeIn(
                eq("N"), eq("N"), eq(List.of("F", "M")))).thenReturn(List.of());

        service.seedAllTenants();

        verify(busiInfmRepository).findByDelYnAndBusiSttsAndPrdtTypeIn("N", "N", List.of("F", "M"));
    }

    // ── Per-tenant failure isolation ────────────────────────────────────────────

    @Test
    void refreshAllTenants_oneTenantFailure_doesNotAbortOthers() {
        OperationStatusBatchService spy = spy(service);
        spy.self = spy;
        when(busiInfmRepository.findByDelYnAndBusiSttsAndPrdtTypeIn(anyString(), anyString(), anyList()))
                .thenReturn(List.of(busi("BIZ_BAD", "F"), busi("BIZ_OK", "M")));
        doThrow(new RuntimeException("GDT down")).when(spy).refreshTenant("BIZ_BAD");
        doReturn(3).when(spy).refreshTenant("BIZ_OK");

        OperationStatusBatchService.BatchSummary summary = spy.refreshAllTenants();

        // The bad tenant did not abort the run; the good tenant still processed.
        verify(spy).refreshTenant("BIZ_BAD");
        verify(spy).refreshTenant("BIZ_OK");
        assertThat(summary.tenants()).isEqualTo(2);
        assertThat(summary.okTenants()).isEqualTo(1);
        assertThat(summary.failedTenants()).isEqualTo(1);
        assertThat(summary.affectedRows()).isEqualTo(3);
    }

    // ── Parity: no notification / NXSIAS collaborator ───────────────────────────

    @Test
    void service_hasNoNotificationOrNxsiasCollaborator() {
        for (Field f : OperationStatusBatchService.class.getDeclaredFields()) {
            String typeName = f.getType().getName().toLowerCase();
            assertThat(typeName)
                    .as("field %s of type %s", f.getName(), f.getType().getName())
                    .doesNotContain("nxsias")
                    .doesNotContain("nxisas")
                    .doesNotContain("genlnoti")
                    .doesNotContain("notification");
        }
    }

    /* ------------------------------------------------------------------ */

    private static BusiInfm busi(String bizKey, String prdtType) {
        BusiInfm b = new BusiInfm();
        b.setBizKey(bizKey);
        b.setPrdtType(prdtType);
        b.setEml("admin@" + bizKey + ".test");
        return b;
    }
}
