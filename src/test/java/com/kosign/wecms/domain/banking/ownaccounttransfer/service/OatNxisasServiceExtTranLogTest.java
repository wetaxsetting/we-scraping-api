package com.kosign.wecms.domain.banking.ownaccounttransfer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.bankcredentials.repository.BankCertInfoRepository;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCertCryptoService;
import com.kosign.wecms.domain.banking.nxisas.audit.NxsiasModuleTranLogHisRepository;
import com.kosign.wecms.domain.banking.ownaccounttransfer.config.OatSeedDecryptProperties;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.request.NxisasLoggingRequest;
import com.kosign.wecms.domain.banking.synchistory.entity.ExtTranCallHis;
import com.kosign.wecms.domain.banking.synchistory.repository.ExtTranCallHisRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B10 — verifies the OAT logging path writes {@code EXT_TRAN_CALL_HIS}
 * for the scrape categories Settings &gt; Firm Banking History renders
 * ({@code BALANCE_INQUIRY} → "Scraping Balance"), and does NOT write one
 * for jobs the legacy path / FBH view ignore (LOGIN → BANKING_CREDENTIAL,
 * not in the EXT_TRAN leg).
 */
class OatNxisasServiceExtTranLogTest {

    private final ObjectMapper                   mapper     = new ObjectMapper();
    private final SeedCbcDecryptService          seedCbc    = new SeedCbcDecryptService(mapper);
    private ExtTranCallHisRepository             extTranRepo;
    private NxsiasModuleTranLogHisRepository     nxsiasRepo;
    private OatNxisasService                     service;

    @BeforeEach
    void setUp() {
        extTranRepo = mock(ExtTranCallHisRepository.class);
        nxsiasRepo  = mock(NxsiasModuleTranLogHisRepository.class);
        when(extTranRepo.findMaxSeqNoForDay(anyString(), anyString())).thenReturn(BigDecimal.ZERO);
        service = new OatNxisasService(
                mock(BankCertInfoRepository.class),
                mock(BankCertCryptoService.class),
                nxsiasRepo,
                extTranRepo,
                mapper,
                seedCbc,
                new OatSeedDecryptProperties(false));
        TenantContext.set("tester", "VN_VNM0004_000001", LangCd.EN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void updateBalance_writes_scraping_balance_ext_tran_row() {
        // Thread tree the bundle posts after Update Balance: an org-obj
        // (bankCd 11704088 = Shinhan VN) whose result[] holds LOGIN + BALANCE.
        String json = "[{\"bankCd\":\"11704088\",\"job\":[{\"Job\":\"BALANCE\"}],\"result\":["
                + "{\"Job\":\"LOGIN\",\"Module\":\"GLOBAL/GBL/BANK_Shinhan\","
                +   "\"Output\":{\"ErrorCode\":\"00000000\",\"ErrorMessage\":\"\"}},"
                + "{\"Job\":\"BALANCE\",\"Module\":\"GLOBAL/GBL/BANK_Shinhan\","
                +   "\"Input\":{\"acctNum\":\"700005285280\",\"currency\":\"VND\"},"
                +   "\"Output\":{\"ErrorCode\":\"00000000\",\"ErrorMessage\":\"\",\"Result\":\"{\\\"balance\\\":[{}]}\"}}"
                + "]}]";

        service.recordLogging(new NxisasLoggingRequest(json));

        // Exactly one EXT_TRAN_CALL_HIS row — for the BALANCE job. The LOGIN
        // job (BANKING_CREDENTIAL) is NOT written (legacy + FBH parity).
        ArgumentCaptor<ExtTranCallHis> cap = ArgumentCaptor.forClass(ExtTranCallHis.class);
        verify(extTranRepo, times(1)).save(cap.capture());

        ExtTranCallHis row = cap.getValue();
        assertThat(row.getSvcId()).isEqualTo("BALANCE_INQUIRY");   // view → "Scraping Balance"
        assertThat(row.getWebSvcId()).isEqualTo("BALANCE_INQUIRY");
        assertThat(row.getBankCd()).isEqualTo("11704088");         // view → "Shinhan VN - 11704088"
        assertThat(row.getRsltCd()).isEqualTo("00000000");
        assertThat(row.getAcctNo()).isEqualTo("700005285280");
        assertThat(row.getBizKey()).isEqualTo("VN_VNM0004_000001");
        assertThat(row.getSeqNo()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(row.getRefDate()).hasSize(8);
        assertThat(row.getRegDtm()).hasSize(14);
    }

    @Test
    void loginOnly_does_not_write_ext_tran_row() {
        // A credential/login-only run → BANKING_CREDENTIAL, which the FBH
        // EXT_TRAN leg does not render → no EXT_TRAN_CALL_HIS row.
        String json = "[{\"bankCd\":\"11704088\",\"job\":[{\"Job\":\"ACCT_LIST\"}],\"result\":["
                + "{\"Job\":\"LOGIN\",\"Output\":{\"ErrorCode\":\"00000000\",\"ErrorMessage\":\"\"}}]}]";

        service.recordLogging(new NxisasLoggingRequest(json));

        verify(extTranRepo, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
