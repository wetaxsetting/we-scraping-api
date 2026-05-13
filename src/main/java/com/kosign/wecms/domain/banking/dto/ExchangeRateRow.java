package com.kosign.wecms.domain.banking.dto;

import java.math.BigDecimal;

/**
 * One counter-currency row in the Exchange Rate Inquiry response.
 *
 * <p>Field names are camelCase here; the global Jackson {@code SNAKE_CASE}
 * strategy serialises them as {@code ccy_cd}, {@code basic_rate}, etc.
 * on the wire — matching the frontend {@code ApiExchangeRateRow} wire type.</p>
 *
 * <p>All amount fields may be {@code null} when the source API did not
 * provide a value for that rate type on the requested date.</p>
 */
public record ExchangeRateRow(

        /** Counter currency code, e.g. USD, EUR, JPY. */
        String ccyCd,

        /** Base/reference currency the rates are quoted against, e.g. KRW. */
        String refCcyCd,

        /** Basic (middle) rate. */
        BigDecimal basicRate,

        /** Basic transfer rate. */
        BigDecimal basicTran,

        /** Spot buy rate. */
        BigDecimal buyAmt,

        /** Spot sell rate. */
        BigDecimal sellAmt,

        /** Transfer buy rate (remittance receive). */
        BigDecimal tranBuyAmt,

        /** Transfer sell rate (remittance send). */
        BigDecimal tranSellAmt,

        /** Stand point from the Coocon API resultData. */
        String standPoint,

        /** Last update date from the source — YYYYMMDD. */
        String lastUpdateDate,

        /** Last update time from the source — HHMMSS. */
        String lastUpdateTime,

        /** Datetime the rate was registered in the source — YYYYMMDDHHMMSS. */
        String exchRateRegDtm
) {}
