# Auth > Profile — Database Mapping (Backend)

Legacy-column → entity/projection-field → DTO-field → UI-field mapping for the auth-profile surface,
taken from the real code. Legacy DDL is the schema source of truth; the refactor mirrors it
column-for-column (Legacy Schema Parity Rule). All tables are in the
[legacy table inventory](../../../references/database/legacy-table-inventory.md): `user_infm`,
`user_busi_conn_infm`, `busi_infm`.

> This surface is **read-only** — it owns no entity of its own. It reads through the existing
> [UserBusiConnInfmRepository](../../../../src/main/java/com/kosign/wecms/domain/auth/repository/UserBusiConnInfmRepository.java)
> projection (`findTenantUserPicker`), which joins `USER_INFM` to `USER_BUSI_CONN_INFM`. There is no
> auth-profile `@Entity`, no migration, no key generation.

---

## 1. The directory projection — `USER_INFM` ⋈ `USER_BUSI_CONN_INFM`

The `GET /api/v1/users` projection (`TenantUserProjection`) selects five JPQL aliases across the two
joined entities. `USER_INFM` supplies identity / name / position / department; `USER_BUSI_CONN_INFM`
supplies tenant scope + soft-delete + the avatar presence flag.

| # | DB column | Source table | JPQL alias | Projection getter | DTO field (wire) | UI use |
|---|---|---|---|---|---|---|
| 1 | `USER_ID` | `USER_INFM` (PK) | `userId` | `getUserId()` | `TenantUserRow.user_id` | row key / picker value |
| 2 | `USER_NM` | `USER_INFM` | `userNm` | `getUserNm()` | `TenantUserRow.user_nm` | approver display name; `q` filter target |
| 3 | `POSI_NM` | `USER_INFM` | `posiNm` | `getPosiNm()` | `TenantUserRow.posi_nm` | position label |
| 4 | `DEPT_NM` | `USER_INFM` | `deptNm` | `getDeptNm()` | `TenantUserRow.dept_nm` | department label |
| 5 | `USER_IMG` | `USER_BUSI_CONN_INFM` | `userImg` | `getUserImg()` | *(never on wire — drives `user_img_url`)* | per-tenant avatar source |

`TenantUserRow.user_img_url` is **derived**, not a column: when `USER_IMG` is non-blank the service
emits the slug-based template `/api/v1/users/{userId}/image`; otherwise `null`. The raw `USER_IMG`
value never reaches the wire.

### Filter / scope predicates (not projected)

| DB column | Source table | Used as |
|---|---|---|
| `BIZ_KEY` | `USER_BUSI_CONN_INFM` (PK part) | tenant scope — `c.bizKey = TenantContext.currentBizKey()` (never a param) |
| `DEL_YN` | `USER_BUSI_CONN_INFM` | active-only — `c.delYn = 'N'` |
| `USER_NM` | `USER_INFM` | `q` substring — `LOWER(USER_NM) LIKE LOWER('%'||:q||'%')` |

---

## 2. `USER_INFM` (identity / name — read for the directory)

Entity: [UserInfm.java](../../../../src/main/java/com/kosign/wecms/domain/auth/entity/UserInfm.java)
(`@Table("USER_INFM")`, PK `USER_ID`). Columns relevant to this surface (the entity maps more; only the
projected / filtered ones are listed):

| DB column | Type (DEV parity) | JPA field | Profile use |
|---|---|---|---|
| `USER_ID` | VARCHAR(100) | `userId` | PK; picker value |
| `USER_NM` | VARCHAR(100) | `userNm` | display name; `q` filter (≤100) |
| `POSI_NM` | VARCHAR | `posiNm` | position label |
| `DEPT_NM` | VARCHAR | `deptNm` | department label |
| `CLPH_NO` | VARCHAR (encrypted) | `clphNo` | **not** read here — legacy `decrypt()`; surfaced only by Settings > Users management grid |

---

## 3. `USER_BUSI_CONN_INFM` (tenant link + avatar flag)

Entity: [UserBusiConnInfm.java](../../../../src/main/java/com/kosign/wecms/domain/auth/entity/UserBusiConnInfm.java)
(`@Table("USER_BUSI_CONN_INFM")`, composite PK `(USER_ID, BIZ_KEY)` via `UserBusiConnInfmId`). Columns
relevant to this surface:

| DB column | Type (DEV parity) | JPA field | Profile use |
|---|---|---|---|
| `USER_ID` | VARCHAR(100) | `userId` (PK) | join key to `USER_INFM` |
| `BIZ_KEY` | VARCHAR(20) | `bizKey` (PK) | tenant scope predicate |
| `DEL_YN` | VARCHAR(1) | `delYn` | active-link filter (`'N'`) |
| `USER_IMG` | (legacy avatar slot) | `userImg` | **per-tenant avatar source** — presence drives `user_img_url`; value never on wire |
| `EMPL_NO` | VARCHAR | `emplNo` | not read here |
| `LST_LGN_DATE` | VARCHAR | `lstLgnDate` | not read here (login path) |

`USER_BUSI_CONN_INFM.USER_IMG` is the **per-tenant** avatar source — a user can carry a different
avatar at each company they link to. The auth directory reads only its presence; the bytes are
written / served by **Settings > Users** (`UserImageService`, out of auth scope).

---

## 4. `BUSI_INFM` (company identity — backs `/me`, cross-link)

Entity: [BusiInfm.java](../../../../src/main/java/com/kosign/wecms/domain/auth/entity/BusiInfm.java)
(`@Table("BUSI_INFM")`, PK `BIZ_KEY`). **Not** read by `GET /api/v1/users`; listed here because it
backs the **session identity** surface (`GET /api/v1/auth/me`, session-management module):

| DB column | JPA field | `/me` use |
|---|---|---|
| `BIZ_KEY` | `bizKey` (PK) | tenant key |
| `CMPN_NM` | `cmpnNm` | company display name on `MeResponse.cmpn_nm` |
| `TAX_ID` | `taxId` | `MeResponse.tax_id` (re-queried each `/me` call) |
| `PRDT_TYPE` | `prdtType` | echoed from JWT, not re-read for product gating |

Full `/me` column mapping lives in the session-management module — this row exists only so the
auth-profile reader knows where the session's company identity comes from.

---

## 5. Relationships & scope

- **Tenant isolation:** `BIZ_KEY` is the leading PK column on `USER_BUSI_CONN_INFM`; the directory join
  is scoped to `TenantContext.currentBizKey()`, so cross-tenant rows cannot surface by accident.
- **Join shape:** `USER_BUSI_CONN_INFM c JOIN USER_INFM u ON u.userId = c.userId` — one directory row
  per active tenant link, name/position/department from the master, avatar flag from the link.
- **No new tables, no entity, no key generation.** Everything is read through the existing auth
  repository projection; there is no auth-profile-owned mapping, migration, or `wb_get_next_val` branch.
