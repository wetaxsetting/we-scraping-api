package com.kosign.wecms.domain.weinvoice.service;

import com.kosign.wecms.domain.weinvoice.common.CompanyAccessGuard;
import com.kosign.wecms.domain.weinvoice.dto.DashboardResponse;
import com.kosign.wecms.domain.weinvoice.repository.CompanyConnectionRepository.CompanyRow;
import com.kosign.wecms.domain.weinvoice.repository.DashboardRepository;
import com.kosign.wecms.domain.weinvoice.repository.InvoiceQueryRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

/** Screen 03 — KPIs, 12-month chart, data status, top suppliers, last sync of one MST and year. */
@Service
public class DashboardService {

    private static final int TOP_SUPPLIERS = 5;

    private final CompanyAccessGuard guard;
    private final DashboardRepository dashboard;
    private final InvoiceQueryRepository invoices;

    public DashboardService(CompanyAccessGuard guard, DashboardRepository dashboard, InvoiceQueryRepository invoices) {
        this.guard = guard;
        this.dashboard = dashboard;
        this.invoices = invoices;
    }

    public DashboardResponse get(String companyMst, Integer year) {
        CompanyRow company = guard.requireCompany(companyMst);
        int y = year == null ? Year.now().getValue() : year;
        if (y < 2000 || y > 2100) {
            throw new IllegalArgumentException("year: out of range");
        }
        String bizKey = guard.bizKey();
        String mst = company.mst();
        LocalDate from = LocalDate.of(y, 1, 1);
        LocalDate to = LocalDate.of(y, 12, 31);

        BigDecimal[] sales = zeros();
        BigDecimal[] purchase = zeros();
        long[] salesCount = new long[12];
        long[] purchaseCount = new long[12];
        BigDecimal outVat = BigDecimal.ZERO;
        BigDecimal inVat = BigDecimal.ZERO;
        for (DashboardRepository.MonthRow m : dashboard.monthly(bizKey, mst, from, to)) {
            int idx = Integer.parseInt(m.month().substring(5)) - 1;
            if ("ban".equals(m.engineDirection())) {
                sales[idx] = sales[idx].add(m.total());
                salesCount[idx] += m.count();
                outVat = outVat.add(m.tax());
            } else {
                purchase[idx] = purchase[idx].add(m.total());
                purchaseCount[idx] += m.count();
                inVat = inVat.add(m.tax());
            }
        }
        List<DashboardResponse.MonthPoint> months = new ArrayList<>(12);
        long sc = 0;
        long pc = 0;
        BigDecimal st = BigDecimal.ZERO;
        BigDecimal pt = BigDecimal.ZERO;
        for (int i = 0; i < 12; i++) {
            months.add(new DashboardResponse.MonthPoint(String.format("%d-%02d", y, i + 1),
                    sales[i], purchase[i], salesCount[i], purchaseCount[i]));
            sc += salesCount[i];
            pc += purchaseCount[i];
            st = st.add(sales[i]);
            pt = pt.add(purchase[i]);
        }
        DashboardResponse.Kpis kpis = new DashboardResponse.Kpis(sc + pc, sc, pc, st, pt, outVat, inVat,
                outVat.subtract(inVat));

        DashboardRepository.DataStatusRow ds = dashboard.dataStatus(bizKey, mst, from, to);
        DashboardResponse.DataStatus dataStatus = new DashboardResponse.DataStatus(ds.missingXml(), ds.noCodeNoXml(),
                ds.missingPdf(), invoices.incompletePeriods(mst, null, y + "01", y + "12"));

        List<DashboardResponse.TopPartner> top = dashboard.topSuppliers(bizKey, mst, from, to, TOP_SUPPLIERS).stream()
                .map(p -> new DashboardResponse.TopPartner(p.taxId(), p.name(), p.count(), p.total()))
                .toList();
        DashboardResponse.LastSync last = dashboard.lastRun(mst)
                .map(r -> new DashboardResponse.LastSync(r.finishedAt(), r.status()))
                .orElse(null);
        return new DashboardResponse(mst, y, kpis, months, dataStatus, top, last);
    }

    private static BigDecimal[] zeros() {
        BigDecimal[] a = new BigDecimal[12];
        java.util.Arrays.fill(a, BigDecimal.ZERO);
        return a;
    }
}
