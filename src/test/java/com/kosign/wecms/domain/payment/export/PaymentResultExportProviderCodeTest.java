package com.kosign.wecms.domain.payment.export;

import com.kosign.wecms.domain.payment.expensepayment.export.ExpensePaymentResultExportProvider;
import com.kosign.wecms.domain.payment.expensepayment.service.ExpensePaymentResultService;
import com.kosign.wecms.domain.payment.internaltransfer.export.InternalTransferResultExportProvider;
import com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferResultService;
import com.kosign.wecms.domain.payment.payrollpayment.export.PayrollPaymentResultExportProvider;
import com.kosign.wecms.domain.payment.payrollpayment.service.PayrollPaymentResultService;
import com.kosign.wecms.domain.payment.vendorpayment.export.VendorPaymentResultExportProvider;
import com.kosign.wecms.domain.payment.vendorpayment.service.VendorPaymentResultService;
import com.kosign.wecms.domain.setting.erptransactionhistory.service.ErpTrnsHistoryWriter;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PaymentResultExportProviderCodeTest {
    private void check(PaymentResultExportProvider p, String expectedCode) {
        ErpTrnsHistoryWriter w = mock(ErpTrnsHistoryWriter.class);
        ReflectionTestUtils.setField(p, "erpHistoryWriter", w);
        assertThat(p.auditsErpExport()).isTrue();
        Path f = Path.of("export.xlsx");
        p.recordErpExport(f, "name.xlsx");
        verify(w).recordExportedFile(expectedCode, f, "name.xlsx");
    }
    @Test void vendor_code_04() {
        check(new VendorPaymentResultExportProvider(mock(VendorPaymentResultService.class)), "04");
    }
    @Test void expense_code_05() {
        check(new ExpensePaymentResultExportProvider(mock(ExpensePaymentResultService.class)), "05");
    }
    @Test void payroll_code_06() {
        check(new PayrollPaymentResultExportProvider(mock(PayrollPaymentResultService.class)), "06");
    }
    @Test void internal_transfer_code_17() {
        check(new InternalTransferResultExportProvider(mock(InternalTransferResultService.class)), "17");
    }
}
