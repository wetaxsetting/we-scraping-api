# notification — Refactor Gap Analysis (Backend)

**Part of:** Common → notification
**Last verified:** 2026-06-23

Refactor vs legacy JEX `comm_0192_007`. Verdict vocabulary: **MATCH** (refactor reproduces legacy) ·
**APPROVED DEVIATION** (intentional, signed off) · **DEAD LEGACY** (removed refactor code / table — never
revive) · **PARTIAL** (subset implemented) · **MISSING** (not implemented; open decision).

## Summary

| # | Area | Verdict |
|---|---|---|
| G1 | List / unread-count / mark-one / mark-all (BLTB branch) | MATCH |
| G2 | Tenant + recipient scoping from JWT | MATCH (refactor hardens — never reads request) |
| G3 | Search on `SND_NAME`, paging, default sort | MATCH |
| G4 | `DEL_YN='Y'` rows kept in list | MATCH |
| G5 | Producer leg (request / approve / reject / withdraw) | MATCH |
| G6 | Removed `domain/notification/` package + `NOTIFICATION` table (V238) | **DEAD LEGACY** |
| C1 | General notification branch (`TYPE='G'`, `GENL_NOTI_*`) | PARTIAL / MISSING — open decision |
| C2 | No per-user notification delete endpoint | MATCH-with-legacy — open decision (confirm) |
| G7 | `PUSH_NOTICE_INFM` / `MSG_SND_HIS` on request-approval path | MATCH (legacy skips them too) |
| G8 | Approval-detail popup content (click-through target) | PARTIAL — see parity audit |

## Detail

### G1–G4 — read path MATCH
The four read/mark behaviours reproduce `comm_0192_007_r001`/`r002`/`u001`: Unread tab = `ACTV_YN='N'`;
All = no filter; count always over unread; sort `REG_DTM DESC` (+ `NOTI_SEQ_NO DESC` deterministic
tiebreaker); search `ILIKE` on `SND_NAME`; soft-deleted rows retained. Localized title/content via the
`BLTB_NOTI_CONT_INFM` INNER JOIN on the caller's language. Verified across `NotificationServiceTest`.

### G2 — scoping (MATCH, hardened)
Legacy reads `RCV_ID` + `BIZ_KEY` from session; the refactor reads them from `TenantContext` (signed JWT)
and the wire intentionally carries neither — a request can't override the recipient. Strictly safer than
legacy, same observable result.

### G5 — producer leg MATCH
`ApprovalNotificationService` reproduces the request-approval fan-out (1 `BLTB_POST_INFM` + per-approver
`BLTB_USER_NEW_POST_INFM` + `BLTB_NOTI_INFM` × 3 content rows EN/KO/VI) and the approve/reject leg
(`BLTB_COMNT_INFM` remark + per-recipient notification) and withdraw cleanup (soft-delete noti, hard-delete
the rest). Keys via the `wb_get_next_val` funnel. See `business-flow.md`.

### G6 — DEAD LEGACY (removed module + table)
The original refactor-only `domain/notification/` package + the **`NOTIFICATION` table (Flyway V238)**
were **removed 2026-06-10** — a refactor-only table with no legacy backing breaks the legacy-first /
migration-safety rule (memory `project_notification_module_removed.md`). The surface was re-based on the
legacy `BLTB_*` tables. **Never document the removed version as live; never re-create a refactor-only
notification table.** This is the canonical "use legacy BLTB tables, not a refactor-only table" precedent.

### C1 — General notification branch (PARTIAL / MISSING, open decision)
Legacy `comm_0192_007_r001` UNIONs a General-notification branch (`TYPE='G'` over `GENL_NOTI_*`). Those
tables are **out of the refactor legacy-table-inventory**, so the refactor implements the **BLTB branch
only** (`NotificationController.java:18-22`). Producer never writes `TYPE='G'` rows, so the read side has
nothing of that type to show — internally consistent. **Open decision:** whether `GENL_NOTI_*` ever enters
the inventory (and gets ported) is a PO call. Until then this is intentionally PARTIAL. See the popup
analysis §2.4.

### C2 — no delete endpoint (MATCH-with-legacy, open decision)
There is no per-user "delete this notification" endpoint. Legacy `comm_0192_007` does not expose one on
this surface either — `DEL_YN='Y'` is set only as a producer-side **withdraw** side effect
(`ApprovalNotificationService.cleanupApprovalArtifacts` → `softDeleteByPost`), and soft-deleted rows still
list (then show `WBI1098` on click). So the absence is **MATCH-with-legacy**, recorded as an open decision
only to confirm no future product requirement needs a user-initiated delete.

### G7 — push/message rows MATCH
`PUSH_NOTICE_INFM` / `MSG_SND_HIS` are not written on the request-approval path — legacy
`pushToMobileNoti` returns early (no `dvCd`), so the refactor omits them too
(`ApprovalNotificationService.java:53-58`).

### G8 — approval-detail popup content PARTIAL
Clicking an approval notification opens the Approval Details popup on the current page (navigation parity
is correct), but the popup currently shows the approval header / status / approver chain / content and is
**missing the transaction-detail rows, attachments, and the Approve/Reject/Edit/Withdraw actions**. This
is a frontend-plus-small-read-only-backend follow-up, not a notification-module gap per se — full detail in
[../../../audits/notification-approval-detail-popup-parity-audit.md](../../../audits/notification-approval-detail-popup-parity-audit.md).

## Open decisions (carry forward)

- **C1** — port the General `TYPE='G'` branch? Blocked on `GENL_NOTI_*` entering the inventory (PO).
- **C2** — confirm no user-initiated delete is needed (MATCH-with-legacy today).
