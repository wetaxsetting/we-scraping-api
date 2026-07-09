package com.kosign.wecms.common.memo;

/**
 * Per-parent-row memo rollup for a list grid — the memo count and the
 * row-level sticker color (newest memo's color). Consumed by the memo
 * icon on Card Transactions and Transaction History VND/FCY.
 *
 * <p>{@code color} is null for a row with no memos (the grid renders the
 * neutral/empty icon).
 */
public record MemoAggregate(int count, String color) {

    /** Shared "no memo" rollup — zero count, null color. */
    public static final MemoAggregate EMPTY = new MemoAggregate(0, null);
}
