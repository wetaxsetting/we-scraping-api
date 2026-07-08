package com.kosign.wecms.common.memo.dto;

import java.util.List;

/** Memo side-panel envelope — flat list (no pagination; the legacy
 *  panel scrolls in-place and the typical thread is &lt; 20 rows). */
public record MemoListResponse(int totalCount, List<MemoRow> rows) {
}
