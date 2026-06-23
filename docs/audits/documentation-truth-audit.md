# Documentation Truth Audit — Executive Summary

> **Phase-3 code-vs-documentation audit (2026-06-23).** Implementation code was treated as the **sole source of truth**.
> Six modules (auth · banking · payment · settings · common · addon-service) audited across both repos (`wecms-api`,
> `wecms-web`) on five dimensions: documentation accuracy, coverage, authorization, database mapping, API contract.
> Method: one investigator per module reading controllers / services / repositories / DTOs / security annotations /
> SQL / migrations / frontend routes / clients / hooks, then cross-checking against the docs. Every finding cites an
> exact file location and is tagged **CONFIRMED** (verified against code) or **SUSPECTED** (not fully verified).
> **No files were fixed.** Detailed findings live in the three companion drift reports:
> [authorization-doc-drift.md](authorization-doc-drift.md) · [database-doc-drift.md](database-doc-drift.md) · [api-contract-drift.md](api-contract-drift.md).

## Top-line conclusion

The WeCMS refactor documentation is **healthier than a typical doc set** and, unusually, the feature docs are mostly
**ahead of the code comments** — they proactively flag stale Javadocs rather than repeating them. The drift that exists
falls into four buckets, in priority order:

1. **Banking → Daily Financial (CMC) docs are wholesale stale** — still describe a retired "Strategy B Java live-compute"
   read path; the migration guide actively *forbids* adding the DB function the code now calls. (Highest-value doc fix.)
2. **Common module migration-version numbers are systemically wrong** — docs cite `V242/V243` (navigation) and `V155`
   (audit-log key-gen); the on-disk files are `V137–V139` and `V140`. Root cause is a stale/fabricated `wecms-api/CLAUDE.md`.
3. **Stale source-code Javadoc & root CLAUDE.md narration** contradict the live code (authz "deferred", BCrypt/AES-GCM,
   `@Subselect` "views", endpoint counts). Docs are right; the code comments / CLAUDE.md are the liability.
4. **A referenced audit file is missing** (`audits/login-credentials-legacy-vs-refactor-audit.md`) and **two real
   endpoints are unauthenticated + undocumented** (`/payment/dispatch/callbacks/*`).

**Coverage is excellent:** every feature leaf in all six modules carries the full 9-doc (API) / 6-doc (WEB) set — **zero
missing or empty leaf docs**. The only coverage gap is the one missing audit file above.

## Module scorecards

| Module | Coverage | Accuracy | Authz | DB mapping | API contract | Risk |
|---|---|---|---|---|---|---|
| **auth** | ✅ complete (8×9 + web) | ✅ docs accurate; stale code Javadocs only | ✅ match | ⚠️ 1 suspected (UserPwChgHis type) | ✅ match (1 minor FE table) | **LOW** |
| **banking** | ✅ complete (10 leaves) | ❌ **Daily Financial CMC docs stale (~14 files)**; daily-cash hdr + HS mock-label | ✅ match | ⚠️ DB-1/2 function-call stale | ✅ match (AC-3 minor) | **MEDIUM-HIGH** |
| **payment** | ✅ complete (8×9/6) | ✅ no doc-side drift | ⚠️ code-side: stale javadoc + **unauth callbacks** | ✅ match | ⚠️ 2 undocumented endpoints | **LOW** (docs) / **MEDIUM** (code security) |
| **settings** | ⚠️ 1 missing audit file | ⚠️ contacts "audit aspect" fiction; chart depth-cap 3≠4 | ✅ match (stale javadocs only) | ⚠️ bank-account PK order; stale view javadocs | ⚠️ AC-1 wrong error code | **MEDIUM** |
| **common** | ✅ complete (11 leaves incl. excel-import/export/file-mgmt) | ❌ **migration versions wrong (~12 files)** | ✅ match | ❌ DB-3/DB-4 version drift | ✅ match | **MEDIUM** |
| **addon-service** | ✅ complete (3 leaves) | ✅ docs accurate; "known bugs" already corrected | ✅ match | ✅ match | ✅ match (IR-4 honestly deferred) | **LOW** |

## Drift counts (confirmed unless noted)

| Category | Count | Where |
|---|---|---|
| **Feature-doc drift (docs wrong — fix in docs)** | **~22 files** | banking daily-financial CMC (~14), common migration versions (~12 overlap), settings bank-account PK (1) + contacts error code (1), auth login FE table (1), banking HS mock-label (3) |
| Stale **source-code Javadoc** (docs correct) | 10 | AZ-1..5, DB-6..10 |
| Stale **root CLAUDE.md** narration (docs correct) | 6 | AZ-8/9, AC-4..7 (+ contacts audit-aspect, chart 4-level) |
| **Undocumented endpoints** | 2 | `/payment/dispatch/callbacks/*` (AC-8/AZ-6), `BlockedCharsRuleController` (AC-9/AZ-7) |
| **Missing referenced file** | 1 | `audits/login-credentials-legacy-vs-refactor-audit.md` (3 dangling links) |
| **Coverage gaps (missing/empty leaf docs)** | 0 | — |
| **Suspected (needs verification)** | 3 | UserPwChgHis REG_DTM type (DB-S1), firm-banking-history V153 seed (DB-S2), TransferController duplicate own-accounts path (AZ-S1) |

## Highest-priority corrections

| Pri | Item | Why | Effort |
|---|---|---|---|
| **P1** | Rewrite banking **Daily Financial (CMC)** docs to the live "calls `rept_0015_001_r001_rec1` directly" path (DB-1/2 + database-mapping, migration-guide, legacy-spec, P0-fix-plan, parity-verification-report, web README/legacy-spec) | migration-guide actively misdirects a future porter; ~14 files describe a retired design | ~0.5 day |
| **P2** | Fix **common migration-version citations** — navigation `V242/V243`→`V137-139`, audit-log `V155`→`V140` (DB-3/4) — **and** correct the root `wecms-api/CLAUDE.md` that is the source | wrong version numbers send readers to non-existent / unrelated `.sql` files | ~0.5 day (mostly mechanical) |
| **P3** | Resolve the missing **`login-credentials-legacy-vs-refactor-audit.md`** — author it, or repoint the 3 dangling links + CLAUDE.md + memory to `login-legacy-compatibility-2026-06-10.md` | dangling links + cited-as-canonical in memory; needs PO/author decision | ~0.25 day (decision-gated) |
| **P4** | Document / secure the **`/payment/dispatch/callbacks/*`** endpoints — wire `CallbackIpWhitelistFilter` (code) and add an interface-spec / allow-list note (AC-8/AZ-6) | real unauthenticated POST surface invisible in docs | code task — out of docs scope |
| **P5** | Fix the small doc-side items: settings `AcctAuthInfo` PK ordering (DB-5), contacts `WBE2040`→`WBE_CONTACTS_NOT_FOUND` (AC-1), banking HS "mock fallback" label (AC-3), auth login `all_menus` table rows (AC-2) | low-risk accuracy nits | ~0.25 day |
| **P6** *(separate code-tree task)* | Scrub stale **source-code Javadocs** (AZ-1..5, DB-6..10) + stale **CLAUDE.md** rows (AC-4..7, contacts audit-aspect, chart 4-level) | docs are already correct; the code comments mislead code readers | ~0.5 day, **code repo** |

## Estimated effort

- **Doc-only corrections (P1–P3, P5):** ~1.5 days. Mostly Banking daily-financial rewrite + a mechanical version-number sweep + small nits. P3 is decision-gated on the PO.
- **Code-tree follow-ups (P4, P6):** ~1 day, **not part of the documentation workstream** (touches `src/` Javadoc + a security filter wiring) — explicitly out of this audit's "do not modify code" scope; raised for the dev/PO.

## Success-criteria compliance

- ✅ Implementation code treated as the source of truth (every "stale doc" finding is doc-loses-to-code).
- ✅ No assumptions — every finding cites an exact file (and line where available); memory-derived facts were re-verified against current code.
- ✅ Confirmed vs suspected separated throughout (3 suspected items isolated).
- ✅ Audit reports only — **no docs or code modified** (these four audit files are the sole new artifacts).
