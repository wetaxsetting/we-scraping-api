package com.kosign.wecms.domain.einvoice.service;

import com.kosign.wecms.domain.einvoice.entity.BusaDocMstr;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Counterparty ({@code TRCO_*}) address stamp on the invoice master
 * ({@link EInvoiceDetailScrapeService#applyCounterpartyAddress}). Regression
 * cover for the scrape gap where the supplier address was parsed into the
 * {@code TRCO_INFM} contact but never written onto {@code BUSA_DOC_MSTR.TRCO_BSUN_*}
 * — so the "View e-Invoice Details" popup / PDF showed an empty seller
 * {@code Địa chỉ:} on Bills. Legacy parity: {@code nxisas_einvoice_c002:625-630}.
 */
class EInvoiceCounterpartyAddressTest {

    /** Recompose the address exactly as {@code EInvoiceDetailService.getDetail}
     *  does — {@code joinAddr(post, dtl, city, st)}, blanks dropped, ", " glue. */
    private static String joinAsPopup(BusaDocMstr m) {
        return Stream.of(m.getTrcoBsunPostAdrs(), m.getTrcoBsunDtlAdrs(),
                        m.getTrcoBsunCity(), m.getTrcoBsunSt())
                .filter(s -> s != null && !s.isBlank())
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    @Test
    void stampsSupplierAddress_andReconstructsInPopupJoin() {
        BusaDocMstr m = new BusaDocMstr();
        String addr = "Tầng 01, Khu AB, Tòa nhà 27B, Số 27B Nguyễn Đình Chiểu, "
                + "Phường Sài Gòn, Thành phố Hồ Chí Minh, Việt Nam";

        EInvoiceDetailScrapeService.applyCounterpartyAddress(m, addr);

        // Columns are populated (previously all NULL — the bug).
        assertThat(m.getTrcoBsunPostAdrs()).isNotBlank();
        assertThat(m.getTrcoBsunCity()).isEqualTo("Phường Sài Gòn, Thành phố Hồ Chí Minh");
        assertThat(m.getTrcoBsunSt()).isEqualTo("Việt Nam");
        // The View-Details join recomposes the original scraped address.
        assertThat(joinAsPopup(m)).isEqualTo(addr);
    }

    @Test
    void shortAddress_wholeStringToPostAdrs() {
        BusaDocMstr m = new BusaDocMstr();
        String addr = "123 Lê Lợi, Quận 1";

        EInvoiceDetailScrapeService.applyCounterpartyAddress(m, addr);

        assertThat(m.getTrcoBsunPostAdrs()).isEqualTo("123 Lê Lợi, Quận 1");
        assertThat(m.getTrcoBsunSt()).isEqualTo("Việt Nam");
        assertThat(m.getTrcoBsunCity()).isNull();
        assertThat(joinAsPopup(m)).isEqualTo("123 Lê Lợi, Quận 1, Việt Nam");
    }

    @Test
    void blankAddress_leavesColumnsUntouched() {
        BusaDocMstr m = new BusaDocMstr();
        m.setTrcoBsunPostAdrs("PRE-EXISTING");

        EInvoiceDetailScrapeService.applyCounterpartyAddress(m, "   ");

        // A blank scraped address must never wipe a good phase-1 value.
        assertThat(m.getTrcoBsunPostAdrs()).isEqualTo("PRE-EXISTING");
        assertThat(m.getTrcoBsunCity()).isNull();
        assertThat(m.getTrcoBsunSt()).isNull();
    }

    @Test
    void nullAddress_noop() {
        BusaDocMstr m = new BusaDocMstr();
        EInvoiceDetailScrapeService.applyCounterpartyAddress(m, null);
        assertThat(m.getTrcoBsunPostAdrs()).isNull();
        assertThat(m.getTrcoBsunSt()).isNull();
    }
}
