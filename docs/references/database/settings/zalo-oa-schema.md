# Zalo OA — Database Schema (Refactor)

**Flyway migration:** `V254__zalo_oa_integration.sql`
**Java package:** `com.kosign.wecms.domain.setting.zalooa`
**Feature doc (service layer, endpoints, vendor contract):** [../../../features/settings/zalo-oa.md](../../../features/settings/zalo-oa.md)
**Setup console Zalo (app + template):** [../../../features/settings/zalo-oa-app-setup.md](../../../features/settings/zalo-oa-app-setup.md)
**Frontend types:** [wecms-web/components/settings/zalo-oa/types.ts](../../../../../wecms-web/components/settings/zalo-oa/types.ts)
**Status:** schema + entity layer landed; DDL **applied to the shared dev DB `172.168.5.172/wecmsdevdb` on 2026-08-17** and verified (see §9). Service layer (client / token / send / controller / scheduler) landed the same day — see the feature doc.

> **Scope of this document: the three tables only.** Endpoints, the Zalo HTTP
> contract, token-rotation handling and configuration live in the feature doc linked
> above. The open decisions in §10 are duplicated there as a single list — when they
> diverge, the feature doc is the one being maintained.

> **Not a JEX port.** Zalo has no legacy JEX counterpart, so the Legacy Schema
> Parity Rule (`wecms-api/CLAUDE.md`) does not apply — there is no DEV table to
> mirror column-for-column. These three tables are refactor-native. House
> conventions still hold: UPPER_SNAKE identifiers, `_INFM` / `_HIS` suffixes,
> `BIZ_KEY` on every tenant-scoped table, the `REG_ID` / `REG_DTM` / `MOD_ID` /
> `MOD_DTM` audit quartet as `varchar(14)`, Y/N flags as `bpchar(1)` + CHECK.

**Functional reference:** the WeBill365 VN implementation, audited 2026-08-17 —
`webill365-vn-api/docs/features/zalo-oa-integration.md`. That audit is the source
for §8; three defects it found are designed out of this schema rather than
carried over.

---

## 1. Tables touched

| Table | Role | Migration ownership |
|---|---|---|
| `ZALO_OA_INFM` | Owning table — one Official Account connected by one company, with its OAuth tokens | This feature (V254) |
| `ZALO_TMPL_INFM` | Zalo-approved template ids bound to an OA, per type × language | This feature (V254) |
| `ZALO_SEND_HIS` | Outbound send log, one row per Zalo API call | This feature (V254) |
| `SQ_ZALO_SEND_SEQ` | Sequence backing `ZALO_SEND_HIS.SEND_SEQ` | This feature (V254) |
| `BUSI_INFM` | Tenant registry — FK target for `ZALO_OA_INFM.BIZ_KEY` | Owned by `domain/auth` |

---

## 2. `ZALO_OA_INFM` — column-for-column DDL

**Column count: 13.** Primary key: `(BIZ_KEY, OA_ID)`.

| # | Column | Type | Length | Nullable | Default | Notes |
|---|---|---|---|---|---|---|
| 1 | `BIZ_KEY` | `VARCHAR` | 20 | NOT NULL | — | Tenant key. PK. FK → `BUSI_INFM`. |
| 2 | `OA_ID` | `VARCHAR` | 50 | NOT NULL | — | Zalo Official Account ID. PK. Immutable once created. |
| 3 | `OA_NM` | `VARCHAR` | 100 | NULL | — | Display name. Not used when sending. |
| 4 | `ACCESS_TOKEN` | `VARCHAR` | 1000 | NULL | — | Short-lived Zalo access token. Plaintext — see §10. |
| 5 | `REFRESH_TOKEN` | `VARCHAR` | 1000 | NULL | — | Long-lived token. Zalo **rotates** it on every refresh. |
| 6 | `TOKEN_EXPI_DTM` | `VARCHAR` | 14 | NULL | — | Absolute access-token expiry, UTC `yyyyMMddHHmmss`. Null before the first exchange. |
| 7 | `AUTH_CD` | `VARCHAR` | 255 | NULL | — | Most recent OAuth code. Troubleshooting only — a code is single-use. |
| 8 | `USE_YN` | `CHAR` | 1 | NOT NULL | `'N'` | `Y` = outbound sends use this OA. At most one per tenant. |
| 9 | `DEL_YN` | `CHAR` | 1 | NOT NULL | `'N'` | `Y` = disconnected (soft delete). |
| 10 | `REG_ID` | `VARCHAR` | 100 | NOT NULL | — | Audit. |
| 11 | `REG_DTM` | `VARCHAR` | 14 | NOT NULL | — | Audit, UTC `yyyyMMddHHmmss`. |
| 12 | `MOD_ID` | `VARCHAR` | 100 | NULL | — | Audit. |
| 13 | `MOD_DTM` | `VARCHAR` | 14 | NULL | — | Audit. Drives the token-refresh scan — see §5. |

**Constraints**

| Name | Kind | Definition |
|---|---|---|
| `zalo_oa_infm_pk` | PRIMARY KEY | `(BIZ_KEY, OA_ID)` |
| `zalo_oa_infm_chk01` | CHECK | `USE_YN IN ('Y','N')` |
| `zalo_oa_infm_chk02` | CHECK | `DEL_YN IN ('Y','N')` |
| `zalo_oa_infm_fk01` | FOREIGN KEY | `BIZ_KEY` → `BUSI_INFM(BIZ_KEY)` |

---

## 3. `ZALO_TMPL_INFM` — column-for-column DDL

**Column count: 11.** Primary key: `(BIZ_KEY, OA_ID, TMPL_TYPE, LANG_CD)`.

| # | Column | Type | Length / Precision | Nullable | Default | Notes |
|---|---|---|---|---|---|---|
| 1 | `BIZ_KEY` | `VARCHAR` | 20 | NOT NULL | — | Tenant key. PK. |
| 2 | `OA_ID` | `VARCHAR` | 50 | NOT NULL | — | Owning OA. PK. |
| 3 | `TMPL_TYPE` | `VARCHAR` | 20 | NOT NULL | — | Message-type code. PK. **Unconstrained** — see §10. |
| 4 | `LANG_CD` | `VARCHAR` | 3 | NOT NULL | — | `EN` / `KO` / `KM` / `VI`, matching `common.enums.LangCd`. PK. |
| 5 | `TMPL_ID` | `VARCHAR` | 50 | NOT NULL | — | Template id issued by Zalo on approval, e.g. `358731`. |
| 6 | `UNIT_PRC` | `NUMERIC` | (20,6) | NULL | — | Zalo's per-message charge. Reference only; no billing logic reads it. |
| 7 | `USE_YN` | `CHAR` | 1 | NOT NULL | `'Y'` | `N` parks a template without deleting it. |
| 8–11 | `REG_ID` / `REG_DTM` / `MOD_ID` / `MOD_DTM` | | | | | Audit quartet, as §2. |

**Constraints**

| Name | Kind | Definition |
|---|---|---|
| `zalo_tmpl_infm_pk` | PRIMARY KEY | `(BIZ_KEY, OA_ID, TMPL_TYPE, LANG_CD)` |
| `zalo_tmpl_infm_chk01` | CHECK | `USE_YN IN ('Y','N')` |
| `zalo_tmpl_infm_fk01` | FOREIGN KEY | `(BIZ_KEY, OA_ID)` → `ZALO_OA_INFM(BIZ_KEY, OA_ID)` |

Templates cannot be created from WeCMS. Each one is drafted in the Zalo Business
console and **manually approved by Zalo**, which then issues `TMPL_ID`. This table
only records that id so the send path can quote it. Approval lead time is outside
our control and belongs in sprint planning, not in a dev estimate.

---

## 4. `ZALO_SEND_HIS` — column-for-column DDL

**Column count: 18.** Primary key: `(SEND_SEQ)`.

| # | Column | Type | Length | Nullable | Notes |
|---|---|---|---|---|---|
| 1 | `SEND_SEQ` | `NUMERIC` | (18) | NOT NULL | PK, drawn from `SQ_ZALO_SEND_SEQ`. |
| 2 | `BIZ_KEY` | `VARCHAR` | 20 | NOT NULL | Tenant. No FK — see below. |
| 3 | `OA_ID` | `VARCHAR` | 50 | NULL | Null if the send failed before an OA was resolved. |
| 4 | `TMPL_ID` | `VARCHAR` | 50 | NULL | |
| 5 | `TMPL_TYPE` | `VARCHAR` | 20 | NULL | |
| 6 | `LANG_CD` | `VARCHAR` | 3 | NULL | |
| 7 | `RCVR_NO` | `VARCHAR` | 50 | NULL | Recipient phone, E.164 digits, exactly as sent. |
| 8 | `TRACKING_ID` | `VARCHAR` | 50 | NULL | UUID passed to Zalo as `tracking_id`. |
| 9 | `REQ_DATA` | `TEXT` | — | NULL | Serialized request body. |
| 10 | `REQ_DTM` | `VARCHAR` | 14 | NULL | |
| 11 | `RES_DATA` | `TEXT` | — | NULL | Zalo response, or a local error marker when the call threw. |
| 12 | `RES_DTM` | `VARCHAR` | 14 | NULL | |
| 13 | `RSLT_CD` | `VARCHAR` | 20 | NULL | Zalo `error` as text (`0` = success), or HTTP status / local marker. |
| 14 | `RSLT_MSG` | `VARCHAR` | 500 | NULL | |
| 15–18 | `REG_ID` / `REG_DTM` / `MOD_ID` / `MOD_DTM` | | | | Audit quartet. |

**No foreign key to `ZALO_OA_INFM`** — deliberate. The log must outlive a
disconnected OA, and a send that failed before any OA was resolved still deserves
a row. `BIZ_KEY` likewise carries no FK so a row is never blocked from being
written.

`TEXT` rather than `JSON` / `JSONB`: nothing queries into the payload, and the
codebase has no `jsonb` precedent. Revisit if a report ever needs to filter on a
template parameter.

---

## 5. Indexes and the invariants they enforce

| Index | Table | Definition | Why |
|---|---|---|---|
| `zalo_oa_infm_uk01` | `ZALO_OA_INFM` | `UNIQUE (BIZ_KEY) WHERE USE_YN='Y' AND DEL_YN='N'` | **One live OA per tenant.** The reference implementation enforces this in service code only, which leaves the invariant open to a concurrent update. A partial unique index closes it. |
| `zalo_oa_infm_ix01` | `ZALO_OA_INFM` | `(MOD_DTM) WHERE USE_YN='Y' AND DEL_YN='N'` | Backs the token-refresh scan, which selects by staleness across all tenants. |
| `zalo_send_his_ix01` | `ZALO_SEND_HIS` | `(BIZ_KEY, REG_DTM DESC)` | Send-history list, newest first, per tenant. |
| `zalo_send_his_ix02` | `ZALO_SEND_HIS` | `(TRACKING_ID)` | Correlate an inbound delivery report back to its send. Unused today — the webhook does not exist — but shipping the index now means adding it needs no migration. |

**Consequence of `zalo_oa_infm_uk01` for service code:** activating an OA must
deactivate the tenant's others **in the same transaction**, or the write is
rejected. `ZaloOaInfmRepository.findByBizKeyAndUseYnAndDelYnAndOaIdNot(...)` exists
for exactly that step.

---

## 6. JPA entity mapping

`ddl-auto: validate`, so every mapped column must exist with a compatible type.

| Table | Entity | Id class |
|---|---|---|
| `ZALO_OA_INFM` | `entity/ZaloOaInfm` | `entity/ZaloOaInfmId` |
| `ZALO_TMPL_INFM` | `entity/ZaloTmplInfm` | `entity/ZaloTmplInfmId` |
| `ZALO_SEND_HIS` | `entity/ZaloSendHis` | — (single-column PK) |

All three `extends BaseEntity`, inheriting the audit quartet. Unlike most feature
entities, these map the **full** column set — there is no "Sprint subset" here,
because the tables were designed for this feature rather than inherited.

**Repositories** — every request-scoped method constrains on `BIZ_KEY`, per the
tenant-isolation rule:

| Repository | Notable methods |
|---|---|
| `ZaloOaInfmRepository` | `findByBizKeyAndUseYnAndDelYn` (the active OA, `Optional` because `uk01` guarantees at most one) · `findByBizKeyAndUseYnAndDelYnAndOaIdNot` (stand-down set) · `findStaleForRefresh(staleDays)` |
| `ZaloTmplInfmRepository` | `findByBizKeyAndOaIdAndTmplTypeAndLangCdAndUseYn` — exact PK lookup |
| `ZaloSendHisRepository` | `findByBizKeyOrderByRegDtmDesc` · `findByBizKeyAndTrackingId` |

`findStaleForRefresh` is the **one** method that is deliberately not
tenant-scoped: it runs from a scheduled job with no request context, so there is
no `TenantContext` to read. It must never be reachable from a controller.

---

## 7. Hibernate `validate` traps

- **V254 must be applied before the app starts.** With `ddl-auto: validate`, an
  unapplied migration means startup fails on the first missing relation, not at
  first use.
- **`SQ_ZALO_SEND_SEQ` is validated too.** `ZaloSendHis` uses
  `GenerationType.SEQUENCE`, so Hibernate checks the sequence exists. It is
  created in the same migration. `allocationSize = 1` must stay in step with the
  sequence's `INCREMENT BY 1`; raising one without the other silently skips ids.
- **`bpchar` needs the JDBC-type hint.** `USE_YN` / `DEL_YN` carry
  `@JdbcTypeCode(SqlTypes.CHAR)` + `columnDefinition = "CHAR(1) DEFAULT 'x'"`,
  matching the `TbFirmConf` precedent. Dropping the hint makes Hibernate expect
  `varchar` and fail validation against `bpchar`.
- **`TOKEN_EXPI_DTM` stores UTC.** It uses `LegacyDtmConverter`, which normalises
  to UTC on write and tolerates malformed strings on read (returns null rather
  than throwing). Anything comparing this column in raw SQL must account for that
  — see the next point.
- **UTC vs session timezone in native SQL.** `findStaleForRefresh` compares
  `MOD_DTM` against `CURRENT_TIMESTAMP`. `TO_TIMESTAMP` interprets its input in
  the *session* timezone, but the column holds UTC, so the query casts to a naive
  timestamp and re-anchors: `TO_TIMESTAMP(mod_dtm, 'YYYYMMDDHH24MISS')::timestamp
  AT TIME ZONE 'UTC'`. Omitting that skews the comparison by the session offset
  (7 hours on `Asia/Ho_Chi_Minh`). Immaterial at a 29-day threshold, wrong at a
  1-hour one.

---

## 8. Design decisions vs the WeBill365 reference

Three defects from the 2026-08-17 audit are designed out here, and two pieces of
dead weight are not carried over.

| # | WeBill365 behaviour | Consequence there | Here |
|---|---|---|---|
| 1 | Tenant↔OA link (`msg_auth`) is split from tokens (`msg_auth_token`), keyed by a **globally UNIQUE** `client_id` | Two tenants connecting the same Official Account share one token row and overwrite each other's credentials | Link and tokens are **one row** keyed `(BIZ_KEY, OA_ID)`. Tenants cannot collide. |
| 2 | "One active OA per tenant" enforced in Java only | Open to a concurrent update writing a second active row | Partial unique index `zalo_oa_infm_uk01` |
| 3 | Template lookup filters on type, then `findFirst()` | A tenant holding both `vi` and `en` templates can be shown, and send, the wrong locale | `LANG_CD` is part of the PK — resolution is an exact lookup |

**Not carried over**

- `OPR_STS` — a third status flag on `msg_auth` that had no effect on send
  behaviour; display-only state that read as a control.
- `client.zalo.template.notify` / `.payment` config keys — dead config in the
  reference (no Java reads them). Template ids belong in `ZALO_TMPL_INFM`.
- Opaque template type codes `'001'` / `'002'` — replaced by readable codes so a
  log line can be understood without a lookup table.

**Also improved:** the reference's log table stores only request/response blobs,
so support cannot answer "did this recipient get the message, under which
template?" without parsing JSON. `RCVR_NO`, `TMPL_ID`, `TMPL_TYPE`, `LANG_CD`,
`TRACKING_ID` and `RSLT_CD` are first-class columns here.

---

## 9. Migration status

**Applied to `172.168.5.172:5432/wecmsdevdb` on 2026-08-17.** `psql -1 -f` ran the
whole file in one transaction: 3 tables, 1 sequence, 4 indexes, 11 comments, no
errors. Verified against §11 — column counts 13 / 11 / 18, all 8 constraints and
7 indexes present, and every mapped entity column resolves (see §7).

**Why it had not been applied before.** Not the reason an earlier revision of this
section gave. That revision claimed a Flyway version collision at `V250` / `V251`
(`V250__tax_declaration_items_ct42a_ct42b.sql` vs `V250__trco_rspb_infm.sql`, and
`V251__sys_trco_infm_dis_nm.sql` vs `V251__tax_declarations_annex_alloc_split.sql`)
was aborting every migration. **That premise is wrong: Flyway is no longer a
dependency of this project** — there is no `flyway-core` in `build.gradle`, so
nothing resolves `db/migration/` at runtime and no collision can be raised. The
duplicate filenames are real and still worth renumbering for human clarity, but
they were never the blocker.

The actual reason is simply that **nobody ran the file.** Since Flyway was
removed, everything under `src/main/resources/db/migration/` is a **manual DBA
script** — the two VAT files above say so in their own headers (*"Manual DBA
Script. Flyway disabled/removed. Execute manually in DEV -> UAT -> PROD."*). A new
`V…sql` therefore does nothing until someone executes it by hand. The
`spring.flyway.out-of-order` key still sitting in `application-local.yml` is dead
config and reads misleadingly.

**Consequences worth carrying forward**

- **`ddl-auto: validate` turns an unrun script into a startup failure**, not a
  runtime one. Adding an entity without running its DDL means the app refuses to
  boot on the first missing relation.
- **The file is now idempotent.** Every `CREATE TABLE` / `CREATE SEQUENCE` /
  `CREATE INDEX` carries `IF NOT EXISTS`, so the same file re-runs safely on
  DEV / UAT / PROD — required now that execution is manual and has no history
  table to guard it. This follows the "make migrations idempotent" rule in
  `wecms-api/CLAUDE.md`; the e-Invoice pair (`V250__trco_rspb_infm.sql`,
  `V251__sys_trco_infm_dis_nm.sql`) is **not** idempotent and will fail on a
  second run.
- **`local` points at a shared DB**, not a developer-local instance — running DDL
  there affects everyone on it. These three tables are new and used only by this
  feature, so the blast radius was nil, but that is a property of this change, not
  a general licence.

**Still to run elsewhere:** UAT / PROD have not had this DDL applied.

---

## 10. Open decisions

| # | Decision | Blocked artifact |
|---|---|---|
| 1 | **Who receives Zalo messages in WeCMS?** WeBill365 sends to a *payer* of a *biller* — a two-level model with no WeCMS equivalent. Until this is settled, the recipient-resolution path cannot be designed. | Controller, service, `TMPL_TYPE` catalogue |
| 2 | **`TMPL_TYPE` value set.** Deliberately left as an open `VARCHAR(20)` with no CHECK constraint. `enums/ZaloTemplateType` ships two provisional values (`NOTICE`, `RECEIPT`) carried over from the reference's shape. Add the CHECK in a follow-up migration once the catalogue is agreed. | CHECK constraint, enum finalisation |
| 3 | **Shared OA with WeBill365, or a separate one?** Decides whether the template-approval cycle has to be repeated. Raised but unanswered in [ZALO-INTEGRATION-SETUP.md](../../../../../ZALO-INTEGRATION-SETUP.md). | Zalo console setup, not code |
| 4 | **ZNS or ZBS Template Message?** Zalo changed its ZNS/UID policy on 2026-01-01 and introduced ZBS Template Message. The reference implementation targets the older phone-addressed endpoint; ZBS also prices UID-addressed sends lower than phone-addressed ones. If WeCMS adopts UID addressing, `RCVR_NO` gains a sibling column. | Send path, possibly a schema addition |
| 5 | **Token encryption at rest.** `ACCESS_TOKEN` / `REFRESH_TOKEN` are plaintext, matching the reference. A DB dump exposes live credentials. | Follow-up migration + converter |

---

## 11. Verification queries (post-migration sanity)

```sql
-- 1. All three tables and the sequence exist
SELECT table_name FROM information_schema.tables
WHERE table_name IN ('zalo_oa_infm','zalo_tmpl_infm','zalo_send_his');
SELECT sequencename FROM pg_sequences WHERE sequencename = 'sq_zalo_send_seq';

-- 2. Column counts: expect 13 / 11 / 18
SELECT table_name, count(*) FROM information_schema.columns
WHERE table_name IN ('zalo_oa_infm','zalo_tmpl_infm','zalo_send_his')
GROUP BY table_name ORDER BY table_name;

-- 3. Constraints present
SELECT conrelid::regclass AS tbl, conname, contype
FROM pg_constraint
WHERE conrelid::regclass::text LIKE 'zalo\_%'
ORDER BY 1, 2;

-- 4. The one-live-OA invariant is a real index, not just service code
SELECT indexname, indexdef FROM pg_indexes WHERE indexname = 'zalo_oa_infm_uk01';

-- 5. The invariant actually bites (expect: second INSERT raises unique_violation)
--    Run inside a transaction and ROLLBACK.
```
