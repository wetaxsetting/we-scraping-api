package com.kosign.wecms.domain.payment.common;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.payment.controller.OverseasPaymentController;
import com.kosign.wecms.domain.payment.expensepayment.service.ExpensePaymentService;
import com.kosign.wecms.domain.payment.internaltransfer.dto.WithdrawalAccountRow;
import com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferService;
import com.kosign.wecms.domain.payment.payrollpayment.service.PayrollPaymentService;
import com.kosign.wecms.domain.payment.vendorpayment.service.VendorPaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Wiring lock for the Option A consolidation: every one of the five
 * withdrawal-account consumers must route its {@code listWithdrawalAccounts}
 * through the single shared {@link WithdrawalAccountListService}. Because
 * the shared service is the only strict (G1/G2/G3) implementation, proving
 * delegation proves each consumer is now strict — Internal Transfer and
 * Overseas keep that path, Vendor/Expense/Payroll move onto it.
 *
 * <p>Each consumer is built with {@code @InjectMocks}; only the shared
 * service is mocked (every other collaborator is irrelevant to this path
 * and is left null by Mockito's constructor injection).
 */
@ExtendWith(MockitoExtension.class)
class WithdrawalAccountDelegationTest {

    private static final List<WithdrawalAccountRow> STUB =
            List.of(new WithdrawalAccountRow("11704088", "Shinhan", "1111", "03", "VND", null, "ACCT HOLDER"));

    @Mock private WithdrawalAccountListService shared;

    @InjectMocks private InternalTransferService internalTransfer;
    @InjectMocks private VendorPaymentService    vendor;
    @InjectMocks private ExpensePaymentService   expense;
    @InjectMocks private PayrollPaymentService   payroll;
    @InjectMocks private OverseasPaymentController overseas;

    @Test
    void internalTransfer_delegatesToSharedService() {
        when(shared.list("USD")).thenReturn(STUB);
        assertThat(internalTransfer.listWithdrawalAccounts("USD")).isSameAs(STUB);
        verify(shared).list("USD");
    }

    @Test
    void vendor_delegatesToSharedService() {
        when(shared.list("USD")).thenReturn(STUB);
        assertThat(vendor.listWithdrawalAccounts("USD")).isSameAs(STUB);
        verify(shared).list("USD");
    }

    @Test
    void expense_delegatesToSharedService() {
        when(shared.list("USD")).thenReturn(STUB);
        assertThat(expense.listWithdrawalAccounts("USD")).isSameAs(STUB);
        verify(shared).list("USD");
    }

    @Test
    void payroll_delegatesToSharedService() {
        when(shared.list("USD")).thenReturn(STUB);
        assertThat(payroll.listWithdrawalAccounts("USD")).isSameAs(STUB);
        verify(shared).list("USD");
    }

    @Test
    void overseas_delegatesToSharedService_andNoLongerUsesInternalTransfer() {
        when(shared.list("USD")).thenReturn(STUB);
        ApiResponse<List<WithdrawalAccountRow>> response = overseas.listWithdrawalAccounts("USD");
        assertThat(response.getData()).isSameAs(STUB);
        verify(shared).list("USD");
    }
}
