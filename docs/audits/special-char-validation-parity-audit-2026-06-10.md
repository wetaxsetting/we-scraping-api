# Special-Character Validation Parity Audit — Payment Uploads (JEX Legacy → Spring/Next.js Refactor)

> _**Historical one-off audit.** A point-in-time record of a completed verification / remediation — findings are preserved unchanged. For the feature’s current state see its `features/.../refactor-gap-analysis.md`; for context see the [audits index](README.md)._

**System:** Refactor (wecms-api / wecms-web) audited against JEX legacy (source of truth)
**Date:** 2026-06-10
**Author:** Special-character validation parity audit (Claude)
**Status:** Audit complete — 3 migration gaps + 1 open verification item (see `docs/bug-reports/`)
**Scope:** Excel-upload special-character validation across all 5 payment types (Internal Transfer, Vendor, Expense, Payroll, Overseas) and the 4 configured banks (VCB, BIDV, Woori VN, Shinhan VN).

---

## Framing — legacy is the baseline

This audit treats **JEX legacy behavior as the authoritative baseline.** The refactor's goal for this surface is **legacy-compatible behavior** so that migrated data and payment processing behave identically — not modernization or improvement.

- Any refactor behavior that differs from legacy is a **migration gap** unless it was explicitly approved as an intentional design change. No such approval is recorded for the divergences below.
- The purpose is to **preserve** legacy behavior, including transforms legacy applies before/after special-character validation.
- Findings are source-grounded with file paths and line numbers. Where a fact could not be proven from source/runtime, it is marked **Needs Confirmation** / **Open Verification Item**.

**Audit only.** No application source code, tests, or schema were changed in producing this document.

---

## 1. Executive summary

The **validation engine itself is at parity**: both systems validate special characters via a database-driven, per-(bank × payment-type × field) blocked-character set checked by substring membership — **no regex, no hardcoded whitelist/blacklist in code** on either side.

- Legacy: `SpecialCharValidator.validate(...)` → `fieldValue.indexOf(blockedChar) >= 0` — `WECMS DEV/WECMS/src/wabooks/validator/SpecialCharValidator.java:73-141`
- Refactor: `specialCharValidator.validate(...)` → `blocked.contains(c)` char-loop — `wecms-api/src/main/java/com/kosign/wecms/domain/payment/validation/SpecialCharValidator.java:67-94`

These are functionally equivalent. **The risk is not the validator** — it is the **transforms legacy applies around the validator** that the refactor does not replicate, plus one field-coverage divergence on Internal Transfer.

### Findings

| ID | Finding | Type | Severity (legacy-baseline) |
|---|---|---|---|
| **F1** | Refactor does not apply legacy Vietnamese tone-stripping (`replaceTone`) — all 5 payment types | Migration gap | High |
| **F2** | Refactor does not strip embedded newlines (`\r`/`\n`) that legacy removes from every cell | Migration gap | Medium |
| **F4** | Refactor validates Internal Transfer recipient name; legacy does not (and refactor drops the tone-strip) | Migration gap | Medium |
| **OV-1** | `BLOCKED_CHARS_RULE` runtime data in the refactor DB could not be verified (DB unreachable) | Open verification item | Contingent (P0 if rules absent) |

> **F3 (informational, not a gap):** NBSP normalization (`StringUtils.sanitize`) is **not** invoked on the legacy Excel-parse path, so its absence in the refactor is **not** a divergence on this path. Marked Needs Confirmation for any deeper/global save filter.

---

## 2. Complete validation path (traced, both systems)

| Stage | Legacy (JEX) | Refactor (Spring) |
|---|---|---|
| Excel template | `WeCMS_*_upload_*.xlsx` (EN/KO/VI headers) | `/public/templates/*` (same headers — `InternalTransferExcelParser.java:116-133`) |
| Frontend validation | none (server-authoritative) | none — file posted to parse endpoint; preview paints backend errors |
| Upload/parse API | `pymn_0001/0003/0005/0007/0009_*_act.jsp` | `*Controller` `POST …/uploads/parse` (+ create) |
| Cell read | `DatImpWizHelper.getCellValue` → `ExcelUtils.getCell` — **strips `\r\n`** (`ExcelUtils.java:144`; `DatImpWizHelper.java:1437-1440`) | `cellString` → `DataFormatter.formatCellValue(cell).trim()` (`InternalTransferExcelParser.java:537-548`) — trims ends only |
| **Vietnamese tone strip** | **`replaceTone`** on validated fields before validation & storage (see §4) | **absent — 0 occurrences in `wecms-api/src`** |
| Service validation (special char) | `SpecialCharValidator.validate(...)` | `specialCharValidator.validate(...)` |
| Rule lookup | `BLOCKED_CHARS_RULE` via `BlockedCharRuleDAO.findActiveRules` (`BlockedCharRuleDAO.java:75-105`) | same table via `BlockedCharsRuleRepository.findActiveRules` (`BlockedCharsRuleRepository.java:21-27`) |
| DB store | `recItem` → `SCDUL_PYMN_SUMR_DETL` (tone-stripped value) | `ParsedTransferLine` → `SCDUL_PYMN_SUMR_DETL` (raw value) |
| Transfer file / bank API | legacy builds the bank file from the stored (tone-stripped) value | **out of scope** — refactor has no bank integration yet (R&D, local-only) |

---

## 3. The validator engine — parity confirmed

- **Mechanism (both):** raw character set (NOT regex), substring containment. Empty/blank value → passes (required-field check is separate).
- **Active-rule filter — functional match:**
  - Legacy: `status = 'Y' AND eff_fr_dt <= today AND (eff_to_dt IS NULL OR eff_to_dt >= today)` (`BlockedCharRuleDAO.java:84-89`).
  - Refactor: `r.status = 'Y' AND (r.effFrDt IS NULL OR r.effFrDt <= :asOfDate) AND (r.effToDt IS NULL OR r.effToDt >= :asOfDate)` (`BlockedCharsRuleRepository.java:21-27`).
  - **Subtle divergence (edge, no live impact):** legacy `eff_fr_dt <= today` **excludes** a row with NULL `EFF_FR_DT`; refactor's `effFrDt IS NULL OR …` treats it as **active**. All 39 current rows carry a non-null `EFF_FR_DT`, so no live effect today; latent if a NULL-start rule is ever added.
- **Cache:** refactor caches the active set for 60 s (`SpecialCharValidator.java:47,104-115`); legacy queries per call. A rule edit takes ≤ 60 s to take effect in the refactor. Negligible.
- **Timezone:** legacy `current_date` (DB) vs refactor `LocalDate.now(Asia/Ho_Chi_Minh)` — both VN. Negligible.

---

## 4. Character-transformation inventory (payment-upload path)

| Transformation | Legacy (all 5 types) | Refactor (all 5 types) | Diverges? |
|---|---|---|---|
| **Vietnamese tone removal** (`replaceTone`) | **YES** — every validated field, before validation & storage. Sites: Vendor/Expense `PymnHelper.java:446,527,980`; Internal `:789` (remarks only); Payroll `pymn_0005_002_r001_act.jsp:306,345,571,610`; Overseas `pymn_0007_003_r001_act.jsp:170,192,367`. Map: `StringUtils.java:68-88` | **NO** — absent everywhere | ❌ **F1** |
| Newline removal `\r\n` | **YES** — `ExcelUtils.getCell:144` on every cell | **NO** — `.trim()` only; internal `\n`/`\r` survive | ❌ **F2** |
| Trimming (ends) | partial (`checkValidStringInMax`, cell-title `.trim()`) | YES (`formatCellValue(cell).trim()` + `emptyToNull().trim()`) | ~equiv |
| Uppercase conversion | relies on input | currency `trnfCcyIn.trim().toUpperCase()` (e.g. `VendorPaymentExcelParser.java:506`) | minor (refactor upper-cases currency code) |
| Tab removal `\t` | NO (only `\r\n` stripped) | NO | ✅ both keep |
| Unicode normalization (NFC/NFD) | NO | NO | ✅ both none |
| NBSP normalization (`sanitize`) | **NO** on the parse path (`StringUtils.sanitize` not called in `PymnHelper`/payroll/overseas JSPs) | NO | ✅ both none (Needs Confirmation re: deeper save layer) |
| Character replacement (`&`→` VA `, per-product strip) | **disabled** — commented out (`PymnHelper.java:533-535,548-557,992-1006`, issue #42761) | NO | ✅ both none |
| Whitespace normalization (`\s+`→` `) | **disabled** — commented out with the above | NO | ✅ both none |

---

## 5. Data-persistence impact — the 5 value stages

**Field: RECIPIENT_NAME / RCPT_NM — input `Nguyễn Văn Đức`** (NAPAS bank, no diacritic-blocking rule)

| Stage | Legacy | Refactor |
|---|---|---|
| 1. Original Excel value | `Nguyễn Văn Đức` | `Nguyễn Văn Đức` |
| 2. After preprocessing | `Nguyen Van Duc` (`replaceTone`) | `Nguyễn Văn Đức` (no transform) |
| 3. Value into validation | `Nguyen Van Duc` | `Nguyễn Văn Đức` |
| 4. Stored in DB (`SCDUL_PYMN_SUMR_DETL.RCPT_NM`) | `Nguyen Van Duc` | `Nguyễn Văn Đức` |
| 5. Transfer-file value | `Nguyen Van Duc` (ASCII → bank file) | **N/A today**; would be `Nguyễn Văn Đức` |

**Field: REMARKS / TRAN_USAG — input `Thanh toán⏎lương T6`** (embedded newline)

| Stage | Legacy | Refactor |
|---|---|---|
| 1. Original | `Thanh toán⏎lương T6` | `Thanh toán⏎lương T6` |
| 2. Preprocessing | `Thanh toanluong T6` (`\n` stripped, then `replaceTone`) | `Thanh toán⏎lương T6` (unchanged) |
| 3. Into validation | `Thanh toanluong T6` | `Thanh toán⏎lương T6` |
| 4. Stored DB | `Thanh toanluong T6` | `Thanh toán⏎lương T6` |
| 5. Transfer file | `Thanh toanluong T6` | N/A; would retain newline + diacritics |

---

## 6. Upload-outcome comparison

Because the rule data is identical (§8), the **pass/fail decision is identical in every case except F4**. The other divergences are in **stored / transmitted content**.

| Scenario | Exists? | Where |
|---|---|---|
| Legacy accepts, **Refactor rejects** | **YES (narrow)** | **F4** — Internal Transfer recipient name on VCB / BIDV (refactor validates `RECIPIENT_NAME`; legacy internal-transfer reader validates only REMARKS). |
| Legacy rejects, Refactor accepts | NO (none found) | Symmetric on shared data. (Latent only via the `EFF_FR_DT IS NULL` edge — §3.) |
| Both accept, **stored values differ** | **YES (broad)** | **F1** (VN diacritics retained) + **F2** (newlines retained). |
| Both accept, **generated bank content differs** | **YES (latent)** | Consequence of F1/F2 once transfer-file/bank-API generation is ported. No bank integration today ⇒ latent. |

---

## 7. Internal Transfer special review

| | Legacy (`PymnHelper.readExcelFromInternalTransfer`) | Refactor (`InternalTransferExcelParser`) |
|---|---|---|
| REMARKS validated | ✅ `PymnHelper.java:794` | ✅ `:310` |
| REMARKS tone-stripped | ✅ `:789` | ❌ |
| RECIPIENT_NAME resolved | **No** — never resolves a recipient name (`PymnHelper.java:784-836`) | **Yes** — from `ACCT_MSTR.ACCT_NM` (`:408-411`) |
| RECIPIENT_NAME validated | **No** | **Yes** (`:424-428`) |
| **Validation introduced by refactor** | — | **RECIPIENT_NAME blocked-char check on Internal Transfer** (effective for VCB rule `0028` + BIDV rule `0036`; Woori/Shinhan have no `INTR/RCPT` rule) |
| **Validation/transform removed by refactor** | — | **Vietnamese tone-strip** on Internal Transfer remarks (F1) |

Net: the refactor's internal-transfer recipient handling is **not identical** to legacy — it is **stricter** (extra field validated) *and* **drops** the tone-strip transform. Per the legacy baseline, both are migration gaps.

---

## 8. Rule-data source verification (requirement: do not assume seed = runtime)

| Source | Reachable? | Total | Active (`STATUS='Y'`) | Banks | Effective dates |
|---|---|---|---|---|---|
| **Legacy JEX DEV DB** (`postgres_dev` MCP) | ✅ queried 2026-06-10 | **39** | **36** | VCB `11704004`, BIDV `11704008`, Woori `11704020`, Shinhan `11704088` | VCB/BIDV `20260414→null`; Woori `20251216→null`; Shinhan domestic `20251118→null`; Shinhan OVER v1 `20251218→20260224` (STATUS=N, retired), v2 `20260225→null` |
| **Flyway seed `V149`** (`V149__blocked_chars_rule_schema_and_seed.sql`) | ✅ read | **39** (RULE_ID 0001–0039) | 36 | same 4 | identical to DEV (verbatim 2026-05-15 snapshot) |
| **Refactor RUNTIME DB** (`postgres_local` MCP) | ❌ **UNREACHABLE** (`-32603`, 5 attempts incl. `SELECT 1`) | **UNVERIFIED** | UNVERIFIED | UNVERIFIED | UNVERIFIED |

**Field-specific (legacy DB & seed both):** RMKS+RCPT for INTR/VEND/EXPN/PAYR on VCB/BIDV/Shinhan; Woori adds OVER MEMO/BENA/BEAD; Shinhan adds OVER MEMO/BENA/BEAD (v2). **No `INTR/RCPT` rule for Woori or Shinhan** (relevant to F4).

**OV-1:** the seed file is **not proof of runtime state**. If `V149` failed/was skipped or rules were edited, the validator returns *empty* (no rule → cell passes) and the refactor would silently accept every special character legacy rejects. See `docs/bug-reports/special-char-validation-blocked-chars-rule-runtime-data-unverified.md`.

---

## 9. Bank-Specific Validation Matrix

Verified against **live DEV** (39 rows, 36 active) and refactor seed **V149** (RULE_ID 0001–0039) — **every row matches** (same banks, blocked sets, effective dates, and the Shinhan-OVER v1-retired→v2-active transition). *Allowed = every character except those listed. Subject to OV-1 for runtime.*

| Bank | Field (payment types) | Blocked characters | Legacy rows | Seed rows | Match |
|---|---|---|---|---|---|
| **VCB** `11704004` | RMKS & RCPT (INTR/VEND/EXPN/PAYR) | `` `~!@#$%^_={}[]\|'";>< `` | DEV | 0024–0031 | ✅ |
| **BIDV** `11704008` | RMKS (all) | `` `~!@#$%^&*()_=+{}[]\|/?'";: `` | DEV | 0032–0035 | ✅ |
| **BIDV** `11704008` | RCPT (all) | `` `~!@#$%^*()_=+{}[]\|/?'";:,> `` (no `&`; adds `,>`) | DEV | 0036–0039 | ✅ |
| **Woori VN** `11704020` | RMKS & RCPT (VEND/EXPN/PAYR), INTR RMKS, OVER MEMO/BENA/BEAD | `` <>'"@$& `` | DEV | 0011–0020 | ✅ |
| **Shinhan VN** `11704088` | RMKS (all) | `~!@#$%^*={}[]<>` | DEV | 0001–0004 | ✅ |
| **Shinhan VN** `11704088` | RCPT (VEND/EXPN/PAYR) | `~!@^*={}[]'"<>` | DEV | 0005–0007 | ✅ |
| **Shinhan VN** `11704088` | OVER MEMO/BENA/BEAD (v2 active ≥ 2026-02-25) | `` `@$'"<> `` | DEV | 0021–0023 | ✅ |
| **Shinhan VN** `11704088` | OVER (v1, STATUS=N, retired 2026-02-24) | `` `$'" `` / `` `~!@#$%^&*()_=+{}[]\|?'":>< `` | DEV | 0008–0010 | ✅ |

> Woori `INTR` and Shinhan `INTR` are **RMKS-only** (no `INTR/RCPT` rule) — same in both. Only VCB and BIDV carry an `INTR/RCPT` rule, which is why F4 is scoped to those two banks.

**Fields NOT special-char-validated in either system** (no `FieldCd` entry, no rule rows): Transfer Purpose, Description/Note, domestic Address, **Email, Phone, Beneficiary Bank Name**. These get length/lookup/numeric validation only. This is parity, but note: if a bank restricts characters in any of these, neither system enforces it.

---

## 10. Real examples

Recipient field unless noted. Assumes the refactor runtime DB holds the seeded rules (subject to OV-1). "Transfer" = legacy bank file; refactor transfer-gen N/A today.

| Input | Bank / field | Legacy: validate → stored → transfer | Refactor: validate → stored | Verdict |
|---|---|---|---|---|
| `Nguyễn Văn Đức` | any / RCPT | PASS → `Nguyen Van Duc` → `Nguyen Van Duc` | PASS → `Nguyễn Văn Đức` | both accept, **stored differs (F1)** |
| `lương tháng 6` | any / RMKS | PASS → `luong thang 6` | PASS → `lương tháng 6` | both accept, **stored differs (F1)** |
| `홍길동` (Korean) | any | no strip (Hangul outside VN ranges) → PASS → `홍길동` | PASS → `홍길동` | **identical** |
| `ABC⏎DEF` (newline) | any / RMKS | strip → `ABCDEF` → PASS → `ABCDEF` | PASS → `ABC⏎DEF` | both accept, **stored differs (F2)** |
| `Pay_now#1` | **VCB** / RMKS | `_`,`#` blocked → **REJECT** | **REJECT** | identical |
| `A&B Co` | **BIDV** / RMKS | `&` blocked → **REJECT** | **REJECT** | identical |
| `A&B Co` | **BIDV** / RCPT | `&` not in RCPT set → **PASS** | **PASS** | identical (field-specific) |
| `Công ty A&B` | **Woori** / RMKS or RCPT | `&` blocked → **REJECT** (legacy `&`→` VA ` is commented out) | **REJECT** | identical |
| `pay@now!` | **Shinhan** / RMKS | `@`,`!` blocked → **REJECT** | **REJECT** | identical |
| `O'Brien` | **Shinhan** / RCPT | `'` blocked → **REJECT** | **REJECT** | identical |
| `O'Brien` | **Shinhan** / RMKS | `'` not in RMKS set → **PASS** | **PASS** | identical (field-specific) |
| `A&B` recipient | **VCB/BIDV Internal Transfer** | recipient **not validated** → **PASS** | recipient **validated** (VCB blocks `&` → **REJECT**; BIDV RCPT allows `&` → PASS) | **legacy accepts / refactor may reject (F4)** |

---

## 11. Critical questions — explicit answers

1. **Validation present in legacy but missing in refactor?** Yes — **Vietnamese tone-stripping (F1)** and **embedded-newline stripping (F2)**. The blocked-character check itself is present and at parity.
2. **Present in refactor but not legacy?** Yes — **Internal Transfer recipient-name blocked-char check (F4)** for VCB/BIDV. (Plus the latent `EFF_FR_DT IS NULL` edge — §3.)
3. **Bank-specific restriction that could make a legacy-OK upload fail in refactor?** Yes — **F4** (VCB/BIDV internal-transfer recipient names containing a blocked char).
4. **Scenarios where refactor accepts what legacy rejects?** Not for the blocked-char check (identical data). But the refactor **retains** content legacy would transform — VN diacritics (F1) and embedded newlines (F2) survive into storage.
5. **Could any difference impact transfer-file / bank-API generation?** Yes — **F1 is the material one.** The stored REMARKS/RECIPIENT value differs (diacritics retained), so any file/request built from it diverges from legacy. Latent today (no bank integration); treat F1 as **P0 for the file-generation milestone**.

---

## 12. Source citations (anchor list)

Legacy (JEX, source of truth):
- `WECMS DEV/WECMS/src/wabooks/validator/SpecialCharValidator.java:73-141`
- `WECMS DEV/WECMS/src/wabooks/commons/StringUtils.java:45-60` (`hasVietnameseCharacters`), `:68-88` (`replaceTone`), `:118-124` (`sanitize`)
- `WECMS DEV/WECMS/src/wabooks/commons/ExcelUtils.java:113-145` (`getCell`, newline strip at `:144`)
- `WECMS DEV/WECMS/src/wabooks/helper/DatImpWizHelper.java:1437-1440` (`getCellValue` → `ExcelUtils.getCell`)
- `WECMS DEV/WECMS/src/wabooks/dao/BlockedCharRuleDAO.java:75-105`
- `WECMS DEV/WECMS/src/wabooks/helper/PymnHelper.java` — `readExcel` `:302-606` (Vendor/Expense: REMARKS `:446/451`, CITAD RCPT `:527/540`), `readExcelFromInternalTransfer` `:648-857` (REMARKS only `:789/794`), `ckRecName` NAPAS RCPT `:979-1010`
- `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/pymn/pymn_0005_002_r001_act.jsp:305-350,570-615` (Payroll RCPT+REMARKS tone-strip + validate)
- `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/pymn/pymn_0007_003_r001_act.jsp:169-197,366-372` (Overseas BENA/BEAD/MEMO tone-strip + validate)
- `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/pymn/pymn_0005_002_c001_act.jsp` (Payroll save — copies pre-parsed REC)

Refactor:
- `wecms-api/src/main/java/com/kosign/wecms/domain/payment/validation/SpecialCharValidator.java:67-94`
- `wecms-api/src/main/java/com/kosign/wecms/domain/payment/validation/repository/BlockedCharsRuleRepository.java:21-47`
- `wecms-api/src/main/java/com/kosign/wecms/domain/payment/internaltransfer/service/InternalTransferExcelParser.java:308-312,408-428,537-548`
- `wecms-api/src/main/java/com/kosign/wecms/domain/payment/vendorpayment/service/VendorPaymentExcelParser.java:490-563`
- `wecms-api/src/main/java/com/kosign/wecms/domain/payment/expensepayment/service/ExpensePaymentExcelParser.java` (mirrors Vendor; validate `:549/554`)
- `wecms-api/src/main/java/com/kosign/wecms/domain/payment/payrollpayment/service/PayrollPaymentExcelParser.java:300-347`
- `wecms-api/src/main/java/com/kosign/wecms/domain/payment/service/OverseasPaymentExcelService.java:188,193,198`
- `wecms-api/src/main/resources/db/migration/V149__blocked_chars_rule_schema_and_seed.sql`

---

## 13. Related bug reports

- `docs/bug-reports/special-char-validation-f1-vietnamese-tone-stripping-not-applied.md`
- `docs/bug-reports/special-char-validation-f2-embedded-newline-not-stripped.md`
- `docs/bug-reports/special-char-validation-f4-internal-transfer-recipient-name-validation-divergence.md`
- `docs/bug-reports/special-char-validation-blocked-chars-rule-runtime-data-unverified.md`

---

## Migration-Compatibility Review — F1, F2, F4

**Added:** 2026-06-10 · **Type:** pre-implementation review · **Baseline:** JEX legacy.
**Question per finding:** must the refactor replicate legacy exactly to stay compatible with legacy data migration, legacy payment processing, and future bank integration? **No application code, tests, or schema were changed in producing this review.**

### Decisive shared evidence — stored value flows to the bank verbatim

The value persisted at upload is the value sent to the bank — there is **no second transformation** downstream. On transfer execution (VND firm-banking path), legacy reads `SCDUL_PYMN_SUMR_DETL` and copies the **stored** values **verbatim** into the bank-bound tables:

- `PymnHelper.java:2137-2139` — reads `SCDUL_PYMN_SUMR_DETL` rows for the batch.
- `:2170` `tbPayTarget.setTranUsage(rows.getString("TRAN_USAG"))` and `:2171` `tbPayTarget.setFincMdaOwnerNm(rows.getString("RCPT_NM"))` → `TB_PAY_TARGET` (firm-banking payment target).
- `:2276` / `:2661` `tbPayFileData.setFincMdaOwnerNm(rows.getString("RCPT_NM"))` → `TB_PAY_FILE_DATA` (payment file data).
- `com/firm/FIRM_PROC.java:40,65,85` ships `FINC_MDA_OWNER_NM` to the bank gateway.
- `replaceTone` appears **only** at the four upload sites (`PymnHelper.java:446,527,789,980` + Payroll/Overseas JSPs) — **never** in the `2120-2680` transfer/file-build range.

**Conclusion:** upload-time normalization (`replaceTone` + `\r\n` strip) is the **single, load-bearing normalization point**. Legacy intentionally persists the normalized value in `SCDUL_PYMN_SUMR_DETL` and builds the bank file from it verbatim. This governs the F1 and F2 decisions below.

### F1 — Vietnamese tone stripping — DECISION: **refactor must replicate legacy exactly**

- It is a server-side **business rule** (not UI convenience), guarded by `hasVietnameseCharacters` and added under tracked issues (#42761, #42655).
- Legacy **intentionally stores ASCII-only** values for REMARKS and recipient/beneficiary names; `TB_PAY_TARGET` / `TB_PAY_FILE_DATA` carry that value to the bank verbatim (`:2170-2171`, `:2276`).
- Keeping Vietnamese diacritics would change the bank-bound content (NAPAS/CITAD content + names are conventionally unsigned Latin), risking bank-side rejection/mojibake.
- **Decision: replicate legacy exactly (strip-then-validate-then-store) across all 5 payment types — mandatory before bank integration.** (Exception: the Internal-Transfer *recipient* is not tone-stripped by legacy — see F4 — so F1 covers Internal-Transfer REMARKS only on that path.)

### F2 — Embedded CR/LF stripping — DECISION: **refactor must replicate legacy CR/LF stripping**

- Framework-level, deliberate: `ExcelUtils.getCell:144` (`replaceAll("\\r|\\n","")`) runs on **every** cell for **every** importer.
- The stored value flows verbatim downstream (same path as F1), so a retained newline would reach the bank file and can break record/line framing; legacy never stores embedded CR/LF in these fields.
- **Decision: replicate legacy exactly — strip `\r`/`\n` at cell read; do NOT also strip `\t` (legacy keeps tabs).** Required for data-migration consistency and downstream/bank-file safety.

### F4 — Internal Transfer recipient validation — DECISION: **must align to legacy by not validating the Internal Transfer recipient name**

- Internal Transfer targets the company's **own** account; the recipient name is **system-resolved** from `ACCT_MSTR.ACCT_NM` (curated bank data — `PymnHelper.java:636`), not free Excel text. Legacy validates **REMARKS only** on this path (`:794`) and never special-char-validates the resolved own-account name — an intentional/by-design omission.
- The refactor **introduces a new rule** (`InternalTransferExcelParser.java:424-428`); its code comment cites `:985`, which is in `ckRecName` (Vendor/Expense NAPAS path), not `readExcelFromInternalTransfer`.
- This can **reject legacy-accepted** Internal Transfers (VCB rule `0028` / BIDV rule `0036`).
- **Decision: must align to legacy — remove the added Internal-Transfer recipient-name validation (validate REMARKS only).** The refactor follows legacy; this is a required migration-compatibility correction, not an optional design choice. Tone-strip is not owed here — legacy does not tone-strip this field, so F1 does not apply to it.

### Final decision matrix

| Finding | Legacy Behavior Proven | Business Impact | Migration Risk | Recommendation |
|---|---|---|---|---|
| **F1** Vietnamese tone strip | **Yes** — all 5 types pre-store (`PymnHelper:446/527/789/980` + Payroll/Overseas JSPs); stored value shipped verbatim to bank (`:2170-2171/2276`) | High — stored data + bank-file content diverge; likely bank-side rejection of diacritics | High (data migration + bank integration) | **Replicate legacy exactly** (mandatory pre-bank-integration) |
| **F2** Newline strip | **Yes** — framework-level on every cell (`ExcelUtils.getCell:144`); flows verbatim downstream | Medium — multi-line values diverge; can break bank-file record framing | Medium | **Replicate legacy exactly** (strip `\r`/`\n`, keep `\t`) |
| **F4** Internal-transfer recipient validation | **Yes** — legacy validates REMARKS only; recipient is system-resolved own-account data, never special-char-validated | Medium — refactor adds a new rule; can reject legacy-OK uploads (VCB/BIDV) | Medium (regression vs legacy acceptance) | **Must align to legacy** — remove the added recipient-name check (validate REMARKS only) |

### OV-1 reminder — runtime rule data still unverified

None of the "validation parity" conclusions hold unless the refactor **runtime** `BLOCKED_CHARS_RULE` actually contains the expected `39 total / 36 active / 4 banks`. The runtime DB was **unreachable** during this audit (`postgres_local` → `-32603`, 5 attempts). This remains an **open verification item**, not a confirmed code defect — see `docs/bug-reports/special-char-validation-blocked-chars-rule-runtime-data-unverified.md` for the verification SQL and pass/fail criteria. Confirm before relying on parity or starting F1/F2/F4 implementation.

*Needs Confirmation (does not change the decisions):* the exact NAPAS/CITAD bank file-format character/line constraints live in the separate firm-banking project (`FIRM_PROC` gateway / WeCMS Batch). The legacy stored-value behavior is proven here; the bank-format spec would only reinforce the F1/F2 "replicate" decisions.

---

## Post-Implementation Verification — F1, F2, F4

**Verified:** 2026-06-10 · **Type:** post-implementation verification (audit only — no code changed in this pass).
**Final verdict: PASS** — legacy behavior fully restored for the implemented scope (F1, F2, F4).

### Shared implementation

A verbatim port of the legacy normalization landed in `wecms-api/src/main/java/com/kosign/wecms/domain/payment/common/LegacyTextNormalizer.java`:
- `stripCrLf(value)` → `value.replaceAll("\\r|\\n", "")` (null-safe; tabs not matched) — mirrors `ExcelUtils.getCell:144` (**F2**).
- `hasVietnameseCharacters(...)` + `replaceTone(...)` — copied verbatim incl. the `VN_WITH_TONE` / `VN_ASCII` mapping table from `StringUtils.java:73-74`; `stripVietnameseTone(...)` applies the legacy guard `if (hasVietnameseCharacters(v)) v = replaceTone(v)` (**F1**).

### F1 — verification summary (PASS)

All 10 legacy tone-strip targets are mirrored, **before validation** (strip line precedes validate line) and **before persistence** (the normalized variable is what flows to the stored row / preview row). No payment type missed; no extra fields stripped beyond legacy.

| Payment Type | Legacy field (evidence) | Refactor field (evidence) | Match |
|---|---|---|---|
| Internal Transfer | REMARKS — `PymnHelper:788-789` | `tranUsag` — `InternalTransferExcelParser.java:299` (validate `:313`) | ✅ |
| Vendor | REMARKS — `PymnHelper:445-446` | `tranUsag` — `VendorPaymentExcelParser.java:283` (validate `:562`) | ✅ |
| Vendor | CITAD recipient — `PymnHelper:526-527` | `resolvedRcptNm` — `VendorPaymentExcelParser.java:449` (validate `:567`) | ✅ |
| Expense | REMARKS — `PymnHelper:445-446` | `tranUsag` — `ExpensePaymentExcelParser.java:283` (validate `:555`) | ✅ |
| Expense | CITAD recipient — `PymnHelper:526-527` | `resolvedRcptNm` — `ExpensePaymentExcelParser.java:449` (validate `:560`) | ✅ |
| Payroll | RECIPIENT_NAME — `pymn_0005_002_r001_act.jsp:305-306,571` | `rcptNm` — `PayrollPaymentExcelParser.java:228` (validate `:344`) | ✅ |
| Payroll | REMARKS — `pymn_0005_002_r001_act.jsp:344-345,610` | `tranUsag` — `PayrollPaymentExcelParser.java:231` (validate `:349`) | ✅ |
| Overseas | MEMO — `pymn_0007_003_r001_act.jsp:366-367` | `memo` — `OverseasPaymentExcelService.java:151` (validate `:201`) | ✅ |
| Overseas | BENEFICIARY_NAME — `pymn_0007_003_r001_act.jsp:169-170` | `bnfcNm` — `OverseasPaymentExcelService.java:318` (validate `:191`) | ✅ |
| Overseas | BENEFICIARY_ADDRESS — `pymn_0007_003_r001_act.jsp:191-192` | `bnfcAddr` — `OverseasPaymentExcelService.java:321` (validate `:196`) | ✅ |

### F2 — verification summary (PASS)

CR/LF stripping added at the single cell-read return (the analog of `ExcelUtils.getCell`), applied to every text cell. Tabs preserved (`stripCrLf` regex is exactly `\r|\n`); no `\s+`-collapse added; numeric/date branches untouched.

| Parser | Refactor stage (evidence) | Match |
|---|---|---|
| Internal Transfer | `cellString` — `InternalTransferExcelParser.java:549` | ✅ |
| Vendor | `cellString` — `VendorPaymentExcelParser.java:675` | ✅ |
| Expense | `cellString` — `ExpensePaymentExcelParser.java:668` | ✅ |
| Payroll | `cellString` — `PayrollPaymentExcelParser.java:414` | ✅ |
| Overseas | `readCell` STRING `:385` + FORMULA-fallback `:397` | ✅ |

### F4 — verification summary (PASS)

| Validation target | Legacy | Refactor | Match |
|---|---|---|---|
| Internal Transfer recipient (special-char) | not validated (`readExcelFromInternalTransfer:648-857`) | **removed** — 0 hits for `INTERNAL_TRANSFER, FieldCd.RECIPIENT_NAME` | ✅ |
| Internal Transfer REMARKS (special-char) | validated (`PymnHelper:794`) | validated — `InternalTransferExcelParser.java:313` | ✅ |
| Recipient resolution + storage from `ACCT_MSTR` | resolved + stored, unvalidated (`PymnHelper:636`) | resolved + stored, unvalidated (`InternalTransferExcelParser.java:408-411`) | ✅ |

No indirect path: across `domain/payment`, the only `FieldCd.RECIPIENT_NAME` validate calls are Vendor `:567`, Expense `:560`, Payroll `:344` — none for Internal Transfer.

### Regression grep summary

| Query | Result |
|---|---|
| Legacy `replaceTone` site with no refactor counterpart | Only the NAPAS recipient (`ckRecName:980`) — gated behind the stubbed firm-banking inquiry; no resolved/stored value today ⇒ no divergence. Other 10 sites mirrored. |
| Legacy strips CR/LF but refactor does not | None — all 4 `cellString` + Overseas `readCell` (both string branches) strip CR/LF; no other payment Excel cell reader exists. |
| Refactor still validates Internal Transfer recipient | None — 0 hits. |

### Build / test result summary

- `./gradlew compileJava` → **BUILD SUCCESSFUL** (exit 0).
- `./gradlew test --tests "*InternalTransferExcelParserTest" --tests "*SpecialChar*" --tests "*BlockedChars*"` → **BUILD SUCCESSFUL** (exit 0). No tests modified.

### Follow-up — NAPAS recipient tone-stripping (future)

Legacy NAPAS recipient tone-stripping (`PymnHelper.ckRecName:979-980`) is tied to the firm-banking account inquiry. The current refactor inquiry (`firmBankingInquiryService.inquiryAccount` → WB_00000100) is **stubbed** (returns `Optional.empty()`), so the NAPAS recipient name is not resolved or stored and there is **no active stored-value divergence today** (legacy `ckRecName:1037` also skips when the inquiry/config is absent). When the inquiry is implemented, apply `LegacyTextNormalizer.stripVietnameseTone(...)` to the bank-returned recipient name **before** validation/storage to preserve legacy parity. Tracked in the F1 bug report.

### OV-1 status

**Still open.** Runtime `BLOCKED_CHARS_RULE` data could not be verified (refactor DB unreachable during the audit). This verification does **not** close OV-1 — see `docs/bug-reports/special-char-validation-blocked-chars-rule-runtime-data-unverified.md` for the verification SQL and pass/fail criteria. Parity of the F1/F2/F4 corrections holds independently of OV-1, but the "rules actually enforced at runtime" question remains an open verification item.

### Status roll-up

| Finding | Status |
|---|---|
| F1 — Vietnamese tone-stripping | **Resolved / Verified** |
| F2 — CR/LF stripping | **Resolved / Verified** |
| F4 — Internal Transfer recipient validation | **Resolved / Verified** |
| OV-1 — runtime `BLOCKED_CHARS_RULE` data | **Still Open** |
