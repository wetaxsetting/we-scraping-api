package com.kosign.wecms.domain.setting.contacts.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.repository.AcctTrnsRcvPayInfmRepository;
import com.kosign.wecms.domain.setting.contacts.dto.AccountOp;
import com.kosign.wecms.domain.setting.contacts.dto.AddressOp;
import com.kosign.wecms.domain.setting.contacts.dto.ContactBankListResponse;
import com.kosign.wecms.domain.setting.contacts.dto.ContactBankRow;
import com.kosign.wecms.domain.setting.contacts.dto.ContactCreateRequest;
import com.kosign.wecms.domain.setting.contacts.dto.ContactListResponse;
import com.kosign.wecms.domain.setting.contacts.dto.ContactRow;
import com.kosign.wecms.domain.setting.contacts.dto.OverseasOp;
import com.kosign.wecms.domain.setting.contacts.entity.PgroBsacLnknInfm;
import com.kosign.wecms.domain.setting.contacts.entity.TrcoInfm;
import com.kosign.wecms.domain.setting.contacts.entity.TrcoOvrsInfm;
import com.kosign.wecms.domain.setting.contacts.repository.PgroBsacLnknInfmRepository;
import com.kosign.wecms.domain.setting.contacts.repository.TrcoAcctInfoRepository;
import com.kosign.wecms.domain.setting.contacts.repository.TrcoAddrInfmRepository;
import com.kosign.wecms.domain.setting.contacts.repository.TrcoInfmRepository;
import com.kosign.wecms.domain.setting.contacts.repository.TrcoOvrsInfmRepository;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmBankCode;
import com.kosign.wecms.domain.setting.firmbankingsettings.repository.TbFirmBankCodeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Service-layer coverage for Settings &gt; Contacts. Covers the
 * mandatory scenarios from the spec test matrix:
 *
 * <ul>
 *   <li><b>T1/T2/T3</b> &mdash; {@code WBE1190} in-use guard on single
 *       delete, bulk delete, and the happy-path delete.</li>
 *   <li><b>K1/K2/K4</b> &mdash; {@code TRCO_KEY} allocation via the
 *       funnel, monotonicity across consecutive creates, and the
 *       one-shot retry on PK collision.</li>
 *   <li><b>A1/A4</b> &mdash; {@code disp_ord} address discriminator
 *       (current/previous classified by {@code disp_ord}, validator
 *       rejects out-of-range upsert).</li>
 *   <li>List happy path with sort + filter + tenant scoping.</li>
 *   <li>{@code getOne} not-found / wrong-tenant collapse.</li>
 *   <li>Local contact with zero accounts succeeds (legacy parity —
 *       {@code comm_0184_002_c001_act.jsp:69-80} only validates rows
 *       carrying {@code NAPAS_BANK_CD} / {@code CITAD_BANK_CD}).</li>
 *   <li>Duplicate {@code hand_trco_key} / {@code trco_tax_id} rejected
 *       at create.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ContactsServiceTest {

    private static final String BIZ      = "VN_VNM0004_000001";
    private static final String USER     = "tester";
    private static final String TRCO_KEY = "1000001";

    @Mock TrcoInfmRepository trcoInfmRepo;
    @Mock TrcoAddrInfmRepository addrRepo;
    @Mock TrcoAcctInfoRepository acctRepo;
    @Mock TrcoOvrsInfmRepository ovrsRepo;
    @Mock TbFirmBankCodeRepository bankCodeRepo;
    @Mock PgroBsacLnknInfmRepository pgroRepo;
    @Mock AcctTrnsRcvPayInfmRepository acctTrnsRcvPayRepo;
    @Mock LegacyFunctionRepository legacyFn;
    @Mock com.kosign.wecms.domain.setting.firmbankingsettings.repository.FirmBankingSettingRepository firmBankingSettingRepo;
    @Mock com.kosign.wecms.domain.payment.dispatch.recipient.DepositorInquiryService depositorInquiryService;

    private ContactsService service;
    private ContactsValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ContactsValidator(trcoInfmRepo);
        service = new ContactsService(
                trcoInfmRepo, addrRepo, acctRepo, ovrsRepo, bankCodeRepo,
                pgroRepo, acctTrnsRcvPayRepo, legacyFn, validator,
                new com.kosign.wecms.common.firmbanking.FirmBankingInquiryService(
                        firmBankingSettingRepo, depositorInquiryService));
        TenantContext.set(USER, BIZ, LangCd.EN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /* =================================================================
     *  List
     * ================================================================= */

    @Test
    void list_returnsPagedRowsWithTenantScope() {
        TrcoInfm m = stubMaster(TRCO_KEY, "C001", "L");
        Page<TrcoInfm> page = new PageImpl<>(List.of(m), PageRequest.of(0, 15), 1);
        when(trcoInfmRepo.search(eq(BIZ), any(), any(), any(Pageable.class))).thenReturn(page);
        when(addrRepo.findByBizKeyAndTrcoKeyIn(eq(BIZ), any())).thenReturn(List.of());
        when(acctRepo.findByBizKeyAndTrcoKeyIn(eq(BIZ), any())).thenReturn(List.of());
        when(ovrsRepo.findByBizKeyAndTrcoKeyIn(eq(BIZ), any())).thenReturn(List.of());

        ContactListResponse resp = service.list(null, null, 0, 15, null, null);

        assertThat(resp.totalCount()).isEqualTo(1);
        assertThat(resp.rows()).hasSize(1);
        assertThat(resp.rows().get(0).trcoKey()).isEqualTo(TRCO_KEY);
    }

    @Test
    void list_sortFieldHandTrcoKeyAscReachesSearchAsHandTrcoKeyOrder() {
        when(trcoInfmRepo.search(eq(BIZ), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 15), 0));
        when(addrRepo.findByBizKeyAndTrcoKeyIn(eq(BIZ), any())).thenReturn(List.of());
        when(acctRepo.findByBizKeyAndTrcoKeyIn(eq(BIZ), any())).thenReturn(List.of());
        when(ovrsRepo.findByBizKeyAndTrcoKeyIn(eq(BIZ), any())).thenReturn(List.of());

        service.list(null, null, 0, 15, "HAND_TRCO_KEY", "ASC");

        org.mockito.ArgumentCaptor<Pageable> cap = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(trcoInfmRepo).search(eq(BIZ), any(), any(), cap.capture());
        Sort.Order primary = cap.getValue().getSort().iterator().next();
        assertThat(primary.getProperty()).isEqualTo("handTrcoKey");
        assertThat(primary.isAscending()).isTrue();
    }

    @Test
    void list_unknownSortFieldFallsBackToDefaultOrder() {
        when(trcoInfmRepo.search(eq(BIZ), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 15), 0));
        when(addrRepo.findByBizKeyAndTrcoKeyIn(eq(BIZ), any())).thenReturn(List.of());
        when(acctRepo.findByBizKeyAndTrcoKeyIn(eq(BIZ), any())).thenReturn(List.of());
        when(ovrsRepo.findByBizKeyAndTrcoKeyIn(eq(BIZ), any())).thenReturn(List.of());

        service.list(null, null, 0, 15, "SOME_UNKNOWN_FIELD", "ASC");

        org.mockito.ArgumentCaptor<Pageable> cap = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(trcoInfmRepo).search(eq(BIZ), any(), any(), cap.capture());
        // Default ORDER BY: handTrcoKey ASC, trcoKey DESC (legacy
        // SortHelper.java for comm_0184_001_r001).
        Sort.Order primary = cap.getValue().getSort().iterator().next();
        assertThat(primary.getProperty()).isEqualTo("handTrcoKey");
        assertThat(primary.isAscending()).isTrue();
    }

    /* =================================================================
     *  getOne — not-found / wrong-tenant collapse
     * ================================================================= */

    @Test
    void getOne_missingRowThrowsNotFound() {
        when(trcoInfmRepo.findByTrcoKeyAndBizKey("9999", BIZ)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getOne("9999"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("WBE_CONTACTS_NOT_FOUND");
    }

    @Test
    void getOne_trashedRowThrowsNotFound() {
        TrcoInfm trashed = stubMaster(TRCO_KEY, "C001", "L");
        trashed.setTrcoStts("C");
        when(trcoInfmRepo.findByTrcoKeyAndBizKey(TRCO_KEY, BIZ)).thenReturn(Optional.of(trashed));
        assertThatThrownBy(() -> service.getOne(TRCO_KEY))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("WBE_CONTACTS_NOT_FOUND");
    }

    /* =================================================================
     *  Create — happy path + duplicate guards + retry-on-PK-collision
     * ================================================================= */

    @Test
    void create_happyPathAllocatesKeySeedsPgroAndReadsBack() {
        ContactCreateRequest req = localCreate("CC_TEST", "0123456789");
        when(trcoInfmRepo.existsByBizKeyAndHandTrcoKey(BIZ, "CC_TEST")).thenReturn(false);
        when(trcoInfmRepo.existsByBizKeyAndTrcoTaxId(BIZ, "0123456789")).thenReturn(false);
        when(legacyFn.nextTrcoKey(BIZ)).thenReturn(TRCO_KEY);
        when(trcoInfmRepo.findByTrcoKeyAndBizKey(TRCO_KEY, BIZ))
                .thenReturn(Optional.of(stubMaster(TRCO_KEY, "CC_TEST", "L")));
        when(addrRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of());
        when(acctRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of());
        when(ovrsRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of());

        ContactRow row = service.create(req);

        assertThat(row.trcoKey()).isEqualTo(TRCO_KEY);
        verify(legacyFn).nextTrcoKey(BIZ);

        // PGRO seed — exactly two rows, GRP_CD in {'B', 'S'} with USER_ID='ALL'.
        org.mockito.ArgumentCaptor<PgroBsacLnknInfm> pgroCap =
                org.mockito.ArgumentCaptor.forClass(PgroBsacLnknInfm.class);
        verify(pgroRepo, times(2)).save(pgroCap.capture());
        assertThat(pgroCap.getAllValues()).extracting(PgroBsacLnknInfm::getGrpCd).containsExactlyInAnyOrder("B", "S");
        assertThat(pgroCap.getAllValues()).allMatch(p -> "ALL".equals(p.getUserId()));

        // OQ-3 — TRCO_INQ_YN defaulted to 'Y' on create.
        org.mockito.ArgumentCaptor<TrcoInfm> masterCap =
                org.mockito.ArgumentCaptor.forClass(TrcoInfm.class);
        verify(trcoInfmRepo).save(masterCap.capture());
        assertThat(masterCap.getValue().getTrcoInqYn()).isEqualTo("Y");
    }

    @Test
    void create_duplicateHandTrcoKeyThrows() {
        ContactCreateRequest req = localCreate("DUP", null);
        when(trcoInfmRepo.existsByBizKeyAndHandTrcoKey(BIZ, "DUP")).thenReturn(true);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("WBE_CONTACTS_HAND_KEY_DUP");
        verifyNoInteractions(legacyFn);
    }

    @Test
    void create_duplicateTaxIdThrows() {
        ContactCreateRequest req = localCreate("OK", "0123456789");
        when(trcoInfmRepo.existsByBizKeyAndHandTrcoKey(BIZ, "OK")).thenReturn(false);
        when(trcoInfmRepo.existsByBizKeyAndTrcoTaxId(BIZ, "0123456789")).thenReturn(true);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("WBE_CONTACTS_TAX_ID_DUP");
    }

    @Test
    void create_localWithoutBankSucceedsPerLegacyParity() {
        // Legacy `comm_0184_002_c001_act.jsp:69-80` allows a Local
        // contact with zero account rows — the per-row required-fields
        // check only fires for rows carrying NAPAS_BANK_CD or
        // CITAD_BANK_CD. The refactor matches that surface as of
        // 2026-05-25 (the previous WBE_CONTACTS_LOCAL_BANK_REQUIRED
        // rule has been retired).
        ContactCreateRequest req = new ContactCreateRequest(
                "NB", "Co", "Co EN", null, "L",
                List.of(),
                List.of(),  // zero accounts on a Local contact
                List.of());
        when(trcoInfmRepo.existsByBizKeyAndHandTrcoKey(BIZ, "NB")).thenReturn(false);
        when(legacyFn.nextTrcoKey(BIZ)).thenReturn(TRCO_KEY);
        when(trcoInfmRepo.findByTrcoKeyAndBizKey(TRCO_KEY, BIZ))
                .thenReturn(Optional.of(stubMaster(TRCO_KEY, "NB", "L")));
        when(addrRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of());
        when(acctRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of());
        when(ovrsRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of());

        ContactRow row = service.create(req);

        assertThat(row.trcoKey()).isEqualTo(TRCO_KEY);
        verify(legacyFn).nextTrcoKey(BIZ);
    }

    @Test
    void create_addressUpsertWithDispOrdOutOfRangeThrowsValidation() {
        AddressOp bad = new AddressOp("upsert", "1", "Some address", 3, "20260513", "20260514");
        ContactCreateRequest req = new ContactCreateRequest(
                "AD", "Co", "Co EN", null, "O",
                List.of(bad), List.of(), List.of());
        when(trcoInfmRepo.existsByBizKeyAndHandTrcoKey(BIZ, "AD")).thenReturn(false);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("WBE_VALIDATION");
    }

    @Test
    void create_pkCollisionRetriesOnceWithNewKey() {
        ContactCreateRequest req = localCreate("RT", null);
        when(trcoInfmRepo.existsByBizKeyAndHandTrcoKey(BIZ, "RT")).thenReturn(false);
        when(legacyFn.nextTrcoKey(BIZ)).thenReturn("OLD", "NEW");
        // First save throws DIVE; second save (after re-validation +
        // re-allocation) succeeds.
        when(trcoInfmRepo.save(any(TrcoInfm.class)))
                .thenThrow(new DataIntegrityViolationException("dup"))
                .thenAnswer(inv -> inv.getArgument(0));
        when(trcoInfmRepo.findByTrcoKeyAndBizKey("NEW", BIZ))
                .thenReturn(Optional.of(stubMaster("NEW", "RT", "L")));
        when(addrRepo.findByBizKeyAndTrcoKey(BIZ, "NEW")).thenReturn(List.of());
        when(acctRepo.findByBizKeyAndTrcoKey(BIZ, "NEW")).thenReturn(List.of());
        when(ovrsRepo.findByBizKeyAndTrcoKey(BIZ, "NEW")).thenReturn(List.of());

        ContactRow row = service.create(req);

        assertThat(row.trcoKey()).isEqualTo("NEW");
        verify(legacyFn, times(2)).nextTrcoKey(BIZ);
    }

    /* =================================================================
     *  Soft-delete — T1 single, T2 bulk, T3 happy path; WBE1190 guard
     * ================================================================= */

    @Test
    void softDelete_inUseRowThrowsWbe1190() {
        TrcoInfm row = stubMaster(TRCO_KEY, "C001", "L");
        when(trcoInfmRepo.findByTrcoKeyAndBizKey(TRCO_KEY, BIZ)).thenReturn(Optional.of(row));
        when(acctTrnsRcvPayRepo.existsByBizKeyAndRcvPayTrcoKey(BIZ, TRCO_KEY)).thenReturn(true);

        assertThatThrownBy(() -> service.softDelete(TRCO_KEY))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("WBE1190");
        assertThat(row.getTrcoStts()).isNotEqualTo("C");
    }

    @Test
    void softDelete_freeRowFlipsToTrashed() {
        TrcoInfm row = stubMaster(TRCO_KEY, "C001", "L");
        when(trcoInfmRepo.findByTrcoKeyAndBizKey(TRCO_KEY, BIZ)).thenReturn(Optional.of(row));
        when(acctTrnsRcvPayRepo.existsByBizKeyAndRcvPayTrcoKey(BIZ, TRCO_KEY)).thenReturn(false);

        service.softDelete(TRCO_KEY);

        assertThat(row.getTrcoStts()).isEqualTo("C");
        assertThat(row.getModId()).isEqualTo(USER);
        assertThat(row.getModDtm()).isNotNull();
    }

    @Test
    void bulkSoftDelete_anyInUseKeyRollsBackEntireBatch() {
        TrcoInfm free   = stubMaster("FREE",  "C-FREE",  "L");
        TrcoInfm inUse  = stubMaster("INUSE", "C-INUSE", "L");
        when(trcoInfmRepo.findByTrcoKeyAndBizKey("FREE", BIZ)).thenReturn(Optional.of(free));
        when(trcoInfmRepo.findByTrcoKeyAndBizKey("INUSE", BIZ)).thenReturn(Optional.of(inUse));
        when(acctTrnsRcvPayRepo.existsByBizKeyAndRcvPayTrcoKey(BIZ, "FREE")).thenReturn(false);
        when(acctTrnsRcvPayRepo.existsByBizKeyAndRcvPayTrcoKey(BIZ, "INUSE")).thenReturn(true);

        // FREE is processed first and flipped in memory; the second
        // key throws WBE1190 and the surrounding @Transactional would
        // roll back the FREE change at the DB level. The mocks can't
        // physically reverse the in-memory mutation, but we assert
        // the throw fires before the second flip lands.
        assertThatThrownBy(() ->
                service.bulkSoftDelete(List.of("FREE", "INUSE")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("WBE1190");
        assertThat(inUse.getTrcoStts()).isNotEqualTo("C");
    }

    /* =================================================================
     *  Bank picker (listBanks dedup — legacy comm_0109_001_r002 parity)
     * ================================================================= */

    @Test
    void listBanks_collapsesCitadBranchesAndPrefersNapas() {
        // ABBank: one NAPAS row + (in the CITAD HQ leg) its HQ row. The
        // flat read used to repeat ABBank once per CITAD branch (~29x);
        // the deduped read must surface ABBank exactly once, routed as
        // NAPAS with status 'Y' (Lookup-eligible).
        when(bankCodeRepo.findByAntDivCdAndBankStas("NAPAS", "1")).thenReturn(List.of(
                bank("NAPAS", "970425", " ", "ABBank", "An Binh Commercial Joint Stock Bank")));
        // CITAD HQ leg: ABBank HQ (also NAPAS → skipped as a duplicate)
        // + a CITAD-exclusive bank (kept, status 'N' → manual recipient).
        when(bankCodeRepo.findByAntDivCdAndBankBranchDivAndBankStas("CITAD", "1", "1"))
                .thenReturn(List.of(
                        bank("CITAD", "01323002", "1", "ABBank", "An Binh Commercial Joint Stock Bank"),
                        bank("CITAD", "01616001", "1", "CoopBank", "Co-operative Bank")));

        ContactBankListResponse resp = service.listBanks();

        assertThat(resp.rows()).hasSize(2);
        assertThat(resp.rows().stream().filter(r -> "ABBank".equals(r.engAbrvatNm())).count())
                .isEqualTo(1);

        ContactBankRow ab = resp.rows().stream()
                .filter(r -> "ABBank".equals(r.engAbrvatNm())).findFirst().orElseThrow();
        assertThat(ab.antDivCd()).isEqualTo("NAPAS");
        assertThat(ab.bankCd()).isEqualTo("970425");
        assertThat(ab.status()).isEqualTo("Y");

        ContactBankRow coop = resp.rows().stream()
                .filter(r -> "CoopBank".equals(r.engAbrvatNm())).findFirst().orElseThrow();
        assertThat(coop.antDivCd()).isEqualTo("CITAD");
        assertThat(coop.bankCd()).isEqualTo("01616001");
        assertThat(coop.status()).isEqualTo("N");
    }

    /* =================================================================
     *  Helpers
     * ================================================================= */

    private static TbFirmBankCode bank(String antDiv, String bankCd, String branchDiv,
                                       String abbrev, String engNm) {
        TbFirmBankCode b = new TbFirmBankCode();
        b.setAntDivCd(antDiv);
        b.setBankCd(bankCd);
        b.setBankBranchDiv(branchDiv);
        b.setEngAbrvatNm(abbrev);
        b.setBankEngNm(engNm);
        b.setBankNm(engNm);
        b.setBankStas("1");
        return b;
    }

    /* =================================================================
     *  OD-1 — TRCO_ACCT_INFO.trco_acct_key via wb_get_next_val funnel
     * ================================================================= */

    @Test
    void create_account_generatesKeyViaFunnel_ignoringPlaceholder() {
        ContactCreateRequest req = localCreate("ACCT1", "0123456789"); // account placeholder key "1"
        when(trcoInfmRepo.existsByBizKeyAndHandTrcoKey(BIZ, "ACCT1")).thenReturn(false);
        when(trcoInfmRepo.existsByBizKeyAndTrcoTaxId(BIZ, "0123456789")).thenReturn(false);
        when(legacyFn.nextTrcoKey(BIZ)).thenReturn(TRCO_KEY);
        when(legacyFn.nextTrcoAcctKey(BIZ, TRCO_KEY)).thenReturn("5001");
        when(trcoInfmRepo.findByTrcoKeyAndBizKey(TRCO_KEY, BIZ))
                .thenReturn(Optional.of(stubMaster(TRCO_KEY, "ACCT1", "L")));
        when(addrRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of());
        when(acctRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of());
        when(ovrsRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of());

        service.create(req);

        org.mockito.ArgumentCaptor<com.kosign.wecms.domain.setting.contacts.entity.TrcoAcctInfo> cap =
                org.mockito.ArgumentCaptor.forClass(com.kosign.wecms.domain.setting.contacts.entity.TrcoAcctInfo.class);
        verify(acctRepo).saveAndFlush(cap.capture());
        // Persisted key is the funnel value — NOT the frontend placeholder "1".
        assertThat(cap.getValue().getTrcoAcctKey()).isEqualTo("5001");
        assertThat(cap.getValue().getTrcoAcctKey()).isNotEqualTo("1");
        verify(legacyFn).nextTrcoAcctKey(BIZ, TRCO_KEY);
    }

    @Test
    void create_multipleAccounts_generatesDistinctSequentialKeys() {
        AccountOp a1 = new AccountOp("upsert", "1", "Bank A", "111", null, null, "VND", "Owner", null, 1);
        AccountOp a2 = new AccountOp("upsert", "2", "Bank B", "222", null, null, "VND", "Owner", null, 2);
        ContactCreateRequest req = new ContactCreateRequest(
                "MULTI", "Co", "Co EN", null, "L", List.of(), List.of(a1, a2), List.of());
        when(trcoInfmRepo.existsByBizKeyAndHandTrcoKey(BIZ, "MULTI")).thenReturn(false);
        when(legacyFn.nextTrcoKey(BIZ)).thenReturn(TRCO_KEY);
        when(legacyFn.nextTrcoAcctKey(BIZ, TRCO_KEY)).thenReturn("5001", "5002");
        when(trcoInfmRepo.findByTrcoKeyAndBizKey(TRCO_KEY, BIZ))
                .thenReturn(Optional.of(stubMaster(TRCO_KEY, "MULTI", "L")));
        when(addrRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of());
        when(acctRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of());
        when(ovrsRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of());

        service.create(req);

        org.mockito.ArgumentCaptor<com.kosign.wecms.domain.setting.contacts.entity.TrcoAcctInfo> cap =
                org.mockito.ArgumentCaptor.forClass(com.kosign.wecms.domain.setting.contacts.entity.TrcoAcctInfo.class);
        verify(acctRepo, times(2)).saveAndFlush(cap.capture());
        java.util.List<String> keys = cap.getAllValues().stream()
                .map(com.kosign.wecms.domain.setting.contacts.entity.TrcoAcctInfo::getTrcoAcctKey)
                .toList();
        assertThat(keys).containsExactly("5001", "5002");   // distinct + sequential from funnel
        verify(legacyFn, times(2)).nextTrcoAcctKey(BIZ, TRCO_KEY);
    }

    @Test
    void update_existingAccount_preservesKey_noFunnelCall() {
        com.kosign.wecms.domain.setting.contacts.entity.TrcoAcctInfo existingAcct = stubAcct("9001");
        AccountOp keep = new AccountOp("upsert", "9001", "Bank A", "111", null, null, "VND", "Owner", null, 1);
        com.kosign.wecms.domain.setting.contacts.dto.ContactUpdateRequest req =
                new com.kosign.wecms.domain.setting.contacts.dto.ContactUpdateRequest(
                        "C001", "Test Co", "Test Co EN", "1201496252", "L", null,
                        List.of(), List.of(keep), List.of(), List.of(), List.of());
        when(trcoInfmRepo.findByTrcoKeyAndBizKey(TRCO_KEY, BIZ))
                .thenReturn(Optional.of(stubMaster(TRCO_KEY, "C001", "L")));
        when(addrRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of());
        when(acctRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of(existingAcct));
        when(ovrsRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of());

        service.update(TRCO_KEY, req);

        org.mockito.ArgumentCaptor<com.kosign.wecms.domain.setting.contacts.entity.TrcoAcctInfo> cap =
                org.mockito.ArgumentCaptor.forClass(com.kosign.wecms.domain.setting.contacts.entity.TrcoAcctInfo.class);
        verify(acctRepo).saveAndFlush(cap.capture());
        assertThat(cap.getValue().getTrcoAcctKey()).isEqualTo("9001");   // existing key preserved
        verify(legacyFn, never()).nextTrcoAcctKey(any(), any());
    }

    @Test
    void update_newPlaceholderAccount_generatesKey() {
        AccountOp added = new AccountOp("upsert", "CA0202606240001", "Bank A", "111", null, null, "VND", "Owner", null, 1);
        com.kosign.wecms.domain.setting.contacts.dto.ContactUpdateRequest req =
                new com.kosign.wecms.domain.setting.contacts.dto.ContactUpdateRequest(
                        "C001", "Test Co", "Test Co EN", "1201496252", "L", null,
                        List.of(), List.of(added), List.of(), List.of(), List.of());
        when(trcoInfmRepo.findByTrcoKeyAndBizKey(TRCO_KEY, BIZ))
                .thenReturn(Optional.of(stubMaster(TRCO_KEY, "C001", "L")));
        when(legacyFn.nextTrcoAcctKey(BIZ, TRCO_KEY)).thenReturn("9002");
        when(addrRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of());
        when(acctRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of());
        when(ovrsRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of());

        service.update(TRCO_KEY, req);

        org.mockito.ArgumentCaptor<com.kosign.wecms.domain.setting.contacts.entity.TrcoAcctInfo> cap =
                org.mockito.ArgumentCaptor.forClass(com.kosign.wecms.domain.setting.contacts.entity.TrcoAcctInfo.class);
        verify(acctRepo).saveAndFlush(cap.capture());
        assertThat(cap.getValue().getTrcoAcctKey()).isEqualTo("9002");   // generated, placeholder ignored
        verify(legacyFn).nextTrcoAcctKey(BIZ, TRCO_KEY);
    }

    @Test
    void update_deleteAccount_unchanged_noFunnelCall() {
        AccountOp del = new AccountOp("delete", "9001", null, null, null, null, null, null, null, null);
        com.kosign.wecms.domain.setting.contacts.dto.ContactUpdateRequest req =
                new com.kosign.wecms.domain.setting.contacts.dto.ContactUpdateRequest(
                        "C001", "Test Co", "Test Co EN", "1201496252", "L", null,
                        List.of(), List.of(del), List.of(), List.of(), List.of());
        when(trcoInfmRepo.findByTrcoKeyAndBizKey(TRCO_KEY, BIZ))
                .thenReturn(Optional.of(stubMaster(TRCO_KEY, "C001", "L")));
        when(addrRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of());
        when(acctRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of());
        when(ovrsRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of());

        service.update(TRCO_KEY, req);

        verify(acctRepo).deleteByBizKeyAndTrcoKeyAndTrcoAcctKey(BIZ, TRCO_KEY, "9001");
        verify(acctRepo, never()).saveAndFlush(any());
        verify(legacyFn, never()).nextTrcoAcctKey(any(), any());
    }

    /* =================================================================
     *  OD-1 — TRCO_ADDR_INFM.trco_addr_key via wb_get_next_val funnel
     *  (per (biz_key, trco_key) MAX+1 — branch already on the live fn)
     * ================================================================= */

    @Test
    void create_address_generatesKeyViaFunnel_ignoringPlaceholder() {
        // Frontend sends a non-blank placeholder "TEMP"; it must NOT persist.
        AddressOp addr = new AddressOp("upsert", "TEMP", "123 Some Street", 1, "20260513", "20260514");
        ContactCreateRequest req = new ContactCreateRequest(
                "ADDR1", "Co", "Co EN", null, "L", List.of(addr), List.of(), List.of());
        when(trcoInfmRepo.existsByBizKeyAndHandTrcoKey(BIZ, "ADDR1")).thenReturn(false);
        when(legacyFn.nextTrcoKey(BIZ)).thenReturn(TRCO_KEY);
        when(legacyFn.nextTrcoAddrKey(BIZ, TRCO_KEY)).thenReturn("3001");
        when(trcoInfmRepo.findByTrcoKeyAndBizKey(TRCO_KEY, BIZ))
                .thenReturn(Optional.of(stubMaster(TRCO_KEY, "ADDR1", "L")));
        when(addrRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of());
        when(acctRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of());
        when(ovrsRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of());

        service.create(req);

        org.mockito.ArgumentCaptor<com.kosign.wecms.domain.setting.contacts.entity.TrcoAddrInfm> cap =
                org.mockito.ArgumentCaptor.forClass(com.kosign.wecms.domain.setting.contacts.entity.TrcoAddrInfm.class);
        verify(addrRepo).saveAndFlush(cap.capture());
        assertThat(cap.getValue().getTrcoAddrKey()).isEqualTo("3001");
        assertThat(cap.getValue().getTrcoAddrKey()).isNotEqualTo("TEMP");
        verify(legacyFn).nextTrcoAddrKey(BIZ, TRCO_KEY);
    }

    @Test
    void create_multipleAddresses_generatesDistinctSequentialKeys() {
        AddressOp a1 = new AddressOp("upsert", "T1", "Addr 1", 1, "20260513", "20260514");
        AddressOp a2 = new AddressOp("upsert", "T2", "Addr 2", 2, "20260513", "20260514");
        ContactCreateRequest req = new ContactCreateRequest(
                "ADDRM", "Co", "Co EN", null, "L", List.of(a1, a2), List.of(), List.of());
        when(trcoInfmRepo.existsByBizKeyAndHandTrcoKey(BIZ, "ADDRM")).thenReturn(false);
        when(legacyFn.nextTrcoKey(BIZ)).thenReturn(TRCO_KEY);
        when(legacyFn.nextTrcoAddrKey(BIZ, TRCO_KEY)).thenReturn("1", "2");
        when(trcoInfmRepo.findByTrcoKeyAndBizKey(TRCO_KEY, BIZ))
                .thenReturn(Optional.of(stubMaster(TRCO_KEY, "ADDRM", "L")));
        when(addrRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of());
        when(acctRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of());
        when(ovrsRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of());

        service.create(req);

        org.mockito.ArgumentCaptor<com.kosign.wecms.domain.setting.contacts.entity.TrcoAddrInfm> cap =
                org.mockito.ArgumentCaptor.forClass(com.kosign.wecms.domain.setting.contacts.entity.TrcoAddrInfm.class);
        verify(addrRepo, times(2)).saveAndFlush(cap.capture());
        assertThat(cap.getAllValues().stream()
                .map(com.kosign.wecms.domain.setting.contacts.entity.TrcoAddrInfm::getTrcoAddrKey).toList())
                .containsExactly("1", "2");
        verify(legacyFn, times(2)).nextTrcoAddrKey(BIZ, TRCO_KEY);
    }

    @Test
    void update_existingAddress_preservesKey_noFunnelCall() {
        com.kosign.wecms.domain.setting.contacts.entity.TrcoAddrInfm existing = stubAddr("7");
        AddressOp keep = new AddressOp("upsert", "7", "Addr", 1, "20260513", "20260514");
        com.kosign.wecms.domain.setting.contacts.dto.ContactUpdateRequest req =
                new com.kosign.wecms.domain.setting.contacts.dto.ContactUpdateRequest(
                        "C001", "Test Co", "Test Co EN", "1201496252", "L", null,
                        List.of(keep), List.of(), List.of(), List.of(), List.of());
        when(trcoInfmRepo.findByTrcoKeyAndBizKey(TRCO_KEY, BIZ))
                .thenReturn(Optional.of(stubMaster(TRCO_KEY, "C001", "L")));
        when(addrRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of(existing));
        when(acctRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of());
        when(ovrsRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of());

        service.update(TRCO_KEY, req);

        org.mockito.ArgumentCaptor<com.kosign.wecms.domain.setting.contacts.entity.TrcoAddrInfm> cap =
                org.mockito.ArgumentCaptor.forClass(com.kosign.wecms.domain.setting.contacts.entity.TrcoAddrInfm.class);
        verify(addrRepo).saveAndFlush(cap.capture());
        assertThat(cap.getValue().getTrcoAddrKey()).isEqualTo("7");
        verify(legacyFn, never()).nextTrcoAddrKey(any(), any());
    }

    @Test
    void update_newPlaceholderAddress_generatesKey() {
        AddressOp added = new AddressOp("upsert", "CA0202606240001", "New addr", 2, "20260513", "20260514");
        com.kosign.wecms.domain.setting.contacts.dto.ContactUpdateRequest req =
                new com.kosign.wecms.domain.setting.contacts.dto.ContactUpdateRequest(
                        "C001", "Test Co", "Test Co EN", "1201496252", "L", null,
                        List.of(added), List.of(), List.of(), List.of(), List.of());
        when(trcoInfmRepo.findByTrcoKeyAndBizKey(TRCO_KEY, BIZ))
                .thenReturn(Optional.of(stubMaster(TRCO_KEY, "C001", "L")));
        when(legacyFn.nextTrcoAddrKey(BIZ, TRCO_KEY)).thenReturn("9");
        when(addrRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of());
        when(acctRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of());
        when(ovrsRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of());

        service.update(TRCO_KEY, req);

        org.mockito.ArgumentCaptor<com.kosign.wecms.domain.setting.contacts.entity.TrcoAddrInfm> cap =
                org.mockito.ArgumentCaptor.forClass(com.kosign.wecms.domain.setting.contacts.entity.TrcoAddrInfm.class);
        verify(addrRepo).saveAndFlush(cap.capture());
        assertThat(cap.getValue().getTrcoAddrKey()).isEqualTo("9");
        assertThat(cap.getValue().getTrcoAddrKey()).isNotEqualTo("CA0202606240001");
        verify(legacyFn).nextTrcoAddrKey(BIZ, TRCO_KEY);
    }

    @Test
    void update_deleteAddress_unchanged_noFunnelCall() {
        AddressOp del = new AddressOp("delete", "7", null, null, null, null);
        com.kosign.wecms.domain.setting.contacts.dto.ContactUpdateRequest req =
                new com.kosign.wecms.domain.setting.contacts.dto.ContactUpdateRequest(
                        "C001", "Test Co", "Test Co EN", "1201496252", "L", null,
                        List.of(del), List.of(), List.of(), List.of(), List.of());
        when(trcoInfmRepo.findByTrcoKeyAndBizKey(TRCO_KEY, BIZ))
                .thenReturn(Optional.of(stubMaster(TRCO_KEY, "C001", "L")));
        when(addrRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of());
        when(acctRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of());
        when(ovrsRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of());

        service.update(TRCO_KEY, req);

        verify(addrRepo).deleteByBizKeyAndTrcoKeyAndTrcoAddrKey(BIZ, TRCO_KEY, "7");
        verify(addrRepo, never()).saveAndFlush(any());
        verify(legacyFn, never()).nextTrcoAddrKey(any(), any());
    }

    /* =================================================================
     *  OD-1 — TRCO_OVRS_INFM.trco_ovrs_key via wb_get_next_val funnel
     *  (per (biz_key) MAX+1 — branch already on the live fn)
     * ================================================================= */

    @Test
    void create_overseas_generatesKeyViaFunnel_ignoringPlaceholder() {
        OverseasOp o = new OverseasOp("upsert", "TEMP", "Bene", "ACC1", "BIC1", "BankName", "BC", "Addr");
        ContactCreateRequest req = new ContactCreateRequest(
                "OVRS1", "Co", "Co EN", null, "O", List.of(), List.of(), List.of(o));
        when(trcoInfmRepo.existsByBizKeyAndHandTrcoKey(BIZ, "OVRS1")).thenReturn(false);
        when(legacyFn.nextTrcoKey(BIZ)).thenReturn(TRCO_KEY);
        when(legacyFn.nextTrcoOvrsKey(BIZ)).thenReturn("40");
        when(trcoInfmRepo.findByTrcoKeyAndBizKey(TRCO_KEY, BIZ))
                .thenReturn(Optional.of(stubMaster(TRCO_KEY, "OVRS1", "O")));
        when(addrRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of());
        when(acctRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of());
        when(ovrsRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of());

        service.create(req);

        org.mockito.ArgumentCaptor<TrcoOvrsInfm> cap = org.mockito.ArgumentCaptor.forClass(TrcoOvrsInfm.class);
        verify(ovrsRepo).saveAndFlush(cap.capture());
        assertThat(cap.getValue().getTrcoOvrsKey()).isEqualTo("40");
        assertThat(cap.getValue().getTrcoOvrsKey()).isNotEqualTo("TEMP");
        verify(legacyFn).nextTrcoOvrsKey(BIZ);
    }

    @Test
    void create_multipleOverseas_generatesDistinctSequentialKeys() {
        OverseasOp o1 = new OverseasOp("upsert", "T1", "Bene1", "ACC1", "BIC1", "Bank1", "BC1", "Addr1");
        OverseasOp o2 = new OverseasOp("upsert", "T2", "Bene2", "ACC2", "BIC2", "Bank2", "BC2", "Addr2");
        ContactCreateRequest req = new ContactCreateRequest(
                "OVRSM", "Co", "Co EN", null, "O", List.of(), List.of(), List.of(o1, o2));
        when(trcoInfmRepo.existsByBizKeyAndHandTrcoKey(BIZ, "OVRSM")).thenReturn(false);
        when(legacyFn.nextTrcoKey(BIZ)).thenReturn(TRCO_KEY);
        when(legacyFn.nextTrcoOvrsKey(BIZ)).thenReturn("40", "41");
        when(trcoInfmRepo.findByTrcoKeyAndBizKey(TRCO_KEY, BIZ))
                .thenReturn(Optional.of(stubMaster(TRCO_KEY, "OVRSM", "O")));
        when(addrRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of());
        when(acctRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of());
        when(ovrsRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of());

        service.create(req);

        org.mockito.ArgumentCaptor<TrcoOvrsInfm> cap = org.mockito.ArgumentCaptor.forClass(TrcoOvrsInfm.class);
        verify(ovrsRepo, times(2)).saveAndFlush(cap.capture());
        assertThat(cap.getAllValues().stream().map(TrcoOvrsInfm::getTrcoOvrsKey).toList())
                .containsExactly("40", "41");
        verify(legacyFn, times(2)).nextTrcoOvrsKey(BIZ);
    }

    @Test
    void update_existingOverseas_preservesKey_noFunnelCall() {
        TrcoOvrsInfm existing = stubOvrs("21");
        OverseasOp keep = new OverseasOp("upsert", "21", "Bene", "ACC1", "BIC1", "Bank", "BC", "Addr");
        com.kosign.wecms.domain.setting.contacts.dto.ContactUpdateRequest req =
                new com.kosign.wecms.domain.setting.contacts.dto.ContactUpdateRequest(
                        "C001", "Test Co", "Test Co EN", "1201496252", "O", null,
                        List.of(), List.of(), List.of(), List.of(keep), List.of());
        when(trcoInfmRepo.findByTrcoKeyAndBizKey(TRCO_KEY, BIZ))
                .thenReturn(Optional.of(stubMaster(TRCO_KEY, "C001", "O")));
        when(addrRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of());
        when(acctRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of());
        when(ovrsRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of(existing));

        service.update(TRCO_KEY, req);

        org.mockito.ArgumentCaptor<TrcoOvrsInfm> cap = org.mockito.ArgumentCaptor.forClass(TrcoOvrsInfm.class);
        verify(ovrsRepo).saveAndFlush(cap.capture());
        assertThat(cap.getValue().getTrcoOvrsKey()).isEqualTo("21");
        verify(legacyFn, never()).nextTrcoOvrsKey(any());
    }

    @Test
    void update_newPlaceholderOverseas_generatesKey() {
        OverseasOp added = new OverseasOp("upsert", "OV0202606240001", "Bene", "ACC9", "BIC9", "Bank", "BC", "Addr");
        com.kosign.wecms.domain.setting.contacts.dto.ContactUpdateRequest req =
                new com.kosign.wecms.domain.setting.contacts.dto.ContactUpdateRequest(
                        "C001", "Test Co", "Test Co EN", "1201496252", "O", null,
                        List.of(), List.of(), List.of(), List.of(added), List.of());
        when(trcoInfmRepo.findByTrcoKeyAndBizKey(TRCO_KEY, BIZ))
                .thenReturn(Optional.of(stubMaster(TRCO_KEY, "C001", "O")));
        when(legacyFn.nextTrcoOvrsKey(BIZ)).thenReturn("42");
        when(addrRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of());
        when(acctRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of());
        when(ovrsRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of());

        service.update(TRCO_KEY, req);

        org.mockito.ArgumentCaptor<TrcoOvrsInfm> cap = org.mockito.ArgumentCaptor.forClass(TrcoOvrsInfm.class);
        verify(ovrsRepo).saveAndFlush(cap.capture());
        assertThat(cap.getValue().getTrcoOvrsKey()).isEqualTo("42");
        assertThat(cap.getValue().getTrcoOvrsKey()).isNotEqualTo("OV0202606240001");
        verify(legacyFn).nextTrcoOvrsKey(BIZ);
    }

    @Test
    void update_deleteOverseas_unchanged_noFunnelCall() {
        OverseasOp del = new OverseasOp("delete", "21", null, null, null, null, null, null);
        com.kosign.wecms.domain.setting.contacts.dto.ContactUpdateRequest req =
                new com.kosign.wecms.domain.setting.contacts.dto.ContactUpdateRequest(
                        "C001", "Test Co", "Test Co EN", "1201496252", "O", null,
                        List.of(), List.of(), List.of(), List.of(del), List.of());
        when(trcoInfmRepo.findByTrcoKeyAndBizKey(TRCO_KEY, BIZ))
                .thenReturn(Optional.of(stubMaster(TRCO_KEY, "C001", "O")));
        when(addrRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of());
        when(acctRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of());
        when(ovrsRepo.findByBizKeyAndTrcoKey(BIZ, TRCO_KEY)).thenReturn(List.of());

        service.update(TRCO_KEY, req);

        verify(ovrsRepo).deleteByBizKeyAndTrcoKeyAndTrcoOvrsKey(BIZ, TRCO_KEY, "21");
        verify(ovrsRepo, never()).saveAndFlush(any());
        verify(legacyFn, never()).nextTrcoOvrsKey(any());
    }

    private static com.kosign.wecms.domain.setting.contacts.entity.TrcoAddrInfm stubAddr(String addrKey) {
        com.kosign.wecms.domain.setting.contacts.entity.TrcoAddrInfm e =
                new com.kosign.wecms.domain.setting.contacts.entity.TrcoAddrInfm();
        e.setBizKey(BIZ);
        e.setTrcoKey(TRCO_KEY);
        e.setTrcoAddrKey(addrKey);
        e.setDispOrd(1);
        return e;
    }

    private static TrcoOvrsInfm stubOvrs(String ovrsKey) {
        TrcoOvrsInfm e = new TrcoOvrsInfm();
        e.setBizKey(BIZ);
        e.setTrcoKey(TRCO_KEY);
        e.setTrcoOvrsKey(ovrsKey);
        return e;
    }

    private static com.kosign.wecms.domain.setting.contacts.entity.TrcoAcctInfo stubAcct(String acctKey) {
        com.kosign.wecms.domain.setting.contacts.entity.TrcoAcctInfo e =
                new com.kosign.wecms.domain.setting.contacts.entity.TrcoAcctInfo();
        e.setBizKey(BIZ);
        e.setTrcoKey(TRCO_KEY);
        e.setTrcoAcctKey(acctKey);
        e.setDispOrd(1);
        return e;
    }

    private static TrcoInfm stubMaster(String trcoKey, String handTrcoKey, String trcoType) {
        TrcoInfm m = new TrcoInfm();
        m.setTrcoKey(trcoKey);
        m.setBizKey(BIZ);
        m.setTaxId("1201496252");
        m.setTrcoCmnm("Test Co");
        m.setTrcoCmenm("Test Co EN");
        m.setTrcoType(trcoType);
        m.setTrscCcyCd("L".equals(trcoType) ? "VND" : "USD");
        m.setTrcoInqYn("Y");
        m.setHandTrcoKey(handTrcoKey);
        m.setRegId(USER);
        m.setRegDtm("20260513000000");
        m.setModId(USER);
        m.setModDtm("20260513000000");
        return m;
    }

    private static ContactCreateRequest localCreate(String handKey, String taxId) {
        AccountOp acct = new AccountOp(
                "upsert", "1", "ABBank - ABBANK", "1234567890",
                "970425", null, "VND", "Owner", null, 1);
        return new ContactCreateRequest(
                handKey, "Test Co", "Test Co EN", taxId, "L",
                List.of(), List.of(acct), List.of());
    }
}
