package com.kosign.wecms.domain.weinvoice.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.weinvoice.common.CompanyAccessGuard;
import com.kosign.wecms.domain.weinvoice.common.CompanyAccessGuard.InvoiceRef;
import com.kosign.wecms.domain.weinvoice.common.WeInvoiceIds;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceDb;
import com.kosign.wecms.domain.weinvoice.dto.FetchXmlResult;
import com.kosign.wecms.domain.weinvoice.dto.IdsRequest;
import com.kosign.wecms.domain.weinvoice.dto.LookupCodeRequest;
import com.kosign.wecms.domain.weinvoice.dto.RequestAccepted;
import com.kosign.wecms.domain.weinvoice.dto.TagAssignRequest;
import com.kosign.wecms.domain.weinvoice.repository.CompanyConnectionRepository.CompanyRow;
import com.kosign.wecms.domain.weinvoice.repository.InvoiceCriteria;
import com.kosign.wecms.domain.weinvoice.repository.InvoiceQueryRepository;
import com.kosign.wecms.domain.weinvoice.repository.LookupCodeRepository;
import com.kosign.wecms.domain.weinvoice.repository.TagRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Invoice actions of screens 04 / 05 that change state: fetch missing XML, fetch the original
 * PDF now, type a lookup code, tag in bulk. Engine work is queued, never run here.
 */
@Service
public class InvoiceActionService {

    /** Query keys that carry the lookup code in the links providers send (mail / QR). */
    private static final Set<String> CODE_KEYS = Set.of(
            "mtc", "matracuu", "ma_tra_cuu", "code", "sobaomat", "fkey", "keysearch", "sec", "secret",
            "transactionid", "searchinvoice", "mtcuu", "c");

    private final CompanyAccessGuard guard;
    private final InvoiceQueryRepository invoices;
    private final LookupCodeRepository lookupCodes;
    private final TagRepository tags;
    private final EngineRequestService requests;
    private final WeInvoiceDb db;

    public InvoiceActionService(CompanyAccessGuard guard, InvoiceQueryRepository invoices,
                                LookupCodeRepository lookupCodes, TagRepository tags, EngineRequestService requests,
                                WeInvoiceDb db) {
        this.guard = guard;
        this.invoices = invoices;
        this.lookupCodes = lookupCodes;
        this.tags = tags;
        this.requests = requests;
        this.db = db;
    }

    /**
     * "Lấy XML còn thiếu" (UI Spec 3.6): queues coded invoices without XML; invoices without
     * code (symbol K…) are reported, not queued — GDT keeps no XML for them.
     */
    public FetchXmlResult fetchMissingXml(IdsRequest req) {
        CompanyRow company = guard.requireCompany(req.companyMst());
        String bizKey = guard.bizKey();
        List<Long> ids = WeInvoiceIds.parseAll(req.ids(), "ids");
        List<Long> fetchable = invoices.fetchableXmlIds(bizKey, company.mst(), ids);
        long noCode = invoices.kpis(new InvoiceCriteria(bizKey, company.mst(), null, null, null, null, "missing", null,
                List.of(), "no_code", List.of(), null, List.of(), ids, null, false)).count();
        int other = (int) Math.max(0, ids.size() - fetchable.size() - noCode);
        if (fetchable.isEmpty()) {
            return new FetchXmlResult(0, (int) noCode, other, null);
        }
        long requestId = requests.queue(company.mst(), EngineRequestService.FETCH_XML, "MANUAL",
                Map.of("invoice_ids", fetchable));
        return new FetchXmlResult(fetchable.size(), (int) noCode, other, String.valueOf(requestId));
    }

    /** "Lấy PDF gốc ngay": queues the provider lookup for one invoice. */
    public RequestAccepted fetchOriginalPdf(String invoiceId) {
        long id = WeInvoiceIds.parse(invoiceId, "id");
        InvoiceRef ref = guard.requireInvoice(id);
        long requestId = requests.queue(ref.companyMst(), EngineRequestService.FETCH_PDF, "MANUAL",
                Map.of("invoice_ids", List.of(id)));
        return new RequestAccepted(String.valueOf(requestId), String.valueOf(id));
    }

    /**
     * Saves a lookup code typed by a user (UI Spec 3.4 — the input takes the code or the whole
     * link) and queues a PDF fetch with it.
     */
    public RequestAccepted setLookupCode(String invoiceId, LookupCodeRequest req) {
        long id = WeInvoiceIds.parse(invoiceId, "id");
        InvoiceRef ref = guard.requireInvoice(id);
        String input = req.code().trim();
        String url = null;
        String code = input;
        if (input.regionMatches(true, 0, "http://", 0, 7) || input.regionMatches(true, 0, "https://", 0, 8)) {
            url = input;
            code = codeFromLink(input);
        }
        if (code != null && code.length() > 200) {
            throw new BusinessException("WBE3020");
        }
        String companyCode = req.companyCode() == null || req.companyCode().isBlank() ? null : req.companyCode().trim();
        String finalCode = code;
        String finalUrl = url;
        Long requestId = db.tx().execute(s -> {
            lookupCodes.upsert(guard.bizKey(), id, ref.companyMst(), finalCode, companyCode, finalUrl, guard.userId());
            Map<String, Object> params = new HashMap<>();
            params.put("invoice_ids", List.of(id));
            if (finalCode != null) params.put("lookup_code", finalCode);
            if (finalUrl != null) params.put("lookup_url", finalUrl);
            if (companyCode != null) params.put("company_code", companyCode);
            return requests.queue(ref.companyMst(), EngineRequestService.FETCH_PDF, "MANUAL", params);
        });
        return new RequestAccepted(String.valueOf(requestId), String.valueOf(id));
    }

    /** Adds or removes one tag on the invoices the tenant may see; returns how many were targeted. */
    public int assignTag(TagAssignRequest req) {
        String bizKey = guard.bizKey();
        long tagId = WeInvoiceIds.parse(req.tagId(), "tag_id");
        tags.find(bizKey, tagId).orElseThrow(() -> new BusinessException("WBE3013"));
        List<Long> visible = guard.visibleInvoiceIds(WeInvoiceIds.parseAll(req.ids(), "ids"));
        if (Boolean.TRUE.equals(req.on())) {
            tags.assign(bizKey, tagId, visible, guard.userId());
        } else {
            tags.unassign(bizKey, tagId, visible);
        }
        return visible.size();
    }

    /** Lookup code carried by a provider link, or {@code null} when the link has none we know. */
    static String codeFromLink(String url) {
        try {
            MultiValueMap<String, String> query = UriComponentsBuilder.fromUriString(url).build().getQueryParams();
            for (Map.Entry<String, List<String>> e : query.entrySet()) {
                if (CODE_KEYS.contains(e.getKey().toLowerCase(Locale.ROOT)) && !e.getValue().isEmpty()) {
                    String v = e.getValue().get(0);
                    if (v != null && !v.isBlank()) return v.trim();
                }
            }
            // tracuu.ehoadon.vn/<MTC> style: the code is the last path segment. A code always
            // carries a digit, which keeps page names such as /tra-cuu/ from passing for one.
            List<String> segments = UriComponentsBuilder.fromUriString(url).build().getPathSegments();
            if (!segments.isEmpty()) {
                String last = segments.get(segments.size() - 1);
                if (last.matches("[A-Za-z0-9_-]{6,64}") && last.matches(".*\\d.*")) return last;
            }
        } catch (IllegalArgumentException e) {
            throw new BusinessException("WBE3020");
        }
        return null;
    }
}
