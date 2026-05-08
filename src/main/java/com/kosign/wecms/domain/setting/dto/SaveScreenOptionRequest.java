package com.kosign.wecms.domain.setting.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * PUT body for {@code /api/v1/screen-options/{webSvcId}}. The frontend
 * sends the FULL merged column list back — service-side handles the
 * delete-and-insert pattern atomically. Items the user toggled to
 * use_yn='N' are STILL present (with use_yn='N'), not omitted, so the
 * row order is preserved across save.
 *
 * Wire name auto-derived from the camelCase Java field via the global
 * SNAKE_CASE Jackson naming strategy.
 */
public record SaveScreenOptionRequest(
        @NotNull
        @NotEmpty
        List<ScreenOptionItem> items
) { }
