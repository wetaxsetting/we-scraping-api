package com.kosign.wecms.domain.payment.dispatch.callback.controller;

import com.kosign.wecms.domain.payment.dispatch.callback.dto.EfsCallbackEnvelope;
import com.kosign.wecms.domain.payment.dispatch.callback.service.BulkExecutionResultService;
import com.kosign.wecms.domain.payment.dispatch.callback.service.BulkReceptionResultService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Routing + DTO-binding tests for the inbound EFS callback controllers —
 * {@code standaloneSetup} (no Spring context, no DB, no security), matching the
 * repo idiom. Proves: path mapping, that the vendor-cased envelope fields
 * ({@code ID}/{@code EV}/{@code VV}/…) bind via {@code @JsonProperty}, and that
 * the controller delegates to its service. No business assertions (services are
 * mocked skeletons).
 */
@ExtendWith(MockitoExtension.class)
class BulkCallbackRoutingTest {

    @Mock
    BulkReceptionResultService bulkReceptionResultService;

    @Mock
    BulkExecutionResultService bulkExecutionResultService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new BulkReceptionResultController(bulkReceptionResultService),
                new BulkExecutionResultController(bulkExecutionResultService)
        ).build();
    }

    @Test
    void receptionResult_route_binds_envelope_and_delegates() throws Exception {
        doNothing().when(bulkReceptionResultService).handle(org.mockito.ArgumentMatchers.any());

        mockMvc.perform(post("/api/v1/payment/dispatch/callbacks/bulk-reception-result")
                        .contentType("application/json")
                        .content("""
                                {"ID":"GV00000002","EV":"ENCRYPTED","VV":"HMAC","EM":"1","VM":"1",
                                 "RQ_DTIME":"20260608120000","TNO":"123","RC":"0000","RM":"OK","CNT":"1"}
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<EfsCallbackEnvelope> captor = ArgumentCaptor.forClass(EfsCallbackEnvelope.class);
        verify(bulkReceptionResultService).handle(captor.capture());
        assertThat(captor.getValue().id()).isEqualTo("GV00000002");
        assertThat(captor.getValue().ev()).isEqualTo("ENCRYPTED");
        assertThat(captor.getValue().rqDtime()).isEqualTo("20260608120000");
    }

    @Test
    void executionResult_route_binds_envelope_and_delegates() throws Exception {
        doNothing().when(bulkExecutionResultService).handle(org.mockito.ArgumentMatchers.any());

        mockMvc.perform(post("/api/v1/payment/dispatch/callbacks/bulk-execution-result")
                        .contentType("application/json")
                        .content("{\"ID\":\"GV00000002\",\"EV\":\"ENC\",\"VV\":\"HMAC\",\"TNO\":\"9\"}"))
                .andExpect(status().isOk());

        verify(bulkExecutionResultService).handle(org.mockito.ArgumentMatchers.any());
    }
}
