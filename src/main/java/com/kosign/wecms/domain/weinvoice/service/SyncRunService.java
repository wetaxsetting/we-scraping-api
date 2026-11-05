package com.kosign.wecms.domain.weinvoice.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.weinvoice.common.CompanyAccessGuard;
import com.kosign.wecms.domain.weinvoice.common.WeInvoiceIds;
import com.kosign.wecms.domain.weinvoice.common.WeInvoiceJson;
import com.kosign.wecms.domain.weinvoice.common.WeInvoicePaging;
import com.kosign.wecms.domain.weinvoice.common.WeInvoiceTime;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceDb;
import com.kosign.wecms.domain.weinvoice.dto.EngineRequestResponse;
import com.kosign.wecms.domain.weinvoice.dto.RequestAccepted;
import com.kosign.wecms.domain.weinvoice.dto.SyncRunCreateRequest;
import com.kosign.wecms.domain.weinvoice.dto.SyncRunItem;
import com.kosign.wecms.domain.weinvoice.dto.SyncRunProgress;
import com.kosign.wecms.domain.weinvoice.dto.WeInvoicePage;
import com.kosign.wecms.domain.weinvoice.repository.CompanyConnectionRepository;
import com.kosign.wecms.domain.weinvoice.repository.CompanyConnectionRepository.CompanyRow;
import com.kosign.wecms.domain.weinvoice.repository.EngineRequestRepository;
import com.kosign.wecms.domain.weinvoice.repository.EngineRequestRepository.RequestRow;
import com.kosign.wecms.domain.weinvoice.repository.SyncRunRepository;
import com.kosign.wecms.domain.weinvoice.repository.SyncRunRepository.HistoryRow;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Screen 10 — GDT sync runs. A run is a {@code SYNC} engine request per MST; the engine
 * reports step and progress on the request. History also lists the runs the engine schedules
 * itself (trigger {@code schedule}).
 */
@Service
public class SyncRunService {

    /** Longest period one request may cover. GDT itself caps each query at one calendar month. */
    static final int MAX_MONTHS = 36;

    private static final Map<String, List<String>> STATUS_FILTER = Map.of(
            "queued", List.of("QUEUED"),
            "running", List.of("RUNNING", "_running_"),
            "success", List.of("DONE", "ok"),
            "partial", List.of("PARTIAL", "partial"),
            "failed", List.of("FAILED", "error"),
            "cancelled", List.of("CANCELLED"));

    private final CompanyAccessGuard guard;
    private final CompanyConnectionRepository companies;
    private final EngineRequestRepository requestRows;
    private final EngineRequestService requests;
    private final SyncRunRepository history;
    private final WeInvoiceDb db;
    private final WeInvoiceJson json;

    public SyncRunService(CompanyAccessGuard guard, CompanyConnectionRepository companies,
                          EngineRequestRepository requestRows, EngineRequestService requests, SyncRunRepository history,
                          WeInvoiceDb db, WeInvoiceJson json) {
        this.guard = guard;
        this.companies = companies;
        this.requestRows = requestRows;
        this.requests = requests;
        this.history = history;
        this.db = db;
        this.json = json;
    }

    public List<RequestAccepted> create(SyncRunCreateRequest req) {
        String direction = req.direction() == null || req.direction().isBlank()
                ? "both" : req.direction().trim().toLowerCase(Locale.ROOT);
        if (!List.of("both", "purchase", "sales").contains(direction)) {
            throw new IllegalArgumentException("direction: must be both, purchase or sales");
        }
        YearMonth from = WeInvoiceTime.parseMonth(req.fromMonth(), "from_month");
        YearMonth to = WeInvoiceTime.parseMonth(req.toMonth(), "to_month");
        if (from.isAfter(to) || to.isAfter(YearMonth.now())) {
            throw new BusinessException("WBE3016");
        }
        if (ChronoUnit.MONTHS.between(from, to) + 1 > MAX_MONTHS) {
            throw new BusinessException("WBE3023", MAX_MONTHS);
        }
        String trigger = "bulk".equalsIgnoreCase(req.trigger()) ? "BULK" : "MANUAL";
        List<CompanyRow> targets = new ArrayList<>();
        for (String mst : new LinkedHashSet<>(req.companyMsts())) {
            CompanyRow c = guard.requireCompany(mst);
            if (!c.hasPassword()) {
                throw new BusinessException("WBE3024", c.mst());
            }
            targets.add(c);
        }
        Map<String, Object> params = Map.of("direction", direction, "from_month", from.toString(), "to_month", to.toString());
        return db.tx().execute(s -> {
            List<RequestAccepted> out = new ArrayList<>(targets.size());
            for (CompanyRow c : targets) {
                long id = requests.queue(c.mst(), EngineRequestService.SYNC, trigger, params);
                out.add(new RequestAccepted("R" + id, c.mst()));
            }
            return out;
        });
    }

    public SyncRunProgress progress(String runId) {
        RequestRow row = requestRows.find(guard.bizKey(), requestId(runId))
                .filter(r -> EngineRequestService.SYNC.equals(r.type()))
                .orElseThrow(() -> new BusinessException("WBE3011"));
        Map<String, Object> p = json.map(row.progressJson());
        Map<String, Object> pr = p == null ? Map.of() : p;
        return new SyncRunProgress("R" + row.id(), row.mst(), EngineRequestService.lower(row.status()), row.step(),
                str(pr.get("month")), num(pr.get("listed")), num(pr.get("xml_done")), num(pr.get("xml_total")),
                row.error(), toItem(new HistoryRow(row.id(), null, row.mst(), row.trigger(), row.paramsJson(), row.status(),
                        row.resultJson(), row.error(), row.regDtm(), row.startDtm(), row.endDtm(), null, null), names()));
    }

    public EngineRequestResponse cancel(String runId) {
        long id = requestId(runId);
        requestRows.find(guard.bizKey(), id)
                .filter(r -> EngineRequestService.SYNC.equals(r.type()))
                .orElseThrow(() -> new BusinessException("WBE3011"));
        return requests.cancel(id);
    }

    public WeInvoicePage<SyncRunItem> history(String companyMst, String status, int page, int size) {
        List<String> msts = companyMst == null || companyMst.isBlank()
                ? guard.tenantMsts() : List.of(guard.requireCompany(companyMst).mst());
        List<String> raw = List.of();
        if (status != null && !status.isBlank()) {
            raw = STATUS_FILTER.get(status.trim().toLowerCase(Locale.ROOT));
            if (raw == null) {
                throw new IllegalArgumentException("status: must be one of " + STATUS_FILTER.keySet());
            }
        }
        int p = WeInvoicePaging.page(page);
        int s = WeInvoicePaging.size(size);
        Map<String, String> names = names();
        List<SyncRunItem> rows = history.history(guard.bizKey(), msts, raw, p, s).stream()
                .map(h -> toItem(h, names)).toList();
        return new WeInvoicePage<>(rows, history.count(guard.bizKey(), msts, raw), p, s);
    }

    private SyncRunItem toItem(HistoryRow h, Map<String, String> names) {
        boolean isRequest = h.requestId() != null;
        Map<String, Object> params = isRequest ? json.map(h.paramsJson()) : null;
        Map<String, Object> result = json.map(h.resultJson());
        Map<String, Object> pm = params == null ? Map.of() : params;
        Map<String, Object> rs = result == null ? Map.of() : result;
        String startedAt;
        Long duration;
        if (isRequest) {
            startedAt = WeInvoiceTime.dtmToIso(h.startDtm() != null ? h.startDtm() : h.regDtm());
            duration = WeInvoiceTime.secondsBetween(h.startDtm(), h.endDtm());
        } else {
            startedAt = h.startedAt();
            duration = h.startedAt() == null || h.finishedAt() == null ? null
                    : ChronoUnit.SECONDS.between(OffsetDateTime.parse(h.startedAt()), OffsetDateTime.parse(h.finishedAt()));
        }
        return new SyncRunItem(
                isRequest ? "R" + h.requestId() : "J" + h.runId(),
                isRequest ? String.valueOf(h.requestId()) : null,
                h.mst(),
                names.get(h.mst()),
                isRequest ? str(pm.getOrDefault("direction", "both")) : "both",
                str(pm.get("from_month")),
                str(pm.get("to_month")),
                startedAt,
                duration,
                num(rs.containsKey("new") ? rs.get("new") : rs.get("new_invoices")),
                num(rs.get("updated")),
                num(rs.containsKey("xml") ? rs.get("xml") : rs.get("xml_ok")),
                status(h.rawStatus()),
                h.error(),
                h.trigger() == null ? null : h.trigger().toLowerCase(Locale.ROOT));
    }

    /** Stored status → UI status. */
    static String status(String raw) {
        if (raw == null) return null;
        return switch (raw) {
            case "QUEUED" -> "queued";
            case "RUNNING", "_running_" -> "running";
            case "DONE", "ok" -> "success";
            case "PARTIAL", "partial" -> "partial";
            case "FAILED", "error" -> "failed";
            case "CANCELLED" -> "cancelled";
            default -> raw.toLowerCase(Locale.ROOT);
        };
    }

    private Map<String, String> names() {
        Map<String, String> out = new HashMap<>();
        for (CompanyRow c : companies.listActive(guard.bizKey())) {
            out.put(c.mst(), c.companyName());
        }
        return out;
    }

    /** Accepts {@code R123} (history id) or {@code 123} (request id). */
    private static long requestId(String runId) {
        String s = runId == null ? "" : runId.trim();
        if (s.startsWith("R") || s.startsWith("r")) s = s.substring(1);
        return WeInvoiceIds.parse(s, "run_id");
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Integer num(Object o) {
        if (o instanceof Number n) return n.intValue();
        if (o instanceof String s && s.matches("-?\\d{1,9}")) return Integer.parseInt(s);
        return null;
    }
}
