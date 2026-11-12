package com.kosign.wecms.domain.weinvoice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.exception.GlobalExceptionHandler;
import com.kosign.wecms.common.i18n.MessageResolver;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.weinvoice.dto.NotificationItem;
import com.kosign.wecms.domain.weinvoice.dto.NotificationListResponse;
import com.kosign.wecms.domain.weinvoice.dto.UnreadCountResponse;
import com.kosign.wecms.domain.weinvoice.service.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Wire contract of the bell: snake_case, envelope for GETs, HTTP 204 without body for the two writes. */
class WeInvoiceNotificationControllerTest {

    private MockMvc mvc;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = mock(NotificationService.class);
        ObjectMapper mapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .registerModule(new JavaTimeModule());
        MessageResolver messages = mock(MessageResolver.class);
        when(messages.resolve(any(String.class))).thenReturn("msg");
        when(messages.resolve(any(String.class), any(Object[].class))).thenReturn("msg");
        MethodValidationPostProcessor processor = new MethodValidationPostProcessor();
        processor.afterPropertiesSet();
        Object proxied = processor.postProcessAfterInitialization(new WeInvoiceNotificationController(service),
                "weInvoiceNotificationController");
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
    void listAnswersSnakeCaseInTheEnvelope() throws Exception {
        NotificationItem item = new NotificationItem("PDF_READY|1201496252|purchase|2026111208", "PDF_READY",
                "1201496252", "Webcash", "purchase", 10L, 7L, 3L, null, null, null, null, null,
                "2026-11-12T08:30:05Z", false, false);
        when(service.list("1201496252", "mst", 0, 20)).thenReturn(new NotificationListResponse(List.of(item), 1, 1, 0, 20));

        mvc.perform(get("/api/v1/weinvoice/notifications").param("company_mst", "1201496252").param("filter", "mst"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(false)))
                .andExpect(jsonPath("$.data.unread_count", is(1)))
                .andExpect(jsonPath("$.data.total_count", is(1)))
                .andExpect(jsonPath("$.data.size", is(20)))
                .andExpect(jsonPath("$.data.items[0].id", is("PDF_READY|1201496252|purchase|2026111208")))
                .andExpect(jsonPath("$.data.items[0].company_mst", is("1201496252")))
                .andExpect(jsonPath("$.data.items[0].company_name", is("Webcash")))
                .andExpect(jsonPath("$.data.items[0].from_provider", is(7)))
                .andExpect(jsonPath("$.data.items[0].from_email", is(3)))
                .andExpect(jsonPath("$.data.items[0].mailbox_id", nullValue()))
                .andExpect(jsonPath("$.data.items[0].at", is("2026-11-12T08:30:05Z")))
                .andExpect(jsonPath("$.data.items[0].read", is(false)))
                .andExpect(jsonPath("$.data.items[0].action", is(false)));
    }

    @Test
    void missingWorkingMstIsTheBusinessError() throws Exception {
        when(service.list(isNull(), isNull(), anyInt(), anyInt())).thenThrow(new BusinessException("WBE3037"));
        mvc.perform(get("/api/v1/weinvoice/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(true)))
                .andExpect(jsonPath("$.status.code", is("WBE3037")));
    }

    @Test
    void unreadCount() throws Exception {
        when(service.unreadCount()).thenReturn(new UnreadCountResponse(4));
        mvc.perform(get("/api/v1/weinvoice/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unread_count", is(4)));
    }

    @Test
    void readAnswers204() throws Exception {
        mvc.perform(post("/api/v1/weinvoice/notifications/read").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[\"NEW_INVOICES|1201496252|purchase|2026111208\"]}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(service).markRead(List.of("NEW_INVOICES|1201496252|purchase|2026111208"));
    }

    @Test
    void moreThan100IdsIsAValidationError() throws Exception {
        String ids = IntStream.range(0, 101).mapToObj(i -> "\"x" + i + "\"").collect(Collectors.joining(","));
        mvc.perform(post("/api/v1/weinvoice/notifications/read").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[" + ids + "]}"))
                .andExpect(jsonPath("$.status.error", is(true)))
                .andExpect(jsonPath("$.status.code", is("WBE_VALIDATION")));
        verify(service, never()).markRead(any());
    }

    @Test
    void readAllWithAndWithoutBody() throws Exception {
        mvc.perform(post("/api/v1/weinvoice/notifications/read-all").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"company_mst\":\"1201496252\"}"))
                .andExpect(status().isNoContent());
        verify(service).markAllRead("1201496252");

        mvc.perform(post("/api/v1/weinvoice/notifications/read-all"))
                .andExpect(status().isNoContent());
        verify(service).markAllRead(null);
    }
}
