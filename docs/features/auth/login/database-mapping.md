# Login — Database Mapping (Backend)

Legacy-column → entity-field mapping for the five tables the login flow touches, taken from the real
entity code. Legacy JEX DDL is the schema source of truth; the refactor mirrors it column-for-column
(Legacy Schema Parity Rule). All five tables are in the
[legacy table inventory](../../../references/database/legacy-table-inventory.md):
`USER_INFM`, `USER_BUSI_CONN_INFM`, `BUSI_INFM`, `BUSI_ADMN_INFM`, `USER_LOGIN_HIS`.

| Table | Role in login | Key columns | Access |
|---|---|---|---|
| `USER_INFM` | Credential + flag gates | PK `USER_ID` | read (verify-only) |
| `USER_BUSI_CONN_INFM` | Active tenant links + last-login marker | PK `(USER_ID, BIZ_KEY)` | read + write (`LST_LGN_DATE`) |
| `BUSI_INFM` | Company display name, product type, tax id | PK `BIZ_KEY` | read |
| `BUSI_ADMN_INFM` | Authority (ADMIN/USER) | PK `(BIZ_KEY, USER_ID)` | read |
| `USER_LOGIN_HIS` | Audit row per terminal attempt | PK `(USER_ID, REG_DTM, REG_UID)` | write |

---

## 1. `USER_INFM` — credential + flag gates

Entity: [UserInfm.java](../../../../src/main/java/com/kosign/wecms/domain/auth/entity/UserInfm.java)
(`@Table("USER_INFM")`, PK `USER_ID`, extends `BaseEntity`). Login reads only the columns below; the rest
exist for direct legacy data copy.

| DB column | Type (DEV parity) | JPA field | Login use |
|---|---|---|---|
| `USER_ID` | VARCHAR(100) | `userId` (`@Id`) | lookup key |
| `USER_NM` | VARCHAR(100) | `userNm` | display name (response + JWT) |
| `USER_PW` | VARCHAR(100) | `userPw` | SHA-256 hash — verified, never written |
| `EML` | VARCHAR(100) | `eml` | (not read by login) |
| `ACTV_YN` | VARCHAR(1) | `actvYn` | gate via `isActive()` → `WBE1010` |
| `LOCK_YN` | VARCHAR(1) | `lockYn` | gate via `isLocked()` → `WBE1011` |
| `DEL_YN` | VARCHAR(1) | `delYn` | gate via `isDeleted()` → `WBE1128` |
| `LST_LGN_DATE` | VARCHAR(14) `yyyyMMddHHmmss` | `lstLgnDate` (`@Convert LegacyDtmConverter`, `OffsetDateTime`) | mapped, not updated by login (the link-table copy is the one stamped) |
| `PW_CHG_REQ_SEQ_NO` | NUMERIC | `pwChgReqSeqNo` (`BigDecimal`) | mapped, unused (password-expiry deferred) |

Boolean helpers `isActive()` / `isLocked()` / `isDeleted()` test `"Y".equals(...)`.

---

## 2. `USER_BUSI_CONN_INFM` — active tenant links

Entity: [UserBusiConnInfm.java](../../../../src/main/java/com/kosign/wecms/domain/auth/entity/UserBusiConnInfm.java)
(`@Table("USER_BUSI_CONN_INFM")`, `@IdClass(UserBusiConnInfmId.class)`, extends `BaseEntity`). The link
between a user and a company; the source of multi-company resolution and the `LST_LGN_DATE` write.

| DB column | Type | JPA field | PK | Login use |
|---|---|---|---|---|
| `USER_ID` | VARCHAR(100) | `userId` | ✅ | composite key |
| `BIZ_KEY` | VARCHAR(20) | `bizKey` | ✅ | resolved tenant |
| `EMPL_NO` | VARCHAR(20) | `emplNo` | | (not read) |
| `USER_IMG` | VARCHAR(500) | `userImg` | | (not read by login) |
| `LST_LGN_DATE` | VARCHAR(14) | `lstLgnDate` (`@Convert`, `OffsetDateTime`) | | **written** on finalize (`OffsetDateTime.now()`) |
| `MOB_LST_LGN_DATE` | VARCHAR(14) | `mobLstLgnDate` | | (not read) |
| `DEL_YN` | VARCHAR(1) | `delYn` | | active filter (`= 'N'`) |

Legacy `USER_BUSI_CONN_INFM` carries **no `AUTH_GR_CD` column** (confirmed on DEV 2026-05-11) — authority
comes from `BUSI_ADMN_INFM` instead.

---

## 3. `BUSI_INFM` — company display name, product type, tax id

Entity: [BusiInfm.java](../../../../src/main/java/com/kosign/wecms/domain/auth/entity/BusiInfm.java)
(`@Table("BUSI_INFM")`, PK `BIZ_KEY`, extends `BaseEntity`, ~40 columns for direct copy). Login reads
exactly three columns from the single `findById` round-trip.

| DB column | Type | JPA field | Login use |
|---|---|---|---|
| `BIZ_KEY` | VARCHAR(20) | `bizKey` (`@Id`) | lookup key |
| `CMPN_NM` | VARCHAR(200) | `cmpnNm` | response `cmpn_nm` + JWT `userNm`-adjacent display (empty string when null) |
| `PRDT_TYPE` | VARCHAR(1) | `prdtType` | verbatim letter → response `prdt_type` + JWT `prdtType` claim (null preserved) |
| `TAX_ID` | VARCHAR(40) | `taxId` | response `tax_id` (Lock Screen popup; null preserved) |
| `NTNL_CD` | VARCHAR(2) | `ntnlCd` | mapped, not read by login (post-auth gate deferred) |
| `BUSI_STTS` | VARCHAR(1) | `busiStts` | mapped, not read by login (post-auth gate deferred) |

---

## 4. `BUSI_ADMN_INFM` — authority resolution

Entity: [BusiAdmnInfm.java](../../../../src/main/java/com/kosign/wecms/domain/auth/entity/BusiAdmnInfm.java)
(`@Table("BUSI_ADMN_INFM")`, `@IdClass(BusiAdmnInfmId.class)`, extends `BaseEntity`). Presence of a row
for `(BIZ_KEY, USER_ID)` is the entire authority signal.

| DB column | Type | JPA field | PK | Login use |
|---|---|---|---|---|
| `BIZ_KEY` | VARCHAR(20) | `bizKey` | ✅ | composite key |
| `USER_ID` | VARCHAR(100) | `userId` | ✅ | composite key |
| `USER_NM` | VARCHAR(100) | `userNm` | | (not read by login) |

`existsByBizKeyAndUserId(bizKey, userId)` → `ADMIN` if present, else `USER` (single-link finalize).
`findAllByUserId(userId)` resolves per-option authority for the chooser response.

---

## 5. `USER_LOGIN_HIS` — login audit (write)

Entity: [UserLoginHis.java](../../../../src/main/java/com/kosign/wecms/domain/auth/entity/UserLoginHis.java)
(`@Table("USER_LOGIN_HIS")`, `@IdClass(UserLoginHisId.class)`, **does not extend `BaseEntity`** — it has
its own audit columns). Written by `LoginHistoryService` on every terminal attempt.

| DB column | Type | JPA field | PK | Written value |
|---|---|---|---|---|
| `USER_ID` | VARCHAR(100) | `userId` | ✅ | attempted user id |
| `REG_DTM` | VARCHAR(14) `yyyyMMddHHmmss` | `regDtm` (**String**, not `OffsetDateTime`) | ✅ | UTC now (`WB_FORMATTER_DTM14`) |
| `REG_UID` | VARCHAR(50) | `regUid` | ✅ | `UUID.randomUUID()` tiebreaker (refactor PK addition matching DEV `(USER_ID, REG_DTM, REG_UID)`) |
| `IP_ADDR` | VARCHAR(100) | `ipAddr` | | client IP |
| `LGN_CHNL` | VARCHAR(3) | `lgnChnl` | | `"PC"` (`CHNL_PC`) |
| `RSLT_CD` | VARCHAR(10) | `rsltCd` | | `"SUCCESS"` / `"FAIL"` |
| `RSLT_CONT` | VARCHAR(200) | `rsltCont` | | FAIL reason tag, or `null` on SUCCESS |

> `REG_DTM` is intentionally mapped as `String varchar(14)` (not `OffsetDateTime` + converter) to match
> the live DDL column-for-column; the value is generated UTC-now in `LoginHistoryService`, mirroring
> legacy `UserLoginHisDAO.create` → `DateTimeUtils.utcNow()` + `WB_FORMATTER_DTM14`.

---

## 6. Relationships & scope

- **Tenant isolation** — `BIZ_KEY` is the (or a) leading PK column on every tenant-scoped table; the
  resolved `biz_key` always comes from the user's own link rows, never from a request param.
- **Login → link → company** chain: `USER_INFM (USER_ID)` → `USER_BUSI_CONN_INFM (USER_ID, BIZ_KEY)` →
  `BUSI_INFM (BIZ_KEY)`; authority is the side-lookup `BUSI_ADMN_INFM (BIZ_KEY, USER_ID)`.
- **No new tables.** Every table is a legacy inventory table mapped column-for-column. No key-generation
  funnel is involved on the login path — `USER_LOGIN_HIS.REG_DTM` / `REG_UID` are produced in Java
  (timestamp + UUID), not via `wb_get_next_val`.
