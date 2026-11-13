package com.kosign.wecms.domain.weinvoice.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.weinvoice.common.CompanyAccessGuard;
import com.kosign.wecms.domain.weinvoice.common.WeInvoiceIds;
import com.kosign.wecms.domain.weinvoice.common.WeInvoiceJson;
import com.kosign.wecms.domain.weinvoice.common.WeInvoiceTime;
import com.kosign.wecms.domain.weinvoice.dto.EngineRequestResponse;
import com.kosign.wecms.domain.weinvoice.repository.EngineRequestRepository;
import com.kosign.wecms.domain.weinvoice.repository.EngineRequestRepository.RequestRow;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;

/**
 * Hands work to the engine and reports on it ({@code winv_engn_rqst}). The engine worker that
 * polls the table is part of the we-scraping repository; until it runs, requests stay
 * {@code queued}.
 */
@Service
public class EngineRequestService {

    public static final String SYNC = "SYNC";
    public static final String FETCH_XML = "FETCH_XML";
    public static final String FETCH_PDF = "FETCH_PDF";
    public static final String LOGIN_TEST = "LOGIN_TEST";
    public static final String IMPORT = "IMPORT";
    public static final String TEST_DATA = "TEST_DATA";

    private final EngineRequestRepository requests;
    private final CompanyAccessGuard guard;
    private final WeInvoiceJson json;

    public EngineRequestService(EngineRequestRepository requests, CompanyAccessGuard guard, WeInvoiceJson json) {
        this.requests = requests;
        this.guard = guard;
        this.json = json;
    }

    /** Queues a request for the current user and returns its id. */
    public long queue(String mst, String type, String trigger, Map<String, Object> params) {
        return requests.insert(guard.ownerId(), mst, type, trigger, json.write(params == null ? Map.of() : params),
                guard.userId());
    }

    public EngineRequestResponse get(String requestId) {
        long id = WeInvoiceIds.parse(requestId, "request_id");
        return toResponse(requests.find(guard.ownerId(), id).orElseThrow(() -> new BusinessException("WBE3011")));
    }

    /** Cancels a queued request or flags a running one; {@code WBE3012} once it has finished. */
    public EngineRequestResponse cancel(long id) {
        String ownerId = guard.ownerId();
        RequestRow row = requests.find(ownerId, id).orElseThrow(() -> new BusinessException("WBE3011"));
        if (requests.cancel(ownerId, id, guard.userId()) == 0) {
            throw new BusinessException("WBE3012");
        }
        return toResponse(requests.find(ownerId, id).orElse(row));
    }

    public EngineRequestResponse toResponse(RequestRow r) {
        return new EngineRequestResponse(
                String.valueOf(r.id()),
                lower(r.type()),
                r.mst(),
                lower(r.trigger()),
                lower(r.status()),
                r.step(),
                json.map(r.paramsJson()),
                json.map(r.progressJson()),
                json.map(r.resultJson()),
                r.error(),
                r.cancelRequested(),
                WeInvoiceTime.dtmToIso(r.regDtm()),
                WeInvoiceTime.dtmToIso(r.startDtm()),
                WeInvoiceTime.dtmToIso(r.endDtm()));
    }

    static String lower(String s) {
        return s == null ? null : s.toLowerCase(Locale.ROOT);
    }
}
