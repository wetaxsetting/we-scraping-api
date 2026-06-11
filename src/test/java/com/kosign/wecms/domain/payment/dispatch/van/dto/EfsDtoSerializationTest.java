package com.kosign.wecms.domain.payment.dispatch.van.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.kosign.wecms.domain.payment.dispatch.callback.dto.BulkExecutionResultRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Serialization/deserialization tests for the EFS {@code van/dto} models, using
 * an {@link ObjectMapper} configured exactly like production
 * ({@code JacksonConfig}: {@link PropertyNamingStrategies#SNAKE_CASE}). No
 * Spring context, no DB, no network. ({@code JavaTimeModule} not needed — these
 * DTOs carry no {@code java.time} types.)
 *
 * <p>Proves the two-tier casing contract:
 * <ul>
 *   <li>the <b>envelope</b> keeps exact vendor UPPER-case names via {@code @JsonProperty}
 *       (the global SNAKE_CASE strategy must not touch them);</li>
 *   <li>the <b>business payloads</b> serialize as snake_case via the global strategy alone.</li>
 * </ul>
 */
class EfsDtoSerializationTest {

    /** Mirror of the production mapper (JacksonConfig). */
    private final ObjectMapper mapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    // ── 1. Envelope: exact vendor casing survives SNAKE_CASE ──────────────────

    @Test
    void envelope_preserves_exact_vendor_field_casing() throws Exception {
        EfsEnvelope env = new EfsEnvelope(
                "CORP000001", "20240422103822", "20240422103823", "TNO-1",
                "ENC", "HMAC", "AES", "HmacSHA256", "0000", "OK");

        JsonNode node = mapper.readTree(mapper.writeValueAsString(env));

        assertThat(iterable(node.fieldNames())).containsExactlyInAnyOrder(
                "ID", "RQ_DTIME", "RS_DTIME", "TNO", "EV", "VV", "EM", "VM", "RC", "RM");
        // global SNAKE_CASE must NOT have produced lowercase variants
        assertThat(node.has("rq_dtime")).isFalse();
        assertThat(node.has("rs_dtime")).isFalse();
        assertThat(node.has("id")).isFalse();

        EfsEnvelope back = mapper.readValue(mapper.writeValueAsString(env), EfsEnvelope.class);
        assertThat(back).isEqualTo(env);
        assertThat(back.em()).isEqualTo(EfsEnvelope.EM_AES);
        assertThat(back.vm()).isEqualTo(EfsEnvelope.VM_HMAC_SHA256);
    }

    @Test
    void envelope_request_mode_drops_response_only_fields() throws Exception {
        // request mode: only request fields set; RS_DTIME/RC/RM null → dropped (NON_NULL)
        EfsEnvelope req = new EfsEnvelope(
                "CORP000001", "20240422103822", null, "TNO-1",
                "ENC", "HMAC", "AES", "HmacSHA256", null, null);
        JsonNode node = mapper.readTree(mapper.writeValueAsString(req));
        assertThat(node.has("RQ_DTIME")).isTrue();
        assertThat(node.has("RS_DTIME")).isFalse();
        assertThat(node.has("RC")).isFalse();
        assertThat(node.has("RM")).isFalse();
    }

    // ── 2. Business payloads: snake_case via the global strategy ──────────────

    @Test
    void efs201Request_serializes_snake_case_and_round_trips() throws Exception {
        Efs201Request req = new Efs201Request(
                "970424", "ORG000001", "970424", "A", "12345678901111", "WEBCASH", "5555");
        JsonNode node = mapper.readTree(mapper.writeValueAsString(req));
        assertThat(iterable(node.fieldNames())).containsExactlyInAnyOrder(
                "bank_cd", "prtn_id", "tar_bank_cd", "tar_finc_mda_div", "tar_finc_mda_no",
                "finc_mda_owner_nm", "withd_acct_no");
        assertThat(mapper.readValue(mapper.writeValueAsString(req), Efs201Request.class)).isEqualTo(req);
    }

    @Test
    void efs202Request_serializes_snake_case() throws Exception {
        Efs202Request req = new Efs202Request(
                "970424", "ORG000001", "VND", "1000000", "0", "A", "111", "970457",
                "222", "WEBCASH", "salary", "NAPAS");
        JsonNode node = mapper.readTree(mapper.writeValueAsString(req));
        assertThat(iterable(node.fieldNames())).contains(
                "bank_cd", "prtn_id", "curren_cd", "amt", "dcm_amt", "tar_finc_mda_div",
                "macct_no", "tar_bank_cd", "tar_finc_mda_no", "finc_mda_owner_nm", "tran_use", "ant_div_cd");
        assertThat(mapper.readValue(mapper.writeValueAsString(req), Efs202Request.class)).isEqualTo(req);
    }

    @Test
    void efs203Request_and_response_serialize_snake_case() throws Exception {
        Efs203Request req = new Efs203Request("970424", "ORG000001", "20240422", "TNO-9", "111");
        assertThat(iterable(mapper.readTree(mapper.writeValueAsString(req)).fieldNames()))
                .containsExactlyInAnyOrder("bank_cd", "prtn_id", "org_tran_dt", "org_tno", "macct_no");

        Efs203Response resp = new Efs203Response("20240422", "TNO-9", "103822", "0000", "OK",
                "111", "VND", "1000000", "0", "222", "WEBCASH", "wrmk", "trmk");
        assertThat(iterable(mapper.readTree(mapper.writeValueAsString(resp)).fieldNames()))
                .contains("org_proc_tm", "org_resp_cd", "org_resp_msg", "withd_acct_rmrk", "tar_acct_rmrk");
        assertThat(mapper.readValue(mapper.writeValueAsString(resp), Efs203Response.class)).isEqualTo(resp);
    }

    @Test
    void efs301_and_302_request_serialize_snake_case() throws Exception {
        Efs301Request r301 = new Efs301Request("970424", "ORG000001", "S_FILE_001", "10", "1000000", "0");
        assertThat(iterable(mapper.readTree(mapper.writeValueAsString(r301)).fieldNames()))
                .containsExactlyInAnyOrder("bank_cd", "prtn_id", "file_nm", "data_cnt", "data_amt", "data_dcm_amt");
        assertThat(mapper.readValue(mapper.writeValueAsString(r301), Efs301Request.class)).isEqualTo(r301);

        Efs302Request r302 = new Efs302Request("970424", "ORG000001", "S_FILE_001", "10", "1000000", "0");
        assertThat(iterable(mapper.readTree(mapper.writeValueAsString(r302)).fieldNames()))
                .containsExactlyInAnyOrder("bank_cd", "prtn_id", "file_nm", "data_cnt", "data_amt", "data_dcm_amt");
        assertThat(mapper.readValue(mapper.writeValueAsString(r302), Efs302Request.class)).isEqualTo(r302);
    }

    // ── 3. 01302 callback compatibility: optional TOT/SUCC/FAIL ───────────────

    @Test
    void bulkExecutionResult_deserializes_without_optional_tot_succ_fail() throws Exception {
        String sixBaseFieldsOnly = """
                {"bank_cd":"970424","prtn_id":"ORG000001","file_nm":"S_FILE_001",
                 "data_cnt":"10","data_amt":"1000000","data_dcm_amt":"0"}
                """;
        BulkExecutionResultRequest req = mapper.readValue(sixBaseFieldsOnly, BulkExecutionResultRequest.class);

        assertThat(req.bankCd()).isEqualTo("970424");
        assertThat(req.fileNm()).isEqualTo("S_FILE_001");
        assertThat(req.dataCnt()).isEqualTo("10");
        // PDF §5.12 omits these — must remain null, never fail
        assertThat(req.tot()).isNull();
        assertThat(req.succ()).isNull();
        assertThat(req.fail()).isNull();
    }

    @Test
    void bulkExecutionResult_deserializes_with_legacy_tot_succ_fail_present() throws Exception {
        String withAggregates = """
                {"bank_cd":"970424","prtn_id":"ORG000001","file_nm":"S_FILE_001",
                 "data_cnt":"10","data_amt":"1000000","data_dcm_amt":"0",
                 "TOT":"10","SUCC":"9","FAIL":"1"}
                """;
        BulkExecutionResultRequest req = mapper.readValue(withAggregates, BulkExecutionResultRequest.class);
        assertThat(req.tot()).isEqualTo("10");
        assertThat(req.succ()).isEqualTo("9");
        assertThat(req.fail()).isEqualTo("1");
    }

    private static Iterable<String> iterable(java.util.Iterator<String> it) {
        java.util.List<String> list = new java.util.ArrayList<>();
        it.forEachRemaining(list::add);
        return list;
    }
}
