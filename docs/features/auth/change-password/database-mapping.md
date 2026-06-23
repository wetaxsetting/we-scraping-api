# Change Password — Database Mapping (Backend)

Legacy-column → entity-field → DTO-field mapping, taken from the real entity code. Legacy DDL is the
schema source of truth; the refactor mirrors it (Legacy Schema Parity Rule). Both tables are in the
[legacy table inventory](../../../references/database/legacy-table-inventory.md): `user_infm`,
`user_pw_chg_his`.

---

## 1. Live credential — `USER_INFM`

Entity: [UserInfm.java](../../../../src/main/java/com/kosign/wecms/domain/auth/entity/UserInfm.java)
(`@Table("USER_INFM")`, PK `USER_ID`). Only the two columns this feature touches are shown — the full
column set is documented by the login / session-management features.

| DB column | Type (DEV parity) | JPA field | Read/Write here | Refactor use |
|---|---|---|---|---|
| `USER_ID` | VARCHAR(100) | `userId` (PK) | read | caller identity (from `TenantContext`, used to load the row) |
| `USER_PW` | VARCHAR(100) | `userPw` | **write** | new password hash (legacy SHA-256 base64, 44 chars) |
| `MOD_ID` | VARCHAR(100) | `modId` | write | stamped with `userId` on change |
| `PW_CHG_REQ_SEQ_NO` | NUMERIC | `pwChgReqSeqNo` | — (not read) | password-expiry pointer — **mapped but unused** (deferred; see [refactor-gap-analysis.md](refactor-gap-analysis.md)) |
| `ACTV_YN` / `LOCK_YN` / `DEL_YN` | VARCHAR(1) | `actvYn` / `lockYn` / `delYn` | read | eligibility re-check (`isActive` / `isLocked` / `isDeleted` helpers) |

> `USER_PW` is `VARCHAR(100)`, comfortably wide enough for the 44-char SHA-256 base64 hash. The same
> column is what the live JEX system reads — the refactor must keep writing the legacy format
> (coexistence). `PW_CHG_REQ_SEQ_NO` exists on the entity but the password-expiry flow that would read
> it is not implemented (MISSING).

---

## 2. Password-change ledger — `USER_PW_CHG_HIS`

Entity: [UserPwChgHis.java](../../../../src/main/java/com/kosign/wecms/domain/auth/entity/UserPwChgHis.java)
(`@Table("USER_PW_CHG_HIS")`, `@IdClass(`[UserPwChgHisId](../../../../src/main/java/com/kosign/wecms/domain/auth/entity/UserPwChgHisId.java)`)`).
No `BaseEntity` (no `BIZ_KEY`, no standard audit column set — same precedent as the sibling
`UserLoginHis`).

| # | DB column | Type (DEV parity) | JPA field | PK | Refactor use |
|---|---|---|---|---|---|
| 1 | `USER_ID` | VARCHAR(100) | `userId` | ✅ | the user whose password changed |
| 2 | `REG_SEQ_NO` | NUMERIC | `regSeqNo` (`Long`, `@JdbcTypeCode(NUMERIC)`) | ✅ | per-user MAX+1 via `wb_get_next_val` funnel |
| 3 | `USER_PW` | VARCHAR(100) | `userPw` | | the hash that was set (same value written to `USER_INFM.USER_PW`) |
| 4 | `MOD_DTM` | VARCHAR(14) | `modDtm` (`OffsetDateTime` + `LegacyDtmConverter`) | | `yyyyMMddHHmmss` — change time |
| 5 | `IP_ADDR` | VARCHAR(100) | `ipAddr` | | client IP at change time |
| 6 | `REG_DTM` | VARCHAR(14) | `regDtm` (`OffsetDateTime` + `LegacyDtmConverter`) | | `yyyyMMddHHmmss` — change time (sort key for the last-3 read) |

> **Type deviations (documented).** `REG_SEQ_NO` is mapped as `Long` (legacy NUMERIC) and
> `REG_DTM`/`MOD_DTM` as `OffsetDateTime` via `LegacyDtmConverter` over the legacy `VARCHAR(14)`
> `yyyyMMddHHmmss` shape — the same converter precedent used across the refactor. See the entity
> Javadoc (references V207 for the deviations).
>
> **Stale Javadoc flag.** The entity Javadoc says "`USER_PW` stores a BCrypt hash" — this is **STALE**.
> The live write path emits legacy SHA-256 (see [business-flow.md](business-flow.md)).

### Composite PK — `UserPwChgHisId`

```java
class UserPwChgHisId implements Serializable { String userId; Long regSeqNo; }
```

PK is **`USER_ID + REG_SEQ_NO`** — no `BIZ_KEY` (the table is keyed on the user, not the tenant).
`REG_SEQ_NO` is per-user monotonic (MAX+1 within a `USER_ID`).

### Last-3 read

```java
List<UserPwChgHis> findTop3ByUserIdOrderByRegDtmDescRegSeqNoDesc(String userId);
```

[UserPwChgHisRepository](../../../../src/main/java/com/kosign/wecms/domain/auth/repository/UserPwChgHisRepository.java)
— the three most-recent rows for the user, newest first, ordered by `REG_DTM DESC, REG_SEQ_NO DESC`
(the `REG_SEQ_NO` tiebreaker makes the order deterministic when two rows share a 14-char timestamp).
Mirrors legacy `IDO.USER_PW_CHG_HIS_R003.xml` (`ORDER BY REG_DTM DESC LIMIT 3`). The reuse check
compares the candidate plaintext against each row's `USER_PW` hash via the shared `PasswordEncoder`.

---

## 3. Key generation — `REG_SEQ_NO`

`USER_PW_CHG_HIS.REG_SEQ_NO` is allocated through the single `wb_get_next_val` funnel:

```java
row.setRegSeqNo(Long.parseLong(legacyFunctions.nextUserPwChgRegSeqNo(userId)));
```

[LegacyFunctionRepository.nextUserPwChgRegSeqNo](../../../../src/main/java/com/kosign/wecms/common/legacy/LegacyFunctionRepository.java)
→ `wb_get_next_val('user_pw_chg_his','reg_seq_no', userId, …)` — per-user MAX+1, body copied verbatim
from the live JEX function (funnel branch V140 #27). Never a Java-side `MAX+1` or `IDENTITY` column.

---

## 4. DTO → wire mapping

| Source | Entity / DTO field | Wire field (snake) |
|---|---|---|
| request | `ChangePasswordRequest.currentPassword` | `current_password` |
| request | `ChangePasswordRequest.newPassword` | `new_password` |
| request | `ChangePasswordRequest.confirmPassword` | `confirm_password` |
| response | (no payload) | `data: null` |

Password plaintext is never persisted, never returned, and never logged. The wire never carries a
hash either — `USER_PW` (both tables) stays server-side.

---

## 5. Relationships & scope

- **No tenant column.** `USER_PW_CHG_HIS` has no `BIZ_KEY` — it is keyed on `USER_ID` only. The
  caller can only operate on their own `USER_ID` (from the JWT), so cross-user access cannot happen.
- **No new tables.** Both `USER_INFM` and `USER_PW_CHG_HIS` are legacy inventory tables; the refactor
  maps them column-for-column. The `REG_SEQ_NO` branch for `user_pw_chg_his` lives in the
  `wb_get_next_val` funnel.
