package com.kosign.wecms.domain.setting.erptransactionhistory.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.exception.GlobalExceptionHandler;
import com.kosign.wecms.common.export.ExcelExportService;
import com.kosign.wecms.common.i18n.MessageResolver;
import com.kosign.wecms.common.export.ExcelExportService;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.setting.erptransactionhistory.dto.ErpTrnsHistoryListResponse;
import com.kosign.wecms.domain.setting.erptransactionhistory.dto.ErpTrnsHistoryRow;
import com.kosign.wecms.domain.setting.erptransactionhistory.service.ErpTrnsHistoryService;
import com.kosign.wecms.domain.setting.erptransactionhistory.service.ErpTrnsHistoryService.DownloadResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone-MockMvc slice test for {@link ErpTrnsHistoryController}.
 * Verifies the snake_case wire contract end-to-end (request params on
 * the way in + response JSON on the way out), the global exception
 * envelope for missing required params, and the download endpoint's
 * {@code Content-Disposition} + binary body wiring.
 *
 * <p>Mirrors the {@code FirmBankingHistoryControllerTest} pattern —
 * the global SNAKE_CASE strategy is wired into the {@link MockMvc}
 * Jackson converter manually so the response bytes match production
 * exactly without spinning up the full Spring context.
 */
class ErpTrnsHistoryControllerTest {

    private MockMvc mvc;
    private ErpTrnsHistoryService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        service = mock(ErpTrnsHistoryService.class);

        // Mirror production: ObjectMapper with SNAKE_CASE naming
        // strategy + JavaTimeModule. Without snake-case the response
        // would serialise camelCase Java field names verbatim and the
        // snake_case wire assertions would fail; without JavaTimeModule
        // the error envelope's `timestamp` (Instant) would fail to
        // serialise and the @ExceptionHandler chain would fall through
        // to DefaultHandlerExceptionResolver, surfacing as ServletException
        // rather than the standard envelope.
        ObjectMapper mapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .registerModule(new JavaTimeModule());
        MappingJackson2HttpMessageConverter jsonConverter =
                new MappingJackson2HttpMessageConverter(mapper);

        // The download endpoint returns InputStreamResource; the resource
        // converter handles the binary stream. The byte-array converter
        // is added so MockMvc can drain the response body for assertions.
        ResourceHttpMessageConverter resourceConverter = new ResourceHttpMessageConverter();
        ByteArrayHttpMessageConverter byteConverter = new ByteArrayHttpMessageConverter();

        // GlobalExceptionHandler funnels validation failures into the
        // standard envelope; install it so the test exercises the same
        // path production does.
        MessageResolver messages = mock(MessageResolver.class);
        // Lenient stub — any code returns a generic message.
        when(messages.resolve(any(String.class))).thenReturn("ERP transaction history error.");
        when(messages.resolve(any(String.class), any(Object[].class))).thenReturn("ERP transaction history error.");
        GlobalExceptionHandler advice = new GlobalExceptionHandler(messages);

        // ExcelExportService dependency added to ErpTrnsHistoryController in a
        // prior MR; not exercised by this list/download-focused test — pass a
        // pure mock so the constructor resolves.
        mvc = MockMvcBuilders
                .standaloneSetup(new ErpTrnsHistoryController(service, mock(ExcelExportService.class)))
                .setMessageConverters(jsonConverter, resourceConverter, byteConverter)
                .setControllerAdvice(advice)
                .build();

        TenantContext.set("tester", "VN_VNM0004_000001", LangCd.EN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /* =================================================================
     *  List endpoint — happy path + request-param decoding
     * ================================================================= */

    @Test
    void happyPath_returnsSnakeCaseWireFields() throws Exception {
        ErpTrnsHistoryRow row = new ErpTrnsHistoryRow(
                759L,
                "Account Inquiry(VND)_30042026.xls",
                "Account Inquiry(VND)_30042026.xls",
                11264L,
                "W",
                "settings.erp_transaction_history.direction.wecms_to_erp",
                "09",
                "settings.erp_transaction_history.type.09",
                "20260430073409",
                "lamine@gmail.com",
                "Test User");
        ErpTrnsHistoryListResponse payload = new ErpTrnsHistoryListResponse(
                List.of(row), 759L, 533L, 205L, 21L, 0, 15, 51);

        when(service.list(eq("20260101"), eq("20260131"), isNull(), isNull(), isNull(),
                isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(payload);

        mvc.perform(get("/api/v1/settings/erp-transaction-history")
                        .param("from_date", "20260101")
                        .param("to_date",   "20260131"))
                .andExpect(status().isOk())
                // Envelope — snake_case
                .andExpect(jsonPath("$.status.error",   is(false)))
                .andExpect(jsonPath("$.status.code",    is("WBI0000")))
                // List payload — snake_case
                .andExpect(jsonPath("$.data.total_count",         is(759)))
                .andExpect(jsonPath("$.data.total_count_wecms",   is(533)))
                .andExpect(jsonPath("$.data.total_count_erp",     is(205)))
                .andExpect(jsonPath("$.data.total_count_deleted", is(21)))
                .andExpect(jsonPath("$.data.page_number",         is(0)))
                .andExpect(jsonPath("$.data.page_size",           is(15)))
                .andExpect(jsonPath("$.data.total_pages",         is(51)))
                // Row — every wire field in lowercase snake_case
                .andExpect(jsonPath("$.data.list[0].seq_no",         is(759)))
                .andExpect(jsonPath("$.data.list[0].org_file_nm",    is("Account Inquiry(VND)_30042026.xls")))
                .andExpect(jsonPath("$.data.list[0].save_file_nm",   is("Account Inquiry(VND)_30042026.xls")))
                .andExpect(jsonPath("$.data.list[0].file_size",      is(11264)))
                .andExpect(jsonPath("$.data.list[0].erp_type",       is("W")))
                .andExpect(jsonPath("$.data.list[0].erp_type_key",   is("settings.erp_transaction_history.direction.wecms_to_erp")))
                .andExpect(jsonPath("$.data.list[0].trns_type_cd",   is("09")))
                .andExpect(jsonPath("$.data.list[0].trns_type_key",  is("settings.erp_transaction_history.type.09")))
                .andExpect(jsonPath("$.data.list[0].reg_dtm",        is("20260430073409")))
                .andExpect(jsonPath("$.data.list[0].reg_id",         is("lamine@gmail.com")))
                .andExpect(jsonPath("$.data.list[0].user_nm",        is("Test User")));
    }

    @Test
    void requestParams_areReadFromSnakeCaseWireNames() throws Exception {
        when(service.list(eq("20260101"), eq("20260131"),
                eq("W"), eq("09"), eq("lamine"),
                eq("REG_DTM"), eq("DESC"),
                eq(1), eq(20)))
                .thenReturn(emptyResponse());

        mvc.perform(get("/api/v1/settings/erp-transaction-history")
                        .param("from_date",      "20260101")
                        .param("to_date",        "20260131")
                        .param("erp_type",       "W")
                        .param("trns_type_cd",   "09")
                        .param("keyword",        "lamine")
                        .param("sort_field",     "REG_DTM")
                        .param("sort_direction", "DESC")
                        .param("page",           "1")
                        .param("size",           "20"))
                .andExpect(status().isOk());

        verify(service).list("20260101", "20260131",
                "W", "09", "lamine",
                "REG_DTM", "DESC",
                1, 20);
    }

    /* =================================================================
     *  List endpoint — validation envelopes
     * ================================================================= */

    @Test
    void missingFromDate_envelopesAsWbeValidation() throws Exception {
        mvc.perform(get("/api/v1/settings/erp-transaction-history")
                        .param("to_date", "20260131"))
                // Validation envelope is HTTP 200 per the project's
                // "BusinessException → 200 + status.error=true" contract.
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(true)))
                .andExpect(jsonPath("$.status.code",  is("WBE_VALIDATION")));

        verify(service, never()).list(any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void missingToDate_envelopesAsWbeValidation() throws Exception {
        mvc.perform(get("/api/v1/settings/erp-transaction-history")
                        .param("from_date", "20260101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code", is("WBE_VALIDATION")));
    }

    /* =================================================================
     *  Download endpoint
     * ================================================================= */

    @Test
    void download_streamsFileWithContentDispositionAndOctetStream() throws Exception {
        Path file = Files.createTempFile(tempDir, "test-", ".xlsx");
        byte[] payload = "hello erp world".getBytes();
        Files.write(file, payload);

        when(service.resolveDownload(759L))
                .thenReturn(new DownloadResource(file, "original.xlsx", "saved.xlsx", payload.length));

        mvc.perform(get("/api/v1/settings/erp-transaction-history/759/download"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(content().bytes(payload))
                // Filename in Content-Disposition is the SAVED file name
                // (legacy parity with comm_0219_001_r002_view.jsp).
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("saved.xlsx")))
                .andExpect(header().string("X-Content-Type-Options", is("nosniff")))
                .andExpect(header().string("Access-Control-Expose-Headers", containsString(HttpHeaders.CONTENT_DISPOSITION)));
    }

    @Test
    void download_nonPositiveSeqNo_envelopesAsWbeValidation() throws Exception {
        mvc.perform(get("/api/v1/settings/erp-transaction-history/0/download"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(true)))
                .andExpect(jsonPath("$.status.code",  is("WBE_VALIDATION")));
        verify(service, never()).resolveDownload(anyLong());
    }

    @Test
    void download_rowNotFound_envelopesAsWbe2014() throws Exception {
        when(service.resolveDownload(9999L)).thenThrow(new BusinessException("WBE2014"));
        mvc.perform(get("/api/v1/settings/erp-transaction-history/9999/download"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code", is("WBE2014")));
    }

    @Test
    void download_fileMissing_envelopesAsWbe2015() throws Exception {
        when(service.resolveDownload(759L)).thenThrow(new BusinessException("WBE2015"));
        mvc.perform(get("/api/v1/settings/erp-transaction-history/759/download"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code", is("WBE2015")));
    }

    @Test
    void thumbnail_notYetImplemented_envelopesAsWbe9001() throws Exception {
        mvc.perform(get("/api/v1/settings/erp-transaction-history/759/thumbnail"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code", is("WBE9001")));
    }

    /* =================================================================
     *  helpers
     * ================================================================= */

    private static ErpTrnsHistoryListResponse emptyResponse() {
        return new ErpTrnsHistoryListResponse(List.of(), 0L, 0L, 0L, 0L, 0, 15, 0);
    }
}
