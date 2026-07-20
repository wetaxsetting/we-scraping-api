package com.kosign.wecms.domain.einvoice.service;

import com.kosign.wecms.domain.einvoice.entity.BusaDocMstr;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D5.4 — payment-term due-date recompute ({@link EInvoiceDetailScrapeService#applyDueDate},
 * legacy {@code nxisas_einvoice_c002_act.jsp:691-711}, #40183). Pins the exact
 * legacy behaviour PO-confirmed 2026-07-14: DUE_DT is recomputed / NULLed on
 * every scrape (no user-edit preservation), guarded by ETC / PA-PP / valid
 * ISSU_DT.
 */
class EInvoiceDueDateTest {

    private static final String NON_ETC = "1000001";

    private static BusaDocMstr master(String issuDt, String provStts, String dueDt) {
        BusaDocMstr m = new BusaDocMstr();
        m.setIssuDt(issuDt);
        m.setProvStts(provStts);
        m.setDueDt(dueDt);
        return m;
    }

    @Test
    void setsDueDate_issuePlusTermDays() {
        BusaDocMstr m = master("20260701", "OP", null);
        EInvoiceDetailScrapeService.applyDueDate(m, NON_ETC, "30");
        assertThat(m.getDueDt()).isEqualTo("20260731"); // +30 days
    }

    @Test
    void crossesMonthBoundary() {
        BusaDocMstr m = master("20260220", "OP", null);
        EInvoiceDetailScrapeService.applyDueDate(m, NON_ETC, "15");
        assertThat(m.getDueDt()).isEqualTo("20260307"); // Feb 20 + 15 (2026 not leap)
    }

    @Test
    void blankTerm_nullsDueDate_evenIfPreviouslySet() {
        // Legacy overwrites: a blank payment term clears DUE_DT to null.
        BusaDocMstr m = master("20260701", "OP", "20260801"); // user-edited due date
        EInvoiceDetailScrapeService.applyDueDate(m, NON_ETC, "  ");
        assertThat(m.getDueDt()).isNull();
    }

    @Test
    void etcCounterparty_leavesDueDateUntouched() {
        BusaDocMstr m = master("20260701", "OP", "20260801");
        EInvoiceDetailScrapeService.applyDueDate(m, "99999999999999999999", "30");
        assertThat(m.getDueDt()).isEqualTo("20260801");
    }

    @Test
    void paidStatus_PA_leavesDueDateUntouched() {
        BusaDocMstr m = master("20260701", "PA", "20260801");
        EInvoiceDetailScrapeService.applyDueDate(m, NON_ETC, "30");
        assertThat(m.getDueDt()).isEqualTo("20260801");
    }

    @Test
    void partlyPaidStatus_PP_leavesDueDateUntouched() {
        BusaDocMstr m = master("20260701", "PP", "20260801");
        EInvoiceDetailScrapeService.applyDueDate(m, NON_ETC, "30");
        assertThat(m.getDueDt()).isEqualTo("20260801");
    }

    @Test
    void invalidIssueDate_leavesDueDateUntouched() {
        BusaDocMstr m = master("", "OP", "20260801");
        EInvoiceDetailScrapeService.applyDueDate(m, NON_ETC, "30");
        assertThat(m.getDueDt()).isEqualTo("20260801");
    }

    @Test
    void nonNumericTerm_leavesDueDateUntouched() {
        BusaDocMstr m = master("20260701", "OP", "20260801");
        EInvoiceDetailScrapeService.applyDueDate(m, NON_ETC, "N/A");
        assertThat(m.getDueDt()).isEqualTo("20260801");
    }
}
