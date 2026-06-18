package com.kosign.wecms.domain.payment.vendorpayment.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrRepository;
import com.kosign.wecms.domain.setting.erptransactionhistory.service.ErpTrnsHistoryWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** F2 download 'W' audit (code 04) — scoped to dispatched (trnf_yn='Y'). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VendorPaymentResultDownloadAuditTest {
    private static final String BIZ_KEY = "VN_VNM0004_000001";
    @Mock private ScdulPymnSumrRepository sumrRepository;
    @Mock private VendorPaymentFileResolver fileResolver;
    @Mock private ErpTrnsHistoryWriter erpHistoryWriter;
    @InjectMocks private VendorPaymentService service;
    @TempDir Path tmp;
    @BeforeEach void setUp() { TenantContext.set("tester","tester",BIZ_KEY,LangCd.EN,TenantContext.AUTH_GR_USER,"F"); }
    @AfterEach void tearDown() { TenantContext.clear(); }
    private ScdulPymnSumr master(String trnfYn) {
        ScdulPymnSumr s = new ScdulPymnSumr();
        s.setBizKey(BIZ_KEY); s.setScdulPymnNo("SP1");
        s.setSaveFileNm("save.xlsx"); s.setOrgFileNm("orig.xlsx"); s.setTrnfYn(trnfYn);
        return s;
    }
    private Path realFile() throws IOException { return Files.createFile(tmp.resolve("save.xlsx")); }
    @Test void dispatched_download_writes_one_W_row() throws IOException {
        when(sumrRepository.findByBizKeyAndScdulPymnNoAndScdulPymnTypeCd(eq(BIZ_KEY), eq("SP1"), anyString())).thenReturn(Optional.of(master("Y")));
        when(fileResolver.resolveForRead(BIZ_KEY, "save.xlsx")).thenReturn(realFile());
        service.resolveDownload("SP1");
        verify(erpHistoryWriter, times(1)).recordExportedFile(eq("04"), any(Path.class), eq("orig.xlsx"));
    }
    @Test void non_dispatched_download_writes_no_row() throws IOException {
        when(sumrRepository.findByBizKeyAndScdulPymnNoAndScdulPymnTypeCd(eq(BIZ_KEY), eq("SP1"), anyString())).thenReturn(Optional.of(master("N")));
        when(fileResolver.resolveForRead(BIZ_KEY, "save.xlsx")).thenReturn(realFile());
        service.resolveDownload("SP1");
        verify(erpHistoryWriter, never()).recordExportedFile(anyString(), any(), anyString());
    }
}
