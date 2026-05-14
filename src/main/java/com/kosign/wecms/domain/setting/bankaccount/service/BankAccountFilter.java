package com.kosign.wecms.domain.setting.bankaccount.service;

import org.springframework.util.StringUtils;

/**
 * Request filter for {@code GET /api/v1/settings/bank-accounts}. All
 * three fields are optional — null / blank means "no narrowing".
 *
 * <p>The Settings &gt; Bank Account panel currently renders no filter
 * row above the table (Figma 142:18741), but the wire still carries
 * these fields so:
 * <ol>
 *   <li>The Excel-export request can travel with the active filter
 *       map (per the §13b "All filters + current sort must travel"
 *       rule).</li>
 *   <li>A future filter row drops into the panel without an endpoint
 *       contract change.</li>
 * </ol>
 *
 * @param keyword case-insensitive contains match against acct nickname
 *                OR account number; null skips
 * @param bankCd  exact-match {@code BANK_CD}; null skips
 * @param ccyCd   exact-match {@code CCY_CD} ({@code VND} / {@code USD} /
 *                {@code KRW}); null skips
 */
public record BankAccountFilter(
        String keyword,
        String bankCd,
        String ccyCd
) {
    /** Returns a copy with blanks normalised to {@code null}. */
    public BankAccountFilter normalised() {
        return new BankAccountFilter(
                StringUtils.hasText(keyword) ? keyword.trim() : null,
                StringUtils.hasText(bankCd)  ? bankCd.trim()  : null,
                StringUtils.hasText(ccyCd)   ? ccyCd.trim()   : null);
    }
}
