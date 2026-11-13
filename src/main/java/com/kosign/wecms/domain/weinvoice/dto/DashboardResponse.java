package com.kosign.wecms.domain.weinvoice.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@code GET /dashboard} (screen 03). Amounts in VND; cancelled and replaced invoices are left
 * out of every figure.
 */
public record DashboardResponse(
        String companyMst,
        int year,
        Kpis kpis,
        List<MonthPoint> months,
        DataStatus dataStatus,
        List<TopPartner> topSuppliers,
        LastSync lastSync) {

    /** @param vatPayable output VAT minus input VAT */
    public record Kpis(long count, long salesCount, long purchaseCount, BigDecimal salesTotal,
                       BigDecimal purchaseTotal, BigDecimal outVat, BigDecimal inVat, BigDecimal vatPayable) {
    }

    /** @param month {@code yyyy-MM} */
    public record MonthPoint(String month, BigDecimal sales, BigDecimal purchase, long salesCount, long purchaseCount) {
    }

    /**
     * @param missingXml        coded invoices without XML
     * @param noCodeNoXml       invoices without code still without XML
     * @param missingPdf        invoices without original PDF
     * @param incompletePeriods months whose GDT list is not complete
     */
    public record DataStatus(long missingXml, long noCodeNoXml, long missingPdf,
                             List<IncompletePeriod> incompletePeriods) {
    }

    public record TopPartner(String taxId, String name, long count, BigDecimal total) {
    }

    /** @param status engine status of the run: ok | partial | error */
    public record LastSync(String finishedAt, String status) {
    }
}
