package com.kosign.wecms.domain.weinvoice.dto;

/**
 * {@code GET /email/address}: the receiving address of one MST, {@code hd-<mst>@<domain>}.
 * {@code configured = false} (and {@code address = null}) when no mail domain is set.
 */
public record EmailAddressResponse(String companyMst, String address, boolean configured) {
}
