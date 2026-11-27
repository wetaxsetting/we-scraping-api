package com.kosign.wecms.domain.weinvoice.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.domain.weinvoice.dto.InvoiceRow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** UI Spec v1.2 list extras: accent-insensitive search, PO / due date / fee SQL, due-date parsing. */
class InvoiceExtraSqlTest {

    @Test
    void searchPatternMatchesAccentedTextWhateverTheKeywordAccentsAndCase() {
        Pattern p = Pattern.compile(InvoiceExtraSql.searchPattern("mi an lien"));
        assertThat(p.matcher("OPPA Mì ăn liền bò hầm 65g").find()).isTrue();
        assertThat(p.matcher("MÌ ĂN LIỀN").find()).isTrue();
        assertThat(Pattern.compile(InvoiceExtraSql.searchPattern("Cước")).matcher("CUOC DUONG BO").find()).isTrue();
        assertThat(Pattern.compile(InvoiceExtraSql.searchPattern("dau")).matcher("Dầu Điêzen").find()).isTrue();
        assertThat(Pattern.compile(InvoiceExtraSql.searchPattern("đ")).matcher("Đường").find()).isTrue();
        assertThat(p.matcher("Mì ăn ngon").find()).isFalse();
    }

    @Test
    void searchPatternEscapesRegexCharacters() {
        String re = InvoiceExtraSql.searchPattern("PO-1.2(3)");
        assertThat(Pattern.compile(re).matcher("so PO-1.2(3)").find()).isTrue();
        assertThat(Pattern.compile(re).matcher("PO-1x2(3)").find()).isFalse();
    }

    @Test
    void poAndDueReadTheKnownTtKhacNamesFirstNonBlankWins() {
        assertThat(InvoiceExtraSql.PO).startsWith("COALESCE(NULLIF(btrim(x.parsed->'tt_khac'->>'Số PO'), '')")
                .contains("'RPO'", "'DO'", "'PONumber'");
        assertThat(InvoiceExtraSql.DUE).contains("'Hạn thanh toán'", "'DueDate'", "'Thời hạn thanh toán'");
        assertThat(InvoiceExtraSql.FEE).contains("x.parsed->'thanh_toan'->'phi'", "i.list_raw->>'tgtphi'");
        assertThat(InvoiceExtraSql.TRADE_DISCOUNT).contains("'chiet_khau'", "'ttcktmai'");
    }

    @Test
    void relatedKeyDropsTheTemplateDigitOfASevenCharacterSymbol() {
        assertThat(InvoiceExtraSql.refSymbol("j")).contains("substr(j->>'ky_hieu', 2)");
        assertThat(InvoiceExtraSql.refNumber("j")).contains("ltrim(regexp_replace");
    }

    @Test
    void dueDateIsIsoOnlyWhenTheTextIsADate() {
        assertThat(InvoiceCodes.dueDate("2023-01-31")).isEqualTo("2023-01-31");
        assertThat(InvoiceCodes.dueDate("12/11/2026")).isEqualTo("2026-11-12");
        assertThat(InvoiceCodes.dueDate("Hạn: 5-11-2026")).isEqualTo("2026-11-05");
        assertThat(InvoiceCodes.dueDate("30DAYS")).isNull();
        assertThat(InvoiceCodes.dueDate("31/02/2026")).isNull();
        assertThat(InvoiceCodes.dueDate(" ")).isNull();
    }

    @Test
    void detailFeeAndValueStillInForce() throws Exception {
        var om = new ObjectMapper();
        var pay = om.readTree("{\"phi\":[{\"ten\":\"Phí BVMT\",\"tien\":104520},{\"ten\":\"x\",\"tien\":480}]}");
        assertThat(com.kosign.wecms.domain.weinvoice.service.InvoiceQueryServiceTestAccess.fee(pay, om.readTree("{}")))
                .isEqualByComparingTo("105000");
        assertThat(com.kosign.wecms.domain.weinvoice.service.InvoiceQueryServiceTestAccess.fee(om.readTree("{}"),
                om.readTree("{\"tgtphi\":51}"))).isEqualByComparingTo("51");
        List<InvoiceRow.Ref> by = List.of(
                new InvoiceRow.Ref("2", "adjusts", "1", "C26MDC", "3163", "2026-11-11", new BigDecimal("-7024500")),
                new InvoiceRow.Ref("3", "replaces", "1", "C26MDC", "3164", "2026-11-12", new BigDecimal("1")));
        assertThat(com.kosign.wecms.domain.weinvoice.service.InvoiceQueryServiceTestAccess
                .effectiveTotal(new BigDecimal("10000000"), by)).isEqualByComparingTo("2975500");
        assertThat(com.kosign.wecms.domain.weinvoice.service.InvoiceQueryServiceTestAccess
                .effectiveTotal(new BigDecimal("10000000"), List.of(by.get(1)))).isNull();
    }
}
