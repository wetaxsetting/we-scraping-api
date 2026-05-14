package com.kosign.wecms.domain.setting.logincredentials.dto;

import java.math.BigDecimal;

/**
 * Returned by every Create endpoint — just the newly allocated SEQ_NO
 * so the frontend can re-issue the list call and key its new row.
 */
public record CredentialCreateResponse(BigDecimal seqNo) { }
