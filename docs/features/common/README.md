# features/common/

**Cross-cutting system domain** — shared, screen-agnostic features reused by every menu group
(Banking / Payment / Add-on / Settings) of the refactor.

This is not a WeCMS top-menu group. It is a **system feature group** for capabilities that are wired
once and consumed product-wide. It was formalized 2026-06-01 to resolve **OQ-DOC-2** (see
[`../../planning/open-questions/documentation-follow-up.md`](../../planning/open-questions/documentation-follow-up.md)).

## Docs

| Doc | Scope | Status |
|---|---|---|
| [view-setting.md](view-setting.md) | Column visibility / order / size persistence per user × screen (a.k.a. Screen Options) | Working (backend implemented) |
| [audit-log.md](audit-log.md) | Cross-feature shared writer for `AUDIT_LOGS_INFM` (locale fan-out, REQUIRES_NEW, slug `WSVC_ID`) | Working (backend implemented) |

## Scope of this domain

| Common feature | This domain? | Where it is documented |
|---|---|---|
| **View Setting / Screen Options** | ✅ yes | [view-setting.md](view-setting.md) |
| **Excel / XLSX export** | shared pipeline | `wecms-api/CLAUDE.md` §"Excel export — `common/export/`"; per-screen specs live on the owning feature doc |
| **Audit logging** (`AUDIT_LOGS_INFM`) | ✅ yes (shared writer) | [audit-log.md](audit-log.md) — shared service; design detail in [`../../references/business-rules/security/README.md`](../../references/business-rules/security/README.md) + shared [`../../../../docs/security/`](../../../../docs/security/) |
| **Common API envelope / codes** | reference | [`../../../../docs/jex/api-spec/common.md`](../../../../docs/jex/api-spec/common.md) (JEX) + [`../../error-codes.md`](../../error-codes.md) |
| **Tenant context** | design | resolved per request from the JWT — see [`login-auth.md`](../auth/login-auth.md) + security design |
| **Approval workflow** (approval-LINE vs approval-BOX) | documented per-feature | [`../settings/approval-line.md`](../settings/approval-line.md) (config) + [`../payment/approval-box.md`](../payment/approval-box.md) (execution). Unifying cross-link doc pending **OQ-DOC-12**. |
| **Attachments / file upload** | per-feature | Contacts file upload in [`../settings/contacts.md`](../settings/contacts.md); ERP / firm-banking downloads in their specs. Generic doc pending **OQ-DOC-12**. |
| Notifications · Dashboard | **not yet in scope** | no refactor endpoint exists (**OQ-DOC-13**); add a doc here when the feature lands. Do **not** pre-create empty stubs. |

## What does NOT live here

- Per-table column lists for View Setting → live on the **owning** feature spec under
  [`../banking/`](../banking/) / [`../payment/`](../payment/) / [`../addon-service/`](../addon-service/) /
  [`../settings/`](../settings/), referenced from the screen registry.
- Frontend hook / dialog architecture → [`../../../../wecms-web/docs/architecture/view-setting/README.md`](../../../../wecms-web/docs/architecture/view-setting/README.md).

## Naming traceability

Slug `view-setting` matches the JEX counterpart
[`../../../../docs/jex/features/view-setting.md`](../../../../docs/jex/features/view-setting.md) and the
frontend counterpart `wecms-web/docs/features/common/view-setting.md`.
