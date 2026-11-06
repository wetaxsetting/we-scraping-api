# WeCMS Mobile — Notification / push — Field-Level Spec

Part of the [mobile-app migration mapping](README.md) set. 5 of the 76 legacy mobile services. Source: `WeCMS_Mobile_INOUT정의서(영수증관리API)_v2024.1.46.xlsx` (one sheet per service).

Legacy service ids (`WABOOKS_MREC_*`) appear here as **historical pointers** only, per the workspace identifier-naming rule. Field names are quoted in the legacy `UPPER_SNAKE` form used by the mobile gateway; any refactor endpoint that serves them returns lowercase `snake_case` JSON through the global Jackson strategy (see [docs/README.md](../../../README.md) §field-name casing).

---

<a id="wabooks_mrec_r010"></a>

## `WABOOKS_MREC_R010` — Notification list

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_R010` |
| Sheet name (source) | `WABOOKS_MREC_R010` |
| Sheet title (원문) | 모바일알림목록조회(API) |
| Business area (업무) | 영수증관리API |
| Screen (source index) | — |
| Legacy web reference | `WABOOKS_MREC_R010 + comm_0192_007_r001` |
| Legacy tables (테이블) | 1.PUSH_NOTICE_INFM <br> 2.USER_INFM <br> 3. BLTB_POST_INFM |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | `GET /api/v1/notifications` |
| Coverage | **PARTIAL** |
| Last spec revision | Updated v2024.1.25 |

**Delta.** The app merges `PUSH_NOTICE_INFM` with board notifications from `BLTB_POST_INFM`; the refactor only serves payment notifications.

### Input — 3 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `NOTI_TYPE` | C | 1 |  | Notification Type | v2024.1.23 added <br> 'U': Unread, 'A': All <br> Default: 'U' |
| `PAGE_NO` | N | 15 | Y | 페이지번호 | (2021/05/10) 추가. 페이징 처리용. |
| `PAGE_PER_CNT` | N | 15 | Y | 페이지별건수 | (2021/05/10) 추가. 페이징 처리용. |

### Output — 20 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `TOT_CNT` | N | 15 |  | 총건수 | (2021/05/10) 추가. 페이징 처리용. |
| `PAGE_NO` | N | 15 |  | 페이지번호 | (2021/05/21) 추가 |
| `PAGE_PER_CNT` | N | 15 |  | 페이지별건수 | (2021/05/21) 추가 |
| `NEXT_PAGE_YN` | C | 1 |  | 다음페이지유무 | (2021/05/25) 추가 <br> 페이징을 적용하여 SELECT한 쿼리의 마지막 Row의 ROWNUM이 같은 쿼리의 총 데이터 건수를 조회(페이징 미적용)하여 나온 총건수 값보다 낮으면 Y, 그렇지 않으면 N. |
| `REC` | R |  |  | 그룹 |  |
| ↳ `SEQ_NO` | N | 10 |  | 일련번호 |  |
| ↳ `SND_DATE` | C | 8 |  | 발송일자 |  |
| ↳ `SND_TIME` | C | 6 |  | 발송시간 |  |
| ↳ `DV_CD` | C | 1 |  | 구분코드 | **see note 1 below** |
| ↳ `CONT` | C | 200 |  | 내용 | The value of CONT is depend on DV_CD like below: <br> 6.CONT(JSON String {CCY_CD, AMT}) <br> 8.CONT(Comment Value) <br> 10.CONT(Comment Value) <br> 11.CONT(Simple Approval title Value) <br> 12.CONT(Article title Value) <br> 14.CONT(Comment Value) <br> 15.CONT(Comment Value) |
| ↳ `REG_DT` | C | 8 |  | 등록일자 | DV_CD가 3, 4, 5일 경우 스마트비서 조회를 하기 위해 필요한 SMRT_ASST_PTCL 테이블의 REG_DT 컬럼값 |
| ↳ `REG_SEQ_NO` | N | 20 |  | 등록일련번호 | DV_CD가 3, 4, 5일 경우 스마트비서 조회를 하기 위해 필요한 SMRT_ASST_PTCL 테이블의 REG_SEQ_NO 컬럼값 <br> v2024.1.15 added <br> Required if DEV_CD = 6 to 15 <br> (Value is from POST_SEQ_NO) |
| ↳ `COMNT_SEQ_NO` | N | 100 |  | comment sequence number | v2024.1.24 added, Table 1 |
| ↳ `BLTB_CD` | C | 100 |  | Bulletin board code | v2024.1.24 added, Table 1 |
| ↳ `POST_APPR_DV` | C | 2 |  | Post Approval Division | v2024.1.25 added <br> 00 :‘Simple approval’, 10 : 'Expense approval' <br> Subquery from table 3 <br> Select post_appr_dv from table 3 <br> where <br> biz_key: BIZ_KEY(Table 1) <br> ,bltb_cd: BLTB_CD(Table 1) <br> ,post_seq_no: REG_SEQ_NO(Table 1) |
| ↳ `NOTI_CNFM_YN` | C | 1 |  | 알림확인여부 | (2021/05/25) 추가. |
| ↳ `CNFR_DTM` | C | 14 |  | 확인일자 | (2021/05/25) 추가. |
| ↳ `SND_ID` | C | 100 |  | Sender ID | v2024.1.15 added |
| ↳ `SND_NM` | C | 100 |  | Sender name | v2024.1.15 added |
| ↳ `SND_IMG_URL` | C | 200 |  | Sender image Url | v2024.1.15 added <br> Table 2 >> USER_IMG <br> if USER_IMG is not null >> {Base URL}/szon_0012_003_r002.act?USER_ID={SND_ID} |

**Note 1 — `DV_CD`** (verbatim from the sheet):

```text
1 : 거래처
2 : 급여명세
3 : 입금안내
4 : 출금안내
5 : 시재보고
해당 값에 따라 UI에 아이콘 표시

v2024.1.15 added
- Bulleting Board Notification Type
6.Expense Approval request from specific user 
7.Expense Approval approved by approver(s)
8.Expense Approval approved by approver(s) with comment 
9.Rejected by approver
10.Rejected by approver with comment 
11.Simple Approval request from specific user 
12.New article posted by specific user 
13.Simple Approval approved by approver(s) 
14.Simple Approval approved by approver(s) with comment
15.Specific user comment on your post
```

### Process rules (verbatim from the sheet)

Input section:

```text
PUSH_NOTICE_INFM 테이블 조회. (BIZ_KEY, USER_ID는 공통 Header에서 가져옴.)

v2024.1.23 added
if NOTI_TYPE = 'U' >> and NOTI_CNFM_YN = 'N'

SEQ_NO 컬럼을 ORDER BY DESC.
```

---

<a id="wabooks_mrec_u001"></a>

## `WABOOKS_MREC_U001` — Mark one notification read

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_U001` |
| Sheet name (source) | `WABOOKS_MREC_U001` |
| Sheet title (원문) | 모바일알림확인여부수정 |
| Business area (업무) | 영수증관리API |
| Screen (source index) | — |
| Legacy web reference | — |
| Legacy tables (테이블) | 1. PUSH_NOTICE_INFM |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | `PUT /api/v1/notifications/{notificationId}/read` |
| Coverage | **AVAILABLE** |
| Last spec revision | — |

### Input — 2 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `SEQ_NO` | N | 10 | Y | 일련번호 |  |
| `NOTI_CNFM_YN` | C | 1 | Y | 알림확인여부 | Y : 확인 <br> N : 미확인 |

### Output — 1 field

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `PUSH_BADGE_COUNT` | N | 5 |  | Push Badge Count | (2021/06/08) 추가 |

### Process rules (verbatim from the sheet)

Input section:

```text
1. 1번 테이블의 NOTI_CNFM_YN을 Input값에 따라 Update. Where 조건은 BIZ_KEY(공통부), USER_ID(공통부), SEQ_NO(Input)
이 때, Input의 NOTI_CNFM_YN이 'Y'이면 CNFR_DTM을 SYSDATE로 UPDATE 해 주고, 'N'이면 Null로 UPDATE 해 줌.
```

---

<a id="wabooks_mrec_bltb_0007_u001"></a>

## `WABOOKS_MREC_BLTB_0007_U001` — Mark all (or one) notifications read

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_BLTB_0007_U001` |
| Sheet name (source) | `WABOOKS_MREC_BLTB_0007_U001` |
| Sheet title (원문) | Read All or Read One |
| Business area (업무) | Read notification (Read All and Read One) |
| Screen (source index) | Notification |
| Legacy web reference | `comm_0192_007_u001` |
| Legacy tables (테이블) | 1.PUSH_NOTICE_INFM |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | `PUT /api/v1/notifications/read-all` |
| Coverage | **AVAILABLE** |
| Last spec revision | Add v2024.1.15 |

### Input — 2 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `READ_TYPE` | C | 1 |  | Read Type | A': Read All, 'O': Read a notification |
| `SEQ_NO` | N | 10 |  | 일련번호 | If READ_TYPE = 'O' >> Required |

### Output — 0 fields

_No output payload beyond the common `RSLT_CD` / `RSLT_MSG` envelope._

### Process rules (verbatim from the sheet)

Input section:

```text
Update table 1 set
NOTI_CNFM_YN: 'Y' 
CNFR_DTM: Date Time on Now(14)
Where
biz_key: (Session)
user_id: (Session)
noti_cnfm_yn: 'N'
if READ_TYPE = 'O' >> seq_no: (INPUT>>seq_no)
```

---

<a id="wabooks_mrec_c006"></a>

## `WABOOKS_MREC_C006` — Register a device for push

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_C006` |
| Sheet name (source) | `WABOOKS_MREC_C006` |
| Sheet title (원문) | PUSH 디바이스 등록 |
| Business area (업무) | 영수증관리API |
| Screen (source index) | — |
| Legacy web reference | — |
| Legacy tables (테이블) | 1. PUSH_DEVICE_MSTR <br> 2. PUSH_DEVICE_MSTR_HIS <br> 3. EMPL_INFM |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | — (none) |
| Coverage | **MISSING** |
| Last spec revision | — |

**Delta.** `PUSH_DEVICE_MSTR` and the whole push stack are absent from the refactor.

### Input — 9 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `PUSHSERVER_KIND` | C | 10 | Y | 푸시서버종류 | APNS : Apple Push Notifications Service (IOS용) <br> FCM : Firebase Cloud Messaging (Android용) |
| `APP_ID` | C | 200 | Y | 앱ID | App Package Name or Bundle Identifier <br> OS에 따라 아래와 같이 고정값으로 올라옴. <br> IOS : kh.com.kosign.wabook.ent(개발), kr.com.webcash.wabook(운영) <br> Android : com.webcash.wabooks |
| `DEVICE_ID` | C | 200 | Y | 디바이스ID | Device UUID |
| `PUSH_ID` | C | 200 | Y | 푸시ID | Device Push Token |
| `MODEL_NAME` | C | 100 |  | 기기모델명 |  |
| `OS` | C | 20 |  | 모바일운영체제 |  |
| `NOTE` | C | 255 |  | 비고 |  |
| `RETRANS_YN` | C | 1 | Y | 재전송여부 |  |
| `LANG_CODE` | C | 2 | Y | 언어코드 | (2021/05/26) 추가. <br> Native의 언어코드 |

### Output — 0 fields

_No output payload beyond the common `RSLT_CD` / `RSLT_MSG` envelope._

### Process rules (verbatim from the sheet)

Input section:

```text
0. Table 1: Find Device by Device_id if each device include not same(BIZ_KEY, USER_ID) then Delete where biz_key, user_id, device_id. (v2023.1.0.14 add)

1. 1번 테이블을 BIZ_KEY(공통부), USER_ID(공통부), DEL_YN('N') 으로 조회.

2. 조회 결과가 존재할 경우 DEVICE_ID, PUSH_ID 컬럼값이 Input의 DEVICE_ID, PUSH_ID와 일치하면 프로그램 종료, 결과는 존해자나 DEVICE_ID, PUSH_ID 값이 서로 일치하지 않으면 PUSH API 'PS0003', 존재하지 않을 경우 PUSH API 'PS0002'를 호출한다.
(Request Message는 PUSH API 개발가이드 참조.)

3. PUSH API에서 오류가 발생하면 Exception 처리하고 오류 결과를 Return.

4. PUSH API가 정상적으로 호출되면 1, 2번 테이블에 Data Insert, 3번 테이블의 MAPP_SEUP_YN 컬럼값을 'Y'로 Update.
(※ 위 1번의 조회 결과가 존재하면 1번 테이블은 UPDATE 처리.)
(※ 3번 테이블의 Where 조건은 BIZ_KEY(공통부), USER_ID(공통부))

※ Note!
- PUSH API의 _relation_key는 USER_ID(공통부) 값을 셋팅.
- PUSH API의 _company_id는 webcash를 셋팅.
```

Input section:

```text
]
```

---

<a id="wabooks_mrec_u002"></a>

## `WABOOKS_MREC_U002` — Update the app language (push payload language)

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_U002` |
| Sheet name (source) | `WABOOKS_MREC_U002` |
| Sheet title (원문) | 모바일앱언어수정 |
| Business area (업무) | 영수증관리API |
| Screen (source index) | — |
| Legacy web reference | — |
| Legacy tables (테이블) | 1. PUSH_DEVICE_MSTR <br> 2. PUSH_DEVICE_MSTR_HIS |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | — (none) |
| Coverage | **MISSING** |
| Last spec revision | — |

**Delta.** Updates `LANG_CODE` on `PUSH_DEVICE_MSTR` - depends on C006.

### Input — 2 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `LANG_CODE` | C | 2 | Y | 언어코드 |  |
| `DEVICE_ID` | C | 2 | Y | 디바이스ID | v2024.1.39 added |

### Output — 0 fields

_No output payload beyond the common `RSLT_CD` / `RSLT_MSG` envelope._

### Process rules (verbatim from the sheet)

Input section:

```text
1번 테이블 Update, 2번 테이블 Insert.
(Update Where 조건은 BIZ_KEY, USER_ID(공통 Header) , DEVICE_ID (INPUT)
```

---

