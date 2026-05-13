package com.kosign.wecms.domain.banking.client;

import com.kosign.wecms.config.CooconProperties;
import com.kosign.wecms.domain.banking.dto.coocon.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class CooconApiClient {

    private final CooconProperties cooconProperties;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final DateTimeFormatter DTM_14 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * Calls the Coocon Smart API to fetch exchange rates for a given date, bank module, and category.
     *
     * @param inqDate  The inquiry date (YYYYMMDD)
     * @param seqNo    The sequence number (e.g., "1")
     * @param module   Coocon module string (e.g., "GLOBAL/GBL/BANK_Shinhan")
     * @param category Coocon category string (e.g., "COMMON" for Shinhan, "CORPORATE" for Woori)
     * @return The response DTO or empty if the request fails
     */
    public Optional<CooconExchangeRateResponse> fetchExchangeRates(String inqDate, String seqNo,
                                                                    String module, String category) {
        try {
            NxisasCommon common = NxisasCommon.builder()
                    .authReq("")
                    .gwSysNo("")
                    .lbsNo("")
                    .resultCd("")
                    .resultMsg("")
                    .ssSysNo("")
                    .threadNo("")
                    .authKey(cooconProperties.smartApiKey())
                    .trSeq(LocalDateTime.now().format(DTM_14))
                    .build();

            NxisasJobInput jobInput = NxisasJobInput.builder()
                    .inqDate(inqDate)
                    .seqNo(seqNo)
                    .build();

            NxisasThread<NxisasJobInput> thread = NxisasThread.<NxisasJobInput>builder()
                    .category(category)
                    .module(module)
                    .job("FOREIGN_EXCHANGE")
                    .errorLang("ENG")
                    .input(jobInput)
                    .build();

            NxisasApiIn<NxisasJobInput> apiIn = NxisasApiIn.<NxisasJobInput>builder()
                    .sasSessionID("")
                    .dnsc("S")
                    .common(common)
                    .inputList(Collections.singletonList(thread))
                    .build();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<NxisasApiIn<NxisasJobInput>> request = new HttpEntity<>(apiIn, headers);

            log.info("Calling Coocon Smart API for exchange rates. url={}, inqDate={}", cooconProperties.smartApiUrl(), inqDate);

            // Fetch raw JSON string to log what Coocon actually returns
            String rawResponse = restTemplate.postForObject(
                    cooconProperties.smartApiUrl(),
                    request,
                    String.class
            );

            log.info("RAW Coocon API Response: {}", rawResponse);

            if (rawResponse == null || rawResponse.trim().isEmpty()) {
                return Optional.empty();
            }

            CooconExchangeRateResponse response = objectMapper.readValue(rawResponse, CooconExchangeRateResponse.class);

            return Optional.ofNullable(response);

        } catch (Exception e) {
            log.error("Failed to fetch exchange rates from Coocon API", e);
            return Optional.empty();
        }
    }
}
