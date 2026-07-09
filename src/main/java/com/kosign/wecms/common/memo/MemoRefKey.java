package com.kosign.wecms.common.memo;

/**
 * The {@code REF_NO..REF_NO4} coordinate that pins a memo thread to its
 * parent record. Two shapes are supported (see {@link MemoInfmRepository}):
 *
 * <ul>
 *   <li><b>Single-key</b> — {@link #single(String)}: only {@code refNo}
 *       set (Card Transactions, {@code refNo = CARD_SEQ_NO}).</li>
 *   <li><b>4-key</b> — {@link #of(String, String, String, String)}:
 *       all four set (Transaction History VND/FCY — {@code BANK_CD /
 *       ACCT_NO / TRSC_DT / ACCT_SEQ_NO}).</li>
 * </ul>
 *
 * <p>{@link #isSingle()} lets {@code MemoService} pick the matching
 * repository finder — a derived-query {@code AndRefNo2 = null} would emit
 * {@code refNo2 = NULL} (never true), so single-key threads must use the
 * single-key finders, not the 4-key ones with null args.
 */
public record MemoRefKey(String refNo, String refNo2, String refNo3, String refNo4) {

    /** Single-key thread — only {@code REF_NO} populated (Card Transactions). */
    public static MemoRefKey single(String refNo) {
        return new MemoRefKey(refNo, null, null, null);
    }

    /** 4-key thread — Transaction History VND/FCY
     *  ({@code BANK_CD / ACCT_NO / TRSC_DT / ACCT_SEQ_NO}). */
    public static MemoRefKey of(String refNo, String refNo2, String refNo3, String refNo4) {
        return new MemoRefKey(refNo, refNo2, refNo3, refNo4);
    }

    /** True when only {@code refNo} is set (all other coordinates null). */
    public boolean isSingle() {
        return refNo2 == null && refNo3 == null && refNo4 == null;
    }
}
