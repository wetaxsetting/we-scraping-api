package com.kosign.wecms.domain.setting.vasaccount.dto;

import java.util.List;

/** Response wrapper for {@code GET /api/v1/settings/vas-account/lookup}. */
public record VasAccountLookupResponse(List<VasAccountLookupRow> items) { }
