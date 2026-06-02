# Content-Validation Report — `wecms-api/docs`

**Date:** 2026-06-02 · **Owner:** Sonny / Arthur (via Claude) · **System:** Refactor (`wecms-api/`) · **Status:** ✅ Validation complete · corrections applied where source-of-truth was CERTAIN; uncertain items flagged below.

A content-accuracy pass (no structural changes) over the refactor backend docs, verifying the §8 items from `doc-consolidation-report-2026-06-02.md` plus a broad stale-status/API/parity sweep. Each finding was verified against the **actual Spring source** (`wecms-api/src/main/java`), the **Next.js source** (`wecms-web/`), and **legacy JEX source** (`WECMS DEV/`) as applicable, with file:line evidence.

---

## 1. Verdicts on the §8 items

| # | Item | Verdict | Action |
|---|---|---|---|
| 1 | Contacts marked "not implemented" | **CONFIRMED STALE** — shipped (`ContactsController` 18 handlers, `ContactsService`, `ContactsFileService`, `ContactsImportService`) | Status flipped + banner; deep design-doc §§ flagged |
| 2 | Retired `validateCell` endpoint refs | **CONFIRMED STALE** — no `validateCell` / `VasAccountCellValidationRequest` / `/excel/cell/validate` in code (the hub + audit F2 already correct) | Removed from `business-rules/vas-account.md` + `testing/vas-account.md` |
| 3 | Own Account Transfer status contradiction | **CONFIRMED STALE** — only the `own-account-transfer.md` header ("design only") was wrong; README + transfer-history "Implemented" are accurate | Header flipped to Implemented |
| 4 | `fnsh_0200_001` Smart Organizer vs Income Statement | **RESOLVED — CERTAIN** — JEX `WSVC.fnsh_0200_001.xml` title = "Transactions History >> Smart Organizer"; the migration doc was wrong; no Income Statement / Trial Balance screen exists in JEX | Corrected the migration-doc bullet |
| 5 | Other stale status / API / parity | **MULTIPLE CONFIRMED** — see §2/§3 | Applied the certain ones; flagged the rest |

---

## 2. Corrections applied (CERTAIN — verified against shipped code)

| File | Correction | Evidence |
|---|---|---|
| `features/settings/contacts.md` | Status → Implemented + authority banner | `domain/setting/contacts/` full tree; `ContactsController.java:69` |
| `features/settings/approval-line.md` | Status → Implemented (Working) | `ApprovalLineController.java:55` |
| `features/settings/purpose.md` | Status → Implemented (backend) | `PurposeSettingsController.java:65` |
| `features/settings/transaction-history.md` | Status → Implemented (backend) | `ErpTrnsHistoryController.java:60` |
| `features/settings/users.md` | Status → Implemented; "no endpoints yet" sentence corrected | `UserManagementController.java:71` |
| `features/banking/own-account-transfer/own-account-transfer.md` | Header status → Implemented | `OwnAccountTransferController.java:66-271`, FE `app/(dashboard)/banking/own-account-transfer/page.tsx` |
| `references/business-rules/vas-account.md` | Removed `VasAccountCellValidationRequest` (§4), `validateCell` (§6), §7 row; left retirement note | grep `validateCell` → 0 hits in `src/main/java`; `VasAccountController.java` 8 endpoints, none cell-validate |
| `testing/vas-account.md` | Removed 2 `validateCell` test rows (the 3rd prose ref did not exist) | same |
| `references/migration/chart-of-accounts.md` | `fnsh_0200_001` bullet rewritten to Smart Organizer / Organizer Setting | `WSVC.fnsh_0200_001.xml:6`, `fnsh_0200_001_r001_act.jsp:138`, `IDO.ACCT_TRNS_HSTR_R028.xml` |
| `features/banking/reports/daily-financial.md` | `/api/v1/reports/...` → `/api/v1/banking/reports/...` (4 spots) | `ReportController.java:16` `@RequestMapping("/api/v1/banking/reports")`; no `/api/v1/reports/**` controller exists |
| `features/banking/reports/daily-cash.md` | §4 export leftover → `/api/v1/banking/reports/daily-cash-report/export` | `DailyCashReportController.java:29,46` |
| `features/settings/firm-banking-history.md` | §10 Excel export moved from Out-of-scope → Implemented | `FirmBankingHistoryController.java:76` `@PostMapping("/export")` |
| `features/banking/own-account-transfer/transfer-history.md` | Migration ref `V181` → `V196`; dropped "source file not found" | actual file `V196__transfer_history_helper_index.sql` on disk |
| `references/business-rules/contacts-design.md`, `references/database/contacts-schema.md`, `references/migration/contacts-mapping.md` | Added "Implemented — code is authoritative" banner; fixed `contacts-schema.md` PK `(BIZ_KEY, TRCO_KEY)` → `(TRCO_KEY)` | `ContactsService.java:334` (`nextTrcoKey`), `LegacyFunctionRepository.java:81-83,251-262,224-226`, `ContactsFileService.java`, `V112` schema (PK on `TRCO_KEY` alone) |

Post-edit internal link scan: **0 broken internal links.** No source code touched.

**Key resolved design-decision corrections** (captured in the contacts banners): TRCO_KEY → `wb_get_next_val('trco_infm','trco_key')` via `LegacyFunctionRepository.nextTrcoKey` (NOT a Java `TrcoKeyGeneratorService`); `TRCO_INFO_FILE` IS in scope (attachments shipped); bank-name resolution via ported `wb_get_napas_bank_nm_or_citad_bank_nm` (NOT `BankCdResolver`). OQ-4 and OQ-7 are resolved in code.

---

## 3. Flagged for manual review (NOT auto-edited — source-of-truth uncertain or out of scope)

1. **Flyway migration-number drift — HIGH PRIORITY, needs a human call.** Docs *and* `wecms-api/CLAUDE.md` cite Contacts migrations as **V84–V89**, but the files on disk are **V112 / V113 / V139 / V140** (and their internal header comments mislabel themselves as V85/V98/V99 — copy-paste artifacts). VAS cited V77–V79 (those match disk). Transfer-history helper index is **V196** (its own SQL header mislabels it `V181`). This is either a stale CLAUDE.md migration table or a renumber that never propagated. I did **not** rewrite any V-numbers in docs (except the one verified `V181→V196` doc reference). Reconcile the source of truth before correcting V-numbers across docs.

2. **`wecms-api/CLAUDE.md` "Feature Status" table is itself stale.** It lists ~7 Working features and "everything else — Not started", yet 30+ controllers exist on disk (firm-banking-settings/history, bank-account, users, login-credentials, preferences, environment-settings, vas-account, purpose-settings, banking accounts/reports/transfers, payment/*, einvoice, operation). This is the **project-instructions file** (not under `docs/`), and updating it is a large, separate edit — flagged for a deliberate pass, not auto-changed.

3. **`login-credentials.md` OQ-1/2/3 — backend exists, frontend status unverified.** Backend endpoints DO exist (`LoginCredentialsController` `POST /gdt`, `PUT /gdt/{seqNo}`, `GET /{type}/{seqNo}/audit-logs`, `POST /{type}/{seqNo}/reveal`), contradicting the docs' "no endpoint exists" framing. But the OQs also concern **frontend wiring** and **decrypt-reveal security policy** — rewording correctly requires confirming current frontend state. Recommend: reword each OQ to "backend implemented; frontend/policy pending" after a frontend check.

4. **Add-on Service docs — backend exists, "not wired on either side" overstated.** `operation-status-inquiry.md:8`, `sales-e-invoices.md`, `expenses-e-invoices.md` claim endpoints are not implemented, but `OperationStatusController` (inquire/update/export) and `EInvoiceController` (`GET /einvoices` with server-side page/size/sort + `POST /einvoices/export`) are shipped. The **frontend** wiring may genuinely be pending — verify before rewording. **Also a code bug to file separately:** `sales-e-invoices.md` notes `resolveDocType` maps EXPENSE→`'E'` but should be `'B'` — that's a Spring-code defect, not a doc issue.

5. **Deep contacts design-doc section rewrites (deferred).** Banners were added, but the stale technical bodies remain: `contacts-design.md` §7 (TrcoKeyGeneratorService Option A/B), `contacts-schema.md` §6/§7, `contacts-mapping.md` §9 (out-of-scope rows for TRCO_INFO_FILE / bank-resolver / key-gen), plus secondary drifts (default sort `handTrcoKey ASC, trcoKey DESC`; method `existsByBizKeyAndRcvPayTrcoKey`; return type `ContactRow` not `ContactDetail`; `TrcoInfmId` IdClass field set vs single-column PK). These are CERTAIN-but-deep; a targeted rewrite pass is safer than blanket auto-edits.

6. **`vas-account` business-rules §4 secondary drift.** DTO names `VasAccountImportRequest/Result` should be `VasAccountExcelImportRequest/Result`; field `rec` → `rows`; `cont` max-200 not stated in §4. Secondary to the validateCell fix.

7. **`contacts.md:7` JEX counterpart "Needs Confirmation".** Confirm `docs/jex/features/contacts.md` exists before clearing the marker.

8. **Cross-tree (not actioned, prior known item):** `references/migration/screen-mapping/page-mapping.md` is a frontend routing concern (conceptually `wecms-web/docs`) — unchanged, pending PO call.

---

## 4. Confirmed ACCURATE (verified, no change)

`auth/login-auth.md` (login/logout/me), `common/view-setting.md` (screen-options paths), `settings/change-password.md`, `settings/firm-banking-settings.md`, `settings/environment-settings.md`, `settings/preferences.md`, `settings/vas-account.md` (hub — correctly shows the removed endpoint struck through), `settings/bank-account.md`, `banking/current-savings-vnd/account-inquiry.md` + `transaction-history.md`, `banking/reports/cash-flow.md`, and the `references/audits/vas-account.md` F2 finding — all match shipped controllers/source.

---

## 5. Follow-up corrections (2026-06-02, second pass)

### 5.1 Migration-number drift — reconciled
Several features were authored with **draft Flyway numbers** that were **renumbered on commit** (version collisions); docs + CLAUDE.md kept the draft numbers. Confirmed against the files on disk (filename = the version Flyway actually applies):

| Drafted | Real applied file | Feature |
|---|---|---|
| V84 | **V112**`__contacts_schema.sql` | Contacts schema |
| V85 | **V113**`__wb_get_next_val_trco_infm.sql` | TRCO_KEY branch |
| V86 | **V114**`__wb_get_napas_bank_nm_or_citad_bank_nm.sql` | bank-name fn |
| V87 | **V115**`__contacts_demo_seed.sql` | Contacts demo seed |
| V88 | **V154**`__contacts_view_setting_seed.sql` (re-applied **V187**) | Contacts View Setting |
| V89 | **V117**`__contacts_disp_ord_to_integer.sql` | disp_ord hotfix |
| V98 | **V139**`__trco_info_file_schema.sql` | TRCO_INFO_FILE schema |
| V99 | **V140**`__wb_get_next_val_trco_info_file.sql` | file_id branch |
| V84 | **V127**`__bank_exch_rate_infm_table.sql` | Exchange-rate table |
| V87 | **V130**`__bank_exch_rate_infm_seed.sql` | Exchange-rate seed |
| V86 | **V100**`__bank_cert_info.sql` | NXISAS bank-cert |
| V87 | **V101**`__nxsias_module_tran_log_his.sql` | NXISAS tran-log |

**Fixed in:** `wecms-api/CLAUDE.md` (migration table, Surviving-legacy-functions table, Contacts feature-status row, TRCO_INFO_FILE scope-override note — corrected from the wrong "V94+V95" to V139+V140); `exchange-rate-inquiry.md` (V84/V87→V127/V130); `nxisas-vcb-spike.md` (V86/V87→V100/V101, "source file not found" removed); cross-refs in `chart-of-accounts.md` (db + business-rules), `erd/settings-erd.md`, `transfer-history.md` (×3 docs). OQ-DOC-3 annotated (bank_cert/nxsias found as V100/V101) — **not closed**.

**Reported, NOT edited (source files — `--Vxx` header comments are stale, recommend dev fix):** V112(hdr V84), V113(V85), V114(V86), V115(V87), V117(V89), V139(V98), V140(V99), V154(V88), V187(hdr "V169, renumbered from V127"), V127(V84), V130(V87). NB: V100/V101 headers are **correct** — only the nxisas doc was stale there.

**Flagged for manual review:** `references/business-rules/internal-transfer.md` "V85 / Phase A" mentions — ambiguous (internal-transfer schema is V131); not edited. Archived docs (`archive/**`) left untouched.

### 5.2 CLAUDE.md Feature Status table — rebuilt
The prior "Everything else — Not started" line was inaccurate: **42 controllers / ~60 services / ~50 test files** exist. Rebuilt the table conservatively from the `domain/**/controller|service/` + `src/test/` tree, with a status legend (*Implemented* = backend+frontend evidenced; *Backend implemented* = controller+service present, frontend not verified; *Partial*; *Unknown*; *Not implemented*). Backend-only rows are explicitly **not** claimed full-stack. Genuinely absent (no controller): `domain/member`, dashboard, notifications, menu/permission resolution, refresh-token/logout-revocation, auto-lock.

### 5.3 Frontend verification (login-credentials + add-on) — all wired
Verified against `wecms-web` on disk (2026-06-02). All three were **more complete than the docs claimed**:
- **Login Credentials** — GDT create/update, audit-logs, and reveal all call live endpoints (no mock). OQ-1/2/3 marked RESOLVED.
- **Sales / Expenses e-Invoices** — server-side keyword/page/size/sort + XLSX export wired. Residual: invoice-**status** filter still client-side (backend IR-4 gap); the `EInvoiceService.resolveDocType EXPENSE→'E'` (should be `'B'`) is a **backend code bug** — frontend has no such mapping.
- **Operation Status** — Inquire / Update / Export wired to live endpoints; only the inquire-dialog **Save/persist** is a stub (no backend upsert endpoint).

Feature docs (`login-credentials.md`, `sales-e-invoices.md`, `expenses-e-invoices.md`, `operation-status-inquiry.md`) and the three CLAUDE.md rows updated to **Implemented** with the residual caveats noted.

### 5.4 Open code bugs to file (not doc issues)
- `EInvoiceService.resolveDocType` maps EXPENSE→`'E'`; docs/parity expect `'B'`.
- Operation-status inquire-dialog **Save** has no backend upsert endpoint.
- Stale `--Vxx` header comments inside the renumbered migration `.sql` files (§5.1).
- Post-cleanup internal-link scan: **0 broken internal links.**
