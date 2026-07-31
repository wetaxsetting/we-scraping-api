package com.kosign.wecms.domain.vatdeclaration.dto;

/**
 * Save-draft payload — carries ONLY the user-editable (white-cell) indicators
 * as integer-VND strings; a {@code null} field leaves the stored value
 * unchanged. Pure formula cells ([27] [28] [34] [35] [36] [40a] [40] [41] [43])
 * are always recomputed server-side by {@code VatIndicatorCalculator} and are
 * never accepted from the client.
 *
 * <p>Editable set mirrors the PO prototype's {@code _vin(...)} cells
 * (docs/vat-declaration/VAT_Declaration_Report.html): [22] [23] [23a] [24]
 * [24a] [25] [26] [29] [30] [31] [32] [32a] [33] [37] [38] [39a] [40b] [42].
 * {@code ct25} / {@code ct31} / {@code ct33} are <b>source</b> cells — prefilled
 * from the rate formula but user-overridable: when the client omits them the
 * calculator derives them ([25]=[24], [31]=[30]x5%, [33]=[32]x10%), when the
 * client sends them the sent value wins (mirrors the prototype's
 * {@code propagate()} which only re-derives on edit of the driver cell).
 *
 * <p>{@code ct21} is the "no purchase/sale activity in the period" <b>flag</b>
 * (prototype renders a checkbox) — send {@code "0"} or {@code "1"}, not an amount.
 */
public record VatDeclarationUpdateRequest(
        String ct21,
        String ct22,
        String ct23,
        String ct24,
        String ct23a,
        String ct24a,
        String ct25,
        String ct26,
        String ct29,
        String ct30,
        String ct31,
        String ct32,
        String ct32a,
        String ct33,
        String ct33a,
        String ct37,
        String ct38,
        String ct39a,
        String ct40b,
        String ct42,
        String ct42a,
        String ct42b,

        // --- Header [06]–[11A]: tax agent + dependent unit ---------------------
        // A {@code null} field leaves the stored value unchanged (same
        // omitted-vs-sent contract as the indicators); a blank string clears it.
        // {@code agentCtrDt} is wire {@code yyyy-MM-dd}.
        String agentNm,       // [06] Tax Agent Name
        String agentTaxCode,  // [07] Agent Tax Code
        String agentCtrNo,    // [08] Tax Agent Contract No.
        String agentCtrDt,    //      Contract Date (yyyy-MM-dd)
        String duNm,          // [09] Dependent Unit / business location in another province
        String duTaxCode,     // [10] Tax Code / business location code
        String duAddr,        // [11] Business address in another province
        String duProv,        // [11C] Province / City
        String duDist,        // [11B] District
        String duWard         // [11A] Ward / Commune
) {
}
