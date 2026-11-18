package com.kosign.wecms.domain.weinvoice.dto;

/** UI Spec {@code Tag} plus the number of invoices carrying it. */
public record TagItem(String id, String name, String color, long invoiceCount) {
}
