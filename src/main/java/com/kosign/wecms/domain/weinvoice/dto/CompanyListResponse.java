package com.kosign.wecms.domain.weinvoice.dto;

import java.util.List;

/** {@code GET /companies}: connections and the tenant quota (UI Spec 3.1, prototype maximum 5). */
public record CompanyListResponse(List<CompanyItem> companies, Quota quota) {

    public record Quota(int used, int max) {
    }
}
