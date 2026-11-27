# Settings > Zalo OA — backend feature

**Java package:** `com.kosign.wecms.domain.setting.zalooa`
**Route slug / menu code:** `settings-core-zalo-oa`
**Schema reference:** [../../references/database/settings/zalo-oa-schema.md](../../references/database/settings/zalo-oa-schema.md)
**Functional reference:** the WeBill365 VN production implementation, audited 2026-08-17 —
`webill365-vn-api/docs/features/zalo-oa-integration.md`
**Hướng dẫn setup console Zalo (tạo app + template):** [zalo-oa-app-setup.md](zalo-oa-app-setup.md)
**Workspace research note:** [../../../../ZALO-INTEGRATION-SETUP.md](../../../../ZALO-INTEGRATION-SETUP.md)

**Status: 🟡 Backend implemented (client + service + controller + scheduler + unit tests).**
No frontend API module yet; static-UI types exist at
`wecms-web/components/settings/zalo-oa/types.ts`. Not wired to any business event —
see [§6 Open decisions](#6-open-decisions).

> **Not a JEX port.** Zalo has no legacy JEX counterpart, so there is no `comm_*`
> WSVC to trace and the Legacy Schema Parity Rule does not apply. House conventions
> (kebab slug, `ApiResponse` envelope, snake_case wire, `BIZ_KEY` scoping, `WBE`
> codes) do.

---

## 1. What it does

Lets a tenant connect its **Zalo Official Account** to WeCMS and register the
**Zalo-approved template ids** that OA may send, then sends template messages
through Zalo's OA messaging API and logs every attempt.

Three tables, all created by `V254__zalo_oa_integration.sql`:
`ZALO_OA_INFM` (connection + OAuth tokens) · `ZALO_TMPL_INFM` (approved template ids
per type × language) · `ZALO_SEND_HIS` (send log). Column-level detail is in the
schema reference, not repeated here.

---

## 2. Layers

| Layer | Types | Note |
|---|---|---|
| Client port | `client/ZaloApiClient` + `RestClientZaloApiClient` / `MockZaloApiClient` | Two vendor surfaces only: token exchange and template send |
| Client config | `client/ZaloProperties`, `client/ZaloClientConfig` | `wecms.zalo.*`; `RestClient` with **no** base URL — see §3 |
| Client types | `ZaloTokenGrant`, `ZaloSendCommand`, `ZaloSendOutcome`, `ZaloApiException` | Vendor contract; explicit `@JsonProperty`, not the global `SNAKE_CASE` strategy |
| Tokens | `service/ZaloTokenService` | On-demand access-token refresh + the keep-alive sweep |
| Records | `service/ZaloOaService` | Connection lifecycle + template registration |
| Send | `service/ZaloMessageService`, `service/ZaloSendHistoryService` | Send, and the log write that must survive a failure |
| Schedule | `scheduler/ZaloTokenRefreshScheduler` | ShedLock `ZaloTokenRefreshJob.run` |
| REST | `controller/ZaloOaController` | 9 endpoints under `/api/v1/settings/zalo-oa` |

### Endpoints

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/settings/zalo-oa` | list connections + their templates (`page`, `size`) |
| POST | `/api/v1/settings/zalo-oa` | register an OA id — **step 1**, before the Zalo popup |
| POST | `/api/v1/settings/zalo-oa/{oa_id}/authorize` | exchange the consent code — **step 2** |
| PUT | `/api/v1/settings/zalo-oa/{oa_id}` | rename / make it the sending OA |
| DELETE | `/api/v1/settings/zalo-oa/{oa_id}` | disconnect (soft delete, tokens cleared) |
| PUT | `/api/v1/settings/zalo-oa/{oa_id}/templates` | record a Zalo-approved template id (upsert) |
| DELETE | `/api/v1/settings/zalo-oa/{oa_id}/templates` | remove a registration (`tmpl_type`, `lang_cd`) |
| GET | `/api/v1/settings/zalo-oa/send-history` | attempts, newest first |
| POST | `/api/v1/settings/zalo-oa/test-send` | diagnostic send to an explicit number — see §6 |

**Authorization:** class-level `@PreAuthorize("@menuAuthz.canAccess('settings-core-zalo-oa')")`.
`settings-*` codes are product-invariant, so the product-tree gate is skipped: an
ADMIN passes unconditionally, a normal user needs a `USE_YN='Y'` grant in
`USER_MENU_AUTH_INFM`. Class-level gating also satisfies
`EndpointAuthorizationCoverageTest` with no allow-list entry.

**No unauthenticated endpoint is added.** Zalo's OAuth redirect targets a *frontend*
route, which then calls `/authorize` with the user's JWT. The one piece that would
need a public endpoint — the delivery webhook — is deliberately not built while
**F-09 (callback hardening)** is still a PROD blocker for the two existing
unauthenticated dispatch callbacks.

---

## 3. Vendor contract (verified against the WeBill365 implementation)

```
POST https://oauth.zaloapp.com/v4/oa/access_token
     Content-Type: application/x-www-form-urlencoded
     secret_key: <app secret>          # header, not a form field
     app_id, grant_type=authorization_code|refresh_token, code|refresh_token
  -> { access_token, refresh_token, expires_in }   # expires_in is a STRING
  -> refusal: HTTP 200 with { error: <non-zero>, error_name }

POST https://business.openapi.zalo.me/message/template
     Content-Type: application/json
     access_token: <token>             # bare header, NOT Authorization
     { phone, template_id, template_data, tracking_id }
  -> { error: 0 | <code>, message, data }
```

The two surfaces are **different hosts**, which is why `ZaloClientConfig` builds a
`RestClient` with no `baseUrl` and the client passes absolute URIs. A base URL would
silently work for one surface and break the other.

Responses are read as `String` and parsed here rather than bound by message
converters, for two reasons: Zalo's token endpoint has been observed answering
`Content-Type: text/json` (which no default Spring converter claims — WeBill365
registers an extra converter to cope), and `ZALO_SEND_HIS.RES_DATA` must store what
Zalo actually said rather than the subset a DTO happens to model.

---

## 4. Rules that are load-bearing

**One live OA per tenant, enforced by the database.** `zalo_oa_infm_uk01` is a
partial unique index on `(BIZ_KEY) WHERE USE_YN='Y' AND DEL_YN='N'`. PostgreSQL
checks it **per statement, not at commit**, so `ZaloOaService.update` stands the
other connections down *and flushes* before activating the new one. The explicit
`flush()` is that ordering — removing it makes activation intermittently violate the
index.

**Zalo rotates the refresh token on every refresh and invalidates the previous one.**
Three consequences, each implemented deliberately:

- `ZaloTokenService.accessTokenFor` runs `REQUIRES_NEW`. The rotated pair must commit
  independently of the caller: if a later failure rolled the caller back, the only
  copy of the new refresh token would be discarded and the stored value would be one
  Zalo no longer accepts — recoverable only by a human redoing the consent flow.
- The row is locked (`ZaloOaInfmRepository.lockForTokenRefresh`, `FOR UPDATE`). Two
  concurrent refreshes produce two rotations where the second retires the first.
- The keep-alive sweep exists at all. A refresh token lasts ~3 months, so a tenant
  idle for a quarter would lose authorisation outright. The sweep exercises the
  rotation on connections stale for `refresh-stale-days` (29).

**No in-memory token cache.** WeBill365 caches in a per-process `ConcurrentHashMap`
(its finding §10.2); combined with rotation that guarantees stale-token failures on a
multi-instance deploy. Tokens are read from the table each time.

**A send attempt is always logged — except before the call.** `ZaloSendHistoryService`
is a separate bean with `REQUIRES_NEW` (mirroring `LoginHistoryService`) so the row
survives any downstream rollback: a message that reached Zalo was **billed** and may
have been delivered, and an unrecorded one is a charge the tenant cannot account for.
A Zalo rejection (`error != 0`) and a transport fault both produce a row plus
`success=false`. A failure *before* the call — no active OA, no template, no token —
throws and writes nothing, because nothing was attempted.

**`LANG_CD` is part of the template primary key**, so resolution at send time is an
exact lookup. WeBill365 filters on type then takes `findFirst()`, which can send the
wrong locale to a tenant that registered both (its finding §10.6).

---

## 5. Configuration

```yaml
wecms:
  zalo:
    enabled: ${WECMS_ZALO_ENABLED}                       # false -> MockZaloApiClient
    app-id: ${WECMS_ZALO_APP_ID}
    app-secret: ${WECMS_ZALO_APP_SECRET}
    token-refresh-cron: "${WECMS_ZALO_TOKEN_REFRESH_CRON}"   # "-" = never fire
```

All four are bare `${VAR}` per the 2026-07-30 convention and are listed in every
`.env.*.example`. **Adding them to `application.yml` means an `.env` without those
four lines fails startup** — they are not optional.

`enabled=false` (the shipped value) keeps `MockZaloApiClient` active, and it throws
on every call. That is stricter than the EFS mock precedent on purpose: each message
is billed (210–400 VND, cheaper by UID than by phone) and reaches a real person, so a
mock returning a fake success would let an untested send path look healthy locally.

Vendor-fixed / tuning values default in `ZaloProperties` and are intentionally absent
from the yml: `token-url`, `notify-url`, `connect-timeout-ms`, `read-timeout-ms`,
`expiry-skew-seconds`, `refresh-stale-days`.

**Secrets:** `app-id` / `app-secret` carry no default anywhere. WeBill365 hardcoded
both into its `application-local.yml` and they are in its git history (its finding
§10.1) — the same class of mistake F-05 closed here.

**Error codes** (`messages.properties` + `messages_en.properties`, 9 keys):
`WBE_ZALO_OA_NOT_FOUND`, `WBE_ZALO_OA_ALREADY_EXISTS`, `WBE_ZALO_NOT_AUTHORIZED`,
`WBE_ZALO_AUTHORIZE_FAILED`, `WBE_ZALO_TOKEN_REFRESH_FAILED`, `WBE_ZALO_NO_ACTIVE_OA`,
`WBE_ZALO_TEMPLATE_NOT_FOUND`, `WBE_ZALO_INVALID_LANG_CD`, `WBE_ZALO_DISABLED`.
A rejected *send* is deliberately not among them — it is a business outcome returned
as `success=false`, not an error envelope.

---

## 6. Open decisions

| # | Decision | Blocks |
|---|---|---|
| 1 | **Who receives a Zalo message in WeCMS?** WeBill365 sends to a *payer* of a *biller* — a two-level model WeCMS has no equivalent of. Nothing resolves a recipient yet, which is why the only send endpoint is `test-send` with an explicit number. | wiring any business event to `ZaloMessageService.send` |
| 2 | **`TMPL_TYPE` value set.** Open `VARCHAR(20)` with no CHECK. `enums/ZaloTemplateType` ships two provisional codes (`NOTICE`, `RECEIPT`) carried over from the reference's shape. | CHECK constraint, enum finalisation |
| 3 | **Shared OA with WeBill365, or a separate one?** Decides whether the template-approval cycle must be repeated. | Zalo console setup, not code |
| 4 | **ZNS or ZBS Template Message?** Zalo changed ZNS/UID policy on 2026-01-01. The implemented path is phone-addressed, matching the reference. ZBS prices UID-addressed sends ~30% lower, so adopting UID would give `RCVR_NO` a sibling column. | send path, possibly a schema addition |
| 5 | **Token encryption at rest.** `ACCESS_TOKEN` / `REFRESH_TOKEN` are plaintext, matching the reference. A DB dump exposes live credentials. | follow-up migration + converter |
| 6 | **Menu Entitlement.** `settings-core-zalo-oa` is not yet in the frontend entitlement tree (`wecms-web/components/settings/users/entitlementTree.ts`), so no grant can be issued through the UI and only an ADMIN can reach these endpoints. Fails closed, but is a gap. | normal-user access |
| 7 | **Delivery webhook.** `tracking_id` is generated, sent and persisted, and `zalo_send_his_ix02` indexes it — but there is no endpoint receiving Zalo's delivery reports, so "did it arrive?" is unanswerable. Deliberately deferred behind F-09. | delivery reconciliation |

---

## 7. Tests

| Class | Covers |
|---|---|
| `ZaloTokenServiceTest` (7) | valid-token no-op · refresh on expiry · refresh inside the skew window · never-authorised · refused refresh leaves the stored pair alone · missing connection · sweep continues past one failure |
| `ZaloMessageServiceTest` (8) | success logged · Zalo rejection returned not thrown · transport fault still logs **with the recipient** · no-active-OA writes nothing · unregistered template throws before a token is fetched · disabled Zalo as its own code · exact-language resolution · token failure not swallowed |

Both assert the invariants that cost real money or real recovery work if broken.
WeBill365 has no tests for this feature at all (its finding §10.13).

**Not covered:** `RestClientZaloApiClient` against a `MockRestServiceServer` (the
`text/json` content-type and `expires_in`-as-string quirks are handled but unverified
by test), `ZaloOaService` activation ordering, and the controller layer.

---

## 8. Defects designed out of the reference implementation

Beyond the three schema-level ones recorded in the schema doc §8:

| Reference finding | Here |
|---|---|
| §10.2 in-memory `TokenStore`, unsafe with rotation on multi-instance | no cache; DB read each time + row lock |
| §10.4 `body.get("error").asInt()` with no null check → NPE recorded as a bogus HTTP 500 | field presence checked; absence reported honestly |
| §10.5 recipient assigned *after* the call, so failure rows have no recipient | recipient captured before the call; asserted by test |
| §10.7 phone normalisation differs between two send paths | one send path, one normalisation point |
| §10.9 no OAuth `state` parameter | not resolved — the code is exchanged for a connection the caller names in the path, so a mismatched code fails at Zalo rather than binding the wrong OA, but `state` is still the correct fix |
| §4.3 logs the bare access token and full payload incl. phone | tokens never logged; phone masked (F-06 hygiene) |
| §10.3 hardcoded test phone numbers bypassing normalisation | not carried over |
| §10.12 dead `client.zalo.template.*` config | not carried over — template ids live in `ZALO_TMPL_INFM` |
