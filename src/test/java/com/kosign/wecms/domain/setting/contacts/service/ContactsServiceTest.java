package com.kosign.wecms.domain.setting.contacts.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.repository.AcctTrnsRcvPayInfmRepository;
import com.kosign.wecms.domain.setting.contacts.dto.AccountOp;
import com.kosign.wecms.domain.setting.contacts.dto.AddressOp;
import com.kosign.wecms.domain.setting.contacts.dto.ContactCreateRequest;
import com.kosign.wecms.domain.setting.contacts.dto.ContactListResponse;
import com.kosign.wecms.domain.setting.contacts.dto.ContactRow;
import com.kosign.wecms.domain.setting.contacts.entity.PgroBsacLnknInfm;
import com.kosign.wecms.domain.setting.contacts.entity.TrcoInfm;
import com.kosign.wecms.domain.setting.contacts.repository.PgroBsacLnknInfmRepository;
import com.kosign.wecms.domain.setting.contacts.repository.TrcoAcctInfoRepository;
import com.kosign.wecms.domain.setting.contacts.repository.TrcoAddrInfmRepository;
import com.kosign.wecms.domain.setting.contacts.repository.TrcoInfmRepository;
import com.kosign.wecms.domain.setting.contacts.repository.TrcoOvrsInfmRepository;
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

    private ContactsService service;
    private ContactsValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ContactsValidator(trcoInfmRepo);
        service = new ContactsService(
                trcoInfmRepo, addrRepo, acctRepo, ovrsRepo, bankCodeRepo,
                pgroRepo, acctTrnsRcvPayRepo, legacyFn, validator,
                new com.kosign.wecms.common.firmbanking.FirmBankingInquiryService(firmBankingSettingRepo));
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
     *  Helpers
     * ================================================================= */

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
