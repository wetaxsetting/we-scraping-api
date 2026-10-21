# WeCMS Mobile — Master data / settings — Field-Level Spec

Part of the [mobile-app migration mapping](README.md) set. 14 of the 76 legacy mobile services. Source: `WeCMS_Mobile_INOUT정의서(영수증관리API)_v2024.1.46.xlsx` (one sheet per service).

Legacy service ids (`WABOOKS_MREC_*`) appear here as **historical pointers** only, per the workspace identifier-naming rule. Field names are quoted in the legacy `UPPER_SNAKE` form used by the mobile gateway; any refactor endpoint that serves them returns lowercase `snake_case` JSON through the global Jackson strategy (see [docs/README.md](../../../README.md) §field-name casing).

---

<a id="wabooks_mrec_r004"></a>

## `WABOOKS_MREC_R004` — Read the receipt screen's input-field configuration

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_R004` |
| Sheet name (source) | `WABOOKS_MREC_R004` |
| Sheet title (원문) | 입력항목설정조회 |
| Business area (업무) | 영수증관리API |
| Screen (source index) | — |
| Legacy web reference | — |
| Legacy tables (테이블) | USER_SCR_OPTION |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | `GET /api/v1/screen-options/{webSvcId}/{dymAreaId}` |
| Coverage | **PARTIAL** |
| Last spec revision | — |

**Delta.** The app reads `USER_SCR_OPTION`; the refactor uses `USER_SCR_OPTION_WECMS` keyed by the web `WEB_SVC_ID`, so a `web_svc_id` has to be registered for the mobile screens.

### Input — 0 fields

_No input fields (the common envelope carries `USER_ID` / session)._

### Output — 8 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `INPUT_FIELD_SET_YN` | C | 1 |  | 입력항목설정여부 | FIELD_ID가 'INPUT_FIELD_SET'인 Row의 'USE_YN' 컬럼값 <br> (v2023.1.0.11 추가) 베트남 버전의 경우 무조건 'Y' v2024.1.43 updated |
| `TRCO_TRSC_CCY_CD_YN` | C | 1 |  | 거래처거래통화코드사용여부 | FIELD_ID가 'TRCO_TRSC_CCY_CD'인 Row의 'USE_YN' 컬럼값 <br> (v2023.1.0.11 추가) 베트남 버전의 경우 무조건 'Y' |
| `TRSC_AMT_YN` | C | 1 |  | 거래금액사용여부 | FIELD_ID가 'TRSC_AMT'인 Row의 'USE_YN' 컬럼값 <br> (v2023.1.0.11 추가) 베트남 버전의 경우 무조건 'Y' |
| `TRCO_YN` | C | 1 |  | 거래처사용여부 | FIELD_ID가 'TRCO_CMNM'인 Row의 'USE_YN' 컬럼값 <br> (v2023.1.0.11 추가) 베트남 버전의 경우 무조건 'Y' |
| `TRSC_DT_YN` | C | 1 |  | 거래일자사용여부 | FIELD_ID가 'TRSC_DT'인 Row의 'USE_YN' 컬럼값 <br> (v2023.1.0.11 추가) 베트남 버전의 경우 무조건 'Y' |
| `USAG_CD_YN` | C | 1 |  | 용도사용여부 | FIELD_ID가 'BUSA_USAG_CD'인 Row의 'USE_YN' 컬럼값 <br> (v2023.1.0.11 추가) 베트남 버전의 경우 무조건 'Y' |
| `USER_NM_YN` | C | 1 |  | 사용자명사용여부 | FIELD_ID가 'USER_NM'인 Row의 'USE_YN' 컬럼값 <br> (v2023.1.0.11 추가) 베트남 버전의 경우 무조건 'Y' |
| `PRJT_NO_YN` | C | 1 |  | project number Yes/NO | FIELD_ID가 'PRJT_NO'인 Row의 'USE_YN' 컬럼값 <br> (v2024.1.41 추가) 베트남 버전의 경우 무조건 'Y' v2024.1.43 updated |

### Process rules (verbatim from the sheet)

Input section:

```text
※ Where 조건
BIZ_KEY, USER_ID : API 공통부
WEB_SVC_ID : 'WABOOKS_MREC_R004'
```

---

<a id="wabooks_mrec_c001"></a>

## `WABOOKS_MREC_C001` — Save the receipt screen's input-field configuration

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_C001` |
| Sheet name (source) | `WABOOKS_MREC_C001` |
| Sheet title (원문) | 입력항목설정변경 |
| Business area (업무) | 영수증관리API |
| Screen (source index) | — |
| Legacy web reference | — |
| Legacy tables (테이블) | USER_SCR_OPTION |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | `PUT /api/v1/screen-options/{webSvcId}/{dymAreaId}` |
| Coverage | **PARTIAL** |
| Last spec revision | — |

**Delta.** Same table/key delta as R004.

### Input — 8 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `INPUT_FIELD_SET_YN` | C | 1 | Y | 입력항목설정여부 | FIELD_ID가 'INPUT_FIELD_SET'인 Row의 'USE_YN' 컬럼값 |
| `TRCO_TRSC_CCY_CD_YN` | C | 1 | Y | 거래처거래통화코드사용여부 | FIELD_ID가 'TRCO_TRSC_CCY_CD'인 Row의 'USE_YN' 컬럼값 |
| `TRSC_AMT_YN` | C | 1 | Y | 거래금액사용여부 | FIELD_ID가 'TRSC_AMT'인 Row의 'USE_YN' 컬럼값 |
| `TRCO_YN` | C | 1 | Y | 거래처사용여부 | FIELD_ID가 'TRCO_CMNM'인 Row의 'USE_YN' 컬럼값 |
| `TRSC_DT_YN` | C | 1 | Y | 거래일자사용여부 | FIELD_ID가 'TRSC_DT'인 Row의 'USE_YN' 컬럼값 |
| `USAG_CD_YN` | C | 1 | Y | 용도사용여부 | FIELD_ID가 'BUSA_USAG_CD'인 Row의 'USE_YN' 컬럼값 |
| `USER_NM_YN` | C | 1 | Y | 사용자명사용여부 | FIELD_ID가 'USER_NM'인 Row의 'USE_YN' 컬럼값 |
| `PRJT_NO_YN` | C | 1 | Y | project number Yes/NO | FIELD_ID가 'PRJT_NO'인 Row의 'USE_YN' 컬럼값 v2024.1.40 added |

### Output — 0 fields

_No output payload beyond the common `RSLT_CD` / `RSLT_MSG` envelope._

### Process rules (verbatim from the sheet)

Input section:

```text
위의 설명을 참조하여 테이블의 데이터 Update.

- 기타 Where 조건
BIZ_KEY, USER_ID : 공통부
WEB_SVC_ID : 'WABOOKS_MREC_R004'
```

---

<a id="wabooks_mrec_r005"></a>

## `WABOOKS_MREC_R005` — Purpose (expense-usage) list

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_R005` |
| Sheet name (source) | `WABOOKS_MREC_R005` |
| Sheet title (원문) | 용도리스트조회 |
| Business area (업무) | 영수증관리API |
| Screen (source index) | — |
| Legacy web reference | — |
| Legacy tables (테이블) | 1. BUSA_DOC_MSTR <br> 2. BUSI_USAG_SET_INFM <br> 3. ADV_PAYM_MSTR |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | `GET /api/v1/settings/purpose-settings` |
| Coverage | **PARTIAL** |
| Last spec revision | — |

**Delta.** Field-level verified: `PurposeSettingRow` has no `CCY_CD` and no `REC2` imprest list (`ADV_PAYM_*`) - the refactor endpoint only covers the `USE_LIST` / `REC` branch. `/recent` also exists.

### Input — 2 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `SCH_WORD` | C | 255 |  | 검색어 | 2번 테이블 USAG_NM을 LIKE 검색 |
| `CCY_CD` | C | 3 | Y | 통화코드 | v2023.1.0.1 추가 |

### Output — 11 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `USE_LIST` | R |  |  | 최근사용용도 |  |
| ↳ `USE_USAG_CD` | C | 20 |  | 사용용도코드 |  |
| ↳ `USAG_NM` | C | 100 |  | 용도명 |  |
| `REC` | R |  |  | 그룹 |  |
| ↳ `USE_USAG_CD` | C | 20 |  | 사용용도코드 |  |
| ↳ `USAG_NM` | C | 100 |  | 용도명 |  |
| `REC2` | R |  |  | 그룹2 | (12/22 추가) |
| ↳ `ADV_PAYM_SEQ` | N | 10 |  | 전도금원장일련번호 | (12/22 추가) |
| ↳ `ADV_PAYM_NM` | C | 50 |  | 전도금원장명 | (12/22 추가) |
| ↳ `ADV_PAYM_RSPB_ID` | C | 100 |  | 전도금담당자ID | (2022-10-26 추가) |
| ↳ `USER_NM` | C | 100 |  | 사용자명 | (2022-10-26 추가) <br> ADV_PAYM_RSPB_ID를 USER_INFM 테이블에서 조회한 Row의 USER_NM 컬럼값 |

### Process rules (verbatim from the sheet)

Input section:

```text
2 3개의 SELECT 쿼리를 실행해야 함. (12/22 변경)

1. 최근사용용도 조회쿼리. (USE_LIST 반복부에 들어갈 데이터)
1, 2번 테이블을 BUSA_USAG_CD로 JOIN하여 1번 테이블에서 사용한 BUSA_USAG_CD를 REG_DTM으로 DESC하여서 조회.
- Where 조건
BIZ_KEY : (공통부)
DOC_TYPE : 'E'
REG_CHNL : 'MOB'
REG_ID : USER_ID(공통부)

2. 용도전체리스트 조회쿼리. (REC 반복부에 들어갈 데이터)
2번 테이블을 USE_USAG_CD로 JOIN하여 DISP_ORD를 ASC하여서 조회.
- Where 조건
BIZ_KEY(3번 테이블) : (공통부)
USE_YN(3번 테이블) : 'Y'
DISP_YN(3번 테이블) : 'Y'
USAG_GRP_CD : '3'
REPT_DV : '30'

(12/22 추가)
3. 전도금원장 조회쿼리. (REC 반복부에 들어갈 데이터)
3번 테이블 조회.
- Where 조건
BIZ_KEY : (공통부)
ADV_PAYM_RSPB_ID : (SELECT EMPL_NO FROM USER_BUSI_CONN_INFM WHERE USER_ID = USER_ID(공통부) AND BIZ_KEY = (공통부) (v2023.1.0.2 변경)
```

---

<a id="wabooks_mrec_r008"></a>

## `WABOOKS_MREC_R008` — Bank list with logo

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_R008` |
| Sheet name (source) | `WABOOKS_MREC_R008` |
| Sheet title (원문) | 은행 목록조회(API) |
| Business area (업무) | 영수증관리API |
| Screen (source index) | — |
| Legacy web reference | — |
| Legacy tables (테이블) | 1. BANK_INFM <br> 2. BANK_INFM_DETL |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | `GET /api/v1/settings/contacts/banks` |
| Coverage | **PARTIAL** |
| Last spec revision | — |

**Delta.** The refactor endpoint is the NAPAS/CITAD picker for the contact dialog. The app needs the `BANK_INFM` + `BANK_INFM_DETL` master with the `IMG` logo field.

### Input — 4 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `LANG_CODE` | C | 2 | Y | 언어코드 | 테이블 WHERE 조건 |
| `BANK_NM` | C | 2000 |  | 은행명 | bank_detl_info_cont 컬럼 LIKE 조건 |
| `IS_SCRAPPING` | C | 1 |  | 스크래핑지원여부 | 'Y' or null |
| `USE_SHORT_NAME` | C | 1 |  | Short Name 사용여부 | 'Y' or null |

### Output — 4 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `REC` | R |  |  | 목록 |  |
| ↳ `BANK_CD` | C | 20 |  | 은행코드 |  |
| ↳ `BANK_NM` | C | 2000 |  | 은행명 | LANG_CODE에 맞는 은행명을 출력해야 함. |
| ↳ `IMG` | C | 2000 |  | 이미지경로 |  |

### Process rules (verbatim from the sheet)

Input section:

```text
comm_0109_001_r001 과 프로세스 동일
```

---

<a id="wabooks_mrec_r011"></a>

## `WABOOKS_MREC_R011` — Country list

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_R011` |
| Sheet name (source) | `WABOOKS_MREC_R011` |
| Sheet title (원문) | Inquiry Country |
| Business area (업무) | Inquiry Country |
| Screen (source index) | Add Receipt |
| Legacy web reference | — |
| Legacy tables (테이블) | — |
| Called by | iOS — · Android — |
| Refactor endpoint | `GET /api/v1/common/countries` |
| Coverage | **PARTIAL** |
| Last spec revision | Added v2024.1.29 |

**Delta.** Field-level verified: `CountryRow` is not localized by `LANG_CD` and has no `NATN_KEY`.

### Input — 1 field

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `LANG_CD` | C | 2 |  | Language Code |  |

### Output — 3 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `REC` | C | 4 |  | Record |  |
| ↳ `NATN_CODE` | C | 100 |  | National Code | Ex: Cambodia |
| ↳ `NATN_KEY` | C | 3 |  | National Key | Ex: KH |

### Process rules (verbatim from the sheet)

Input section:

```text
Read From Jex studio code Group Name "NATN_NUM_CD".
EX:  CodeManager.getCode("NATN_NUM_CD");
```

---

<a id="wabooks_mrec_r020"></a>

## `WABOOKS_MREC_R020` — Dial-code list

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_R020` |
| Sheet name (source) | `WABOOKS_MREC_R020` |
| Sheet title (원문) | 국가번호목록조회(API) |
| Business area (업무) | 영수증관리API |
| Screen (source index) | — |
| Legacy web reference | — |
| Legacy tables (테이블) | NATN_INFM |
| Called by | iOS ✓ · Android — |
| Refactor endpoint | `GET /api/v1/common/countries` |
| Coverage | **AVAILABLE** |
| Last spec revision | — |

**Delta.** Field-level verified: `CountryRow` already carries `natnNum` (dial code), `natlNm` and `defaultYn` - nothing to add.

### Input — 1 field

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `NATL_NM` | C | 100 |  | 국가명 |  |

### Output — 4 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `REC` | R |  |  | 목록 |  |
| ↳ `NATN_NUM` | C | 5 |  | 국가번호 |  |
| ↳ `NATL_NM` | C | 100 |  | 국가명 |  |
| ↳ `DEFAULT_YN` | C | 1 |  | 기본여부 | NTNL_CD가 'KM'이면 'Y', 그 외에는 'N' |

### Process rules (verbatim from the sheet)

Input section:

```text
1. NATN_INFM Table을 조회.
(NATL_NM Input 값을 LIKE 검색.)
```

---

<a id="wabooks_mrec_r012"></a>

## `WABOOKS_MREC_R012` — Read a business environment setting

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_R012` |
| Sheet name (source) | `WABOOKS_MREC_R012` |
| Sheet title (원문) | 업무 환경 설정 정보 가져오기 |
| Business area (업무) | 공통 |
| Screen (source index) | Add Receipt and Edit Receipt |
| Legacy web reference | `comm_0600_001_r001` |
| Legacy tables (테이블) | BUSI_ENV_SET_INFM |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | `GET /api/v1/settings/environment-settings/{env_set_inf}` |
| Coverage | **AVAILABLE** |
| Last spec revision | Added v2024.1.46 |

**Delta.** Same `BUSI_ENV_SET_INFM` row shape.

### Input — 1 field

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `ENV_SET_INF` | C | 5 |  | 환경설정정보 |  |

### Output — 1 field

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `ENV_SET_CONT` | C | 1000 |  | 환경설정내용 |  |

### Process rules (verbatim from the sheet)

Input section:

```text
※ 추가 WHERE 조건
BIZ_KEY : (Session)
```

---

<a id="wabooks_mrec_r030"></a>

## `WABOOKS_MREC_R030` — Contact list

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_R030` |
| Sheet name (source) | `WABOOKS_MREC_R030` |
| Sheet title (원문) | 거래처목록조회(API) |
| Business area (업무) | 영수증관리API |
| Screen (source index) | — |
| Legacy web reference | — |
| Legacy tables (테이블) | 1. USER_TRCO_USE_HIS <br> 2. TRCO_INFM |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | `GET /api/v1/settings/contacts` |
| Coverage | **PARTIAL** |
| Last spec revision | — |

**Delta.** Field-level verified: no contacts DTO carries `TRCO_NICK_NM`, and the mobile service returns three differently-shaped lists (REC1 / REC2 / REC3).

### Input — 2 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `TRCO_CMNM` | C | 200 |  | 거래처회사명 | TRCO_CMNM, TRCO_CMENM Like 검색 |
| `TRSC_CCY_CD` | C | 3 |  | 거래처통화코드 | 특정 통화코드를 사용하는 거래처만 Filtering 하고 싶을 때 넣으면 됨. |

### Output — 43 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `REC1` | R |  |  | 목록1 | USER_TRCO_USE_HIS(with TRCO_INFM) 테이블 조회결과. |
| ↳ `TRCO_KEY` | C | 20 |  | 거래처키 |  |
| ↳ `TRCO_CMNM` | C | 200 |  | 거래처회사명 |  |
| ↳ `TRCO_CMENM` | C | 200 |  | 거래처회사영문명 |  |
| ↳ `TRSC_CCY_CD` | C | 3 |  | 거래처통화코드 |  |
| ↳ `TRCO_BSUN_POST_ADRS` | C | 200 |  | 거래처사업장우편주소 |  |
| ↳ `TRCO_BSUN_DTL_ADRS` | C | 200 |  | 거래처사업장상세주소 |  |
| ↳ `TRCO_BSUN_CITY` | C | 200 |  | 거래처사업장도시 |  |
| ↳ `TRCO_BSUN_ST` | C | 200 |  | 거래처사업장국가 |  |
| ↳ `TRCO_RPRS_TLPH` | C | 30 |  | 거래처대표전화 |  |
| ↳ `TRCO_FXNO` | C | 30 |  | 거래처팩스번호 |  |
| ↳ `TRCO_TAX_ID` | C | 40 |  | 거래처세금ID |  |
| ↳ `TRCO_RPPR_NM` | C | 100 |  | 거래처대표자명 |  |
| ↳ `TRCO_RPPR_ENM` | C | 100 |  | 거래처대표자영문명 |  |
| ↳ `TRCO_TYPE` | C | 1 |  | 거래처등록유형 |  |
| ↳ `GST_NO` | C | 100 |  | GST번호 |  |
| ↳ `EML` | C | 100 |  | 이메일 |  |
| ↳ `TRCO_NICK_NM` | C | 200 |  | Display Name | v2023.1.0.12 |
| `REC2` | R |  |  | 목록2 | TRCO_INFM 테이블 조회결과 |
| ↳ `TRCO_KEY` | C | 20 |  | 거래처키 |  |
| ↳ `TRCO_CMNM` | C | 200 |  | 거래처회사명 |  |
| ↳ `TRCO_CMENM` | C | 200 |  | 거래처회사영문명 |  |
| ↳ `TRSC_CCY_CD` | C | 3 |  | 거래처통화코드 |  |
| ↳ `TRCO_BSUN_POST_ADRS` | C | 200 |  | 거래처사업장우편주소 |  |
| ↳ `TRCO_BSUN_DTL_ADRS` | C | 200 |  | 거래처사업장상세주소 |  |
| ↳ `TRCO_BSUN_CITY` | C | 200 |  | 거래처사업장도시 |  |
| ↳ `TRCO_BSUN_ST` | C | 200 |  | 거래처사업장국가 |  |
| ↳ `TRCO_RPRS_TLPH` | C | 30 |  | 거래처대표전화 |  |
| ↳ `TRCO_FXNO` | C | 30 |  | 거래처팩스번호 |  |
| ↳ `TRCO_TAX_ID` | C | 40 |  | 거래처세금ID |  |
| ↳ `TRCO_RPPR_NM` | C | 100 |  | 거래처대표자명 |  |
| ↳ `TRCO_RPPR_ENM` | C | 100 |  | 거래처대표자영문명 | (11/19) 추가 |
| ↳ `TRCO_TYPE` | C | 1 |  | 거래처등록유형 |  |
| ↳ `GST_NO` | C | 100 |  | GST번호 |  |
| ↳ `EML` | C | 100 |  | 이메일 |  |
| ↳ `TRCO_NICK_NM` | C | 200 |  | Display Name | V2023.1.0.12 |
| `REC3` | R |  |  | 목록3 | TRCO_FAV_INFO, TRCO_INFM 테이블을 TRCO_KEY로 Join 하여 조회한 거래결과 |
| ↳ `TRCO_KEY` | C | 20 |  | 거래처키 |  |
| ↳ `TRCO_CMNM` | C | 200 |  | 거래처회사명 |  |
| ↳ `TRCO_CMENM` | C | 200 |  | 거래처회사영문명 |  |
| ↳ `TRSC_CCY_CD` | C | 3 |  | 거래처통화코드 |  |
| ↳ `TRCO_TAX_ID` | C | 40 |  | 거래처세금ID |  |
| ↳ `TRCO_NICK_NM` | C | 200 |  | Display Name | V2023.1.0.12 |

### Process rules (verbatim from the sheet)

Input section:

```text
1. USER_TRCO_USE_HIS, TRCO_INFM Table을 각각 조회.
USER_TRCO_USE_HIS 테이블 조회 시에는 BIZ_KEY, TRCO_KEY로 TRCO_INFM을 Join하여 조회하도록
2. USER_TRCO_USE_HIS 테이블 조회 시 WHERE 조건으로 BIZ_KEY, USER_ID, TRSC_CCY_CD(Input) 가 들어가야 함.
3. USER_TRCO_USE_HIS 테이블 조회 결과는 REG_DTM으로 ORDER BY DESC하여서 상위 3개 Row만 Return
```

---

<a id="wabooks_mrec_c080"></a>

## `WABOOKS_MREC_C080` — Add a contact to favourites

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_C080` |
| Sheet name (source) | `WABOOKS_MREC_C080` |
| Sheet title (원문) | 거래처 즐겨찾기 등록(API) |
| Business area (업무) | 영수증관리API |
| Screen (source index) | — |
| Legacy web reference | — |
| Legacy tables (테이블) | 1. TRCO_FAV_INFO |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | — (none) |
| Coverage | **MISSING** |
| Last spec revision | — |

**Delta.** `TRCO_FAV_INFO` has not been refactored; only `/contacts/recent` exists.

### Input — 1 field

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `TRCO_KEY` | C | 20 | Y | 거래처키 |  |

### Output — 0 fields

_No output payload beyond the common `RSLT_CD` / `RSLT_MSG` envelope._

### Process rules (verbatim from the sheet)

Input section:

```text
1번 테이블 Insert
```

---

<a id="wabooks_mrec_d080"></a>

## `WABOOKS_MREC_D080` — Remove a contact from favourites

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_D080` |
| Sheet name (source) | `WABOOKS_MREC_D080` |
| Sheet title (원문) | #N/A |
| Business area (업무) | 영수증관리API |
| Screen (source index) | — |
| Legacy web reference | — |
| Legacy tables (테이블) | 1. TRCO_FAV_INFO |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | — (none) |
| Coverage | **MISSING** |
| Last spec revision | — |

**Delta.** This service is missing from the workbook's `목록` index (row 34 is blank) although both apps call it.

### Input — 1 field

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `TRCO_KEY` | C | 20 | Y | 거래처키 |  |

### Output — 0 fields

_No output payload beyond the common `RSLT_CD` / `RSLT_MSG` envelope._

### Process rules (verbatim from the sheet)

Input section:

```text
1번 테이블 Delete

추가 Where 조건 : BIZ_KEY, USER_ID
```

---

<a id="wabooks_mrec_r040"></a>

## `WABOOKS_MREC_R040` — Exchange rates

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_R040` |
| Sheet name (source) | `WABOOKS_MREC_R040` |
| Sheet title (원문) | 환율조회(API) |
| Business area (업무) | 영수증관리API |
| Screen (source index) | — |
| Legacy web reference | — |
| Legacy tables (테이블) | EXCH_RATE_INFM |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | `GET /api/v1/banking/exchange-rates` |
| Coverage | **PARTIAL** |
| Last spec revision | — |

**Delta.** Field-level verified: `ExchangeRateRow` covers basic / buy / sell plus the date but has no `UNIT_AMT`. `/banks` and `/currencies` also exist.

### Input — 3 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `INQ_DT` | C | 8 |  | 조회일자 | Default : SYSDATE |
| `REF_CCY_CD` | C | 3 | Y | 기준통화코드 | 당분간은 'KHR' 고정일 듯. |
| `CCY_CD` | C | 3 | Y | 통화코드 |  |

### Output — 5 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `UNIT_AMT` | N | 20.6 |  | 단위금액 |  |
| `BASIC_AMT` | N | 20.6 |  | 기준금액 |  |
| `BUY_AMT` | N | 20.6 |  | 구매금액 |  |
| `SELL_AMT` | N | 20.6 |  | 판매금액 |  |
| `REF_DATE` | C | 8 |  | 기준일자 |  |

### Process rules (verbatim from the sheet)

Input section:

```text
comm_0500_001_r001 과 프로세스 동일
```

---

<a id="wabooks_mrec_r050"></a>

## `WABOOKS_MREC_R050` — Generate the next Expense document number

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_R050` |
| Sheet name (source) | `WABOOKS_MREC_R050` |
| Sheet title (원문) | Expense 번호채번(API) |
| Business area (업무) | 영수증관리API |
| Screen (source index) | — |
| Legacy web reference | — |
| Legacy tables (테이블) | SYS_DOC_ID_GEN |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | `GET /api/v1/payment/card-transactions/next-doc-no` |
| Coverage | **AVAILABLE** |
| Last spec revision | — |

**Delta.** Same `SYS_DOC_ID_GEN` generator.

### Input — 0 fields

_No input fields (the common envelope carries `USER_ID` / session)._

### Output — 1 field

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `DOC_ID` | C | 50 |  | 문서ID |  |

### Process rules (verbatim from the sheet)

Input section:

```text
prch_0005_001_r001 과 프로세스 동일
```

---

<a id="wabooks_mrec_r070"></a>

## `WABOOKS_MREC_R070` — Per-user payment methods (advance / imprest ledger)

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_R070` |
| Sheet name (source) | `WABOOKS_MREC_R070` |
| Sheet title (원문) | 사용자별 지출방법 목록조회(API) |
| Business area (업무) | 영수증관리API |
| Screen (source index) | — |
| Legacy web reference | — |
| Legacy tables (테이블) | 1. CASH_REPY_USER_LIST USER_MENU_AUTH_INFM (v2023.1.0.8 변경) <br> 2. ADV_PAYM_USER_LIST <br> 3. ADV_PAYM_MSTR |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | — (none) |
| Coverage | **MISSING** |
| Last spec revision | — |

**Delta.** The `ADV_PAYM_*` imprest ledger is currently out of refactor scope.

### Input — 0 fields

_No input fields (the common envelope carries `USER_ID` / session)._

### Output — 4 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `CASH_USER_YN` | C | 1 |  | 현금출납장사용자여부 | 1번 테이블 COUNT 값이 1이면 Y, 그 외에는 N |
| `REC` | R |  |  | 목록 |  |
| ↳ `ADV_PAYM_SEQ` | N | 10 |  | 전도금원장일련번호 |  |
| ↳ `ADV_PAYM_NM` | C | 50 |  | 전도금원장명 |  |

### Process rules (verbatim from the sheet)

Input section:

```text
1. 1번 테이블을 COUNT SELECT. (Where 조건 : BIZ_KEY, USER_ID, WEB_SVC_ID['fnsh_0011_001'], USE_YN['Y']) (v2023.1.0.8 변경)

2. 2, 3번 테이블을 BIZ_KEY, ADV_PAYM_SEQ 컬럼으로 Inner join하여 조회.
※ Where 조건
BIZ_KEY, USER_ID : (공통부)
DEL_YN : 'N'
```

---

<a id="wabooks_mrec_r901"></a>

## `WABOOKS_MREC_R901` — Withholding-tax configuration

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_R901` |
| Sheet name (source) | `WABOOKS_MREC_R901` |
| Sheet title (원문) | Withholding Common (API) |
| Business area (업무) | Withholding Common (API) |
| Screen (source index) | — |
| Legacy web reference | — |
| Legacy tables (테이블) | BUSI_WHT_SET_INFM |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | — (none) |
| Coverage | **MISSING** |
| Last spec revision | — |

**Delta.** `BUSI_WHT_SET_INFM` is not present in wecms-api.

### Input — 1 field

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `SRCH_WD` | C | 100 |  | 검색어 | 테이블에서 WITH_CD를 EQUAL SEARCH. <br> 추가로 WTH_NM 을 LIKE SEARCH. |

### Output — 4 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `REC` | C |  |  | 목록 |  |
| ↳ `WITH_CD` | C | 3 |  | 품목코드 | 테이블 SELECT |
| ↳ `WTH_NM` | C | 255 |  | 품목명 | 테이블 SELECT |
| ↳ `TAX_RATE` | C | 100 |  | 품목규격 | 테이블 SELECT |

### Process rules (verbatim from the sheet)

Input section:

```text
※ 추가 Where 조건 , USE_YN= 'Y'
BIZ_KEY : (Session)
```

---

