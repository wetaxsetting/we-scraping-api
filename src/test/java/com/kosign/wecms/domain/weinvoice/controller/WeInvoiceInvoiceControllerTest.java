package com.kosign.wecms.domain.weinvoice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.exception.GlobalExceptionHandler;
import com.kosign.wecms.common.i18n.MessageResolver;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.weinvoice.dto.InvoiceFilter;
import com.kosign.wecms.domain.weinvoice.dto.InvoiceKpis;
import com.kosign.wecms.domain.weinvoice.dto.InvoiceListResponse;
import com.kosign.wecms.domain.weinvoice.service.InvoiceActionService;
import com.kosign.wecms.domain.weinvoice.service.InvoiceExportService;
import com.kosign.wecms.domain.weinvoice.service.InvoiceFileService;
import com.kosign.wecms.domain.weinvoice.service.InvoiceQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Standalone MockMvc (house pattern): snake_case wire, envelope, parameter binding, business errors. */
class WeInvoiceInvoiceControllerTest {

    private MockMvc mvc;
    private InvoiceQueryService queries;

    @BeforeEach
    void setUp() {
        queries = mock(InvoiceQueryService.class);
        ObjectMapper mapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .registerModule(new JavaTimeModule());
        MessageResolver messages = mock(MessageResolver.class);
        when(messages.resolve(any(String.class))).thenReturn("msg");
        when(messages.resolve(any(String.class), any(Object[].class))).thenReturn("msg");
        WeInvoiceInvoiceController controller = new WeInvoiceInvoiceController(queries, mock(InvoiceFileService.class),
                mock(InvoiceActionService.class), mock(InvoiceExportService.class));
        MethodValidationPostProcessor processor = new MethodValidationPostProcessor();
        processor.afterPropertiesSet();
        Object proxied = processor.postProcessAfterInitialization(controller, "weInvoiceInvoiceController");
        mvc = MockMvcBuilders.standaloneSetup(proxied)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .setControllerAdvice(new GlobalExceptionHandler(messages))
                .build();
        TenantContext.set("tester", "VN_VNM0004_000001", LangCd.VI);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void listBindsSnakeCaseParamsAndAnswersInTheEnvelope() throws Exception {
        InvoiceKpis kpis = new InvoiceKpis(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 3, 1, 2, 0, 0,
                BigDecimal.ZERO, BigDecimal.ZERO);
        when(queries.list(any(), anyInt(), anyInt())).thenReturn(new InvoiceListResponse(List.of(), 0, 0, 50, kpis, List.of()));

        mvc.perform(get("/api/v1/weinvoice/invoices")
                        .param("company_mst", "1201496252")
                        .param("direction", "purchase")
                        .param("statuses", "original,cancelled")
                        .param("tag_ids", "1", "2")
                        .param("sort_field", "issue_date"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(false)))
                .andExpect(jsonPath("$.data.total_count", is(0)))
                .andExpect(jsonPath("$.data.kpis.missing_xml", is(3)))
                .andExpect(jsonPath("$.data.incomplete_periods").isArray());

        ArgumentCaptor<InvoiceFilter> filter = ArgumentCaptor.forClass(InvoiceFilter.class);
        verify(queries).list(filter.capture(), anyInt(), anyInt());
        assertThat(filter.getValue().companyMst()).isEqualTo("1201496252");
        assertThat(filter.getValue().statuses()).containsExactly("original", "cancelled");
        assertThat(filter.getValue().tagIds()).containsExactly("1", "2");
        assertThat(filter.getValue().sortField()).isEqualTo("issue_date");
    }

    @Test
    void missingWorkingMstIsValidationEnvelope() throws Exception {
        mvc.perform(get("/api/v1/weinvoice/invoices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(true)))
                .andExpect(jsonPath("$.status.code", is("WBE_VALIDATION")));
    }

    @Test
    void foreignMstIsBusinessEnvelope() throws Exception {
        when(queries.list(any(), anyInt(), anyInt())).thenThrow(new BusinessException("WBE3002", "0101243150"));
        mvc.perform(get("/api/v1/weinvoice/invoices").param("company_mst", "0101243150"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(true)))
                .andExpect(jsonPath("$.status.code", is("WBE3002")));
    }
}
