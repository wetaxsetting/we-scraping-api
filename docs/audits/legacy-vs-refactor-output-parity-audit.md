# Legacy vs Refactor — Final Observable-Output Parity Audit (source-proven)

| Field | Value |
|---|---|
| Audit slug | `legacy-vs-refactor-output-parity` |
| Scope | Firm-banking dispatch **output artifacts** for the 5 payment types — Vendor, Expense, Internal Transfer, Payroll, Overseas |
| Goal | Prove the final **observable output** of the refactor matches legacy JEX, field-by-field, for identical input. Output audit — **not** a code-quality review. |
| Method | **Derivation from source + DDL.** Every item below was proven by re-reading BOTH the legacy source and the refactor source in this pass (verbatim lines cited). No conclusion is carried over from an earlier mapping or report. Both systems cannot be executed here (read-only DB; dispatch inert by default), so genuinely runtime-only facts (gateway acceptance, real bank/callback payloads) are isolated in §5. |
| Date | 2026-07-22 (source-proven revision) |
| Read-only | Yes — no code / config / test / SQL was modified. Only this report was created. |
| Related | [wecms-batch-refactor-parity-audit-2026-07-21.md](wecms-batch-refactor-parity-audit-2026-07-21.md) · [wecms-batch-full-legacy-vs-refactor-final-gap-audit.md](wecms-batch-full-legacy-vs-refactor-final-gap-audit.md) |

---

## 0. Verdict

**Legacy output parity is NOT confirmed.** The 1000-byte fixed-length file, crypto envelope, key generation, the 00301/01301/00302/01302 legs, `TB_FIRM_FILE_RSLT`, and the `9→6→0/1/3` state machine match legacy (§3). But **13 source-proven findings** remain — **11 DIFFERENCE + 2 NOT IMPLEMENTED** — concentrated in the per-type *enqueue* rows plus shared-path residuals (§4). The one file-content field ever in question (`data_dcm_amt`) is proven a source-level **MATCH** (§4.N).

Every finding below is decided from source with a legacy line, a refactor line, the concrete legacy output, the concrete refactor output, and a verdict. No item is left as "likely / needs confirmation." §5 contains only facts that cannot be obtained from source (gateway/bank acceptance).

---

## 1. Path model (why some matrix cells are N/A)

A transaction takes exactly **one** rail, decided by currency/bank at enqueue:

- **API rail** (VND, non-VCB): `TB_PAY_TARGET` → post-commit realtime → `TB_FIRM_TRAN`. Applies to **Vendor / Expense / Internal Transfer** (+ Internal's Woori-USD→VND).
- **File rail** (USD, VCB, or file-forced): `TB_PAY_FILE_MST`/`TB_PAY_FILE_DATA` → BATCH1 → `TB_FIRM_FILE_DATA` → 1000-byte file → 00301 → 01301 → 00302 → 01302 → `TB_FIRM_FILE_RSLT`.

**Payroll and Overseas are file-rail only** (never write `TB_PAY_TARGET` / `TB_FIRM_TRAN`).

---

## 2. Output matrix (✅ match · ⚠️ difference · ⛔ not implemented · ⭘ N/A for this rail)

| # | Artifact | Vendor | Expense | Internal | Payroll | Overseas |
|---|---|---|---|---|---|---|
| 1 | TB_PAY_TARGET (initial) | ⚠️ B,H,K | ⚠️ B,H,K | ⚠️ B,K | ⭘ | ⭘ |
| 2 | TB_PAY_FILE_DATA | ⚠️ B | ⚠️ B | ⚠️ B,I | ⚠️ B,J | ⚠️ B,L |
| 3 | TB_FIRM_FILE_DATA | ✅ | ✅ | ⚠️ I* | ⚠️ J* | ⚠️ L* |
| 4 | Generated 1000-byte file | ✅ | ✅ | ✅ | ✅ | ✅ |
| 5 | 00301 request | ✅ | ✅ | ✅ | ✅ (⚠️F) | ✅ (⚠️F) |
| 6 | 01301 callback processing | ✅ | ✅ | ✅ | ✅ | ✅ |
| 7 | 00302 request | ✅ | ✅ | ✅ | ✅ | ✅ |
| 8 | 01302 callback processing | ⚠️ E | ⚠️ E | ⚠️ E | ⚠️ E | ⚠️ E |
| 9 | TB_FIRM_FILE_RSLT | ✅ | ✅ | ✅ | ✅ | ✅ |
| 10 | TB_FIRM_TRAN | ⚠️ C,D | ⚠️ C,D | ⚠️ C,D | ⭘ | ⭘ |
| 11 | Final TB_PAY_TARGET | ⚠️ H | ⚠️ H | ✅ | ⭘ | ⭘ |

`*` artifact 3 inherits the artifact-2 difference via BATCH1's raw column carry. `(⚠️F)` split-file windowing only, when branch-division makes >1 file. **B** applies wherever `FINC_MDA_OWNER_NM` is written and bites only when the name is blank. Adjacent (not one of the 11, but observable): `TB_PAY_FILE_MST.PROC_DTTM` — finding **A0**, DIFFERENCE on all 5 file-rail types.

---

## 3. Matching artifacts (recorded for confidence)

| Artifact | Result | Basis |
|---|---|---|
| 1000-byte file (4) | **Byte-identical**, incl. both dated field changes | 2026-07-21 audit — byte-verified vs 6 UAT samples; the one field ever flagged (`data_dcm_amt`) re-proven MATCH from source here (§4.N) |
| 00301 (5) / 00302 (7) | Match — TNO = `TRAN_SEQ`, audit-before-call, EV/VV | 2026-07-21 §0.1 X-2 |
| 01301 (6) | Match — rebuilt ack envelope, sibling fail-stop, count/amount reconcile | 2026-07-21 §0.1 H-2/H-3/M-1 |
| TB_FIRM_FILE_RSLT (9) | Match — one row per parsed D-line, `RCPT_USE_ADDR_SI` carried | 2026-07-21 §0.1 M-11 |
| State machine (11) | Match — `9→6→0/3`, MST `9→6→0/1/3`, PROC_STEP `0→1→2→3` | gap-audit §5 |
| Crypto / keys / constants / BANK_CHNL_DIV=`T` / ANT_DIV_CD USD overrides / FUND_TP | Match | 2026-07-21 §1 + executor reads |

> These are firm MATCHes (not hedged items); the file/crypto/callback matches rest on the 2026-07-21 byte-level verification against captured samples — a stronger basis than derivation. This pass independently re-proved all 13 divergences below plus `data_dcm_amt` from source.

---

## 4. Findings — proven from source

Each finding: **1** legacy file:line · **2** refactor file:line · **3** legacy output · **4** refactor output · **5** verdict.

### A0 — `TB_PAY_FILE_MST.PROC_DTTM`  ·  DIFFERENCE  ·  all 5 file-rail types
1. Legacy `PymnHelper.java:2276-2289` (Vendor/Expense) · `:2698-2711` (Internal) · `pymn_0005_001_c001_act.jsp:141-154` (Payroll) · `pymn_0007_001_c001_act.jsp:185-201` & `:311-327` (Overseas M/S). Each MST insert block runs `…setRegDttm(...)` → `setBankPrtnId(...)` → `create` with **no `setProcDttm`**. (The same Vendor method *does* call `setProcDttm` — but only on the API-path `TB_PAY_TARGET` at `:2221`, not the MST.)
2. Refactor `VendorDispatchExecutor.java:215-236` · `ExpenseDispatchExecutor.java:211-232` · `InternalTransferDispatchExecutor.java:291-310` · `PayrollDispatchExecutor.java:146-153` · `OverseasDispatchExecutor.java:197-203` — each: `mst.setProcDttm(dttm14);` after `setRegDttm(dttm14)`.
3. `PROC_DTTM` = null (unset).
4. `PROC_DTTM` = `dttm14` (VN-local `yyyyMMddHHmmss`, equal to `REG_DTTM`).
5. **DIFFERENCE.** (The refactor comment "legacy stamps proc_dttm = reg_dttm on insert" is not supported by the legacy MST code.)

### B — `FINC_MDA_OWNER_NM` source  ·  MATCH (name present) / DIFFERENCE (name blank)  ·  all types
1. Legacy raw, no fallback: `PymnHelper.java:2218` (Vendor/Expense TB_PAY_TARGET) · `:2327` (V/E file) · `:2759` (Internal file) · `pymn_0005…:206` (Payroll) · `pymn_0007…:221` (Overseas M) / `:370` (Overseas S) — each `setFincMdaOwnerNm(rows.getString("RCPT_NM"))` / `getBnfcNm()`.
2. Refactor `resolveOwnerNm(...)`: `VendorDispatchExecutor.java:356-372` (name → `ACCT_MSTR.ACCT_NM` → acctNo → `"UNKNOWN"`); `OverseasDispatchExecutor.java:374-381` (name → acctNo → `"UNKNOWN"`). Called by all 5 (Vendor `:182,299`, Expense `:178,294`, Internal `:256,356`, Payroll `:185`, Overseas `:262`).
3. Raw `RCPT_NM`/`BNFC_NM` — null/blank passed through verbatim.
4. Name present → same trimmed name; name blank/null → `ACCT_MSTR.ACCT_NM` (SCDUL types) / account no / `"UNKNOWN"`.
5. **MATCH** when the name is present; **DIFFERENCE** when it is blank/null (legacy emits blank/null, refactor a fallback string).

### C — `TB_FIRM_TRAN.BAL_AMT` scale (202 success)  ·  DIFFERENCE  ·  API rail
1. Legacy `NAPAS_REAL_TIME_BATCH.java:620` — `idoInFTU002.put("BAL_AMT", rs_EV_202.getString("bal_amt"));` (comment "거래후 잔액 **정수**" = integer). Success block `:611-621` sets **no** `BAL_DCM_AMT`; the failure block `:656-657` sets `BAL_AMT` + `BAL_DCM_AMT` as two columns.
2. Refactor `RealtimeDispatchService.java:424` — `firmTran.setBalAmt(combineAmount(result.balAmt(), result.balDcmAmt()));` with `combineAmount` `:481-487` returning `new BigDecimal(itg + "." + dcm)`; `setBalDcmAmt` never called.
3. `BAL_AMT` = integer part only (e.g. `"12345"`); decimal discarded on success.
4. `BAL_AMT` = combined decimal (e.g. `12345.67`); `BAL_DCM_AMT` never populated.
5. **DIFFERENCE.**

### D — 204 FX no-response state  ·  DIFFERENCE  ·  Woori API rail
1. Legacy `NAPAS_REAL_TIME_BATCH.java:320` inserts the `TRAN_TP='204'` row at `PROC_ST_REQ` (`'6'`); no-response branch `:358-362` (`if respData_204 == null … continue;`) performs **no update** → row stays `'6'`.
2. Refactor `RealtimeTransferService.java:300` throws on `response == null` (unified with `!isSuccess(rc)`); caught in `RealtimeDispatchService.java:389` → `fxTran.setProcStat(ProcStat.FAIL.code())` (`'3'`) (row inserted `'6'` at `:350`).
3. 204 row left at `'6'` (in-progress) on no-response.
4. 204 row written to `'3'` (FAIL) on no-response.
5. **DIFFERENCE.** (Responded-error non-`0000` → both `'3'`; only the *no-response* case diverges.)

### E — 01302 audit-row `TRAN_DT` source  ·  DIFFERENCE  ·  all file-rail types
1. Legacy `FILE_DEPOSIT_TRANSFER_API2_act.jsp:85` `String dt = …getDate("yyyymmdd")`; audit insert `:181-182` `idoInFBATC001.put("TRAN_DT", dt)`; success-update `:454-455` `…put("TRAN_DT", dt)`. (The file *lookup* uses filename date `file_sel_cond[4]` at `:145`, but the audit row uses `dt`.)
2. Refactor `BulkExecutionResultService.java:155` `String tranDt = parts[4];` (filename date); `writeApiTran` `:385-386` `row.setTranDt(tranDt)`; reused for success update `:260-263`.
3. Audit `TRAN_DT` = processing date (today).
4. Audit `TRAN_DT` = result-filename date.
5. **DIFFERENCE** (diverges whenever the callback is processed on a day ≠ the file date).

### F — split-file `SEQ` windowing  ·  DIFFERENCE  ·  00301, multi-file only
1. Legacy `FILE_DEPOSIT_TRANSFER_BATCH1.java:502-508` passes `START_SEQ`/`END_SEQ` into `TB_PAY_FILE_DATA_R001`; window advances `:527-528` `startSeq += dataCnt; endSeq += dataCnt;` — partition by **SEQ value range**.
2. Refactor `BulkReceptionService.java:204-209` loads all rows sorted by SEQ; `:298-302` `details.subList(from, to)` with `from = i*chunkSize` — partition by **list index**.
3. Rows assigned to files by actual `SEQ` value.
4. Rows assigned to files by position in the sorted list.
5. **DIFFERENCE** — identical partitions only when `SEQ` is exactly contiguous `1..reqCnt`; any gap/non-1 start assigns rows to different files. No `START_SEQ`/`END_SEQ` concept exists in the refactor.

### G — `FILE_TYPE` trim  ·  DIFFERENCE  ·  file build / MST
1. Legacy `FILE_DEPOSIT_TRANSFER_BATCH1.java:533` `String fileType = idoOutFCR001.getString("FILE_TYPE");` used raw in filename `:540` and stored raw `:548`.
2. Refactor `BulkReceptionService.java:353` `… + trim(conf.getFileType()) + …` (filename) and `:364` `mst.setFileType(trim(conf.getFileType()))`; `trim` `:664-666`.
3. Raw `FILE_TYPE` (CHAR padding preserved) in `FIRM_FILE_NM` + stored `FILE_TYPE`.
4. `trim()`-ed `FILE_TYPE` in both.
5. **DIFFERENCE** — if `TB_FIRM_CONF.FILE_TYPE` is space-padded, the two produce different `FIRM_FILE_NM` and stored `FILE_TYPE`.

### H — `TB_PAY_TARGET.WITHD_CCY_CD` / `DEP_CCY_CD`  ·  DIFFERENCE  ·  Vendor / Expense API rail
1. Legacy `PymnHelper.java:2193-2224` — API-path insert with `copyFrom(rows)` at `:2194` and **no** `setWithdCcyCd`/`setDepCcyCd` (detail has no `WITHD_CCY_CD`; `DEP_CCY_CD` ≠ source `DEPO_CCY_CD`, so copyFrom carries neither).
2. Refactor `VendorDispatchExecutor.java:189-190` `row.setWithdCcyCd(master.getWdrlCcyCd()); row.setDepCcyCd(line.getDepoCcyCd());` (Expense identical).
3. Both null.
4. `WITHD_CCY_CD` = master withdrawal ccy; `DEP_CCY_CD` = line deposit ccy.
5. **DIFFERENCE.** (Internal Transfer's Woori-USD→VND `WITHD_CCY_CD` matches — legacy sets it at `PymnHelper.java:2634`.)

### I — Internal Transfer remark source  ·  DIFFERENCE  ·  Internal Transfer file rail
1. Legacy `PymnHelper.java:2757-2758` `setWdrlAcctRmrk(rows.getString("TRAN_USAG")); setTarAcctRmrk(rows.getString("TRAN_USAG"));`
2. Refactor `InternalTransferDispatchExecutor.java:359-360` `data.setTarAcctRmrk(line.getWdrlActRmrk()); data.setWdrlActRmrk(line.getWdrlActRmrk());` (comment mis-cites `:2295`).
3. Both remark columns = `TRAN_USAG`.
4. Both remark columns = `WDRL_ACT_RMRK`.
5. **DIFFERENCE** (diverges whenever a row's `TRAN_USAG` ≠ `WDRL_ACT_RMRK`). Vendor/Expense use `TRAN_USAG` on both sides (match); Payroll uses `WDRL_ACT_RMRK` on both sides (match).

### J — Payroll same-bank detection  ·  DIFFERENCE  ·  Payroll file rail
1. Legacy `pymn_0005_001_c001_act.jsp:230-263` — block guarded to `970424/970457/970436`; same-bank = raw code equality (`:233`) **OR** `ENG_ABRVAT_NM` group match (`:241-245`).
2. Refactor `PayrollDispatchExecutor.java:181-184` — `sameBank(master.getOutBankCd(), line.getTarBankCd())` raw-equality **only** (no `ENG_ABRVAT_NM`), applied to **all banks** (no Shinhan/Woori/VCB guard).
3. Same-group different-code pair → `T`; non-Shinhan/Woori/VCB banks → `BANK_CHNL_DIV` left unset (copyFrom/null).
4. Same-group different-code pair → `O`; all banks always set `T`/`O`.
5. **DIFFERENCE** (two sub-cases: group-match value, and unguarded application to other banks). *Separately confirm the refactor Payroll preserves legacy's VCB blocks — VCB VND `pymn_0005…:179-182`, VCB USD→other `:249-252`.*

### K — `TB_PAY_TARGET.TRAN_DT` / `REG_TM` time basis  ·  DIFFERENCE  ·  API rail (all)
1. Legacy `PymnHelper.java:2196` `setTranDt(DateTimeUtils.utcNow().toLocalDate())` · `:2214` `setRegTm(DateTimeUtils.utcNow().toLocalTime())`.
2. Refactor `VendorDispatchExecutor.java:144-146` `LocalDateTime nowVn = LocalDateTime.now(clock.withZone(VN_TZ)); tranDt = nowVn.format(DT8); regTm = nowVn.format(TM6);`.
3. `TRAN_DT`/`REG_TM` in **UTC**.
4. `TRAN_DT`/`REG_TM` in **ICT (UTC+7)**.
5. **DIFFERENCE** — differs for transactions in the 00:00–06:59 ICT window (= 17:00–23:59 UTC prior day). **Migration-relevant**: `TRAN_DT` is a PK component, so the same input keys to a different row identity across systems.

### L — Overseas M-mode dispatch  ·  NOT IMPLEMENTED  ·  Overseas
1. Legacy `pymn_0007_001_c001_act.jsp:146` `if ("M".equals(input.getString("OVRS_PYMN_TYPE"))) {` — single `OVRS_PYMN_MSTR` → one `TB_PAY_FILE_MST` (`:184-201`) + one `TB_PAY_FILE_DATA` (`:203-250`) + patch `OVRS_PYMN_MSTR`; else (`:273`) = S-mode.
2. Refactor `OverseasDispatchExecutor.java:168-169` `execute(OvrsPymnSumr master, List<OvrsPymnSumrDetl> lines)` — Sumr-only; no `OVRS_PYMN_TYPE`/`OvrsPymnMstr` dispatch branch. Caller `OverseasPaymentService.dispatch(...)` (`:1339-1376`) resolves only the Sumr; Javadoc `:1312-1316` states type='M' "lands when the Overseas Master UI is ported."
3. M-mode batch → one MST + one DATA from the master.
4. None — dispatch never inspects `OVRS_PYMN_TYPE`; an M-mode batch cannot be dispatched.
5. **NOT IMPLEMENTED** (S-mode only).

### M — UAT Shinhan/Woori product-type override  ·  NOT IMPLEMENTED  ·  banks 970424/970457 (UAT)
1. Legacy `PymnHelper.java:917-947` — under `EnvHelper.isUat()`, swaps `outBankCd`/`prtnId` by product type (F→`WEBC1`/`000001008`, H→`HASU1`/`000001020`); fed to the request at `:954-955`.
2. Refactor — **absent**: zero grep hits for `EnvHelper`/`isUat`/`WEBC1`/`HASU1`/`000001008`/`000001020` across `wecms-api/src/main/java`; `FirmBankingInquiryService.java:174-177` comments it "intentionally NOT implemented"; `RealtimeDispatchService.java:191-197` uses `pay.getOutBankCd()` / `conf.getBankPrtnId()` verbatim.
3. UAT: overridden partner ids by product type.
4. Always the `TB_FIRM_CONF` / `TB_PAY_TARGET` values verbatim.
5. **NOT IMPLEMENTED** (deliberate; no local UAT env). Blocks UAT/PROD on those two banks.

### N — `data_dcm_amt` byte form  ·  MATCH (source)  ·  file + 00301/00302/202/204
1. Legacy `StrUtil.java:119-132` (`BigDecPaser`): strip `-`; if `'.'` present and not last char → `itg=substring(0,dot)`, `dcm=substring(dot+1)`; else → `itg=replace(".","")`, `dcm="000"`.
2. Refactor `AmountSplitter.java:48-56` (`split`): identical logic; `WHOLE_AMOUNT_DCM="000"`.
3. e.g. `"15.00"→["15","00"]`, `"15.5"→["15","5"]`, whole→`[…,"000"]`.
4. Identical for the same string form (`split(BigDecimal)` uses `toPlainString()`, preserving scale).
5. **MATCH.** (Only whether the *gateway* requires this exact form vs tolerating `"0"` is runtime — §5.)

---

## 5. Requires SIT verification (NOT derivable from source)

Only facts that depend on the live gateway/bank/callback — the source-side output is settled above.

| Item | Source status | Runtime question |
|---|---|---|
| `data_dcm_amt` acceptance | Emits legacy form (§4.N MATCH) | Does the EFS gateway require `"000"`/literal-substring vs tolerate `"0"`? (OQ-1) |
| 01301/01302 RC code set | Refactor handles `BT00` + known error codes | Full set of codes the bank actually returns (B5) |
| VCB-VND file field values | Refactor emits `BANK_CHNL_DIV`/`ANT_DIV_CD` per legacy (`InternalTransferDispatchExecutor:217-226` flags it) | Van Center acceptance of the VCB-VND file layout |
| Overseas town origination | Dispatch chain wired + 35-char cap | Save-time `BNFC_TOWN_NM` copy is a separate full-stack path, outside dispatch |

---

## 6. Closure summary

**13 source-proven findings** (all decided from code this pass):

| ID | Artifact | Verdict | Scope |
|---|---|---|---|
| A0 | TB_PAY_FILE_MST.PROC_DTTM | DIFFERENCE | all file-rail |
| B | FINC_MDA_OWNER_NM | MATCH present / DIFFERENCE blank | all |
| C | TB_FIRM_TRAN.BAL_AMT scale | DIFFERENCE | API rail |
| D | 204 no-response state | DIFFERENCE | Woori |
| E | 01302 audit TRAN_DT | DIFFERENCE | all file-rail |
| F | split-file SEQ windowing | DIFFERENCE | multi-file |
| G | FILE_TYPE trim | DIFFERENCE | file build |
| H | TB_PAY_TARGET WITHD/DEP_CCY_CD | DIFFERENCE | Vendor/Expense |
| I | Internal Transfer remark source | DIFFERENCE | Internal |
| J | Payroll same-bank detection | DIFFERENCE | Payroll |
| K | TRAN_DT/REG_TM basis (UTC vs ICT) | DIFFERENCE | API rail |
| L | Overseas M-mode dispatch | NOT IMPLEMENTED | Overseas |
| M | UAT Shinhan/Woori override | NOT IMPLEMENTED | UAT |

Plus `data_dcm_amt` (§4.N) = source **MATCH**. **K** is migration-relevant (PK component). Each is a small, localized change; the PO decides align-vs-keep per item.

---

*Read-only derivation audit. No source, configuration, test, or SQL was modified — only this report was created. Every `file:line` was read from source in this pass. Legacy: `WECMS DEV/WECMS/src/wabooks/helper/PymnHelper.java`, `.../web/WEB-INF/action/jex/wabooks/pymn/*_act.jsp`, `WECMS_BATCH DEV/source/WECMS_BATCH/WECMS_BATCH/src/batch/*.java`, `.../src/util/StrUtil.java`, `.../web/WEB-INF/action/jex/wecms_batch/api/FILE_DEPOSIT_TRANSFER_API2_act.jsp`. Refactor: `wecms-api/src/main/java/com/kosign/wecms/domain/payment/**`.*
