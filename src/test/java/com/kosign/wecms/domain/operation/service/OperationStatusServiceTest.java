package com.kosign.wecms.domain.operation.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.nxisas.audit.NxsiasModuleTranLogHisRepository;
import com.kosign.wecms.domain.banking.synchistory.repository.ExtTranCallHisRepository;
import com.kosign.wecms.domain.operation.client.OsmcGdtClient;
import com.kosign.wecms.domain.operation.dto.CreateOperationStatusRequest;
import com.kosign.wecms.domain.operation.dto.OperationStatusRow;
import com.kosign.wecms.domain.operation.entity.OprtSttsInfo;
import com.kosign.wecms.domain.operation.entity.OprtSttsInfoId;
import com.kosign.wecms.domain.operation.repository.OprtSttsInfoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Service-layer coverage for the Add-on &gt; Operation Status Inquiry
 * <b>Save / persist</b> path (legacy {@code aosv_0001_002_c001}). Pure
 * Mockito — scope is the {@link OperationStatusService#create} collaborator
 * orchestration: duplicate guard, audit stamping, tenant scoping, and the
 * concurrent-insert race fallback.
 */
@ExtendWith(MockitoExtension.class)
class OperationStatusServiceTest {

    private static final String TENANT = "VN_VNM0004_000001";
    private static final String USER   = "tester";

    @Mock private OprtSttsInfoRepository           repo;
    @Mock private ExtTranCallHisRepository         extTranCallHisRepo;
    @Mock private NxsiasModuleTranLogHisRepository nxisasLogRepo;
    @Mock private OsmcGdtClient                    osmcClient;
    @Mock private com.kosign.wecms.common.legacy.LegacyFunctionRepository legacyFunctions;

    @InjectMocks
    private OperationStatusService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(USER, TENANT, LangCd.EN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static CreateOperationStatusRequest request() {
        return new CreateOperationStatusRequest(
                "0312975013",
                "CÔNG TY TNHH WEBCASH VIỆT NAM",
                "NNT đang hoạt động (đã được cấp GCN ĐKT)",
                "123 Some Street, HCMC",
                "20260603101500",
                "Nguyen Van A");
    }

    @Test
    void create_persistsNewRow_stampsTenantAndAudit() {
        when(repo.existsById(new OprtSttsInfoId(TENANT, "0312975013"))).thenReturn(false);

        OperationStatusRow row = service.create(request());

        ArgumentCaptor<OprtSttsInfo> saved = ArgumentCaptor.forClass(OprtSttsInfo.class);
        verify(repo).save(saved.capture());
        OprtSttsInfo entity = saved.getValue();

        // Tenant comes from TenantContext, never the wire.
        assertThat(entity.getBizKey()).isEqualTo(TENANT);
        assertThat(entity.getTaxcode()).isEqualTo("0312975013");
        assertThat(entity.getCompanystatus()).isEqualTo("NNT đang hoạt động (đã được cấp GCN ĐKT)");
        assertThat(entity.getOfficeadd()).isEqualTo("123 Some Street, HCMC");
        assertThat(entity.getSearchdatetime()).isEqualTo("20260603101500");
        assertThat(entity.getRepresentativefirstname()).isEqualTo("Nguyen Van A");
        // Audit stamped from the JWT-bound user.
        assertThat(entity.getRegId()).isEqualTo(USER);
        assertThat(entity.getRegDtm()).isNotBlank();
        assertThat(entity.getModId()).isEqualTo(USER);

        // Response mirrors the list-row wire shape, incl. derived status_cd.
        assertThat(row.taxcode()).isEqualTo("0312975013");
        assertThat(row.companyNm()).isEqualTo("CÔNG TY TNHH WEBCASH VIỆT NAM");
        assertThat(row.statusCd()).isEqualTo("active_certificate_issued");
        assertThat(row.lastUpdate()).isEqualTo("20260603101500");
    }

    @Test
    void create_blankRepresentative_persists_coalescesToEmpty() {
        when(repo.existsById(new OprtSttsInfoId(TENANT, "1201496252-001"))).thenReturn(false);

        // GDT returns a blank representative for some tax codes (e.g. ceased
        // branches); legacy allows the empty value through, so Save must too.
        CreateOperationStatusRequest req = new CreateOperationStatusRequest(
                "1201496252-001",
                "VĂN PHÒNG ĐẠI DIỆN CÔNG TY CỔ PHẦN WEBCASH VIỆT NAM",
                "NNT ngừng hoạt động và đã hoàn thành thủ tục chấm dứt hiệu lực MST",
                "Căn số B-00.21, TP Hồ Chí Minh",
                "20260505100616",
                "");

        OperationStatusRow row = service.create(req);

        ArgumentCaptor<OprtSttsInfo> saved = ArgumentCaptor.forClass(OprtSttsInfo.class);
        verify(repo).save(saved.capture());
        // Blank in → blank persisted (NOT NULL column satisfied, no validation error).
        assertThat(saved.getValue().getRepresentativefirstname()).isEqualTo("");
        assertThat(row.taxcode()).isEqualTo("1201496252-001");
        // Legacy parity: this exact phrase resolves to the dedicated
        // "Closed - tax code terminated" status, not the retired 3-state
        // "suspended" bucket. See OperationStatusService.resolveStatusCd.
        assertThat(row.statusCd()).isEqualTo("mst_terminated");
    }

    @Test
    void create_duplicateTaxCode_throwsDuplicate_doesNotSave() {
        when(repo.existsById(new OprtSttsInfoId(TENANT, "0312975013"))).thenReturn(true);

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode())
                        .isEqualTo("WBE_OPERATION_STATUS_DUPLICATE"));

        verify(repo, never()).save(any());
    }

    @Test
    void create_concurrentInsertRace_mapsToDuplicate() {
        when(repo.existsById(any())).thenReturn(false);
        when(repo.save(any())).thenThrow(new DataIntegrityViolationException("PK violation"));

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode())
                        .isEqualTo("WBE_OPERATION_STATUS_DUPLICATE"));
    }
}
