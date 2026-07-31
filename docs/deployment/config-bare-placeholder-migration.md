# Config migration — bare `${VAR}` placeholders, no defaults in yml

**Date:** 2026-07-30
**Scope:** `src/main/resources/application.yml` + `application-{local,sit,uat,prod}.yml`,
and the seven `.env` / `.env.*.example` files.
**Status:** applied in the repo. **Not yet applied to any server `.env`.**

> ### ⚠️ ORDERING REQUIREMENT — read this first
>
> **The server's `.env` (or `setenv.sh`) MUST be updated BEFORE the new yml is
> deployed.** Old `.env` + new yml = **the application does not start.**
>
> This is not a theoretical risk. Verified locally: booting the `local` profile
> against an un-updated `.env.local` fails at context initialisation with
>
> ```
> Failed to bind properties under 'wecms.dispatch.callback.trust-forwarded-for' to boolean:
>     Value: "${WECMS_CALLBACK_TRUST_FORWARDED_FOR}"
>     Origin: class path resource [application-local.yml] - 175:28
>     Reason: failed to convert java.lang.String to boolean
> ```
>
> A deploy normally replaces `ROOT.war` and leaves host config untouched
> ([wecms-batch-activation-runbook.md](wecms-batch-activation-runbook.md) §2 step 4).
> That sequence is now unsafe for this change. Update host config first, confirm,
> then deploy.

---

## 1. What changed

Every `${VAR:default}` placeholder became bare `${VAR}`. **169 placeholders** across
the five config files:

| File | Defaults stripped |
|---|---|
| `application.yml` | 10 |
| `application-local.yml` | 40 |
| `application-sit.yml` | 43 |
| `application-uat.yml` | 40 |
| `application-prod.yml` | 36 |

Nothing else moved. No key added or removed, no value changed, no quoting changed
(cron placeholders stay quoted; `result-check.cron` and `recovery.cron` remain
unquoted in all four profiles, as before). Non-placeholder literals —
`driver-class-name`, `ddl-auto`, `show-sql`, the four hardcoded timeouts, logging
levels — were not touched; they are not env-controlled.

Each stripped default was added to the matching `.env.*.example` **as a value**, so
copying the template reproduces the previous behaviour exactly.

## 2. Why

A default in the yml is a value hidden where nobody looks. With one, a **misspelled
variable name is indistinguishable from "deliberately not configured"** — the app
boots on the hidden value while ops believes the `.env` is in force. Without one, the
same typo stops startup.

This mattered concretely here. Before the migration, a typo in
`WECMS_DISPATCH_AUTO_RECEIVE`, `_REALTIME_DISPATCH` or `_RECIPIENT_INQUIRY` silently
resolved to `false` — dispatch off, while payment rows still land at `PROC_STAT='9'`
and, on a shared JEX database, the legacy batch picks them up and sends them within
60 seconds. "Our flags are off" did not mean "no money moves", and nothing in the
logs said so. Likewise a typo in `WECMS_SFTP_LOCAL_UPLOAD` resolved to empty, and the
code then falls back to `java.io.tmpdir` — bank-account-bearing files written to OS
temp, lost on restart, no error at any layer.

Convention now: **values live in `.env` only; the yml declares shape.**

## 3. The accuracy caveat — bare does NOT guarantee a clear error

Bare placeholders buy fail-fast only where the binding is strict. Two mechanisms,
both verified on this codebase:

| Binding | Missing variable behaves as |
|---|---|
| `@Value` / `@Scheduled` | **STRICT** — aborts with `Could not resolve placeholder 'VAR'`, naming the variable. |
| `@ConfigurationProperties` | **LENIENT** — Spring's `PropertySourcesPlaceholdersResolver` ignores the unresolvable placeholder and binds the **literal string** `${VAR}`. |

Under lenient binding the outcome depends only on the target type:

- **boolean / int** → bind error naming the property and showing the literal in its
  `Value:` field (the `trust-forwarded-for` failure quoted above).
- **String** → **no error at all.** The literal is accepted and surfaces later as a
  domain fault that never mentions the variable. Verified: a missing
  `WECMS_JWT_SECRET` produced
  `wecms.jwt.secret must be at least 32 bytes for HS256 (current length: 19)` —
  19 being the character count of the text `${WECMS_JWT_SECRET}`.

String properties bound this way include the DB URL/username/password, all SFTP
paths, the 10 storage roots, and the Coocon/OSMC URLs and keys. **For those, a
missing variable is not caught at startup.**

So a complete `.env` matters **more** under this convention, not less. Diff the host
`.env` against the `.env.*.example` before deploying; do not treat a clean startup as
proof of a complete configuration.

## 4. Not covered — the 13 code-only properties

These are read with an in-code default and appear in no yml and no `.env`. The
"must be set in `.env`" rule does **not** apply to them, and this migration did not
change them:

| Property | Default |
|---|---|
| `wecms.dispatch.recovery.batch-size` | 50 |
| `wecms.dispatch.stuck-monitor.threshold-minutes` | 15 |
| `wecms.dispatch.stuck-monitor.re-alert-minutes` | 60 |
| `wecms.dispatch.stuck-monitor.max-alert-keys` | 20 |
| `wecms.dispatch.stuck-monitor.pay-file-batch-size` | 200 |
| `wecms.dispatch.operating-hours.zone` | `Asia/Ho_Chi_Minh` |
| `wecms.internal-transfer.routing.shinhan-bank-cd` | 970424 |
| `wecms.internal-transfer.routing.woori-bank-cd` | 970457 |
| `wecms.internal-transfer.routing.vcb-bank-cd` | 970436 |
| `wecms.internal-transfer.cutoff.refresh-interval-ms` | 300000 |
| `wecms.nxisas.timeout-seconds` | 60 |
| `wecms.nxisas.session-idle-seconds` | 300 |
| `wecms.oat.seed-decrypt.enabled` | false |

They are listed, commented out, at the end of every `.env.*.example` for
discoverability. Setting one there does nothing until a `${VAR}` line is added to the
yml — and doing that brings it under the rule above, including the deploy-ordering
requirement.

## 5. Per-profile variable checklist

The authoritative checklist is the `.env.*.example` file for the profile. Counts
(profile file + `application.yml`, which is always active underneath):

| Profile | Required variables | Checklist | Was |
|---|---|---|---|
| base only | 35 | `.env.example` | 20 bare / 15 defaulted |
| local | **68** | `.env.local.example` | 28 bare / 40 defaulted |
| sit | **72** | `.env.sit.example` | 29 bare / 43 defaulted |
| uat | **72** | `.env.uat.example` | 32 bare / 40 defaulted |
| prod | **68** | `.env.prod.example` | 32 bare / 36 defaulted |

All five examples verified complete: every `${VAR}` in the yml pair has a matching
line. Mechanical check for any profile:

```bash
cd wecms-api
P=sit    # local | sit | uat | prod
comm -23 \
  <(grep -vE '^\s*#' src/main/resources/application.yml src/main/resources/application-$P.yml \
      | grep -oE '\$\{[A-Z][A-Z0-9_]*\}' | tr -d '${}' | sort -u) \
  <(grep -vE '^\s*#' .env.$P.example | grep '=' | sed 's/=.*//' | sort -u)
```

Empty output = complete. Run the same command against the real host `.env` before
deploying.

### Per-profile values that differ — do not normalise

| Variable | local | sit | uat | prod |
|---|---|---|---|---|
| `WECMS_CALLBACK_PROTECTION` | `DISABLED` | `ENFORCE` | `ENFORCE` | `ENFORCE` |
| `WECMS_CALLBACK_TRUST_FORWARDED_FOR` | `false` | `true` | `true` | `true` |
| `WECMS_DISPATCH_STUCK_MONITOR_ENABLED` | `false` | `true` | `false`¹ | `false` |
| `WECMS_DISPATCH_STUCK_MONITOR_CRON` | `-` | `0 */5 * * * *` | `-`¹ | `-` |
| `WECMS_DISPATCH_STUCK_MONITOR_RETENTION_DAYS` | n/a² | `7` | `7` | n/a² |
| `WECMS_DISPATCH_ALERT_ENABLED` | `false` | `false`³ | `false`³ | `false` |
| `WECMS_DISPATCH_ALERT_ENV_MODE` | n/a² | `T` | `U` | n/a² |

¹ `.env.uat.example` already shipped these as `false`/`-`, overriding a yml default
of `true`/`0 */5 * * * *`. The template's values were preserved rather than raised to
the old yml default — raising them would be a behaviour change, not a migration. If
UAT is meant to run the read-only monitor, set them to `true` / `0 */5 * * * *`
deliberately.

² The key is not declared in that profile's yml, so the variable is not read there.
`retention-days` falls back to the code default of 7 in local/prod.

³ Same as ¹ — sit/uat ymls used to default `true`, but both templates shipped `false`
and an `.env` value always beat the yml default, so behaviour is unchanged. These
lines are now the only thing deciding it.

## 6. Deploy procedure

1. **Take a before-image of host config** — `env | grep -E '^(WECMS|HAENGSUNG)_'`
   (redact secrets). Keep it; it is the rollback reference.
2. **Diff host config against the profile's `.env.*.example`** using the command in
   §5, run against the host's real variable list.
3. **Add every missing variable** with the value from the example, unless the host
   deliberately differs — in which case record why.
4. **Confirm** by re-running the diff. Empty output.
5. **Only then deploy the new `ROOT.war`.**
6. **Verify startup** and check for the callback WARN, per the activation runbook
   §3.1–§3.3.

**Rollback:** if startup fails and host config cannot be corrected immediately,
redeploy the previous WAR. The old yml carries the defaults, so it starts against the
un-updated `.env`. Do not attempt to fix it by adding defaults back to one yml — that
reintroduces exactly the drift this migration removed, in one profile only.

## 7. What nobody in this repo can verify

- **`.env.uat` and `.env.prod` are server-only.** Neither exists in the repository
  (`.gitignore:42`), and nobody working from the repo can see or verify their
  contents. The `.example` files are a *template*, not a description of the hosts.
  Live evidence has already shown deployed flag states contradicting the committed
  templates (shedlock rows for jobs the templates said were `false`; `TRAN_TP='201'`
  rows implying `recipient-inquiry=true` and `efs.enabled=true` on SIT). **Ops must
  do the §6 diff on the host itself. This document cannot do it for them.**
- **`.env.local` and `.env.sit` were not updated by this change** — they are
  `Edit`-denied in `.claude/settings.json`. Their required additions (12 and 13 lines)
  are written out in
  `scratchpad/env-bare-migration-additions.md` and must be applied by hand. Until
  then **neither profile starts.**
- **On a deployed host, `setenv.sh` is the runtime authority**, not the `.env` — it
  exports OS environment variables, which override `.env` files. `setenv.sh` is
  host-owned and not in this repository.

## 8. Related

- [wecms-batch-activation-runbook.md](wecms-batch-activation-runbook.md) — §2 deploy
  steps (now subject to the ordering requirement above), §5.0 activation gates.
- [wecms-batch-environment-variables.md](wecms-batch-environment-variables.md) —
  per-variable reference for the batch subsystem. Its §12 startup-required list is
  superseded by the per-profile counts in §5 here.
- `batch-dispatch-variable-reference.md` — batch-subsystem template. Its "CRITICAL" header carries a
  dated correction: the claim that every variable there was startup-required was
  wrong *before* this migration (only 4 were). **After this migration the original
  claim is effectively true again** for the variables that appear in a yml.
