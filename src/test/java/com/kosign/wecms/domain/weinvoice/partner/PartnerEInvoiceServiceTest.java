package com.kosign.wecms.domain.weinvoice.partner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.domain.einvoice.dto.ScrapedEInvoiceDetailRow;
import com.kosign.wecms.domain.einvoice.dto.ScrapedEInvoiceRow;
import com.kosign.wecms.domain.weinvoice.common.WeInvoiceJson;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceDb;
import com.kosign.wecms.domain.weinvoice.repository.InvoiceDetailRepository;
import com.kosign.wecms.domain.weinvoice.repository.InvoiceDetailRepository.ItemRow;
import com.kosign.wecms.domain.weinvoice.repository.PartnerInvoiceRepository;
import com.kosign.wecms.domain.weinvoice.repository.PartnerInvoiceRepository.PartnerRow;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Partner rows must be accepted by the WeCMS intake as-is: exact status / check-result phrases,
 * codes WeCMS normalises, and every value within the {@code @Size} of its DTO (WeCMS runs
 * {@code @Valid} on the batch).
 */
class PartnerEInvoiceServiceTest {

    private final PartnerEInvoiceService service = new PartnerEInvoiceService(mock(WeInvoiceDb.class),
            mock(PartnerInvoiceRepository.class), mock(InvoiceDetailRepository.class), new WeInvoiceJson(new ObjectMapper()));

    private static final String LIST_RAW_EXCEL = """
            {"_tthai_text": "Hóa đơn đã bị xóa bỏ/hủy bỏ", "ttxly": 6,
             "_ttxly_text": "Tổng cục Thuế đã nhận không mã", "dvtte": "VNĐ", "ttcktmai": 1500, "nbdchi": "HCM"}
            """;

    private static final String PARSED = """
            {"thong_tin_chung": {"loai_hd": "Hóa đơn giá trị gia tăng", "don_vi_tt": "USD", "ty_gia": 25300,
                                 "hinh_thuc_tt": "TM/CK",
                                 "hoa_don_lien_quan": {"ky_hieu_mau": "1", "ky_hieu": "C26TAA", "so_hd": "00000045"}},
             "nguoi_ban": {"ten": "CÔNG TY BÁN", "dia_chi": "%s", "dien_thoai": "028", "so_tk": "123", "ngan_hang": "VCB"},
             "nguoi_mua": {"ten": "WEBCASH", "mst": "1201496252", "dia_chi": "Hà Nội", "cccd": "001"},
             "thanh_toan": {"chiet_khau": 10, "bang_chu": "Mười đô",
                            "theo_thue_suat": [{"thue_suat": "8%%", "thanh_tien": 100, "tien_thue": 8}],
                            "phi": [{"ten": "Phí", "tien": 2}]},
             "ma_cqt": "00ABC", "qr": {"raw": "QR"},
             "ky_so": {"nguoi_ban": "2026-10-01T09:08:07", "nguoi_mua": "2026-10-02T10:11:12+07:00"}}
            """.formatted("A".repeat(2100));

    private static PartnerRow row(String symbol, String listRaw, String parsed) {
        return new PartnerRow(1L, 1, symbol, 123L, LocalDate.of(2026, 10, 1), new BigDecimal("1100.00"),
                new BigDecimal("1000.00"), new BigDecimal("100.00"), null, "mua-khma", "0101243150", "CÔNG TY BÁN",
                "1201496252", "WEBCASH", listRaw, parsed);
    }

    @Test
    void headerRowUsesThePhrasesWeCmsAccepts() {
        ScrapedEInvoiceRow r = service.toRow(row("K26TEV", LIST_RAW_EXCEL, null), false);
        assertThat(r.numericSymbol()).isEqualTo("1");
        assertThat(r.invoiceSymbol()).isEqualTo("K26TEV");
        assertThat(r.invoiceNo()).isEqualTo("123");
        assertThat(r.invoiceDate()).isEqualTo("20261001");
        assertThat(r.totalAmt()).isEqualTo("1100");
        assertThat(r.totalDiscount()).isEqualTo("1500");
        assertThat(r.invoiceStatus()).isEqualTo("Hóa đơn đã bị hủy");
        assertThat(r.checkResult()).isEqualTo("Cục thuế đã nhận không mã");
        assertThat(r.einvTestResults()).isEqualTo("1");
        assertThat(r.currencyCd()).isEqualTo("VND");
        assertThat(r.trcoCmnm()).isEqualTo("CÔNG TY BÁN");
        assertThat(r.invoiceInfo()).isEmpty();
    }

    @Test
    void salesRowNamesTheBuyerAsCounterparty() {
        assertThat(service.toRow(row("C26TAA", "{}", null), true).trcoCmnm()).isEqualTo("WEBCASH");
    }

    @Test
    void detailRowMapsXmlAndStaysWithinWeCmsSizes() {
        ItemRow line = new ItemRow(1, 1, "SP1", "Hàng A", "cái", new BigDecimal("2.000000"), new BigDecimal("50.000000"),
                null, null, new BigDecimal("100.000000"), "8%", new BigDecimal("8.000000"), new BigDecimal("108.000000"));
        ScrapedEInvoiceDetailRow d = service.toDetail(row("C26TAA", "{}", PARSED), false, List.of(line));

        assertThat(d.currencyCd()).isEqualTo("USD");
        assertThat(d.rate()).isEqualTo("25300");
        assertThat(d.invoiceTitle()).isEqualTo("Hóa đơn giá trị gia tăng");
        assertThat(d.originalInvoice()).isEqualTo("1C26TAA-45");
        assertThat(d.sellerSignDate()).isEqualTo("20261001");
        assertThat(d.sellerSignTime()).isEqualTo("090807");
        assertThat(d.buyerSignDate()).isEqualTo("20261002");
        assertThat(d.buyerSignTime()).isEqualTo("101112");
        assertThat(d.buyerCitizenId()).isEqualTo("001");
        assertThat(d.totalFee()).isEqualTo("2");
        assertThat(d.sellerAddr()).hasSize(2000);
        assertThat(d.products()).singleElement().satisfies(p -> {
            assertThat(p.productName()).isEqualTo("Hàng A");
            assertThat(p.product()).isEqualTo("Hàng A");
            assertThat(p.quantity()).isEqualTo("2");
            assertThat(p.supplyAmt()).isEqualTo("100");
            assertThat(p.taxRate()).isEqualTo("8%");
        });
        assertThat(d.taxes()).singleElement().satisfies(t -> assertThat(t.taxRate()).isEqualTo("8%"));
        assertThat(d.fees()).singleElement().satisfies(f -> assertThat(f.feeName()).isEqualTo("Phí"));

        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        assertThat(validator.validate(d)).isEmpty();
    }

    @Test
    void signingTimeEdgeCases() {
        assertThat(PartnerEInvoiceService.signDate(null)).isNull();
        assertThat(PartnerEInvoiceService.signDate("2026-10")).isNull();
        assertThat(PartnerEInvoiceService.signTime("2026-10-01")).isNull();
    }
}
