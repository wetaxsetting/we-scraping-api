package com.kosign.wecms.domain.banking.dto.coocon;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CooconExchangeRateResponse {
    @JsonProperty("Common")
    private NxisasCommon common;

    @JsonProperty("ResultList")
    private List<ResultItem> resultList;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResultItem {
        @JsonProperty("Output")
        private Output output;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Output {
        @JsonProperty("ErrorCode")
        private String errorCode;

        @JsonProperty("ErrorMessage")
        private String errorMessage;

        @JsonProperty("Result")
        private Result result;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        @JsonProperty("standPoint")
        private String standPoint;

        @JsonProperty("foreignExchange")
        private List<ForeignExchange> foreignExchange;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ForeignExchange {
        @JsonProperty("baseCurrency")
        private String baseCurrency;

        @JsonProperty("counterCurrency")
        private String counterCurrency;

        @JsonProperty("transferSell")
        private String transferSell;

        @JsonProperty("transferBuy")
        private String transferBuy;

        @JsonProperty("buy")
        private String buy;

        @JsonProperty("sell")
        private String sell;

        @JsonProperty("baseDate")
        private String baseDate;

        @JsonProperty("basicRate")
        private String basicRate;

        @JsonProperty("basicTransfer")
        private String basicTransfer;

        @JsonProperty("baseTime")
        private String baseTime;
    }
}
