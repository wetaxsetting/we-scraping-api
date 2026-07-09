package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.dto.DailyFinancialHsEntryUpdate;
import com.kosign.wecms.domain.banking.entity.ReptTrnsHstr;
import com.kosign.wecms.domain.banking.repository.ReptTrnsHstrRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guards the HS editable-cell save (legacy {@code rept_0017_001_u001}):
 * per-field patch, tenant-scoped PK lookup, MOD_ID stamp, and the
 * not-found business error. Balances / other columns are never touched.
 */
@ExtendWith(MockitoExtension.class)
class DailyFinancialHsWriteServiceTest {

    @Mock ReptTrnsHstrRepository repo;

    DailyFinancialHsWriteService service;

    private static final String BIZ  = "VN_VNM0004_000014"; // HAENGSUNG tenant
    private static final String USER = "tester";
    private static final String SEQ  = "AC0001";

    @BeforeEach
    void setUp() {
        service = new DailyFinancialHsWriteService(repo);
        TenantContext.set(USER, BIZ, LangCd.EN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private ReptTrnsHstr existingRow() {
        ReptTrnsHstr e = new ReptTrnsHstr();
        e.setBizKey(BIZ);
        e.setAcctSeqNo(SEQ);
        e.setReptCd("OLD_RC");
        e.setPymnCd("OLD_PC");
        e.setDescNmEn("old-en");
        e.setDescNmKo("old-ko");
        e.setMemo("old-memo");
        return e;
    }

    @Test
    void appliesOnlyProvidedFields_andStampsModId_scopedToTenant() {
        ReptTrnsHstr row = existingRow();
        when(repo.findByBizKeyAndAcctSeqNo(BIZ, SEQ)).thenReturn(Optional.of(row));

        // Only Receipt Code provided — the other columns must stay untouched.
        service.updateEntry(SEQ, new DailyFinancialHsEntryUpdate("RC_NEW", null, null, null, null));

        // Tenant scope comes from the JWT (TenantContext), never the request.
        verify(repo).findByBizKeyAndAcctSeqNo(BIZ, SEQ);

        ArgumentCaptor<ReptTrnsHstr> cap = ArgumentCaptor.forClass(ReptTrnsHstr.class);
        verify(repo).save(cap.capture());
        ReptTrnsHstr saved = cap.getValue();
        assertThat(saved.getReptCd()).isEqualTo("RC_NEW");
        assertThat(saved.getPymnCd()).isEqualTo("OLD_PC");
        assertThat(saved.getDescNmEn()).isEqualTo("old-en");
        assertThat(saved.getDescNmKo()).isEqualTo("old-ko");
        assertThat(saved.getMemo()).isEqualTo("old-memo");
        assertThat(saved.getModId()).isEqualTo(USER);
    }

    @Test
    void emptyStringClearsField() {
        ReptTrnsHstr row = existingRow();
        when(repo.findByBizKeyAndAcctSeqNo(BIZ, SEQ)).thenReturn(Optional.of(row));

        service.updateEntry(SEQ, new DailyFinancialHsEntryUpdate(null, null, null, null, ""));

        ArgumentCaptor<ReptTrnsHstr> cap = ArgumentCaptor.forClass(ReptTrnsHstr.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getMemo()).isEmpty();
        assertThat(cap.getValue().getReptCd()).isEqualTo("OLD_RC"); // untouched
    }

    @Test
    void unknownRowThrowsNotFound_andDoesNotSave() {
        when(repo.findByBizKeyAndAcctSeqNo(BIZ, SEQ)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.updateEntry(SEQ, new DailyFinancialHsEntryUpdate("x", null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode())
                        .isEqualTo("WBE_DAILY_FINANCIAL_HS_ENTRY_NOT_FOUND"));

        verify(repo, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
