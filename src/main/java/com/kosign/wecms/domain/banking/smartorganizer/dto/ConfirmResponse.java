package com.kosign.wecms.domain.banking.smartorganizer.dto;

/**
 * Confirm result envelope — the frontend uses {@code confirmedCount}
 * to drive the success toast / dialog ("12 transaction(s) confirmed").
 */
public record ConfirmResponse(int confirmedCount) {
}
