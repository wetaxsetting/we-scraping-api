package com.kosign.wecms.domain.einvoice.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.auth.entity.BusiInfm;
import com.kosign.wecms.domain.auth.repository.BusiInfmRepository;
import com.kosign.wecms.domain.einvoice.dto.EInvoiceScrapeRequest;
import com.kosign.wecms.domain.einvoice.dto.EInvoiceScrapeResult;
import com.kosign.wecms.domain.banking.synchistory.entity.ExtTranCallHis;
import com.kosign.wecms.domain.banking.synchistory.repository.ExtTranCallHisRepository;
import com.kosign.wecms.domain.einvoice.dto.ScrapedEInvoiceRow;
import com.kosign.wecms.domain.einvoice.entity.BusaDocMstr;
import com.kosign.wecms.domain.einvoice.entity.BusaDocMstrId;
import com.kosign.wecms.domain.einvoice.repository.BusaDocMstrRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Coverage for the scrape-intake save path (legacy
 * {@code nxisas_einvoice_c001_act.jsp} port): branch A / branch B
 * classification, DOC_TYPE + DOC_ID + ISSU_TAX_ID composition, PROV_STTS
 * OP/VD, accept/skip on {@code checkResult}, upsert on re-scrape, and the
 * EXT_TRAN_CALL_HIS audit row (SVC_ID + MAX+1 SEQ_NO).
 */
@ExtendWith(MockitoExtension.class)
class EInvoiceScrapeServiceTest {

    @Mock BusaDocMstrRepository    busaRepo;
    @Mock ExtTranCallHisRepository auditRepo;
    @Mock BusiInfmRepository       busiRepo;
    @Mock com.kosign.wecms.domain.banking.nxisas.audit.NxsiasModuleTranLogHisRepository nxisasLogRepo;
    @Mock com.kosign.wecms.common.legacy.LegacyFunctionRepository legacyFunctions;

    private EInvoiceScrapeService service;

    private static final String BIZ  = "VN_VNM0004_000001";
    private static final String USER = "testuser";
    private static final String FROM = "20240101";
    private static final String TO   = "20240131";
    private static final String COMPANY_TAX = "0301234567";

    @BeforeEach
    void seed() {
        service = new EInvoiceScrapeService(busaRepo, auditRepo, busiRepo, nxisasLogRepo, legacyFunctions);
        TenantContext.set(USER, BIZ, LangCd.EN);
        lenient().when(busaRepo.findById(any(BusaDocMstrId.class))).thenReturn(Optional.empty());
        lenient().when(legacyFunctions.nextExtTranCallHisSeqNo(anyString(), anyString())).thenReturn("1");
        BusiInfm busi = org.mockito.Mockito.mock(BusiInfm.class);
        lenient().when(busi.getTaxId()).thenReturn(COMPANY_TAX);
        lenient().when(busiRepo.findById(BIZ)).thenReturn(Optional.of(busi));
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    // ------------------------------------------------------------------
    // Branch A — type-driven DOC_TYPE, SALES keeps base DOC_ID
    // ------------------------------------------------------------------

    @Test
    void branchA_sales_insertsInvoiceRow_withCompanyTaxAndBaseDocId() {
        EInvoiceScrapeResult res = service.saveScrapedInvoices(
                req("SALES", row("1", "C24TAA", "0000001", "Đã cấp mã hóa đơn", "Hóa đơn mới", "0309999999")));

        BusaDocMstr saved = captureSaved();
        assertThat(saved.getDocType()).isEqualTo("I");
        assertThat(saved.getDocId()).isEqualTo("1C24TAA-0000001");        // no seller-tax suffix
        assertThat(saved.getDispDocId()).isEqualTo("1C24TAA-0000001");
        assertThat(saved.getIssuTaxId()).isEqualTo(COMPANY_TAX);
        assertThat(saved.getHandRegYn()).isEqualTo("N");                  // synced, visible in list
        assertThat(saved.getProvStts()).isEqualTo("OP");
        assertThat(saved.getHandDocId()).isEqualTo("0000001");
        assertThat(saved.getRegId()).isEqualTo(USER);
        assertThat(res.savedCount()).isEqualTo(1);
        assertThat(res.skippedCount()).isZero();
    }

    @Test
    void branchA_expense_appendsSellerTax_andConstIssuTaxId() {
        service.saveScrapedInvoices(
                req("EXPENSE", row("2", "K24XYZ", "0000050", "Tổng cục Thuế đã nhận", "Hóa đơn mới", "0307777777")));

        BusaDocMstr saved = captureSaved();
        assertThat(saved.getDocType()).isEqualTo("B");
        assertThat(saved.getDocId()).isEqualTo("2K24XYZ-0000050-0307777777");
        assertThat(saved.getDispDocId()).isEqualTo("2K24XYZ-0000050");
        assertThat(saved.getIssuTaxId()).isEqualTo("99999999999999999999");
    }

    // ------------------------------------------------------------------
    // Branch B — forces DOC_TYPE 'B' + seller-tax suffix even for SALES
    // ------------------------------------------------------------------

    @Test
    void branchB_forcesBill_evenForSalesRequest() {
        service.saveScrapedInvoices(
                req("SALES", row("1", "C24AAA", "0000009", "Cục Thuế đã nhận", "Hóa đơn mới", "0305555555")));

        BusaDocMstr saved = captureSaved();
        assertThat(saved.getDocType()).isEqualTo("B");
        assertThat(saved.getDocId()).isEqualTo("1C24AAA-0000009-0305555555");
        assertThat(saved.getIssuTaxId()).isEqualTo("99999999999999999999");
    }

    // ------------------------------------------------------------------
    // PROV_STTS OP/VD from invoiceStatus
    // ------------------------------------------------------------------

    @Test
    void voidStatus_branchA_setsProvSttsVD() {
        service.saveScrapedInvoices(
                req("SALES", row("1", "C24AAA", "0000001", "Đã cấp mã hóa đơn", "Hóa đơn đã bị hủy", "0301")));
        assertThat(captureSaved().getProvStts()).isEqualTo("VD");
    }

    @Test
    void voidStatus_branchB_replacedNotVoid_butAdjustedIsVoid() {
        // branch B VD set = {điều chỉnh, hủy}. "thay thế" (replaced) is
        // NOT in branch B's void set → stays OP.
        service.saveScrapedInvoices(
                req("EXPENSE", row("1", "C24AAA", "0000001", "Cục Thuế đã nhận", "Hóa đơn đã bị thay thế", "0301")));
        assertThat(captureSaved().getProvStts()).isEqualTo("OP");

        org.mockito.Mockito.clearInvocations(busaRepo);
        service.saveScrapedInvoices(
                req("EXPENSE", row("1", "C24AAA", "0000002", "Cục Thuế đã nhận", "Hóa đơn đã bị điều chỉnh", "0301")));
        assertThat(captureSaved().getProvStts()).isEqualTo("VD");
    }

    // ------------------------------------------------------------------
    // Accept / skip on checkResult
    // ------------------------------------------------------------------

    @Test
    void unacceptedCheckResult_isSkipped_notSaved() {
        EInvoiceScrapeResult res = service.saveScrapedInvoices(
                req("SALES", row("1", "C24AAA", "0000001", "Some other status", "Hóa đơn mới", "0301")));

        verify(busaRepo, never()).save(any());
        assertThat(res.savedCount()).isZero();
        assertThat(res.skippedCount()).isEqualTo(1);
    }

    @Test
    void emptyInvoiceList_writesAuditOnly() {
        EInvoiceScrapeResult res = service.saveScrapedInvoices(req("SALES"));
        verify(busaRepo, never()).save(any());
        verify(auditRepo).save(any(ExtTranCallHis.class));
        assertThat(res.savedCount()).isZero();
    }

    // ------------------------------------------------------------------
    // Upsert on re-scrape
    // ------------------------------------------------------------------

    @Test
    void reScrape_existingDocId_updatesInsteadOfInsert() {
        BusaDocMstr existing = new BusaDocMstr();
        existing.setBizKey(BIZ);
        existing.setDocId("1C24TAA-0000001");
        existing.setRegId("olduser");
        existing.setRegDtm("20230101000000");
        existing.setTrcoKey("TRCO-9");
        when(busaRepo.findAllById(any())).thenReturn(List.of(existing));

        EInvoiceScrapeResult res = service.saveScrapedInvoices(
                req("SALES", row("1", "C24TAA", "0000001", "Đã cấp mã hóa đơn", "Hóa đơn mới", "0301")));

        BusaDocMstr saved = captureSaved();
        assertThat(saved).isSameAs(existing);
        assertThat(saved.getRegId()).isEqualTo("olduser");        // reg stamp preserved
        assertThat(saved.getTrcoKey()).isEqualTo("TRCO-9");       // existing trcoKey preserved
        assertThat(saved.getModId()).isEqualTo(USER);             // mod stamp refreshed
        assertThat(res.updatedCount()).isEqualTo(1);
        assertThat(res.savedCount()).isZero();
    }

    // ------------------------------------------------------------------
    // Audit row
    // ------------------------------------------------------------------

    @Test
    void audit_salesSvcId_andSeqNoMaxPlusOne() {
        when(legacyFunctions.nextExtTranCallHisSeqNo(eq("GDT_INQUIRY_SALES"), anyString())).thenReturn("8");

        service.saveScrapedInvoices(req("SALES"));

        ArgumentCaptor<ExtTranCallHis> cap = ArgumentCaptor.forClass(ExtTranCallHis.class);
        verify(auditRepo).save(cap.capture());
        ExtTranCallHis h = cap.getValue();
        assertThat(h.getSvcId()).isEqualTo("GDT_INQUIRY_SALES");
        assertThat(h.getWebSvcId()).isEqualTo("addon-einvoices-scrape");
        assertThat(h.getSeqNo()).isEqualByComparingTo("8");
        assertThat(h.getBizKey()).isEqualTo(BIZ);
        assertThat(h.getStartDate()).isEqualTo(FROM);
        assertThat(h.getEndDate()).isEqualTo(TO);
        assertThat(h.getRsltCd()).isEqualTo("00000000");
    }

    @Test
    void audit_expenseSvcId() {
        service.saveScrapedInvoices(req("EXPENSE"));
        ArgumentCaptor<ExtTranCallHis> cap = ArgumentCaptor.forClass(ExtTranCallHis.class);
        verify(auditRepo).save(cap.capture());
        assertThat(cap.getValue().getSvcId()).isEqualTo("GDT_INQUIRY_EXPENSES");
    }

    // ------------------------------------------------------------------
    // static helpers
    // ------------------------------------------------------------------

    @Test
    void parseAmount_stripsGroupingAndHandlesBlank() {
        assertThat(EInvoiceScrapeService.parseAmount("1,000,000")).isEqualByComparingTo("1000000");
        assertThat(EInvoiceScrapeService.parseAmount("  ")).isNull();
        assertThat(EInvoiceScrapeService.parseAmount(null)).isNull();
        assertThat(EInvoiceScrapeService.parseAmount("abc")).isNull();
    }

    @Test
    void normalizeTestResult_keepsOnly1And2() {
        assertThat(EInvoiceScrapeService.normalizeTestResult("1")).isEqualTo("1");
        assertThat(EInvoiceScrapeService.normalizeTestResult("2")).isEqualTo("2");
        assertThat(EInvoiceScrapeService.normalizeTestResult("")).isEqualTo("");
        assertThat(EInvoiceScrapeService.normalizeTestResult(null)).isEqualTo("");
        assertThat(EInvoiceScrapeService.normalizeTestResult("X")).isEqualTo("");
    }

    // ------------------------------------------------------------------

    private BusaDocMstr captureSaved() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BusaDocMstr>> cap = ArgumentCaptor.forClass(List.class);
        verify(busaRepo).saveAll(cap.capture());
        List<BusaDocMstr> saved = cap.getValue();
        return saved.get(saved.size() - 1);
    }

    private static ScrapedEInvoiceRow row(String numeric, String symbol, String no,
                                          String checkResult, String invoiceStatus, String sellerTax) {
        return new ScrapedEInvoiceRow(
                numeric, symbol, no, "20240115",
                "1000000", "909091", "90909", "0",
                invoiceStatus, checkResult, "{}", sellerTax,
                "", "VND", "Seller Co");
    }

    private static EInvoiceScrapeRequest req(String type, ScrapedEInvoiceRow... rows) {
        return new EInvoiceScrapeRequest(
                type, FROM, TO, "00000000", null, null, "NXiSAS", "1.0", List.of(rows));
    }
}
