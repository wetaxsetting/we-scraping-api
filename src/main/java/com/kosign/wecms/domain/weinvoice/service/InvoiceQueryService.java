package com.kosign.wecms.domain.weinvoice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.weinvoice.common.CompanyAccessGuard;
import com.kosign.wecms.domain.weinvoice.common.InvoiceCodes;
import com.kosign.wecms.domain.weinvoice.common.WeInvoiceIds;
import com.kosign.wecms.domain.weinvoice.common.WeInvoiceJson;
import com.kosign.wecms.domain.weinvoice.common.WeInvoicePaging;
import com.kosign.wecms.domain.weinvoice.common.WeInvoiceTime;
import com.kosign.wecms.domain.weinvoice.dto.InvoiceDetail;
import com.kosign.wecms.domain.weinvoice.dto.InvoiceFacets;
import com.kosign.wecms.domain.weinvoice.dto.InvoiceFilter;
import com.kosign.wecms.domain.weinvoice.dto.InvoiceKpis;
import com.kosign.wecms.domain.weinvoice.dto.InvoiceListResponse;
import com.kosign.wecms.domain.weinvoice.repository.CompanyConnectionRepository.CompanyRow;
import com.kosign.wecms.domain.weinvoice.repository.InvoiceCriteria;
import com.kosign.wecms.domain.weinvoice.repository.InvoiceDetailRepository;
import com.kosign.wecms.domain.weinvoice.repository.InvoiceDetailRepository.DetailRow;
import com.kosign.wecms.domain.weinvoice.repository.InvoiceDetailRepository.ItemRow;
import com.kosign.wecms.domain.weinvoice.repository.InvoiceDetailRepository.TaskRow;
import com.kosign.wecms.domain.weinvoice.repository.InvoiceQueryRepository;
import com.kosign.wecms.domain.weinvoice.repository.InvoiceSortField;
import com.kosign.wecms.domain.weinvoice.repository.TagRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Screens 04 and 05 — invoice list, filters, "select all", and the invoice detail. */
@Service
public class InvoiceQueryService {

    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");

    private final CompanyAccessGuard guard;
    private final InvoiceQueryRepository invoices;
    private final InvoiceDetailRepository details;
    private final TagRepository tags;
    private final InvoiceFileService files;
    private final WeInvoiceJson json;

    public InvoiceQueryService(CompanyAccessGuard guard, InvoiceQueryRepository invoices, InvoiceDetailRepository details,
                               TagRepository tags, InvoiceFileService files, WeInvoiceJson json) {
        this.guard = guard;
        this.invoices = invoices;
        this.details = details;
        this.tags = tags;
        this.files = files;
        this.json = json;
    }

    public InvoiceListResponse list(InvoiceFilter filter, int page, int size) {
        InvoiceCriteria c = criteria(filter);
        int p = WeInvoicePaging.page(page);
        int s = WeInvoicePaging.size(size);
        InvoiceKpis kpis = invoices.kpis(c);
        return new InvoiceListResponse(
                invoices.list(c, p, s),
                kpis.count(),
                p,
                s,
                kpis,
                invoices.incompletePeriods(c.mst(), c.engineDirection(), ym(c.from()), ym(c.to())));
    }

    public InvoiceFacets facets(InvoiceFilter filter) {
        InvoiceCriteria c = criteria(filter);
        // Facets describe the period, not the current selection: keep MST, direction and dates only.
        return invoices.facets(new InvoiceCriteria(c.ownerId(), c.mst(), c.engineDirection(), c.from(), c.to(), null, null,
                null, List.of(), null, List.of(), null, List.of(), List.of(), null, false));
    }

    /** Every id matching the filter ("select all"); {@code WBE3017} above {@link InvoiceQueryRepository#MAX_IDS}. */
    public List<String> ids(InvoiceFilter filter) {
        InvoiceCriteria c = criteria(filter);
        List<Long> ids = invoices.ids(c, InvoiceQueryRepository.MAX_IDS + 1);
        if (ids.size() > InvoiceQueryRepository.MAX_IDS) {
            throw new BusinessException("WBE3017", ids.size(), InvoiceQueryRepository.MAX_IDS);
        }
        return ids.stream().map(String::valueOf).toList();
    }

    /** Validates the filter against the user and turns it into typed criteria. */
    public InvoiceCriteria criteria(InvoiceFilter f) {
        CompanyRow company = guard.requireCompany(f.companyMst());
        String dir = f.direction() == null || f.direction().isBlank() || "both".equalsIgnoreCase(f.direction())
                ? null : InvoiceCodes.engineDirection(f.direction());
        LocalDate from = blank(f.from()) ? null : WeInvoiceTime.parseDate(f.from(), "from");
        LocalDate to = blank(f.to()) ? null : WeInvoiceTime.parseDate(f.to(), "to");
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException("WBE3016");
        }
        String xml = fileFilter(f.xml(), "xml");
        String pdf = fileFilter(f.pdf(), "pdf");
        List<Integer> tthais = f.statuses() == null ? List.of()
                : f.statuses().stream().filter(s -> !blank(s)).map(InvoiceCodes::tthai).distinct().toList();
        String kind = blank(f.kind()) || "all".equalsIgnoreCase(f.kind()) ? null : f.kind().trim().toLowerCase(Locale.ROOT);
        if (kind != null && !List.of("normal", "cash_register", "no_code").contains(kind)) {
            throw new IllegalArgumentException("kind: must be normal, cash_register or no_code");
        }
        List<Long> tagIds = f.tagIds() == null ? List.of() : WeInvoiceIds.parseAll(f.tagIds(), "tag_ids");
        String provider = blank(f.provider()) ? null : f.provider().trim();
        if (provider != null && !provider.equals("no_code") && !provider.equals("unknown") && !WeInvoiceIds.isTaxId(provider)) {
            throw new IllegalArgumentException("provider: must be a provider MST, no_code or unknown");
        }
        List<String> exclude = f.excludeTaxIds() == null ? List.of()
                : f.excludeTaxIds().stream().filter(s -> !blank(s)).map(String::trim).toList();
        List<Long> ids = f.ids() == null ? List.of() : WeInvoiceIds.parseAll(f.ids(), "ids");
        InvoiceSortField sort = InvoiceSortField.parse(f.sortField());
        boolean desc = !"asc".equalsIgnoreCase(f.sortDirection());
        return new InvoiceCriteria(guard.ownerId(), company.mst(), dir, from, to, blank(f.keyword()) ? null : f.keyword(),
                xml, pdf, tthais, kind, tagIds, provider, exclude, ids, sort, desc);
    }

    // ------------------------------------------------------------------ detail

    public InvoiceDetail detail(String invoiceId) {
        long id = WeInvoiceIds.parse(invoiceId, "id");
        guard.requireInvoice(id);
        String ownerId = guard.ownerId();
        DetailRow d = details.find(ownerId, id).orElseThrow(() -> new BusinessException("WBE3005"));
        JsonNode parsed = json.tree(d.parsedJson());
        JsonNode raw = json.tree(d.listRawJson());
        JsonNode tc = parsed.path("thong_tin_chung");
        JsonNode sellerNode = parsed.path("nguoi_ban");
        JsonNode buyerNode = parsed.path("nguoi_mua");
        JsonNode pay = parsed.path("thanh_toan");

        String currency = InvoiceCodes.currency(WeInvoiceJson.firstNonBlank(
                WeInvoiceJson.text(tc, "don_vi_tt"), WeInvoiceJson.text(raw, "dvtte")));
        BigDecimal rate = Optional.ofNullable(WeInvoiceJson.decimal(tc, "ty_gia")).orElse(WeInvoiceJson.decimal(raw, "tgia"));
        BigDecimal vndRate = "VND".equals(currency) || rate == null || rate.signum() <= 0 ? BigDecimal.ONE : rate;
        int tthai = InvoiceCodes.effectiveTthai(d.tthai(), WeInvoiceJson.text(raw, "_tthai_text"));

        InvoiceDetail.Party seller = new InvoiceDetail.Party(
                d.sellerTaxId(),
                WeInvoiceJson.firstNonBlank(WeInvoiceJson.text(sellerNode, "ten"), d.sellerName()),
                WeInvoiceJson.firstNonBlank(WeInvoiceJson.text(sellerNode, "dia_chi"), WeInvoiceJson.text(raw, "nbdchi")),
                WeInvoiceJson.text(sellerNode, "dien_thoai"),
                WeInvoiceJson.text(sellerNode, "email"),
                WeInvoiceJson.text(sellerNode, "so_tk"),
                WeInvoiceJson.text(sellerNode, "ngan_hang"),
                null);
        InvoiceDetail.Party buyer = new InvoiceDetail.Party(
                WeInvoiceJson.firstNonBlank(WeInvoiceJson.text(buyerNode, "mst"), d.buyerTaxId()),
                WeInvoiceJson.firstNonBlank(WeInvoiceJson.text(buyerNode, "ten"), d.buyerName()),
                WeInvoiceJson.firstNonBlank(WeInvoiceJson.text(buyerNode, "dia_chi"), WeInvoiceJson.text(raw, "nmdchi")),
                WeInvoiceJson.text(buyerNode, "dien_thoai"),
                WeInvoiceJson.text(buyerNode, "email"),
                WeInvoiceJson.text(buyerNode, "so_tk"),
                WeInvoiceJson.text(buyerNode, "ngan_hang"),
                WeInvoiceJson.firstNonBlank(WeInvoiceJson.text(buyerNode, "cccd"), WeInvoiceJson.text(raw, "nmcccd")));

        InvoiceDetail.Related related = related(d, tc, raw, tthai);
        String providerMst = WeInvoiceJson.firstNonBlank(d.xmlProviderMst(), WeInvoiceJson.text(raw, "msttcgp"));
        boolean known = d.catalogueMst() != null;

        List<TaskRow> taskRows = details.tasks(id);
        return new InvoiceDetail(
                String.valueOf(d.id()),
                d.companyMst(),
                InvoiceCodes.direction(d.direction()),
                d.issueDate(),
                String.valueOf(d.templateNo()),
                d.symbol(),
                String.valueOf(d.number()),
                WeInvoiceJson.text(tc, "loai_hd"),
                seller,
                buyer,
                currency,
                rate,
                d.preTax(),
                d.tax(),
                Optional.ofNullable(WeInvoiceJson.decimal(pay, "chiet_khau")).orElse(WeInvoiceJson.decimal(raw, "ttcktmai")),
                d.total(),
                d.total() == null ? null : d.total().multiply(vndRate),
                WeInvoiceJson.text(pay, "bang_chu"),
                WeInvoiceJson.firstNonBlank(WeInvoiceJson.text(tc, "hinh_thuc_tt"), WeInvoiceJson.text(raw, "thtttoan")),
                InvoiceCodes.status(tthai),
                related == null ? null : related.id(),
                related,
                InvoiceCodes.kind(d.symbol()),
                WeInvoiceJson.firstNonBlank(WeInvoiceJson.text(parsed, "ma_cqt"), WeInvoiceJson.text(raw, "mhdon")),
                d.providerName(),
                providerMst,
                known,
                lookup(d, providerMst, known),
                items(id, parsed),
                taxes(pay),
                fees(pay),
                new InvoiceDetail.Signatures(
                        WeInvoiceJson.text(parsed.path("ky_so"), "nguoi_ban"),
                        WeInvoiceJson.text(parsed.path("ky_so"), "nguoi_mua"),
                        WeInvoiceJson.text(parsed.path("ky_so"), "cqt")),
                files.list(d),
                tags.tagIdsOf(ownerId, id),
                task(taskRows, "xml"),
                task(taskRows, "pdf"),
                warnings(parsed),
                InvoiceCodes.recordOrigin(WeInvoiceJson.text(raw, "nguon")),
                d.firstSeenAt());
    }

    /**
     * Related invoice. This invoice names its original in {@code TTHDLQuan} (XML) or
     * {@code khhdgoc / shdgoc} (GDT list); a replaced / adjusted invoice is found through the
     * invoice that names it.
     */
    private InvoiceDetail.Related related(DetailRow d, JsonNode tc, JsonNode raw, int tthai) {
        JsonNode lq = tc.path("hoa_don_lien_quan");
        String symbol = null;
        String number = null;
        String template = null;
        String date = null;
        String reason = null;
        Integer nature = null;
        if (lq.isObject()) {
            template = WeInvoiceJson.text(lq, "ky_hieu_mau");
            symbol = WeInvoiceJson.text(lq, "ky_hieu");
            number = WeInvoiceJson.text(lq, "so_hd");
            date = WeInvoiceJson.text(lq, "ngay_lap");
            reason = WeInvoiceJson.firstNonBlank(WeInvoiceJson.text(lq, "ly_do_ten"), WeInvoiceJson.text(lq, "ghi_chu"));
            nature = WeInvoiceJson.integer(lq, "tinh_chat");
        } else if (WeInvoiceJson.text(raw, "khhdgoc") != null) {
            template = WeInvoiceJson.text(raw, "khmshdgoc");
            symbol = WeInvoiceJson.text(raw, "khhdgoc");
            number = WeInvoiceJson.text(raw, "shdgoc");
        }
        if (symbol != null && number != null) {
            // TCHDon: 1 = replacement, 2 = adjustment; without it fall back on this invoice's own status.
            String relation = (nature != null && nature == 2) || (nature == null && tthai == 3) ? "adjusts" : "replaces";
            if (symbol.length() == 7 && Character.isDigit(symbol.charAt(0))) {
                template = template == null ? symbol.substring(0, 1) : template;
                symbol = symbol.substring(1);
            }
            Long relatedId = null;
            String digits = number.replaceAll("\\D", "");
            if (!digits.isEmpty() && template != null && template.matches("\\d{1,2}")) {
                relatedId = details.findByKey(d.companyMst(), d.sellerTaxId(), Integer.parseInt(template), symbol,
                        Long.parseLong(digits)).orElse(null);
            }
            return new InvoiceDetail.Related(relation, relatedId == null ? null : String.valueOf(relatedId),
                    template, symbol, number, date, reason);
        }
        if (tthai == 4 || tthai == 5) {
            return details.findReferencing(d.id(), d.companyMst(), d.sellerTaxId(), d.templateNo(), d.symbol(), d.number())
                    .map(rid -> new InvoiceDetail.Related(tthai == 4 ? "replaced_by" : "adjusted_by", String.valueOf(rid),
                            null, null, null, null, null))
                    .orElse(null);
        }
        return null;
    }

    private static InvoiceDetail.Lookup lookup(DetailRow d, String providerMst, boolean known) {
        boolean manual = d.manualCode() != null || d.manualUrl() != null;
        String code = manual ? d.manualCode() : d.xmlLookupCode();
        String ownUrl = manual ? d.manualUrl() : d.xmlLookupUrl();
        String url = ownUrl != null ? ownUrl : d.providerLookupUrl();
        boolean needsCode = providerMst != null && known && code == null && ownUrl == null;
        return new InvoiceDetail.Lookup(code, manual ? d.manualCompanyCode() : null, url, ownUrl != null, manual, needsCode);
    }

    private List<InvoiceDetail.Item> items(long id, JsonNode parsed) {
        List<ItemRow> rows = details.items(id);
        List<InvoiceDetail.Item> out = new ArrayList<>(rows.size());
        for (ItemRow r : rows) {
            out.add(new InvoiceDetail.Item(r.lineNo(), r.lineKind(), r.code(), r.name(), r.unit(), r.qty(), r.price(),
                    r.discount(), r.amount(), InvoiceCodes.vatRate(r.vatRate()), r.vatRate(), r.taxAmount()));
        }
        if (!out.isEmpty()) return out;
        // Engine stores lines for every XML it parses; read the JSON only if the table is empty.
        int n = 0;
        for (JsonNode h : parsed.path("hang_hoa")) {
            String rate = WeInvoiceJson.text(h, "thue_suat");
            out.add(new InvoiceDetail.Item(++n, WeInvoiceJson.integer(h, "tinh_chat"), WeInvoiceJson.text(h, "ma_hhdv"),
                    WeInvoiceJson.text(h, "ten_hhdv"), WeInvoiceJson.text(h, "don_vi_tinh"),
                    WeInvoiceJson.decimal(h, "so_luong"), WeInvoiceJson.decimal(h, "don_gia"),
                    WeInvoiceJson.decimal(h, "st_ck"), WeInvoiceJson.decimal(h, "thanh_tien"),
                    InvoiceCodes.vatRate(rate), rate, WeInvoiceJson.decimal(h, "tien_thue")));
        }
        return out;
    }

    private static List<InvoiceDetail.TaxLine> taxes(JsonNode pay) {
        List<InvoiceDetail.TaxLine> out = new ArrayList<>();
        for (JsonNode t : pay.path("theo_thue_suat")) {
            String rate = WeInvoiceJson.text(t, "thue_suat");
            out.add(new InvoiceDetail.TaxLine(rate, InvoiceCodes.vatRate(rate),
                    WeInvoiceJson.decimal(t, "thanh_tien"), WeInvoiceJson.decimal(t, "tien_thue")));
        }
        return out;
    }

    private static List<InvoiceDetail.Fee> fees(JsonNode pay) {
        List<InvoiceDetail.Fee> out = new ArrayList<>();
        for (JsonNode f : pay.path("phi")) {
            out.add(new InvoiceDetail.Fee(WeInvoiceJson.text(f, "ten"), WeInvoiceJson.decimal(f, "tien")));
        }
        return out;
    }

    private static List<String> warnings(JsonNode parsed) {
        List<String> out = new ArrayList<>();
        for (JsonNode w : parsed.path("canh_bao")) {
            out.add(w.isTextual() ? w.asText() : w.toString());
        }
        return out;
    }

    private static InvoiceDetail.TaskState task(List<TaskRow> rows, String type) {
        return rows.stream().filter(r -> type.equals(r.type())).findFirst()
                .map(r -> new InvoiceDetail.TaskState(r.status(), r.attempts(), r.errorKind(), r.lastError(),
                        r.nextRunAt(), r.updatedAt()))
                .orElse(null);
    }

    private static String fileFilter(String v, String field) {
        if (blank(v) || "all".equalsIgnoreCase(v)) return null;
        String s = v.trim().toLowerCase(Locale.ROOT);
        if (!s.equals("has") && !s.equals("missing")) {
            throw new IllegalArgumentException(field + ": must be all, has or missing");
        }
        return s;
    }

    private static String ym(LocalDate d) {
        return d == null ? null : d.format(YM);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
