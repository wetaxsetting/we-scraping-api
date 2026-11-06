package com.kosign.wecms.domain.weinvoice.dto;

/**
 * Answer of an endpoint that hands work to the engine: poll {@code GET /requests/{request_id}}.
 *
 * @param subject what the request is about (an MST, an invoice id, an upload id)
 */
public record RequestAccepted(String requestId, String subject) {
}
