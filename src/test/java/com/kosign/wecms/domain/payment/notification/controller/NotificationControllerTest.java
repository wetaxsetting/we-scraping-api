package com.kosign.wecms.domain.payment.notification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.exception.GlobalExceptionHandler;
import com.kosign.wecms.common.i18n.MessageResolver;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.notification.dto.NotificationListResponse;
import com.kosign.wecms.domain.payment.notification.dto.NotificationRow;
import com.kosign.wecms.domain.payment.notification.dto.UnreadCountResponse;
import com.kosign.wecms.domain.payment.notification.service.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone-MockMvc slice test for {@link NotificationController}.
 * Verifies the snake_case wire contract end-to-end (request params in +
 * response JSON out), the default {@code read_type=A} / pagination
 * defaults, mark-read / read-all wiring, and the not-found envelope.
 *
 * <p>Mirrors {@code ErpTrnsHistoryControllerTest} — the global SNAKE_CASE
 * Jackson strategy is wired into the MockMvc converter manually so the
 * response bytes match production without a full Spring context.
 */
class NotificationControllerTest {

    private MockMvc mvc;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = mock(NotificationService.class);

        ObjectMapper mapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .registerModule(new JavaTimeModule());
        MappingJackson2HttpMessageConverter jsonConverter =
                new MappingJackson2HttpMessageConverter(mapper);

        MessageResolver messages = mock(MessageResolver.class);
        when(messages.resolve(any(String.class))).thenReturn("Notification not found.");
        when(messages.resolve(any(String.class), any(Object[].class))).thenReturn("Notification not found.");
        GlobalExceptionHandler advice = new GlobalExceptionHandler(messages);

        mvc = MockMvcBuilders
                .standaloneSetup(new NotificationController(service))
                .setMessageConverters(jsonConverter)
                .setControllerAdvice(advice)
                .build();

        TenantContext.set("approver1@example.com", "VN_VNM0004_000001", LangCd.EN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /* =================================================================
     *  List
     * ================================================================= */

    @Test
    void list_happyPath_returnsSnakeCaseWireFields() throws Exception {
        NotificationRow row = new NotificationRow(
                42L, "000000000000000", 42L,
                "Added a new approval.", "",
                "requestor@example.com", "Requestor Name",
                "{\"POST_SEQ_NO\":7}", "comm_0193_001.act",
                "N", false, "N",
                "20260614030405", "20260614030405",
                "/api/v1/users/requestor@example.com/image");
        NotificationListResponse payload =
                new NotificationListResponse(List.of(row), 1L, 1L, 0, 20, 1);

        when(service.list(eq("A"), isNull(), eq(0), eq(0))).thenReturn(payload);

        mvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(false)))
                .andExpect(jsonPath("$.status.code",  is("WBI0000")))
                .andExpect(jsonPath("$.data.total_count",  is(1)))
                .andExpect(jsonPath("$.data.unread_count", is(1)))
                .andExpect(jsonPath("$.data.page_number",  is(0)))
                .andExpect(jsonPath("$.data.page_size",    is(20)))
                .andExpect(jsonPath("$.data.list[0].notification_id", is(42)))
                .andExpect(jsonPath("$.data.list[0].noti_seq_no",     is(42)))
                .andExpect(jsonPath("$.data.list[0].bltb_cd",         is("000000000000000")))
                .andExpect(jsonPath("$.data.list[0].title",           is("Added a new approval.")))
                .andExpect(jsonPath("$.data.list[0].snd_name",        is("Requestor Name")))
                .andExpect(jsonPath("$.data.list[0].noti_wsvc",       is("comm_0193_001.act")))
                .andExpect(jsonPath("$.data.list[0].del_yn",          is("N")))
                .andExpect(jsonPath("$.data.list[0].read",            is(false)))
                .andExpect(jsonPath("$.data.list[0].actv_yn",         is("N")))
                .andExpect(jsonPath("$.data.list[0].user_img_url",    is("/api/v1/users/requestor@example.com/image")));
    }

    @Test
    void list_readsParamsFromSnakeCaseWireNames() throws Exception {
        when(service.list(eq("U"), eq("sok"), eq(2), eq(50)))
                .thenReturn(new NotificationListResponse(List.of(), 0L, 0L, 2, 50, 0));

        mvc.perform(get("/api/v1/notifications")
                        .param("read_type", "U")
                        .param("search",    "sok")
                        .param("page",      "2")
                        .param("size",      "50"))
                .andExpect(status().isOk());

        verify(service).list("U", "sok", 2, 50);
    }

    @Test
    void list_defaultsReadTypeToAll() throws Exception {
        when(service.list(eq("A"), isNull(), anyInt(), anyInt()))
                .thenReturn(new NotificationListResponse(List.of(), 0L, 0L, 0, 20, 0));

        mvc.perform(get("/api/v1/notifications")).andExpect(status().isOk());

        verify(service).list("A", null, 0, 0);
    }

    /* =================================================================
     *  Unread count
     * ================================================================= */

    @Test
    void unreadCount_returnsTotalCount() throws Exception {
        when(service.unreadCount()).thenReturn(new UnreadCountResponse(5L));

        mvc.perform(get("/api/v1/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(false)))
                .andExpect(jsonPath("$.data.total_count", is(5)));
    }

    /* =================================================================
     *  Mark read / read-all
     * ================================================================= */

    @Test
    void markRead_invokesServiceWithPathId() throws Exception {
        doNothing().when(service).markRead(759L);

        mvc.perform(put("/api/v1/notifications/759/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(false)));

        verify(service).markRead(759L);
    }

    @Test
    void markRead_notFound_envelopesAsWbeNotificationNotFound() throws Exception {
        doThrow(new BusinessException("WBE_NOTIFICATION_NOT_FOUND"))
                .when(service).markRead(9999L);

        mvc.perform(put("/api/v1/notifications/9999/read"))
                // BusinessException → HTTP 200 + status.error=true per the project contract.
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(true)))
                .andExpect(jsonPath("$.status.code",  is("WBE_NOTIFICATION_NOT_FOUND")));
    }

    @Test
    void markAllRead_invokesService() throws Exception {
        doNothing().when(service).markAllRead();

        mvc.perform(put("/api/v1/notifications/read-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.error", is(false)));

        verify(service).markAllRead();
    }
}
