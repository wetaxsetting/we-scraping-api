package com.kosign.wecms.domain.weinvoice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * {@code POST /sync-runs} — sync one or several MSTs (screens 03, 04, 06, 10).
 *
 * @param direction {@code both} (default), {@code purchase}, {@code sales}
 * @param fromMonth {@code yyyy-MM}
 * @param toMonth   {@code yyyy-MM}
 * @param trigger   {@code manual} (default) or {@code bulk}
 */
public record SyncRunCreateRequest(
        @NotEmpty @Size(max = 50) List<String> companyMsts,
        String direction,
        @NotBlank String fromMonth,
        @NotBlank String toMonth,
        String trigger) {
}
