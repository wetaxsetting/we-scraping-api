# WeCMS Mobile — Receipt - the app's core flow — Field-Level Spec

Part of the [mobile-app migration mapping](README.md) set. 10 of the 76 legacy mobile services. Source: `WeCMS_Mobile_INOUT정의서(영수증관리API)_v2024.1.46.xlsx` (one sheet per service).

Legacy service ids (`WABOOKS_MREC_*`) appear here as **historical pointers** only, per the workspace identifier-naming rule. Field names are quoted in the legacy `UPPER_SNAKE` form used by the mobile gateway; any refactor endpoint that serves them returns lowercase `snake_case` JSON through the global Jackson strategy (see [docs/README.md](../../../README.md) §field-name casing).

---

<a id="wabooks_mrec_r006"></a>

## `WABOOKS_MREC_R006` — Receipt list

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_R006` |
| Sheet name (source) | `WABOOKS_MREC_R006` |
| Sheet title (원문) | 영수증목록조회 |
| Business area (업무) | 영수증관리API |
| Screen (source index) | — |
| Legacy web reference | — |
| Legacy tables (테이블) | 1. BUSA_DOC_MSTR <br> 2. MREC_PIC_STORE <br> 3. USAG_INFM <br> 4. ADV_PAYM_TRNS_HSTR <br> 5. ADV_PAYM_MSTR <br> 6. BUSA_DOC_RCV_PAY_HIS <br> 7. BUSA_DOC_FILE |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | `GET /api/v1/payment/card-transactions/links/receipts` |
| Coverage | **PARTIAL** |
| Last spec revision | — |

**Delta.** The refactor query filters `DOC_TYPE='E' + HAND_REG_YN='Y'`. The mobile list filters `REG_CHNL='MOB'`, UNIONs `ADV_PAYM_TRNS_HSTR`, and adds a "photo captured, not yet keyed" group from `MREC_PIC_STORE` - both extra branches are missing.

### Input — 3 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `PAGE_NO` | N | 15 | Y | 페이지번호 | 페이징 DF:1 |
| `PAGE_PER_CNT` | N | 15 | Y | 페이지별건수 |  |
| `DOC_ID` | C |  |  | Document ID |  |

### Output — 59 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `PAGE_NO` | N | 15 |  | 페이지번호 | 페이징 DF:1 |
| `PAGE_PER_CNT` | N | 15 |  | 페이지별건수 |  |
| `NEXT_PAGE_YN` | C | 1 |  | 다음페이지유무 | 페이징을 적용하여 SELECT한 쿼리의 마지막 Row의 ROWNUM이 같은 쿼리의 총 데이터 건수를 조회(페이징 미적용)하여 나온 총건수 값보다 낮으면 Y, 그렇지 않으면 N. |
| `REC` | R |  |  | 그룹 |  |
| ↳ `INQ_GRP` | C | 4 |  | 조회그룹 | USE_DT가 이번주 내 날짜이면 '0000', <br> 그 외에는 USE_DT의 YYMM 을 넣어줌. <br> ※ 2번 테이블 출력항목 참조. |
| ↳ `DOC_ID` | C | 50 |  | 문서ID |  |
| ↳ `TRCO_CMNM` | C | 200 |  | 거래처회사명 | 1번 테이블 컬럼값 + 2번 테이블 출력항목 참조. |
| ↳ `USE_DT` | C | 14 |  | 사용일시 | 1번 테이블 컬럼값 + 2번 테이블 출력항목 참조. |
| ↳ `TRCO_TRSC_CCY_CD` | C | 3 |  | 거래처거래통화코드 |  |
| ↳ `TOTL_AMT` | N | 20.6 |  | 총금액 |  |
| ↳ `BUSA_USAG_CD` | C | 20 |  | 매입매출용도코드 |  |
| ↳ `USAG_NM` | C | 100 |  | 용도명 | wb_get_usag_nm DB function |
| ↳ `CONT` | C | 200 |  | 내용 |  |
| ↳ `USER_NM` | C | 100 |  | 사용자명 |  |
| ↳ `PROV_STTS` | C | 2 |  | 증빙상태 | PP : 사진등록 <br> OP : 제출완료 <br> 1번 테이블 컬럼값 + 2번 테이블 출력항목 참조. |
| ↳ `RCPT_TYPE` | C | 1 |  | 영수증타입 | 'P(사진)' fix. |
| ↳ `SAVE_FILE_NM` | C | 200 |  | 저장파일명 | (2021/06/29 삭제. REC 그룹 안으로 이동.) |
| ↳ `TRSC_WEEK` | C | 1 |  | 거래요일 | 1번 테이블 USE_DT의 YYYYMMDD 를 추출하여 해당 날짜의 요일값을 추출. + 2번 테이블 출력항목 참조. <br> 유효값은 1(월요일) ~ 7(일요일) |
| ↳ `SEQ_NO` | N | 10 |  | 일련번호 | 1, 4번 테이블 컬럼값. 2번 테이블과 혼동하지 않도록 주의. |
| ↳ `REG_DT` | C | 8 |  | 등록일자 | 1, 4번 테이블 컬럼값. 2번 테이블과 혼동하지 않도록 주의. |
| ↳ `PC_MOD_YN` | C | 1 |  | PC수정여부 | 1, 4번 테이블 컬럼값. |
| ↳ `ADV_PAYM_TRNS_SEQ` | N | 10 |  | 전도금원장거래내역일련번호 | (12/23 추가) <br> 4번 테이블 컬럼값. 1, 2번 테이블에서는 NULL |
| ↳ `ADV_PAYM_SEQ` | N | 10 |  | 전도금원장일련번호 | (12/22 추가) <br> 4번 테이블 컬럼값. 1, 2번 테이블에서는 NULL |
| ↳ `ADV_PAYM_NM` | C | 50 |  | 전도금원장명 | (12/28) 추가 |
| ↳ `HAND_DOC_ID` | C | 50 |  | 수기문서번호 | (2021/03/09 추가) |
| ↳ `TRCO_TYPE` | C | 1 |  | 거래처등록유형 | (2021/07/01 추가) <br> 1. Taxable Person <br> 2. Non-Taxable Person <br> 3. Oversea |
| ↳ `TRCO_TAX_ID` | C | 40 |  | 거래처세금ID | (2021/07/01 추가) |
| ↳ `APLY_EXCH_RATE` | N | 20.6 |  | 적용환율 | (2021/07/01 추가) |
| ↳ `TRCO_RPRS_TLPH` | C | 30 |  | 거래처대표전화 | (2021/07/01 추가) |
| ↳ `GST_NO` | C | 100 |  | GST번호 | (2021/07/01 추가) |
| ↳ `EML` | C | 100 |  | 이메일 | (2021/07/01 추가) |
| ↳ `TRCO_BSUN_POST_ADRS` | C | 100 |  | 거래처사업장우편주소 | (2021/07/01 추가) |
| ↳ `TRCO_BSUN_DTL_ADRS` | C | 100 |  | 거래처사업장상세주소 | (2021/07/01 추가) |
| ↳ `TRCO_BSUN_ST` | C | 100 |  | 거래처사업장국가 | (2021/07/01 추가) |
| ↳ `TRCO_BSUN_CITY` | C | 100 |  | 거래처사업장시도 | (2021/07/01 추가) |
| ↳ `TRCO_CMENM` | C | 200 |  | 거래처영문명 | (2021/07/08 추가) |
| ↳ `WHT_CD` | C | 3 |  | 원천세 코드 | (2021/09/29 추가) |
| ↳ `TAX_RATE` | N | 3 |  | 원천세 RATE | (2021/09/29 추가) |
| ↳ `WHT_NM` | C | 100 |  | 원천세 명 | (2021/09/29 추가) |
| ↳ `PAY_WAY_CD` | C | 1 |  | 지급방법코드 | (2022/10/28 추가) |
| ↳ `PAY_WAY_AMT` | N | 20.6 |  | 지급방법금액 | (2022/10/28 추가) |
| ↳ `BANK_CD` | C | 20 |  | 은행코드20 | (2022/10/28 추가) |
| ↳ `BANK_NM` | C | 100 |  | 은행명 | (2022/10/28 추가) |
| ↳ `REF_NO` | C | 100 |  | 참조번호 | (2022/10/28 추가) |
| ↳ `ACCT_NO` | C | 100 |  | 계좌번호 | (2022/10/28 추가) |
| ↳ `TRSC_DT2` | C | 8 |  | 거래일자2 | (2022/10/28 추가) |
| ↳ `CHEQ_NUM` | C | 100 |  | 체크번호 | (2022/10/28 추가) |
| ↳ `EINV_CON_DOC_ID` | C | 50 |  | e-invoice 연결문서ID | (v2023.1.0.4 추가) |
| ↳ `TRCO_NICK_NM` | C | 200 |  | Display Name | v2023.1.0.12 |
| ↳ `PRJT_NO` | C | 20 |  | 프로젝트번호 | v2024.1.42 added |
| ↳ `PRJT_NM` | C | 200 |  | Project Name | v2024.1.43 added |
| ↳ `REC1` | R |  |  | 그룹1 | (2021/06/29 추가) <br> REC가 1번 테이블 결과값 Row이면 7번 테이블 출력항목, <br> 2번 테이블 결과값 Row이면 2번 테이블 출력항목 참조 |
| ↳ ↳ `REG_DT` | C | 8 |  | 등록일자 | (2021/06/29 추가) <br> 2번 테이블 데이터. REC가 2번 테이블 결과값 Row일 때만 데이터 있음. |
| ↳ ↳ `SEQ_NO` | N | 10 |  | 일련번호 | (2021/06/29 추가) <br> 2번 테이블 데이터. REC가 2번 테이블 결과값 Row일 때만 데이터 있음. |
| ↳ ↳ `FILE_ID` | C | 100 |  | 파일ID | (2021/06/29 추가) <br> 7번 테이블 데이터. REC가 1번 테이블 결과값 Row일 때만 데이터 있음. |
| ↳ ↳ `SAVE_FILE_NM` | C | 500 |  | 저장파일명 | (2021/06/29 이동) |
| ↳ ↳ `FILE_URL` | C | 500 |  | 첨부파일 URL | (2021/07/01) 추가 |
| ↳ ↳ `ORG_FILE_NM` | C | 500 |  | 원본파일명 | (v2023.1.0.9 추가) |
| ↳ ↳ `FILE_SIZE` | C | 100 |  | 파일크기 | (v2023.1.0.9 추가) |

### Process rules (verbatim from the sheet)

Input section:

```text
1, 2, 4번 테이블을 UNION ALL로 조회. (12/22 변경)
3번 테이블은 1번 테이블의 BUSA_USAG_CD를 USE_USAG_CD로 Join 하여 조회.
3번 테이블 조회 쿼리는 wb_get_usag_nm DB Function으로 대체.
4번 테이블은 BUSA_DOC_MSTR >> ADV_PAYM_TRNS_SEQ 로 Join 하여 조회.
(2021/03/19 추가) 6번 테이블은 BIZ_KEY, DOC_ID로 Outer Join하여 조회.

※ 2번 테이블 출력항목.
INQ_GRP : REG_DT 컬럼값이 이번주 내 날짜이면 '0000', 그 외에는 REG_DT 컬럼값을 넣어줌.
TRCO_CMNM : '사진보관' Fix text.
USE_DT : REG_DT 컬럼값.
SAVE_FILE_NM : FILE_URL 컬럼값.
TRSC_WEEK : REG_DT 컬람값의 요일값.
PROV_STTS : 'PP' Fix text.
DOC_ID, TRCO_TRSC_CCY_CD, TOTL_AMT, BUSA_USAG_CD, CONT, USER_NM, RCPT_TYPE : Null
REC1 >> REG_DT, SEQ_NO : 2번 테이블 결과값 그대로.
REC1 >> FILE_ID : Null
REC1 >> SAVE_FILE_NM : FILE_URL 컬럼값.

※ 1번 테이블 추가 Where 조건
BIZ_KEY, USER_ID : (공통부)
DOC_TYPE : 'E'
PROV_STTS NOT IN ('EE', 'VD') (05/14 변경)
REG_CHNL : 'MOB'

※ 2번 테이블 Where 조건
BIZ_KEY, USER_ID : (공통부)
WRITE_YN : 'N' (2021/06/29 추가)
※ 7번 테이블 Where 조건 : BIZ_KEY, DOC_ID

(12/22 추가)

※ 4번 테이블 Where 조건(5번 테이블을 BIZ_KEY, ADV_PAYM_SEQ 로 JOIN하여 같이 조회.)
BIZ_KEY : (공통부)
REG_ID : USER_ID(공통부)
INOUT_DV : '2'
REG_CHNL : 'MOB'

(2021/06/29 아래내용 삭제)
※ 4번 테이블 출력항목 BUSA_DOC_MSTR >> ADV_PAYM_TRNS_SEQ 컬럼값이 있으면 일부 출력항목을 아래와 같이 처리.
SAVE_FILE_NM : 
'{WABOOKS_URL}/fnsh_0020_007_r002.act?BIZ_KEY={공통부}&ADV_PAYM_TRNS_SEQ=:ADV_PAYM_TRNS_SEQ'

(2021/06/29 추가)
REC가 1번 테이블 결과값 Row인 경우 REC1 Row는 아래와 같이 처리.
REC1 >> REG_DT, SEQ_NO : Null
REC1 >> FILE_ID : 7번 테이블 컬럼값.
REC1 >> SAVE_FILE_NM : 아래와 같이 처리.
{와북URL}/prch_0006_001_r002.act?DOC_ID={7번 테이블 DOC_ID}&FILE_ID={7번 테이블 FILE_ID}
```

---

<a id="wabooks_mrec_r009"></a>

## `WABOOKS_MREC_R009` — Receipt detail

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_R009` |
| Sheet name (source) | `WABOOKS_MREC_R009` |
| Sheet title (원문) | Receipt Detail |
| Business area (업무) | Receipt Detail |
| Screen (source index) | Expenses Approval |
| Legacy web reference | `WABOOKS_MREC_R006` |
| Legacy tables (테이블) | 1. BUSA_DOC_MSTR <br> 2. MREC_PIC_STORE <br> 3. USAG_INFM <br> 4. ADV_PAYM_TRNS_HSTR <br> 5. ADV_PAYM_MSTR <br> 6. BUSA_DOC_RCV_PAY_HIS <br> 7. BUSA_DOC_FILE |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | `GET /api/v1/payment/card-transactions/receipts/{doc_id}` |
| Coverage | **PARTIAL** |
| Last spec revision | Updated v2024.1.20 |

**Delta.** Missing the `ADV_PAYM_TRNS_HSTR` branch and the `MREC_PIC_STORE` photo set.

### Input — 1 field

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `DOC_ID` | C |  | Y | Document ID |  |

### Output — 53 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `INQ_GRP` | C | 4 |  | 조회그룹 | USE_DT가 이번주 내 날짜이면 '0000', <br> 그 외에는 USE_DT의 YYMM 을 넣어줌. <br> ※ 2번 테이블 출력항목 참조. |
| `DOC_ID` | C | 50 |  | 문서ID |  |
| `TRCO_CMNM` | C | 200 |  | 거래처회사명 | 1번 테이블 컬럼값 + 2번 테이블 출력항목 참조. |
| `USE_DT` | C | 14 |  | 사용일시 | 1번 테이블 컬럼값 + 2번 테이블 출력항목 참조. |
| `TRCO_TRSC_CCY_CD` | C | 3 |  | 거래처거래통화코드 |  |
| `TOTL_AMT` | N | 20.6 |  | 총금액 |  |
| `BUSA_USAG_CD` | C | 20 |  | 매입매출용도코드 |  |
| `USAG_NM` | C | 100 |  | 용도명 | wb_get_usag_nm DB function |
| `CONT` | C | 200 |  | 내용 |  |
| `USER_NM` | C | 100 |  | 사용자명 |  |
| `PROV_STTS` | C | 2 |  | 증빙상태 | PP : 사진등록 <br> OP : 제출완료 <br> 1번 테이블 컬럼값 + 2번 테이블 출력항목 참조. |
| `RCPT_TYPE` | C | 1 |  | 영수증타입 | 'P(사진)' fix. |
| `TRSC_WEEK` | C | 1 |  | 거래요일 | 1번 테이블 USE_DT의 YYYYMMDD 를 추출하여 해당 날짜의 요일값을 추출. + 2번 테이블 출력항목 참조. <br> 유효값은 1(월요일) ~ 7(일요일) |
| `SEQ_NO` | N | 10 |  | 일련번호 | 1, 4번 테이블 컬럼값. 2번 테이블과 혼동하지 않도록 주의. |
| `REG_DT` | C | 8 |  | 등록일자 | 1, 4번 테이블 컬럼값. 2번 테이블과 혼동하지 않도록 주의. |
| `PC_MOD_YN` | C | 1 |  | PC수정여부 | 1, 4번 테이블 컬럼값. |
| `ADV_PAYM_TRNS_SEQ` | N | 10 |  | 전도금원장거래내역일련번호 | (12/23 추가) <br> 4번 테이블 컬럼값. 1, 2번 테이블에서는 NULL |
| `ADV_PAYM_SEQ` | N | 10 |  | 전도금원장일련번호 | (12/22 추가) <br> 4번 테이블 컬럼값. 1, 2번 테이블에서는 NULL |
| `ADV_PAYM_NM` | C | 50 |  | 전도금원장명 | (12/28) 추가 |
| `HAND_DOC_ID` | C | 50 |  | 수기문서번호 | (2021/03/09 추가) |
| `TRCO_TYPE` | C | 1 |  | 거래처등록유형 | (2021/07/01 추가) <br> 1. Taxable Person <br> 2. Non-Taxable Person <br> 3. Oversea |
| `TRCO_TAX_ID` | C | 40 |  | 거래처세금ID |  |
| `APLY_EXCH_RATE` | N | 20.6 |  | 적용환율 |  |
| `TRCO_RPRS_TLPH` | C | 30 |  | 거래처대표전화 |  |
| `GST_NO` | C | 100 |  | GST번호 |  |
| `EML` | C | 100 |  | 이메일 |  |
| `TRCO_BSUN_POST_ADRS` | C | 100 |  | 거래처사업장우편주소 |  |
| `TRCO_BSUN_DTL_ADRS` | C | 100 |  | 거래처사업장상세주소 |  |
| `TRCO_BSUN_ST` | C | 100 |  | 거래처사업장국가 |  |
| `TRCO_BSUN_CITY` | C | 100 |  | 거래처사업장시도 |  |
| `TRCO_CMENM` | C | 200 |  | 거래처영문명 |  |
| `WHT_CD` | C | 3 |  | 원천세 코드 |  |
| `TAX_RATE` | N | 3 |  | 원천세 RATE |  |
| `WHT_NM` | C | 100 |  | 원천세 명 |  |
| `PAY_WAY_CD` | C | 1 |  | 지급방법코드 |  |
| `PAY_WAY_AMT` | N | 20.6 |  | 지급방법금액 |  |
| `BANK_CD` | C | 20 |  | 은행코드20 |  |
| `BANK_NM` | C | 100 |  | 은행명 |  |
| `REF_NO` | C | 100 |  | 참조번호 |  |
| `ACCT_NO` | C | 100 |  | 계좌번호 |  |
| `TRSC_DT2` | C | 8 |  | 거래일자2 |  |
| `CHEQ_NUM` | C | 100 |  | 체크번호 |  |
| `EINV_CON_DOC_ID` | C | 50 |  | e-invoice 연결문서ID |  |
| `TRCO_NICK_NM` | C | 200 |  | Display Name |  |
| `TRCO_KEY` | C | 20 |  | 거래처키 | Added v2024.1.20 <br> Table 1 |
| `REC1` | R |  |  | 그룹1 | REC가 1번 테이블 결과값 Row이면 7번 테이블 출력항목, <br> 2번 테이블 결과값 Row이면 2번 테이블 출력항목 참조 |
| ↳ `REG_DT` | C | 8 |  | 등록일자 | (2021/06/29 추가) <br> 2번 테이블 데이터. REC가 2번 테이블 결과값 Row일 때만 데이터 있음. |
| ↳ `SEQ_NO` | N | 10 |  | 일련번호 | (2021/06/29 추가) <br> 2번 테이블 데이터. REC가 2번 테이블 결과값 Row일 때만 데이터 있음. |
| ↳ `FILE_ID` | C | 100 |  | 파일ID | (2021/06/29 추가) <br> 7번 테이블 데이터. REC가 1번 테이블 결과값 Row일 때만 데이터 있음. |
| ↳ `SAVE_FILE_NM` | C | 500 |  | 저장파일명 |  |
| ↳ `FILE_URL` | C | 500 |  | 첨부파일 URL |  |
| ↳ `ORG_FILE_NM` | C | 500 |  | 원본파일명 |  |
| ↳ `FILE_SIZE` | C | 100 |  | 파일크기 |  |

### Process rules (verbatim from the sheet)

Input section:

```text
Table 1,4 UNION ALL
Table 3 >> use wb_get_usag_nm DB Function
Table 4 Join Table 1 on ADV_PAYM_TRNS_SEQ of Table 1
Table 6 Outer Join Table 1 on BIZ_KEY, DOC_ID of Table 1

※ Table 1 add Where condition
BIZ_KEY, USER_ID : (공통부)
DOC_TYPE : 'E'
PROV_STTS NOT IN ('EE', 'VD')
REG_CHNL : 'MOB' ※ Table 7 add where condition : BIZ_KEY, DOC_ID

REC가 1번 테이블 결과값 Row인 경우 REC1 Row는 아래와 같이 처리.
REC1 >> REG_DT, SEQ_NO : Null
REC1 >> FILE_ID : From Table 7
REC1 >> SAVE_FILE_NM : 아래와 같이 처리.
{와북URL}/prch_0006_001_r002.act?DOC_ID={7번 테이블 DOC_ID}&FILE_ID={7번 테이블 FILE_ID}
```

---

<a id="wabooks_mrec_c002"></a>

## `WABOOKS_MREC_C002` — Upload a receipt photo (base64)

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_C002` |
| Sheet name (source) | `WABOOKS_MREC_C002` |
| Sheet title (원문) | 사진업로드하기 |
| Business area (업무) | 영수증관리API |
| Screen (source index) | — |
| Legacy web reference | — |
| Legacy tables (테이블) | 1. MREC_PIC_STORE <br> 2. MREC_PIC_STORE_FILE |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | `POST /api/v1/payment/card-transactions/receipts/{doc_id}/files` |
| Coverage | **PARTIAL** |
| Last spec revision | — |

**Delta.** The app uploads photos *before* a `DOC_ID` exists (into `MREC_PIC_STORE`), so a staging endpoint that does not require a document id is needed.

### Input — 3 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `REC_IN` | R |  |  | 그룹 |  |
| ↳ `FILE_NM` | C | 500 | Y | 파일명 | (2021/07/01) 반복부로 변경 |
| ↳ `FILE_DATA` | B | 4000 | Y | 파일데이터 | (2021/07/01) 반복부로 변경 |

### Output — 4 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `REG_DT` | C | 8 |  | 등록일자 | 테이블에 Insert한 REG_DT 컬럼값. |
| `SEQ_NO` | N | 10 |  | 일련번호 | 테이블에 Insert한 SEQ_NO 컬럼값. |
| `REC_OUT` | R |  |  | 그룹 |  |
| ↳ `FILE_URL` | C | 200 |  | 파일URL | {와북URL}/file/{biz_key}/WABOOKS_MREC_C002/{FILE_NM} <br> (2021/07/01 반복부로 수정) <br> (10/06 수정) <br> {와북URL}/prch_0001_004.act?BIZ_KEY={BIZ_KEY}&REG_DT={REG_DT}&SEQ_NO={SEQ_NO}&FILE_ID={FILE_ID} <br> 형태로 Return. |

### Process rules (verbatim from the sheet)

Input section:

```text
※ 모바일 팀에서 올리는 파일 Binary 데이터를

/WAS_DATA/file/{biz_key}/WABOOKS_MREC_C002

디렉토리에 업로드 후(파일명은 FILE_NM Input 값 대로) URL 정보를 Return.

(※ 이 때 FILE_NM이 중복될 경우 파일명 뒤에 Sequence 붙여서 저장)

※ 파일 업로드 완료 후 MREC_PIC_STORE, MREC_PIC_STORE_FILE 테이블에 데이터 Insert.

- MREC_PIC_STORE 컬럼별 데이터
BIZ_KEY, USER_ID : 공통부
REG_DT : SYSDATE
WRITE_YN : 'N'

- MREC_PIC_STORE_FILE 컬럼별 데이터 (2021/07/01 추가)
(10/06 추가)
ORG_FILE_NM, SAVE_FILE_NM, FILE_SIZE : 공통 파일 저장 양식과 동일
FILE_URL : {와북URL}/prch_0001_004.act?BIZ_KEY={BIZ_KEY}&REG_DT={REG_DT}&SEQ_NO={SEQ_NO}&FILE_ID={FILE_ID}
```

---

<a id="wabooks_mrec_d001"></a>

## `WABOOKS_MREC_D001` — Delete a receipt photo

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_D001` |
| Sheet name (source) | `WABOOKS_MREC_D001` |
| Sheet title (원문) | 사진삭제 |
| Business area (업무) | 영수증관리API |
| Screen (source index) | — |
| Legacy web reference | — |
| Legacy tables (테이블) | MREC_PIC_STORE |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | `DELETE /api/v1/payment/card-transactions/receipts/{doc_id}/files/{file_id}` |
| Coverage | **PARTIAL** |
| Last spec revision | — |

**Delta.** Same staging delta as C002.

### Input — 2 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `REG_DT` | C | 8 | Y | 등록일자 | 테이블 DELETE WHERE 조건 |
| `SEQ_NO` | N | 10 | Y | 일련번호 | 테이블 DELETE WHERE 조건 |

### Output — 0 fields

_No output payload beyond the common `RSLT_CD` / `RSLT_MSG` envelope._

### Process rules (verbatim from the sheet)

Input section:

```text
※ DELETE Querry 실행 전에,
Input 값으로 테이블의 데이터를 SELECT하여 FILE_URL 컬럼값에서 파일명을 추출하여,

/WAS_DATA/webRoot/wabooks/file/{biz_key}/WABOOKS_MREC_C002

디렉토리에 있는 해당 파일명의 파일 삭제.
서버상의 파일 삭제 후, DELETE Query 진행.

- 기타 Where 조건
BIZ_KEY, USER_ID : 공통부
```

---

<a id="wabooks_mrec_c003"></a>

## `WABOOKS_MREC_C003` — Create a receipt

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_C003` |
| Sheet name (source) | `WABOOKS_MREC_C003` |
| Sheet title (원문) | 영수증작성 |
| Business area (업무) | 영수증관리API |
| Screen (source index) | — |
| Legacy web reference | — |
| Legacy tables (테이블) | 1. BUSA_DOC_MSTR <br> 2. BUSA_DOC_MSTR_HIS <br> 3. BUSI_INFM <br> 4. MREC_PIC_STORE <br> 5. ADV_PAYM_TRNS_HSTR <br> 6. BUSA_DOC_FILE <br> 7. MREC_PIC_STORE_FILE <br> 8. ADV_PAYM_MSTR <br> 9. USER_INFM |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | `POST /api/v1/payment/card-transactions/receipts` |
| Coverage | **PARTIAL** |
| Last spec revision | Updated v2024.1.21 |

**Delta.** Same `BUSA_DOC_MSTR` target. Needs `REG_CHNL='MOB'`, the `MREC_PIC_STORE` link, withholding fields, `PRJT_NO` and `EINV_CON_DOC_ID`.

### Input — 39 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `TRCO_CMNM` | C | 200 |  | 거래처회사명 | 1, 2번 테이블 INSERT (ADV_PAYM_SEQ Field값이 있으면 5번 테이블 Insert) |
| `USE_DT` | C | 14 |  | 사용일시 | 1, 2번 테이블 INSERT (ADV_PAYM_SEQ Field값이 있으면 5번 테이블 Insert) <br> YYYYMMDD(날짜) 만 받는 것으로 변경. (2021-02-23) |
| `TRCO_TRSC_CCY_CD` | C | 3 |  | 거래처거래통화코드 | 1, 2번 테이블 INSERT (ADV_PAYM_SEQ Field값이 있으면 5번 테이블 Insert) <br> (v2023.1.0.4 변경) <br> Vietnam version 에서는 VND 고정 |
| `TOTL_AMT` | N | 20.6 |  | 총금액 | 1, 2번 테이블 INSERT (ADV_PAYM_SEQ Field값이 있으면 5번 테이블 Insert) |
| `BUSA_USAG_CD` | C | 20 |  | 매입매출용도코드 | 1, 2번 테이블 INSERT (ADV_PAYM_SEQ Field값이 있으면 5번 테이블 Insert) |
| `ADV_PAYM_SEQ` | N | 10 |  | 전도금원장일련번호 | (12/22 추가). 5번 테이블 Insert <br> RCV_PAY_WAY_CD가 'P'이면 필수, 그 외에는 NULL |
| `CONT` | C | 200 |  | 내용 | 1, 2번 테이블 INSERT (ADV_PAYM_SEQ Field값이 있으면 5번 테이블 Insert) |
| `USER_NM` | C | 100 |  | 사용자명 | 1, 2번 테이블 INSERT <br> (v2023.1.0.4 변경) <br> Vietnam version 에서는 NULL |
| `REG_TYPE` | C | 1 | Y | Register Type | Added v2024.1.21 <br> 'D':Add Detail (From 'Receipt' Page), <br> 'M': Add Manually (From 'Select Expense' Page) |
| `REG_DT` | C | 8 |  | 등록일자 | 1, 2번 테이블 INSERT, 4번 테이블 UPDATE WHERE 조건 <br> (ADV_PAYM_SEQ Field값이 있으면 5번 테이블 Insert) <br> v2024.1.21 <br> Required if REG_TYPE = 'D' |
| `SEQ_NO` | N | 10 |  | 일련번호 | 1, 2번 테이블 INSERT, 4번 테이블 UPDATE WHERE 조건 <br> (ADV_PAYM_SEQ Field값이 있으면 5번 테이블 Insert) <br> v2024.1.21 <br> Required if REG_TYPE = 'D' |
| `HAND_DOC_ID` | C | 50 |  | 수기문서번호 | (2021/03/09 추가) <br> (v2023.1.0.4 변경) <br> Vietnam version 에서는 NULL. <br> (DOC_ID는 Default Rule로 Generate) |
| `TRCO_TYPE` | C | 1 |  | 거래처등록유형 | (2021/06/28 추가) <br> TRCO_KEY가 '99999999999999999999' 이면 필수. <br> 1. Taxable Person <br> 2. Non-Taxable Person <br> 3. Oversea <br> Vietnam version 에서는 NULL |
| `TRCO_TAX_ID` | C | 40 |  | 거래처세금ID | (2021/06/28 추가) <br> TRCO_KEY가 '99999999999999999999' 이고, TRCO_TYPE이 '1'이면 필수. <br> (v2023.1.0.4 변경) <br> Vietnam Version의 경우 EINV_CON_DOC_ID 값이 있으면 필수. |
| `APLY_EXCH_RATE` | N | 20.6 |  | 적용환율 | (2021/06/28 추가) <br> TRCO_TRSC_CCY_CD가 'USD'이면 필수 <br> Vietnam version 에서는 NULL |
| `TRCO_RPRS_TLPH` | C | 30 |  | 거래처대표전화 | (2021/06/28 추가) <br> TRCO_KEY가 '99999999999999999999' 이고, TRCO_TYPE이 '2'이면 필수. <br> Vietnam version 에서는 NULL |
| `GST_NO` | C | 100 |  | GST번호 | (2021/06/28 추가) <br> TRCO_KEY가 '99999999999999999999' 이고, TRCO_TYPE이 '3'이면 필수. <br> Vietnam version 에서는 NULL |
| `EML` | C | 100 |  | 이메일 | (2021/06/28 추가) <br> TRCO_KEY가 '99999999999999999999' 이고, TRCO_TYPE이 '3'이면 필수. <br> Vietnam version 에서는 NULL |
| `TRCO_BSUN_POST_ADRS` | C | 100 |  | 거래처사업장우편주소 | (2021/06/28 추가) <br> TRCO_KEY가 '99999999999999999999' 이고, TRCO_TYPE이 '3'이면 필수. |
| `TRCO_BSUN_DTL_ADRS` | C | 100 |  | 거래처사업장상세주소 | (2021/06/28 추가) |
| `TRCO_BSUN_ST` | C | 100 |  | 거래처사업장국가 | (2021/06/28 추가) <br> TRCO_KEY가 '99999999999999999999' 이고, TRCO_TYPE이 '3'이면 필수. |
| `TRCO_BSUN_CITY` | C | 100 |  | 거래처사업장시도 | (2021/06/28 추가) <br> TRCO_KEY가 '99999999999999999999' 이고, TRCO_TYPE이 '3'이면 필수. |
| `TRCO_KEY` | C | 20 |  | 거래처키 | (2021/07/07 추가) |
| `WTH_CD` | C | 3 |  | 원천세 코드 | (2021/09/29 추가) <br> Vietnam version 에서는 NULL |
| `PAY_WAY_CD` | C | 1 |  | 지급방법코드 | **see note 1 below** |
| `PAY_WAY_AMT` | N | 20.6 |  | 지급방법금액 |  |
| `BANK_CD` | C | 20 |  | 은행코드20 |  |
| `ACCT_NO` | C | 100 |  | 계좌번호 |  |
| `REF_NO` | C | 100 |  | 참조번호 |  |
| `TRSC_DT2` | C | 8 |  | 거래일자2 |  |
| `PRJT_NO` | C | 20 |  | 프로젝트번호 | v2024.1.42 added |
| `EINV_CON_DOC_ID` | C | 50 |  | e-invoice 연결문서 ID | (v2023.1.0.4 추가) |
| `REC` | R |  |  | 그룹 | (2021/06/29) 추가 |
| ↳ `FILE_NM` | C | 500 |  | 파일명 | (2021/06/29) 추가 |
| ↳ `FILE_DATA` | B | 4000 |  | 파일데이터 | (2021/06/29) 추가 |
| `REC2` | R |  |  | 그룹2 | (2021/08/02) 추가 <br> C002에서 추가한 첨부파일 삭제용 REC |
| ↳ `REG_DT` | C | 8 | Y | 등록일자 | (2021/08/02) 추가 <br> R006 >> REC1 에서 가져옴 |
| ↳ `SEQ_NO` | N | 10 | Y | 일련번호 | (2021/08/02) 추가 <br> R006 >> REC1 에서 가져옴 |
| ↳ `FILE_ID` | C | 100 | Y | 파일ID | (2021/08/02) 추가 <br> R006 >> REC1 에서 가져옴 |

**Note 1 — `PAY_WAY_CD`** (verbatim from the sheet):

```text
(2022/10/11 추가)

prch_0005_001_c001 참조

※ 참고 (v2023.1.0.5 삭제)
베트남 버전은 PAY_WAY_CD에 E(기타경비), I(개인경비) 추가됨.
E(기타경비)인 경우 PAY_WAY_AMT ~ TRSC_DT2 컬럼 Insert 항목 없음.

I(개인경비)인 경우 ADV_PAYM_TRNS_HSTR 테이블 데이터 Insert 하면서 생긴 ADV_PAYM_TRNS_SEQ 컬럼값을 REF_NO 컬럼에 Insert.

※ 참고 (v2023.1.0.5 추가)
베트남 버전은 지출방법 페이지의 선택 수단에 따라 현금출납장이면 C(현금), 전도금원장이면 P(전도금) 로 셋팅해야 함.
```

### Output — 3 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `DOC_ID` | C | 50 |  | 문서ID |  |
| `ADV_PAYM_TRNS_SEQ` | N | 10 |  | 전도금입출금일련번호 |  |
| `ADV_PAYM_SEQ` | N | 10 |  | 전도금원장일련번호 |  |

### Process rules (verbatim from the sheet)

Input section:

```text
※ ADV_PAYM_SEQ 필드값이 없으면 아래 Process 진행(기존 Process와 동일).

- 1, 2번 테이블 추가 INSERT 데이터.
BIZ_KEY, USER_ID : (공통부)
DOC_TYPE : 'E'
ISSU_CMNM, ISSU_CMENM, ISSU_RPPR_NM, ISSU_RPRS_TLPH, ISSU_FXNO, ISSU_BSUN_POST_ADRS, ISSU_BSUN_DTL_ADRS, ISSU_BSUN_ST, ISSU_BSUN_CITY : 3번 테이블을 BIZ_KEY로 검색하여 각 컬럼에 알맞은 컬럼값 INSERT.
ISSU_DT : SYSDATE
PROV_STTS : 'OP' (05/14 변경)
REG_CHNL : 'MOB'
PC_MOD_YN : 'N'
TRCO_CMENM : TRCO_CMNM(Input) (2021/01/04 추가)
TRCO_TAX_ID ~ TRCO_TRSC_CCY_CD : TRCO_KEY가 '99999999999999999999'가 아닌 경우 TRCO_INFM에서 검색하여 입력. '99999999999999999999' 이면 Input값 입력. (2021/07/07 추가)

(2021/06/08) 추가
- 6번 테이블 추가 INSERT 데이터.
FILE_ID : (Sequence)
ORG_FILE_NM, SAVE_FILE_NM, FILE_SIZE : 7번 테이블에서 가져옴.
(2021/06/29 추가)
- REC Group에 데이터가 있을 경우 FILE_NM, FILE_DATA를 이용해 6번 테이블에 추가 데이터 적재. (v2023.1.0.5 삭제)
- PAY_WAY_CD가 'I'인 경우 8번, 5번 테이블 데이터 INSERT.

※ 8번 테이블 추가 Insert 데이터.
ADV_PAYM_NM : 9번 테이블의 USER_NM 컬럼값(Where 조건 : 공통부 USER_ID)
ADV_PAYM_RSPB_ID : (공통부 USER_ID)
PRV_ADV_YN : 'Y'

※ 5번 테이블 추가 Insert 데이터.
ADV_PAYM_SEQ : 8번 테이블에서 추가한 ADV_PAYM_SEQ 컬럼값.
그 외에는 기존 5번 테이블 INSERT 데이터와 동일한 형식.
단, 이 Case에서는 FnshHelper.fnsh_0020_update_bal java 메소드 실행하지 않음.
(ADV_PAYM_SEQ 값이 8번 테이블에서 PRV_ADV_YN이 'Y'인 경우 5번 테이블 BAL 컬럼값의 음수값을 허용함.)

4번 테이블 UPDATE.

- UPDATE 컬럼.
WRITE_YN : 'Y'
수정자ID : (Session)
수정일시 : SYSDATE
- UPDATE 기타 WHERE 조건
BIZ_KEY, USER_ID : (Session) (12/22 추가)

※ ADV_PAYM_SEQ 필드값이 있으면 아래의 Process 대로 진행.

- 5번 테이블 INSERT. 데이터는 아래 참조.

BIZ_KEY : (공통부)
TRSC_DT : USE_DT(YYYYMMDD)
TRSC_TM : USE_DT(HHMMSS)
ADV_PAYM_TRNS_SEQ : (Sequence)
ADV_PAYM_SEQ : (Input)
INOUT_DV : '2'
TRSC_AMT : TOTL_AMT
USE_USAG_CD : BUSA_USAG_CD
CCY_CD : TRCO_TRSC_CCY_CD
TRCO_KEY : '99999999999999999999'
TRCO_NM : TRCO_CMNM
TRCO_ENM : TRCO_CMNM(Input) (2021/01/04 추가)
BAL : 0
CONT : (Input)
REG_ID, MOD_ID : USER_ID(공통부)
REG_DTM, MOD_DTM : SYSDATE
REG_CHNL : 'MOB'
PC_MOD_YN : 'N' - 5번 테이블 BAL 컬럼을 Update하기 위하여 FnshHelper.fnsh_0020_update_bal java 메소드 실행. 해당 메소드에서 Exception 발생 시 DB Rollback 및 throw Exception 처리.

4번 테이블 UPDATE.
- UPDATE 컬럼.
WRITE_YN : 'Y'
수정자ID : (Session)
수정일시 : SYSDATE (옆에서 계속)

'- UPDATE 기타 WHERE 조건
BIZ_KEY, USER_ID : (Session)

(2021/02/05 추가)

- 1, 2번 테이블 추가 INSERT 데이터.
(ADV_PAYM_SEQ 필드값이 없을 때의 Insert 방식과 동일하며, 추가로 ADV_PAYM_TRNS_SEQ만 5번 테이블 동일 컬럼에 Insert된 값으로 Insert.)

(2021/08/02 추가)

REC2 Group에 데이터가 있을 경우 7번 테이블에서 SAVE_FILE_NM 컬럼에 저장된 값의 파일을 /WAS_DATA/file/{biz_key}/WABOOKS_MREC_C002 디렉토리에서 찾아서 삭제 처리 후, DB 값도 삭제.
```

---

<a id="wabooks_mrec_c004"></a>

## `WABOOKS_MREC_C004` — Update a receipt

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_C004` |
| Sheet name (source) | `WABOOKS_MREC_C004` |
| Sheet title (원문) | 영수증수정 |
| Business area (업무) | 영수증관리API |
| Screen (source index) | — |
| Legacy web reference | — |
| Legacy tables (테이블) | 1. BUSA_DOC_MSTR <br> 2. BUSA_DOC_MSTR_HIS <br> 3. BUSI_INFM <br> 4. MREC_PIC_STORE <br> 5. ADV_PAYM_TRNS_HSTR <br> 6. BUSA_DOC_FILE |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | — (none) |
| Coverage | **MISSING** |
| Last spec revision | — |

**Delta.** There is no PUT/PATCH for receipts - only create.

### Input — 39 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `CORR_TYPE` | C | 1 | Y | 수정타입 | U : 수정, D : 삭제 |
| `DOC_ID` | C | 50 | Y | 문서ID | 1, 2 혹은 5번 테이블 WHERE 조건 |
| `TRCO_CMNM` | C | 200 |  | 거래처회사명 | CORR_TYPE이 'U'일 때 1, 2 혹은 5번 테이블(TRCO_NM, TRCO_ENM) UPDATE <br> (2021/01/04 추가) 1, 2번 테이블 Update할 때, TRCO_CMENM 컬럼값도 같은 값으로 Update. |
| `USE_DT` | C | 14 |  | 사용일시 | CORR_TYPE이 'U'일 때 1, 2 혹은 5번 테이블(TRSC_DT, TRSC_TM) UPDATE <br> YYYYMMDD(날짜) 만 받는 것으로 변경. (2021-02-23) |
| `TRCO_TRSC_CCY_CD` | C | 3 |  | 거래처거래통화코드 | CORR_TYPE이 'U'일 때 1, 2 혹은 5번 테이블(CCY_CD) UPDATE <br> (v2023.1.0.4 변경) <br> Vietnam version 에서는 VND 고정 |
| `TOTL_AMT` | N | 20.6 |  | 총금액 | CORR_TYPE이 'U'일 때 1, 2 혹은 5번 테이블(TRSC_AMT) UPDATE |
| `ADV_PAYM_TRNS_SEQ` | N | 10 |  | 전도금원장거래내역일련번호 | (12/23 추가). 5번 테이블 Update Where 조건 |
| `ADV_PAYM_SEQ` | N | 10 |  | 전도금원장일련번호 | (12/22 추가). 5번 테이블 Update <br> RCV_PAY_WAY_CD가 'P'이면 필수, 그 외에는 NULL |
| `BUSA_USAG_CD` | C | 20 |  | 매입매출용도코드 | CORR_TYPE이 'U'일 때 1, 2 혹은 5번 테이블(USE_USAG_CD) UPDATE |
| `CONT` | C | 200 |  | 내용 | CORR_TYPE이 'U'일 때 1, 2 혹은 5번 테이블(CONT) UPDATE |
| `USER_NM` | C | 100 |  | 사용자명 | CORR_TYPE이 'U'일 때 1, 2 혹은 5번 테이블(USER_NM) UPDATE <br> (v2023.1.0.4 변경) <br> Vietnam version 에서는 NULL |
| `REG_DT` | C | 8 | Y | 등록일자 | CORR_TYPE이 'U'일 때 1, 2 혹은 5번 테이블 UPDATE <br> CORR_TYPE이 'D'일 때 4번 테이블 DELETE WHERE 조건 |
| `SEQ_NO` | N | 10 | Y | 일련번호 | CORR_TYPE이 'U'일 때 1, 2 혹은 5번 테이블 UPDATE <br> CORR_TYPE이 'D'일 때 4번 테이블 DELETE WHERE 조건 |
| `HAND_DOC_ID` | C | 50 |  | 수기문서번호 | (2021/03/09 추가) <br> (v2023.1.0.4 변경) <br> Vietnam version 에서는 NULL. |
| `TRCO_TYPE` | C | 1 |  | 거래처등록유형 | (2021/06/28 추가) <br> TRCO_KEY가 '99999999999999999999' 이면 필수. <br> 1. Taxable Person <br> 2. Non-Taxable Person <br> 3. Oversea <br> Vietnam version 에서는 NULL |
| `TRCO_TAX_ID` | C | 40 |  | 거래처세금ID | (2021/06/28 추가) <br> TRCO_KEY가 '99999999999999999999' 이고, TRCO_TYPE이 '1'이면 필수. <br> (v2023.1.0.4 변경) <br> Vietnam Version의 경우 EINV_CON_DOC_ID 값이 있으면 필수. |
| `APLY_EXCH_RATE` | N | 20.6 |  | 적용환율 | (2021/06/28 추가) <br> TRCO_TRSC_CCY_CD가 'USD'이면 필수 <br> Vietnam version 에서는 NULL |
| `TRCO_RPRS_TLPH` | C | 30 |  | 거래처대표전화 | (2021/06/28 추가) <br> TRCO_KEY가 '99999999999999999999' 이고, TRCO_TYPE이 '2'이면 필수. <br> Vietnam version 에서는 NULL |
| `GST_NO` | C | 100 |  | GST번호 | (2021/06/28 추가) <br> TRCO_KEY가 '99999999999999999999' 이고, TRCO_TYPE이 '3'이면 필수. <br> Vietnam version 에서는 NULL |
| `EML` | C | 100 |  | 이메일 | (2021/06/28 추가) <br> TRCO_KEY가 '99999999999999999999' 이고, TRCO_TYPE이 '3'이면 필수. <br> Vietnam version 에서는 NULL |
| `TRCO_BSUN_POST_ADRS` | C | 100 |  | 거래처사업장우편주소 | (2021/06/28 추가) <br> TRCO_KEY가 '99999999999999999999' 이고, TRCO_TYPE이 '3'이면 필수. |
| `TRCO_BSUN_DTL_ADRS` | C | 100 |  | 거래처사업장상세주소 | (2021/06/28 추가) |
| `TRCO_BSUN_ST` | C | 100 |  | 거래처사업장국가 | (2021/06/28 추가) <br> TRCO_KEY가 '99999999999999999999' 이고, TRCO_TYPE이 '3'이면 필수. |
| `TRCO_BSUN_CITY` | C | 100 |  | 거래처사업장시도 | (2021/06/28 추가) <br> TRCO_KEY가 '99999999999999999999' 이고, TRCO_TYPE이 '3'이면 필수. |
| `TRCO_KEY` | C | 20 |  | 거래처키 | (2021/07/07 추가) |
| `PRJT_NO` | C | 20 |  | 프로젝트번호 | v2024.1.42 added |
| `WTH_CD` | C | 3 |  | 원천세 코드 | (2021/09/29 추가) <br> null - not withholding <br> Any value - Reference from SYS_WTH_SET_INFM table - WABOOKS_MREC_R901 common API |
| `PAY_WAY_CD` | C | 1 |  | 지급방법코드 | (2022/10/11 추가) <br> prch_0006_001_u001 참조 |
| `PAY_WAY_AMT` | N | 20.6 |  | 지급방법금액 |  |
| `BANK_CD` | C | 20 |  | 은행코드20 |  |
| `ACCT_NO` | C | 100 |  | 계좌번호 |  |
| `REF_NO` | C | 100 |  | 참조번호 |  |
| `TRSC_DT2` | C | 8 |  | 거래일자2 |  |
| `EINV_CON_DOC_ID` | C | 50 |  | e-invoice 연결문서 ID | (v2023.1.0.4 추가) |
| `REC1` | R |  |  | 그룹1 | (2021/06/29) 추가. <br> 기존 첨부파일 삭제용 Group |
| ↳ `FILE_ID` | C | 100 |  | 파일ID | (2021/06/29) 추가 |
| `REC2` | R |  |  | 그룹2 | (2021/06/29) 추가 <br> 첨부파일 추가용 Group |
| ↳ `FILE_NM` | C | 500 |  | 파일명 | (2021/06/29) 추가 |
| ↳ `FILE_DATA` | B | 4000 |  | 파일데이터 | (2021/06/29) 추가 |

### Output — 0 fields

_No output payload beyond the common `RSLT_CD` / `RSLT_MSG` envelope._

### Process rules (verbatim from the sheet)

Input section:

```text
1. R006의 PC_MOD_YN 값이 'Y'인 DOC_ID는 수정, 삭제 불가능.
2. (12/22 추가) R006 API의 ADV_PAYM_SEQ가 Null이면 C004의 ADV_PAYM_SEQ도 반드시 Null이어야 하며, R006의 ADV_PAYM_SEQ가 Null이 아니면, C004의 ADV_PAYM_SEQ도 반드시 Null이 아니어야 한다.

(2021/02/05 추가)
3. BUSA_DOC_RCV_PAY_HIS 테이블을 조회하여(BIZ_KEY, DOC_ID 조회조건) 결과가 있을 경우 Block.
Error Message는 '지급확인이 완료된 Expense는 수정할 수 없습니다.'
4. BUSA_DOC_MSTR 테이블을 조회하여(BIZ_KEY, DOC_ID 조회조건)  APPR_SEQ_NO 컬럼값이 있을 경우 Block.
Error Message는 '지출결의서가 발행된 Expense는 수정할 수 없습니다.'
5. ADV_PAYM_TRNS_SEQ가 Null이 아닌 경우, ADV_PAYM_DOC_DETL 테이블을 조회하여(BIZ_KEY, ADV_PAYM_TRNS_SEQ 조회조건) 조회 결과가 있을 경우 Block.
Error Message는 '전도금정산서가 발행된 Expense는 수정할 수 없습니다.'

CORR_TYPE가 'U'이면 테이블 UPDATE,
'D'이면 DELETE.
(12/22 추가) ADV_PAYM_SEQ 필드값이 없으면 1번 테이블 Update(혹은 Delete. 2번 테이블에는 Trigger에 의해 Update 혹은 Delete된 데이터 자동 Insert.), 필드값이 있으면 (2021/02/05 수정) 5, 1, 2(Trigger)번 테이블 Update(혹은 Delete). (2021/06/08) 추가
- 6번 테이블 추가 INSERT 데이터.
FILE_ID : (Sequence)
ORG_FILE_NM, SAVE_FILE_NM, FILE_SIZE : 4번 테이블에서 가져옴.

- 추가 WHERE 조건
BIZ_KEY, USER_ID : 공통부

(2021/06/29) 추가
REC1에 값이 있을 경우 6번 테이블 데이터 삭제.

(2021/06/29) 추가
REC2에 값이 있을 경우 6번 테이블에 데이터 추가 적재.
```

---

<a id="wabooks_mrec_c005"></a>

## `WABOOKS_MREC_C005` — Submit a receipt

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_C005` |
| Sheet name (source) | `WABOOKS_MREC_C005` |
| Sheet title (원문) | 영수증제출 |
| Business area (업무) | 영수증관리API |
| Screen (source index) | — |
| Legacy web reference | — |
| Legacy tables (테이블) | 1. BUSA_DOC_MSTR <br> 2. BUSA_DOC_MSTR_HIS |
| Called by | iOS ✓ · Android — |
| Refactor endpoint | — (none) |
| Coverage | **NOT USED** |
| Last spec revision | — |

**Delta.** The workbook marks this service deleted and unused ("(04/14) 삭제. 사용하지 않음") - do not rebuild it.

### Input — 1 field

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `DOC_ID` | C | 50 | Y | 문서ID | 1, 2번 테이블 WHERE 조건 |

### Output — 0 fields

_No output payload beyond the common `RSLT_CD` / `RSLT_MSG` envelope._

### Process rules (verbatim from the sheet)

Input section:

```text
1번 테이블 PROV_STTS를 'OP'로 UPDATE.

※ 추가 Where 조건
BIZ_KEY, USER_ID : (공통부)
```

---

<a id="wabooks_mrec_r007"></a>

## `WABOOKS_MREC_R007` — Withdrawal history for expense matching

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_R007` |
| Sheet name (source) | `WABOOKS_MREC_R007` |
| Sheet title (원문) | Expense 출금내역 조회(API) |
| Business area (업무) | 영수증관리API |
| Screen (source index) | — |
| Legacy web reference | — |
| Legacy tables (테이블) | — |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | `GET /api/v1/banking/accounts/{acctSeqNo}/transactions` |
| Coverage | **PARTIAL** |
| Last spec revision | — |

**Delta.** The app searches by amount + currency + date range across accounts, not per account, so a match-candidate style query is needed.

### Input — 4 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `TRSC_AMT` | N | 20.6 | Y | 거래금액 | Expense 등록/수정 화면에서 입력한 Expense의 금액. <br> ACCT_TRNS_HSTR >> TRSC_AMT |
| `CCY_CD` | C | 3 | Y | 통화코드 | Expense 등록/수정 화면에서 선택한 Expense의 통화코드. <br> ACCT_TRNS_HSTR >> CCY_CD |
| `FROM_DT` | C | 8 | Y | 검색일자 From | ACCT_TRNS_HSTR >> TRSC_DT BETWEEN FROM |
| `TO_DT` | C | 8 | Y | 검색일자 To | ACCT_TRNS_HSTR >> TRSC_DT BETWEEN TO |

### Output — 10 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `REC` | R |  |  | 목록 |  |
| ↳ `BANK_CD` | C | 20 |  | 은행코드 | ACCT_TRNS_HSTR table |
| ↳ `BANK_NM` | C | 100 |  | 은행명 | (10/27) 추가 |
| ↳ `ACCT_NO` | C | 100 |  | 계좌번호 | ACCT_TRNS_HSTR table <br> 화면에는 뒷 4자리만 출력. |
| ↳ `TRSC_DT` | C | 8 |  | 거래일자 | ACCT_TRNS_HSTR table |
| ↳ `ACCT_SEQ_NO` | C | 20 |  | 계좌일련번호 | ACCT_TRNS_HSTR table |
| ↳ `CCY_CD` | C | 3 |  | 통화코드 | ACCT_TRNS_HSTR table |
| ↳ `TRSC_AMT` | N | 20.6 |  | 거래금액 | ACCT_TRNS_HSTR table |
| ↳ `RMRK` | C | 50 |  | 적요 | ACCT_TRNS_HSTR table |
| ↳ `MEMO` | C | 200 |  | 메모 | ACCT_TRNS_HSTR table |

### Process rules (verbatim from the sheet)

Input section:

```text
prch_0005_003_r001 과 프로세스 동일
```

---

<a id="wabooks_mrec_r060"></a>

## `WABOOKS_MREC_R060` — E-invoice list for attaching to a receipt

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_R060` |
| Sheet name (source) | `WABOOKS_MREC_R060` |
| Sheet title (원문) | e-invoice 목록조회(API) |
| Business area (업무) | 영수증관리API |
| Screen (source index) | — |
| Legacy web reference | — |
| Legacy tables (테이블) | 1. BUSA_DOC_MSTR |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | `GET /api/v1/payment/card-transactions/links/e-invoices` |
| Coverage | **PARTIAL** |
| Last spec revision | — |

**Delta.** Field-level verified: `LinkableEInvoiceRow` covers every field except `TRCO_CMENM` (contact English name). Same `DOC_TYPE='B' + HAND_REG_YN='N'` filter plus the already-linked exclusion.

### Input — 4 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `TRCO_TAX_ID` | C | 40 |  | 거래처세금ID | 현재 사용자가 일반 사용자인 경우 필수. |
| `TOTL_AMT` | N | 20.6 |  | 총금액 | 현재 사용자가 일반 사용자인 경우 필수. |
| `FROM_DT` | C | 8 |  | 검색일자 From | ISSU_DT BETWEEN FROM <br> (v2023.1.0.8 Required 제거) |
| `TO_DT` | C | 8 |  | 검색일자 To | ISSU_DT BETWEEN TO <br> (v2023.1.0.8 Required 제거) |

### Output — 8 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `REC` | R |  |  | 목록 |  |
| ↳ `DOC_ID` | C | 50 |  | 문서ID |  |
| ↳ `TRCO_CMNM` | C | 200 |  | 거래처회사명 |  |
| ↳ `TRCO_CMENM` | C | 200 |  | 거래처회사영문명 |  |
| ↳ `TRCO_TAX_ID` | C | 40 |  | 거래처세금ID |  |
| ↳ `ISSU_DT` | C | 8 |  | 발행일자 |  |
| ↳ `HAND_DOC_ID` | C | 50 |  | 수기문서번호 | Hoa don |
| ↳ `TOTL_AMT` | N | 20.6 |  | 총금액 |  |

### Process rules (verbatim from the sheet)

Input section:

```text
1번 테이블 SELECT.

※ 추가 Where 조건
DOC_TYPE : 'B'
HAND_REG_YN : 'N'
DOC_ID가 BUSA_DOC_MSTR >> EINV_CON_DOC_ID 컬럼에 없는 Row. --> not exists ( select 1 from busa_doc_mstr bdm2 where bdm2.biz_key = bdm1.biz_key and bdm2.einv_con_doc_id = bdm1.doc_id )
```

---

<a id="wabooks_mrec_u003"></a>

## `WABOOKS_MREC_U003` — Link an e-invoice to a receipt

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_U003` |
| Sheet name (source) | `WABOOKS_MREC_U003` |
| Sheet title (원문) | 영수증별 e-invoice 연결(API) |
| Business area (업무) | 영수증관리API |
| Screen (source index) | — |
| Legacy web reference | — |
| Legacy tables (테이블) | 1. BUSA_DOC_MSTR |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | `POST /api/v1/payment/card-transactions/links` |
| Coverage | **PARTIAL** |
| Last spec revision | — |

**Delta.** The refactor links through the card transaction; the app writes `BUSA_DOC_MSTR.EINV_CON_DOC_ID` directly.

### Input — 2 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `DOC_ID` | C | 50 | Y | 문서ID | 테이블 WHERE 조건 |
| `EINV_CON_DOC_ID` | C | 50 | Y | e-invoice 연결문서 ID |  |

### Output — 0 fields

_No output payload beyond the common `RSLT_CD` / `RSLT_MSG` envelope._

### Process rules (verbatim from the sheet)

Input section:

```text
1번 테이블 UPDATE
      v2024.1.40 added
EINV_CON_DOC_ID : EINV_CON_DOC_ID(INPUT)
DISP_DOC_ID     : (SELECT DISP_DOC_ID 
      FROM BUSA_DOC_MSTR 
      WHERE BIZ_KEY (SESSION) 
      AND  DOC_ID :  EINV_CON_DOC_ID(INPUT)
      AND HAND_REG_YN : 'N')
추가 WHERE 조건 : BIZ_KEY
```

---

