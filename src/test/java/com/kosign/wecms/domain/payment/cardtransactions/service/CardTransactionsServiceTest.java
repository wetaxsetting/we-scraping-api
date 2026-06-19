package com.kosign.wecms.domain.payment.cardtransactions.service;

import com.kosign.wecms.common.auditlog.service.AuditLogContext;
import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.bankcredentials.repository.BankCertInfoRepository;
import com.kosign.wecms.domain.banking.entity.BankInfm;
import com.kosign.wecms.domain.banking.repository.BankInfmRepository;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolApprvrInfmRepository;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolMstrRepository;
import com.kosign.wecms.domain.payment.approvalbox.service.ApprovalBoxFanoutService;
import com.kosign.wecms.domain.payment.cardtransactions.dto.CardTransactionListResponse;
import com.kosign.wecms.domain.payment.cardtransactions.dto.CardTransactionRow;
import com.kosign.wecms.domain.payment.cardtransactions.dto.PatchDescriptionRequest;
import com.kosign.wecms.domain.payment.cardtransactions.dto.PatchPurposeRequest;
import com.kosign.wecms.domain.payment.cardtransactions.dto.UpdateReportingAccountRequest;
import com.kosign.wecms.domain.payment.cardtransactions.entity.CardMstrId;
import com.kosign.wecms.domain.payment.cardtransactions.entity.CardTrnsHstr;
import com.kosign.wecms.domain.payment.cardtransactions.repository.BusaDocRcvPayHisRepository;
import com.kosign.wecms.domain.payment.cardtransactions.repository.CardMstrRepository;
import com.kosign.wecms.domain.payment.cardtransactions.repository.CardTrnsHstrRepository;
import com.kosign.wecms.domain.payment.cardtransactions.repository.CardTrnsHstrRepository.LedgerTotalsProjection;
import com.kosign.wecms.domain.setting.approvalline.repository.ApprovalLineApproverRepository;
import com.kosign.wecms.domain.setting.chartofaccounts.entity.ChartAcctInfm;
import com.kosign.wecms.domain.setting.chartofaccounts.repository.ChartOfAccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Service-layer coverage for Payment &gt; Card Transactions. Pure
 * Mockito — no Spring context, no DB. Mirrors
 * {@code ChartOfAccountsServiceTest} / {@code ContactsServiceTest} in
 * shape: set {@link TenantContext}, exercise one method, assert the
 * repository / audit interactions and the response shape.
 */
@ExtendWith(MockitoExtension.class)
class CardTransactionsServiceTest {

    private static final String TENANT = "VN_VNM0004_000001";
    private static final String USER   = "tester";

    @Mock private CardTrnsHstrRepository           cardTrnsRepo;
    @Mock private CardMstrRepository               cardMstrRepo;
    @Mock private BankInfmRepository               bankInfmRepo;
    @Mock private BankCertInfoRepository           bankCertInfoRepo;
    @Mock private ChartOfAccountRepository         chartAcctRepo;
    @Mock private AuditLogService                  auditLogService;
    @Mock private ExptResolMstrRepository          exptResolMstrRepo;
    @Mock private ExptResolApprvrInfmRepository    exptResolApprvrRepo;
    @Mock private ApprovalLineApproverRepository   apprLineApproverRepo;
    @Mock private ApprovalBoxFanoutService         approvalBoxFanoutService;
    @Mock private BusaDocRcvPayHisRepository       busaDocRcvPayHisRepo;

    @InjectMocks
    private CardTransactionsService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(USER, TENANT, LangCd.EN);
        lenient().when(exptResolMstrRepo.findDocNoByApprSeqNos(any(), any()))
                .thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /* =================================================================
     *  list
     * ================================================================= */

    @Test
    void list_happyPathProjectsRowsAndTotals() {
        CardTrnsHstr row = entityRow("156", "11704020", "407465******5559",
                "20251019", "114140", "GOLDEN LOTUS - TVL",
                new BigDecimal("799200.000000"), "VND", "1", "1390");
        Page<CardTrnsHstr> page = new PageImpl<>(List.of(row), PageRequest.of(0, 15), 1L);
        when(cardTrnsRepo.findLedgerPage(eq(TENANT), eq("20250101"), eq("20251231"),
                eq(false), any(), eq(false), any(), eq(null), eq(null), any(Pageable.class)))
                .thenReturn(page);
        when(cardTrnsRepo.findLedgerTotals(eq(TENANT), eq("20250101"), eq("20251231"),
                eq(false), any(), eq(false), any(), eq(null), eq(null)))
                .thenReturn(totals(1L, "799200.000000", 1L, "799200.000000", 0L, "0"));
        when(bankInfmRepo.findById("11704020"))
                .thenReturn(Optional.of(bankInfm("11704020", "VietinBank")));
        when(cardMstrRepo.findById(any(CardMstrId.class)))
                .thenReturn(Optional.empty());

        CardTransactionListResponse res = service.list(
                "20250101", "20251231", null, null, null, null, null, null, 0, 15);

        assertThat(res.totalCount()).isEqualTo(1L);
        assertThat(res.page()).isZero();
        assertThat(res.pageSize()).isEqualTo(15);
        assertThat(res.rows()).hasSize(1);

        CardTransactionRow first = res.rows().get(0);
        assertThat(first.cardSeqNo()).isEqualTo("156");
        assertThat(first.bankCd()).isEqualTo("11704020");
        assertThat(first.bankNm()).isEqualTo("VietinBank");
        assertThat(first.merchNm()).isEqualTo("GOLDEN LOTUS - TVL");
        assertThat(first.trscAmt()).isEqualByComparingTo("799200.000000");
        assertThat(first.linkedDocs()).isEmpty();   // Phase B placeholder
        assertThat(first.approvers()).isEmpty();    // Phase B placeholder
        assertThat(first.stts()).isEqualTo("1");
        assertThat(first.sttsKey()).isEqualTo("payment.card_transactions.status.1");

        assertThat(res.totals().totalCount()).isEqualTo(1L);
        assertThat(res.totals().totlAmt()).isEqualByComparingTo("799200.000000");
        assertThat(res.totals().totlApprReqCnt()).isEqualTo(1L);
        assertThat(res.totals().totlApprNotReqCnt()).isZero();
    }

    @Test
    void list_emptyTenantPopulatesZeroedTotals() {
        Page<CardTrnsHstr> empty = new PageImpl<>(List.of(), PageRequest.of(0, 15), 0L);
        when(cardTrnsRepo.findLedgerPage(any(), any(), any(), anyBoolean(), any(), anyBoolean(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(empty);
        when(cardTrnsRepo.findLedgerTotals(any(), any(), any(), anyBoolean(), any(), anyBoolean(), any(), any(), any()))
                .thenReturn(totals(0L, "0", 0L, "0", 0L, "0"));

        CardTransactionListResponse res = service.list(
                "20250101", "20250131", null, null, null, null, null, null, 0, 15);

        assertThat(res.rows()).isEmpty();
        assertThat(res.totals().totalCount()).isZero();
        assertThat(res.totals().totlAmt()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    /* =================================================================
     *  D2 — bank_cd / card_no multi-select filters (audit D2)
     *  Legacy card_0001_001_r001_act.jsp:72-102 applies
     *  CTH.bank_cd IN (...) and CTH.card_no IN (...) to the grid AND
     *  the count. The refactor previously accepted both on the wire but
     *  dropped them. These tests pin the filters through to BOTH the
     *  page query and the totals query so grid + dashboard stay in sync.
     * ================================================================= */

    @Test
    void list_bankAndCardFilters_passedToPageAndTotals() {
        Page<CardTrnsHstr> empty = new PageImpl<>(List.of(), PageRequest.of(0, 15), 0L);
        when(cardTrnsRepo.findLedgerPage(any(), any(), any(), anyBoolean(), any(), anyBoolean(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(empty);
        when(cardTrnsRepo.findLedgerTotals(any(), any(), any(), anyBoolean(), any(), anyBoolean(), any(), any(), any()))
                .thenReturn(totals(0L, "0", 0L, "0", 0L, "0"));

        service.list("20250101", "20251231", null,
                List.of("11704020", "11704088"), List.of("407465******5559"),
                null, null, null, 0, 15);

        // Page query: filterBank=true with the two bank codes, filterCard=true with the one card.
        ArgumentCaptor<Boolean> fb = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<List<String>> banks = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Boolean> fc = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<List<String>> cards = ArgumentCaptor.forClass(List.class);
        verify(cardTrnsRepo).findLedgerPage(eq(TENANT), eq("20250101"), eq("20251231"),
                fb.capture(), banks.capture(), fc.capture(), cards.capture(),
                eq(null), eq(null), any(Pageable.class));
        assertThat(fb.getValue()).isTrue();
        assertThat(banks.getValue()).containsExactly("11704020", "11704088");
        assertThat(fc.getValue()).isTrue();
        assertThat(cards.getValue()).containsExactly("407465******5559");

        // Totals query must carry the SAME flags + lists so the dashboard
        // tiles reflect the same filtered set as the grid.
        verify(cardTrnsRepo).findLedgerTotals(eq(TENANT), eq("20250101"), eq("20251231"),
                eq(true), eq(List.of("11704020", "11704088")),
                eq(true), eq(List.of("407465******5559")),
                eq(null), eq(null));
    }

    @Test
    void list_noBankOrCardFilter_passesFalseFlagsAndNonEmptySentinel() {
        Page<CardTrnsHstr> empty = new PageImpl<>(List.of(), PageRequest.of(0, 15), 0L);
        when(cardTrnsRepo.findLedgerPage(any(), any(), any(), anyBoolean(), any(), anyBoolean(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(empty);
        when(cardTrnsRepo.findLedgerTotals(any(), any(), any(), anyBoolean(), any(), anyBoolean(), any(), any(), any()))
                .thenReturn(totals(0L, "0", 0L, "0", 0L, "0"));

        service.list("20250101", "20251231", null, null, null, null, null, null, 0, 15);

        ArgumentCaptor<Boolean> fb = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<List<String>> banks = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Boolean> fc = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<List<String>> cards = ArgumentCaptor.forClass(List.class);
        verify(cardTrnsRepo).findLedgerPage(eq(TENANT), any(), any(),
                fb.capture(), banks.capture(), fc.capture(), cards.capture(),
                any(), any(), any(Pageable.class));
        // Flags off, but the bound IN lists must stay NON-EMPTY so the
        // generated SQL `IN (?)` is valid (empty IN () is a SQL error).
        assertThat(fb.getValue()).isFalse();
        assertThat(fc.getValue()).isFalse();
        assertThat(banks.getValue()).isNotEmpty();
        assertThat(cards.getValue()).isNotEmpty();
    }

    /* =================================================================
     *  D1 — Linked / Unlinked filter forwards linkYn to page + totals
     *  (audit D1). The link semantics themselves (EXISTS over
     *  BUSA_DOC_RCV_PAY_HIS with RCV_PAY_WAY_CD='R') live in the
     *  repository @Query; here we pin that the normalised linkYn reaches
     *  BOTH queries so the grid and dashboard agree.
     * ================================================================= */

    @Test
    void list_linkYn_forwardedToPageAndTotals() {
        Page<CardTrnsHstr> empty = new PageImpl<>(List.of(), PageRequest.of(0, 15), 0L);
        when(cardTrnsRepo.findLedgerPage(any(), any(), any(), anyBoolean(), any(), anyBoolean(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(empty);
        when(cardTrnsRepo.findLedgerTotals(any(), any(), any(), anyBoolean(), any(), anyBoolean(), any(), any(), any()))
                .thenReturn(totals(0L, "0", 0L, "0", 0L, "0"));

        service.list("20250101", "20251231", null, null, null, "y", null, null, 0, 15);

        // "y" normalises to "Y"; both queries receive it.
        verify(cardTrnsRepo).findLedgerPage(eq(TENANT), any(), any(),
                anyBoolean(), any(), anyBoolean(), any(), eq(null), eq("Y"), any(Pageable.class));
        verify(cardTrnsRepo).findLedgerTotals(eq(TENANT), any(), any(),
                anyBoolean(), any(), anyBoolean(), any(), eq(null), eq("Y"));
    }

    @Test
    void listByApprSeqNo_surfacesLegacyCardDetailFields() {
        // GAP-CARD-FIELDS — the Approval Details card grid must expose the
        // legacy CARD_TRNS_HSTR_R002 detail fields (using type / auth code /
        // city / note) alongside the existing merchant / amount / currency.
        CardTrnsHstr row = entityRow("156", "11704020", "407465******5559",
                "20251019", "114140", "GOLDEN LOTUS - TVL",
                new BigDecimal("799200.000000"), "USD", "1", "1390");
        row.setUsingType("ONLINE");
        row.setAuthCode("AUTH123");
        row.setCity("HANOI");
        row.setNote("client dinner");
        when(cardTrnsRepo.findByBizKeyAndApprSeqNo(TENANT, "1390"))
                .thenReturn(List.of(row));
        when(bankInfmRepo.findById("11704020"))
                .thenReturn(Optional.of(bankInfm("11704020", "VietinBank")));
        when(cardMstrRepo.findById(any(CardMstrId.class)))
                .thenReturn(Optional.empty());

        List<CardTransactionRow> rows = service.listByApprSeqNo("1390");

        assertThat(rows).hasSize(1);
        CardTransactionRow r = rows.get(0);
        assertThat(r.usingType()).isEqualTo("ONLINE");
        assertThat(r.authCode()).isEqualTo("AUTH123");
        assertThat(r.city()).isEqualTo("HANOI");
        assertThat(r.note()).isEqualTo("client dinner");
        // Existing fields still flow through (currency drives the popup
        // header currency for Corporate Card per the GAP-CARD-CCY fix).
        assertThat(r.ccyCd()).isEqualTo("USD");
        assertThat(r.merchNm()).isEqualTo("GOLDEN LOTUS - TVL");
    }

    @Test
    void list_invalidDateRangeRejected() {
        assertThatThrownBy(() -> service.list(
                "bad", "20251231", null, null, null, null, null, null, 0, 15))
                .isInstanceOf(BusinessException.class)
                .extracting(b -> ((BusinessException) b).getCode())
                .isEqualTo("WBE_CARD_TRANSACTIONS_PERIOD_INVALID");

        assertThatThrownBy(() -> service.list(
                "20251231", "20250101", null, null, null, null, null, null, 0, 15))
                .isInstanceOf(BusinessException.class)
                .extracting(b -> ((BusinessException) b).getCode())
                .isEqualTo("WBE_CARD_TRANSACTIONS_PERIOD_INVALID");
    }

    // Period cap test removed 2026-05-20 — legacy `card_0001_001_r001`
    // has no 365-day limit (only start <= end). The refactor's earlier
    // MAX_PERIOD_DAYS = 365 constant was dropped from
    // `CardTransactionsService.validateDateRange` for legacy parity, so
    // a >365-day range no longer throws.

/* =================================================================
     *  patch description / purpose
     * ================================================================= */

    @Test
    void patchDescription_happyPathWritesAndAudits() {
        when(cardTrnsRepo.patchDescription(eq(TENANT), eq("156"),
                eq("Lunch with client"), eq(USER), any()))
                .thenReturn(1);

        service.patch("156", new PatchDescriptionRequest("description", "Lunch with client"));

        ArgumentCaptor<AuditLogContext> ctx = ArgumentCaptor.forClass(AuditLogContext.class);
        verify(auditLogService).append(ctx.capture());
        assertThat(ctx.getValue().wsvcId()).isEqualTo("card-transactions");
        assertThat(ctx.getValue().refNo()).isEqualTo("CARD_TRNS_HSTR");
        assertThat(ctx.getValue().refNo1()).isEqualTo("156");
        assertThat(ctx.getValue().detailKey()).isEqualTo("audit.card_transactions.patch_description.detail");
    }

    @Test
    void patchPurpose_happyPathWritesAndAudits() {
        when(cardTrnsRepo.patchPurpose(eq(TENANT), eq("156"),
                eq("1007010"), eq(USER), any()))
                .thenReturn(1);

        service.patch("156", new PatchPurposeRequest("purpose", "1007010"));

        ArgumentCaptor<AuditLogContext> ctx = ArgumentCaptor.forClass(AuditLogContext.class);
        verify(auditLogService).append(ctx.capture());
        assertThat(ctx.getValue().detailKey()).isEqualTo("audit.card_transactions.patch_purpose.detail");
    }

    @Test
    void patch_nullBodyRejected() {
        assertThatThrownBy(() -> service.patch("156", null))
                .isInstanceOf(BusinessException.class)
                .extracting(b -> ((BusinessException) b).getCode())
                .isEqualTo("WBE_VALIDATION");
        verify(auditLogService, never()).append(any());
    }

    @Test
    void patch_blankValueTrimsToNullThenWrites() {
        when(cardTrnsRepo.patchDescription(eq(TENANT), eq("156"),
                eq(null), eq(USER), any()))
                .thenReturn(1);

        service.patch("156", new PatchDescriptionRequest("description", "   "));

        verify(cardTrnsRepo).patchDescription(eq(TENANT), eq("156"),
                eq(null), eq(USER), any());
    }

    @Test
    void patch_pkMissesInTenantThrowsNotFound() {
        when(cardTrnsRepo.patchDescription(eq(TENANT), eq("missing"),
                any(), any(), any()))
                .thenReturn(0);

        assertThatThrownBy(() -> service.patch("missing",
                new PatchDescriptionRequest("description", "anything")))
                .isInstanceOf(BusinessException.class)
                .extracting(b -> ((BusinessException) b).getCode())
                .isEqualTo("WBE_CARD_TRANSACTIONS_NOT_FOUND");
        verify(auditLogService, never()).append(any());
    }

    /* =================================================================
     *  updateReportingAccount
     * ================================================================= */

    @Test
    void updateReportingAccount_happyPathValidatesChartAndAudits() {
        when(chartAcctRepo.findByBizKeyAndChartAcctCdAndDelYn(TENANT, "100", "N"))
                .thenReturn(Optional.of(chartAcct("100", "100", "A")));
        when(cardTrnsRepo.patchReportingAccount(eq(TENANT), eq("156"),
                eq("100"), eq("100"), eq(USER), any()))
                .thenReturn(1);

        service.updateReportingAccount("156",
                new UpdateReportingAccountRequest("100", "100"));

        ArgumentCaptor<AuditLogContext> ctx = ArgumentCaptor.forClass(AuditLogContext.class);
        verify(auditLogService).append(ctx.capture());
        assertThat(ctx.getValue().wsvcId()).isEqualTo("card-transactions");
        assertThat(ctx.getValue().refNo1()).isEqualTo("156");
        assertThat(ctx.getValue().refNo2()).isEqualTo("100");
        assertThat(ctx.getValue().detailKey()).isEqualTo("audit.card_transactions.update_reporting_account.detail");
    }

    @Test
    void updateReportingAccount_chartRowMissingThrowsChartAcctInvalid() {
        when(chartAcctRepo.findByBizKeyAndChartAcctCdAndDelYn(TENANT, "999", "N"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateReportingAccount("156",
                new UpdateReportingAccountRequest("999", "999")))
                .isInstanceOf(BusinessException.class)
                .extracting(b -> ((BusinessException) b).getCode())
                .isEqualTo("WBE_CARD_TRANSACTIONS_CHART_ACCT_INVALID");
        verify(auditLogService, never()).append(any());
    }

    @Test
    void updateReportingAccount_seqNoMismatchThrowsChartAcctInvalid() {
        when(chartAcctRepo.findByBizKeyAndChartAcctCdAndDelYn(TENANT, "100", "N"))
                .thenReturn(Optional.of(chartAcct("999", "100", "A"))); // seqNo drift

        assertThatThrownBy(() -> service.updateReportingAccount("156",
                new UpdateReportingAccountRequest("100", "100")))
                .isInstanceOf(BusinessException.class)
                .extracting(b -> ((BusinessException) b).getCode())
                .isEqualTo("WBE_CARD_TRANSACTIONS_CHART_ACCT_INVALID");
    }

    @Test
    void updateReportingAccount_inactiveChartRowThrowsChartAcctInvalid() {
        when(chartAcctRepo.findByBizKeyAndChartAcctCdAndDelYn(TENANT, "100", "N"))
                .thenReturn(Optional.of(chartAcct("100", "100", "I"))); // STTS inactive

        assertThatThrownBy(() -> service.updateReportingAccount("156",
                new UpdateReportingAccountRequest("100", "100")))
                .isInstanceOf(BusinessException.class)
                .extracting(b -> ((BusinessException) b).getCode())
                .isEqualTo("WBE_CARD_TRANSACTIONS_CHART_ACCT_INVALID");
    }

    @Test
    void updateReportingAccount_pkMissesInTenantThrowsNotFound() {
        when(chartAcctRepo.findByBizKeyAndChartAcctCdAndDelYn(TENANT, "100", "N"))
                .thenReturn(Optional.of(chartAcct("100", "100", "A")));
        when(cardTrnsRepo.patchReportingAccount(eq(TENANT), eq("missing"),
                any(), any(), any(), any()))
                .thenReturn(0);

        assertThatThrownBy(() -> service.updateReportingAccount("missing",
                new UpdateReportingAccountRequest("100", "100")))
                .isInstanceOf(BusinessException.class)
                .extracting(b -> ((BusinessException) b).getCode())
                .isEqualTo("WBE_CARD_TRANSACTIONS_NOT_FOUND");
        verify(auditLogService, never()).append(any());
    }

    /* =================================================================
     *  cards dropdown
     * ================================================================= */

    @Test
    void cards_resolvesBankNameAndProjects() {
        com.kosign.wecms.domain.payment.cardtransactions.entity.CardMstr c =
                new com.kosign.wecms.domain.payment.cardtransactions.entity.CardMstr();
        c.setBizKey(TENANT);
        c.setBankCd("11704088");
        c.setCardNo("4696********4372");
        c.setCardTypeCd("1 ");
        c.setCardNm("");
        c.setCardNickNm("Shinhan VN(4372)");
        c.setCardHldrNm("LEE JAEHWAN");
        c.setActvYn("Y");
        c.setRegId(USER);
        c.setRegDtm("20260101000000");
        c.setModId(USER);
        c.setModDtm("20260101000000");

        when(cardMstrRepo.findActiveCards(TENANT, null)).thenReturn(List.of(c));
        when(bankInfmRepo.findById("11704088"))
                .thenReturn(Optional.of(bankInfm("11704088", "Shinhan Bank VN")));

        var resp = service.cards(null);
        assertThat(resp.cards()).hasSize(1);
        assertThat(resp.cards().get(0).bankCd()).isEqualTo("11704088");
        assertThat(resp.cards().get(0).bankNm()).isEqualTo("Shinhan Bank VN");
        assertThat(resp.cards().get(0).cardHldrNm()).isEqualTo("LEE JAEHWAN");
    }

    /* ---------------------------------------------------------------
     *  Helpers
     * --------------------------------------------------------------- */

    private static CardTrnsHstr entityRow(String cardSeqNo, String bankCd, String cardNo,
                                          String trscDt, String trscTm, String merch,
                                          BigDecimal amt, String ccy, String stts,
                                          String apprSeqNo) {
        CardTrnsHstr e = new CardTrnsHstr();
        e.setBizKey(TENANT);
        e.setCardSeqNo(cardSeqNo);
        e.setBankCd(bankCd);
        e.setCardNo(cardNo);
        e.setTrscDt(trscDt);
        e.setTrscTm(trscTm);
        e.setMerchNm(merch);
        e.setCcyCd(ccy);
        e.setInoutDv("2");
        e.setTrscAmt(amt);
        e.setPrchStts(stts);
        e.setApprSeqNo(apprSeqNo);
        e.setRegId(USER);
        e.setRegDtm("20260101000000");
        e.setModId(USER);
        e.setModDtm("20260101000000");
        e.setErpSndYn("N");
        return e;
    }

    private static LedgerTotalsProjection totals(long count, String amt,
                                                  long reqCnt, String reqAmt,
                                                  long notReqCnt, String notReqAmt) {
        return new LedgerTotalsProjection() {
            @Override public Long       getTotalCount()          { return count; }
            @Override public BigDecimal getTotlAmt()             { return new BigDecimal(amt); }
            @Override public Long       getTotlApprReqCnt()      { return reqCnt; }
            @Override public BigDecimal getTotlApprReqAmt()      { return new BigDecimal(reqAmt); }
            @Override public Long       getTotlApprNotReqCnt()   { return notReqCnt; }
            @Override public BigDecimal getTotlApprNotReqAmt()   { return new BigDecimal(notReqAmt); }
        };
    }

    private static ChartAcctInfm chartAcct(String seqNo, String code, String stts) {
        ChartAcctInfm c = new ChartAcctInfm();
        c.setBizKey(TENANT);
        c.setChartAcctSeqNo(seqNo);
        c.setChartAcctCd(code);
        c.setChartAcctNmEn(code);
        c.setStts(stts);
        c.setDelYn("N");
        c.setRegId(USER);
        c.setRegDtm("20260101000000");
        c.setModId(USER);
        c.setModDtm("20260101000000");
        return c;
    }

    private static BankInfm bankInfm(String bankCd, String displayName) {
        BankInfm b = new BankInfm();
        b.setBankCd(bankCd);
        b.setRprsBankNm(displayName);
        // Whatever other columns BankInfm carries are not exercised by
        // this test — the resolver only reads bankCd + rprsBankNm.
        return b;
    }

    // Silences unused-mock warnings when Mockito sees the @Mock declared
    // but only some tests use it.
    @SuppressWarnings("unused")
    private void quietUnused() {
        lenient().when(bankInfmRepo.findById(any())).thenReturn(Optional.empty());
    }
}
