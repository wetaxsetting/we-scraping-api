# WeCMS Mobile — Auth / account — Field-Level Spec

Part of the [mobile-app migration mapping](README.md) set. 10 of the 76 legacy mobile services. Source: `WeCMS_Mobile_INOUT정의서(영수증관리API)_v2024.1.46.xlsx` (one sheet per service).

Legacy service ids (`WABOOKS_MREC_*`) appear here as **historical pointers** only, per the workspace identifier-naming rule. Field names are quoted in the legacy `UPPER_SNAKE` form used by the mobile gateway; any refactor endpoint that serves them returns lowercase `snake_case` JSON through the global Jackson strategy (see [docs/README.md](../../../README.md) §field-name casing).

---

<a id="wabooks_mrec_r001"></a>

## `WABOOKS_MREC_R001` — Company lookup for a user (user id + password)

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_R001` |
| Sheet name (source) | `WABOOKS_MREC_R001` |
| Sheet title (원문) | 사업장조회 |
| Business area (업무) | 영수증관리API |
| Screen (source index) | — |
| Legacy web reference | — |
| Legacy tables (테이블) | 1. USER_INFM <br> 2. USER_BUSI_CONN_INFM (v2023.1.0.2 추가) <br> 3. BUSI_INFM |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | `POST /api/v1/auth/login` |
| Coverage | **PARTIAL** |
| Last spec revision | — |

**Delta.** The refactor login authenticates and issues the JWT in one call, with a multi-company chooser inside the response. The app needs a first step that only returns the `BIZ_KEY` list plus `LOCK_YN` / `PW_ERR_CNT` before a company is picked, so either a mode flag or a separate endpoint is required.

### Input — 2 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `USER_ID` | C | 100 | Y | 사용자ID |  |
| `USER_PW` | C | 100 | Y | 사용자비밀번호 |  |

### Output — 7 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `LOCK_YN` | C | 1 |  | 잠김여부 | v2023.1.0.7 add |
| `PW_ERR_CNT` | C | 1 |  | 패스워드오류횟수 | v2023.1.0.7 add |
| `REC` | R |  |  | 그룹 |  |
| ↳ `BIZ_KEY` | C | 20 |  | 사업자키 |  |
| ↳ `CMPN_NM` | C | 200 |  | 회사명 |  |
| ↳ `CMPN_ENM` | C | 200 |  | 회사영문명 |  |
| ↳ `TAX_ID` | C | 40 |  | 세금ID |  |

### Process rules (verbatim from the sheet)

Input section:

```text
1, 2번 테이블을 USER_ID,  DEL_YN: ‘N’ (table 2) v2023.1.0.13
2, 3번 테이블을 BIZ_KEY로 Inner join하여 조회. (v2023.1.0.2 수정)

(v2023.1.0.7 updated)
Process like action logn_0001_001_r000
```

---

<a id="wabooks_mrec_r002"></a>

## `WABOOKS_MREC_R002` — Log in with the selected company - returns token + profile

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_R002` |
| Sheet name (source) | `WABOOKS_MREC_R002` |
| Sheet title (원문) | 로그인하기(사업장선택) |
| Business area (업무) | 영수증관리API |
| Screen (source index) | — |
| Legacy web reference | — |
| Legacy tables (테이블) | 1. USER_INFM <br> 2. USER_LOGIN_HIS <br> 3. BUSI_INFM <br> 4. USER_MENU_AUTH_INFM <br> 5. MENU_AUTH_ADMN_SYS <br> 6. BUSI_ENV_SET_INFM <br> 7. SYS_ENV_SET_INFM <br> 8. BUSI_USAG_SET_INFM <br> 9. SYS_USAG_SET_INFM <br> 10. TRCO_GRP_BSC_CSTM <br> 11. TRCO_GRP_BSC_SYS <br> 12. BUSI_ADMIN_INFM <br> 13. USER_SCR_OPTION <br> 14. SYS_SCR_OPTION <br> 15. USER_POINT_INFM <br> 16. USER_BUSI_CONN_INFM (v2023.1.0.3 추가) |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | `POST /api/v1/auth/login` |
| Coverage | **PARTIAL** |
| Last spec revision | Update v2024.1.33 |

**Delta.** The app reads `ACCESS_TOKEN` / `TOKEN_TYPE` plus 17 company/user profile fields from this call. JWT issuance already exists; the field names have to be mapped onto the mobile envelope.

### Input — 3 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `BIZ_KEY` | C | 20 | Y | 사업자키 |  |
| `USER_ID` | C | 100 | Y | 사용자ID |  |
| `USER_PW` | C | 100 | Y | 사용자비밀번호 |  |

### Output — 17 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `BIZ_KEY` | C | 20 |  | 사업자키 | 1번 테이블 조회결과 |
| `CMPN_NM` | C | 200 |  | 회사명 | 1번 테이블 조회결과 |
| `CMPN_ENM` | C | 200 |  | 회사영문명 | 1번 테이블 조회결과 |
| `TAX_ID` | C | 40 |  | 세금ID | 3번 테이블 조회결과 |
| `USER_ID` | C | 100 |  | 사용자ID | 1번 테이블 조회결과 |
| `USER_NM` | C | 100 |  | 사용자명 | 1번 테이블 조회결과 |
| `EML` | C | 100 |  | 이메일 | 1번 테이블 조회결과 |
| `RPPR_NM` | C | 100 |  | 대표자명 | 3번 테이블 조회결과 |
| `RPRS_BSUN_POST_ADRS` | C | 200 |  | 대표사업장우편주소 | 3번 테이블 조회결과 (v2023.1.0.10 추가) |
| `RPRS_BSUN_DTL_ADRS` | C | 200 |  | 대표사업장상세주소 | 3번 테이블 조회결과 (v2023.1.0.10 추가) |
| `RPRS_BSUN_CITY` | C | 200 |  | 대표사업장시도 | 3번 테이블 조회결과 (v2023.1.0.10 추가) |
| `RPRS_BSUN_ST` | C | 200 |  | 대표사업장국가 | 3번 테이블 조회결과 (v2023.1.0.10 추가) |
| `CLPH_NO` | C | 100 |  | 휴대폰번호 | 1번 테이블 조회결과 (v2023.1.0.10 추가) |
| `ACCESS_TOKEN` | C | 500 |  | Access Token | (2021/06/08) 추가 <br> 모바일View 보안용으로 생성된 Access Token |
| `TOKEN_TYPE` | C | 100 |  | Access Token Type | (2021/06/08) 추가 <br> 모바일View 보안용으로 생성된 Access Token의 Type |
| `USER_IMG_URL` | C | 200 |  | User Image Url | v2024.1.33 added <br> Table 1: USER_IMG is not null where USER_ID: {USER_ID} >> {Base URL}/szon_0012_003_r002.act?USER_ID={USER_ID} |
| `USER_IMG` | C | 500 |  | User Image | v2024.1.33 added >> Table 1 |

### Process rules (verbatim from the sheet)

Input section:

```text
※ 아래 순서대로 진행.

1. S-Zone login_0001_001_r001의 1 ~ 6번과 동일하게 진행.

2. 1번 테이블의 MOB_LST_LGN_DATE가 Null이 아니면 3번으로 진행.
  2-1. 13번 테이블에 데이터를 Insert. WEB_SVC_ID, FIELD_ID, DISP_ORD, USE_YN, SIZE는 14번 테이블에 INSERT되어있는대로 INSERT.
  (이 때, WEB_SVC_ID가 'WABOOKS_MREC_R004' 인 항목만 INSERT.)

3. 16번 테이블의 MOB_LST_LGN_DATE를 SYSDATE로 UPDATE. (v2023.1.0.3 변경)

4. 2번 테이블에 데이터 INSERT. IP_ADDR은 request.getRemoteAddr(), LGN_CHNL은 'MOB', RSLT_CD는 'SUCCESS'로 넣으면 됨.

5. Output에 데이터를 담고 W/S 종료.

※ 주의사항 : 테이블 SELECT 시, BIZ_KEY만 PK로 존재하는 테이블은  BIZ_KEY만 조회조건에 넣으며, USER_ID가 같이 PK로 있는 테이블은 USER_ID까지 같이 조회조건에 넣어야 함.
```

---

<a id="wabooks_mrec_r003"></a>

## `WABOOKS_MREC_R003` — Log out

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_R003` |
| Sheet name (source) | `WABOOKS_MREC_R003` |
| Sheet title (원문) | 로그아웃 |
| Business area (업무) | 영수증관리API |
| Screen (source index) | — |
| Legacy web reference | — |
| Legacy tables (테이블) | 1. PUSH_DEVICE_MSTR |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | `POST /api/v1/auth/logout` |
| Coverage | **PARTIAL** |
| Last spec revision | — |

**Delta.** The refactor endpoint is a stub - the client drops the token, nothing is blacklisted server-side.

### Input — 1 field

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `DEVICE_ID` | C | 200 | Y | 디바이스ID | Device UUID |

### Output — 0 fields

_No output payload beyond the common `RSLT_CD` / `RSLT_MSG` envelope._

### Process rules (verbatim from the sheet)

Input section:

```text
※ Dummy Action.

Table 1: 
Delete where biz_key=: Session ,user_id=: Session ,device_id=: Input and del_yn = 'N' (v2023.1.0.14 add)
```

---

<a id="wabooks_mrec_mbmr_0002_r001"></a>

## `WABOOKS_MREC_MBMR_0002_R001` — Find ID - request an SMS OTP

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_MBMR_0002_R001` |
| Sheet name (source) | `WABOOKS_MREC_MBMR_0002_R001` |
| Sheet title (원문) | 아이디찾기_휴대전화 인증요청(모바일API) |
| Business area (업무) | 영수증관리API |
| Screen (source index) | — |
| Legacy web reference | — |
| Legacy tables (테이블) | MSG_SND_HIS |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | — (none) |
| Coverage | **MISSING** |
| Last spec revision | — |

**Delta.** No SMS OTP flow exists in wecms-api.

### Input — 2 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `USER_NM` | C | 100 | Y | 사용자명 |  |
| `CLPH_NO` | C | 100 | Y | 휴대전화번호 |  |

### Output — 1 field

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `SND_RSLT` | C | 5 |  | 발신결과 |  |

### Process rules (verbatim from the sheet)

Input section:

```text
mbmr_0002_001_r001 과 프로세스 동일
```

---

<a id="wabooks_mrec_mbmr_0002_r002"></a>

## `WABOOKS_MREC_MBMR_0002_R002` — Find ID - verify the OTP

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_MBMR_0002_R002` |
| Sheet name (source) | `WABOOKS_MREC_MBMR_0002_R002` |
| Sheet title (원문) | 아이디찾기_휴대전화 인증확인(모바일API) |
| Business area (업무) | 영수증관리API |
| Screen (source index) | — |
| Legacy web reference | — |
| Legacy tables (테이블) | — |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | — (none) |
| Coverage | **MISSING** |
| Last spec revision | — |

### Input — 3 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `USER_NM` | C | 100 | Y | 사용자명 |  |
| `CLPH_NO` | C | 100 | Y | 휴대전화번호 |  |
| `CRTC_NUM` | C | 100 | Y | 인증번호 |  |

### Output — 1 field

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `CRTC_YN` | C | 1 |  | 인증여부 |  |

### Process rules (verbatim from the sheet)

Input section:

```text
mbmr_0002_001_r002 과 프로세스 동일
```

---

<a id="wabooks_mrec_mbmr_0002_r003"></a>

## `WABOOKS_MREC_MBMR_0002_R003` — Find ID - return the user id

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_MBMR_0002_R003` |
| Sheet name (source) | `WABOOKS_MREC_MBMR_0002_R003` |
| Sheet title (원문) | 아이디찾기_아이디조회 처리(모바일API) |
| Business area (업무) | 영수증관리API |
| Screen (source index) | — |
| Legacy web reference | — |
| Legacy tables (테이블) | USER_INFM |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | — (none) |
| Coverage | **MISSING** |
| Last spec revision | — |

### Input — 2 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `USER_NM` | C | 100 | Y | 사용자명 |  |
| `CLPH_NO` | C | 100 | Y | 휴대전화번호 |  |

### Output — 5 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `REC` | R |  |  | 그룹 |  |
| ↳ `USER_ID` | C | 100 |  | 사용자ID |  |
| ↳ `USER_NM` | C | 100 |  | 사용자명 |  |
| ↳ `CLPH_NO` | C | 100 |  | 휴대전화번호 |  |
| ↳ `REG_DTM` | C | 14 |  | 등록일시 |  |

### Process rules (verbatim from the sheet)

Input section:

```text
※ 추가 WHERE 조건

ACTV_YN = 'Y'
DEL_YN = 'N' (2022-09-21 추가)
```

---

<a id="wabooks_mrec_mbmr_0003_r001"></a>

## `WABOOKS_MREC_MBMR_0003_R001` — Reset password - request an SMS OTP

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_MBMR_0003_R001` |
| Sheet name (source) | `WABOOKS_MREC_MBMR_0003_R001` |
| Sheet title (원문) | 비밀번호변경_휴대전화 인증요청(모바일API) |
| Business area (업무) | 영수증관리API |
| Screen (source index) | — |
| Legacy web reference | — |
| Legacy tables (테이블) | MSG_SND_HIS |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | — (none) |
| Coverage | **MISSING** |
| Last spec revision | — |

### Input — 2 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `USER_NM` | C | 100 | Y | 사용자명 |  |
| `CLPH_NO` | C | 100 | Y | 휴대전화번호 |  |

### Output — 1 field

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `SND_RSLT` | C | 5 |  | 발신결과 |  |

### Process rules (verbatim from the sheet)

Input section:

```text
mbmr_0003_001_r001 과 프로세스 동일
```

---

<a id="wabooks_mrec_mbmr_0003_r002"></a>

## `WABOOKS_MREC_MBMR_0003_R002` — Reset password - verify the OTP

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_MBMR_0003_R002` |
| Sheet name (source) | `WABOOKS_MREC_MBMR_0003_R002` |
| Sheet title (원문) | 비밀번호변경_휴대전화 인증확인(모바일API) |
| Business area (업무) | 영수증관리API |
| Screen (source index) | — |
| Legacy web reference | — |
| Legacy tables (테이블) | — |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | — (none) |
| Coverage | **MISSING** |
| Last spec revision | — |

### Input — 3 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `USER_NM` | C | 100 | Y | 사용자명 |  |
| `CLPH_NO` | C | 100 | Y | 휴대전화번호 |  |
| `CRTC_NUM` | C | 100 | Y | 인증번호 |  |

### Output — 2 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `CRTC_YN` | C | 1 |  | 인증여부 | 휴대폰 인증확인 API의 인증결과에 따라 Y/N 처리. |
| `VERI_KEY` | C | 30 |  | 인증검증용KEY | (2022/09/15) 추가 |

### Process rules (verbatim from the sheet)

Input section:

```text
mbmr_0003_001_r002 과 프로세스 동일
```

---

<a id="wabooks_mrec_mbmr_0003_r003"></a>

## `WABOOKS_MREC_MBMR_0003_R003` — Reset password - pre-change lookup

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_MBMR_0003_R003` |
| Sheet name (source) | `WABOOKS_MREC_MBMR_0003_R003` |
| Sheet title (원문) | 비밀번호변경_비밀번호변경 전조회(모바일API) |
| Business area (업무) | 영수증관리API |
| Screen (source index) | — |
| Legacy web reference | — |
| Legacy tables (테이블) | 1. USER_INFM <br> 2. USER_PW_CHG_HIS |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | — (none) |
| Coverage | **MISSING** |
| Last spec revision | — |

### Input — 4 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `USER_ID` | C | 100 | Y | 사용자ID | 1번 테이블 SELECT WHERE 조건 |
| `USER_NM` | C | 100 | Y | 사용자명 | 1번 테이블 SELECT WHERE 조건 |
| `CLPH_NO` | C | 100 | Y | 휴대전화번호 |  |
| `VERI_KEY` | C | 30 | Y | 인증검증용KEY | (2022/09/15) 추가 |

### Output — 1 field

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `REG_SEQ_NO` | N | 20 |  | 등록일련번호 |  |

### Process rules (verbatim from the sheet)

Input section:

```text
1.. 1번 테이블 SELECT.
※ 추가 WHERE 조건
ACTV_YN = 'Y'

2. 2번 테이블에 데이터 INSERT.

USER_ID : (Input)
REG_DTM : SYSDATE
(REG_SEQ_NO를 Return 받아야 함.)
```

---

<a id="wabooks_mrec_mbmr_0003_u001"></a>

## `WABOOKS_MREC_MBMR_0003_U001` — Reset password - apply the new password

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_MBMR_0003_U001` |
| Sheet name (source) | `WABOOKS_MREC_MBMR_0003_U001` |
| Sheet title (원문) | 비밀번호변경_비밀번호변경 처리(모바일API) |
| Business area (업무) | 영수증관리API |
| Screen (source index) | — |
| Legacy web reference | — |
| Legacy tables (테이블) | 1. USER_INFM <br> 2. USER_PW_CHG_HIS |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | `POST /api/v1/auth/change-password` |
| Coverage | **PARTIAL** |
| Last spec revision | — |

**Delta.** The existing endpoint requires an authenticated session; the OTP reset path needs an unauthenticated variant.

### Input — 4 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `REG_SEQ_NO` | C | 20 | Y | 등록일련번호 | 2번 테이블 Update WHERE 조건 |
| `USER_ID` | C | 100 | Y | 사용자ID | 1, 2번 테이블 Update WHERE 조건 |
| `USER_PW` | C | 100 | Y | 변경후 비밀번호 | 1, 2번 테이블 Update WHERE 조건 |
| `USER_PW2` | C | 100 | Y | 변경후 비밀번호2 |  |

### Output — 0 fields

_No output payload beyond the common `RSLT_CD` / `RSLT_MSG` envelope._

### Process rules (verbatim from the sheet)

Input section:

```text
mbmr_0003_002_u001 과 프로세스 동일하지만, USER_PW_PRE 값을 USER_INFM 테이블에서 COUNT(*)로 SELECT하는 부분은 제외.
```

---

