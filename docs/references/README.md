# references/

**Status:** active · **Last reviewed:** 2026-08-17

Supporting **reference material** for the WeCMS refactor — the detail layers that the feature-first docs
under [../features/](../features/) link out to. A feature doc is the spine; everything here is the detail it
points at.

> **New here (Korea / Cambodia / Vietnam developers)?** Read [../README.md](../README.md) → **Start Here** first.
> WeCMS is the refactor of the legacy Korean **JEX** system, so you will meet legacy ids (`comm_0220_001`),
> IDO/WSVC names, and Korean business terms (결재라인 → *approval line*, 사용용도 → *purpose*). All docs are in
> **English**; unfamiliar terms are explained in [../glossary.md](../glossary.md) and in [jex/README.md](jex/README.md).

## How this fits with the other doc homes

Four places hold docs — know which one to open:

| Home | What it answers | When to use |
|---|---|---|
| [../features/](../features/) | *"What does this screen do, and where is its code?"* — per-screen spec (README · business-flow · functional/interface-spec · source-code-index · test-scenarios) | **Start here** when working on a feature. The spine. |
| **references/** *(this folder)* | *"What's the exact API contract / DB schema / JEX→Spring mapping / domain rule?"* — the deep detail a feature doc links to | When a feature doc points you to a contract, schema, or mapping. |
| [../audits/](../audits/) | *"Is the refactor faithful to JEX? what decisions/gaps exist?"* — governance + per-module triplets + parity/verification history (see [../audits/README.md](../audits/README.md)) | When you need parity history, a decision record, or a known-gap list. |
| [../architecture/](../architecture/) | *"How does the system fit together?"* — cross-feature design (module boundaries, request flow, auth, error model, i18n) | When you need the big picture rather than one screen. |

## Reference folders (what each holds)

| Folder | Purpose |
|---|---|
| [api-spec/](api-spec/) | **REST API reference.** Endpoint + DTO contracts (`*-api.md`, lowercase snake_case wire shape) and the cross-feature [api-mapping.md](api-spec/api-mapping.md). Open this for "what does this endpoint accept/return?". |
| [database/](database/) | **Database reference.** Per-table schemas, the [legacy-table-inventory.md](database/legacy-table-inventory.md) (the authoritative table baseline), [table-ownership.md](database/table-ownership.md), [ERDs](database/erd/), [db-function-classification.md](database/db-function-classification.md), and [manual-tenant-setup.md](database/manual-tenant-setup.md). Open this for "what columns/keys/relationships exist?". |
| [business-rules/](business-rules/) | **Per-feature domain design** — the deep "why/how" behind a feature's logic (e.g. [internal-transfer.md](business-rules/internal-transfer.md), [contacts-design.md](business-rules/contacts-design.md), [settings/purpose-settings.md](business-rules/settings/purpose-settings.md)). Complements the matching `features/<…>/business-flow.md`. |
| [migration/](migration/) | **JEX → Spring mapping reference.** Service/field maps per feature + the [migration-playbook.md](migration/migration-playbook.md), [jex-analysis/](migration/jex-analysis/), and [screen-mapping/](migration/screen-mapping/). *This is migration-**reference** only — unrelated to the (removed) top-level `docs/migration/`.* |
| [audits/](audits/) | **Implementation parity checklists & reviews** (refactor vs JEX) supporting the build — e.g. firm-banking / own-account-transfer parity checklists, the DB parity audit, the NXiSAS spike. *Project-wide governance and per-module audits live in [../audits/](../audits/).* |
| [figma/](figma/) | **Design-source pointer.** The Figma **WB365** typography rule and the figma-first workflow for `wecms-web`. |
| [jex/](jex/) | **Legacy JEX onboarding pointer** — JEX ids, IDO/WSVC naming, ML/`WBE` codes, and how a JEX screen maps to a Spring feature. Start here if JEX terminology is unfamiliar. Authoritative JEX docs live at [../../../docs/jex/](../../../docs/jex/). |

## What goes here

- Endpoint contracts, DB schema, JEX→Spring mappings, per-feature domain design, parity checklists/spikes.
- Framework / library reference notes (Spring Boot, Next.js, Tailwind, jjwt, …).

## What does NOT go here

- Per-feature functional spec → [../features/](../features/)
- Project-wide governance / module audits → [../audits/](../audits/)
- System architecture → [../architecture/](../architecture/)
- Cross-system JEX framework reference → [docs/_shared/jex-framework.md](../../../docs/_shared/jex-framework.md)

> JEX is **read-only reference**. Cite JEX docs by relative link rather than duplicating their content here.
