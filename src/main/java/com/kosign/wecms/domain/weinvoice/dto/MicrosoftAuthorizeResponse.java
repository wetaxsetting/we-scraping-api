package com.kosign.wecms.domain.weinvoice.dto;

/**
 * {@code GET /mailboxes/microsoft/authorize}: the Microsoft sign-in URL the browser opens. It carries a
 * signed, 10-minute {@code state}; Microsoft sends the browser back to the backend callback.
 */
public record MicrosoftAuthorizeResponse(String url) {
}
