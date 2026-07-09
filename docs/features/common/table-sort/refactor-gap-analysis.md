# Table Sort — Refactor Gap Analysis

**Part of:** Common → table-sort
**Last verified:** 2026-06-23

Vocabulary: **MATCH** (refactor behaves like legacy) · **FORBIDDEN BY DESIGN** (the refactor
deliberately removes a capability) · **PARTIAL** (correct where implemented; rollout incomplete) ·
**APPROVED DEVIATION** (intentional difference) · **MISSING** (gap to close).

## Parity matrix

| Aspect | Legacy JEX | Refactor | Verdict |
|---|---|---|---|
| Sort executed DB-side (`ORDER BY`) | IDO composes `ORDER BY` | `Sort.by(...).and(DEFAULT_SORT)` → repo → DB | **MATCH** |
| Frontend `Array.sort()` | n/a | explicitly forbidden — sort is DB-side | **MATCH** (by rule) |
| Raw sort token → SQL string concat | possible at IDO layer | wire string never concatenated; only via whitelist enum | **FORBIDDEN BY DESIGN** |
| Per-grid default order | `SortHelper` per-grid (e.g. `prn_seq:asc`) | per-endpoint `DEFAULT_SORT` reproducing legacy | **MATCH** where mappable |
| Stable secondary sort key | implicit | always `.and(DEFAULT_SORT)` appended | **APPROVED DEVIATION** (refactor is stricter/deterministic) |
| Unknown sort token handling | IDO-specific | lenient → default `ORDER BY`, never 4xx | **APPROVED DEVIATION** (standardised) |
| Sort key namespace | JSP `data-sort` attrs | View Setting `FIELD_ID` (shared with visibility + export) | **APPROVED DEVIATION** (unified) |
| Special FIELD_IDs (`CHECKBOX`/`ETC`) sortable | n/a | never in whitelist; frontend filters too | **MATCH** (both inert) |
| `SORT_YN` ↔ whitelist agreement | n/a | enforced (V41 / V154 precedents) | **MATCH** (rule-enforced) |
| Per-endpoint adoption | every grid sorted | one whitelist enum per adopted endpoint; rolled out screen-by-screen | **PARTIAL** |

## PARTIAL — adoption rollout

The common primitives (`SortDirection`, `SortableField`) are complete and stable. Per-endpoint
adoption is incremental: 28 `*SortField` enums exist as of 2026-06-23 (see `source-code-index.md`),
but not every list page in the product has wired the two params + a `Sort`-accepting repository
finder yet. New endpoints adopt in their next stabilisation pass. This is expected, not a defect —
the rule is "extend the seed + add an enum, never fork `common/web/`".

## FORBIDDEN BY DESIGN (caught at review)

These are intentionally absent — flagging them as "missing" would be wrong:

- `@Query(value = "... ORDER BY " + sortField, nativeQuery = true)` — user input in SQL.
- `Sort.by(sortField)` passing the raw wire string (the wire `FIELD_ID` is not the entity property).
- Throwing on an unknown `sortField` (degrade to default instead).
- `sortField` / `sortDirection` as path segments (`/accounts/{sortField}`).
- A per-domain `SortDirection` fork.
- Re-adding any sort-specific DB table or refactor-only object — sort owns no table.

## Open / uncertain items

| Item | Status |
|---|---|
| Exact default `ORDER BY` for Contacts + Operation Status | ⚠️ UNVERIFIED this pass — read the service before relying on it |
| Legacy `SortHelper` internals beyond the `#86` default-order citation | ⚠️ UNVERIFIED — cited as a pointer only |
| Full allowed-FIELD_ID list for the other ~23 enums | not transcribed; pattern confirmed, per-enum values read from the file |

No MISSING items in the common contract itself.
