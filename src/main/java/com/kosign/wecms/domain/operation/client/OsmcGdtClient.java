package com.kosign.wecms.domain.operation.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Outbound client for the Coocon bigdata API
 * ({@code overseas/finance/scrap/bigdata_info.jsp}) — the masothue.com /
 * GDT business-registration lookup. Replaces the legacy
 * {@code WABOOKSOSMCAPIUtil.callApi("TRCO_TRCO001_00003", body, false)}
 * call that the JEX side makes through its OSMC config-table indirection.
 *
 * <p>Wire shape pinned by
 * {@code WECMS DEV/.../kr/co/coocon/sgw/cmo/ApiIn.java}
 * + {@code BI08In.java}
 * + {@code BI08Out.java}
 * + {@code wabooks/helper/TrcoHelper.java} (which sends {@code ApiId="BI09"}
 * and a {@code search} value that can be either a single tax code or an
 * array of tax codes):
 *
 * <pre>{@code
 * Request:
 * {
 *   "ApiKey":    "<wecms.osmc.api-key>",   // <configured-via-WECMS_OSMC_API_KEY>
 *   "ApiId":     "BI09",                    // from wecms.osmc.api-id
 *   "UniqueKey": "<7-digit seq>",
 *   "Input":     { "search": "<taxCode | [taxCode,...]>" }
 * }
 *
 * Response:
 * {
 *   "ErrorCode":      "0000" | "00000000" (success),
 *   "ErrorMessage":   "...",
 *   "UniqueKey":      "...",
 *   "search":         "<echoed>",
 *   "searchDatetime": "<yyyyMMddHHmmss>",
 *   "Result": {
 *     "registrationDetail": [
 *       { "taxCode": "...", "companyName": "...", "companyStatus": "...",
 *         "officeAdd": "...", "representativeFirstName": "...",
 *         "searchDateTime": "..." }
 *     ]
 *   }
 * }
 * }</pre>
 *
 * <p>Connect timeout 10 s, read timeout 60 s — bigdata scrape can take
 * 20–40 s; 60 s covers slow GDT responses without hanging the request
 * thread indefinitely.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OsmcGdtClient {

    /** Default ApiId for the GDT lookup — matches legacy
     *  {@code TrcoHelper.API_ID}. Overridable via {@code wecms.osmc.api-id}. */
    public static final String DEFAULT_API_ID = "BI09";

    private final OsmcProperties properties;
    private final RestTemplateBuilder restTemplateBuilder;
    private final ObjectMapper mapper = new ObjectMapper();

    private RestTemplate restTemplate;

    @PostConstruct
    void init() {
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(60))
                .build();
    }

    /**
     * Invoke the GDT lookup for one or more tax codes. {@code uniqueKey}
     * is the per-request sequence (legacy {@code SEQ_NO} from
     * {@code EXT_TRAN_CALL_HIS}, zero-padded to 7 digits).
     *
     * <p>Sends a single string for one-tax-code calls and a JSON array
     * for multi-tax-code calls — mirrors {@code TrcoHelper.requestBody}.
     */
    public OsmcGdtResult lookup(List<String> taxCodes, String uniqueKey) {
        if (taxCodes == null || taxCodes.isEmpty()) {
            return OsmcGdtResult.error("WBE_OSMC_BAD_INPUT", "No tax codes supplied",
                    List.of(), List.of());
        }
        String url = properties.url();
        if (url == null || url.isBlank()) {
            log.warn("OSMC URL is not configured (wecms.osmc.url) — short-circuiting GDT lookup");
            return OsmcGdtResult.error("WBE_OSMC_NOT_CONFIGURED",
                    "OSMC bridge URL not configured", List.of(), List.of());
        }

        ObjectNode body = buildRequest(taxCodes, uniqueKey);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        try {
            if (log.isDebugEnabled()) {
                log.debug("OSMC GDT request body: {}", body.toString());
            }
            log.info("Calling OSMC GDT lookup. url={}, taxCodes={}, uniqueKey={}",
                    url, taxCodes, uniqueKey);
            String raw = restTemplate.postForObject(
                    url, new HttpEntity<>(body.toString(), headers), String.class);
            if (log.isDebugEnabled()) {
                log.debug("OSMC GDT raw response: {}", raw);
            }
            if (raw == null || raw.isBlank()) {
                return OsmcGdtResult.error("WBE_OSMC_EMPTY", "Empty OSMC response",
                        List.of(), List.of());
            }
            return parseResponse(raw);
        } catch (Exception e) {
            log.error("OSMC GDT lookup failed. url={}, taxCodes={}", url, taxCodes, e);
            return OsmcGdtResult.error("WBE_OSMC_TRANSPORT",
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(),
                    List.of(), List.of());
        }
    }

    /* ------------------------------------------------------------------
     *  Request build
     * ------------------------------------------------------------------ */

    private ObjectNode buildRequest(List<String> taxCodes, String uniqueKey) {
        ObjectNode input = mapper.createObjectNode();
        if (taxCodes.size() == 1) {
            input.put("search", taxCodes.get(0));
        } else {
            ArrayNode arr = input.putArray("search");
            taxCodes.forEach(arr::add);
        }

        ObjectNode body = mapper.createObjectNode();
        body.put("ApiKey",    nullToEmpty(properties.apiKey()));
        body.put("ApiId",     resolveApiId());
        body.put("UniqueKey", nullToEmpty(uniqueKey));
        body.set("Input",     input);
        return body;
    }

    private String resolveApiId() {
        String configured = properties.apiId();
        return (configured == null || configured.isBlank()) ? DEFAULT_API_ID : configured;
    }

    /* ------------------------------------------------------------------
     *  Response parse
     * ------------------------------------------------------------------ */

    private OsmcGdtResult parseResponse(String raw) throws Exception {
        JsonNode root = mapper.readTree(raw);

        // Bigdata API uses either "0000" or "00000000" for success
        // (varies by scraper variant — accept both).
        String errCode = root.path("ErrorCode").asText("");
        String errMsg  = root.path("ErrorMessage").asText("");

        if (!isSuccessCode(errCode)) {
            return OsmcGdtResult.error(errCode, errMsg, List.of(), List.of());
        }

        List<OsmcGdtRegistration> rows = new ArrayList<>();
        List<OsmcGdtSearchOutcome> outcomes = new ArrayList<>();

        // Two response shapes in the wild:
        //   (a) RespData[]  — legacy OSMC wrapper. Each element is a
        //                     per-tax-code envelope with its own
        //                     ErrorCode + searchDatetime + Result.
        //                     Matches aosv_0001_002_r001_act.jsp:107.
        //   (b) Flat Result.registrationDetail — newer single-tax-code
        //                     variant where the wrapper has been
        //                     collapsed.
        JsonNode respData = root.path("RespData");
        if (respData.isArray() && !respData.isEmpty()) {
            for (JsonNode resp : respData) {
                String rowErr = resp.path("ErrorCode").asText("");
                String rowMsg = resp.path("ErrorMessage").asText("");
                String search = resp.path("search").asText("");
                String respSearchDt = resp.path("searchDatetime").asText(
                        resp.path("searchDateTime").asText(""));

                boolean rowOk = isSuccessCode(rowErr) || rowErr.isBlank();
                outcomes.add(new OsmcGdtSearchOutcome(
                        search,
                        rowOk ? "00000000" : rowErr,
                        rowMsg));

                if (rowOk) {
                    collectDetails(resp.path("Result").path("registrationDetail"),
                                   respSearchDt, rows);
                }
                // Per-row failure: outcome recorded above; no detail rows.
                // Mirrors aosv_0001_001_u001_act.jsp:206-221 which writes
                // the audit row from the service layer even when the
                // per-search lookup failed.
            }
        } else {
            // Flat shape — searchDatetime sits at the root. Outcome list
            // stays empty; bulk callers infer per-search status from the
            // overall errCode + the rows() projection.
            String topSearchDt = root.path("searchDatetime").asText(
                    root.path("searchDateTime").asText(""));
            collectDetails(root.path("Result").path("registrationDetail"),
                           topSearchDt, rows);
        }

        // Normalise to the legacy 8-zero success code so the service
        // layer (which checks `isSuccess()` and writes audit rows) stays
        // unchanged across bridge variants.
        return new OsmcGdtResult("00000000", errMsg, rows, outcomes);
    }

    /** Walks a {@code registrationDetail[]} array and appends each row
     *  to {@code out}. The per-row {@code searchDateTime} wins when
     *  present, falling back to the enclosing wrapper's
     *  {@code searchDatetime} (legacy field-casing on the response is
     *  inconsistent — accept both spellings). */
    private void collectDetails(JsonNode details, String fallbackSearchDt,
                                List<OsmcGdtRegistration> out) {
        if (!details.isArray()) return;
        for (JsonNode d : details) {
            out.add(new OsmcGdtRegistration(
                    d.path("taxCode").asText(""),
                    d.path("companyName").asText(""),
                    d.path("companyStatus").asText(""),
                    d.path("officeAdd").asText(""),
                    d.path("representativeFirstName").asText(""),
                    firstNonBlank(
                            d.path("searchDateTime").asText(""),
                            d.path("searchDatetime").asText(""),
                            fallbackSearchDt)
            ));
        }
    }

    /** Bigdata API returns "0000" (4 digits) on success; legacy OSMC
     *  wrapper normalised to "00000000" (8 digits). Accept both. */
    private static boolean isSuccessCode(String code) {
        return "0000".equals(code) || "00000000".equals(code);
    }

    private static String firstNonBlank(String... candidates) {
        for (String s : candidates) if (s != null && !s.isBlank()) return s;
        return "";
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** Single registration row pulled from the GDT response. */
    public record OsmcGdtRegistration(
            String taxCode,
            String companyName,
            String companyStatus,
            String officeAdd,
            String representativeFirstName,
            String searchDatetime
    ) { }

    /** Per-tax-code lookup outcome. Multi-search calls (bulk update) get
     *  one outcome per element in the request's {@code search} array
     *  so the caller can audit per-tax-code failures even when the
     *  overall batch reported success. {@code errorCode} is normalised
     *  to {@code "00000000"} on success. */
    public record OsmcGdtSearchOutcome(
            String taxCode,
            String errorCode,
            String errorMessage
    ) {
        public boolean isSuccess() {
            return "00000000".equals(errorCode);
        }
    }

    /** Container — top-level GDT error code + the flat list of matched
     *  registrations. Single-taxCode callers read {@code rows().get(0)}
     *  after checking {@link #isSuccess()}. Multi-tax-code callers walk
     *  {@link #outcomes()} for per-search status. */
    public record OsmcGdtResult(
            String errorCode,
            String errorMessage,
            List<OsmcGdtRegistration> rows,
            List<OsmcGdtSearchOutcome> outcomes
    ) {
        public boolean isSuccess() {
            return "00000000".equals(errorCode);
        }

        static OsmcGdtResult error(String code, String msg,
                                   List<OsmcGdtRegistration> rows,
                                   List<OsmcGdtSearchOutcome> outcomes) {
            return new OsmcGdtResult(code, msg, rows, outcomes);
        }
    }
}
