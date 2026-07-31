# VAN Callback Security Configuration (H-1)

**Date:** 2026-07-29 · **System:** Refactor (`wecms-api`) · **Status:** `FIXED` — fail-closed by default
**Applies to:** the inbound bank callbacks `01301` (bulk reception result) and `01302` (bulk execution result)
**Endpoints:** `POST /api/v1/payment/dispatch/callbacks/**`

---

## 1. Classification — this is NOT a legacy-parity issue

Verified from source on 2026-07-29, not assumed from a prior report.

| Question | Evidence | Answer |
|---|---|---|
| Does the legacy callback check the source IP? | `FILE_DEPOSIT_TRANSFER_API1_act.jsp` — grepped for `remoteaddr` / `x-forwarded` / `accessip` / `allow` / `deny` | **No.** No IP is read anywhere in either callback JSP. |
| Does the legacy web container restrict it? | `WECMS_BATCH/web/WEB-INF/web.xml` | **No.** Only the generic `JexMainFilter`; no `security-constraint`, no remote-address valve. |
| Is `x-coocon-accessIp` the legacy inbound control? | `util/ConnectionUtil.java:191` — `con.setRequestProperty("x-coocon-accessIp", getLocalServerIp())` | **No — the opposite.** It is a header WeCMS **sends** on **outbound** Coocon calls. A previous revision of `CallbackProperties`' Javadoc cited it as the legacy inbound counterpart; that was **wrong** and has been corrected. |
| What *does* authenticate the legacy callback? | `FILE_DEPOSIT_TRANSFER_API1_act.jsp:102-109` — `SecurityUtil.VerifyMac(CRYPT_KEY, inputEV, inputVV, "", false)` → throws `CD_ENC_VRFY_ERR` | **The per-VAN HMAC, and only that.** |

**Conclusion.** Legacy relied on the payload HMAC plus whatever network isolation the deployment
provided (firewall / private routing) — which is **not provable from this repository**. The refactor
keeps that identical HMAC gate untouched and *adds* an IP allow-list on top.

> **H-1 is therefore a deployment-security requirement, not a strict Java-code parity issue.**
> Fixing it does not change legacy business behaviour in any way.

## 2. The actual defect

The added control **failed open**:

```java
// CallbackIpWhitelistFilter, before the fix
if (uri == null || !uri.startsWith(props.getPathPrefix()) || props.getAllowedIps().isEmpty()) {
    chain.doFilter(request, response);   // ← empty list ⇒ EVERY source accepted
    return;
}
```

and trusted `X-Forwarded-For` unconditionally:

```java
if (props.isTrustForwardedFor()) {                 // hardcoded true in all four profile YAMLs
    String xff = request.getHeader("X-Forwarded-For");
    if (StringUtils.hasText(xff)) return xff.split(",")[0].trim();   // ← any client can forge this
}
```

Two consequences: (a) an environment that forgot `WECMS_CALLBACK_ALLOWED_IPS` accepted callbacks
from anywhere **while appearing protected**; (b) even a configured list could be bypassed by any
client setting the header. A control that advertises protection it does not provide is worse than no
control, because it stops anyone looking for the real one.

## 3. Implementation

Design uses **both** sanctioned options from the task, applied where each fits best:

* **Option A (startup validation)** for entries that are *malformed*. A typo'd rule is
  indistinguishable at request time from a legitimately-absent one, so it aborts context refresh
  with the offending entry named.
* **Option B (403)** for a list that is *well-formed but empty*. The node still boots and serves
  every non-callback endpoint while the gateway IPs are being provisioned; only callbacks are denied.

| Component | Change |
|---|---|
| `IpAllowList` (**new**) | Parses/validates literal IPv4+IPv6 and CIDR; matches on raw address bytes so an IPv4 rule never matches an IPv6 address. Hostnames rejected — the allow-list must never depend on DNS at request time. |
| `CallbackProperties` | New `protection` enum (`ENFORCE` default / `DISABLED`), new `trustedProxies` list, `@PostConstruct validate()` for fail-fast + startup logging. Javadoc corrected re: legacy. |
| `CallbackIpWhitelistFilter` | Fail-closed on empty list; `X-Forwarded-For` honoured only from a trusted proxy; left-most hop used; safe rejection logging (decision inputs only — never headers wholesale, never the payload). |

### Trust and proxy model

```text
                     ┌─ peer ∈ trusted-proxies ──► use LEFT-MOST X-Forwarded-For hop
request ─► peer IP ──┤
                     └─ peer ∉ trusted-proxies ──► IGNORE the header, use the peer IP
```

* `trust-forwarded-for: false` ⇒ the header is never consulted, regardless of the peer.
* `trusted-proxies` empty (**the default**) ⇒ no proxy is trusted ⇒ the header is always ignored.
  A deployment terminating TLS upstream must name that proxy **deliberately**.
* A header of only separators falls back to the peer address — never to allow-all.
* The chain is `client, proxy1, proxy2`; the left-most entry is the originating client. The filter
  does **not** scan the chain for *any* allowed entry — that would let an attacker append an allowed
  IP.

## 4. Configuration

```yaml
wecms:
  dispatch:
    callback:
      protection:          ${WECMS_CALLBACK_PROTECTION:ENFORCE}
      trust-forwarded-for: ${WECMS_CALLBACK_TRUST_FORWARDED_FOR:true}
      trusted-proxies:     ${WECMS_CALLBACK_TRUSTED_PROXIES:}
      allowed-ips:         ${WECMS_CALLBACK_ALLOWED_IPS}      # sit/uat/prod: NO default → fail fast
```

| Profile | `protection` | `allowed-ips` | Effect |
|---|---|---|---|
| `local` | **`DISABLED`** (explicit, WARN-logged each startup) | `${…:}` empty default | IP filtering off; HMAC still applies. Developers can POST from localhost. |
| `sit` / `uat` / `prod` | **`ENFORCE`** | **no in-yaml default** | Unset variable ⇒ startup fails. Empty/blank ⇒ every callback 403. Malformed ⇒ startup fails. |

**Behaviour matrix:**

| Condition | Result |
|---|---|
| Allow-list absent (`ENFORCE`) | **403** every callback |
| Allow-list empty / blank-only | **403** every callback |
| Invalid IP entry | **Startup aborts** — `IllegalStateException` naming the entry |
| Invalid CIDR (`/33`, `/abc`) | **Startup aborts** |
| Hostname entry | **Startup aborts** |
| Direct peer in list | **Allowed** |
| Direct peer not in list | **403** |
| Forged `X-Forwarded-For`, untrusted peer | Header **ignored**, peer matched → **403** |
| Trusted proxy + allowed origin | **Allowed** |
| Trusted proxy + blocked origin | **403** |
| Non-callback endpoint | **Unaffected**, always |

## 5. Activation

**No flag needs to be turned on.** `ENFORCE` is the default and is already active in the committed
`sit`/`uat`/`prod` profiles. What Ops must supply before those environments can receive callbacks:

1. `WECMS_CALLBACK_ALLOWED_IPS` — the real VAN gateway source IP(s), comma-separated literals and/or
   CIDR. **NEEDS CONFIRMATION from the VAN Center** (still open — `batch-dispatch-variable-reference.md` §3).
2. `WECMS_CALLBACK_TRUSTED_PROXIES` — only if TLS terminates at a reverse proxy. Leave empty
   otherwise; empty is the safe value.
3. Verify at startup: `Callback IP allow-list active: N rule(s), M trusted proxy rule(s)`.
   Seeing `Callback IP allow-list is EMPTY while protection=ENFORCE` means callbacks are being denied.

> **Deliberate operational consequence.** If the gateway IPs are wrong or unset, callbacks now fail
> **visibly** (403 + WARN per request) instead of silently succeeding from any source. That is the
> intended trade: a loud misconfiguration beats a quiet hole. If a controlled cutover needs the old
> behaviour temporarily, set `WECMS_CALLBACK_PROTECTION=DISABLED` — it is logged as a WARN on every
> startup and must be reverted.

## 6. Remaining risk

| Risk | Status |
|---|---|
| Real gateway IPs unknown | **OPEN — [CONFIRM: VAN Center]**. Until supplied, `sit`/`uat`/`prod` will 403 callbacks. |
| HMAC remains the primary gate | By design, and identical to legacy. The allow-list is defence-in-depth; it does not replace `VV` verification. |
| `protection=DISABLED` could be set in a deployed env | Mitigated by a startup WARN naming the path prefix, and by `batch-dispatch-variable-reference.md` documenting it as local-only. Not preventable in code — an operator with env access can always weaken config. |
| Multi-hop chains where the true client is not left-most | Not applicable to this topology (gateway → optional single proxy → app). If a second proxy layer is ever added, the trusted-proxy list must include it and the model re-verified. |

## 7. Tests

`CallbackIpWhitelistFilterTest` — 19 tests, all passing.

| # | Requirement | Test |
|---|---|---|
| 1 | allowed direct IP accepted | `allowedDirectIpAccepted` |
| 2 | blocked direct IP rejected | `blockedDirectIpRejected` |
| 3 | empty allow-list fails closed | `emptyAllowListFailsClosed`, `blankOnlyAllowListFailsClosed` |
| 4 | missing allow-list fails closed | `missingAllowListFailsClosed` |
| 5 | invalid IP rejected at validation | `Validation.invalidIpRejected` |
| 6 | invalid CIDR rejected | `invalidCidrRejected`, `nonNumericCidrRejected`, `invalidTrustedProxyRejected`, `hostnameRejected` |
| 7 | forged XFF from untrusted source ignored | `forgedForwardedForIgnored` |
| 8 | trusted proxy + allowed origin accepted | `trustedProxyWithAllowedOriginAccepted` |
| 9 | trusted proxy + blocked origin rejected | `trustedProxyWithBlockedOriginRejected` |
| 10 | multiple forwarded IPs parsed safely | `multipleForwardedHopsUseLeftMost`, `multiHopDoesNotScanForAnAllowedEntry`, `malformedHeaderFallsBackToPeer` |
| 11 | non-callback endpoints unaffected | `nonCallbackEndpointsUnaffected`, `nonCallbackUnaffectedByFailClosed` |
| 12 | local/mock mode explicit | `disabledProtectionAllowsAny`, `disabledIsNeverTheDefault` |
| 13 | deployed config cannot default to allow-all | `deployedDefaultsCannotAllowAll` |
| 14 | existing callback controller tests pass | `BulkCallbackRoutingTest` 2/2, `BulkReceptionResultServiceTest` 12/12, `BulkExecutionResultServiceTest2` 4/4 |
| — | CIDR + IPv4/IPv6 isolation | `cidrMatching`, `ipv4RuleDoesNotMatchIpv6`, `trustForwardedForOffIgnoresHeader`, `blankEntriesTolerated` |
