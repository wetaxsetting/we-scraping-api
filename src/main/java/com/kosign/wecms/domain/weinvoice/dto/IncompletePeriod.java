package com.kosign.wecms.domain.weinvoice.dto;

/**
 * A month whose GDT list is not fully fetched (UI Spec 3.6 banner).
 *
 * @param month {@code yyyy-MM}
 * @param got   invoices stored for the month
 * @param total invoices GDT listed for the month ({@code null} when GDT did not report it)
 */
public record IncompletePeriod(String month, Integer got, Integer total) {
}
