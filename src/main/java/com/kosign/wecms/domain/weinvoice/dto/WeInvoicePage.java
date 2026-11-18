package com.kosign.wecms.domain.weinvoice.dto;

import java.util.List;

/** Generic page of rows. {@code page} is zero-based. */
public record WeInvoicePage<T>(List<T> rows, long totalCount, int page, int size) {
}
