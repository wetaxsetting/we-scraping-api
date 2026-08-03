package com.kosign.wecms.domain.vatdeclaration.export;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.vatdeclaration.entity.VatDeclaration;
import com.kosign.wecms.domain.vatdeclaration.entity.VatDeclarationIndicator;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;

/**
 * Fills the designed 01/GTGT Excel form (the PO's
 * {@code WeCMS_VAT_Report_download_{EN,VN}.xlsx}) with a declaration's data —
 * "template-fill": the shipped workbook is loaded from the classpath and only
 * its data cells are set, so every merge / style / border stays pixel-identical
 * to the design. Values are written as literals (from the authoritative
 * {@code VatDeclarationIndicator}) — never relying on the template's in-cell
 * formulas — so the output is correct without an Excel recalc pass.
 *
 * <p>EN ↔ VN chosen by the session {@link LangCd} (VI → VN, else EN). Both
 * templates share the exact same cell coordinates (verified) — one cell map.
 */
@Slf4j
@Component
public class VatReportExcelExporter {

    private static final String TEMPLATE_EN = "templates/vat/vat-report-en.xlsx";
    private static final String TEMPLATE_VN = "templates/vat/vat-report-vn.xlsx";
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public VatExportFile build(VatDeclaration d, VatDeclarationIndicator i, LangCd lang) {
        String template = lang == LangCd.VI ? TEMPLATE_VN : TEMPLATE_EN;
        byte[] bytes;
        try (InputStream in = new ClassPathResource(template).getInputStream();
             XSSFWorkbook wb = new XSSFWorkbook(in)) {
            Sheet s = wb.getSheetAt(0);

            // --- Header [01a]–[11a] ---
            text(s, "F4", d.getActivityName());          // [01a] activity name
            text(s, "G8", d.getPeriod());                // [01b] tax period
            text(s, "D10", d.getTpName());               // [04] taxpayer name
            text(s, "D11", d.getTpTaxCode());            // [05] tax code
            text(s, "D12", d.getAgentName());            // [06] tax agent name
            text(s, "D13", d.getAgentTaxCode());         // [07] agent tax code
            text(s, "D14", d.getAgentCtrNo());           // [08] contract no
            text(s, "J14", d.getAgentCtrDt() == null ? null : d.getAgentCtrDt().format(DMY));
            text(s, "B16", d.getDuName());               // [09] dependent unit
            text(s, "H18", d.getDuTaxCode());            // [10] DU tax code
            text(s, "D22", d.getDuProv());               // [11c] province
            text(s, "D24", d.getDuDist());               // [11b] district
            text(s, "D26", d.getDuWard());               // [11a] ward

            // --- Indicators — value-of-goods column J, VAT-amount column L ---
            text(s, "I36", pos(i.getNoActivity()) ? "X" : "");   // [21] no-activity mark
            num(s, "L37", i::getPrevCredit);             // [22]
            num(s, "J40", i::getPurchAmt);               // [23]
            num(s, "L40", i::getPurchAmtVat);            // [24]
            num(s, "J41", i::getImpAmt);                 // [23a]
            num(s, "L41", i::getImpAmtVat);              // [24a]
            num(s, "L42", i::getDeductVatAmt);           // [25]
            num(s, "J44", i::getSalesAmtNondecl);        // [26]
            num(s, "J45", i::getTolSalesAmt);            // [27]
            num(s, "L45", i::getTotSalesAmtVat);         // [28]
            num(s, "J46", i::getSalesAmt0);              // [29]
            num(s, "J47", i::getSalesAmt5);              // [30]
            num(s, "L47", i::getSalesAmtVat5);           // [31]
            num(s, "J48", i::getSalesAmt10);             // [32]
            num(s, "L48", i::getSalesAmtVat10);          // [33]
            num(s, "J49", i::getSalesAmtNontax);         // [32a]
            num(s, "J50", i::getTotalSalesAmt);          // [34]
            num(s, "L50", i::getTotalSalesAmtVat);       // [35]
            num(s, "L51", i::getVatIncurred);            // [36]
            num(s, "L53", i::getAdjDecrease);            // [37]
            num(s, "L54", i::getAdjIncrease);            // [38]
            num(s, "L55", i::getTransfVat);              // [39a]
            num(s, "L57", i::getVatPayableActivity);     // [40a]
            num(s, "L58", i::getInvestOffsetVatAmt);     // [40b]
            num(s, "L59", i::getVatPayableNet);          // [40]
            num(s, "L60", i::getVatNotDeducted);         // [41]
            num(s, "L61", i::getVatRefundReq);           // [42]
            num(s, "L62", i::getCreditNext);             // [43]

            wb.setForceFormulaRecalculation(true);
            ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
            wb.write(out);
            bytes = out.toByteArray();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("VAT report Excel build failed for sid {}", d.getSid(), e);
            throw new BusinessException("WBE_VAT_DECL_EXPORT_FAILED");
        }
        return new VatExportFile(fileName(d),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
    }

    private String fileName(VatDeclaration d) {
        boolean monthly = "month".equalsIgnoreCase(d.getPeriodType());
        String period = monthly
                ? String.format("M%02d%d", nz(d.getPeriodValue()), nz(d.getPeriodYear()))
                : String.format("Q%d%d", nz(d.getPeriodValue()), nz(d.getPeriodYear()));
        return VatXmlExporter.pad13(d.getTpTaxCode()) + "-01_GTGT_TT80-" + period + ".xlsx";
    }

    /** Set an integer-VND numeric literal on a (possibly merged-anchor) cell. */
    private static void num(Sheet s, String ref, Supplier<BigDecimal> v) {
        BigDecimal b = v.get();
        cell(s, ref).setCellValue(b == null ? 0d
                : b.setScale(0, java.math.RoundingMode.DOWN).doubleValue());
    }

    private static void text(Sheet s, String ref, String value) {
        if (value == null) {
            return;   // leave the template's default in place
        }
        cell(s, ref).setCellValue(value);
    }

    private static Cell cell(Sheet s, String ref) {
        CellReference cr = new CellReference(ref);
        Row row = s.getRow(cr.getRow());
        if (row == null) {
            row = s.createRow(cr.getRow());
        }
        Cell c = row.getCell(cr.getCol());
        if (c == null) {
            c = row.createCell(cr.getCol());
        }
        return c;
    }

    private static boolean pos(BigDecimal v) {
        return v != null && v.signum() > 0;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
