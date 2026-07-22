package com.kosign.wecms.domain.setting.contacts.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.common.web.SortDirection;
import com.kosign.wecms.domain.banking.repository.AcctTrnsRcvPayInfmRepository;
import com.kosign.wecms.domain.setting.contacts.dto.AccountDeleteKey;
import com.kosign.wecms.domain.setting.contacts.dto.AccountOp;
import com.kosign.wecms.domain.setting.contacts.dto.AddressOp;
import com.kosign.wecms.domain.setting.contacts.dto.ContactAccountRow;
import com.kosign.wecms.domain.setting.contacts.dto.ContactAddressRow;
import com.kosign.wecms.domain.setting.contacts.dto.ContactCreateRequest;
import com.kosign.wecms.domain.setting.contacts.dto.ContactListResponse;
import com.kosign.wecms.domain.setting.contacts.dto.ContactOverseasRow;
import com.kosign.wecms.domain.setting.contacts.dto.ContactRow;
import com.kosign.wecms.domain.setting.contacts.dto.ContactUpdateRequest;
import com.kosign.wecms.domain.setting.contacts.dto.OverseasDeleteKey;
import com.kosign.wecms.domain.setting.contacts.dto.OverseasOp;
import com.kosign.wecms.domain.setting.contacts.entity.PgroBsacLnknInfm;
import com.kosign.wecms.domain.setting.contacts.entity.TrcoAcctInfo;
import com.kosign.wecms.domain.setting.contacts.entity.TrcoAddrInfm;
import com.kosign.wecms.domain.setting.contacts.entity.TrcoInfm;
import com.kosign.wecms.domain.setting.contacts.entity.TrcoOvrsInfm;
import com.kosign.wecms.domain.setting.contacts.repository.PgroBsacLnknInfmRepository;
import com.kosign.wecms.domain.setting.contacts.repository.TrcoAcctInfoRepository;
import com.kosign.wecms.domain.setting.contacts.repository.TrcoAddrInfmRepository;
import com.kosign.wecms.domain.setting.contacts.repository.TrcoInfmRepository;
import com.kosign.wecms.domain.setting.contacts.dto.ContactBankListResponse;
import com.kosign.wecms.domain.setting.contacts.dto.SwiftBicListResponse;
import com.kosign.wecms.domain.setting.contacts.dto.SwiftBicRow;
import com.kosign.wecms.domain.setting.contacts.dto.ContactBankRow;
import com.kosign.wecms.domain.setting.contacts.dto.ContactLookupRecipientRequest;
import com.kosign.wecms.domain.setting.contacts.dto.ContactLookupRecipientResponse;
import com.kosign.wecms.domain.setting.contacts.repository.SwiftBicMstRepository;
import com.kosign.wecms.domain.setting.contacts.repository.TrcoOvrsInfmRepository;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmBankCode;
import com.kosign.wecms.domain.setting.firmbankingsettings.repository.TbFirmBankCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Comparator;
import java.util.Set;

/**
 * Settings &gt; Contacts service. Mirrors the legacy
 * {@code comm_0184_001_r001} (list) + {@code comm_0184_002_c001}
 * (create) + {@code comm_0184_005_u001} (update) + {@code comm_0184_001_u001}
 * (bulk soft-delete) flows over the refactor's
 * {@link com.kosign.wecms.domain.setting.contacts.entity.TrcoInfm}
 * entity and four child entities.
 *
 * <p><b>Resolved open questions</b> consumed by this class
 * (full list in {@code docs/refactor-wecms/features/contacts.md}):
 * <ul>
 *   <li><b>OQ-1</b> No group filter &mdash; the {@code GRP_CD} branch
 *       from {@code comm_0184_001_r001_act.jsp:43-71} is NOT ported.</li>
 *   <li><b>OQ-2</b> {@code WBE1190} in-use guard applies to single
 *       delete, bulk delete, AND the edit-time {@code trco_stts &rarr; 'C'}
 *       transition. Refactor uses the legacy code verbatim.</li>
 *   <li><b>OQ-3</b> {@code TRCO_INQ_YN} stays mapped on the entity and
 *       defaults to {@code 'Y'} on create &mdash; service-internal,
 *       NEVER on the wire.</li>
 *   <li><b>OQ-4</b> {@code TRCO_KEY} allocation routes through
 *       {@link LegacyFunctionRepository#nextTrcoKey(String)} (V85 +
 *       {@code sq_trco_key} global sequence). One-shot retry on PK
 *       collision.</li>
 *   <li><b>OQ-5</b> No Trash UI &mdash; the list query hard-codes
 *       {@code coalesce(trco_stts,'') &lt;&gt; 'C'}; recovery is
 *       DBA-only.</li>
 *   <li><b>OQ-6</b> Audit slug is {@code 'contacts'} &mdash; the
 *       existing global audit aspect handles
 *       {@code AUDIT_LOGS_INFM.WSVC_ID}.</li>
 *   <li><b>OQ-7</b> Bank-name resolution via the legacy
 *       {@link LegacyFunctionRepository#resolveNapasOrCitadBankName(String, String)}
 *       funnel; {@code BankCdResolver} is NOT consulted.</li>
 *   <li><b>OQ-8</b> {@code DISP_ORD} is the address current/previous
 *       discriminator; never compare {@code TRCO_ADDR_KEY} to literal
 *       {@code "1"} / {@code "2"}.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ContactsService {

    public static final int MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_PAGE_SIZE = 15;

    /** Default ORDER BY when no user sort is supplied. Mirrors legacy
     *  {@code SortHelper.java} (`else if ("comm_0184_001_r001".equals(webSvcId))
     *  { sortItems.add("hand_trco_key:asc"); }`) — alphabetical by
     *  Contact Code so Local + Overseas naturally interleave on each
     *  page instead of clustering by recency. The {@code trcoKey DESC}
     *  tiebreaker keeps a deterministic order for rows that share a
     *  {@code hand_trco_key}. (The previous {@code mod_dtm DESC}
     *  default put a freshly-uploaded batch on top, hiding rows of the
     *  other location type behind pagination — see the user-reported
     *  issue 2026-05-25.) */
    static final Sort DEFAULT_SORT =
            Sort.by(Sort.Order.asc("handTrcoKey"), Sort.Order.desc("trcoKey"));

    /** {@code yyyyMMddHHmmss} formatter for the legacy
     *  {@code VARCHAR(14)} audit columns. UTC. */
    private static final DateTimeFormatter DTM14 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final TrcoInfmRepository trcoInfmRepo;
    private final TrcoAddrInfmRepository trcoAddrRepo;
    private final TrcoAcctInfoRepository trcoAcctRepo;
    private final TrcoOvrsInfmRepository trcoOvrsRepo;
    private final SwiftBicMstRepository swiftBicRepo;
    private final TbFirmBankCodeRepository bankCodeRepo;
    private final PgroBsacLnknInfmRepository pgroRepo;
    private final AcctTrnsRcvPayInfmRepository acctTrnsRcvPayRepo;
    private final LegacyFunctionRepository legacyFn;
    private final ContactsValidator validator;
    private final com.kosign.wecms.common.firmbanking.FirmBankingInquiryService firmBankingInquiry;

    /* =================================================================
     *  List + getOne
     * ================================================================= */

    @Transactional(readOnly = true)
    public ContactListResponse list(
            String search,
            String trcoType,
            int page,
            int size,
            String sortField,
            String sortDirection) {

        String bizKey = TenantContext.currentBizKey();
        String keywordPattern = buildKeywordPattern(search);
        String trcoTypeFilter = normaliseTrcoTypeFilter(trcoType);

        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int safePage = Math.max(0, page);
        Sort sort = buildSort(
                ContactSortField.parse(sortField).orElse(null),
                SortDirection.parse(sortDirection));
        Pageable pageable = PageRequest.of(safePage, safeSize, sort);

        Page<TrcoInfm> master = trcoInfmRepo.search(bizKey, trcoTypeFilter, keywordPattern, pageable);
        List<TrcoInfm> rows = master.getContent();
        List<String> keys = rows.stream().map(TrcoInfm::getTrcoKey).toList();

        // Batch-fetch the three child collections in one query each.
        Map<String, List<TrcoAddrInfm>> addrByKey  = groupByTrcoKey(
                trcoAddrRepo.findByBizKeyAndTrcoKeyIn(bizKey, keys), TrcoAddrInfm::getTrcoKey);
        Map<String, List<TrcoAcctInfo>> acctByKey  = groupByTrcoKey(
                trcoAcctRepo.findByBizKeyAndTrcoKeyIn(bizKey, keys), TrcoAcctInfo::getTrcoKey);
        Map<String, List<TrcoOvrsInfm>> ovrsByKey  = groupByTrcoKey(
                trcoOvrsRepo.findByBizKeyAndTrcoKeyIn(bizKey, keys), TrcoOvrsInfm::getTrcoKey);

        String langCd = currentLangForBankFn();
        Map<String, String> bankNameCache = resolveBankNamesForAccounts(acctByKey.values(), langCd);

        List<ContactRow> projected = new ArrayList<>(rows.size());
        for (TrcoInfm m : rows) {
            projected.add(projectRow(m,
                    addrByKey.getOrDefault(m.getTrcoKey(), List.of()),
                    acctByKey.getOrDefault(m.getTrcoKey(), List.of()),
                    ovrsByKey.getOrDefault(m.getTrcoKey(), List.of()),
                    bankNameCache));
        }
        return new ContactListResponse(
                projected,
                master.getTotalElements(),
                master.getNumber(),
                master.getSize(),
                master.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ContactRow getOne(String trcoKey) {
        String bizKey = TenantContext.currentBizKey();
        TrcoInfm master = trcoInfmRepo.findByTrcoKeyAndBizKey(trcoKey, bizKey)
                .filter(t -> t.getHandTrcoKey() != null
                        && (t.getTrcoStts() == null || !t.getTrcoStts().equals("C")))
                .orElseThrow(() -> new BusinessException("WBE_CONTACTS_NOT_FOUND"));

        List<TrcoAddrInfm> addrs = trcoAddrRepo.findByBizKeyAndTrcoKey(bizKey, trcoKey);
        List<TrcoAcctInfo> accts = trcoAcctRepo.findByBizKeyAndTrcoKey(bizKey, trcoKey);
        List<TrcoOvrsInfm> ovrs  = trcoOvrsRepo.findByBizKeyAndTrcoKey(bizKey, trcoKey);

        String langCd = currentLangForBankFn();
        Map<String, String> bankNameCache = resolveBankNamesForAccounts(List.of(accts), langCd);
        return projectRow(master, addrs, accts, ovrs, bankNameCache);
    }

    /**
     * Batch-fetch contacts by a list of user-supplied beneficiary codes
     * ({@code HAND_TRCO_KEY}). One DB round-trip per child collection;
     * codes that don't match any active contact in the tenant are silently
     * omitted from the result so callers can detect missing codes by
     * comparing the returned set against their input list.
     *
     * <p>Used by the overseas batch-payment Excel preview to resolve all
     * beneficiary rows without issuing N individual lookups.
     */
    @Transactional(readOnly = true)
    public List<ContactRow> getByBeneficiaryCodes(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) return List.of();
        String bizKey = TenantContext.currentBizKey();

        List<TrcoInfm> masters = trcoInfmRepo.findByBeneficiaryCodes(bizKey, codes);
        if (masters.isEmpty()) return List.of();

        List<String> trcoKeys = masters.stream().map(TrcoInfm::getTrcoKey).toList();

        Map<String, List<TrcoAddrInfm>> addrByKey = groupByTrcoKey(
                trcoAddrRepo.findByBizKeyAndTrcoKeyIn(bizKey, trcoKeys), TrcoAddrInfm::getTrcoKey);
        Map<String, List<TrcoAcctInfo>> acctByKey = groupByTrcoKey(
                trcoAcctRepo.findByBizKeyAndTrcoKeyIn(bizKey, trcoKeys), TrcoAcctInfo::getTrcoKey);
        Map<String, List<TrcoOvrsInfm>> ovrsByKey = groupByTrcoKey(
                trcoOvrsRepo.findByBizKeyAndTrcoKeyIn(bizKey, trcoKeys), TrcoOvrsInfm::getTrcoKey);

        String langCd = currentLangForBankFn();
        Map<String, String> bankNameCache = resolveBankNamesForAccounts(acctByKey.values(), langCd);

        return masters.stream()
                .map(m -> projectRow(m,
                        addrByKey.getOrDefault(m.getTrcoKey(), List.of()),
                        acctByKey.getOrDefault(m.getTrcoKey(), List.of()),
                        ovrsByKey.getOrDefault(m.getTrcoKey(), List.of()),
                        bankNameCache))
                .toList();
    }

    /**
     * Unpaged projection of the tenant's active contacts for Excel
     * export. Reuses the exact same query path as {@link #list}
     * (tenant scope via {@code BIZ_KEY}, active-only
     * {@code coalesce(trco_stts,'')<>'C'}, optional {@code TRCO_TYPE}
     * filter, optional keyword) — only pagination is dropped so the
     * spreadsheet contains every matching row regardless of the page
     * the user is viewing. Mirrors legacy
     * {@code comm_0184_001_r006_excel_view.jsp}, which streams every
     * active contact of the chosen {@code TRCO_TYPE}.
     *
     * <p>Consumed by {@code ContactsExportProvider} (Local / Overseas)
     * through the shared {@code ExcelExportService} pipeline.
     */
    @Transactional(readOnly = true)
    public List<ContactRow> listForExport(String search, String trcoType) {
        String bizKey = TenantContext.currentBizKey();
        String keywordPattern = buildKeywordPattern(search);
        String trcoTypeFilter = normaliseTrcoTypeFilter(trcoType);

        Page<TrcoInfm> master = trcoInfmRepo.search(
                bizKey, trcoTypeFilter, keywordPattern, Pageable.unpaged());
        List<TrcoInfm> rows = master.getContent();
        if (rows.isEmpty()) return List.of();
        List<String> keys = rows.stream().map(TrcoInfm::getTrcoKey).toList();

        Map<String, List<TrcoAddrInfm>> addrByKey = groupByTrcoKey(
                trcoAddrRepo.findByBizKeyAndTrcoKeyIn(bizKey, keys), TrcoAddrInfm::getTrcoKey);
        Map<String, List<TrcoAcctInfo>> acctByKey = groupByTrcoKey(
                trcoAcctRepo.findByBizKeyAndTrcoKeyIn(bizKey, keys), TrcoAcctInfo::getTrcoKey);
        Map<String, List<TrcoOvrsInfm>> ovrsByKey = groupByTrcoKey(
                trcoOvrsRepo.findByBizKeyAndTrcoKeyIn(bizKey, keys), TrcoOvrsInfm::getTrcoKey);

        String langCd = currentLangForBankFn();
        Map<String, String> bankNameCache = resolveBankNamesForAccounts(acctByKey.values(), langCd);

        List<ContactRow> out = new ArrayList<>(rows.size());
        for (TrcoInfm m : rows) {
            out.add(projectRow(m,
                    addrByKey.getOrDefault(m.getTrcoKey(), List.of()),
                    acctByKey.getOrDefault(m.getTrcoKey(), List.of()),
                    ovrsByKey.getOrDefault(m.getTrcoKey(), List.of()),
                    bankNameCache));
        }
        return out;
    }

    /* =================================================================
     *  Create
     * ================================================================= */

    @Transactional
    public ContactRow create(ContactCreateRequest req) {
        String bizKey = TenantContext.currentBizKey();
        validator.validateCreate(req, bizKey);

        // One-shot retry on PK collision (OQ-4 §7.3 from the design doc).
        try {
            return persistNewContact(req, bizKey);
        } catch (DataIntegrityViolationException ex) {
            return persistNewContact(req, bizKey);
        }
    }

    /**
     * Bulk-import counterpart of {@link #create}. Routes through
     * {@link ContactsValidator#validateImport} so the "Local contact
     * must carry at least one bank account" rule is skipped &mdash;
     * legacy {@code comm_0184_007} allows a Local row with no bank
     * info (bank fields are optional as a group). All other
     * tenant-aware checks (duplicate hand_trco_key / tax_id, payload
     * dedup, address disp_ord) still run.
     */
    @Transactional
    public ContactRow createForImport(ContactCreateRequest req) {
        String bizKey = TenantContext.currentBizKey();
        validator.validateImport(req, bizKey);

        try {
            return persistNewContact(req, bizKey);
        } catch (DataIntegrityViolationException ex) {
            return persistNewContact(req, bizKey);
        }
    }

    /**
     * Excel-upload preflight &mdash; runs the per-tenant DB-existence
     * checks (HAND_TRCO_KEY + TRCO_TAX_ID) over the parsed rows so the
     * preview table can flag duplicates BEFORE the user clicks Upload.
     * Mirrors the row-iteration in legacy
     * {@code comm_0184_007_r001_act.jsp:401-450 / 567-630} which calls
     * {@code getContact()} + {@code getTaxId()} per row at parse time.
     *
     * <p>Read-only. Two batched {@code SELECT … IN (…)} queries
     * (one per field) regardless of row count, so 500 rows = 2 round
     * trips instead of 1000.
     */
    @Transactional(readOnly = true)
    public com.kosign.wecms.domain.setting.contacts.dto.ContactsExcelPreflightResponse preflightExcelImport(
            com.kosign.wecms.domain.setting.contacts.dto.ContactsExcelPreflightRequest req) {
        String bizKey = TenantContext.currentBizKey();
        var rows = req.rows();

        // Collect non-blank codes + tax IDs once for the IN-queries.
        Set<String> handTrcoKeysToCheck = new HashSet<>();
        Set<String> trcoTaxIdsToCheck = new HashSet<>();
        for (var row : rows) {
            if (row.handTrcoKey() != null && !row.handTrcoKey().isBlank()) {
                handTrcoKeysToCheck.add(row.handTrcoKey());
            }
            if (row.trcoTaxId() != null && !row.trcoTaxId().isBlank()) {
                trcoTaxIdsToCheck.add(row.trcoTaxId());
            }
        }

        Set<String> existingCodes = handTrcoKeysToCheck.isEmpty()
                ? Set.of()
                : new HashSet<>(trcoInfmRepo.findExistingActiveHandTrcoKeys(bizKey, handTrcoKeysToCheck));
        Set<String> existingTaxIds = trcoTaxIdsToCheck.isEmpty()
                ? Set.of()
                : new HashSet<>(trcoInfmRepo.findExistingActiveTrcoTaxIds(bizKey, trcoTaxIdsToCheck));

        List<com.kosign.wecms.domain.setting.contacts.dto.ContactsExcelPreflightResponse.Result> results =
                new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i);
            boolean codeDup = row.handTrcoKey() != null
                    && !row.handTrcoKey().isBlank()
                    && existingCodes.contains(row.handTrcoKey());
            boolean taxIdDup = row.trcoTaxId() != null
                    && !row.trcoTaxId().isBlank()
                    && existingTaxIds.contains(row.trcoTaxId());
            results.add(new com.kosign.wecms.domain.setting.contacts.dto.ContactsExcelPreflightResponse.Result(
                    i, codeDup, taxIdDup));
        }

        return new com.kosign.wecms.domain.setting.contacts.dto.ContactsExcelPreflightResponse(results);
    }

    private ContactRow persistNewContact(ContactCreateRequest req, String bizKey) {
        String trcoKey = legacyFn.nextTrcoKey(bizKey);
        String userId = TenantContext.currentUserId();
        String now = utcNowDtm14();

        TrcoInfm master = new TrcoInfm();
        master.setTrcoKey(trcoKey);
        master.setBizKey(bizKey);
        // Master tax-id snapshot: legacy stamps the contact's own tax id
        // when present, else the tenant's. We pass the contact's tax id
        // here (or an empty fallback) since the NOT NULL constraint on
        // TAX_ID is otherwise hard to satisfy without an extra lookup.
        master.setTaxId(req.trcoTaxId() != null && !req.trcoTaxId().isBlank()
                ? req.trcoTaxId() : bizKey);
        master.setTrcoCmnm(req.trcoCmnm());
        master.setTrcoCmenm(req.trcoCmenm());
        master.setTrcoTaxId(req.trcoTaxId());
        master.setTrcoType(req.trcoType());
        // OQ-3 — default-on-create. Service-internal; never on the wire.
        master.setTrcoInqYn("Y");
        // CHECK trsc_ccy_cd IN ('VND','USD'); default by legacy pattern.
        master.setTrscCcyCd("L".equals(req.trcoType()) ? "VND" : "USD");
        master.setHandTrcoKey(req.handTrcoKey());
        master.setRegId(userId);
        master.setRegDtm(now);
        master.setModId(userId);
        master.setModDtm(now);
        trcoInfmRepo.save(master);

        // Sub-rows — only upsert ops survive on create. Delete ops on
        // create are nonsensical and silently ignored.
        if (req.addresses() != null) {
            for (AddressOp a : req.addresses()) {
                if (!"upsert".equals(a.op())) continue;
                // OD-1: every address is new on create — ignore the frontend
                // placeholder key and allocate via the wb_get_next_val funnel.
                // saveAndFlush so the next per-(biz_key,trco_key) MAX+1 sees it.
                String addrKey = legacyFn.nextTrcoAddrKey(bizKey, trcoKey);
                trcoAddrRepo.saveAndFlush(toAddrEntity(a, bizKey, trcoKey, addrKey, userId, now));
            }
        }
        if ("L".equals(req.trcoType()) && req.accounts() != null) {
            for (AccountOp a : req.accounts()) {
                if (!"upsert".equals(a.op())) continue;
                // OD-1: every account is new on create — ignore the frontend
                // placeholder key and allocate via the wb_get_next_val funnel.
                // saveAndFlush so the next per-(biz_key,trco_key) MAX+1 sees it.
                String acctKey = legacyFn.nextTrcoAcctKey(bizKey, trcoKey);
                trcoAcctRepo.saveAndFlush(toAcctEntity(a, bizKey, trcoKey, acctKey, userId, now));
            }
        }
        if ("O".equals(req.trcoType()) && req.overseasBeneficiaries() != null) {
            for (OverseasOp o : req.overseasBeneficiaries()) {
                if (!"upsert".equals(o.op())) continue;
                // OD-1: every overseas beneficiary is new on create — ignore the
                // frontend placeholder key and allocate via the wb_get_next_val
                // funnel (per-tenant MAX+1). saveAndFlush so the next read sees it.
                String ovrsKey = legacyFn.nextTrcoOvrsKey(bizKey);
                trcoOvrsRepo.saveAndFlush(toOvrsEntity(o, bizKey, trcoKey, ovrsKey, userId, now));
            }
        }

        // PGRO seed: 'B' (buy) + 'S' (sale) with USER_ID = 'ALL'.
        // Mirrors comm_0184_002_c001_act.jsp:131-147.
        for (String grpCd : List.of("B", "S")) {
            PgroBsacLnknInfm link = new PgroBsacLnknInfm();
            link.setBizKey(bizKey);
            link.setUserId("ALL");
            link.setGrpCd(grpCd);
            link.setTrcoKey(trcoKey);
            link.setTaxId(master.getTaxId());
            link.setRegDtm(now);
            pgroRepo.save(link);
        }

        return getOne(trcoKey);
    }

    /* =================================================================
     *  Update (single transaction; upsert + delete sub-rows + opt trash)
     * ================================================================= */

    @Transactional
    public ContactRow update(String trcoKey, ContactUpdateRequest req) {
        String bizKey = TenantContext.currentBizKey();
        TrcoInfm existing = trcoInfmRepo.findByTrcoKeyAndBizKey(trcoKey, bizKey)
                .orElseThrow(() -> new BusinessException("WBE_CONTACTS_NOT_FOUND"));

        validator.validateUpdate(req, existing, bizKey);

        // OQ-2: in-use guard fires when this update transitions
        // trco_stts from null/non-'C' to 'C' (the edit-time trash path).
        boolean transitioningToTrash =
                "C".equals(req.trcoStts())
                        && (existing.getTrcoStts() == null || !existing.getTrcoStts().equals("C"));
        if (transitioningToTrash
                && acctTrnsRcvPayRepo.existsByBizKeyAndRcvPayTrcoKey(bizKey, trcoKey)) {
            throw new BusinessException("WBE1190");
        }

        String userId = TenantContext.currentUserId();
        String now = utcNowDtm14();

        existing.setHandTrcoKey(req.handTrcoKey());
        existing.setTrcoCmnm(req.trcoCmnm());
        existing.setTrcoCmenm(req.trcoCmenm());
        existing.setTrcoTaxId(req.trcoTaxId());
        existing.setTrcoType(req.trcoType());
        // Pass through the trash flag verbatim (null or 'C').
        if (req.trcoStts() != null && !req.trcoStts().isEmpty()) {
            existing.setTrcoStts(req.trcoStts());
        }
        existing.setModId(userId);
        existing.setModDtm(now);
        trcoInfmRepo.save(existing);

        // Addresses — upsert + delete pass.
        if (req.addresses() != null) {
            // OD-1: tell an existing address (preserve its real key) from a new
            // address carrying a frontend placeholder (generate via the
            // wb_get_next_val funnel). Match against the keys already persisted
            // for this contact — the frontend always sends a non-blank placeholder.
            Set<String> existingAddrKeys = new HashSet<>();
            for (TrcoAddrInfm ex : trcoAddrRepo.findByBizKeyAndTrcoKey(bizKey, trcoKey)) {
                existingAddrKeys.add(ex.getTrcoAddrKey());
            }
            for (AddressOp a : req.addresses()) {
                if ("upsert".equals(a.op())) {
                    String addrKey = (a.trcoAddrKey() != null && existingAddrKeys.contains(a.trcoAddrKey()))
                            ? a.trcoAddrKey()                              // existing → preserve
                            : legacyFn.nextTrcoAddrKey(bizKey, trcoKey);   // new → generate (placeholder ignored)
                    trcoAddrRepo.saveAndFlush(toAddrEntity(a, bizKey, trcoKey, addrKey, userId, now));
                } else {
                    trcoAddrRepo.deleteByBizKeyAndTrcoKeyAndTrcoAddrKey(
                            bizKey, trcoKey, a.trcoAddrKey());
                }
            }
        }

        // Accounts — upsert + delete pass + folded REC4 deletes.
        if (req.accounts() != null) {
            // OD-1: tell an existing account (preserve its real key) from a new
            // account carrying a frontend placeholder (generate via the
            // wb_get_next_val funnel). Blank-check is unsafe — the frontend
            // always sends a non-blank placeholder — so match against the keys
            // already persisted for this contact.
            Set<String> existingAcctKeys = new HashSet<>();
            for (TrcoAcctInfo ex : trcoAcctRepo.findByBizKeyAndTrcoKey(bizKey, trcoKey)) {
                existingAcctKeys.add(ex.getTrcoAcctKey());
            }
            for (AccountOp a : req.accounts()) {
                if ("upsert".equals(a.op())) {
                    String acctKey = (a.trcoAcctKey() != null && existingAcctKeys.contains(a.trcoAcctKey()))
                            ? a.trcoAcctKey()                              // existing → preserve
                            : legacyFn.nextTrcoAcctKey(bizKey, trcoKey);   // new → generate (placeholder ignored)
                    trcoAcctRepo.saveAndFlush(toAcctEntity(a, bizKey, trcoKey, acctKey, userId, now));
                } else {
                    trcoAcctRepo.deleteByBizKeyAndTrcoKeyAndTrcoAcctKey(
                            bizKey, trcoKey, a.trcoAcctKey());
                }
            }
        }
        if (req.accountsToDelete() != null) {
            for (AccountDeleteKey k : req.accountsToDelete()) {
                trcoAcctRepo.deleteByBizKeyAndTrcoKeyAndTrcoAcctKey(
                        bizKey, trcoKey, k.trcoAcctKey());
            }
        }

        // Overseas beneficiaries — same pattern + folded REC6 deletes.
        if (req.overseasBeneficiaries() != null) {
            // OD-1: preserve an existing beneficiary's real key; allocate a fresh
            // key via the funnel for a new row carrying a frontend placeholder.
            // The generator scope is per-tenant (biz_key only), but the existence
            // check matches the contact's own rows (PK = biz_key, trco_key, trco_ovrs_key).
            Set<String> existingOvrsKeys = new HashSet<>();
            for (TrcoOvrsInfm ex : trcoOvrsRepo.findByBizKeyAndTrcoKey(bizKey, trcoKey)) {
                existingOvrsKeys.add(ex.getTrcoOvrsKey());
            }
            for (OverseasOp o : req.overseasBeneficiaries()) {
                if ("upsert".equals(o.op())) {
                    String ovrsKey = (o.trcoOvrsKey() != null && existingOvrsKeys.contains(o.trcoOvrsKey()))
                            ? o.trcoOvrsKey()                          // existing → preserve
                            : legacyFn.nextTrcoOvrsKey(bizKey);        // new → generate (placeholder ignored)
                    trcoOvrsRepo.saveAndFlush(toOvrsEntity(o, bizKey, trcoKey, ovrsKey, userId, now));
                } else {
                    trcoOvrsRepo.deleteByBizKeyAndTrcoKeyAndTrcoOvrsKey(
                            bizKey, trcoKey, o.trcoOvrsKey());
                }
            }
        }
        if (req.overseasToDelete() != null) {
            for (OverseasDeleteKey k : req.overseasToDelete()) {
                trcoOvrsRepo.deleteByBizKeyAndTrcoKeyAndTrcoOvrsKey(
                        bizKey, trcoKey, k.trcoOvrsKey());
            }
        }

        // After the trash transition, the standard list filter hides
        // the row — return via getOne anyway so the response shape stays
        // consistent. The caller's frontend handles the "row now hidden"
        // case at refresh time.
        if ("C".equals(req.trcoStts())) {
            return projectRow(existing,
                    trcoAddrRepo.findByBizKeyAndTrcoKey(bizKey, trcoKey),
                    trcoAcctRepo.findByBizKeyAndTrcoKey(bizKey, trcoKey),
                    trcoOvrsRepo.findByBizKeyAndTrcoKey(bizKey, trcoKey),
                    Map.of());
        }
        return getOne(trcoKey);
    }

    /* =================================================================
     *  Soft-delete (single + bulk; OQ-2)
     * ================================================================= */

    @Transactional
    public void softDelete(String trcoKey) {
        String bizKey = TenantContext.currentBizKey();
        TrcoInfm existing = trcoInfmRepo.findByTrcoKeyAndBizKey(trcoKey, bizKey)
                .orElseThrow(() -> new BusinessException("WBE_CONTACTS_NOT_FOUND"));
        if (acctTrnsRcvPayRepo.existsByBizKeyAndRcvPayTrcoKey(bizKey, trcoKey)) {
            throw new BusinessException("WBE1190");
        }
        existing.setTrcoStts("C");
        existing.setModId(TenantContext.currentUserId());
        existing.setModDtm(utcNowDtm14());
    }

    @Transactional
    public void bulkSoftDelete(List<String> trcoKeys) {
        String bizKey = TenantContext.currentBizKey();
        String userId = TenantContext.currentUserId();
        String now = utcNowDtm14();
        for (String trcoKey : trcoKeys) {
            TrcoInfm existing = trcoInfmRepo.findByTrcoKeyAndBizKey(trcoKey, bizKey)
                    .orElseThrow(() -> new BusinessException("WBE_CONTACTS_NOT_FOUND"));
            // OQ-2 per-key in-use guard. The first offending key throws
            // and rolls back any earlier flips in the same call.
            if (acctTrnsRcvPayRepo.existsByBizKeyAndRcvPayTrcoKey(bizKey, trcoKey)) {
                throw new BusinessException("WBE1190");
            }
            existing.setTrcoStts("C");
            existing.setModId(userId);
            existing.setModDtm(now);
        }
    }

    /* =================================================================
     *  Internal helpers
     * ================================================================= */

    /** Lower-case + escape-meta-chars + wrap with {@code %&hellip;%}.
     *  Returns {@code null} when blank so the JPQL {@code IS NULL OR}
     *  guard skips the predicate. Mirrors the keyword pattern used
     *  by neighbour services (ERP Transaction History, Firm Banking
     *  History). */
    private static String buildKeywordPattern(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;
        StringBuilder b = new StringBuilder(trimmed.length() + 2).append('%');
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '%' || c == '_' || c == '!') b.append('!');
            b.append(c);
        }
        b.append('%');
        return b.toString().toLowerCase(Locale.ROOT);
    }

    /** Coerce the wire {@code trco_type} into a JPQL filter value.
     *  Unknown / blank input collapses to {@code null} so the
     *  {@code IS NULL OR} guard skips the predicate. */
    private static String normaliseTrcoTypeFilter(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;
        return ("L".equals(trimmed) || "O".equals(trimmed)) ? trimmed : null;
    }

    private static Sort buildSort(ContactSortField field, SortDirection direction) {
        if (field == null || direction == null) return DEFAULT_SORT;
        Sort.Order primary = direction == SortDirection.ASC
                ? Sort.Order.asc(field.entityProperty())
                : Sort.Order.desc(field.entityProperty());
        return Sort.by(primary).and(DEFAULT_SORT);
    }

    /** Resolve the locale code accepted by the legacy bank-name function
     *  ({@code 'en'} / {@code 'vi'}; other codes return null from the
     *  function). Reads from {@link TenantContext#currentLangCd()}
     *  populated by the JWT filter. */
    private static String currentLangForBankFn() {
        LangCd lang = TenantContext.currentLangCd();
        if (lang == null) return "en";
        return switch (lang) {
            case VI -> "vi";
            case EN, KO, KM -> "en";  // function only branches on 'en' / 'vi'/'vn'.
        };
    }

    /** Resolve each unique NAPAS-or-CITAD code on the page in one round
     *  trip per unique code (NOT per row) — avoids N+1. The cache key
     *  is the NAPAS code if present, otherwise the CITAD code. */
    private Map<String, String> resolveBankNamesForAccounts(
            Collection<List<TrcoAcctInfo>> grouped, String langCd) {
        Set<String> codes = new HashSet<>();
        for (List<TrcoAcctInfo> bucket : grouped) {
            for (TrcoAcctInfo a : bucket) {
                String code = pickNapasOrCitad(a);
                if (code != null) codes.add(code);
            }
        }
        Map<String, String> cache = new HashMap<>();
        for (String code : codes) {
            cache.put(code, legacyFn.resolveNapasOrCitadBankName(code, langCd));
        }
        return cache;
    }

    private static String pickNapasOrCitad(TrcoAcctInfo a) {
        if (a.getNapasBankCd() != null && !a.getNapasBankCd().isBlank()) {
            return a.getNapasBankCd();
        }
        if (a.getCitadBankCd() != null && !a.getCitadBankCd().isBlank()) {
            return a.getCitadBankCd();
        }
        return null;
    }

    private static <T> Map<String, List<T>> groupByTrcoKey(
            List<T> rows, java.util.function.Function<T, String> keyFn) {
        Map<String, List<T>> out = new HashMap<>();
        for (T row : rows) {
            out.computeIfAbsent(keyFn.apply(row), k -> new ArrayList<>()).add(row);
        }
        return out;
    }

    /** Compose the wire row from the master + child collections. */
    private ContactRow projectRow(
            TrcoInfm master,
            List<TrcoAddrInfm> addresses,
            List<TrcoAcctInfo> accounts,
            List<TrcoOvrsInfm> overseas,
            Map<String, String> bankNameCache) {

        List<ContactAddressRow> addrOut = addresses.stream()
                .sorted(Comparator
                        .comparing(TrcoAddrInfm::getDispOrd, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(TrcoAddrInfm::getTrcoAddrKey))
                .map(a -> new ContactAddressRow(
                        a.getTrcoAddrKey(),
                        a.getTrcoBsunPostAdrs(),
                        a.getDispOrd(),
                        a.getIssuDt(),
                        a.getDueDt()))
                .toList();

        List<ContactAccountRow> acctOut = accounts.stream()
                .sorted(Comparator
                        .comparing(TrcoAcctInfo::getDispOrd, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(TrcoAcctInfo::getTrcoAcctKey))
                .map(a -> {
                    String code = pickNapasOrCitad(a);
                    String resolved = code == null ? null : bankNameCache.get(code);
                    return new ContactAccountRow(
                            a.getTrcoAcctKey(),
                            a.getAcctNo(),
                            a.getDispOrd(),
                            // Prefer the freshly-resolved name; fall back
                            // to the stored display copy when the function
                            // returns null (sparse tb_firm_bank_code seed).
                            resolved != null ? resolved : a.getBankNm(),
                            a.getNapasBankCd(),
                            a.getCitadBankCd(),
                            a.getAcctCcyCd(),
                            a.getFincMdaOwnerNm(),
                            a.getOwacAddr());
                })
                .toList();

        List<ContactOverseasRow> ovrsOut = overseas.stream()
                .sorted(Comparator.comparing(TrcoOvrsInfm::getTrcoOvrsKey))
                .map(o -> new ContactOverseasRow(
                        o.getTrcoOvrsKey(),
                        o.getBeneName(),
                        o.getBeneAcctNo(),
                        o.getBicCode(),
                        o.getBeneBankName(),
                        o.getBeneBankCode(),
                        o.getBeneAddr(),
                        o.getBeneTownNm()))
                .toList();

        return new ContactRow(
                master.getTrcoKey(),
                master.getHandTrcoKey(),
                master.getTrcoCmnm(),
                master.getTrcoCmenm(),
                master.getTrcoTaxId(),
                master.getTrcoType(),
                master.getTrcoStts(),
                master.getModDtm(),
                addrOut,
                acctOut,
                ovrsOut);
    }

    /* =================================================================
     *  Sub-row entity factories
     * ================================================================= */

    private static TrcoAddrInfm toAddrEntity(
            AddressOp a, String bizKey, String trcoKey, String trcoAddrKey, String userId, String now) {
        TrcoAddrInfm e = new TrcoAddrInfm();
        e.setTrcoKey(trcoKey);
        e.setBizKey(bizKey);
        // TRCO_ADDR_KEY is funnel-allocated (or preserved) by the caller —
        // never the frontend placeholder (OD-1 closure).
        e.setTrcoAddrKey(trcoAddrKey);
        e.setTrcoBsunPostAdrs(a.trcoBsunPostAdrs());
        e.setDispOrd(a.dispOrd());
        // Legacy parity: ISSU_DT defaults to today on insert; DUE_DT
        // defaults to today too when the caller omits it.
        String today = now.substring(0, 8);
        e.setIssuDt(a.issuDt() != null && !a.issuDt().isBlank() ? a.issuDt() : today);
        e.setDueDt(a.dueDt()   != null && !a.dueDt().isBlank()   ? a.dueDt()   : today);
        e.setRegId(userId);
        e.setRegDtm(now);
        e.setModId(userId);
        e.setModDtm(now);
        return e;
    }

    private static TrcoAcctInfo toAcctEntity(
            AccountOp a, String bizKey, String trcoKey, String trcoAcctKey, String userId, String now) {
        TrcoAcctInfo e = new TrcoAcctInfo();
        e.setBizKey(bizKey);
        e.setTrcoKey(trcoKey);
        e.setTrcoAcctKey(trcoAcctKey);
        e.setAcctNo(a.acctNo());
        // Legacy default — DISP_ORD = 1 when caller omits.
        e.setDispOrd(a.dispOrd() != null ? a.dispOrd() : 1);
        e.setBankNm(a.bankNm());
        e.setNapasBankCd(a.napasBankCd());
        e.setCitadBankCd(a.citadBankCd());
        e.setAcctCcyCd(a.acctCcyCd());
        e.setFincMdaOwnerNm(a.fincMdaOwnerNm());
        e.setOwacAddr(a.owacAddr());
        e.setRegId(userId);
        e.setRegDtm(now);
        e.setModId(userId);
        e.setModDtm(now);
        return e;
    }

    private static TrcoOvrsInfm toOvrsEntity(
            OverseasOp o, String bizKey, String trcoKey, String trcoOvrsKey, String userId, String now) {
        TrcoOvrsInfm e = new TrcoOvrsInfm();
        e.setTrcoKey(trcoKey);
        e.setBizKey(bizKey);
        // TRCO_OVRS_KEY is funnel-allocated (or preserved) by the caller —
        // never the frontend placeholder (OD-1 closure).
        e.setTrcoOvrsKey(trcoOvrsKey);
        e.setBeneName(o.beneName());
        e.setBeneAcctNo(o.beneAcctNo());
        e.setBicCode(o.bicCode());
        e.setBeneBankName(o.beneBankName());
        e.setBeneBankCode(o.beneBankCode());
        e.setBeneAddr(o.beneAddr());
        // Auto-filled read-only from the selected BIC's SWIFT directory
        // entry (SWIFT_BIC_MST.PSTL_ADR_TWN_NM) — legacy comm_0184_002.js.
        e.setBeneTownNm(o.beneTownNm());
        e.setRegId(userId);
        e.setRegDtm(now);
        e.setModId(userId);
        e.setModDtm(now);
        return e;
    }

    private static String utcNowDtm14() {
        return java.time.OffsetDateTime.now(ZoneOffset.UTC).format(DTM14);
    }

    /* =================================================================
     *  Bank picker (NAPAS + CITAD) + Lookup Recipient — Add Contact UI.
     *  Mirrors legacy comm_0109_001_r002 (bank list) and a deliberate
     *  stub of comm_1003_001 (Lookup Recipient) — see "Surviving
     *  legacy functions" in CLAUDE.md. The real Lookup endpoint calls
     *  the firm-banking scraping module which is out of refactor scope;
     *  the stub returns a deterministic mock so the popup UX flow
     *  (verified vs manual recipient) still exercises end-to-end.
     * ================================================================= */

    /**
     * Bank-picker autocomplete for the contacts Add/Edit dialog —
     * <b>one selectable row per distinct bank</b>, mirroring legacy
     * {@code comm_0109_001_r002} / {@code IDO.TB_FIRM_BANK_CODE_R002}.
     * The dictionary is global — not tenant-scoped — so the read does
     * not consult {@link TenantContext}.
     *
     * <p><b>Why this is not a plain {@code WHERE ANT_DIV_CD IN
     * ('NAPAS','CITAD')} read.</b> {@code TB_FIRM_BANK_CODE} stores one
     * CITAD row per <em>branch</em> (~3,000 rows), so a flat read
     * repeated each bank once per branch — e.g. "ABBank" appeared ~29
     * times in the dropdown. Legacy {@code R002} avoids this by:
     * <ol>
     *   <li>filtering CITAD to headquarters rows only
     *       ({@code BANK_BRANCH_DIV='1'} — its {@code CITAD_BKCD} CTE);</li>
     *   <li>deduping by {@code ENG_ABRVAT_NM} across NAPAS + CITAD, with
     *       NAPAS preferred for display + routing (its three-way UNION:
     *       both / CITAD-only / NAPAS-only).</li>
     * </ol>
     *
     * <p>In the flat {@link ContactBankRow} wire shape (one bank code +
     * one division per row) that collapses to: every active NAPAS row
     * ({@code status='Y'} — the recipient Lookup flow is available, per
     * legacy {@code CASE WHEN NAPAS_CD IS NOT NULL THEN 'Y'}) plus every
     * active CITAD headquarters row whose abbreviation is <em>not</em>
     * already covered by NAPAS ({@code status='N'} — manual Recipient
     * Name entry). NAPAS abbreviations are unique, so each NAPAS bank is
     * a single entry. {@code status} therefore encodes NAPAS
     * availability (legacy {@code R002.STATUS}), not the {@code BANK_STAS}
     * active flag — {@code BANK_STAS='1'} is applied as a filter here,
     * matching legacy.
     */
    @Transactional(readOnly = true)
    public ContactBankListResponse listBanks() {
        List<TbFirmBankCode> napasRows =
                bankCodeRepo.findByAntDivCdAndBankStas("NAPAS", "1");
        List<TbFirmBankCode> citadHqRows =
                bankCodeRepo.findByAntDivCdAndBankBranchDivAndBankStas("CITAD", "1", "1");

        Set<String> napasAbbrevs = new HashSet<>();
        for (TbFirmBankCode r : napasRows) {
            if (r.getEngAbrvatNm() != null) {
                napasAbbrevs.add(r.getEngAbrvatNm());
            }
        }

        List<ContactBankRow> out =
                new ArrayList<>(napasRows.size() + citadHqRows.size());
        for (TbFirmBankCode r : napasRows) {
            out.add(new ContactBankRow(
                    r.getBankCd(), "NAPAS",
                    r.getBankNm(), r.getBankEngNm(), r.getEngAbrvatNm(), "Y"));
        }
        for (TbFirmBankCode r : citadHqRows) {
            // A bank reachable via NAPAS is presented as its NAPAS entry
            // (legacy prioritises NAPAS for display + the saved code) — so
            // skip the CITAD duplicate. Only CITAD-exclusive banks remain.
            if (r.getEngAbrvatNm() != null && napasAbbrevs.contains(r.getEngAbrvatNm())) {
                continue;
            }
            out.add(new ContactBankRow(
                    r.getBankCd(), "CITAD",
                    r.getBankNm(), r.getBankEngNm(), r.getEngAbrvatNm(), "N"));
        }

        // Legacy R002 ORDER BY ENG_ABRVAT_NM ASC, COALESCE(CITAD_CD, NAPAS_CD).
        out.sort(Comparator
                .comparing((ContactBankRow b) -> b.engAbrvatNm() == null ? "" : b.engAbrvatNm(),
                        String.CASE_INSENSITIVE_ORDER)
                .thenComparing(b -> b.bankCd() == null ? "" : b.bankCd()));
        return new ContactBankListResponse(out);
    }

    /**
     * Overseas-contact BIC search typeahead. Mirrors legacy
     * {@code comm_1007_001} ("Search Swift Bic Code") /
     * {@code IDO.SWIFT_BIC_MST_R002}: uppercases the keyword, matches
     * {@code BIC_CD LIKE 'kw%'}, orders by {@code BIC_CD}, caps at 50
     * rows. A blank keyword returns the first 50 codes (legacy initial
     * list). The SWIFT master carries no bank name, so search is
     * BIC-only. Global dictionary — not tenant-scoped.
     *
     * @param q raw search keyword (may be null / blank); uppercased here.
     */
    @Transactional(readOnly = true)
    public SwiftBicListResponse searchSwiftBic(String q) {
        String prefix = (q == null) ? "" : q.trim().toUpperCase();
        List<SwiftBicRow> rows =
                swiftBicRepo.findTop50ByBicCdStartingWithOrderByBicCdAsc(prefix).stream()
                        .map(r -> new SwiftBicRow(r.getBicCd(), r.getPstlAdrTwnNm()))
                        .toList();
        return new SwiftBicListResponse(rows);
    }

    /**
     * Bulk bank-name &rarr; bank-code resolution + per-row recipient
     * inquiry for the Local-Contact Excel-upload preview. Mirrors the
     * legacy preview's per-row work in
     * {@code comm_0184_007_r001_act.jsp:475-533}: the bank cell is
     * matched against {@code TB_FIRM_BANK_CODE.ENG_ABRVAT_NM}, the
     * match-count drives NAPAS-vs-CITAD routing, and NAPAS rows
     * additionally fire {@link FirmBankingInquiryService#inquiryAccount}
     * to stamp the holder name on the preview row.
     *
     * <p>Match-count routing (legacy parity, lines 481-505):
     * <ul>
     *   <li>0 matches &rarr; {@code bankFound = false}; frontend flags
     *       the bank cell red.</li>
     *   <li>1 match &rarr; honour the row's {@code ANT_DIV_CD}.</li>
     *   <li>&ge; 2 matches (legacy assumption: name overlap between
     *       NAPAS and CITAD) &rarr; treat as NAPAS, prefer the NAPAS
     *       row.</li>
     * </ul>
     *
     * <p>One DB call regardless of row count &mdash; the distinct bank
     * names go through {@link TbFirmBankCodeRepository#findByEngAbrvatNmIn}.
     * The recipient inquiry fans out per NAPAS-VND row but each call is
     * a fast in-memory stub today (real bank API later, behind the
     * shared {@link FirmBankingInquiryService} seam).
     */
    @Transactional(readOnly = true)
    public com.kosign.wecms.domain.setting.contacts.dto.ContactsExcelResolveRecipientsResponse resolveExcelImportRecipients(
            com.kosign.wecms.domain.setting.contacts.dto.ContactsExcelResolveRecipientsRequest req) {
        var rows = req.rows();

        // Tenant-level firm-banking gate (legacy comm_1003_001_act.jsp's
        // contactInfo!=null branch). When the tenant has no active
        // TB_FIRM_CONF row we still resolve bank codes for every row
        // (so NAPAS_BANK_CD / CITAD_BANK_CD on the import payload stays
        // correct) but skip every per-row recipient inquiry — there is
        // nothing to inquire against. Saves N DB hits per upload.
        boolean inquiryEnabled = firmBankingInquiry.hasActiveFirmConfig();

        // One IN-query for every distinct non-blank bank name.
        Set<String> distinctBankNames = new HashSet<>();
        for (var row : rows) {
            if (row.bankName() != null && !row.bankName().isBlank()) {
                distinctBankNames.add(row.bankName().trim());
            }
        }
        // Group every TB_FIRM_BANK_CODE row by name. Multiple matches
        // per name are expected (NAPAS row + CITAD row sharing the same
        // ENG_ABRVAT_NM).
        Map<String, List<TbFirmBankCode>> banksByName = new HashMap<>();
        if (!distinctBankNames.isEmpty()) {
            for (TbFirmBankCode b : bankCodeRepo.findByEngAbrvatNmIn(distinctBankNames)) {
                banksByName.computeIfAbsent(b.getEngAbrvatNm(), k -> new ArrayList<>()).add(b);
            }
        }

        List<com.kosign.wecms.domain.setting.contacts.dto.ContactsExcelResolveRecipientsResponse.Result> results =
                new ArrayList<>(rows.size());
        for (var row : rows) {
            String name = row.bankName() == null ? "" : row.bankName().trim();
            if (name.isEmpty()) {
                // Frontend already flagged this row's bank cell during
                // client-side parsing; we still emit a Result entry so
                // the index alignment is 1:1.
                results.add(new com.kosign.wecms.domain.setting.contacts.dto.ContactsExcelResolveRecipientsResponse.Result(
                        row.rowIndex(), false, null, null, null, null));
                continue;
            }

            List<TbFirmBankCode> matches = banksByName.getOrDefault(name, List.of());
            if (matches.isEmpty()) {
                results.add(new com.kosign.wecms.domain.setting.contacts.dto.ContactsExcelResolveRecipientsResponse.Result(
                        row.rowIndex(), false, null, null, null, null));
                continue;
            }

            // Pick the NAPAS row when the name maps to both NAPAS and
            // CITAD (legacy r001_act.jsp:485-491). When only one match,
            // honour whichever ant_div_cd it is.
            TbFirmBankCode picked = null;
            if (matches.size() >= 2) {
                for (TbFirmBankCode m : matches) {
                    if ("NAPAS".equals(m.getAntDivCd())) { picked = m; break; }
                }
                if (picked == null) picked = matches.get(0);
            } else {
                picked = matches.get(0);
            }

            String antDiv = picked.getAntDivCd();
            String napasCd = "NAPAS".equals(antDiv) ? picked.getBankCd() : null;
            String citadCd = "CITAD".equals(antDiv) ? picked.getBankCd() : null;

            String holderName = "";
            // Recipient inquiry runs only for NAPAS rows whose currency
            // is VND — mirrors the dialog's `showLookup` gate
            // (`bankStatus !== 'N' && currency !== 'USD'`) and the
            // legacy upload's `cellValue_.endsWith("NAPAS")` branch at
            // r001_act.jsp:531. Also skip when the tenant-level firm
            // config check above said there's nothing to inquire against.
            if (inquiryEnabled && napasCd != null && "VND".equalsIgnoreCase(row.currency())) {
                holderName = firmBankingInquiry.inquiryAccount(napasCd, row.acctNo()).orElse("");
            }

            results.add(new com.kosign.wecms.domain.setting.contacts.dto.ContactsExcelResolveRecipientsResponse.Result(
                    row.rowIndex(), true, antDiv, napasCd, citadCd, holderName));
        }

        return new com.kosign.wecms.domain.setting.contacts.dto.ContactsExcelResolveRecipientsResponse(results);
    }

    /**
     * Lookup Recipient &mdash; "Lookup Recipient" button on the Add /
     * Edit Contact popup. Delegates to the shared
     * {@link com.kosign.wecms.common.firmbanking.FirmBankingInquiryService}
     * so the popup, the Excel-upload preview ({@link #resolveExcelImportRecipients}),
     * and any future Payment / Transfer flow all share one bank-API
     * seam.
     *
     * <p>Legacy mirror: {@code comm_0184_002.ts:530-580 lookUpRecipitend}
     * &rarr; {@code comm_1003_001} &rarr; {@code FIRM_PROC.inquiryAccount}.
     * The shared service is NAPAS-only by contract; CITAD bank codes
     * pass through to the manual Recipient Name field in the dialog
     * UI (see {@code AddNewContactDialog.tsx} {@code showLookup}
     * gate). When called with only {@code citadBankCd}, this method
     * returns an empty string so the existing dialog branches
     * unchanged.
     */
    public ContactLookupRecipientResponse lookupRecipient(ContactLookupRecipientRequest req) {
        String napasBankCd = req.napasBankCd();
        if (napasBankCd == null || napasBankCd.isBlank()) {
            // CITAD-only call — the dialog won't actually fire this in
            // practice (the Lookup button hides for CITAD) but a stale
            // client should get an empty response instead of a mock.
            return new ContactLookupRecipientResponse("");
        }
        return firmBankingInquiry.inquiryAccount(napasBankCd, req.acctNo())
                .map(ContactLookupRecipientResponse::new)
                .orElse(new ContactLookupRecipientResponse(""));
    }
}
