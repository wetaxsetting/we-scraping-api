package com.kosign.wecms.domain.setting.purposesettings.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Request body for {@code PUT /api/v1/settings/purpose-settings/order}.
 * The frontend sends the full ordered list of
 * {@code (use_usag_cd, disp_ord)} pairs in one call; the service
 * verifies no duplicates and updates every row in a single transaction.
 *
 * <p>Service additionally enforces (with {@code WBE_VALIDATION}):
 * <ul>
 *   <li>no duplicate {@code use_usag_cd} across items;</li>
 *   <li>no duplicate {@code disp_ord} across items;</li>
 *   <li>every {@code use_usag_cd} resolves to an active refactor-scope
 *       row for the current tenant.</li>
 * </ul>
 */
public record PurposeReorderRequest(

        @NotEmpty
        @Valid
        List<PurposeReorderItem> items
) { }
