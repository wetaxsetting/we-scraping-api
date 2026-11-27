package com.kosign.wecms.domain.weinvoice.dto;

import java.util.Map;

/**
 * {@code GET /requests/{request_id}} — an engine request and how far it got.
 *
 * @param type     {@code sync}, {@code fetch_xml}, {@code fetch_pdf}, {@code login_test}, {@code import}, {@code test_data}
 * @param status   {@code queued}, {@code running}, {@code done}, {@code partial}, {@code failed}, {@code cancelled}
 * @param step     engine step, e.g. sync {@code login | captcha | list | xml | done}
 * @param progress engine progress as written by the worker (e.g. {@code month}, {@code listed}, {@code xml_done})
 * @param result   engine result (e.g. sync {@code new}, {@code updated}, {@code xml})
 */
public record EngineRequestResponse(
        String requestId,
        String type,
        String companyMst,
        String trigger,
        String status,
        String step,
        Map<String, Object> params,
        Map<String, Object> progress,
        Map<String, Object> result,
        String error,
        boolean cancelRequested,
        String createdAt,
        String startedAt,
        String finishedAt) {
}
