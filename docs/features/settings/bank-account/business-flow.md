# Bank Account — Business Flow (Backend)

End-to-end request flow for every Bank Account operation. Source of truth: the implemented Spring code
under `com.kosign.wecms.domain.setting.bankaccount`. Legacy behaviour reference: the live JEX
`comm_0183_*` module (see [source-code-index.md](source-code-index.md)).

```
User (tenant admin)
      │
      ▼
Settings modal → Bank Account panel
      │   (every request carries the JWT; @menuAuthz.canAccess('settings-core-bank-account') gates the controller)
      ▼
SettingsBankAccountController  ──►  SettingsBankAccountService  /  BankAccountPermissionService
      │                                   │
      │                                   ├─ TenantContext.currentBizKey() / currentUserId() / currentIsAdmin()  (tenant + actor; never a param)
      │                                   ├─ AcctMstrRepository           (ACCT_MSTR — owned by domain/banking)
      │                                   ├─ AcctAuthInfoRepository        (ACCT_AUTH_INFO — permission rows)
      │                                   ├─ BusiVasAcctInfmRepository     (VAS FK validation on update)
      │                                   ├─ BankCdResolver                (BANK_CD → short bank name)
      │                                   ├─ UserInfmRepository / BusiAdmnInfmRepository / UserBusiConnInfmRepository  (roster, modifier names, admin set)
      │                                   ├─ AuditLogEntryRepository       (audit-logs popup read)
      │                                   └─ AuditLogService.append(...)    (Update / Register / permission change)
      ▼
ApiResponse<...>  (lowercase snake_case envelope)
```

---

## 1. List

```
GET /api/v1/settings/bank-account[?sortField=BANK&sortDirection=ASC]
  → service.list(sortField, sortDirection)
      1. bizKey = TenantContext.currentBizKey()
      2. sort  = buildSort(BankAccountSortField.parse(sortField), SortDirection.parse(sortDirection))
                 → fallback DEFAULT_SORT = (prnSeq ASC, bankCd ASC, acctNo ASC)  -- legacy comm_0183_001_r001 parity
      3. rows  = acctRepo.findByBizKeyAndActvYn(bizKey, "Y", sort)   -- ACTV_YN='Y' only (G11, legacy IDO.ACCT_MSTR_R012)
      4. resolveBankNames(rows)      → BankCdResolver.resolveBankNm (short name, never null)
      5. resolveModifierNames(rows)  → USER_INFM by MOD_ID  (legacy WB_GET_USER_NM(MOD_ID), global, not tenant-scoped)
      6. per row: resolvePermissionLabels(bizKey, row)
                 → composePermissionLabel for 'V' and 'W':
                      count = acctAuthRepo.countActiveByAccount(...)  (USE_YN='Y', admins excluded)
                      label = firstName  (count==1)  |  firstName + " + " + (count-1) + " more"
      7. toRow(...) → maturity EXPI_DT yyyyMMdd → ISO yyyy-MM-dd; vas_account_bank = "VAS_ACCT_NM(CCY)"
  → BankAccountListResponse { items: BankAccountRow[] }
```

- No server-side pagination — the panel renders the full list (legacy parity).
- The sort `FIELD_ID` is a View Setting key resolved through `BankAccountSortField`; unknown / blank
  falls back to `DEFAULT_SORT` (never echoes user input into SQL).

## 2. Update (one ACCT_MSTR row)

```
PATCH /api/v1/settings/bank-account/{bankCd}/{acctNo}   body: BankAccountUpdateRequest
  → @Valid (bean validation: @Size on nickname/description/accountTypeCd/maturityDate/vasAcctCd/vasAcctNm)
  → service.update(bankCd, acctNo, req)  @Transactional
      1. reject all-null body                                     → WBE_VALIDATION
      2. load row by (bizKey, bankCd, acctNo)                     (or WBE_BANK_ACCOUNT_NOT_FOUND)
      3. nickname / description: set when non-null
      4. accountTypeCd: AcctTypeCd.fromCode(...) must resolve     → WBE_BANK_ACCOUNT_INVALID_TYPE
      5. maturityDate: ISO yyyy-MM-dd → strip "-" → VARCHAR(8);
         empty = clear (saving → current); bad length            → WBE_BANK_ACCOUNT_INVALID_DATE
      6. VAS triple: seqNo<=0 → clear all three; positive →
         validate BUSI_VAS_ACCT_INFM (DEL_YN='N' + STTS='A')      → WBE_BANK_ACCOUNT_INVALID_VAS_ACCT
         then stamp the authoritative cd/nm from the DB row (wire cd/nm are echoes only)
      7. modId/modDtm stamped; acctRepo.save(row)
      8. auditLogService.append(UPDATE)
  → 200, data: null
```

## 3. Register (bulk insert from the Add-chain)

```
POST /api/v1/settings/bank-account/register   body: BankAccountRegisterRequest { bankCd, items[] }
  → @Valid (bankCd @NotBlank; items @NotEmpty; each item acctNo/currency/accountTypeCd @NotBlank)
  → service.register(req)  @Transactional
      for each item:
        1. typeCd = item.accountTypeCd or DEFAULT_ACCT_TYPE_CD ("02" Current); AcctTypeCd.fromCode(...) → WBE_BANK_ACCOUNT_INVALID_TYPE
        2. if (bizKey, bankCd, acctNo) already exists → skip silently (legacy parity, re-scrape safety)
        3. else INSERT AcctMstr: ACTV_YN='Y', HAND_REG_YN='N' (scraped), REG_*/MOD_* stamped
        4. auditLogService.append(CREATE)
  → BankAccountRegisterResponse { inserted, skipped }
```

Account PK `(BIZ_KEY, BANK_CD, ACCT_NO)` is written verbatim from the scrape — no `wb_get_next_val`.

## 4. Permissions — roster (read)

```
GET /api/v1/settings/bank-account/{bankCd}/{acctNo}/permissions?type=VIEW|WITHDRAWAL
  → permissionService.list(bankCd, acctNo, type)  @Transactional(readOnly)
      1. kind = BankAccountAuthType.fromWireName(type)   (FULL_ACCESS rejected on this endpoint → WBE_VALIDATION)
      2. load ACCT_MSTR row                              (or WBE_BANK_ACCOUNT_NOT_FOUND)
      3. activeUserIds = findAllByAccount(... kind.code()) where USE_YN='Y'
      4. roster = userBusiRepo.findTenantUsersForManagement(bizKey)   (every active tenant user)
      5. adminIds = BUSI_ADMN_INFM rows for the tenant
      6. per user: SKIP admins (legacy IDO.ACCT_AUTH_INFO_R004:80 ADMIN_YN != 'Y');
                   isSelected = activeUserIds.contains(userId);
                   isLocked = self && selected && !admin → lockReason "SELF"
      7. sort admin-first then by display name; totalSelected = count of selected
  → BankAccountPermissionResponse { kind, accountLabel, members[], totalSelected }
```

## 5. Permissions — replace (write)

```
PUT /api/v1/settings/bank-account/{bankCd}/{acctNo}/permissions   body: { kind, userIds[] }
  → permissionService.replace(bankCd, acctNo, req)  @Transactional
      1. kind = fromWireName(kind)        (FULL_ACCESS rejected → WBE_VALIDATION)
      2. ACCT_MSTR must exist             (or WBE_BANK_ACCOUNT_NOT_FOUND)
      3. WITHDRAWAL + non-empty want set + bank NOT in WithdrawalBankPolicy → WBE0452
      4. every want_id must be an active tenant link                       → WBE_USER_NOT_IN_TENANT
      5. non-admin caller removing themselves (currently granted)          → WBE_CANNOT_REVOKE_SELF
      6. GRANT phase: insert new rows (USE_YN='Y') or flip 'N'→'Y'
      7. REVOKE phase: soft-revoke active rows not in the want set (USE_YN='N')
      8. if nothing changed → return (no audit)
      9. auditLogService.append(permission.{view|withdrawal}.detail, args=[granted, revoked])
  → 200, data: null
```

Replace-semantics: the client sends the **full desired user-id set**; the service computes the
grant/revoke diff and writes a single audit event. Soft-revoke (`USE_YN='N'`) preserves history.

## 6. Audit logs (per-account popup)

```
GET /api/v1/settings/bank-account/{bankCd}/{acctNo}/audit-logs
  → service.auditLogs(bankCd, acctNo)  @Transactional(readOnly)
      lang = TenantContext.currentLangCd() ?? EN
      rows = auditLogReadRepo.findBy…RefNoIn(['ACCT_MSTR','ACCT_AUTH_INFO'])
                              …Wsvc IdIn(['bank-account','comm_0183_001'])   -- slug + legacy id (shared-DB coexistence)
                              …RefNo1=bankCd, RefNo2=acctNo, LangCd, OrderByRegDtmDesc
  → BankAccountAuditResponse { items: BankAccountAuditRow[] }   (empty list, never an error, on no history)
```

The popup **reads** both the refactor slug and the legacy page id so it surfaces rows written by the
live JEX system on a shared DB; **writes** always use the slug only.

---

## Tenant + audit invariants (every mutating path)

- **Tenant** comes only from `TenantContext.currentBizKey()` (signed JWT) — never a request body/param.
  `BIZ_KEY` is the leading column of every query.
- **Actor** (`MOD_ID` / audit `REG_ID`) comes from `TenantContext.currentUserId()`.
- **Audit** rows are written for Update (`audit.bank_account.update.detail`), Register
  (`audit.bank_account.create.detail`), and permission changes
  (`audit.bank_account.permission.{view,withdrawal}.detail`, with `(granted, revoked)` counts), all with
  `WSVC_ID='bank-account'`, `REF_NO='ACCT_MSTR'` (metadata) or `'ACCT_AUTH_INFO'` (permissions),
  `REF_NO1=bankCd`, `REF_NO2=acctNo`. Reads write **no** audit row.
- See [functional-specification.md §Audit](functional-specification.md) for the column map.
