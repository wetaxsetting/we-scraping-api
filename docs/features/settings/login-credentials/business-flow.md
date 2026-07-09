# Login Credentials — Business Flow (Backend)

End-to-end request flow for every Login Credentials operation. Source of truth: the implemented Spring
code in `com.kosign.wecms.domain.setting.logincredentials`
([LoginCredentialsController](../../../../src/main/java/com/kosign/wecms/domain/setting/logincredentials/controller/LoginCredentialsController.java)
→ [LoginCredentialsService](../../../../src/main/java/com/kosign/wecms/domain/setting/logincredentials/service/LoginCredentialsService.java)).

```
User (tenant admin)
      │
      ▼
Settings modal → Login Credentials panel
      │   (every request carries the JWT; @menuAuthz.canAccess('settings-core-login-credentials') gates the controller)
      ▼
LoginCredentialsController  ──►  LoginCredentialsService
      │                              │
      │                              ├─ TenantContext.currentBizKey() / currentUserId()  (tenant scope, never a param)
      │                              ├─ BankCertInfoRepository      (BANK_CERT_INFO — banking package)
      │                              ├─ GdtCertInfoRepository       (GDT_CERT_INFO)
      │                              ├─ BankCertCryptoService       (legacy pgcrypto AES-256-CBC encrypt/decrypt)
      │                              ├─ BankScrapingService         (Coocon/NXiSAS gateway — verify)
      │                              ├─ AccountRegistrationService  (ACCT_MSTR + ACCT_AUTH_INFO fan-out)
      │                              ├─ LegacyFunctionRepository    (wb_get_next_val SEQ_NO funnel)
      │                              ├─ AuditLogService             (Create/Update/Delete only)
      │                              └─ AuditLogEntryRepository     (audit-history read)
      ▼
ApiResponse<...>  (lowercase snake_case envelope)
```

---

## 1. List

```
GET /api/v1/login-credentials
  → service.list()  @Transactional(readOnly)
      bank: bankRepo.findByBizKeyOrderBySeqNoAsc(bizKey)  → group into TreeMap by BANK_CD
            resolveBankNames(...) via BankInfmRepository (BANK_CD → RPRS_BANK_NM, falls back to code)
            acctMstrRepo.findByBizKeyOrderByPrnSeqAscBankCdAscAcctNoAsc(bizKey)  → linked accounts per BANK_CD
            per group: accounts[] (one per credential row), modifiedBy/modifiedAt = latest MOD across the group
      gdt:  gdtRepo.findByBizKeyOrderBySeqNoAsc(bizKey)  → zero or one GDT group
  → LoginCredentialListResponse { items: LoginCredentialGroup[] }
```

- Mirrors legacy `IDO.BANK_CERT_INFO_R007.xml` (UNION of `BANK_CERT_INFO` + `GDT_CERT_INFO`, `json_agg`
  grouped by `BANK_CD`) — done in Java from two simple per-tenant queries instead of a 90-line native
  UNION.
- Linked accounts mirror `BANK_CERT_INFO_R005 REC_1` (keyed only on `(BIZ_KEY, BANK_CD)`, no
  `ACTV_YN` filter), so every credential under a bank surfaces the same account list.
- **No password ciphertext on the wire.** Each `LoginCredentialAccount` carries `seq_no`, `login_id`,
  `nxisas_gb_seq_no`, `login_type` only.

## 2. Bank picker + per-bank form info (Add flow support)

```
GET /api/v1/login-credentials/banks[?search=]
  → service.banks(search) → bankPickerRepo.list(search)
      native SQL over BANK_INFM_DETL: BANK_DETL_INFO_CD '9'='Y' (scraping-capable), '10'=name, '11'=img;
      excludes BANK_CD '11740099'; ILIKE name filter; ORDER BY name
  → BankPickerResponse { items: BankPickerRow[] }  (real BANK_CD values, e.g. "11704088")

GET /api/v1/login-credentials/banks/{bankCd}/form-info
  → service.formInfo(bankCd) → bankFormInfoRepo.formInfo(bankCd)
      native SQL over BANK_INFM_DETL grouped by SEQ_NO; conditional FILTER aggregates project
      codes 25=seq_nm · 12=logo_url · 13=id_label · 16=password_label · 19=confirm_label · 22=extra_label
  → BankFormInfoResponse { bank_cd, sub_types: BankFormInfoSubType[] }  (empty list for unknown banks)
```

Mirrors legacy `nxisas_acctList_r002` (banks) and `nxisas_getCertFormInfo` (form labels / radio
sub-types). The `{LANG_CODE}` placeholder in `logo_url` is substituted EN/KO/VI on the frontend.

## 3. Add a bank credential — two phases

### Phase A — verify-before-save (no DB write)

The frontend collects login id + password and calls **the banking scraping endpoints** (a separate
surface, `/api/v1/banking/scraping/*`) to run the Coocon `LOGIN` + `ACCT_LIST` dance. No
`BANK_CERT_INFO` row is created during the scrape. On `OTP_REQUIRED` / `CAPTCHA_REQUIRED` /
`QNA_REQUIRED` the frontend resumes via the same session; the picked accounts come back only on
`COMPLETED`.

### Phase B — atomic Save (credential + accounts in one transaction)

```
POST /api/v1/login-credentials/bank/register   body: BankCredentialRegisterRequest
  → service.createBankWithAccounts(req)  @Transactional
      1. reject empty accounts BEFORE any write → WBE_BANK_ACCOUNT_REGISTER_EMPTY
         (guarantees no orphan BANK_CERT_INFO for a no-account save)
      2. createBank(req.toCreateRequest())  — in-class, joins this tx:
           a. gbSeq = req.nxisasGbSeqNo ?? 0
           b. find-or-create dedup on (bizKey, bankCd, certInfoCon, nxisasGbSeqNo)  ← legacy fnsh_0101_001_c001:166-194
              present → reuse SEQ_NO, NO audit, password NOT overwritten
           c. else insertBankCredential(): SEQ_NO via legacyFunctions.nextBankCertSeqNo(bizKey);
              CERT_INFO_DETL_CON = crypto.encrypt(password); CON2 = extra; CON3 = loginType; NXISAS_GB_SEQ_NO = gbSeq
              saveAndFlush → DataIntegrityViolation race → re-read once, else retry MAX+1 once
           d. audit append (CREATE) only on a real insert
      3. accountRegistrationService.register(RegisterAccountsRequest(seqNo, accounts))
           → per account: INSERT new / UPDATE existing ACCT_MSTR; PRN_SEQ via wb_get_next_val (seed-once + Java increment)
           → ACCT_AUTH_INFO fan-out on INSERT only: VIEW for every active tenant user (USE_YN='Y' for registrant,
             'N' for others); WITHDRAWAL only for the 5 withdrawal-capable banks (WithdrawalBankPolicy)
  → CredentialCreateResponse { seq_no }
```

A registration failure rolls the whole transaction back, credential included. Observable rule: **no
saved account ⇒ no `BANK_CERT_INFO`** (legacy `fnsh_0101_001_c001` "all-on-Save" parity).

> `POST /api/v1/login-credentials/bank` (single-row create, no accounts) exists as the primitive
> `createBank` path — same dedup + audit; `bank/register` reuses it in-class.

## 4. Edit a bank credential — verify-update then PUT

```
POST /api/v1/login-credentials/bank/{seqNo}/verify-update   body: { password }
  → service.verifyUpdate(seqNo, newPassword)  @Transactional(readOnly)
      load BANK_CERT_INFO by (bizKey, seqNo) (or WBE2090)
      build VerifyCredentialsRequest from the STORED bank context + the typed NEW password
      bankScrapingService.verify(req)  → ScrapeSessionResponse   ← NEVER writes the DB
  → returns the scrape session; frontend persists only on COMPLETED

PUT /api/v1/login-credentials/bank/{seqNo}   body: BankCredentialUpdateRequest
  → service.updateBank(seqNo, req)  @Transactional
      1. all-null patch (loginId/password/extra/loginType/nxisasGbSeqNo) → WBE_VALIDATION
      2. load row (or WBE2090)
      3. if req.oldPassword != null: decryptStrict(stored) must equal it → else WBE2091
         (legacy comm_0218_004_u001_act.jsp:55-57 `decrypt(cert_info_detl_con) = ?` parity)
      4. apply non-null fields; password → crypto.encrypt; stamp MOD_ID/MOD_DTM
      5. audit append (UPDATE)
  → 200, data: null
```

## 5. GDT create / update

```
POST /api/v1/login-credentials/gdt   body: GdtCredentialRequest
  → service.createGdt(req)  @Transactional
      1. one-per-company guard: any existing GDT row → WBE_LOGIN_CREDENTIAL_GDT_EXISTS
         (legacy comm_0218_003_c001 WBE0003 parity)
      2. verifyGdtCredentials(loginId, password)  — flag-gated (gdtVerifyEnabled, default off):
           Coocon verify on pseudo-bank VIETNAM_GDT; non-COMPLETED → WBE_LOGIN_CREDENTIAL_GDT_VERIFY_FAILED
      3. SEQ_NO via legacyFunctions.nextGdtCertSeqNo(bizKey); CERT_INFO_DETL_CON = crypto.encrypt(password)
      4. save; audit append (CREATE)
  → CredentialCreateResponse { seq_no }

PUT /api/v1/login-credentials/gdt/{seqNo}   body: GdtCredentialUpdateRequest
  → service.updateGdt(seqNo, req)  @Transactional
      1. all-null patch (loginId/password/extra) → WBE_VALIDATION
      2. load row (or WBE2090)
      3. if loginId OR password changed → re-verify with effective values (stored password via decryptStrict)
      4. apply non-null fields; password → crypto.encrypt; stamp MOD_*; audit append (UPDATE)
  → 200, data: null

GET /api/v1/login-credentials/gdt/engine-config
  → service.gdtEngineConfig()  — code constants from GdtNxisasConfig (no DB):
      api_id=NTS_NTS001_00001 · scrapping_module=GLOBAL/VNM/PUBLIC_GDT · scrapping_class=COMMON · bank_cd=VIETNAM_GDT
  → GdtEngineConfigResponse   (drives the client-side GDT local engine login test)
```

## 6. Delete

```
DELETE /api/v1/login-credentials/{type}/{seqNo}     type ∈ {bank, gdt}
  → service.delete(type, seqNo)  @Transactional
      normalizeType(type) → BANK | GDT (else WBE2092)
      bank: bankRepo.findByBizKeyAndSeqNo (or WBE2090) → delete → audit (REF_NO=BANK_CERT_INFO)
      gdt:  gdtRepo.findByBizKeyAndSeqNo (or WBE2090) → delete → audit (REF_NO=GDT_CERT_INFO)
  → 200, data: null
```

**Accounts are NOT cascade-deleted.** A credential and the accounts it once authenticated are
independent — removing the credential means "I can no longer log into the bank online", not "delete my
accounts". `AccountService` then surfaces those accounts as "disconnected" (per-bank `certConnYn`
derivation) until the user re-registers a credential.

## 7. Reveal

```
POST /api/v1/login-credentials/{type}/{seqNo}/reveal   body: { user_password }
  → service.reveal(type, seqNo, userPassword)  @Transactional(readOnly)
      load Bank/GDT row by (bizKey, seqNo) (or WBE2090)
      storedPassword = crypto.decryptStrict(CERT_INFO_DETL_CON)
      if storedPassword == null OR != userPassword → WBE2091
         (legacy comm_0218_008_r001 `... AND decrypt(cert_info_detl_con) = ?` parity —
          confirms the CREDENTIAL's own password, NOT the WeCMS account password)
  → RevealResponse { login_id, password }   ← the ONLY path that returns the password
```

## 8. Audit logs

```
GET /api/v1/login-credentials/{type}/{seqNo}/audit-logs
  → service.auditLogs(type, seqNo)  @Transactional(readOnly)
      refNo = REF_NO_BANK | REF_NO_GDT; lang = TenantContext.currentLangCd() ?? EN
      auditLogReadRepo.findByBizKeyAndRefNoAndRefNo1AndWsvcIdInAndLangCdAndRefNo2IsNullOrderByRegDtmDesc(
          bizKey, refNo, seqNo, AUDIT_WSVC_IDS, lang)
      AUDIT_WSVC_IDS = { "login-credentials", "comm_0218_001", "comm_0218_004" }
        ← reads the refactor slug AND legacy page ids so the popup shows rows the live JEX system wrote
          on a shared DB; WRITES still use the slug only.
  → LoginCredentialAuditResponse { items: LoginCredentialAuditRow[] }  (latest-first, pre-localized)
```

---

## Tenant + audit invariants (every mutating path)

- **Tenant** comes only from `TenantContext.currentBizKey()` (signed JWT) — never a request
  body/param. `bizKey` is the first PK component of every query; the route carries no `bizKey`.
- **Audit** rows are written for Create / Update / Delete with `WSVC_ID = 'login-credentials'`,
  `REF_NO = 'BANK_CERT_INFO'` or `'GDT_CERT_INFO'`, `REF_NO1 = SEQ_NO`, activity key
  `audit.login_credentials.activity`, detail key `audit.login_credentials.{create,update,delete}.detail`.
  List / reveal / audit-logs / banks / form-info write **no** audit row. The shared `AuditLogService`
  fans out one row per supported locale and runs `@Transactional(REQUIRES_NEW)` so an audit failure
  doesn't roll back the business transaction.
- **SEQ_NO** is allocated only through the `wb_get_next_val` funnel (`LegacyFunctionRepository`),
  per-tenant MAX+1, never a Java-side scheme.
