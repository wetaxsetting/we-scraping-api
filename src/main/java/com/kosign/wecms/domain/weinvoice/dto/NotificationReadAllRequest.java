package com.kosign.wecms.domain.weinvoice.dto;

/** {@code POST /notifications/read-all} — {@code company_mst} marks one tax code read; absent = every tax code. */
public record NotificationReadAllRequest(String companyMst) {
}
