package com.kosign.wecms.domain.banking.nxisas.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Request to start a scrape against a stored credential. */
public record StartScrapeRequest(
        @NotNull BigDecimal credentialSeqNo
) { }
