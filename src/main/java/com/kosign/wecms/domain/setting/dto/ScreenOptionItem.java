package com.kosign.wecms.domain.setting.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One column row inside the View Setting popup. Returned as-is from
 * GET (merged sys+user view) and accepted as-is on PUT.
 *
 * <p>Wire names auto-derived from camelCase Java fields via the global
 * SNAKE_CASE Jackson naming strategy. The field set still maps 1:1 onto
 * the legacy {@code SYS_SCR_OPTION_WECMS / USER_SCR_OPTION_WECMS}
 * columns — they're the same identifiers, just lowercase.
 *
 * @param fieldId    legacy {@code FIELD_ID} — unique column key (e.g.
 *                   {@code BANK_NM}, {@code BAL}). Stable across releases.
 * @param fieldNm    user-visible label. Falls back to the system default
 *                   when the user record doesn't carry one.
 * @param dispOrd    1-based display order. Lower = leftmost column.
 * @param dispYn     {@code "Y"} = column rendered, {@code "N"} = hidden.
 *                   Always sourced from the system row. Refactor-only DEV
 *                   parity addition (V35).
 * @param useYn      {@code "Y"} = visible, {@code "N"} = hidden.
 * @param size       column width hint, e.g. {@code "250px"}. Sourced from
 *                   the system row; users do not set this from the
 *                   popover. Always rendered by the frontend
 *                   {@code <col width>} when present.
 * @param sortYn     {@code "Y"} = column is sortable. Always sourced
 *                   from the system row.
 * @param htmlCont   optional pre-rendered HTML for the column header
 *                   (legacy DEV uses NULL across the board today).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScreenOptionItem(
        String  fieldId,
        String  fieldNm,
        Integer dispOrd,
        String  dispYn,
        String  useYn,
        String  size,
        String  sortYn,
        String  htmlCont
) { }
