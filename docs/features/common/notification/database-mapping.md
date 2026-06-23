# notification — Database Mapping (Backend)

**Part of:** Common → notification
**Last verified:** 2026-06-23

Maps the five legacy `BLTB_*` tables to their JPA entities. Source of truth:
`domain/payment/notification/entity/*.java`. All five tables are in the legacy table inventory
(`docs/references/database/legacy-table-inventory.md` lines 38–43 — `bltb_comnt_infm`,
`bltb_noti_cont_infm`, `bltb_noti_infm`, `bltb_post_infm`, `bltb_user_new_post_infm`).

> **The removed `NOTIFICATION` table (Flyway V238) is NOT used.** It was a refactor-only table with no
> legacy backing, dropped 2026-06-10 with the `domain/notification/` package. No entity in this module
> maps it. Do not re-introduce it.

## Tables ↔ entities

| Table | Entity | `@IdClass` PK | Used by |
|---|---|---|---|
| `BLTB_NOTI_INFM` | `BltbNotiInfm` | `(BIZ_KEY, BLTB_CD, NOTI_SEQ_NO)` | read (list/count/mark) + write |
| `BLTB_NOTI_CONT_INFM` | `BltbNotiContInfm` | `(BIZ_KEY, BLTB_CD, NOTI_SEQ_NO, LANG_CD)` | read JOIN + write |
| `BLTB_COMNT_INFM` | `BltbComntInfm` | `(BIZ_KEY, BLTB_CD, POST_SEQ_NO, COMNT_SEQ_NO)` | write (approve/reject remark) |
| `BLTB_POST_INFM` | `BltbPostInfm` | `(BIZ_KEY, BLTB_CD, POST_SEQ_NO)` | write (bulletin post) |
| `BLTB_USER_NEW_POST_INFM` | `BltbUserNewPostInfm` | `(BIZ_KEY, BLTB_CD, USER_ID, POST_SEQ_NO)` | write (per-approver unread tracker) |

Read path (list / unread-count / mark-read) touches only `BLTB_NOTI_INFM` ⨝ `BLTB_NOTI_CONT_INFM`
(⟕ `USER_BUSI_CONN_INFM` for the sender image). The other three are write-side only.

## `BLTB_NOTI_INFM` (`BltbNotiInfm.java`) — the core read entity

| Column | Java field | Type / len | Null | PK | Notes |
|---|---|---|---|---|---|
| `BIZ_KEY` | `bizKey` | String 20 | NN | ✓ | Tenant |
| `BLTB_CD` | `bltbCd` | String 15 | NN | ✓ | `000000000000000` (approval board) |
| `NOTI_SEQ_NO` | `notiSeqNo` | `BigDecimal` | NN | ✓ | Per-tenant seq; doubles as `notification_id` on the wire |
| `BLTB_NAME` | `bltbName` | String 50 | NN | | `""` for approval notifications |
| `SND_ID` | `sndId` | String 100 | NN | | Sender login id |
| `SND_NAME` | `sndName` | String 200 | NN | | Sender display name (search target) |
| `RCV_ID` | `rcvId` | String 100 | NN | | Recipient — scoping key (= `TenantContext` user) |
| `RCV_NM` | `rcvNm` | String 200 | NN | | Recipient name |
| `NOTI_DT` | `notiDt` | String 8 | NN | | `yyyyMMdd` |
| `NOTI_TM` | `notiTm` | String 6 | NN | | `HHmmss` |
| `NOTI_URL` | `notiUrl` | String 100 | | | Target URL |
| `ACTV_YN` | `actvYn` | String 1 | NN | | `N` unread / `Y` read — the read flag |
| `REG_ID` / `REG_DTM` | `regId` / `regDtm` | String 100 / 14 | NN | | Create audit; `regDtm` is the list sort key |
| `MOD_ID` / `MOD_DTM` | `modId` / `modDtm` | String 100 / 14 | NN | | Mark-read stamps these (read-at proxy) |
| `NOTI_WSVC` | `notiWsvc` | String 100 | | | Legacy target screen (`comm_0193_001.act`) |
| `NOTI_JSON` | `notiJson` | String 2000 | | | Navigation payload (`POST_SEQ_NO` / `APPR_DV` / …) |
| `POST_SEQ_NO` | `postSeqNo` | `BigDecimal` | | | FK to `BLTB_POST_INFM` |
| `DEL_YN` | `delYn` | String 1 | | | Soft-delete; `Y` rows still listed |

## `BLTB_NOTI_CONT_INFM` (`BltbNotiContInfm.java`) — per-language content

PK `(BIZ_KEY, BLTB_CD, NOTI_SEQ_NO, LANG_CD)`. `LANG_CD` String 2 (NN, PK). `TITL` String 100, `CONT`
String 200 (both nullable). `REG_ID` 100 / `REG_DTM` 14 (NN). One row per language; the read INNER JOIN
matches the caller's `langCd`.

## `BLTB_POST_INFM` (`BltbPostInfm.java`) — bulletin post (write side)

PK `(BIZ_KEY, BLTB_CD, POST_SEQ_NO)`. Mapped subset: `POST_TITLE`(200,NN), `POST_TYPE_CD`(2,NN, `'02'`
APPROVAL), `POST_APPR_DV`(2), `POST_STTS`(2,NN, `'PV'` PRIVATE), `APPR_SEQ_NO`(15), `POST_CONT`(TEXT),
audit cols. Unmapped DB columns (`REG_CHNL`, `PC_MOD_YN`, `REQ_TYPE`, `PIN_*`, `REF_*`) are ignored by
`ddl-auto=validate` — no schema change implied (`BltbPostInfm.java:26-30`).

## `BLTB_USER_NEW_POST_INFM` (`BltbUserNewPostInfm.java`) — unread tracker (write side)

PK `(BIZ_KEY, BLTB_CD, USER_ID, POST_SEQ_NO)`. `READ_YN` String 1 (NN, `'N'` default, `@JdbcTypeCode(CHAR)`
for the bpchar column). `POST_SEQ_NO` reuses the parent post's seq (FK), not a fresh sequence. Audit cols.

## `BLTB_COMNT_INFM` (`BltbComntInfm.java`) — approve/reject remark (write side)

PK `(BIZ_KEY, BLTB_CD, POST_SEQ_NO, COMNT_SEQ_NO)`. `COMNT_DV_CD` String 1 (NN, `@JdbcTypeCode(CHAR)`;
`'R'` remark / `'C'` comment). `COMNT_TASK_CD` String 4 (`R002` reject / `R003` complete / `R004`
approved). `COMNT_CONT` String 1000 (nullable). Audit cols.

## Key generation

`NOTI_SEQ_NO`, `POST_SEQ_NO`, `COMNT_SEQ_NO` are all allocated through the single `wb_get_next_val` funnel
(`LegacyFunctionRepository.nextVal(...)` in `ApprovalNotificationService`). No Hibernate sequence
generator, no Java `MAX+1` — legacy key-format parity.

## Type conventions

- Sequence columns map as `BigDecimal` (DB `NUMERIC`).
- All audit / timestamp columns are legacy `VARCHAR(14)` `yyyyMMddHHmmss` strings (not `TIMESTAMPTZ`),
  matching the JEX DDL.
- `CHAR(1)` columns (`READ_YN`, `COMNT_DV_CD`) carry `@JdbcTypeCode(SqlTypes.CHAR)` so `validate` accepts
  the bpchar columns (same precedent as `SysScrOptionWecms.sortYn`).
