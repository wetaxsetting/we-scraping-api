# Contacts — Spring Boot Backend Design

| Field | Value |
|---|---|
| Refactor slug | `contacts` |
| Java root package | `com.kosign.wecms.domain.setting.contacts` |
| REST base URI | `/api/v1/settings/contacts` |
| Functional spec | [`docs/refactor-wecms/features/contacts.md`](../../features/settings/contacts.md) |
| Migration mapping | [`docs/refactor-wecms/migration/contacts-mapping.md`](../migration/contacts-mapping.md) |
| Schema | [`docs/refactor-wecms/database/contacts-schema.md`](../database/contacts-schema.md) |

This file is the design-only blueprint for the Spring Boot side. Code lives in `wecms-api/src/main/java/com/kosign/wecms/domain/setting/contacts/` once the implementation MR lands; nothing here is implemented yet. All conventions track [`wecms-api/CLAUDE.md`](../../../CLAUDE.md) — flag any deviation in the MR description, never freelance one in.

---

## 1. Package layout

```
com.kosign.wecms.domain.setting.contacts
├── controller/
│   └── ContactsController.java
├── service/
│   ├── ContactsService.java                 — public API (read + write)
│   ├── ContactsValidator.java               — duplicate + business-rule checks
│   ├── TrcoKeyGeneratorService.java         — TRCO_KEY allocation (Java-side, no wb_get_next_val)
│   └── ContactSortField.java                — common-sort whitelist enum
├── repository/
│   ├── TrcoInfmRepository.java
│   ├── TrcoAddrInfmRepository.java
│   ├── TrcoAcctInfoRepository.java
│   ├── TrcoOvrsInfmRepository.java
│   ├── PgroBsacLnknInfmRepository.java     — reuse if it already exists; new otherwise
│   └── AcctTrnsRcvPayInfmRepository.java   — extended with existsByBizKeyAndTrcoKey for the in-use guard
├── entity/
│   ├── TrcoInfm.java + TrcoInfmId.java
│   ├── TrcoAddrInfm.java + TrcoAddrInfmId.java
│   ├── TrcoAcctInfo.java + TrcoAcctInfoId.java
│   ├── TrcoOvrsInfm.java + TrcoOvrsInfmId.java
│   └── PgroBsacLnknInfm.java + PgroBsacLnknInfmId.java     — only if no shared module owns it yet
└── dto/
    ├── ContactRow.java                      — list response row (record)
    ├── ContactDetail.java                   — single-contact GET (record)
    ├── ContactCreateRequest.java            — POST body (record)
    ├── ContactUpdateRequest.java            — PUT body (record)
    ├── AddressOp.java / AccountOp.java / OverseasOp.java   — nested sub-rows with `op` discriminator
    ├── AddressDeleteKey.java / AccountDeleteKey.java / OverseasDeleteKey.java
    ├── BulkDeleteRequest.java
    └── ContactListResponse.java             — wraps { rows, total_count }
```

Package rules followed (from [`wecms-api/CLAUDE.md` § Architecture Rules](../../../CLAUDE.md)):

- New feature → new `domain/{module}/` package. JEX module code `comm` would suggest `domain/comm/contacts/`, but the parent CLAUDE.md normalises to **slug-based** package paths. The chosen path `domain/setting/contacts/` mirrors the existing settings group (`domain/setting/erptransactionhistory/`, `domain/setting/firmbankingsettings/`).
- Controller → service → repository → entity + dto. No cross-layer shortcuts.
- No promotion to `common/` (single domain consumer).
- DTOs are Java records.

## 2. REST surface

| Method | URI | Auth | Body | Returns |
|---|---|---|---|---|
| `GET` | `/api/v1/settings/contacts` | JWT | (none — query params) | `ApiResponse<ContactListResponse>` |
| `GET` | `/api/v1/settings/contacts/{trcoKey}` | JWT | (none) | `ApiResponse<ContactDetail>` |
| `POST` | `/api/v1/settings/contacts` | JWT | `ContactCreateRequest` | `ApiResponse<ContactDetail>` |
| `PUT` | `/api/v1/settings/contacts/{trcoKey}` | JWT | `ContactUpdateRequest` | `ApiResponse<ContactDetail>` |
| `DELETE` | `/api/v1/settings/contacts/{trcoKey}` | JWT | (none) | `ApiResponse<Void>` |
| `POST` | `/api/v1/settings/contacts/bulk-delete` | JWT | `BulkDeleteRequest` | `ApiResponse<Void>` |

All responses follow the global `{ status, data, timestamp?, path?, trace_id? }` envelope. Wire body uses lowercase snake_case via the global Jackson `SNAKE_CASE` strategy in [`JacksonConfig`](../../../src/main/java/com/kosign/wecms/config/JacksonConfig.java) — no per-field `@JsonProperty`.

### 2.1 List query params

| Param | Type | Required | Notes |
|---|---|---|---|
| `search` | `String` | no | Length ≤ 100. ILIKE on `TRCO_CMNM`, `TRCO_CMENM`, `HAND_TRCO_KEY`. |
| `trco_type` | `String` | no | `L` or `O`. Anything else → ignored (default ORDER BY only). |
| `page` | `int` | no, default 1 | 1-based per legacy WSVC. |
| `size` | `int` | no, default 15 | `@Min(1) @Max(100)`. |
| `sort_field` | `String` | no | View Setting FIELD_ID. Validated via `ContactSortField.parse(...)`. |
| `sort_direction` | `String` | no | `ASC` \| `DESC`. Validated via shared `SortDirection.parse(...)`. |

Controller stays thin: forwards raw strings to the service; the service does the parsing. Per [`wecms-api/CLAUDE.md` § Common table sort — Rules](../../../CLAUDE.md), no `@Pattern` on the sort params.

## 3. DTOs (wire-shape)

### 3.1 `ContactListResponse`

```java
public record ContactListResponse(
    List<ContactRow> rows,
    long totalCount
) {}
```

Wire: `{ "rows": [...], "total_count": 123 }`.

### 3.2 `ContactRow`

```java
public record ContactRow(
    String trcoKey,
    String handTrcoKey,
    String trcoCmnm,
    String trcoCmenm,
    String trcoTaxId,
    String trcoType,           // "L" | "O" (verbatim legacy code)
    List<AddressRow> addresses,
    List<AccountRow> accounts,
    List<OverseasRow> overseasBeneficiaries
) {}
```

### 3.3 `ContactDetail`

Same fields as `ContactRow` plus a `trcoStts` (`String`, may be null) for the edit form (so it can read the "Trashed" sentinel if the read endpoint ever surfaces deleted rows — currently filtered out).

### 3.4 `ContactCreateRequest`

```java
public record ContactCreateRequest(
    @NotBlank @Size(max = 20) @Pattern(regexp = "^[A-Za-z0-9_\\-.]+$") String handTrcoKey,
    @NotBlank @Size(max = 200) String trcoCmnm,
    @NotBlank @Size(max = 200) String trcoCmenm,
    @Size(max = 40)               String trcoTaxId,
    @NotBlank @Pattern(regexp = "^[LO]$") String trcoType,
    @Valid @Size(max = 50)        List<AddressOp> addresses,
    @Valid @Size(max = 50)        List<AccountOp> accounts,
    @Valid @Size(max = 50)        List<OverseasOp> overseasBeneficiaries
) {}
```

Validation rules mirror [comm_0184_002_c001_act.jsp:59-87](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0184_002_c001_act.jsp).

### 3.5 `ContactUpdateRequest`

Adds the delete arrays:

```java
public record ContactUpdateRequest(
    @NotBlank @Size(max = 20) String handTrcoKey,
    @NotBlank @Size(max = 200) String trcoCmnm,
    @NotBlank @Size(max = 200) String trcoCmenm,
    @Size(max = 40)               String trcoTaxId,
    @NotBlank @Pattern(regexp = "^[LO]$") String trcoType,
    @Pattern(regexp = "^[C]?$")   String trcoStts,                  // null or "C"
    @Valid @Size(max = 50)        List<AddressOp> addresses,
    @Valid @Size(max = 50)        List<AccountOp> accounts,
    @Valid @Size(max = 50)        List<AccountDeleteKey> accountsToDelete,
    @Valid @Size(max = 50)        List<OverseasOp> overseasBeneficiaries,
    @Valid @Size(max = 50)        List<OverseasDeleteKey> overseasToDelete
) {}
```

### 3.6 Sub-record records (with discriminator)

```java
public record AddressOp(
    @NotBlank @Pattern(regexp = "^(upsert|delete)$") String op,
    @NotBlank String trcoAddrKey,                       // "1" or "2" today; freeform later
    @Size(max = 200) String trcoBsunPostAdrs,
    Integer dispOrd,
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$") String issuDt,   // ISO date on the wire
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$") String dueDt
) {}

public record AccountOp(
    @NotBlank @Pattern(regexp = "^(upsert|delete)$") String op,
    @NotBlank String trcoAcctKey,
    @Size(max = 2000) String bankNm,
    @Size(max = 100)  String acctNo,
    @Size(max = 10)   String napasBankCd,
    @Size(max = 200)  String citadBankCd,
    @Size(max = 3)    String acctCcyCd,
    @Size(max = 150)  String fincMdaOwnerNm,
    @Size(max = 500)  String owacAddr,
    Integer dispOrd
) {}

public record OverseasOp(
    @NotBlank @Pattern(regexp = "^(upsert|delete)$") String op,
    @NotBlank String trcoOvrsKey,
    @Size(max = 200) String beneName,
    @Size(max = 100) String beneAcctNo,
    @Size(max = 50)  String bicCode,
    @Size(max = 200) String beneBankName,
    @Size(max = 20)  String beneBankCode,
    @Size(max = 500) String beneAddr
) {}

public record AccountDeleteKey(@NotBlank String trcoAcctKey) {}
public record OverseasDeleteKey(@NotBlank String trcoOvrsKey) {}
public record BulkDeleteRequest(@NotEmpty List<@NotBlank String> trcoKeys) {}
```

`@Valid` on the list fields cascades validation into the elements.

## 4. ContactsService — public methods

```java
@Service
@RequiredArgsConstructor
public class ContactsService {

    // List + pagination + sort + filter
    @Transactional(readOnly = true)
    public ContactListResponse list(
        String search, String trcoType,
        int page, int size,
        String sortField, String sortDirection
    );

    // Read one for edit pre-fill (rejects "wrong tenant" with the same NOT_FOUND envelope)
    @Transactional(readOnly = true)
    public ContactDetail getOne(String trcoKey);

    // Create — master + addresses + accounts (Local) | overseas (Overseas) + seed PGRO B/S rows
    @Transactional
    public ContactDetail create(ContactCreateRequest req);

    // Full update — discriminated sub-rows, single tx
    @Transactional
    public ContactDetail update(String trcoKey, ContactUpdateRequest req);

    // Soft-delete (single) — sets TRCO_STTS='C'; runs the WBE_CONTACTS_IN_USE guard
    @Transactional
    public void softDelete(String trcoKey);

    // Soft-delete (bulk) — same guard runs per key; failed keys collected into one envelope
    @Transactional
    public void bulkSoftDelete(List<String> trcoKeys);
}
```

### 4.1 List flow

1. Parse `sortField` via `ContactSortField.parse(...)` → `Optional<ContactSortField>`. Unknown → `Optional.empty()`.
2. Parse `sortDirection` via `SortDirection.parse(...)` → `null` on blank / unknown.
3. Build `Sort`:
   - `Sort primary = sortField.map(f -> Sort.by(direction == DESC ? Sort.Order.desc(f.entityProperty()) : Sort.Order.asc(f.entityProperty()))).orElse(Sort.unsorted());`
   - `Sort.by(primary).and(DEFAULT_SORT)` — `DEFAULT_SORT = Sort.by(Sort.Order.desc("modDtm"), Sort.Order.desc("trcoKey"))`.
4. Build `Pageable` from `(page-1, size, sort)`.
5. Query: `Page<TrcoInfm> page = trcoInfmRepo.search(bizKey, search, trcoType, pageable)` — Spring Data `@Query` or `Specification` API.
6. Batch-fetch sub-rows for the page's trcoKey set:
   - `trcoAddrInfmRepo.findAllByBizKeyAndTrcoKeyIn(bizKey, keys)` → group by `trcoKey`.
   - Same for `trcoAcctInfoRepo`, `trcoOvrsInfmRepo`.
7. Resolve bank names via `BankCdResolver` (no new SQL function) — one lookup per `napasBankCd` / `citadBankCd`.
8. Project to `ContactRow` records; assemble `ContactListResponse(rows, page.getTotalElements())`.

### 4.2 Create flow

Mirrors [comm_0184_002_c001_act.jsp:188-246](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0184_002_c001_act.jsp).

1. `validator.validateCreate(req, bizKey)` — duplicate `HAND_TRCO_KEY` and `TRCO_TAX_ID` checks (§ 5).
2. `String trcoKey = trcoKeyGen.allocate(bizKey)` — Java-side, see § 7.
3. Persist `TrcoInfm` via JPA `save`. `REG_USER` = `TenantContext.currentUserId()`. `REG_DTM` / `MOD_DTM` set by `BaseEntity.@PrePersist`.
4. If `trcoType = 'O'`: persist `OverseasOp` rows where `op = upsert` → `TrcoOvrsInfm`.
5. If `trcoType = 'L'`: persist `AccountOp` rows where `op = upsert` → `TrcoAcctInfo`.
6. Persist `AddressOp` rows where `op = upsert` → `TrcoAddrInfm`. `issuDt` defaults to `LocalDate.now()` if null.
7. Seed `PgroBsacLnknInfm` group rows for `B` and `S` with `USER_ID = 'ALL'` — mirrors [comm_0184_002_c001_act.jsp:131-147](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0184_002_c001_act.jsp). Generation rule for `PGRO_KEY` follows the same Java-side approach in `PgroBsacLnknInfm` if needed; otherwise composite PK with `(BIZ_KEY, TRCO_KEY, GRP_CD, USER_ID)` already qualifies it.
8. Return `getOne(trcoKey)`.

### 4.3 Update flow

Mirrors [comm_0184_005_u001_act.jsp:337-396](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0184_005_u001_act.jsp).

1. Load existing `TrcoInfm` by `(bizKey, trcoKey)`; missing → `BusinessException("WBE_CONTACTS_NOT_FOUND")`.
2. `validator.validateUpdate(req, existing, bizKey)`:
   - `HAND_TRCO_KEY` changed → duplicate check.
   - `TRCO_TAX_ID` changed → duplicate check.
   - Transition to `TRCO_STTS = 'C'` → `acctTrnsRcvPayInfmRepo.existsByBizKeyAndTrcoKey(bizKey, trcoKey)` — if true, throw `BusinessException("WBE_CONTACTS_IN_USE")` (legacy `WBE1190`).
   - Per-account `(BANK_NM, ACCT_NO)` duplicate check on `accounts` payload.
   - Per-overseas `(BENE_ACCT_NO, BIC_CODE)` duplicate check on `overseasBeneficiaries` payload.
   - For Local (`trcoType='L'`): at least one account in `accounts` with `op='upsert'` AND non-blank `BANK_NM` (mirrors legacy line 103–115). Empty → `WBE_CONTACTS_LOCAL_BANK_REQUIRED`.
   - For each `AccountOp` with `op='upsert'` AND non-blank `NAPAS_BANK_CD` / `CITAD_BANK_CD` AND not trashing (`trcoStts != 'C'`): require `BANK_NM` + `ACCT_NO` + `TRCO_ACCT_KEY`.
3. Mutate `TrcoInfm` master via `existing.copyFrom(req)` style (Java setters). `MOD_USER` / `MOD_DTM` via `@PreUpdate`.
4. Addresses pass: for each `AddressOp`, dispatch upsert or delete on `TrcoAddrInfmRepository`.
5. Bank-accounts pass: same on `TrcoAcctInfoRepository`. Also process `accountsToDelete[]` (mirrors legacy `REC4`).
6. Overseas pass: same on `TrcoOvrsInfmRepository`. Also process `overseasToDelete[]` (mirrors legacy `REC6`).
7. **Skipped vs. legacy**: file upload (`FILE_DATA`), file-deletes (`REC3`), responsible-person sub-records (no legacy `REC` for it in this WSVC).
8. Return `getOne(trcoKey)`.

### 4.4 Soft-delete flow

```java
public void softDelete(String trcoKey) {
    String bizKey = TenantContext.currentBizKey();
    var entity = trcoInfmRepo.findByBizKeyAndTrcoKey(bizKey, trcoKey)
        .orElseThrow(() -> new BusinessException("WBE_CONTACTS_NOT_FOUND"));
    if (acctTrnsRcvPayInfmRepo.existsByBizKeyAndTrcoKey(bizKey, trcoKey)) {
        throw new BusinessException("WBE_CONTACTS_IN_USE");
    }
    entity.setTrcoStts("C");                  // @PreUpdate stamps MOD_USER / MOD_DTM
}
```

Bulk variant loops and accumulates failures. Per the rule "Don't add error handling, fallbacks, or validation for scenarios that can't happen" in the workspace `CLAUDE.md`, bulk-delete with one bad key fails the whole transaction with the first offending `BusinessException` — there's no "partial success" envelope. PO already accepts this (matches legacy `_u001` which transactionally fails the whole bulk).

## 5. ContactsValidator

```java
@Component
@RequiredArgsConstructor
public class ContactsValidator {

    public void validateCreate(ContactCreateRequest req, String bizKey) {
        if (trcoInfmRepo.existsByBizKeyAndHandTrcoKey(bizKey, req.handTrcoKey()))
            throw new BusinessException("WBE_CONTACTS_HAND_KEY_DUP");
        if (req.trcoTaxId() != null
            && trcoInfmRepo.existsByBizKeyAndTrcoTaxId(bizKey, req.trcoTaxId()))
            throw new BusinessException("WBE_CONTACTS_TAX_ID_DUP");
        checkAccountDuplicates(req.accounts());
        checkOverseasDuplicates(req.overseasBeneficiaries());
        if ("L".equals(req.trcoType()) && !hasAtLeastOneUpsertAccount(req.accounts()))
            throw new BusinessException("WBE_CONTACTS_LOCAL_BANK_REQUIRED");
    }

    public void validateUpdate(ContactUpdateRequest req, TrcoInfm existing, String bizKey) {
        if (!req.handTrcoKey().equals(existing.getHandTrcoKey())
            && trcoInfmRepo.existsByBizKeyAndHandTrcoKey(bizKey, req.handTrcoKey()))
            throw new BusinessException("WBE_CONTACTS_HAND_KEY_DUP");
        // …same shape for tax id, account-dup, overseas-dup, local-bank
        // in-use guard handled in service (needs separate repo)
    }

    private void checkAccountDuplicates(List<AccountOp> accounts) {
        Set<String> seen = new HashSet<>();
        for (var a : accounts) {
            if (!"upsert".equals(a.op())) continue;
            if (a.bankNm() == null || a.acctNo() == null) continue;
            String k = a.bankNm() + "|" + a.acctNo();
            if (!seen.add(k)) throw new BusinessException("WBE_CONTACTS_ACCT_DUP");
        }
    }
    // checkOverseasDuplicates similar
}
```

Single-responsibility: just business-rule + duplicate checks. Bean-Validation on records handles shape (size, regex, required).

## 6. ContactSortField (common-sort whitelist)

```java
public enum ContactSortField implements SortableField {
    HAND_TRCO_KEY("handTrcoKey"),
    TRCO_CMNM("trcoCmnm"),
    TRCO_TYPE("trcoType"),
    TRCO_TAX_ID("trcoTaxId");
    // BANK_NM and TRCO_BSUN_POST_ADRS deliberately omitted (sub-table — SORT_YN='N' seeded)

    private final String entityProperty;

    ContactSortField(String p) { this.entityProperty = p; }

    @Override public String entityProperty() { return entityProperty; }

    public static Optional<ContactSortField> parse(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        try { return Optional.of(valueOf(raw)); }
        catch (IllegalArgumentException ex) { return Optional.empty(); }
    }
}
```

Mirror the canonical implementation in [`AccountSortField`](../../../src/main/java/com/kosign/wecms/domain/banking/service/AccountSortField.java). View Setting seed for `contacts` ships `SORT_YN='N'` on `BANK_NM` and `TRCO_BSUN_POST_ADRS` — the View Setting + whitelist agreement rule in [`wecms-api/CLAUDE.md` § Common table sort](../../../CLAUDE.md) requires this so the popover never shows an inert sort glyph.

## 7. TrcoKeyGeneratorService — TRCO_KEY allocation

Implementation choice depends on **OQ-4** in [`docs/refactor-wecms/features/contacts.md` § 14](../../features/settings/contacts.md). Two options to pick from at MR-time:

**Option A — Postgres `SEQUENCE`** (preferred if legacy `wb_get_next_val('trco_infm','trco_key',…)` returns a plain monotonic value):

```java
@Service
public class TrcoKeyGeneratorService {
    private final EntityManager em;
    public String allocate(String bizKey) {
        var n = ((Number) em.createNativeQuery("SELECT nextval('sq_trco_key')").getSingleResult()).longValue();
        return String.format("%015d", n);   // legacy width — confirm with audit
    }
}
```

The new `sq_trco_key` sequence lands in the schema Flyway migration (V{n} contacts_schema).

**Option B — composite `{biz_key}_{date}_{seq}`** (if the legacy generator encodes meaning):

```java
@Service
public class TrcoKeyGeneratorService {
    private final EntityManager em;
    public String allocate(String bizKey) {
        var today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        var n = ((Number) em.createNativeQuery(
            "SELECT nextval('sq_trco_key_' || ?1)").setParameter(1, bizKey).getSingleResult()).longValue();
        return bizKey + "_" + today + "_" + String.format("%06d", n);
    }
}
```

Either way, the refactor does **not** add a `wb_get_next_val` branch for `trco_infm` (DB Function / Legacy Function Policy in [`wecms-api/CLAUDE.md`](../../../CLAUDE.md)).

## 8. ContactsController

```java
@RestController
@RequestMapping("/api/v1/settings/contacts")
@RequiredArgsConstructor
public class ContactsController {

    private final ContactsService service;

    @GetMapping
    public ApiResponse<ContactListResponse> list(
        @RequestParam(required = false) @Size(max = 100) String search,
        @RequestParam(required = false, name = "trco_type") String trcoType,
        @RequestParam(defaultValue = "1") @Min(1) int page,
        @RequestParam(defaultValue = "15") @Min(1) @Max(100) int size,
        @RequestParam(required = false, name = "sort_field") String sortField,
        @RequestParam(required = false, name = "sort_direction") String sortDirection
    ) {
        return ApiResponse.ok(service.list(search, trcoType, page, size, sortField, sortDirection));
    }

    @GetMapping("/{trcoKey}")
    public ApiResponse<ContactDetail> getOne(@PathVariable @NotBlank String trcoKey) {
        return ApiResponse.ok(service.getOne(trcoKey));
    }

    @PostMapping
    public ApiResponse<ContactDetail> create(@Valid @RequestBody ContactCreateRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @PutMapping("/{trcoKey}")
    public ApiResponse<ContactDetail> update(
        @PathVariable @NotBlank String trcoKey,
        @Valid @RequestBody ContactUpdateRequest req
    ) {
        return ApiResponse.ok(service.update(trcoKey, req));
    }

    @DeleteMapping("/{trcoKey}")
    public ApiResponse<Void> delete(@PathVariable @NotBlank String trcoKey) {
        service.softDelete(trcoKey);
        return ApiResponse.ok(null);
    }

    @PostMapping("/bulk-delete")
    public ApiResponse<Void> bulkDelete(@Valid @RequestBody BulkDeleteRequest req) {
        service.bulkSoftDelete(req.trcoKeys());
        return ApiResponse.ok(null);
    }
}
```

The controller stays thin: parse, delegate, wrap. No business logic. `@Validated` on the class (or on `BulkDeleteRequest`) drives the path-variable / query-param Bean-Validation.

## 9. Entities

Sprint subset mapping rule applies — entities map only what the refactor reads / writes. The DB schema stays column-for-column equivalent to DEV (`@Column` annotations cover the mapped subset; unmapped DEV columns stay in the table and are ignored by Hibernate per `validate` mode).

### 9.1 `TrcoInfm` (subset)

```java
@Entity
@Table(name = "TRCO_INFM")
@IdClass(TrcoInfmId.class)
@Getter @Setter @NoArgsConstructor
public class TrcoInfm extends BaseEntity {                  // BaseEntity → REG_ID / REG_DTM / MOD_ID / MOD_DTM
    @Id @Column(name = "BIZ_KEY",  length = 20)  private String bizKey;
    @Id @Column(name = "TRCO_KEY", length = 20)  private String trcoKey;

    @Column(name = "HAND_TRCO_KEY", length = 20) private String handTrcoKey;
    @Column(name = "TRCO_CMNM",     length = 200) private String trcoCmnm;
    @Column(name = "TRCO_CMENM",    length = 200) private String trcoCmenm;
    @Column(name = "TRCO_TAX_ID",   length = 40)  private String trcoTaxId;
    @Column(name = "TRCO_TYPE",     length = 1)   private String trcoType;
    @Column(name = "TRCO_STTS",     length = 1)   private String trcoStts;
    @Column(name = "TRCO_NICK_NM",  length = 100) private String trcoNickNm;
    @Column(name = "TRCO_INQ_YN",   length = 1)   private String trcoInqYn;   // see OQ-3
}
```

`TrcoInfmId`:

```java
public class TrcoInfmId implements Serializable {
    private String bizKey;
    private String trcoKey;
    // equals + hashCode + ctors
}
```

### 9.2 `TrcoAddrInfm` (subset)

```java
@Entity @Table(name = "TRCO_ADDR_INFM") @IdClass(TrcoAddrInfmId.class)
public class TrcoAddrInfm extends BaseEntity {
    @Id @Column(name = "BIZ_KEY",       length = 20) private String bizKey;
    @Id @Column(name = "TRCO_KEY",      length = 20) private String trcoKey;
    @Id @Column(name = "TRCO_ADDR_KEY", length = 20) private String trcoAddrKey;
    @Column(name = "TRCO_BSUN_POST_ADRS", length = 200) private String trcoBsunPostAdrs;
    @Column(name = "DISP_ORD")           private Integer dispOrd;
    @Column(name = "ISSU_DT")            private LocalDate issuDt;
    @Column(name = "DUE_DT")             private LocalDate dueDt;
}
```

### 9.3 `TrcoAcctInfo` (subset)

```java
@Entity @Table(name = "TRCO_ACCT_INFO") @IdClass(TrcoAcctInfoId.class)
public class TrcoAcctInfo extends BaseEntity {
    @Id @Column(name = "BIZ_KEY",       length = 20) private String bizKey;
    @Id @Column(name = "TRCO_KEY",      length = 20) private String trcoKey;
    @Id @Column(name = "TRCO_ACCT_KEY", length = 20) private String trcoAcctKey;
    @Column(name = "BANK_NM",            length = 2000) private String bankNm;
    @Column(name = "ACCT_NO",            length = 100)  private String acctNo;
    @Column(name = "NAPAS_BANK_CD",      length = 10)   private String napasBankCd;
    @Column(name = "CITAD_BANK_CD",      length = 200)  private String citadBankCd;
    @Column(name = "ACCT_CCY_CD",        length = 3)    private String acctCcyCd;
    @Column(name = "FINC_MDA_OWNER_NM",  length = 150)  private String fincMdaOwnerNm;
    @Column(name = "OWAC_ADDR",          length = 500)  private String owacAddr;
    @Column(name = "DISP_ORD")           private Integer dispOrd;
}
```

### 9.4 `TrcoOvrsInfm` (subset)

```java
@Entity @Table(name = "TRCO_OVRS_INFM") @IdClass(TrcoOvrsInfmId.class)
public class TrcoOvrsInfm extends BaseEntity {
    @Id @Column(name = "BIZ_KEY",       length = 20) private String bizKey;
    @Id @Column(name = "TRCO_KEY",      length = 20) private String trcoKey;
    @Id @Column(name = "TRCO_OVRS_KEY", length = 20) private String trcoOvrsKey;
    @Column(name = "BENE_NAME",         length = 200) private String beneName;
    @Column(name = "BENE_ACCT_NO",      length = 100) private String beneAcctNo;
    @Column(name = "BIC_CODE",          length = 50)  private String bicCode;
    @Column(name = "BENE_BANK_NAME",    length = 200) private String beneBankName;
    @Column(name = "BENE_BANK_CODE",    length = 20)  private String beneBankCode;
    @Column(name = "BENE_ADDR",         length = 500) private String beneAddr;
}
```

`BaseEntity` provides `REG_ID` / `REG_DTM` / `MOD_ID` / `MOD_DTM` plus `@PrePersist` / `@PreUpdate` hooks that pull from `TenantContext.currentUserId()`.

## 10. Repositories

```java
public interface TrcoInfmRepository extends JpaRepository<TrcoInfm, TrcoInfmId> {

    Optional<TrcoInfm> findByBizKeyAndTrcoKey(String bizKey, String trcoKey);

    boolean existsByBizKeyAndHandTrcoKey(String bizKey, String handTrcoKey);

    boolean existsByBizKeyAndTrcoTaxId(String bizKey, String trcoTaxId);

    @Query("""
        SELECT t FROM TrcoInfm t
        WHERE t.bizKey = :bizKey
          AND t.handTrcoKey IS NOT NULL
          AND (t.trcoStts IS NULL OR t.trcoStts <> 'C')
          AND (:trcoType IS NULL OR t.trcoType = :trcoType)
          AND (:search IS NULL OR
               LOWER(t.trcoCmnm)      LIKE LOWER(CONCAT('%', :search, '%')) OR
               LOWER(t.trcoCmenm)     LIKE LOWER(CONCAT('%', :search, '%')) OR
               LOWER(t.handTrcoKey)   LIKE LOWER(CONCAT('%', :search, '%')))
        """)
    Page<TrcoInfm> search(
        @Param("bizKey") String bizKey,
        @Param("search") String search,
        @Param("trcoType") String trcoType,
        Pageable pageable
    );
}

public interface TrcoAddrInfmRepository extends JpaRepository<TrcoAddrInfm, TrcoAddrInfmId> {
    List<TrcoAddrInfm> findAllByBizKeyAndTrcoKeyIn(String bizKey, Collection<String> trcoKeys);
    List<TrcoAddrInfm> findAllByBizKeyAndTrcoKey(String bizKey, String trcoKey);
    long deleteByBizKeyAndTrcoKeyAndTrcoAddrKey(String bizKey, String trcoKey, String trcoAddrKey);
}

public interface TrcoAcctInfoRepository extends JpaRepository<TrcoAcctInfo, TrcoAcctInfoId> {
    List<TrcoAcctInfo> findAllByBizKeyAndTrcoKeyIn(String bizKey, Collection<String> trcoKeys);
    List<TrcoAcctInfo> findAllByBizKeyAndTrcoKey(String bizKey, String trcoKey);
    long deleteByBizKeyAndTrcoKeyAndTrcoAcctKey(String bizKey, String trcoKey, String trcoAcctKey);
}

public interface TrcoOvrsInfmRepository extends JpaRepository<TrcoOvrsInfm, TrcoOvrsInfmId> {
    List<TrcoOvrsInfm> findAllByBizKeyAndTrcoKeyIn(String bizKey, Collection<String> trcoKeys);
    List<TrcoOvrsInfm> findAllByBizKeyAndTrcoKey(String bizKey, String trcoKey);
    long deleteByBizKeyAndTrcoKeyAndTrcoOvrsKey(String bizKey, String trcoKey, String trcoOvrsKey);
}

// Extended interface — already exists in the banking domain. If absent, create with this single method.
public interface AcctTrnsRcvPayInfmRepository extends JpaRepository<AcctTrnsRcvPayInfm, AcctTrnsRcvPayInfmId> {
    boolean existsByBizKeyAndTrcoKey(String bizKey, String trcoKey);
}
```

All tenant-scoped methods constrain on `BIZ_KEY` — never a `%` wildcard. Spring Data derived-method names per the rule "prefer derived method names over `@Query` until needed" — the list query is the one place a custom `@Query` is justified (multi-column ILIKE + null-tolerant filters).

## 11. Exception → response mapping

No new handler additions. Every `BusinessException` thrown by the service rolls through the existing `GlobalExceptionHandler` (`@RestControllerAdvice`) and returns HTTP 200 with the standard envelope. The new codes need only be added to `messages.properties`:

```properties
# messages.properties
WBE_CONTACTS_HAND_KEY_DUP=Contact code already in use.
WBE_CONTACTS_TAX_ID_DUP=Tax ID already in use.
WBE_CONTACTS_ACCT_DUP=Duplicate bank account in the same contact.
WBE_CONTACTS_BENE_DUP=Duplicate overseas beneficiary in the same contact.
WBE_CONTACTS_NOT_FOUND=Contact not found.
WBE_CONTACTS_LOCAL_BANK_REQUIRED=Local contacts must have at least one bank account.
WBE_CONTACTS_IN_USE=Cannot trash a contact still referenced by transactions.
```

`messages_en.properties` mirrors. `messages_ko.properties` / `messages_vi.properties` ship verified translations from Sonny; KM is omitted.

## 12. Audit hook

Every mutation runs through the existing global audit-aspect (the one that writes to `AUDIT_LOGS_INFM` for `firm-banking-settings` and `erp-transaction-history`). For Contacts:

- `WSVC_ID` written as the slug `contacts` — per the audit-WSVC override locked 2026-05-09 in [`wecms-api/CLAUDE.md`](../../../CLAUDE.md).
- Audit row carries the path (`/api/v1/settings/contacts/{trcoKey}`), HTTP method, status code, user, biz_key, trace_id.

If the existing aspect requires a per-package enable-list, register `com.kosign.wecms.domain.setting.contacts.controller.*` in the same MR.

## 13. Transaction boundaries

- `list(...)` / `getOne(...)` — `@Transactional(readOnly = true)`.
- `create(...)` / `update(...)` / `softDelete(...)` / `bulkSoftDelete(...)` — `@Transactional` (default propagation `REQUIRED`, default isolation).
- No `REQUIRES_NEW` — the audit insert lives at the aspect layer outside the service tx, mirroring the existing pattern.
- Repository `delete*` calls inside service methods participate in the outer tx — no explicit `flush`.

## 14. Tenant isolation rules

- Every read query carries `BIZ_KEY = TenantContext.currentBizKey()`.
- Every write entity sets `BIZ_KEY` from `TenantContext` — never from the request body.
- The `BulkDeleteRequest.trcoKeys[]` only lists `TRCO_KEY`s; the service combines them with the current `BIZ_KEY` for the soft-delete query.
- `softDelete(...)` and `update(...)` both load by `(bizKey, trcoKey)` first → `WBE_CONTACTS_NOT_FOUND` if the row isn't in the tenant. This deliberately merges "not found" and "wrong tenant" into one envelope so a malicious client can't probe other tenants' key spaces. Same pattern as the ERP Transaction History service ([`wecms-api/CLAUDE.md` § Feature Status](../../../CLAUDE.md)).

## 15. Reuse manifest (Spring side)

Concrete files in `wecms-api/` that this feature consumes:

| Reuse target | Path |
|---|---|
| Envelope wrapper | [`common/response/ApiResponse`](../../../src/main/java/com/kosign/wecms/common/response/ApiResponse.java) |
| Business exception | [`common/exception/BusinessException`](../../../src/main/java/com/kosign/wecms/common/exception/BusinessException.java) |
| Global handler | [`common/exception/GlobalExceptionHandler`](../../../src/main/java/com/kosign/wecms/common/exception/GlobalExceptionHandler.java) |
| Audit columns + `@PrePersist` / `@PreUpdate` | [`common/entity/BaseEntity`](../../../src/main/java/com/kosign/wecms/common/entity/BaseEntity.java) |
| Tenant context | [`common/tenant/TenantContext`](../../../src/main/java/com/kosign/wecms/common/tenant/TenantContext.java) |
| Message resolver | [`common/i18n/MessageResolver`](../../../src/main/java/com/kosign/wecms/common/i18n/MessageResolver.java) |
| Sort direction enum | [`common/web/SortDirection`](../../../src/main/java/com/kosign/wecms/common/web/SortDirection.java) |
| Sortable interface | [`common/web/SortableField`](../../../src/main/java/com/kosign/wecms/common/web/SortableField.java) |
| Bank-name resolver | `BankCdResolver` (banking domain) — already exposed for cross-domain reuse |
| View Setting feature | [`domain/setting/` ScreenOption controller / service / DTO / tables](../../../src/main/java/com/kosign/wecms/domain/setting) — seed-only adopter, no API extension |
| JWT filter / SecurityConfig | [`config/SecurityConfig`](../../../src/main/java/com/kosign/wecms/config/SecurityConfig.java), [`common/security/JwtAuthenticationFilter`](../../../src/main/java/com/kosign/wecms/common/security/JwtAuthenticationFilter.java) |
| Trace id | [`common/exception/TraceIdFilter`](../../../src/main/java/com/kosign/wecms/common/exception/TraceIdFilter.java) — already wired |

Reused JPA entities (if not yet defined, the contacts MR adds them):

- `PgroBsacLnknInfm` — used to seed the `B` + `S` group rows on contact create. Composite PK `(BIZ_KEY, TRCO_KEY, GRP_CD, USER_ID)`.
- `AcctTrnsRcvPayInfm` — used for the in-use guard. Should already exist (banking domain, V21 schema).

## 16. Test plan

- `@WebMvcTest(ContactsController.class)` with `MockMvc` — happy paths for each endpoint + each error envelope:
  - 400 / `WBE_VALIDATION` on missing required fields.
  - 200 / `WBE_CONTACTS_HAND_KEY_DUP` on duplicate `hand_trco_key`.
  - 200 / `WBE_CONTACTS_LOCAL_BANK_REQUIRED` on local contact with no accounts.
  - 200 / `WBE_CONTACTS_NOT_FOUND` on a key from another tenant (asserts tenant scoping).
- `@DataJpaTest` on each repository — exercises the derived methods + the `search(...)` `@Query`.
- `ContactsServiceIntegrationTest` (`@SpringBootTest` with `test` profile + Flyway) — end-to-end against an embedded / Testcontainers Postgres. Validates the single-transaction edit flow (master + nested upsert + nested delete) and the in-use guard.
- Sort whitelist coverage — supply unknown `sort_field`, assert default ORDER BY applied; supply a known one, assert resolved entity property reached the query.

Test plan detail lives in [`docs/refactor-wecms/testing/`](../../testing/) once the feature is in flight; this section is the scaffold.

## 17. Local-only constraints (R&D)

- No external integrations. The Contacts feature is pure CRUD against the local Postgres.
- No mail / SMS / file storage side-effects.
- No batch / scheduler. `comm_0184_007` Excel-import + cron-style backfills are explicitly deferred.
- Bound to `localhost:5434/wecms_dev` per [`docs/refactor-wecms/database/`](../database) wiring.
