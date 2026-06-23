# references/jex/

**Reference material for legacy parity and migration analysis** — a read-only mirror, **not** a source of truth
and **not** onboarding curriculum.

> **Not part of onboarding.** You do **not** need to learn JEX to start developing on WeCMS. New developers
> start at [../../README.md](../../README.md) → **Start Here** and work from the Spring Boot code. Open this
> folder **only when you are porting a feature** and need to confirm legacy behavior or trace a JEX id back to
> its refactor counterpart.

## Purpose

Quick-reference notes pulled from the JEX source to support **parity checks and migration analysis** while
porting a feature: framework conventions, ML-code patterns, service-id ↔ feature mapping, common DAO shapes,
audit-table contracts.

---

## Legacy ID decoder — for parity / migration work (not a prerequisite to start)

Use this when a refactor doc or migration note cites a JEX id (e.g. `comm_0220_001_r001`) and you need to find
the legacy behavior it ports. While porting a feature you **read** the JEX source as the functional spec, then
build a clean Spring Boot + Next.js equivalent — you do **not** copy JEX code. The four legacy naming systems
below decode any JEX id; once you can read an id like `comm_0220_001_r001`, the rest follows.

> All WeCMS docs are in **English**. Korean business terms are in [../../glossary.md](../../glossary.md)
> (결재라인 → approval line, 사용용도 → purpose, 계좌 → account). JEX is **read-only** — never edit it; the
> authoritative JEX docs are under [../../../docs/jex/](../../../../docs/jex/) and the live source under `WECMS DEV/`.

### 1. JEX screen / service IDs — `module_NNNN_NNN`

A JEX id reads `<module>_<screen>_<variant>`:

| Part | Meaning | Example |
|---|---|---|
| `comm` | **module prefix** — which functional area | `comm` = common/settings |
| `0220` | **screen number** (4 digits) within that module | screen 0220 |
| `001` | **variant / sub-screen** (3 digits) | variant 001 |

So `comm_0220_001` = "common module, screen 0220, variant 001" (it happens to be Firm Banking Settings).

**Common module prefixes you will meet:**

| Prefix | Area | Refactor module |
|---|---|---|
| `logn` | Login / auth | `auth` |
| `comm` | Common / Settings (contacts, approval line, chart of accounts, firm-banking, users, purpose, …) | `settings`, `common` |
| `fnsh` | Banking — accounts / inquiry | `banking` |
| `trns` | Banking — transactions | `banking` |
| `rept` | Reports (daily cash / daily financial / cash flow) | `banking/reports` |
| `pymn` | Payment (scheduled / result, transfers) | `payment` |
| `card` | Corporate card | `payment/corporate-card` |
| `einv` / `aosv` | e-Invoice / Add-on Service | `addon-service` |

### 2. IDO names — `IDO.<TABLE>_<verb><n>.xml` (the SQL layer)

An **IDO** is a JEX data-object: an XML file holding one SQL statement against one table. Naming is
`IDO.<TABLE_NAME>_<VERB><number>.xml`:

| Verb | Operation | Example |
|---|---|---|
| `R` | read / SELECT | `IDO.USER_INFM_R004.xml` — the 4th read query on `USER_INFM` |
| `C` | create / INSERT | `IDO.TRCO_INFM_C001.xml` |
| `U` | update | `IDO.APPR_LINE_INFM_U001.xml` |
| `D` | delete | `IDO.CHART_ACCT_INFM_D001.xml` |

When a refactor doc says "mirrors `IDO.USER_INFM_R004`", it means the Spring repository query reproduces that
legacy SELECT. The table name in the IDO is the real DB table (kept column-for-column in the refactor).

### 3. WSVC names + action JSPs — the request layer

- **WSVC** (`WSVC.<screen_id>_<action><n>.xml`) is the web-service definition that wires a screen action to its
  IDOs — e.g. `WSVC.logn_0001_001_r001.xml` (login screen, read action 001).
- **Action JSP** (`<screen_id>_<action><n>_act.jsp`) holds the server-side logic — e.g.
  `comm_0220_002_c001_act.jsp` (create action). **View JSP** (`<screen_id>_view.jsp`) is the screen markup.
- Action suffix follows the IDO verbs: `r`=read, `c`=create, `u`=update, `d`=delete, + a number.

So `comm_0222_003_u001_act.jsp` = common module, screen 0222, sub 003, **update** action 001 — this is the kind of
file a refactor doc cites as the functional source of truth for a Spring service method.

### 4. Error / message codes — legacy ML codes → `WBExxxx` / `WBIxxxx`

- Legacy JEX uses **ML (message/label) codes** like `P00001`.
- The refactor **does not reuse** them. It uses two WeCMS code families on the wire:
  - **`WBExxxx`** = **W**eCMS **B**usiness **E**rror (e.g. `WBE1009` = ID/password mismatch, `WBE_VALIDATION`).
  - **`WBIxxxx`** = **W**eCMS **B**usiness **I**nfo / success (e.g. `WBI0000` = Success).
- Every refactor error response carries `status.code` (a `WBE*`), a localized message, and a `trace_id`. The full
  registry is [../../error-codes.md](../../error-codes.md); when porting, translate the JEX ML code to a new
  `WBE*`/`WBI*` and record the mapping in the feature's migration doc.

### 5. How a JEX reference becomes a Spring feature

```
JEX:     comm_0220_001  (screen id)   →   IDO.TB_FIRM_CONF_R001.xml (SQL)   →   comm_0220_001_r001_act.jsp (logic)
                              │ port (read JEX as spec, rebuild clean)
                              ▼
Refactor:  slug  firm-banking-settings
           REST  /api/v1/settings/firm-banking-settings        (kebab slug, never the WSVC id)
           code  domain.setting.firmbankingsettings.*          (Controller → Service → Repository → Entity)
           docs  features/settings/firm-banking-settings/**     (the spec)
                 references/migration/<slug>.md                 (the field-by-field JEX→Spring map)
```

**Rule:** new identifier surfaces (REST URIs, packages, classes, route slugs) use **kebab-case slugs**, never
legacy WSVC ids. Legacy ids survive only in (1) `file:line` citations inside docs/Javadoc and (2) migration-
traceability prose under [../migration/](../migration/). To find the slug for a JEX id, see the per-feature
migration map in [../migration/](../migration/) and the feature folder under [../../features/](../../features/).

---

## What goes here

- Per-topic JEX cheatsheets (`<topic>.md`)
- Service-id → feature-slug lookup tables
- ML-code → `WBE*` conventions (legacy `comm_0220_001_*` and how it maps to the new slug)

## Authority

JEX is **read-only reference**. The authoritative JEX docs live under [`../../../jex/`](../../../../docs/jex/).
Notes here are convenience extracts for refactor designers — when in doubt, read the live JEX source under
`WECMS DEV/` and the per-feature spec under `docs/jex/features/`.

## What does NOT go here

- New behaviour proposals (those belong in [`../../features/`](../../features/))
- JEX bug history (that lives under [`../../../jex/features/`](../../../../docs/jex/features/))
