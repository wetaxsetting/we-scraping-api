package com.kosign.wecms.domain.weinvoice.partner;

import com.fasterxml.jackson.databind.JsonNode;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.einvoice.dto.ScrapedEInvoiceDetailRow;
import com.kosign.wecms.domain.einvoice.dto.ScrapedEInvoiceRow;
import com.kosign.wecms.domain.weinvoice.common.InvoiceCodes;
import com.kosign.wecms.domain.weinvoice.common.WeInvoiceIds;
import com.kosign.wecms.domain.weinvoice.common.WeInvoiceJson;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceDb;
import com.kosign.wecms.domain.weinvoice.dto.WeInvoicePage;
import com.kosign.wecms.domain.weinvoice.repository.InvoiceDetailRepository;
import com.kosign.wecms.domain.weinvoice.repository.InvoiceDetailRepository.ItemRow;
import com.kosign.wecms.domain.weinvoice.repository.PartnerInvoiceRepository;
import com.kosign.wecms.domain.weinvoice.repository.PartnerInvoiceRepository.PartnerRow;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Partner API for WeCMS — the "Update" button reads WeInvoice instead of running the Coocon
 * engine in the browser (UI Spec 02 §1).
 *
 * <p>Rows are the {@link ScrapedEInvoiceRow} / {@link ScrapedEInvoiceDetailRow} records WeCMS
 * already ingests, so its {@code EInvoiceScrapeService} / {@code EInvoiceDetailScrapeService}
 * stay unchanged. Every value is clipped to the {@code @Size} of those records: WeCMS validates
 * them with {@code @Valid}, and one over-long address would reject the whole batch. Fields GDT
 * data does not carry (Coocon's {@code invoiceInfo}, signer names, registration enrichment)
 * are sent empty — WeCMS tolerates that (feature record, "Partner API").
 */
@Service
public class PartnerEInvoiceService {

    private static final DateTimeFormatter DATE_8 = DateTimeFormatter.BASIC_ISO_DATE;
    static final int DEFAULT_SIZE = 100;
    static final int MAX_SIZE = 500;
    static final int MAX_DAYS = 366;

    private final WeInvoiceDb db;
    private final PartnerInvoiceRepository invoices;
    private final InvoiceDetailRepository details;
    private final WeInvoiceJson json;

    public PartnerEInvoiceService(WeInvoiceDb db, PartnerInvoiceRepository invoices, InvoiceDetailRepository details,
                                  WeInvoiceJson json) {
        this.db = db;
        this.invoices = invoices;
        this.details = details;
        this.json = json;
    }

    public WeInvoicePage<ScrapedEInvoiceRow> list(String taxId, String type, String from, String to, int page, int size) {
        db.requireEnabled();
        String mst = requireCompany(taxId);
        String dir = engineDirection(type);
        LocalDate f = date(from, "from");
        LocalDate t = date(to, "to");
        if (f.isAfter(t) || ChronoUnit.DAYS.between(f, t) > MAX_DAYS) {
            throw new BusinessException("WBE3016");
        }
        int p = Math.max(0, page);
        int s = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        boolean sales = "ban".equals(dir);
        List<ScrapedEInvoiceRow> rows = invoices.page(mst, dir, f, t, p, s).stream().map(r -> toRow(r, sales)).toList();
        return new WeInvoicePage<>(rows, invoices.count(mst, dir, f, t), p, s);
    }

    public PartnerDetailResponse details(PartnerDetailRequest req) {
        db.requireEnabled();
        String mst = requireCompany(req.taxId());
        String dir = engineDirection(req.type());
        List<PartnerInvoiceRepository.Key> keys = new ArrayList<>();
        List<PartnerDetailRequest.Key> notFound = new ArrayList<>();
        Map<String, PartnerDetailRequest.Key> byKey = new HashMap<>();
        for (PartnerDetailRequest.Key k : req.keys()) {
            Integer tpl = digits(k.numericSymbol());
            Long no = digitsLong(k.invoiceNo());
            if (tpl == null || no == null) {
                notFound.add(k);
                continue;
            }
            PartnerInvoiceRepository.Key key = new PartnerInvoiceRepository.Key(tpl, k.invoiceSymbol().trim(), no,
                    k.sellerTaxCode().trim());
            keys.add(key);
            byKey.put(keyString(key.templateNo(), key.symbol(), key.number(), key.sellerTaxId()), k);
        }
        List<PartnerRow> found = invoices.byKeys(mst, dir, keys);
        Map<Long, List<ItemRow>> items = details.itemsOf(found.stream().map(PartnerRow::id).toList());
        boolean sales = "ban".equals(dir);
        List<ScrapedEInvoiceDetailRow> rows = new ArrayList<>(found.size());
        for (PartnerRow r : found) {
            byKey.remove(keyString(r.templateNo(), r.symbol(), r.number(), r.sellerTaxId()));
            rows.add(toDetail(r, sales, items.getOrDefault(r.id(), List.of())));
        }
        notFound.addAll(byKey.values());
        return new PartnerDetailResponse(rows, notFound);
    }

    // ------------------------------------------------------------------ mapping

    ScrapedEInvoiceRow toRow(PartnerRow r, boolean sales) {
        JsonNode raw = json.tree(r.listRawJson());
        JsonNode parsed = json.tree(r.parsedJson());
        JsonNode tc = parsed.path("thong_tin_chung");
        JsonNode pay = parsed.path("thanh_toan");
        int tthai = InvoiceCodes.effectiveTthai(r.tthai(), WeInvoiceJson.text(raw, "_tthai_text"));
        return new ScrapedEInvoiceRow(
                clip(String.valueOf(r.templateNo()), 5),
                clip(r.symbol(), 10),
                clip(String.valueOf(r.number()), 50),
                r.issueDate() == null ? null : r.issueDate().format(DATE_8),
                amount(r.total()),
                amount(r.preTax()),
                amount(r.tax()),
                amount(firstNonNull(WeInvoiceJson.decimal(pay, "chiet_khau"), WeInvoiceJson.decimal(raw, "ttcktmai"))),
                clip(InvoiceCodes.invoiceStatusText(tthai), 100),
                clip(InvoiceCodes.checkResultText(WeInvoiceJson.integer(raw, "ttxly"),
                        WeInvoiceJson.text(raw, "_ttxly_text"), r.subType()), 100),
                "",
                clip(r.sellerTaxId(), 40),
                InvoiceCodes.testResult(r.symbol()),
                clip(currency(tc, raw), 3),
                clip(sales ? r.buyerName() : r.sellerName(), 200));
    }

    ScrapedEInvoiceDetailRow toDetail(PartnerRow r, boolean sales, List<ItemRow> lines) {
        JsonNode raw = json.tree(r.listRawJson());
        JsonNode parsed = json.tree(r.parsedJson());
        JsonNode tc = parsed.path("thong_tin_chung");
        JsonNode pay = parsed.path("thanh_toan");
        JsonNode seller = parsed.path("nguoi_ban");
        JsonNode buyer = parsed.path("nguoi_mua");
        JsonNode sign = parsed.path("ky_so");

        List<ScrapedEInvoiceDetailRow.Product> products = new ArrayList<>(lines.size());
        for (ItemRow l : lines) {
            products.add(new ScrapedEInvoiceDetailRow.Product(l.lineNo(), clip(l.name(), 500), clip(l.name(), 500),
                    clip(l.unit(), 90), amount(l.price()), amount(l.qty()), amount(l.amount()), clip(l.vatRate(), 10),
                    amount(l.discount())));
        }
        List<ScrapedEInvoiceDetailRow.Fee> fees = new ArrayList<>();
        BigDecimal feeTotal = null;
        int n = 0;
        for (JsonNode fee : pay.path("phi")) {
            BigDecimal v = WeInvoiceJson.decimal(fee, "tien");
            fees.add(new ScrapedEInvoiceDetailRow.Fee(++n, clip(WeInvoiceJson.text(fee, "ten"), 100), amount(v)));
            if (v != null) feeTotal = feeTotal == null ? v : feeTotal.add(v);
        }
        List<ScrapedEInvoiceDetailRow.Tax> taxes = new ArrayList<>();
        n = 0;
        for (JsonNode t : pay.path("theo_thue_suat")) {
            taxes.add(new ScrapedEInvoiceDetailRow.Tax(++n, clip(WeInvoiceJson.text(t, "thue_suat"), 50),
                    amount(WeInvoiceJson.decimal(t, "thanh_tien")), amount(WeInvoiceJson.decimal(t, "tien_thue"))));
        }
        String sellerSigned = WeInvoiceJson.text(sign, "nguoi_ban");
        String buyerSigned = WeInvoiceJson.text(sign, "nguoi_mua");
        return new ScrapedEInvoiceDetailRow(
                clip(String.valueOf(r.templateNo()), 5),
                clip(r.symbol(), 10),
                clip(String.valueOf(r.number()), 50),
                clip(r.sellerTaxId(), 40),
                InvoiceCodes.testResult(r.symbol()),
                clip(currency(tc, raw), 3),
                clip(WeInvoiceJson.text(tc, "loai_hd"), 500),
                clip(WeInvoiceJson.firstNonBlank(WeInvoiceJson.text(seller, "ten"), r.sellerName()), 500),
                amount(r.total()),
                amount(r.preTax()),
                amount(r.tax()),
                amount(firstNonNull(WeInvoiceJson.decimal(pay, "chiet_khau"), WeInvoiceJson.decimal(raw, "ttcktmai"))),
                clip(WeInvoiceJson.firstNonBlank(WeInvoiceJson.text(buyer, "ten"), r.buyerName()), 500),
                clip(WeInvoiceJson.firstNonBlank(WeInvoiceJson.text(buyer, "mst"), r.buyerTaxId()), 40),
                clip(WeInvoiceJson.firstNonBlank(WeInvoiceJson.text(buyer, "dia_chi"), WeInvoiceJson.text(raw, "nmdchi")), 2000),
                clip(WeInvoiceJson.text(buyer, "so_tk"), 200),
                clip(WeInvoiceJson.text(buyer, "ngan_hang"), 200),
                clip(WeInvoiceJson.firstNonBlank(WeInvoiceJson.text(buyer, "cccd"), WeInvoiceJson.text(raw, "nmcccd")), 20),
                clip(WeInvoiceJson.firstNonBlank(WeInvoiceJson.text(seller, "dia_chi"), WeInvoiceJson.text(raw, "nbdchi")), 2000),
                clip(WeInvoiceJson.text(seller, "so_tk"), 200),
                clip(WeInvoiceJson.text(seller, "ngan_hang"), 200),
                clip(WeInvoiceJson.text(seller, "dien_thoai"), 40),
                clip(WeInvoiceJson.firstNonBlank(WeInvoiceJson.text(parsed, "ma_cqt"), WeInvoiceJson.text(raw, "mhdon")), 50),
                clip(originalInvoice(tc, raw), 200),
                clip(WeInvoiceJson.firstNonBlank(WeInvoiceJson.text(tc, "hinh_thuc_tt"), WeInvoiceJson.text(raw, "thtttoan")), 50),
                clip(WeInvoiceJson.text(tc, "so_bang_ke"), 50),
                clip(WeInvoiceJson.text(tc, "ngay_bang_ke"), 50),
                amount(feeTotal != null ? feeTotal : WeInvoiceJson.decimal(raw, "tgtphi")),
                clip(WeInvoiceJson.text(pay, "bang_chu"), 200),
                null,
                signDate(buyerSigned),
                signTime(buyerSigned),
                null,
                signDate(sellerSigned),
                signTime(sellerSigned),
                clip(WeInvoiceJson.text(parsed.path("qr"), "raw"), 2000),
                amount(firstNonNull(WeInvoiceJson.decimal(tc, "ty_gia"), WeInvoiceJson.decimal(raw, "tgia"))),
                null,
                null,
                clip(sales ? WeInvoiceJson.firstNonBlank(WeInvoiceJson.text(buyer, "ten"), r.buyerName())
                        : WeInvoiceJson.firstNonBlank(WeInvoiceJson.text(seller, "ten"), r.sellerName()), 500),
                products,
                fees,
                taxes);
    }

    /** Related invoice as {@code <template><symbol>-<number>} — the same shape WeCMS builds its DOC_ID from. */
    static String originalInvoice(JsonNode tc, JsonNode raw) {
        JsonNode lq = tc.path("hoa_don_lien_quan");
        String tpl = WeInvoiceJson.text(lq, "ky_hieu_mau");
        String sym = WeInvoiceJson.text(lq, "ky_hieu");
        String no = WeInvoiceJson.text(lq, "so_hd");
        if (sym == null) {
            tpl = WeInvoiceJson.text(raw, "khmshdgoc");
            sym = WeInvoiceJson.text(raw, "khhdgoc");
            no = WeInvoiceJson.text(raw, "shdgoc");
        }
        if (sym == null || no == null) return null;
        String prefix = tpl == null || sym.matches("\\d.*") ? "" : tpl;
        return prefix + sym + "-" + no.replaceFirst("^0+(?=\\d)", "");
    }

    /** {@code SigningTime} → {@code yyyyMMdd} (WeCMS keeps the first 8 digits). */
    static String signDate(String signingTime) {
        if (signingTime == null || signingTime.length() < 10) return null;
        String d = signingTime.substring(0, 10).replaceAll("\\D", "");
        return d.length() == 8 ? d : null;
    }

    /** {@code SigningTime} → {@code HHmmss} (WeCMS keeps the first 6 digits). */
    static String signTime(String signingTime) {
        if (signingTime == null || signingTime.length() < 19) return null;
        String t = signingTime.substring(11, 19).replaceAll("\\D", "");
        return t.length() == 6 ? t : null;
    }

    private String requireCompany(String taxId) {
        String mst = WeInvoiceIds.requireTaxId(taxId);
        if (!invoices.companyExists(mst)) {
            throw new BusinessException("WBE3002", mst);
        }
        return mst;
    }

    private static String engineDirection(String type) {
        return switch (type == null ? "" : type.trim().toUpperCase(Locale.ROOT)) {
            case "SALES" -> "ban";
            case "EXPENSE", "BILL" -> "mua";
            default -> throw new IllegalArgumentException("type: must be SALES or EXPENSE");
        };
    }

    private static LocalDate date(String v, String field) {
        try {
            return LocalDate.parse(v, DATE_8);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new IllegalArgumentException(field + ": must be yyyyMMdd");
        }
    }

    private static String currency(JsonNode tc, JsonNode raw) {
        return InvoiceCodes.currency(WeInvoiceJson.firstNonBlank(WeInvoiceJson.text(tc, "don_vi_tt"),
                WeInvoiceJson.text(raw, "dvtte")));
    }

    private static String amount(BigDecimal v) {
        return v == null ? null : v.stripTrailingZeros().toPlainString();
    }

    private static BigDecimal firstNonNull(BigDecimal a, BigDecimal b) {
        return a != null ? a : b;
    }

    static String clip(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    private static Integer digits(String s) {
        String d = s == null ? "" : s.trim();
        return d.matches("\\d{1,2}") ? Integer.valueOf(d) : null;
    }

    private static Long digitsLong(String s) {
        String d = s == null ? "" : s.trim();
        return d.matches("\\d{1,18}") ? Long.valueOf(d) : null;
    }

    private static String keyString(int tpl, String symbol, long number, String seller) {
        return tpl + "|" + symbol.toUpperCase(Locale.ROOT) + "|" + number + "|" + seller;
    }
}
