# Final Implementation Report — Vendor Payment › Scheduled Payment › Excel Upload / Refresh

- **Feature:** Payment → Vendor Payment → Scheduled Payment → Excel Upload / Refresh
- **System pair:** Legacy JEX (`WECMS DEV/`) ⇄ Refactor (`wecms-api/`, `wecms-web/`)
- **Date:** 2026-06-29
- **Status:** ✅ Implementation complete — 5 approved gaps fixed, re-audited, and verified
- **Source audit:** [vendor-payment-excel-upload-audit.md](../audits/vendor-payment-excel-upload-audit.md)
- **Note:** Documentation only — no code changed in producing this report.

---

## 1. Executive Summary

- **Audit completed.** The full upload/refresh flow was traced end-to-end on both sides: legacy (`pymn_0001_001` list, `pymn_0001_002` popup, `pymn_0001_002_r001` parse, `pymn_0001_002_c001` save, `comm_1001_001` withdrawal picker) and refactor (`VendorPaymentController` → `VendorPaymentService` / `VendorPaymentExcelParser`; Web `VendorPaymentUploadDialog` → shared `PaymentExcelUploadDialog`/`ExcelUploadDialog`). The audit identified 8 gaps (2 HIGH, 4 MEDIUM, 2 LOW).
- **Legacy parity completed** for the five approved gaps — **GAP-1, GAP-3, GAP-4, GAP-6, GAP-8**. Each fix was made only after re-reading the exact legacy source lines, and each mirrors the legacy behavior (or the established sibling-module pattern where the refactor architecture differs from the JSP layout).
- **Scope of implementation.** Backend (`wecms-api/`), confined to Vendor Payment plus the intentionally-shared `comm_1001_001` withdrawal-account component; **plus a web-only GAP-7a change in `wecms-web/`** (2026-06-29). No API-contract, database, or migration changes. **All approved technical parity gaps are complete**, and GAP-7a (preview Recipient/Contact-Name columns) is now implemented web-only. Following the §14 legacy re-audit, the only remaining item is **GAP-2** (conditional — bank-inquiry socket + PO policy); GAP-5 and GAP-7b were found to be **not gaps**.

**Headline result:** Vendor Payment Excel Upload/Refresh is at legacy parity for all five approved gaps; full backend suite green (1412 tests, 0 failures); no contract/schema impact.

---

## 2. Implemented Gaps

### GAP-1 — `CITAD_BANK_CD` never persisted (HIGH)

- **Root cause:** Vendor reused the Internal-Transfer line shape where the CITAD bank-code resolver was deferred — the parser hard-coded `citadBankCd = null`, so every CITAD detail row saved `CITAD_BANK_CD = NULL`.
- **Legacy behavior:** `PymnHelper.uploadPaymentExcel:1360-1364,1468` sets `CITAD_BANK_CD = "000"` for NAPAS rows and the 3-char CITAD code for CITAD rows.
- **Refactor behavior before:** `SCDUL_PYMN_SUMR_DETL.CITAD_BANK_CD` persisted as `NULL` for all rows → dispatch / data-migration drift on CITAD payments.
- **Final implementation:** `VendorPaymentService.resolveDetailBankCodes(antDivCd, typedBankCd)` returns `"000"` (NAPAS) / the 3-char code (CITAD); wired into the `doCreateDraft` detail loop via `detl.setCitadBankCd(bankCodes.citadBankCd())`.
- **Files changed:** `VendorPaymentService.java`.
- **Test coverage:** Covered indirectly by compile + the live save flow; no dedicated createDraft unit test (see §3-style observation in the audit §12.6 — recommended follow-up).

### GAP-8 — CITAD `TAR_BANK_CD` stored as 3-char raw, not 6-digit (HIGH)

- **Root cause:** Same deferred CITAD resolver as GAP-1 — the parser stored the user-typed 3-char CITAD code on `TAR_BANK_CD`.
- **Legacy behavior:** `PymnHelper.getBankCode:1232-1257` resolves CITAD `TAR_BANK_CD` to the **6-digit head-office `BANK_CD`** from `TB_FIRM_BANK_CODE` where `ant_div_cd='CITAD' AND citad_bank_cd=<3char> AND bank_stas='1' AND bank_branch_div='1'`; NAPAS keeps the typed 6-digit code.
- **Refactor behavior before:** `TAR_BANK_CD` = raw 3-char CITAD input → format mismatch vs legacy → dispatch/migration parity risk.
- **Final implementation:** `resolveDetailBankCodes` resolves the head-office `BANK_CD` via the new repository finder; a single `citad_bank_cd` maps to many branch rows, so the `bank_branch_div='1'` predicate (DB-confirmed) is required to pick the head office. NAPAS persists the typed code as-is.
- **Files changed:** `VendorPaymentService.java`, `TbFirmBankCodeRepository.java` (new derived finder `findFirstByAntDivCdAndCitadBankCdAndBankStasAndBankBranchDiv`).
- **Test coverage:** As GAP-1 (indirect); dedicated createDraft test recommended.

### GAP-3 — VCB-USD cross-bank restriction absent (LOW → reachable after GAP-6)

- **Root cause:** The Vendor parser never carried the VCB-USD guard its sibling `InternalTransferExcelParser` already had. Previously unreachable (VCB wasn't a selectable out-bank); GAP-6 re-added VCB to the picker, making it reachable.
- **Legacy behavior:** `PymnHelper.java:118` `VCB_BANK_CD = "970436"`; `:1460-1465` rejects when `OUT_BANK_CD == VCB && TRNF_CCY_CD == "USD" && resolved TAR_BANK_CD != VCB` ("VCB does not support USD transfer to other banks.").
- **Refactor behavior before:** A VCB-out USD batch to a non-VCB target was accepted at upload (no guard).
- **Final implementation:** `VendorPaymentExcelParser` parse-time per-cell guard mirroring the sibling — precompute `withdrawalIsVcb = VCB_BANK_CD.equals(outBankCdNormalizer.normalize(bankCd))`, then per row `if (withdrawalIsVcb && "USD".equals(trnfCcyCd) && !VCB_BANK_CD.equals(resolvedBankCd))` → `FieldError(TRNF_CCY_CD, …)`. Surfaces in the upload preview; the dispatch-time `WBE2088` guard (`VendorDispatchExecutor`) remains the authoritative re-check. Verified non-inert: `napas_bank_info 11704004 → 970436`.
- **Files changed:** `VendorPaymentExcelParser.java`.
- **Test coverage:** ✅ New `VendorPaymentExcelParserTest` — 3 tests: VCB+USD+other-bank → flagged; VCB+USD+VCB → allowed; non-VCB withdrawal → unaffected. (3/3 pass.)

### GAP-4 — `WDRL_CCY_CD` set to transfer currency, not withdrawal-account currency (MEDIUM)

- **Root cause:** `doCreateDraft` set `WDRL_CCY_CD = req.ccyCd()` (the transfer currency); the create request carries a single `ccy_cd`.
- **Legacy behavior:** `WDRL_CCY_CD` = the selected withdrawal account's own currency — the `comm_1001_001` dropdown's `data-ccycd`, sourced from `ACCT_MSTR.CCY_CD`, persisted verbatim (independent of transfer currency).
- **Refactor behavior before:** `WDRL_CCY_CD` equalled the transfer currency — wrong whenever the account currency differed.
- **Final implementation:** `VendorPaymentService.resolveWithdrawalCcyCd(bizKey, bankCd, acctNo, fallback)` looks up `ACCT_MSTR.CCY_CD` by the selected `(BIZ_KEY, BANK_CD, ACCT_NO)`; falls back to the transfer currency only when the account row can't be resolved (column never left null). Server-side resolution avoids touching the shared `CreateScheduledTransferRequest` DTO or the web.
- **Files changed:** `VendorPaymentService.java`.
- **Test coverage:** Indirect (live flow); dedicated createDraft test recommended.

### GAP-6 — Withdrawal bank-set + `AUTH_TYPE` narrower than legacy (MEDIUM)

- **Root cause:** The refactor's parity Javadoc was wrong; the constant + grant filter were narrower than legacy (2 banks + V/W).
- **Legacy behavior (re-confirmed by direct read):** `comm_1001_001_act.jsp:31` → `bank_cd in ('11704088','11704020','11704004')` (3 banks; `11704004` = Vietcom Bank); `:51` → `AUTH_TYPE IN ('V','W','F')`.
- **Refactor behavior before:** `WITHDRAWAL_BANK_CDS = ('11704088','11704020')` + grant intersection on `V`/`W` only → VCB withdrawal accounts and `F`-granted accounts were hidden from the picker.
- **Final implementation:** added `11704004` to `WITHDRAWAL_BANK_CDS`, added `AUTH_TYPE_FILE = "F"` to the grant intersection, corrected the Javadoc. `comm_1001_001` is module-agnostic, so all five payment modules inherit the corrected list (intended parity).
- **Files changed:** `WithdrawalAccountConstants.java`, `WithdrawalAccountListService.java`.
- **Test coverage:** ✅ `WithdrawalAccountListServiceTest` updated — parity lock now asserts the 3-bank set + V/W/F intersection (adds a VCB account + an `F`-grant acceptance case; non-V/W/F grant still excluded).

---

## 3. Remaining Gaps (post §14 legacy re-audit)

The §14 legacy re-audit reclassified the originally-"deferred" gaps; **GAP-7a was then implemented (web-only, 2026-06-29 — audit §17).** GAP-5 and GAP-7b are not gaps. **The only remaining open item is GAP-2 (conditional).**

| Gap | Classification | Detail |
|---|---|---|
| **GAP-2** — NAPAS recipient-name verify + persist | 🔶 **Conditional gap (deferred)** | Legacy `ckRecName` runs a **live firm-banking bank-API inquiry** when `TB_FIRM_CONF` config exists (`PymnHelper.java:874-876,964-965`), persists the bank-returned `RCPT_NM` (`:1011`), and flags `WBE0001` on mismatch **and when config is missing** (`:1037-1038`). The refactor's `FirmBankingInquiryService.inquiryAccount(...)` returns empty until the bank socket (`WB_00000100`) is wired, so no verify runs and NAPAS `RCPT_NM` is null. **Conditional** on the socket + `TB_FIRM_CONF` data (unavailable in R&D). Pending the socket port + a PO policy decision (legacy's config-missing flag is likely undesirable for coexistence). Not in the approved scope of this work. |
| **GAP-5** — Partial upload of valid rows | ✅ **NOT A GAP** | Legacy **also blocks upload when `err_cnt > 0`** — the save click is gated at `pymn_0001_002.ts:145`; it never uploads a subset of valid rows while errors exist (the subset path at `:154-162` is commented out). The refactor's `uploadDisabled = errorCount > 0` is functionally equivalent. The earlier "legacy uploads valid rows only / completed tab" framing was incorrect (corrected in audit §14.3). No implementation required. |
| **GAP-7a** — Preview Recipient/Contact-Name columns | ✅ **FIXED 2026-06-29 (web-only)** | Added the two read-only preview columns (Recipient Name after Deposit No.; Contact Name after Contact Code) in `VendorPaymentUploadDialog.tsx`, reading `rcpt_nm` / `trco_cmnm` already on the wire (+ 2 i18n keys × 3 locales). No backend/API/parser/DB/template change. CITAD Recipient Name + all Contact Names populate now; NAPAS Recipient Name stays blank until GAP-2. `tsc --noEmit` clean; changed files lint-clean. See audit §17. |
| **GAP-7b** — 500-row cap | ✅ **NOT A GAP** | Legacy **also silently caps at 500 rows** (`PymnHelper.java:315-317,326`); the refactor's `MAX_ROWS=500` is exact parity. The earlier "refactor-only cap / silent truncation" framing was incorrect (corrected in audit §14.3). No implementation required. |

All three are documented in the audit §9/§10 and require the standard analyze → approve cycle before implementation.

---

## 4. Changed Files

### API (`wecms-api/` — main)

| File | Change | Gap(s) |
|---|---|---|
| `domain/payment/common/WithdrawalAccountConstants.java` | `WITHDRAWAL_BANK_CDS` → 3 banks (+VCB `11704004`); add `AUTH_TYPE_FILE="F"`; Javadoc corrected | GAP-6 |
| `domain/payment/common/WithdrawalAccountListService.java` | grant intersection accepts V/W/**F**; Javadoc G1/G2 | GAP-6 |
| `domain/setting/firmbankingsettings/repository/TbFirmBankCodeRepository.java` | add `findFirstByAntDivCdAndCitadBankCdAndBankStasAndBankBranchDiv` (CITAD head-office lookup) — additive only | GAP-1/8 |
| `domain/payment/vendorpayment/service/VendorPaymentService.java` | `resolveDetailBankCodes` (legacy `getBankCode` + `CITAD_BANK_CD`) wired into `doCreateDraft`; `resolveWithdrawalCcyCd` for `WDRL_CCY_CD`; 2 new fields (`firmBankCodeRepo`, `acctMstrRepository`) | GAP-1/8/4 |
| `domain/payment/vendorpayment/service/VendorPaymentExcelParser.java` | `VCB_BANK_CD="970436"` + `OutBankCdNormalizer` field; `withdrawalIsVcb` precompute; per-cell VCB-USD guard on `TRNF_CCY_CD` | GAP-3 |

### Web (`wecms-web/`)

- **None.** All fixes are backend-only / server-resolved. No component, wire type, or behavior change.

### Tests (`wecms-api/` — test)

| File | Change | Gap(s) |
|---|---|---|
| `domain/payment/common/WithdrawalAccountListServiceTest.java` | parity lock updated → 3-bank set + V/W/F (VCB account + F-grant case) | GAP-6 |
| `domain/payment/vendorpayment/service/VendorPaymentExcelParserTest.java` (new) | 3 tests: reject / allow / non-VCB unaffected | GAP-3 |

### Documentation (`wecms-api/docs/`)

| File | Change |
|---|---|
| `docs/audits/vendor-payment-excel-upload-audit.md` | §0 status, per-gap ✅ markers, §10/§10a done-vs-remaining + changed files, §12 final parity verification |
| `docs/reports/vendor-payment-excel-upload-implementation-report.md` (this file) | Final implementation report |

> **Not part of this work (present in the working tree):** a separate **Internal Transfer Excel-upload** changeset (`internaltransfer/dto/*`, `InternalTransferExcelParser`, `InternalTransferService`, `InternalTransferExcelParserTest`, new `InternalTransferCreateDraftTest`, `internal-transfer-excel-upload-audit.md`). It predates and is independent of this Vendor work; it should be committed/reviewed under its own scope.

---

## 5. Risk Assessment

| Area | Risk | Rationale |
|---|---|---|
| Vendor upload happy path (NAPAS / VND) | **Very low** | Behavior unchanged; covered by existing + new tests |
| Vendor CITAD save (GAP-1/8) | **Low** | Mirrors legacy `getBankCode`; head-office finder DB-verified; migration drift closed. Residual: no dedicated createDraft unit test |
| Cross-module withdrawal picker (GAP-6) | **Low** | Shared change, but legacy-faithful and **broader** (not narrower); full suite green. Recommend SIT confirmation that VCB / `F`-grant accounts surface as expected |
| VCB-USD guard (GAP-3) | **Low** | Two layers (parse preview + dispatch `WBE2088`); verified non-inert; UX-positive |
| API / DB | **None** | No contract, schema, or migration change from this work |

**Remaining technical risks:** none blocking. Two non-blocking follow-ups recommended: (1) add a dedicated `VendorPaymentService` createDraft unit test covering GAP-1/8/4 save-path resolution; (2) optional consolidation of the duplicated `VCB_BANK_CD="970436"` constant (keeping `TransactionService.VCB_BANK_CD="11704004"` separate — different semantic). Both are deferred, report-only.

---

## 6. Verification

- **Build result:** `./gradlew compileJava compileTestJava` → clean (no errors).
- **Test result:** `./gradlew test --rerun` → **BUILD SUCCESSFUL** — **196 test classes, 1412 tests, 0 failures, 0 errors** (20 skipped — pre-existing, unrelated). Parity locks exercised: `VendorPaymentExcelParserTest` (3/3), `WithdrawalAccountListServiceTest` (GAP-6), `InternalTransferExcelParserTest` (sibling, unaffected), full `vendorpayment.*` + `firmbankingsettings.*`.
- **API compatibility:** ✅ No contract change from this work — no controller / DTO / endpoint / wire-field added, removed, or renamed by the Vendor fixes. No `wecms-web/` change required.
- **Database compatibility:** ✅ No DDL, no Flyway migration, no `messages.properties` change. All target columns + lookup tables pre-exist in the baseline; `WBE2088` pre-existed.
- **Legacy parity verification:** ✅ All five gaps re-audited against the exact legacy source lines (`comm_1001_001_act.jsp:31,51`; `PymnHelper.java:118,1232-1257,1360-1364,1450-1451,1460-1465,1468`) and confirmed faithful. VCB guard verified live via DB (`napas_bank_info 11704004 → 970436`).

---

## 7. Ready for Review

| Stage | Ready? | Notes |
|---|---|---|
| **Git commit** | ✅ Yes | Vendor + shared `comm_1001_001` changes are self-contained and compile clean. Commit Vendor scope separately from the unrelated Internal Transfer changeset in the working tree. |
| **Pull Request** | ✅ Yes | Backend-only; no web/DB/migration. PR description should call out the intentional cross-module `comm_1001_001` (GAP-6) change and the two deferred non-blocking follow-ups. |
| **QA testing** | ✅ Yes | Suggested QA: CITAD vendor upload (verify `TAR_BANK_CD` 6-digit + `CITAD_BANK_CD`), VCB-out USD to other bank (rejected at preview) vs VCB→VCB (allowed), withdrawal picker shows VCB + `F`-grant accounts, `WDRL_CCY_CD` reflects the account currency. |
| **UAT** | ⚠️ Conditional | Ready for UAT for the **five implemented gaps**. Per the §14 re-audit, GAP-5 and GAP-7b are **not gaps**; the only open items are GAP-2 (conditional — bank-inquiry socket + PO policy) and GAP-7a (optional preview-UI) — confirm with the PO that UAT scope excludes them. General R&D→UAT constraints (host secrets, dispatch OFF) per the wecms-api security status apply at the program level, not to this feature specifically. |

**Overall:** **Vendor Payment Excel Upload / Refresh has legacy parity for all implemented technical behavior.** All approved technical parity gaps (GAP-1, GAP-3, GAP-4, GAP-6, GAP-8) are complete and verified; **GAP-7a (preview Recipient/Contact-Name columns) is now implemented web-only** (audit §17); GAP-5 and GAP-7b were confirmed **not gaps** by the §14 re-audit. The only remaining work is:

- **GAP-2** — conditional bank-inquiry integration + PO policy decision (blocked on the firm-banking socket / EFS / VAN config). NAPAS `rcpt_nm` (and thus the NAPAS Recipient Name preview cell) stays blank until GAP-2 is separately implemented/enabled.

Ready for commit, PR, and QA; UAT-ready for the implemented scope with GAP-2 noted as the single remaining item.
EOF
