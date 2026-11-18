# WeCMS Mobile — Board / post / approval post — Field-Level Spec

Part of the [mobile-app migration mapping](README.md) set. 37 of the 76 legacy mobile services. Source: `WeCMS_Mobile_INOUT정의서(영수증관리API)_v2024.1.46.xlsx` (one sheet per service).

Legacy service ids (`WABOOKS_MREC_*`) appear here as **historical pointers** only, per the workspace identifier-naming rule. Field names are quoted in the legacy `UPPER_SNAKE` form used by the mobile gateway; any refactor endpoint that serves them returns lowercase `snake_case` JSON through the global Jackson strategy (see [docs/README.md](../../../README.md) §field-name casing).

---

<a id="wabooks_mrec_bltb_0001_r001"></a>

## `WABOOKS_MREC_BLTB_0001_R001` — Board list with favourites

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_BLTB_0001_R001` |
| Sheet name (source) | `WABOOKS_MREC_BLTB_0001_R001` |
| Sheet title (원문) | Inquiry Boards |
| Business area (업무) | List Board and Favorite Board |
| Screen (source index) | Board |
| Legacy web reference | `comm_0192_005_r001` |
| Legacy tables (테이블) | 1. BLTB_INFM <br> 2. BLTB_FAV_INFM <br> 3. USER_BLTB_CONN_INFM <br> 4. BLTB_POST_INFM <br> 5. BLTB_USER_NEW_POST_INFM |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | — (none) |
| Coverage | **MISSING** |
| Last spec revision | Updated v2024.1.22 |

**Delta.** The whole `BLTB_*` bulletin-board module is unrefactored.

### Input — 0 fields

_No input fields (the common envelope carries `USER_ID` / session)._

### Output — 17 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `REC` |  |  |  | Record | Favorite Boards list <br> Select from Table 1 join with Table 2 |
| ↳ `TOT_CNT_NEW_POST` | N | 15 |  | Total count new post | Table 5 : Select count(*) <br> Where all PK and READ_YN = 'N' |
| ↳ `BLTB_CD` | N | 15 |  | Bulletin Board Code | Table 1 |
| ↳ `BLTB_NAME` | C | 50 |  | Bulletin Board Name | Table 1 |
| ↳ `BLTB_ICON` | C | 10 |  | Bulletin Board Icon | Table 1 |
| ↳ `CONT` | C | 200 |  | Bulletin board description | v2024.1.13 added >> Table 1 |
| ↳ `BLTB_USER_TYPE` | C | 1 |  | Bulletin Board User Type | Table 3 <br> M: Member, A: Admin |
| ↳ `DEL_YN` | C | 1 |  | Delete YN | v2024.1.22 added <br> Y: Allow to Delete, N: Not allow to delete <br> Table 4 : Select count(*) as CNT where biz_key, bltb_cd <br> if CNT > 0 >> DEL_YN: 'N' <br> else >> DEL_YN: 'Y' |
| `REC2` |  |  |  | Record | Normal Boards list <br> Select from Table 1 |
| ↳ `TOT_CNT_NEW_POST` | N | 15 |  | Total count new post | Table 5 : Select count(*) <br> Where all PK and READ_YN = 'N' |
| ↳ `BLTB_CD` | N | 15 |  | Bulletin Board Code | Table 1 |
| ↳ `BLTB_NAME` | C | 50 |  | Bulletin Board Name | Table 1 |
| ↳ `BLTB_ICON` | C | 10 |  | Bulletin Board Icon | Table 1 |
| ↳ `BLTB_USER_TYPE` | C | 1 |  | Bulletin Board User Type | Table 3 <br> M: Member, A: Admin |
| ↳ `CONT` | C | 200 |  | Bulletin board description | v2024.1.13 added >> Table 1 |
| ↳ `FAV_YN` | C | 1 |  | Favorite Y/N | Y: Favorite Board, N: Normal Board |
| ↳ `DEL_YN` | C | 1 |  | Delete YN | v2024.1.22 added <br> Y: Allow to Delete, N: Not allow to delete <br> Table 4 : Select count(*) as CNT where biz_key, bltb_cd <br> if CNT > 0 >> DEL_YN: 'N' <br> else >> DEL_YN: 'Y' |

### Process rules (verbatim from the sheet)

Input section:

```text
*List publish board and private board(by USER_ID)

Where
BIZ_KEY: (Session)
reg_id in ('SYSTEM',USER_ID(Session))
*User must be on list board
Table 3: exists (select 1 FROM USER_BLTB_CONN_INFM UBCI WHERE 1=1 AND UBCI.BIZ_KEY = BIZ_KEY(Table 1) AND UBCI.BLTB_CD = BLTB_CD(Table 1)  AND UBCI.USER_ID=user_id (session) )
```

---

<a id="wabooks_mrec_bltb_0001_u001"></a>

## `WABOOKS_MREC_BLTB_0001_U001` — Toggle a favourite board

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_BLTB_0001_U001` |
| Sheet name (source) | `WABOOKS_MREC_BLTB_0001_U001` |
| Sheet title (원문) | Favorite Update |
| Business area (업무) | Update Favorite Board |
| Screen (source index) | Board |
| Legacy web reference | `comm_0192_005_u001` |
| Legacy tables (테이블) | 1. BLTB_INFM <br> 2. BLTB_FAV_INFM |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | — (none) |
| Coverage | **MISSING** |
| Last spec revision | — |

### Input — 1 field

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `BLTB_CD` | N | 15 | Y | Bulletin board cd |  |

### Output — 0 fields

_No output payload beyond the common `RSLT_CD` / `RSLT_MSG` envelope._

### Process rules (verbatim from the sheet)

Input section:

```text
1. Validate BLTB_CD exist on Table 1
Table 1: Select
Where
BIZ_KEY: (Session)
BLTB_CD

if BLTB_CD is not exist>> throws an error "BLTB_CD Not Found"

2. Table 2: Select 
BIZ_KEY: (Session)
USER_ID: (Session)
BLTB_CD

if Num 2 return null >> Table 2: Insert 
else >> Table 2: Delete
```

---

<a id="wabooks_mrec_bltb_0001_c001"></a>

## `WABOOKS_MREC_BLTB_0001_C001` — Create a board

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_BLTB_0001_C001` |
| Sheet name (source) | `WABOOKS_MREC_BLTB_0001_C001` |
| Sheet title (원문) | Create a new Board |
| Business area (업무) | Create New Board |
| Screen (source index) | Board> New Board |
| Legacy web reference | `comm_0192_002_c001` |
| Legacy tables (테이블) | 1. BLTB_INFM <br> 2. USER_BLTB_CONN_INFM |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | — (none) |
| Coverage | **MISSING** |
| Last spec revision | — |

### Input — 2 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `BLTB_NAME` | C | 50 | Y | Bulletin board name |  |
| `CONT` | C | 200 |  | Bulletin board description |  |

### Output — 1 field

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `BLTB_CD` | N | 15 |  | Bulletin board cd | v2024.1.6 add |

### Process rules (verbatim from the sheet)

Input section:

```text
Table 1: Insert
BIZ_KEY: (Session)

Table 2: Insert
BIZ_KEY: (Session)
USER_ID: (Session)
BLTB_CD: (Table 1)
BLTB_USER_TYPE: "A" (Default is Admin)
```

---

<a id="wabooks_mrec_bltb_0002_r001"></a>

## `WABOOKS_MREC_BLTB_0002_R001` — Company-wide post + approval list

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_BLTB_0002_R001` |
| Sheet name (source) | `WABOOKS_MREC_BLTB_0002_R001` |
| Sheet title (원문) | Inquiry Articles & Approval on Company |
| Business area (업무) | List Post(All, Draft, Article, Approval) & Board> Home > Search |
| Screen (source index) | Board> Home |
| Legacy web reference | `bltb_0001_001_r001` |
| Legacy tables (테이블) | 1. BLTB_POST_INFM <br> 2. USER_BLTB_CONN_INFM <br> 3. BLTB_NOTI_CONT_INFM <br> 4. BLTB_USER_NEW_POST_INFM <br> 5. BLTB_DRAFT_POST_MSTR <br> 6. EXPT_RESOL_MSTR <br> 7. BLTB_COMNT_INFM <br> 8. USER_INFM <br> 9. EXPT_RESOL_APPRVR_INFM v2024.1.34 added |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | `GET /api/v1/payment/approval-box` |
| Coverage | **PARTIAL** |
| Last spec revision | Updated v2024.1.32 |

**Delta.** Covers the approval branch (`EXPT_RESOL_MSTR`) only; article, draft, pin and comment data have no counterpart.

### Input — 6 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `POST_TYPE_CD` | C | 2 | Y | Post type code | "": All, "01":Article, "02": Approval, "03": Draft |
| `SRCH_WD` | C | 100 |  | Search word | Like POST_TITLE or DOC_NM or TITL or DOC_NO |
| `PAGE_NO` | N | 15 | Y | 페이지번호 | DF: 1 |
| `PAGE_PER_CNT` | N | 15 | Y | 페이지별건수 | DF: 10 |
| `FROM_DT` | C | 8 |  | 검색일자 From | v2024.1.10 added >> Table 1 >> REG_DTM BETWEEN FROM |
| `TO_DT` | C | 8 |  | 검색일자 To | v2024.1.10 added >> Table 1 >> REG_DTM BETWEEN TO |

### Output — 59 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `TOT_CNT` | N | 15 |  | Total Count | All >> Select count (Table 1 join table 2) + count(Table 5 join table 2) <br> Wherer BIZ_KEY: (Session),POST_STTS: 'PL' |
| `TOT_CNT_ART` | N | 15 |  | Total Count Article | Article >> Select count (Table 1 join table 2) where BIZ_KEY: (Session),POST_TYPE_CD = '01' & POST_STTS: 'PL' |
| `TOT_CNT_APPR` | N | 15 |  | TTotal Count Approval | Approval >> Select count (Table 1 join table 2) where BIZ_KEY: (Session),POST_TYPE_CD = '02' & POST_STTS: 'PL' |
| `TOT_CNT_DRAFT` | N | 15 |  | Total Count Draft | Draft >> Select count (Table 5 join table 2) where BIZ_KEY: (Session),POST_STTS: 'PL' |
| `REC` | R |  |  | Record | Normal List |
| ↳ `BLTB_CD` | C | 15 |  | Bulletin board code |  |
| ↳ `POST_SEQ_NO` | N | 15 |  | Post sequence number |  |
| ↳ `POST_TITLE` | C | 200 |  | Post title |  |
| ↳ `POST_TYPE_CD` | C | 2 |  | Post type code | 01' : Article , '02':Approval |
| ↳ `POST_STTS` | C | 2 |  | Post status | PL : Public , PV:Private <br> if POST_STTS = PV >> <br> table 1 join table 2 |
| ↳ `POST_APPR_DV` | C | 2 |  | Post Approval Division | 00 :‘Simple approval’, 10 : 'Expense approval' |
| ↳ `REG_NM` | C | 100 |  | Register Name | Table 8: USER_NM where USER_ID: REG_ID |
| ↳ `REG_ID` | C | 15 |  | 등록자ID |  |
| ↳ `REG_DTM` | C | 14 |  | 등록일시 | v2024.1.8 add <br> For Article>> Post Date and Request Date <br> For Approval>> Post Date |
| ↳ `REG_IMG_URL` | C | 200 |  | Register Image Url | Table 8: USER_IMG is not null where USER_ID: REG_ID >> {Base URL}/szon_0012_003_r002.act?USER_ID={REG_ID} |
| ↳ `USER_IMG` | C | 500 |  | User Image | v2024.1.11 added >> Table 8 |
| ↳ `PIN_POST_TYPE` | C | 2 |  | Pin post type |  |
| ↳ `PIN_POST_DUE_DT` | C | 8 |  | Pin post due date | v2024.1.8 add <br> For Article>> Schedule Date |
| ↳ `POST_CONT` | C | 2000 |  | Post content |  |
| ↳ `TOT_CMT` | N | 15 |  | Total Comment | Table 7 |
| ↳ `APPR_SEQ_NO` | C | 15 |  | 지출결의번호 | Table 6 |
| ↳ `PAY_END_YN` | C | 1 |  | Approval End Y/N | Table 6: Y: Approved, N: Pending |
| ↳ `PAY_YN` | C | 1 |  | Pay Y/N | Table 6: Y: Paid, N: Not Paid, P: Partially Paid |
| ↳ `READ_YN` | C | 1 |  | Read Y/N | Table 4: N: Show Icon 'N' |
| ↳ `DEL_YN` | C | 1 |  | Delete Y/N | Table 2: Admin and Post owner allow to delete |
| ↳ `APPR_TYPE_CD` | C | 2 |  | Approve type Cd | v2024.1.34 removed <br> '00' : 'Pending' <br> '01' : 'Approve' <br> '02' : 'reject' <br> '03' : 'Partially Approve'" |
| ↳ `APPRVR_TOT_CNT` | N | 15 |  | Approver count | v2024.1.34 removed v2024.1.37 restored <br> select count (*) as APPRVR_TOT_CNT <br> from EXPT_RESOL_APPRVR_INFM <br> where BIZ_KEY('session') <br> and APPR_SEQ_NO : OUTPUT(REC > APPR_SEQ_NO) |
| ↳ `APPRVR_APPR_TOT_CNT` | N | 15 |  | Approver Approve count | v2024.1.34 removed v2024.1.37 restored <br> select count (*) as APPRVR_TOT_CNT <br> from EXPT_RESOL_APPRVR_INFM <br> where BIZ_KEY('session') <br> and APPR_SEQ_NO : OUTPUT(REC > APPR_SEQ_NO) <br> and apprvr_stts = 'A' |
| ↳ `APPR_STTS` | C | 10 |  | Approval Status | **see note 1 below** |
| ↳ `PAY_REQ_DATE` | C | 8 |  | 지급요청일 | v2024.1.8 add <br> Table 6 <br> For Approval>> Request Date |
| ↳ `PAY_DATE` | C | 8 |  | 지급예정일 | v2024.1.8 add <br> Table 6 <br> For Approval>> Schedule Date |
| ↳ `TOT_PAY_LOC_AMT` | N | 20.6 |  | 결의총KHR금액 | v2024.1.7 add, Table 6 <br> Ref: bltb_0001_003_r001 <br> v2024.1.32 <br> KHR or VND |
| ↳ `TOT_PAY_USD_AMT` | N | 20.6 |  | 결의총USD금액 | v2024.1.7 add, Table 6 <br> Ref: bltb_0001_003_r001 <br> USD |
| ↳ `CMTNT_REG_NM` | C | 100 |  | Register Name | v2024.1.7 add <br> Table 8: Select USER_NM where USER_ID:(REG_ID of Table 7) |
| ↳ `COMNT_CONT` | C | 1000 |  | Comment content | v2024.1.7 add <br> Table 7: <br> *Mention data store on DB like below: <br> @\[{USER_NM})\]({USER_ID}) |
| ↳ `COMNT_SEQ_NO` | N | 15 |  | comment sequence number | v2024.1.7 add <br> Table 7: <br> *Mention data store on DB like below: <br> @\[{USER_NM})\]({USER_ID}) |
| ↳ `COMNT_DV_CD` | C | 1 |  | Comment classification code | v2024.1.7 add <br> Table 7: <br> C: 'Comment', R : 'Remark' |
| ↳ `COMNT_TASK_CD` | C | 4 |  | Comment task code | v2024.1.7 add <br> Table 7: <br> If COMNT_DV_CD = 'C' <br> C001(‘Comment’) <br> else if COMNT_DV_CD = 'R' <br> R001 ('Pinned') <br> R002 ('Approval' -> 'Rejected') <br> R003 ('Approval' → ‘Complete’) <br> R004 ('Approval' -> 'Approved') |
| ↳ `COMNT_TASK_CD` |  |  |  |  |  |
| ↳ `DRAFT_POST_SEQ_NO` | N | 15 |  | Dratf post sequence number | Updated v2024.1.17 |
| `REC2` | R |  |  | Record 2 | v2024.1.9 add <br> Plinned List |
| ↳ `BLTB_CD` | C | 15 |  | Bulletin board code |  |
| ↳ `POST_SEQ_NO` | N | 15 |  | Post sequence number |  |
| ↳ `POST_TITLE` | C | 200 |  | Post title |  |
| ↳ `POST_TYPE_CD` | C | 2 |  | Post type code | 01' : Article , '02':Approval |
| ↳ `POST_STTS` | C | 2 |  | Post status | PL : Public , PV:Private <br> if POST_STTS = PV >> <br> table 1 join table 2 |
| ↳ `POST_APPR_DV` | C | 2 |  | Post Approval Division | 00 :‘Simple approval’, 10 : 'Expense approval' |
| ↳ `REG_NM` | C | 100 |  | Register Name | Table 8: USER_NM where USER_ID: REG_ID |
| ↳ `REG_ID` | C | 15 |  | 등록자ID |  |
| ↳ `REG_DTM` | C | 14 |  | 등록일시 | v2024.1.8 add <br> For Article>> Post Date and Request Date <br> For Approval>> Post Date |
| ↳ `REG_IMG_URL` | C | 200 |  | Register Image Url | Table 8: USER_IMG is not null where USER_ID: REG_ID >> {Base URL}/szon_0012_003_r002.act?USER_ID={REG_ID} |
| ↳ `USER_IMG` | C | 500 |  | User Image | v2024.1.11 added >> Table 8 |
| ↳ `PIN_POST_TYPE` | C | 2 |  | Pin post type |  |
| ↳ `PIN_POST_DUE_DT` | C | 8 |  | Pin post due date | v2024.1.8 add <br> For Article>> Schedule Date |
| ↳ `POST_CONT` | C | 2000 |  | Post content |  |
| ↳ `TOT_CMT` | N | 15 |  | Total Comment | Table 7 |
| ↳ `READ_YN` | C | 1 |  | Read Y/N | Table 4: N: Show Icon 'N' |
| ↳ `DEL_YN` | C | 1 |  | Delete Y/N | Table 2: Admin and Post owner allow to delete |
| ↳ `DRAFT_POST_SEQ_NO` | N | 15 |  | Dratf post sequence number | Updated v2024.1.17 |

**Note 1 — `APPR_STTS`** (verbatim from the sheet):

```text
v2024.1.34 added
 if  APPR_TYPE_CD = '00' then 'P'
 else if  APPR_TYPE_CD == '01' then 'C'
 else if  APPR_TYPE_CD == '02' then  'R'
 else if  APPR_TYPE_CD == '03' then 
 
  select  
    APPRVR_APPR_TOT_CNT  || '/'|| APPRVR_TOT_CNT as  APPR_STTS
   from (
    select  
     ,count (*) as  APPRVR_TOT_CNT
     ,sum (case when apprvr_stts ='A' then 1 else 0 end)  APPRVR_APPR_TOT_CNT
    from  expt_resol_apprvr_infm erai 
      where  BIZ_KEY('session')  
      and APPR_SEQ_NO : OUTPUT(REC > APPR_SEQ_NO)  
   ) sub
v2024.1.37 added
if (user is Admin or  USER_ID ('session') ==  OUTPUT(REC > REG_ID )) 
          and not exists (
                select 1 
                  from 
                expt_resol_apprvr_infm 
                    where  biz_key ('session')
                     and apprvr_id  : user_id ('session')
                     and apprvr_stts  = 'P'
                    and apprvr_id  : user_id ('session')|
            )   v2024.1.38 added then 
 if  APPR_TYPE_CD = '00' then 'P'
 else if  APPR_TYPE_CD == '01' then 'C'
 else if  APPR_TYPE_CD == '02' then  'R'
 else if  APPR_TYPE_CD == '03' then  'I'

else 
  select   
      APPR_STTS 
  from expt_resol_apprvr_infm  
  where 
    biz_key ('session')
   and apprvr_id  : user_id ('session')|
   and  APPR_SEQ_NO  : OUTPUT( APPR_SEQ_NO)   
end
```

### Process rules (verbatim from the sheet)

Input section:

```text
Select all posts from user's board and Public Article
Where 
BIZ_KEY: (Session)
Table 1 join table 2: 
Public Article: (POST_TYPE_CD = '01' & POST_STTS: 'PL')

Table 6: 
DEL_YN <> 'Y'

order by reg_dtm DESC (default)
if REG_PIN_POST_DATE != null >>add REG_PIN_POST_DATE DESC

*Note: v2024.1.31 added
if INPUT.SRCH_WD != null >> response All data on REC (Pin and Normal)
else >> response REC (Normal) and REC2 (Pin) v2024.1.34 added
** Approval Display Rule (only for  Approval that has been reqursted) 
 - Admin       : display  all the approvals in the company.
 - Requestor   : display only the approvals that have been requested by the specific requestor.
 - Normal user   : display only the Approvals where the user is listed as an Approver ('EXPT_RESOL_APPRVR_INFM')  and the status of  Approver  is  in
       'A' (approved)
      , 'R' (rejected)
      , or 'P' (pending)
```

Output section:

```text
v2024.1.7 add
Inquiry the latest comment limit 1 order by reg_dtm
```

---

<a id="wabooks_mrec_bltb_0002_r004"></a>

## `WABOOKS_MREC_BLTB_0002_R004` — Per-user post + approval list

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_BLTB_0002_R004` |
| Sheet name (source) | `WABOOKS_MREC_BLTB_0002_R004` |
| Sheet title (원문) | Inquiry Articles & Approval on User |
| Business area (업무) | List Post(All, Draft, Article, Approval) & Board> Home > Search |
| Screen (source index) | Board> My Posts |
| Legacy web reference | `bltb_0002_001_r001` |
| Legacy tables (테이블) | 1. BLTB_POST_INFM <br> 2. USER_BLTB_CONN_INFM <br> 3. BLTB_NOTI_CONT_INFM <br> 4. BLTB_USER_NEW_POST_INFM <br> 5. BLTB_DRAFT_POST_MSTR <br> 6. EXPT_RESOL_MSTR <br> 7. BLTB_COMNT_INFM <br> 8. USER_INFM |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | `GET /api/v1/payment/approval-box` |
| Coverage | **PARTIAL** |
| Last spec revision | Updated v2024.1.32 |

**Delta.** Same delta as BLTB_0002_R001.

### Input — 6 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `POST_TYPE_CD` | C | 2 | Y | Post type code | "": All, "01":Article, "02": Approval, "03": Draft |
| `SRCH_WD` | C | 100 |  | Search word | Like POST_TITLE or DOC_NM or TITL or DOC_NO |
| `PAGE_NO` | N | 15 | Y | 페이지번호 | DF: 1 |
| `PAGE_PER_CNT` | N | 15 | Y | 페이지별건수 | DF: 10 |
| `FROM_DT` | C | 8 |  | 검색일자 From | v2024.1.10 added >> Table 1 >> REG_DTM BETWEEN FROM |
| `TO_DT` | C | 8 |  | 검색일자 To | v2024.1.10 added >> Table 1 >> REG_DTM BETWEEN TO |

### Output — 59 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `TOT_CNT` | N | 15 |  | Total Count | All >> Select count (Table 1 join table 2) + count(Table 5 join table 2) <br> Wherer BIZ_KEY: (Session), REG_ID: (UER_ID of Session) |
| `TOT_CNT_ART` | N | 15 |  | Total Count Article | Article >> Select count (Table 1 join table 2) where POST_TYPE_CD = '01',BIZ_KEY: (Session), REG_ID: (UER_ID of Session) |
| `TOT_CNT_APPR` | N | 15 |  | TTotal Count Approval | Approval >> Select count (Table 1 join table 2) where POST_TYPE_CD = '02', BIZ_KEY: (Session), REG_ID: (UER_ID of Session) |
| `TOT_CNT_DRAFT` | N | 15 |  | Total Count Draft | Draft >> Select count (Table 5 join table 2) where BIZ_KEY: (Session), REG_ID: (UER_ID of Session) |
| `REC` | R |  |  | Record |  |
| ↳ `BLTB_CD` | C | 15 |  | Bulletin board code |  |
| ↳ `POST_SEQ_NO` | N | 15 |  | Post sequence number |  |
| ↳ `POST_TITLE` | C | 200 |  | Post title |  |
| ↳ `POST_TYPE_CD` | C | 2 |  | Post type code | 01' : Article , '02':Approval |
| ↳ `POST_STTS` | C | 2 |  | Post status | PL : Public , PV:Private <br> if POST_STTS = PV >> <br> table 1 join table 2 |
| ↳ `POST_APPR_DV` | C | 2 |  | Post Approval Division | 00 :‘Simple approval’, 10 : 'Expense approval' |
| ↳ `REG_NM` | C | 100 |  | Register Name | Table 8: USER_NM where USER_ID: REG_ID |
| ↳ `REG_ID` | C | 15 |  | 등록자ID |  |
| ↳ `REG_DTM` | C | 2 |  | 등록일시 |  |
| ↳ `REG_IMG_URL` | C | 200 |  | Register Image Url | Table 8: USER_IMG is not null where USER_ID: REG_ID >> {Base URL}/szon_0012_003_r002.act?USER_ID={REG_ID} |
| ↳ `USER_IMG` | C | 500 |  | User Image | v2024.1.11 added >> Table 8 |
| ↳ `PIN_POST_TYPE` | C | 2 |  | Pin post type |  |
| ↳ `PIN_POST_DUE_DT` | C | 8 |  | Pin post due date |  |
| ↳ `POST_CONT` | C | 2000 |  | Post content |  |
| ↳ `TOT_CMT` | N | 15 |  | Total Comment | Table 7 |
| ↳ `APPR_SEQ_NO` | C | 15 |  | 지출결의번호 | Table 6 |
| ↳ `PAY_END_YN` | C | 1 |  | Approval End Y/N | Table 6: Y: Approved, N: Pending |
| ↳ `PAY_YN` | C | 1 |  | Pay Y/N | Table 6: Y: Paid, N: Not Paid, P: Partially Paid |
| ↳ `READ_YN` | C | 1 |  | Read Y/N | Table 4: N: Show Icon 'N' |
| ↳ `DEL_YN` | C | 1 |  | Delete Y/N | Table 2: Admin and Post owner allow to delete |
| ↳ `APPR_TYPE_CD` | C | 2 |  | Approve type Cd | v2024.1.34 removed <br> "00' : 'Pending' <br> '01' : 'Approve' <br> '02' : 'reject' <br> '03' : 'Partially Approve'""" |
| ↳ `APPRVR_TOT_CNT` | N | 15 |  | Approver count | v2024.1.34 removed v2024.1.37 restored <br> select count (*) as APPRVR_TOT_CNT <br> from EXPT_RESOL_APPRVR_INFM <br> where BIZ_KEY('session') <br> and APPR_SEQ_NO : OUTPUT(REC > APPR_SEQ_NO) |
| ↳ `APPRVR_APPR_TOT_CNT` | N | 15 |  | Approver Approve count | v2024.1.34 removed v2024.1.37 restored <br> select count (*) as APPRVR_TOT_CNT <br> from EXPT_RESOL_APPRVR_INFM <br> where BIZ_KEY('session') <br> and APPR_SEQ_NO : OUTPUT(REC > APPR_SEQ_NO) <br> and apprvr_stts = 'A' |
| ↳ `APPR_STTS` | C | 10 |  | Approval Status | **see note 1 below** |
| ↳ `DRAFT_POST_SEQ_NO` | N | 15 |  | Dratf post sequence number | Updated v2024.1.17 |
| ↳ `PAY_REQ_DATE` | C | 8 |  | 지급요청일 | Table 6 <br> For Approval>> Request Date |
| ↳ `PAY_DATE` | C | 8 |  | 지급예정일 | Table 6 <br> For Approval>> Schedule Date |
| ↳ `TOT_PAY_LOC_AMT` | N | 20.6 |  | Total Pay Local Amount | Table 6 <br> Ref: bltb_0001_003_r001 <br> v2024.1.32 <br> KHR or VND |
| ↳ `TOT_PAY_USD_AMT` | N | 20.6 |  | 결의총USD금액 | Table 6 <br> Ref: bltb_0001_003_r001 <br> USD |
| ↳ `CMTNT_REG_NM` | C | 100 |  | Register Name | Table 8: Select USER_NM where USER_ID:(REG_ID of Table 7) |
| ↳ `COMNT_CONT` | C | 1000 |  | Comment content | Table 7: <br> *Mention data store on DB like below: <br> @\[{USER_NM})\]({USER_ID}) |
| ↳ `COMNT_SEQ_NO` | N | 15 |  | comment sequence number | Table 7: <br> *Mention data store on DB like below: <br> @\[{USER_NM})\]({USER_ID}) |
| ↳ `COMNT_DV_CD` | C | 1 |  | Comment classification code | Table 7: <br> C: 'Comment', R : 'Remark' |
| ↳ `COMNT_TASK_CD` | C | 4 |  | Comment task code | Table 7: <br> If COMNT_DV_CD = 'C' <br> C001(‘Comment’) <br> else if COMNT_DV_CD = 'R' <br> R001 ('Pinned') <br> R002 ('Approval' -> 'Rejected') <br> R003 ('Approval' → ‘Complete’) <br> R004 ('Approval' -> 'Approved') |
| ↳ `COMNT_TASK_CD` |  |  |  |  |  |
| `REC2` | R |  |  | Record 2 | Plinned List |
| ↳ `BLTB_CD` | C | 15 |  | Bulletin board code |  |
| ↳ `POST_SEQ_NO` | N | 15 |  | Post sequence number |  |
| ↳ `POST_TITLE` | C | 200 |  | Post title |  |
| ↳ `POST_TYPE_CD` | C | 2 |  | Post type code | 01' : Article , '02':Approval |
| ↳ `POST_STTS` | C | 2 |  | Post status | PL : Public , PV:Private <br> if POST_STTS = PV >> <br> table 1 join table 2 |
| ↳ `POST_APPR_DV` | C | 2 |  | Post Approval Division | 00 :‘Simple approval’, 10 : 'Expense approval' |
| ↳ `REG_NM` | C | 100 |  | Register Name | Table 8: USER_NM where USER_ID: REG_ID |
| ↳ `REG_ID` | C | 15 |  | 등록자ID |  |
| ↳ `REG_DTM` | C | 14 |  | 등록일시 | For Article>> Post Date and Request Date <br> For Approval>> Post Date |
| ↳ `REG_IMG_URL` | C | 200 |  | Register Image Url | Table 8: USER_IMG is not null where USER_ID: REG_ID >> {Base URL}/szon_0012_003_r002.act?USER_ID={REG_ID} |
| ↳ `USER_IMG` | C | 500 |  | User Image | Table 8 |
| ↳ `PIN_POST_TYPE` | C | 2 |  | Pin post type |  |
| ↳ `PIN_POST_DUE_DT` | C | 8 |  | Pin post due date | For Article>> Schedule Date |
| ↳ `POST_CONT` | C | 2000 |  | Post content |  |
| ↳ `TOT_CMT` | N | 15 |  | Total Comment | Table 7 |
| ↳ `READ_YN` | C | 1 |  | Read Y/N | Table 4: N: Show Icon 'N' |
| ↳ `DEL_YN` | C | 1 |  | Delete Y/N | Table 2: Admin and Post owner allow to delete |
| ↳ `DRAFT_POST_SEQ_NO` | N | 15 |  | Dratf post sequence number |  |

**Note 1 — `APPR_STTS`** (verbatim from the sheet):

```text
v2024.1.34 added
 if  APPR_TYPE_CD = '00' then 'P'
 else if  APPR_TYPE_CD == '01' then 'C'
 else if  APPR_TYPE_CD == '02' then  'R'
 else if  APPR_TYPE_CD == '03' then 
 
  select  
    APPRVR_APPR_TOT_CNT  || '/'|| APPRVR_TOT_CNT as  APPR_STTS
   from (
    select  
     ,count (*) as  APPRVR_TOT_CNT
     ,sum (case when apprvr_stts ='A' then 1 else 0 end)  APPRVR_APPR_TOT_CNT
    from  expt_resol_apprvr_infm erai 
      where  BIZ_KEY('session')  
      and APPR_SEQ_NO : OUTPUT(REC > APPR_SEQ_NO)  
   ) sub
v2024.1.37 added
if (user is Admin or  USER_ID ('session') ==  OUTPUT(REC > REG_ID )) 
          and not  exists (
                select 1 
                  from 
                expt_resol_apprvr_infm 
                    where  biz_key ('session')
                     and apprvr_id  : user_id ('session')
                     and apprvr_stts  = 'P'
                    and apprvr_id  : user_id ('session')|
            )   v2024.1.38 added then 
 if  APPR_TYPE_CD = '00' then 'P'
 else if  APPR_TYPE_CD == '01' then 'C'
 else if  APPR_TYPE_CD == '02' then  'R'
 else if  APPR_TYPE_CD == '03' then  'I'

else 
  select   
      APPR_STTS 
  from expt_resol_apprvr_infm  
  where 
    biz_key ('session')
   and apprvr_id  : user_id ('session')|
   and  APPR_SEQ_NO  : OUTPUT( APPR_SEQ_NO)   
end
```

### Process rules (verbatim from the sheet)

Input section:

```text
Where 
BIZ_KEY: (Session)
Table 1:
REG_ID: (UER_ID of Session)

Table 6: 
DEL_YN <> 'Y'

order by reg_dtm DESC (default)
if REG_PIN_POST_DATE != null >>add REG_PIN_POST_DATE DESC
```

---

<a id="wabooks_mrec_bltb_0002_r005"></a>

## `WABOOKS_MREC_BLTB_0002_R005` — Approval list

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_BLTB_0002_R005` |
| Sheet name (source) | `WABOOKS_MREC_BLTB_0002_R005` |
| Sheet title (원문) | Inquiry Approval |
| Business area (업무) | List Post(All, Draft) & Board> Home > Search |
| Screen (source index) | Board> Approval |
| Legacy web reference | `bltb_0003_001_r001` |
| Legacy tables (테이블) | 1. BLTB_POST_INFM <br> 2. USER_BLTB_CONN_INFM <br> 3. BLTB_NOTI_CONT_INFM <br> 4. BLTB_USER_NEW_POST_INFM <br> 5. BLTB_DRAFT_POST_MSTR <br> 6. EXPT_RESOL_MSTR <br> 7. BLTB_COMNT_INFM <br> 8. USER_INFM |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | `GET /api/v1/payment/approval-box` |
| Coverage | **PARTIAL** |
| Last spec revision | Updated v2024.1.32 |

**Delta.** The admin / requestor / approver visibility rule in the sheet needs to be re-verified against the refactor query.

### Input — 6 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `POST_APPR_TYPE_CD` | C | 2 | Y | Post Approval type code | **see note 1 below** |
| `SRCH_WD` | C | 100 |  | Search word | Like POST_TITLE or DOC_NM or TITL or DOC_NO |
| `PAGE_NO` | N | 15 | Y | 페이지번호 | DF: 1 |
| `PAGE_PER_CNT` | N | 15 | Y | 페이지별건수 | DF: 10 |
| `FROM_DT` | C | 8 |  | 검색일자 From | v2024.1.10 added >> Table 1 >> REG_DTM BETWEEN FROM |
| `TO_DT` | C | 8 |  | 검색일자 To | v2024.1.10 added >> Table 1 >> REG_DTM BETWEEN TO |

**Note 1 — `POST_APPR_TYPE_CD`** (verbatim from the sheet):

```text
v2024.1.12 added
Approval Filter:
"": All >> POST_TYPE_CD = "02",
"03": Draft >>  POST_TYPE_CD = "02" and , draft_post_seq_no <> ''
"04":Pending >> POST_TYPE_CD = "02" and appr_type_cd = '00', 
"05":Progress(Partially Approve) >> POST_TYPE_CD = "02" and appr_type_cd = '03',
"06":Reject >> POST_TYPE_CD = "02" and appr_type_cd = '02',
"07":Approved >> POST_TYPE_CD = "02" and appr_type_cd = '01'
```

### Output — 61 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `TOT_CNT_APPR` | N | 15 |  | TTotal Count Approval | All = Approval >> Select count (Table 1 join table 2) where POST_TYPE_CD = '02', BIZ_KEY: (Session) |
| `TOT_CNT_DRAFT` | N | 15 |  | Total Count Draft | Draft >> Select count (Table 5 join table 2) where BIZ_KEY: (Session) |
| `TOT_CNT_APPR_PD` | N | 15 |  | TTotal Count Approval | v2024.1.12 added <br> All = Approval >> Select count (Table 1 join table 2) where POST_TYPE_CD = '02', BIZ_KEY: (Session), appr_type_cd = '00' |
| `TOT_CNT_APPR_PP` | N | 15 |  | TTotal Count Approval | v2024.1.12 added <br> All = Approval >> Select count (Table 1 join table 2) where POST_TYPE_CD = '02', BIZ_KEY: (Session), appr_type_cd = '03' |
| `TOT_CNT_APPR_RJ` | N | 15 |  | TTotal Count Approval | v2024.1.12 added <br> All = Approval >> Select count (Table 1 join table 2) where POST_TYPE_CD = '02', BIZ_KEY: (Session), appr_type_cd = '02' |
| `TOT_CNT_APPR_AP` | N | 15 |  | TTotal Count Approval | v2024.1.12 added <br> All = Approval >> Select count (Table 1 join table 2) where POST_TYPE_CD = '02', BIZ_KEY: (Session), appr_type_cd = '01' |
| `REC` | R |  |  | Record |  |
| ↳ `BLTB_CD` | C | 15 |  | Bulletin board code |  |
| ↳ `POST_SEQ_NO` | N | 15 |  | Post sequence number |  |
| ↳ `POST_TITLE` | C | 200 |  | Post title |  |
| ↳ `POST_TYPE_CD` | C | 2 |  | Post type code | 01' : Article , '02':Approval |
| ↳ `POST_STTS` | C | 2 |  | Post status | PL : Public , PV:Private <br> if POST_STTS = PV >> <br> table 1 join table 2 |
| ↳ `POST_APPR_DV` | C | 2 |  | Post Approval Division | 00 :‘Simple approval’, 10 : 'Expense approval' |
| ↳ `REG_NM` | C | 100 |  | Register Name | Table 8: USER_NM where USER_ID: REG_ID |
| ↳ `REG_ID` | C | 15 |  | 등록자ID |  |
| ↳ `REG_DTM` | C | 2 |  | 등록일시 |  |
| ↳ `REG_IMG_URL` | C | 200 |  | Register Image Url | Table 8: USER_IMG is not null where USER_ID: REG_ID >> {Base URL}/szon_0012_003_r002.act?USER_ID={REG_ID} |
| ↳ `USER_IMG` | C | 500 |  | User Image | v2024.1.11 added >> Table 8 |
| ↳ `PIN_POST_TYPE` | C | 2 |  | Pin post type |  |
| ↳ `PIN_POST_DUE_DT` | C | 8 |  | Pin post due date |  |
| ↳ `POST_CONT` | C | 2000 |  | Post content |  |
| ↳ `TOT_CMT` | N | 15 |  | Total Comment | Table 7 |
| ↳ `APPR_SEQ_NO` | C | 15 |  | 지출결의번호 | Table 6 |
| ↳ `PAY_END_YN` | C | 1 |  | Approval End Y/N | Table 6: Y: Approved, N: Pending |
| ↳ `PAY_YN` | C | 1 |  | Pay Y/N | Table 6: Y: Paid, N: Not Paid, P: Partially Paid |
| ↳ `READ_YN` | C | 1 |  | Read Y/N | Table 4: N: Show Icon 'N' |
| ↳ `DEL_YN` | C | 1 |  | Delete Y/N | Table 2: Admin and Post owner allow to delete |
| ↳ `APPR_TYPE_CD` | C | 2 |  | Approve type Cd | '00' : 'Pending' v2024.1.34 removed <br> '01' : 'Approve' <br> '02' : 'reject' <br> '03' : 'Partially Approve'" |
| ↳ `APPRVR_TOT_CNT` | N | 15 |  | Approver count | v2024.1.34 removed v2024.1.37 restored <br> select count (*) as APPRVR_TOT_CNT <br> from EXPT_RESOL_APPRVR_INFM <br> where BIZ_KEY('session') <br> and APPR_SEQ_NO : OUTPUT(REC > APPR_SEQ_NO) |
| ↳ `APPRVR_APPR_TOT_CNT` | N | 15 |  | Approver Approve count | v2024.1.34 removed v2024.1.37 restored <br> select count (*) as APPRVR_TOT_CNT <br> from EXPT_RESOL_APPRVR_INFM <br> where BIZ_KEY('session') <br> and APPR_SEQ_NO : OUTPUT(REC > APPR_SEQ_NO) <br> and apprvr_stts = 'A' |
| ↳ `APPR_STTS` | C | 10 |  | Approval Status | **see note 1 below** |
| ↳ `DRAFT_POST_SEQ_NO` | N | 15 |  | Dratf post sequence number | Updated v2024.1.17 |
| ↳ `PAY_REQ_DATE` | C | 8 |  | 지급요청일 | Table 6 <br> For Approval>> Request Date |
| ↳ `PAY_DATE` | C | 8 |  | 지급예정일 | Table 6 <br> For Approval>> Schedule Date |
| ↳ `TOT_PAY_LOC_AMT` | N | 20.6 |  | Total Pay Local Amount | Table 6 <br> Ref: bltb_0001_003_r001 <br> v2024.1.32 <br> KHR or VND |
| ↳ `TOT_PAY_USD_AMT` | N | 20.6 |  | 결의총USD금액 | Table 6 <br> Ref: bltb_0001_003_r001 |
| ↳ `CMTNT_REG_NM` | C | 100 |  | Register Name | Table 8: Select USER_NM where USER_ID:(REG_ID of Table 7) |
| ↳ `COMNT_CONT` | C | 1000 |  | Comment content | Table 7: <br> *Mention data store on DB like below: <br> @\[{USER_NM})\]({USER_ID}) |
| ↳ `COMNT_SEQ_NO` | N | 15 |  | comment sequence number | Table 7: <br> *Mention data store on DB like below: <br> @\[{USER_NM})\]({USER_ID}) |
| ↳ `COMNT_DV_CD` | C | 1 |  | Comment classification code | Table 7: <br> C: 'Comment', R : 'Remark' |
| ↳ `COMNT_TASK_CD` | C | 4 |  | Comment task code | Table 7: <br> If COMNT_DV_CD = 'C' <br> C001(‘Comment’) <br> else if COMNT_DV_CD = 'R' <br> R001 ('Pinned') <br> R002 ('Approval' -> 'Rejected') <br> R003 ('Approval' → ‘Complete’) <br> R004 ('Approval' -> 'Approved') |
| ↳ `COMNT_TASK_CD` |  |  |  |  |  |
| `REC2` | R |  |  | Record 2 | Plinned List |
| ↳ `BLTB_CD` | C | 15 |  | Bulletin board code |  |
| ↳ `POST_SEQ_NO` | N | 15 |  | Post sequence number |  |
| ↳ `POST_TITLE` | C | 200 |  | Post title |  |
| ↳ `POST_TYPE_CD` | C | 2 |  | Post type code | 01' : Article , '02':Approval |
| ↳ `POST_STTS` | C | 2 |  | Post status | PL : Public , PV:Private <br> if POST_STTS = PV >> <br> table 1 join table 2 |
| ↳ `POST_APPR_DV` | C | 2 |  | Post Approval Division | 00 :‘Simple approval’, 10 : 'Expense approval' |
| ↳ `REG_NM` | C | 100 |  | Register Name | Table 8: USER_NM where USER_ID: REG_ID |
| ↳ `REG_ID` | C | 15 |  | 등록자ID |  |
| ↳ `REG_DTM` | C | 14 |  | 등록일시 | For Article>> Post Date and Request Date <br> For Approval>> Post Date |
| ↳ `REG_IMG_URL` | C | 200 |  | Register Image Url | Table 8: USER_IMG is not null where USER_ID: REG_ID >> {Base URL}/szon_0012_003_r002.act?USER_ID={REG_ID} |
| ↳ `USER_IMG` | C | 500 |  | User Image | Table 8 |
| ↳ `PIN_POST_TYPE` | C | 2 |  | Pin post type |  |
| ↳ `PIN_POST_DUE_DT` | C | 8 |  | Pin post due date | For Article>> Schedule Date |
| ↳ `POST_CONT` | C | 2000 |  | Post content |  |
| ↳ `TOT_CMT` | N | 15 |  | Total Comment | Table 7 |
| ↳ `READ_YN` | C | 1 |  | Read Y/N | Table 4: N: Show Icon 'N' |
| ↳ `DEL_YN` | C | 1 |  | Delete Y/N | Table 2: Admin and Post owner allow to delete |
| ↳ `DRAFT_POST_SEQ_NO` | N | 15 |  | Dratf post sequence number |  |

**Note 1 — `APPR_STTS`** (verbatim from the sheet):

```text
v2024.1.34 added
 if  APPR_TYPE_CD = '00' then 'P'
 else if  APPR_TYPE_CD == '01' then 'C'
 else if  APPR_TYPE_CD == '02' then  'R'
 else if  APPR_TYPE_CD == '03' then 
 
  select  
    APPRVR_APPR_TOT_CNT  || '/'|| APPRVR_TOT_CNT as  APPR_STTS
   from (
    select  
     ,count (*) as  APPRVR_TOT_CNT
     ,sum (case when apprvr_stts ='A' then 1 else 0 end)  APPRVR_APPR_TOT_CNT
    from  expt_resol_apprvr_infm erai 
      where  BIZ_KEY('session')  
      and APPR_SEQ_NO : OUTPUT(REC > APPR_SEQ_NO)  
   ) sub
v2024.1.37 added
if (user is Admin or  USER_ID ('session') ==  OUTPUT(REC > REG_ID )) 
          and not  exists (
                select 1 
                  from 
                expt_resol_apprvr_infm 
                    where  biz_key ('session')
                     and apprvr_id  : user_id ('session')
                     and apprvr_stts  =  'P'
                    and apprvr_id  : user_id ('session')|
            )   v2024.1.38 added then 
 if  APPR_TYPE_CD = '00' then 'P'
 else if  APPR_TYPE_CD == '01' then 'C'
 else if  APPR_TYPE_CD == '02' then  'R'
 else if  APPR_TYPE_CD == '03' then  'I'

else 
  select   
      APPR_STTS 
  from expt_resol_apprvr_infm  
  where 
    biz_key ('session')
   and apprvr_id  : user_id ('session')|
   and  APPR_SEQ_NO  : OUTPUT( APPR_SEQ_NO)   
end
```

### Process rules (verbatim from the sheet)

Input section:

```text
v2024.1.34 added 
** Approval Display Rule (only for  Approval that has been reqursted) 
 - Admin       : display  all the approvals in the company.
 - Requestor   : display only the approvals that have been requested by the specific requestor.
 - Normal user   : display only the Approvals where the user is listed as an Approver and the status of  Approver  is  in
       'A' (approved)
      , 'R' (rejected)
      , or 'P' (pending) Select all Approval from user's board
Where 
BIZ_KEY: (Session)
Table 1 join Table 2:
POST_TYPE_CD: '02'

Table 6: 
DEL_YN <> 'Y'

order by reg_dtm DESC (default)
if REG_PIN_POST_DATE != null >>add REG_PIN_POST_DATE DESC

*Note: v2024.1.31 added
if INPUT.SRCH_WD != null >> response All data on REC (Pin and Normal)
else >> response REC (Normal) and REC2 (Pin)
```

---

<a id="wabooks_mrec_bltb_0002_d001"></a>

## `WABOOKS_MREC_BLTB_0002_D001` — Delete a post

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_BLTB_0002_D001` |
| Sheet name (source) | `WABOOKS_MREC_BLTB_0002_D001` |
| Sheet title (원문) | Delete Post |
| Business area (업무) | Delete Post (COMM) |
| Screen (source index) | Home \| Post Detail > Tab |
| Legacy web reference | `comm_0192_004_d001` |
| Legacy tables (테이블) | 1. BLTB_POST_INFM <br> 2. USER_BLTB_CONN_INFM <br> 3. BLTB_POST_FILE <br> 4. BLTB_USER_NEW_POST_INFM <br> 5. BLTB_NOTI_INFM <br> 6. BLTB_COMNT_INFM <br> 7. BLTB_POST_COMNT_FILE |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | — (none) |
| Coverage | **MISSING** |
| Last spec revision | — |

### Input — 2 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `BLTB_CD` | N | 15 | Y | Bulletin board code |  |
| `POST_SEQ_NO` | C | 100 | Y | 파일ID |  |

### Output — 0 fields

_No output payload beyond the common `RSLT_CD` / `RSLT_MSG` envelope._

### Process rules (verbatim from the sheet)

Input section:

```text
v2023.1.0.28 
"1. Validate User Admin permission
 Table 2: Select 
 Where
 BIZ_KEY: (Session)
 USER_ID: (Session)
 BLTB_CD
 if BLTB_USER_TYPE  != 'A' >> throws an exception error ""No permission""

2. Validate post owner permission
  Table 1: Select
  Where
  BIZ_KEY: (Session)
  USER_ID: (Session)
  BLTB_CD
  POST_SEQ_NO
  if return null >> throws an exception error ""Not found""
  else  
   if REG_ID != (USER_ID on session) >> throws an exception error ""No permission""
  
3. if (POST_TYPE_CD ( proc'2')  = '02')  
   if 'REF_BLTB_CD or REF_POST_SEQ_NO  is not blank)
   >> thorows exception 'you cannot delete the Approval  that Reference to other post'   v2023.1.0.31 removed

 3.1 Get APPR_DV from EXPT_RESOL_MSTR table. 
  Where condition is BIZ_KEY, APPR_SEQ_NO (proc 2)

 3.2 if APPR_DV is '10', '30', '40', 
  update BUSA_DOC_MSTR's APPR_SEQ_NO to null. 
  (Where condition is BIZ_KEY, APPR_SEQ_NO (proc 2). That may update 2 more rows.)

 3.3 else if APPR_DV is '20',
   update SALY_BOOK_MSTR's APPR_SEQ_NO to null. 
   (Where condition is BIZ_KEY, APPR_SEQ_NO(proc 2).) 3.4 Delete data from table
  EXPT_RESOL_PAY_EVID_FILE
  , EXPT_RESOL_APPR_LINE
  , EXPT_RESOL_FILE
  , EXPT_RESOL_FILE
  , EXPT_RESOL_DETL_INF
  , EXPT_RESOL_DETL
  , EXPT_RESOL_MSTR  
  
 3.5 get rows from  EXPT_RESOL_APPVR_INFM
  where BIZ_KEY ('session'), APPR_SEQ_NO (REC>INPUT)
  
 3.6 delete data from  EXPT_RESOL_APPVR_INFM by  primary key (get value from proc 5)

 3.7.get rows from  BLTB_POST_INFM
   where BIZ_KEY ('session'), APPR_SEQ_NO (from proc 2)
 
 3.8 -delete data from  table  
    BLTB_POST_INFM 
    , BLTB_POST_FILE 
    , BLTB_USER_NEW_POST_INFM
    by PK  (value from proc 3.7)
  -delete data from  table BLTB_COMNT_INFM ,  BLTB_POST_COMNT_FILE by  PK v2023.1.0.31 added
    
   3.9 BLTB_NOTI_INFM: Patch DEL_YN='Y'
  Where 
  ,BIZ_KEY: (Session) 
  ,BLTB_CD : from (proc 3.7)
  ,POST_SEQ_NO : from (proc 3.7)
  ,DEL_YN: 'N'
  "    
4. else if (POST_TYPE_CD ( proc'2') != '02')

 4.1 delete data from table 1,3  with pk
 4.2  Delete data from table 4  by biz_key, bltb_cd, post_seq_no
 4.3  Table 5: Patch DEL_YN='Y'
    Where 
    BIZ_KEY: (Session) 
    BLTB_CD
    POST_SEQ_NO
    DEL_YN: 'N'
```

---

<a id="wabooks_mrec_bltb_0002_d003"></a>

## `WABOOKS_MREC_BLTB_0002_D003` — Delete a draft post

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_BLTB_0002_D003` |
| Sheet name (source) | `WABOOKS_MREC_BLTB_0002_D003` |
| Sheet title (원문) | Delete Draft Post |
| Business area (업무) | Delete Draft Post (COMM) |
| Screen (source index) | Home \| Post Detail > Tab |
| Legacy web reference | `comm_0192_004_d002` |
| Legacy tables (테이블) | 1. BLTB_DRAFT_POST_MSTR <br> 2. BLTB_DRAFT_POST_DETL <br> 3. BLTB_DRAFT_POST_FILE <br> 4. USER_BLTB_CONN_INFM <br> 5. BLTB_DRAFT_POST_APPRVR_INFM |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | — (none) |
| Coverage | **MISSING** |
| Last spec revision | Added v2024.1.18 |

### Input — 2 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `BLTB_CD` | N | 15 | Y | Bulletin board code |  |
| `DRAFT_POST_SEQ_NO` | N | 15 | Y | Dratf post sequence number |  |

### Output — 0 fields

_No output payload beyond the common `RSLT_CD` / `RSLT_MSG` envelope._

### Process rules (verbatim from the sheet)

Input section:

```text
v2024.1.18
1. Validate User Admin permission
Table 4: Select 
Where
BIZ_KEY: (Session)
USER_ID: (Session)
BLTB_CD
if BLTB_USER_TYPE  != 'A' >> throws an exception error "No permission"

2.Validate post owner permission
Table 1: Select
Where
BIZ_KEY: (Session)
USER_ID: (Session)
BLTB_CD
DRAFT_POST_SEQ_NO
if return null >> throws an exception error "Not found"
else  
    if REG_ID != (USER_ID on session) >> throws an exception error "No permission"

3.Table 1,2,3,5: Delete by primary key of each table
```

---

<a id="wabooks_mrec_bltb_0002_r002"></a>

## `WABOOKS_MREC_BLTB_0002_R002` — Board list for re-posting

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_BLTB_0002_R002` |
| Sheet name (source) | `WABOOKS_MREC_BLTB_0002_R002` |
| Sheet title (원문) | Inquiry boards |
| Business area (업무) | List Board (COMM) |
| Screen (source index) | Board> Home > Tab > Post to another board |
| Legacy web reference | `comm_0192_004_r001` |
| Legacy tables (테이블) | 1. BLTB_INFM <br> 2. USER_BLTB_CONN_INFM |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | — (none) |
| Coverage | **MISSING** |
| Last spec revision | Updated v2024.1.14 |

### Input — 2 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `BLTB_CD` | N | 15 | Y | Bulletin board cd |  |
| `SRCH_WD` | C | 100 |  | 검색어 | BLTB_NAME LIKE |

### Output — 4 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `REC` |  |  |  |  |  |
| ↳ `BLTB_CD` |  |  |  |  | Table 1 |
| ↳ `BLTB_NAME` |  |  |  |  | Table 1 |
| ↳ `CONT` | C | 200 |  | Bulletin board description | v2024.1.14 added >> Table 1 |

### Process rules (verbatim from the sheet)

Input section:

```text
1. Table 1 join table 2 on BIZ_KEY and BLTB_CD
Where 
BIZ_KEY: (session)
USER_ID: (session)
BLTB_CD != (BLTB_CD of input)
```

---

<a id="wabooks_mrec_bltb_0002_r003"></a>

## `WABOOKS_MREC_BLTB_0002_R003` — Comment list

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_BLTB_0002_R003` |
| Sheet name (source) | `WABOOKS_MREC_BLTB_0002_R003` |
| Sheet title (원문) | Inquiry Comments |
| Business area (업무) | List Comments (COMM) |
| Screen (source index) | Home \| Post Detail > Comments |
| Legacy web reference | `comm_0192_008_r001` |
| Legacy tables (테이블) | 1. BLTB_COMNT_INFM <br> 2. BLTB_POST_COMNT_FILE <br> 3. USER_INFM |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | — (none) |
| Coverage | **MISSING** |
| Last spec revision | Update v2024.1.27 |

**Delta.** `BLTB_COMNT_INFM` has no refactor counterpart.

### Input — 2 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `BLTB_CD` | C | 15 | Y | post sequence number |  |
| `POST_SEQ_NO` | C | 100 | Y | 파일ID |  |

### Output — 20 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `USER_IMG` | C | 500 |  | User Image | v2024.1.27 added >> <br> Table 3: Select user_img where USER_ID: USER_ID(Session); |
| `USER_IMG_URL` | C | 200 |  | User Image Url | v2024.1.27 added <br> if USER_IMG is not null >> <br> {Base URL}/szon_0012_003_r002.act?USER_ID={USER_ID(Session)}&cmd=getthumb |
| `REC` |  |  |  |  |  |
| ↳ `REG_NM` | C | 100 |  | Register Name | Table 3: Select USER_NM where USER_ID:(REG_ID of Table 1) |
| ↳ `REG_DTM` | C | 14 |  | Register Date Time |  |
| ↳ `REG_ID` | C | 100 |  | Register ID |  |
| ↳ `REG_IMG_URL` | C | 200 |  | Register Image Url | {Base URL}/szon_0012_003_r002.act?USER_ID={REG_ID}&cmd=getthumb |
| ↳ `USER_IMG` | C | 500 |  | User Image | v2024.1.11 added >> Table 3 |
| ↳ `COMNT_CONT` | C | 1000 |  | Comment content | *Mention data store on DB like below: <br> @\[{USER_NM})\]({USER_ID}) |
| ↳ `BLTB_CD` | C | 15 |  | Bulletin board code |  |
| ↳ `POST_SEQ_NO` | N | 15 |  | post sequence number |  |
| ↳ `COMNT_SEQ_NO` | N | 15 |  | comment sequence number |  |
| ↳ `COMNT_DV_CD` | C | 1 |  | Comment classification code | C: 'Comment', R : 'Remark' |
| ↳ `COMNT_TASK_CD` | C | 4 |  | Comment task code | If COMNT_DV_CD = 'C' <br> C001(‘Comment’) <br> else if COMNT_DV_CD = 'R' <br> R001 ('Pinned') <br> R002 ('Approval' -> 'Rejected') <br> R003 ('Approval' → ‘Complete’) <br> R004 ('Approval' -> 'Approved') |
| ↳ `REC2` | R |  |  | Record 2 |  |
| ↳ ↳ `FILE_ID` | C | 100 |  | 파일ID | Table 2 |
| ↳ ↳ `ORG_FILE_NM` | C | 500 |  | 원본파일명 | Table 2 |
| ↳ ↳ `SAVE_FILE_NM` | C | 500 |  | 저장파일명 | Table 2 |
| ↳ ↳ `FILE_SIZE` | C | 100 |  | 파일크기 | Table 2 |
| ↳ ↳ `FILE_URL` | C | 200 |  | File Url | {Base URL}/comm_0192_008_r002.act?{Datetime}&BLTB_CD={BLTB_CD}&POST_SEQ_NO={POST_SEQ_NO}&FILE_ID={FILE_ID}&cmd=getthumb |

### Process rules (verbatim from the sheet)

Input section:

```text
1. Table 1 join table 2: Select
Where 
BIZ_KEY: (Session)
```

---

<a id="wabooks_mrec_bltb_0002_c001"></a>

## `WABOOKS_MREC_BLTB_0002_C001` — Add a comment (mentions + files)

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_BLTB_0002_C001` |
| Sheet name (source) | `WABOOKS_MREC_BLTB_0002_C001` |
| Sheet title (원문) | Add Commments |
| Business area (업무) | Add Comments(COMM) |
| Screen (source index) | Home \| Post Detail > Comments |
| Legacy web reference | `comm_0192_008_c001` |
| Legacy tables (테이블) | 1. BLTB_COMNT_INFM <br> 2. BLTB_POST_COMNT_FILE <br> 3. BLTB_NOTI_INFM <br> 4. BLTB_NOTI_CONT_INFM <br> 5. USER_BLTB_CONT_INFM <br> 6: BLTB_POST_INFM <br> 7. PUSH_NOTICE_INFM <br> 8. MSG_SND_HIS <br> 9. PUSH_DEVICE_MSTR |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | — (none) |
| Coverage | **MISSING** |
| Last spec revision | Updated v2024.1.28 |

### Input — 8 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `REC` | R |  |  | Record |  |
| ↳ `FILE_NM` | C | 500 | Y | 파일명 |  |
| ↳ `FILE_DATA` | B | 4000 | Y | 파일데이터 | **see note 1 below** |
| `BLTB_CD` | C | 15 | Y | Bulletin board code |  |
| `POST_SEQ_NO` | N | 15 | Y | post sequence number |  |
| `COMNT_DV_CD` | C | 1 | Y | Comment classification code | C: 'Comment', R : 'Remark' |
| `COMNT_TASK_CD` | C | 4 | Y | Comment task code | If COMNT_DV_CD = 'C' <br> C001(‘Comment’) <br> else if COMNT_DV_CD = 'R' <br> R001 ('Pinned') <br> R002 ('Approval' -> 'Rejected') <br> R003 ('Approval' → ‘Complete’) <br> R004 ('Approval' -> 'Approved') |
| `COMNT_CONT` | C | 1000 |  | Comment Content | *Mention data store on DB like below: <br> @\[{USER_NM})\]({USER_ID}) |

**Note 1 — `FILE_DATA`** (verbatim from the sheet):

```text
File data to be upload to multipart/form-data 
1. Only files with jpg, png, pdf, doc(x), xls(x), and ppt(x) extensions are allowed.
2. Up to 5 MB per file, up to 5 files allowed(follow expense upload rules)
3. The uploaded file is saved in the
/WAS_DATA/file/{BIZ_KEY}/comm_0192_008_c001 directory on the server.
4. Save attachment information in the table 2.
BLTB_CD: (Input)
POST_SEQ_NO: (Input)
FILE_ID: (Sequence)
ORG_FILE_NM: Original file name of the attached file
SAVE_FILE_NM: File name saved on the server of the attached file
FILE_SIZE: Size of the attached file.
```

### Output — 0 fields

_No output payload beyond the common `RSLT_CD` / `RSLT_MSG` envelope._

### Process rules (verbatim from the sheet)

Input section:

```text
Notification: 
1.0.Table 5: Find all the users on current board.
1.1.Table 3:  Insert notification to all users on current board.
sender_id: (session user_id)
sender_name: (wb_get_user_nm(sender_id))
rcv_id: (table 5 user_id)
rcv_nm: (wb_get_user_nm(rcv_id))
noti_dt: YYYYMMDD
noti_tm: HHMMSS
noti_url: depend on POST_TYPE_CD(Table 6)
- '01': bltb_0001_002.act?POST_SEQ_NO={POST_SEQ_NO}&BLTB_CD={BLTB_CD}&COMM_SEQ_NO={COMM_SEQ_NO}
- '02': bltb_0001_003.act?POST_SEQ_NO={POST_SEQ_NO}&BLTB_CD={BLTB_CD}&COMM_SEQ_NO={COMM_SEQ_NO}
actv_yn: 'N'
Table 4: Insert by languages
- VN version
LANG_CD: 'EN', 'KO','VN'
TITL: 'Added a new comment.'
CONT:
- KH version 
LANG_CD: 'EN', 'KO','KH'
TITL: 'Added a new comment.'
CONT: v2024.1.15 added
1.2.Table 9: 
v2024.1.28 updated
Find each user by PK(BIZ_KEY, USER_ID) != null >> Process 1.3 and 1.4
For notification comments, Should be pushed to only post-owner, approvers, and for whom else they mentioned. >> Process 1.3 and 1.4
1.3. Table 7 : Insert 
BIZ_KEY : BIZ_KEY ('session')
USER_ID: USER_ID ('session')
SEQ_NO : Generate Sequence Base on BIZ_KEY, USER_ID 
SND_DATE : YYYYMMDD
SND_TIME : HHMMSS
SND_ID: USER_ID ('session')
SND_NM: ((wb_get_user_nm(SND_ID)))
RCV_ID: USER_ID ('Process 1.0')
RCV_NM: ((wb_get_user_nm(RCV_ID)))
REG_SEQ_NO: POST_SEQ_NO('Table 3')
COMNT_SEQ_NO: COMNT_SEQ_NO('After Insert Table 1')
DV_CD: '15' (15.Specific user comment on your post)
CONT: COMNT_CONT ('INPUT')
v2024.1.24 added
BLTB_CD: BLTB_CD ('INPUT') 

v2024.1.15 added
1.4. Table 8: Insert
REG_SEQ_NO: generate Sequence
BIZ_KEY: (Session)
USER_ID: USER_ID ('session')
MSG_TYPE: 'P'
RCV_NO:  USER_ID ('Process 1.0')
RCV_NM: ((wb_get_user_nm(RCV_NO))) v2024.1.26 updated
SND_TITL: 'WABOOKS'
SND_CONT: 
 if CONT (Table 7) is null >> SND_NM(Table 7) + " commented on your post with an attachment."
 else if CONT (Table 7) is not null >> SND_NM(Table 7) + " commented on your post: "+ CONT (Table 7)

SND_RSLT: 'SND_WAIT'
STTS: '0'
PUSH_ACTION_URL: if table 7>>DV_CD = 6 to 15 >> 
json string({
DV_CD: DV_CD (Table 7),
REG_SEQ_NO: REG_SEQ_NO (Table 7),
COMNT_SEQ_NO: COMNT_SEQ_NO (Table 7),
v2024.1.24 added
BLTB_CD: BLTB_CD (Table 7) 
})
```

---

<a id="wabooks_mrec_bltb_0002_d002"></a>

## `WABOOKS_MREC_BLTB_0002_D002` — Delete a comment

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_BLTB_0002_D002` |
| Sheet name (source) | `WABOOKS_MREC_BLTB_0002_D002` |
| Sheet title (원문) | Delete Comments |
| Business area (업무) | Edit Comments(COMM) |
| Screen (source index) | Home \| Post Detail > Comments |
| Legacy web reference | `comm_0192_008_d001` |
| Legacy tables (테이블) | 1. BLTB_COMNT_INFM <br> 2. BLTB_POST_COMNT_FILE |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | — (none) |
| Coverage | **MISSING** |
| Last spec revision | — |

### Input — 3 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `BLTB_CD` | C | 15 | Y | Bulletin board code |  |
| `POST_SEQ_NO` | N | 15 | Y | post sequence number |  |
| `COMNT_SEQ_NO` | C | 15 | Y | comment sequence number | C: 'Comment', R : 'Remark' |

### Output — 0 fields

_No output payload beyond the common `RSLT_CD` / `RSLT_MSG` envelope._

### Process rules (verbatim from the sheet)

Input section:

```text
** validation :  
  -if COMNT_DV_CD of table 1 is 'R' throw error
  - if user is not the owner of commnet throw error

1.delete data from table 1,2
```

---

<a id="wabooks_mrec_bltb_0002_u001"></a>

## `WABOOKS_MREC_BLTB_0002_U001` — Edit a comment

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_BLTB_0002_U001` |
| Sheet name (source) | `WABOOKS_MREC_BLTB_0002_U001` |
| Sheet title (원문) | Update Comments |
| Business area (업무) | Delete Comments(COMM) |
| Screen (source index) | Home \| Post Detail > Comments |
| Legacy web reference | `comm_0192_008_u001` |
| Legacy tables (테이블) | 1. BLTB_COMNT_INFM <br> 2. BLTB_POST_COMNT_FILE |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | — (none) |
| Coverage | **MISSING** |
| Last spec revision | — |

### Input — 9 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `BLTB_CD` | C | 15 | Y | Bulletin board code |  |
| `POST_SEQ_NO` | N | 15 | Y | post sequence number |  |
| `REC` | R |  |  | Record |  |
| ↳ `FILE_NM` | C | 500 | Y | 파일명 |  |
| ↳ `FILE_DATA` | B | 4000 | Y | 파일데이터 | **see note 1 below** |
| `COMNT_SEQ_NO` | C | 15 | Y | comment sequence number | C: 'Comment', R : 'Remark' |
| `COMNT_CONT` | C | 15 |  | Comment content |  |
| `REC2` |  |  |  | record 2 | for remove file |
| ↳ `FILE_ID` | C | 100 |  | File Id |  |

**Note 1 — `FILE_DATA`** (verbatim from the sheet):

```text
File data to be upload to multipart/form-data 
1. Only files with jpg, png, pdf, doc(x), xls(x), and ppt(x) extensions are allowed.
2. Up to 5 MB per file, up to 5 files allowed(follow expense upload rules)
3. The uploaded file is saved in the
/WAS_DATA/file/{BIZ_KEY}/comm_0192_008_c001 directory on the server.
4. Save attachment information in the table 2.
BLTB_CD: (Input)
POST_SEQ_NO: (Input)
FILE_ID: (Sequence)
ORG_FILE_NM: Original file name of the attached file
SAVE_FILE_NM: File name saved on the server of the attached file
FILE_SIZE: Size of the attached file.
```

### Output — 0 fields

_No output payload beyond the common `RSLT_CD` / `RSLT_MSG` envelope._

### Process rules (verbatim from the sheet)

Input section:

```text
** validation :  
  -if COMNT_DV_CD of table 1 is 'R' throw error
  - if user is not the owner of commnet throw error
```

---

<a id="wabooks_mrec_bltb_0003_r001"></a>

## `WABOOKS_MREC_BLTB_0003_R001` — Board detail with its posts

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_BLTB_0003_R001` |
| Sheet name (source) | `WABOOKS_MREC_BLTB_0003_R001` |
| Sheet title (원문) | Inquiry Articles and Draft Article |
| Business area (업무) | Board detail and list on current board (All, Draft, Article) |
| Screen (source index) | Board> Board Details |
| Legacy web reference | `bltb_0005_001_r001+ comm_0192_006_r001` |
| Legacy tables (테이블) | 1. BLTB_POST_INFM <br> 2. USER_BLTB_CONN_INFM <br> 3. BLTB_NOTI_CONT_INFM <br> 4. BLTB_USER_NEW_POST_INFM <br> 5. BLTB_DRAFT_POST_MSTR <br> 6. EXPT_RESOL_MSTR <br> 7. BLTB_COMNT_INFM <br> 8. USER_INFM <br> 9. BLTB_INFM |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | — (none) |
| Coverage | **MISSING** |
| Last spec revision | Updated v2024.1.31 |

### Input — 7 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `POST_TYPE_CD` | C | 2 | Y | Post type code | "": All, "01":Article, "02": Approval, "03": Draft |
| `SRCH_WD` | C | 100 |  | Search word | Like POST_TITLE or DOC_NM or TITL or DOC_NO |
| `BLTB_CD` | C | 15 | Y | Bulletin board code | v2024.1.8 update |
| `PAGE_NO` | N | 15 | Y | 페이지번호 | DF: 1 |
| `PAGE_PER_CNT` | N | 15 | Y | 페이지별건수 | DF: 10 |
| `FROM_DT` | C | 8 |  | 검색일자 From | v2024.1.10 added >> Table 1 >> REG_DTM BETWEEN FROM |
| `TO_DT` | C | 8 |  | 검색일자 To | v2024.1.10 added >> Table 1 >> REG_DTM BETWEEN TO |

### Output — 57 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `TOT_CNT` | N | 15 |  | Total Count | All >> Select count (Table 1 join table 2) + count(Table 5 join table 2) <br> Wherer BIZ_KEY: (Session),BLTB_CD |
| `TOT_CNT_ART` | N | 15 |  | Total Count Article | Article >> Select count (Table 1 join table 2) where BIZ_KEY: (Session),POST_TYPE_CD = '01' & BLTB_CD |
| `TOT_CNT_APPR` | N | 15 |  | TTotal Count Approval | Approval >> Select count (Table 1 join table 2) where BIZ_KEY: (Session),POST_TYPE_CD = '02' & BLTB_CD |
| `TOT_CNT_DRAFT` | N | 15 |  | Total Count Draft | Draft >> Select count (Table 5 join table 2) where BIZ_KEY: (Session),BLTB_CD |
| `BLTB_NAME` | C | 50 |  | Bulletin board name | Table 9 |
| `CONT` | C | 200 |  | 내용 | Table 9 |
| `BLTB_USER_TYPE` | C | 1 |  | Bulletin User Type | Table 2 <br> where BIZ_KEY: (Session) <br> USER_ID = (Session USER_ID) <br> A: Admin, M: Member |
| `REC` | R |  |  | Record |  |
| ↳ `BLTB_CD` | C | 15 |  | Bulletin board code |  |
| ↳ `POST_SEQ_NO` | N | 15 |  | Post sequence number |  |
| ↳ `POST_TITLE` | C | 200 |  | Post title |  |
| ↳ `POST_TYPE_CD` | C | 2 |  | Post type code | 01' : Article , '02':Approval |
| ↳ `POST_STTS` | C | 2 |  | Post status | PL : Public , PV:Private <br> if POST_STTS = PV >> <br> table 1 join table 2 |
| ↳ `REG_NM` | C | 100 |  | Register Name | Table 8: USER_NM where USER_ID: REG_ID |
| ↳ `REG_ID` | C | 15 |  | 등록자ID |  |
| ↳ `REG_DTM` | C | 2 |  | 등록일시 |  |
| ↳ `REG_IMG_URL` | C | 200 |  | Register Image Url | Table 8: USER_IMG is not null where USER_ID: REG_ID >> {Base URL}/szon_0012_003_r002.act?USER_ID={REG_ID} |
| ↳ `USER_IMG` | C | 500 |  | User Image | v2024.1.11 added >> Table 8 |
| ↳ `PIN_POST_TYPE` | C | 2 |  | Pin post type |  |
| ↳ `PIN_POST_DUE_DT` | C | 8 |  | Pin post due date |  |
| ↳ `POST_CONT` | C | 2000 |  | Post content |  |
| ↳ `TOT_CMT` | N | 15 |  | Total Comment | Table 7 |
| ↳ `APPR_SEQ_NO` | C | 15 |  | 지출결의번호 | Table 6 |
| ↳ `PAY_END_YN` | C | 1 |  | Approval End Y/N | Table 6: Y: Approved, N: Pending |
| ↳ `PAY_YN` | C | 1 |  | Pay Y/N | Table 6: Y: Paid, N: Not Paid, P: Partially Paid |
| ↳ `READ_YN` | C | 1 |  | Read Y/N | Table 4: N: Show Icon 'N' |
| ↳ `DEL_YN` | C | 1 |  | Delete Y/N | Table 2: Admin and Post owner allow to delete |
| ↳ `DRAFT_POST_SEQ_NO` | N | 15 |  | Dratf post sequence number | Updated v2024.1.17 |
| ↳ `PAY_REQ_DATE` | C | 8 |  | 지급요청일 | Table 6 <br> For Approval>> Request Date |
| ↳ `PAY_DATE` | C | 8 |  | 지급예정일 | Table 6 <br> For Approval>> Schedule Date |
| ↳ `TOT_PAY_KHR_AMT` | N | 20.6 |  | 결의총KHR금액 | Table 6 <br> Ref: bltb_0001_003_r001 |
| ↳ `TOT_PAY_USD_AMT` | N | 20.6 |  | 결의총USD금액 | Table 6 <br> Ref: bltb_0001_003_r001 |
| ↳ `CMTNT_REG_NM` | C | 100 |  | Register Name | Table 8: Select USER_NM where USER_ID:(REG_ID of Table 7) |
| ↳ `COMNT_CONT` | C | 1000 |  | Comment content | Table 7: <br> *Mention data store on DB like below: <br> @\[{USER_NM})\]({USER_ID}) |
| ↳ `COMNT_SEQ_NO` | N | 15 |  | comment sequence number | Table 7: <br> *Mention data store on DB like below: <br> @\[{USER_NM})\]({USER_ID}) |
| ↳ `COMNT_DV_CD` | C | 1 |  | Comment classification code | Table 7: <br> C: 'Comment', R : 'Remark' |
| ↳ `COMNT_TASK_CD` | C | 4 |  | Comment task code | Table 7: <br> If COMNT_DV_CD = 'C' <br> C001(‘Comment’) <br> else if COMNT_DV_CD = 'R' <br> R001 ('Pinned') <br> R002 ('Approval' -> 'Rejected') <br> R003 ('Approval' → ‘Complete’) <br> R004 ('Approval' -> 'Approved') |
| ↳ `COMNT_TASK_CD` |  |  |  |  |  |
| `REC2` | R |  |  | Record 2 | Plinned List |
| ↳ `BLTB_CD` | C | 15 |  | Bulletin board code |  |
| ↳ `POST_SEQ_NO` | N | 15 |  | Post sequence number |  |
| ↳ `POST_TITLE` | C | 200 |  | Post title |  |
| ↳ `POST_TYPE_CD` | C | 2 |  | Post type code | 01' : Article , '02':Approval |
| ↳ `POST_STTS` | C | 2 |  | Post status | PL : Public , PV:Private <br> if POST_STTS = PV >> <br> table 1 join table 2 |
| ↳ `POST_APPR_DV` | C | 2 |  | Post Approval Division | 00 :‘Simple approval’, 10 : 'Expense approval' |
| ↳ `REG_NM` | C | 100 |  | Register Name | Table 8: USER_NM where USER_ID: REG_ID |
| ↳ `REG_ID` | C | 15 |  | 등록자ID |  |
| ↳ `REG_DTM` | C | 14 |  | 등록일시 | For Article>> Post Date and Request Date <br> For Approval>> Post Date |
| ↳ `REG_IMG_URL` | C | 200 |  | Register Image Url | Table 8: USER_IMG is not null where USER_ID: REG_ID >> {Base URL}/szon_0012_003_r002.act?USER_ID={REG_ID} |
| ↳ `USER_IMG` | C | 500 |  | User Image | Table 8 |
| ↳ `PIN_POST_TYPE` | C | 2 |  | Pin post type |  |
| ↳ `PIN_POST_DUE_DT` | C | 8 |  | Pin post due date | For Article>> Schedule Date |
| ↳ `POST_CONT` | C | 2000 |  | Post content |  |
| ↳ `TOT_CMT` | N | 15 |  | Total Comment | Table 7 |
| ↳ `READ_YN` | C | 1 |  | Read Y/N | Table 4: N: Show Icon 'N' |
| ↳ `DEL_YN` | C | 1 |  | Delete Y/N | Table 2: Admin and Post owner allow to delete |
| ↳ `DRAFT_POST_SEQ_NO` | N | 15 |  | Dratf post sequence number |  |

### Process rules (verbatim from the sheet)

Input section:

```text
Select all Approval from user's board
Where 
BIZ_KEY: (Session)
Table 1:
BLTB_CD

Table 6: 
DEL_YN <> 'Y'

order by reg_dtm DESC (default)
if REG_PIN_POST_DATE != null >>add REG_PIN_POST_DATE DESC

*Note: v2024.1.31 added
if INPUT.SRCH_WD != null >> response All data on REC (Pin and Normal)
else >> response REC (Normal) and REC2 (Pin)
```

---

<a id="wabooks_mrec_bltb_0003_d001"></a>

## `WABOOKS_MREC_BLTB_0003_D001` — Delete a board

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_BLTB_0003_D001` |
| Sheet name (source) | `WABOOKS_MREC_BLTB_0003_D001` |
| Sheet title (원문) | Delete Board |
| Business area (업무) | Delete board |
| Screen (source index) | Board> Board Details > Tab |
| Legacy web reference | `comm_0192_005_d001` |
| Legacy tables (테이블) | 1. BLTB_INFM <br> 2. USER_BLTB_CONN_INFM <br> 3. BLTB_POST_INFM <br> 4. BLTB_NOTI_INFM |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | — (none) |
| Coverage | **MISSING** |
| Last spec revision | — |

### Input — 1 field

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `BLTB_CD` | N | 15 | Y | Bulletin board code |  |

### Output — 0 fields

_No output payload beyond the common `RSLT_CD` / `RSLT_MSG` envelope._

### Process rules (verbatim from the sheet)

Input section:

```text
0. Validation Only User Admin can delete board
Table 2: Select 
BIZ_KEY: (Session)
USER_ID: (Session)
BLTB_CD

if BLTB_USER_TYPE != 'A' >> throws exception error (No permission)
1. Table 3: Select count(*) 
BIZ_KEY: (Session)
BLTB_CD

if Num 1 return 0 then execute Num 2 and 3 2. Table 2: Delete
Where
BIZ_KEY: (Session)
USER_ID: (Session)
BLTB_CD

3. Table 1: Delete
Where 
BIZ_KEY: (Session) 
BLTB_CD

V2023.1.0.26
4. Table 4: Patch DEL_YN='Y'
Where 
BIZ_KEY: (Session) 
BLTB_CD
DEL_YN: 'N'
```

---

<a id="wabooks_mrec_bltb_0003_u001"></a>

## `WABOOKS_MREC_BLTB_0003_U001` — Edit a board

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_BLTB_0003_U001` |
| Sheet name (source) | `WABOOKS_MREC_BLTB_0003_U001` |
| Sheet title (원문) | Update Board |
| Business area (업무) | Edit Board |
| Screen (source index) | Board> Board Details > Tab > Edit Board |
| Legacy web reference | `comm_0192_003_u001` |
| Legacy tables (테이블) | 1. BLTB_INFM |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | — (none) |
| Coverage | **MISSING** |
| Last spec revision | — |

### Input — 3 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `BLTB_CD` | N | 15 | Y | Bulletin board code |  |
| `BLTB_NAME` | C | 50 | Y | Bulletin board name |  |
| `CONT` | C | 200 |  | Bulletin board description |  |

### Output — 0 fields

_No output payload beyond the common `RSLT_CD` / `RSLT_MSG` envelope._

### Process rules (verbatim from the sheet)

Input section:

```text
Table 1: update
BIZ_KEY: (Session)
```

---

<a id="wabooks_mrec_bltb_0004_c001"></a>

## `WABOOKS_MREC_BLTB_0004_C001` — Create an article / save an article draft

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_BLTB_0004_C001` |
| Sheet name (source) | `WABOOKS_MREC_BLTB_0004_C001` |
| Sheet title (원문) | Create Article Post & update draft Article |
| Business area (업무) | Create new Article and update draft article (COMM) |
| Screen (source index) | Board Details \| Home > New Article |
| Legacy web reference | `comm_0192_001_c001` |
| Legacy tables (테이블) | 1. BLTB_POST_INFM <br> 2. BLTB_POST_FILE <br> 3. BLTB_DRAFT_POST_MSTR <br> 4. BLTB_DRAFT_POST_FILE <br> 5. BLTB_USER_NEW_POST_INFM <br> 6. BLTB_NOTI_INFM <br> 7. BLTB_NOTI_CONT_INFM <br> 8. USER_BLTB_CONN_INFM <br> 9. PUSH_NOTICE_INFM <br> 10. MSG_SND_HIS <br> 11. PUSH_DEVICE_MSTR |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | — (none) |
| Coverage | **MISSING** |
| Last spec revision | Updated v2024.1.26 |

### Input — 22 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `REC2` | R |  |  | Record 2 | Post to another board |
| ↳ `BLTB_CD` | C | 15 |  | Bulletin board code | if BLTB_CD = null >> BLTB_CD = News Board (default) |
| `DRAFT_POST_SEQ_NO` | N | 15 |  | Dratf post sequence number | It required when Open as draft |
| `POST_TITLE` | C | 200 |  | Post title |  |
| `POST_ANTH_BLTB_YN` | C | 1 |  | Post to another board Y/N | Y: Post to another board, N: default |
| `POST_TYPE_CD` | N | 2 | Y | Post type code | 01' : Article , '02':Approval |
| `POST_CONT` | C | 2000 | Y | Post content |  |
| `POST_STTS` | C | 2 | Y | Post status | PL : Public , PV:Private |
| `POST_DRAFT_YN` | C | 1 | Y | Post draft YN | Y: Save draft, N: Post or Request |
| `PIN_POST_TYPE` | C | 2 |  | Pin post type | UD':'Unlimited dates ', 'LD' : 'limited date' |
| `PIN_POST_DUE_DT` | C | 8 |  | Pin post due date | require when PIN_POST_TYPE is 'LD' |
| `REF_BLTB_CD` | C | 15 |  | Reference bulletin board code | require when POST_ANTH_BLTB_YN is 'Y' v2023.1.0.28 |
| `REF_POST_SEQ_NO` | N | 15 |  | Reference post sequence number | require when APPR_SEQ_NO 'Y' v2023.1.0.28 |
| `REC` | R |  |  | Record | v2024.1.5 add <br> File for Add new <br> For table 2 or 4 <br> Move from Temp directory to Original directory then delete from Temp directory. <br> Temp: /WAS_DATA/file/{biz_key}/comm_0192_001_c003/ <br> Original: /WAS_DATA/file/{biz_key}/comm_0192_001_c001/ |
| ↳ `FILE_NM` | C | 500 |  | 파일명 | * required if COPY_FILE_YN = 'N' |
| ↳ `FILE_DATA` | B | 4000 |  | 파일데이터 | * required if COPY_FILE_YN = 'N' |
| ↳ `ORG_FILE_NM` | C | 500 |  | 원본파일명 | * required if COPY_FILE_YN = 'Y' |
| ↳ `SAVE_FILE_NM` | C | 500 |  | 저장파일명 | * required if COPY_FILE_YN = 'Y' |
| ↳ `FILE_SIZE` | C | 100 |  | 파일크기 | * required if COPY_FILE_YN = 'Y' |
| ↳ `COPY_FILE_YN` | C | 1 |  | copy file yn | *Required for 'post to other board' page <br> if Page is 'Post to other board' >> 'Y' (Copy and rename on Server) , <br> else >> ‘N’ |
| `REC3` | R |  |  | Record 3 | File for Delete <br> Open as draft |
| ↳ `FILE_ID` | C | 100 |  | 파일ID |  |

### Output — 0 fields

_No output payload beyond the common `RSLT_CD` / `RSLT_MSG` envelope._

### Process rules (verbatim from the sheet)

Input section:

```text
BIZ_KEY: (Session)
REG_ID: (Session)
- Table 1 & Table 3:
REG_CHNL: 'MOB'
PC_MOD_YN: 'Y'

1. if DRAFT_POST_SEQ_NO !=null and POST_DRAFT_YN = Y >> Table 3 & 4: Patch

else if DRAFT_POST_SEQ_NO !=null and POST_DRAFT_YN = N >> Table 1 & 2: Insert and Table 5: Insert
 (READ_YN: "N") and Table 3 & 4 : Delete 

else if DRAFT_POST_SEQ_NO =null and POST_DRAFT_YN = Y >> Table 3 & 4: Insert

else if DRAFT_POST_SEQ_NO =null and POST_DRAFT_YN = N >> Table 1 & 2: Insert Table 5: Insert
( READ_YN: "N") 2.0.if POST_DRAFT_YN = 'N' (Notification) >>
2.1.Table 8: Find all the users on current board.
2.2.Table 6:  Insert notification to all users on current board.
sender_id: (session user_id)
sender_name: (wb_get_user_nm(sender_id))
rcv_id: (table 8 user_id)
rcv_nm: (wb_get_user_nm(rcv_id))
noti_dt: YYYYMMDD
noti_tm: HHMMSS
noti_url: depend on POST_TYPE_CD(input)
- '01': bltb_0001_002.act?POST_SEQ_NO={POST_SEQ_NO}&BLTB_CD={BLTB_CD}
- '02': bltb_0001_003.act?POST_SEQ_NO={POST_SEQ_NO}&BLTB_CD={BLTB_CD}
actv_yn: 'N'
Table 7: Insert by languages
- VN version
LANG_CD: 'EN', 'KO','VN'
TITL: 'Added a new article.'
CONT:
- KH version 
LANG_CD: 'EN', 'KO','KH'
TITL: 'Added a new article.'
CONT:
```

Input section:

```text
v2024.1.15 added
2.3.Table 11: Find each user by PK(BIZ_KEY, USER_ID) != null >> Process 2.4 and 2.5
2.4.Table 9: Insert 
BIZ_KEY : BIZ_KEY ('session')
USER_ID: USER_ID ('Table 8')
SEQ_NO : Generate Sequence Base on BIZ_KEY, USER_ID 
SND_DATE : YYYYMMDD
SND_TIME : HHMMSS
SND_ID: USER_ID ('session')
SND_NM: ((wb_get_user_nm(SND_ID)))
RCV_ID: USER_ID ('Process 2.1')
RCV_NM: ((wb_get_user_nm(RCV_ID)))
REG_SEQ_NO: POST_SEQ_NO('Table 1')
DV_CD : '12' (12.New article posted by specific user)
CONT: POST_TITLE ('INPUT')
v2024.1.24 added
BLTB_CD: BLTB_CD ('INPUT.REC2')

2.5.Table 10: Insert
REG_SEQ_NO: generate Sequence
BIZ_KEY: (Session)
USER_ID: USER_ID ('Table 8')
MSG_TYPE: 'P'
RCV_NO: USER_ID ('Process 2.1')
RCV_NM: (wb_get_user_nm(RCV_NO))
SND_RSLT: 'SND_WAIT'
STTS: '0'
PUSH_ACTION_URL: if Table 9>>DV_CD = 6 to 15 >> 
json string({
DV_CD: DV_CD (Table 9),
REG_SEQ_NO: REG_SEQ_NO (Table 9),
COMNT_SEQ_NO: COMNT_SEQ_NO (Table 9),
v2024.1.24 added
BLTB_CD: BLTB_CD (Table 9)
}) v2024.1.26 updated
SND_TITL: 'WABOOKS'
SND_CONT: 
 if DV_CD(Table 9) = '12' >> SND_NM(Table 9) +" posted an article: "+ CONT (Table 9)
```

---

<a id="wabooks_mrec_bltb_0004_c002"></a>

## `WABOOKS_MREC_BLTB_0004_C002` — Create an approval post (expense approval)

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_BLTB_0004_C002` |
| Sheet name (source) | `WABOOKS_MREC_BLTB_0004_C002` |
| Sheet title (원문) | Create Approval Post & update draft Approval |
| Business area (업무) | Create new Approval and update draft approval(COMM) |
| Screen (source index) | Home \| My Posts \| Approval > New Approval |
| Legacy web reference | `comm_0192_001_c002` |
| Legacy tables (테이블) | 1. EXPT_RESOL_MSTR <br> 2. EXPT_RESOL_DETL <br> 3. EXPT_RESOL_DETL_INF <br> 4. EXPT_RESOL_FILE <br> 5. BUSA_DOC_MSTR <br> 6. BUSA_DOC_MSTR_HIS <br> 7. SALY_BOOK_MSTR <br> 8. EXPT_RESOL_APPR_LINE <br> 9. BLTB_POST_INFM <br> 10. BLTB_POST_FILE <br> 11. BLTB_USER_NEW_POST_INFM <br> 12. BLTB_DRAFT_POST_MSTR <br> 13. BLTB_DRAFT_POST_DETL <br> 14. BLTB_DRAFT_USER_APPR(Hold) <br> 15. BLTB_DRAFT_POST_FILE <br> 16. BLTB_NOTI_INFM <br> 17. BLTB_NOTI_CONT_INFM <br> 18. USER_BLTB_CONN_INFM <br> 19. EXPT_RESOL_APPRVR_INFM <br> 20. BLTB_DRAFT_POST_APPRVR_INFM <br> 21. PUSH_NOTICE_INFM <br> 22. MSG_SND_HIS <br> 23. PUSH_DEVICE_MSTR |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | `POST /api/v1/payment/expense-payment` |
| Coverage | **PARTIAL** |
| Last spec revision | Updated v2024.1.32 |

**Delta.** The refactor can create the expense request and set its approval line (`PUT .../approval-line`), but it does not create the accompanying board post, mentions or comments.

### Input — 34 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `APPR_DV` | C | 2 | Y | 지출결의구분 | **see note 1 below** |
| `PAY_DATE` | C | 8 |  | 지급예정일 | 1번 테이블 Insert |
| `TOT_PAY_LOC_AMT` | N | 20.6 |  | Total Pay Local Amount | 1번 테이블 Insert <br> resl_0002_001_r001 의 TOT_KHR_AMT 를 그대로 Insert <br> v2024.1.32 <br> INPUT(TOT_PAY_LOC_AMT) >> Table 1(TOT_PAY_KHR_AMT) |
| `TOT_PAY_USD_AMT` | N | 20.6 |  | 결의총USD금액 | 1번 테이블 Insert <br> resl_0002_001_r001 의 TOT_USD_AMT 를 그대로 Insert |
| `SAL_REG_SEQ_NO` |  | 20 |  | 급여등록일련번호 | 지출결의구분이 20(급여)일 때만 필수. |
| `PAYM_AMT_BYDF` | N | 20.6 |  | 차인지급액 | 지출결의구분이 20(급여)일 때만 필수. <br> 2번 테이블 AMT 컬럼에 들어가야 함. <br> 구하는 방법은 resl_0002_001_r001 참조. |
| `PAY_REQ_DATE` | C | 8 |  | 지급요청일 | (2021/09/23) 추가 <br> 1번 테이블 Insert |
| `REF_DATA_CNT` | N | 10 | Y | 연관데이터건수 | (2022/04/26) 추가 <br> resl_0002_001_r001 >> TOT_CNT 값 Bypass. <br> 1번 테이블 Insert |
| `APLY_EXCH_RATE` | N | 20.6 |  | Apply Exchange Rate | V2023.1.0.29 <br> Table 1, <br> APPR_DV = '00' : Required <br> (Now It use only in VN version) |
| `REC1` | R |  |  | 목록1 | POST_APPR_DV: 10 >> required ** don't insert table 2 if POST_ANTH_BLTB_YN is 'Y' v2023.1.0.28 <br> removed v2023.1.0.31 |
| ↳ `TRCO_KEY` | C | 20 |  | 거래처키 | APPR_DV가 00, 10, 30, 40, 50일 때만 2번 테이블 Insert |
| ↳ `TRCO_CMNM` | C | 200 |  | 거래처회사명 | (10/13) 추가 APPR_DV가 00, 10, 30, 40, 50일 때만 2번 테이블 Insert |
| ↳ `TRCO_CMENM` | C | 200 |  | 거래처회사영문명 | (10/13) 추가 APPR_DV가 00, 10, 30, 40, 50일 때만 2번 테이블 Insert |
| ↳ `CCY_CD` | C | 3 |  | 통화코드 | APPR_DV가 00, 10, 30, 40, 50일 때만 2번 테이블 Insert |
| ↳ `AMT` | N | 20.6 |  | 금액 | APPR_DV가 00, 10, 30, 40, 50일 때만 2번 테이블 Insert |
| ↳ `BUSA_USAG_CD` | C | 20 |  | 매입매출용도코드 | APPR_DV가 00, 10, 30, 40, 50일 때만 2번 테이블 Insert |
| ↳ `MEMO` | C | 200 |  | 메모 | APPR_DV가 00, 10, 30, 40, 50일 때만 2번 테이블 Insert |
| ↳ `DOC_ID` | C | 50 |  | 문서ID | APPR_DV가 10, 30, 40, 50일 때만 2번 테이블 Insert |
| ↳ `DOC_TYPE` | C | 1 |  | 문서종류 | (10/06) 추가 <br> APPR_DV가 10, 30, 40, 50일 때만 2번 테이블 Insert |
| `REC2` | R |  |  | 목록2 |  |
| ↳ `NOTE` | C | 255 |  | 비고 | 4번 테이블 Insert |
| `DRAFT_POST_SEQ_NO` | N | 15 |  | Dratf post sequence number | It required when Open as draft |
| `REC5` | R |  |  | Record 5 | File for Delete <br> Open as draft |
| ↳ `FILE_ID` | C | 100 |  | 파일ID |  |
| `REC6` | R |  | Y | Record 6 | for approver |
| ↳ `APPRVR_ID` | C | 100 |  | 원본파일명 | **see note 2 below** |
| ↳ `APPRVR_NM` | C | 100 |  | 저장파일명 |  |
| `POST_TITLE` | C | 200 |  | Post title | TITL= POST_TITLE |
| `POST_APPR_DV` |  |  |  | Post Approval Division | Table 9, 12 <br> '00': Simple Approval, '10': Expense Approval |
| `POST_CONT` | C | 2000 | Y | Post content | CONT= POST_CONT |
| `POST_DRAFT_YN` | C | 1 | Y | Post draft YN | Y: Save draft, N: Post or Request |
| `REC3` | R |  |  | Record 3 | 4번 테이블 Insert <br> 파일 업로드 후 파일ID, 원본파일명, 저장파일명, 파일크기 추출하여 4번 테이블에 Insert <br> 파일저장경로 <br> Move from Temp directory to Original directory then delete from Temp directory. <br> Temp: /WAS_DATA/file/{biz_key}/comm_0192_001_c004/ <br> Original: /WAS_DATA/file/{biz_key}/resl_0002_001_c001/ |
| ↳ `FILE_NM` | C | 500 | Y | 파일명 |  |
| ↳ `FILE_DATA` | B | 4000 | Y | 파일데이터 |  |

**Note 1 — `APPR_DV`** (verbatim from the sheet):

```text
(01/16) 필드명 수정. TRSC_DT >> APPR_DV
1번 테이블 Insert
v2024.1.35 added
00 : Manually
10 : Expense/Bill
20 : 급여 (10/06 added) 
30 : Expense (Receipt)
40 : Bill (KH version only)
50 : Vietnam e-Invoice (2022/12/26 added) (VN version only)
60 : Cash Advances Request (v2023.1.10 added)
70 : ‘Simple’( v2023.1.0.19 added)
```

**Note 2 — `APPRVR_ID`** (verbatim from the sheet):

```text
if INPUT ('POST_DRAFT_YN') == 'N'
      insert to table 19 ('EXPT_RESOL_APPRVR') 
      with value from INPUT and other value like below 
            -BIZ_KEY : from Session
            -APPRVR_STTS :   if first row then 'P'  else 'W'   2024.1.34 added 
            -APPR_SEQ_NO :  value from table 1 ('EXPT_RESOL_MSTR') 

        insert to table 20 ('BLTB_DRAFT_POST_APPRVR_INFM')   
        with value from INPUT and other value like below 
            -BIZ_KEY : from Session
            -BLTB_CD : REC4 >>  BLTB_CD
            -BLTB_CD : '000000000000000' add   v2023.1.0.31
```

### Output — 0 fields

_No output payload beyond the common `RSLT_CD` / `RSLT_MSG` envelope._

### Process rules (verbatim from the sheet)

Input section:

```text
1. if POST_DRAFT_YN = N >>Table 1 to 11: Insert 
 READ_YN: "N"(Table 11)

1. if DRAFT_POST_SEQ_NO !=null and POST_DRAFT_YN = Y >> Table 12,13 & 15: Patch

else if DRAFT_POST_SEQ_NO !=null and POST_DRAFT_YN = N >> Table 1 to 11(Number 1 Green): Insert,
Table 16, 17: Insert(Number 2 Blue), and Table 12,13 & 15 : Delete 

else if DRAFT_POST_SEQ_NO =null and POST_DRAFT_YN = Y >> Table 12,13 & 15: Insert

else if DRAFT_POST_SEQ_NO =null and POST_DRAFT_YN = N >> Table 1 to 11(Number 1 Green): Insert 
and Table 16, 17, 21, 22: Insert(Number 2 Blue) 
v2024.1.15 (add table 21,22)
```

Input section:

```text
1 **Create Approval sample from action:  resl_0002_001_c001
※ 추가 Insert 데이터.
Table 1, 9 and 12:
REG_CHNL: 'MOB'
PC_MOD_YN: 'Y'

- 1번 테이블
BIZ_KEY : (Session)
APPR_STS : 2
DRAFT_DATE : YYYYMMDD(SYSDATE)
DRAFT_TIME : HHMMSS(SYSDATE)
DRAFT_USER_ID : Session(USER_ID)
DRAFT_USER_NM : Session(USER_NM)
DRAFT_USER_POS_NM : Session(JBCL_NM)
DRAFT_USER_DEPT_CD : NULL
DRAFT_USER_DEPT_NM : Session(DEPT_NM)
DRAFT_USER_SITE_CD : Session(BIZ_KEY)
DRAFT_USER_SITE_NM : Session(CMPN_NM)
DOC_NO : 결의서_YYYYMM_001 (3자리 Sequence)
(08/24 변경)
DOC_NO : VO_YYYYMM_001 (3자리 Sequence)
DOC_NM : APPR_DV에 따라서 아래와 같이 넣어줘야 함.
00 : '지출결의서'
10 : '지출결의서(비용)'
20 : '지출결의서(급여)'
50 : 'Expenses e-invoice Approval'
DEL_YN : 'N'
HAND_REG_YN : APPR_DV가 00이면 Y, 그 외에는 N
REG_ID, MOD_ID : Session
REG_DTM, MOD_DTM : SYSDATE

(06/18) 추가
APPR_BOX_USE_YN : Environment num 00014 Value(From Session)
APPR_BOX_DISP_CNT : Environment num 00015 Value(From Session)
APPR_DAT_DISP_YN : Environment num 00016 Value(From Session)
CMPN_LOGO_DISP_YN : Environment num 00017 Value(From Session)
REG_DISP_YN : Environment num 00018 Value(From Session)
CMPN_LOGO : Company Logo URL from session.
USER_NM : Logined user name from session.

(v2023.1.0.7) 추가
PAY_YN : 'N' Table 2

(APPR_DV가 20인 경우. '00', '10인 경우 위의 'REC1' 참조)

CCY_CD : TOT_PAY_KHR_AMT의 값이 있으면 'KHR', TOT_PAY_USD_AMT의 값이 있으면 'USD'.
AMT : PAYM_AMT_BYDF(Input)
DOC_ID : SAL_REG_SEQ_NO(Input)
REG_ID : Session
REG_DTM : SYSDATE

- 3번 테이블
(APPR_DV가 20인 경우에만 Insert)
SALY_BOOK_DETL_INF 테이블을 SELECT하여 그 B46값을 그대로 Matching되는 컬럼에 INSERT.
WHERE 조건은 BIZ_KEY(Session), SAL_REG_SEQ_NO(Input)
그 외에는,
APPR_DETL_SEQ_NO : '1'
REG_ID : Session
REG_DTM : SYSDATE

- 5번 테이블
APPR_DV가 10, 30, 40, 50인 경우 5번 테이블 APPR_SEQ_NO 컬럼에 지출결의번호 Update 처리.
WHERE 조건은 BIZ_KEY, USER_ID, DOC_ID

- 7번 테이블
APPR_DV가 20인 경우 7번 테이블 APPR_SEQ_NO 컬럼에 지출결의번호 Update 처리.
WHERE 조건은 BIZ_KEY, SAL_REG_SEQ_NO
```

Input section:

```text
(06/18) 추가
- 8번 테이블
APPR_SEQ_NO : Num 1 table inserted Sequence.
APPR_LINE_NO : 1 ~ 5
JBCL_NM : Environment num 00029 ~ 00033 Value(From Session)
EMPL_NM : Environment num 00034 ~ 00038 Value(From Session)

- update 
5번 테이블(v2023.1.0.27) 
APPR_DV가 10, 30, 40, 50인 경우 5번 테이블 APPR_SEQ_NO and CONT(=REC1.MEMO)컬럼에 지출결의번호 Update 처리.
WHERE 조건은 BIZ_KEY, USER_ID, DOC_ID

Table 9: Insert
READ_YN: "N"
APPR_SEQ_NO: (APPR_SEQ_NO of Table 1)

Table 11: Insert
READ_YN: "N" **NOTE : for  Apporval Request
2024.1.34 added 
 N1.  get all admin users of the current company 
  , approver of the current approval
   and exclude requestor
   
   =====================================
   select   
  T.apprvr_id
  , wb_get_user_nm (T.apprvr_id) as  apprvr_nm
  from (
   select  
      user_id   as apprvr_id
    from busi_admn_infm 
    where biz_key = BIZ_KEY ('session')
   UNION 
    select   
      apprvr_id  as apprvr_id
    from expt_resol_apprvr_infm 
    where biz_key  =  BIZ_KEY ('session')
    and appr_seq_no = appr_seq_no ('INPUT')
     and apprvr_stts  in  ('P')
  ) as T
  where  apprvr_id <> USER_ID ('session')
 
N2. Table 16:  Insert notification.
 sender_id: (session user_id)
 sender_name: (wb_get_user_nm(sender_id))
 rcv_id: (proc N1   'apprvr_id')
 rcv_nm: (proc N2  'apprvr_nm' ))
 noti_dt: YYYYMMDD
 noti_tm: HHMMSS bltb_0001_003.act?POST_SEQ_NO={POST_SEQ_NO}&BLTB_CD={BLTB_CD}
 actv_yn: 'N'
 Table 19: Insert by languages
 - VN version
 LANG_CD: 'EN', 'KO','VN'
 TITL: 'Added a new approval.'
 CONT:
 - KH version 
 LANG_CD: 'EN', 'KO','KH'
 TITL: 'Added a new approval.'
 CONT:
```

Input section:

```text
2 Notification:   2024.1.34 added
Table 18: Find all the users on current board.
Table 16:  Insert notification to all users on current board.
sender_id: (session user_id)
sender_name: (wb_get_user_nm(sender_id))
rcv_id: (table 18 user_id)
rcv_nm: (wb_get_user_nm(rcv_id))
noti_dt: YYYYMMDD
noti_tm: HHMMSS
noti_url: depend on POST_TYPE_CD(input)
- '01': bltb_0001_002.act?POST_SEQ_NO={POST_SEQ_NO}&BLTB_CD={BLTB_CD}
- '02': bltb_0001_003.act?POST_SEQ_NO={POST_SEQ_NO}&BLTB_CD={BLTB_CD}
actv_yn: 'N'
Table 19: Insert by languages
- VN version
LANG_CD: 'EN', 'KO','VN'
TITL: 'Added a new approval.'
CONT:
- KH version 
LANG_CD: 'EN', 'KO','KH'
TITL: 'Added a new approval.'
CONT: v2024.1.15 added
PUSH Notification on Mobile
9.0.Table 18: Find all the users on current board.
9.1.Table 23: Find each user by PK(BIZ_KEY, USER_ID) != null >> Insert below table 21 and 22
9.1.0.Table 21>>  Insert notification to all users on current board(Table 18)
BIZ_KEY : BIZ_KEY (Session)
USER_ID: USER_ID (Table 18)
SEQ_NO : Generate Sequence Base on BIZ_KEY, USER_ID 
SND_DATE : YYYYMMDD
SND_TIME : HHMMSS
SND_ID: USER_ID (Table 18)
SND_NM: ((wb_get_user_nm(SND_ID)))
REG_SEQ_NO: POST_SEQ_NO (After insert Table 9 )
if POST_APPR_DV = '00' >>
   DV_CD: '11' (11.Simple Approval request from specific user)
   CONT: POST_TITLE ('INPUT')
else if POST_APPR_DV = '01' >>
   DV_CD: '6' (6.Expense Approval request from specific user)
   CONT: JSON String {CCY_CD, AMT} (CCY('REC1.CCY_CD'), AMT ('REC1.AMT'))
v2024.1.24 added
BLTB_CD : '000000000000000'

9.1.1.Table 22:
REG_SEQ_NO: generate Sequence
BIZ_KEY: (Session)
USER_ID: USER_ID ('Table 18')
MSG_TYPE: 'P'
RCV_NM: ((wb_get_user_nm(USER_ID)))
RCV_NO:  USER_ID ('Table 18')
SND_RSLT: 'SND_WAIT'
STTS: '0'
PUSH_ACTION_URL: if table 21>>DV_CD = 6 to 15 >> 
json string({
DV_CD: DV_CD (Table 21),
REG_SEQ_NO: REG_SEQ_NO (Table 21),
COMNT_SEQ_NO: COMNT_SEQ_NO (Table 21),
v2024.1.24 added
BLTB_CD : '000000000000000'
}) v2024.1.26 updated
SND_TITL: 'WABOOKS'
SND_CONT: 
if DV_CD(Table 21) = '11' >>
   SND_NM(Table 21) +" requested an approval: "+ CONT (Table 21)
else if DV_CD(Table 21) = '6'>>
   SND_NM(Table 21) +" requested an expense approval of "+ REC1.CCY_CD + " " + REC1.AMT (if REC1 length > 1 use ''," before next index)
```

---

<a id="wabooks_mrec_bltb_0004_r001"></a>

## `WABOOKS_MREC_BLTB_0004_R001` — Mentionable member list

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_BLTB_0004_R001` |
| Sheet name (source) | `WABOOKS_MREC_BLTB_0004_R001` |
| Sheet title (원문) | Inquiry Mention members |
| Business area (업무) | List Mention(COMM) |
| Screen (source index) | New Approval \| Edit Approval |
| Legacy web reference | `comm_0192_009_r001` |
| Legacy tables (테이블) | 1. USER_BLTB_CONN_INFM <br> 2. USER_INFM |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | `GET /api/v1/users` |
| Coverage | **PARTIAL** |
| Last spec revision | — |

**Delta.** A company-wide user list exists; there is no per-board scope.

### Input — 2 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `USER_NM` | C | 100 |  | User Name |  |
| `BLTB_CD` | C | 15 | Y | Bulletin board code |  |

### Output — 5 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `REC` | R |  |  | Record |  |
| ↳ `USER_NM` | C | 15 |  | Bulletin board code |  |
| ↳ `USER_ID` | C | 100 |  | Notification sequence number |  |
| ↳ `USER_IMG_URL` | C | 200 |  | User Image Url | If USER_IMG is not null >> szon_0012_003_r002.act?USER_ID={USER_ID} |
| ↳ `USER_IMG` | C | 500 |  | User Image |  |

### Process rules (verbatim from the sheet)

Input section:

```text
*Mention member on each board
1. Table 1 join table 2: Select on user_id
Where
BIZ_KEY: (Session)
```

---

<a id="wabooks_mrec_bltb_0004_r002"></a>

## `WABOOKS_MREC_BLTB_0004_R002` — Approver list

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_BLTB_0004_R002` |
| Sheet name (source) | `WABOOKS_MREC_BLTB_0004_R002` |
| Sheet title (원문) | Inquiry Approver |
| Business area (업무) | List Approver(COMM) |
| Screen (source index) | New Approval \| Edit Approval |
| Legacy web reference | `comm_0192_001_r001` |
| Legacy tables (테이블) | 1.USER_BUSI_CONN_INFM <br> 2. USER_INFM |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | `GET /api/v1/settings/approval-line` |
| Coverage | **PARTIAL** |
| Last spec revision | — |

**Delta.** Also `GET /api/v1/payment/approval-box/{apprSeqNo}/approvers`.

### Input — 2 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `BLTB_CD` | C | 15 | Y | Bulletin board code |  |
| `USER_NM` | C |  | Y | 사용자명 | ilike %""% |

### Output — 6 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `REC1` | R |  |  | 목록1 |  |
| ↳ `USER_ID` | C | 100 |  | 사용자ID |  |
| ↳ `USER_NM` | C | 100 |  | 사용자명 |  |
| ↳ `CLPH_NO` | C | 100 |  |  | v2023.1.0.30 added |
| ↳ `USER_IMG_URL` | C | 200 |  | User Image Url | If USER_IMG is not null >> szon_0012_003_r002.act?USER_ID={USER_ID} |
| ↳ `USER_IMG` | C | 500 |  | User Image |  |

### Process rules (verbatim from the sheet)

Input section:

```text
v2023.1.0.31 added
table 1 join 2 
with USER_ID   
where BIZ_KEY('table1')('session') and DEL_YN = 'N' ('table 1')
```

---

<a id="wabooks_mrec_bltb_0004_r003"></a>

## `WABOOKS_MREC_BLTB_0004_R003` — Attachable expense documents (bill / e-invoice / receipt)

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_BLTB_0004_R003` |
| Sheet name (source) | `WABOOKS_MREC_BLTB_0004_R003` |
| Sheet title (원문) | Inquiry Expenses Approval |
| Business area (업무) | List Expenses (VN>Expense E-invoice,Reciept(MOB); KH>Bill, Reciept(MOB)) |
| Screen (source index) | New Approval |
| Legacy web reference | `comm_0192_001_r002` |
| Legacy tables (테이블) | 1. BUSA_DOC_MSTR <br> 2. EXPT_RESOL_MSTR <br> 3. SALY_BOOK_MSTR |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | `GET /api/v1/payment/card-transactions/links/receipts` |
| Coverage | **PARTIAL** |
| Last spec revision | Update v2024.1.16 |

**Delta.** Combine with `/links/e-invoices`. Missing the payroll source (`SALY_BOOK_MSTR`) and the three `TOT_CNT_BILL` / `_EINV` / `_RCPT` counters.

### Input — 6 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `SRCH_WD` | C | 100 |  | 검색어 | TRCO_CMNM, TRCO_CMENM LIKE 검색 |
| `EXPT_TYPE` | C | 1 | Y | 문서종류 | A:ALL, P:Payroll <br> KH version>> B:Bill, R: Receipt <br> VN version>> E:E-Invoice R: Receipt |
| `FROM_DT` | C | 8 | Y | 검색일자 From | EXPT_TYPE != P >> ISSU_DT BETWEEN FROM |
| `TO_DT` | C | 8 | Y | 검색일자 To | EXPT_TYPE != P >> ISSU_DT BETWEEN TO |
| `PAGE_NO` | N | 15 | Y | 페이지번호 | DF: 1 |
| `PAGE_PER_CNT` | N | 15 | Y | 페이지별건수 | DF: 10 |

### Output — 18 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `TOT_CNT_BILL` | N | 15 |  | Total Count Bill | v2024.1.16 added <br> EXPT_TYPE = 'B' <br> Required for KH version |
| `TOT_CNT_EINV` | N | 15 |  | Total Count E-Invoice | v2024.1.16 added <br> EXPT_TYPE = 'E' <br> Required for VN version |
| `TOT_CNT_RCPT` | N | 15 |  | Total Count Receipt | v2024.1.16 added <br> EXPT_TYPE = 'R' <br> Required for KH and VN version |
| `REC` | R |  |  | Record |  |
| ↳ `DOC_ID` | C | 50 |  | 문서ID | Table 1 |
| ↳ `ISSU_DT` | C | 8 |  | 발행일자 | Table 1 |
| ↳ `DOC_TYPE` | C | 1 |  | 문서종류 | Table 1 |
| ↳ `TRCO_KEY` | C | 20 |  | 거래처키 | Table 1 |
| ↳ `TRCO_CMNM` | C | 200 |  | 거래처회사명 | Table 1 |
| ↳ `TRCO_CMENM` | C | 200 |  | 거래처회사영문명 | Table 1 |
| ↳ `TRCO_TRSC_CCY_CD` | C | 3 |  | 거래처거래통화코드 | Table 1 |
| ↳ `TOTL_AMT` | N | 20.6 |  | 총금액 | Table 1 |
| ↳ `BUSA_USAG_CD` | C | 20 |  | 매입매출용도코드 | Table 1 |
| ↳ `USAG_NM` | C | 100 |  | 용도명 | Table 1 |
| ↳ `COLOR` | C | 5 |  |  | **see note 1 below** |
| ↳ `MEMO` | C | 200 |  |  |  |
| ↳ `TRCO_TAX_ID` | C | 40 |  | Contacts Tax ID | Table 1 |
| ↳ `APPR_DV` | C | 2 |  | Approval Divition | DOC_TYPE <br> B: 40, E: 30, R: 10, P: 20 |

**Note 1 — `COLOR`** (verbatim from the sheet):

```text
Note: EXPT_TYPE = P >> Don't have MEMO and COLOR
- EXPT_TYPE =  B >> 
PAGE_ID = prch_0002_001
- EXPT_TYPE =  R >> 
PAGE_ID = prch_0010_001
- EXPT_TYPE =  E >> 
PAGE_ID = einv_0001_001

wb_get_memo_color(Z.BIZ_KEY,{PAGE_ID},Z.DOC_ID,'','','','','','','','') AS COLOR
wb_get_memo_cont(Z.BIZ_KEY,{PAGE_ID},Z.DOC_ID,'','','','','','','','') AS MEMO
```

### Process rules (verbatim from the sheet)

Input section:

```text
if EXPT_TYPE =  A >>
union all query EXPT_TYPE = E,B,R,P
Where 
BIZ_KEY: (Session) 
- select expenses not yet do approval 
if EXPT_TYPE =  E or B or R >>
Table 1 as b
(select a.doc_no from table2 a where 1=1 and b.biz_key = a.biz_key and a.appr_seq_no = c.appr_seq_no) isnull

if EXPT_TYPE =  P >>
Table 3 as b
(select a.doc_no from table2 a where 1=1 and b.biz_key = a.biz_key and a.appr_seq_no = c.appr_seq_no) isnull

- EXPT_TYPE =  E >> Ref: einv_0001_001_r001
Table 1
DOC_TYPE : 'B'
HAND_REG_YN : 'N'
BUSA_DOC_DETL에 동일한 BIZ_KEY, DOC_ID가 존재하는 Row.
TRCO_KEY != '99999999999999999999' - EXPT_TYPE =  B >> Ref: prch_0002_001_r001
Table 1
DOC_TYPE: 'B'
PROV_STTS NOT IN ('EE', 'VD')
HAND_REG_YN : 'Y
- EXPT_TYPE =  R >> Ref: prch_0010_001_r001
Table 1
DOC_TYPE : 'E'
PROV_STTS NOT IN ('EE', 'VD')
HAND_REG_YN : 'Y'
- EXPT_TYPE =  P >>  Ref: saly_0001_001_r001
Table 3 
DEL_YN : 'N'
SAL_DV in('A','C','B')
```

Output section:

```text
**Note: OUTPUT for Payroll is not details on Figma(Hold)
```

---

<a id="wabooks_mrec_bltb_0004_u001"></a>

## `WABOOKS_MREC_BLTB_0004_U001` — Edit an article

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_BLTB_0004_U001` |
| Sheet name (source) | `WABOOKS_MREC_BLTB_0004_U001` |
| Sheet title (원문) | Update Article |
| Business area (업무) | Edit Article(COMM) |
| Screen (source index) | Edit Article |
| Legacy web reference | `comm_0192_013_u001` |
| Legacy tables (테이블) | 1. BLTB_POST_INFM <br> 2. BLTB_POST_FILE <br> 3. BLTB_NOTI_INFM <br> 4. BLTB_NOTI_CONT_INFM <br> 5. USER_BLTB_CONN_INFM |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | — (none) |
| Coverage | **MISSING** |
| Last spec revision | — |

### Input — 13 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `BLTB_CD` | C | 15 | Y | Bulletin board code | Table 1 |
| `POST_SEQ_NO` | N | 15 | Y | Post sequence number | Table 1 |
| `POST_TITLE` | C | 200 |  | Post title | Table 1 |
| `POST_TYPE_CD` | C | 2 |  | Post type code | 01' : Article |
| `POST_STTS` | C | 2 |  | Post status | PL : Public , PV:Private <br> if POST_STTS = PV >> <br> table 1 join table 2 |
| `PIN_POST_TYPE` | C | 2 |  | Pin post type | Table 1 |
| `PIN_POST_DUE_DT` | C | 8 |  | Pin post due date | Table 1 |
| `POST_CONT` | C | 2000 |  | Post content | Table 1 |
| `REC` | R |  |  | Record | File delete <br> Original Directory : /WAS_DATA/file/BIZ_KEY/comm_0192_001_c001 |
| ↳ `FILE_ID` | C | 100 |  | 파일ID |  |
| `REC2` | R |  |  | Record 2 | File add new <br> Move from Temp directory to Original directory then delete from Temp directory. <br> Temp: /WAS_DATA/file/{biz_key}/comm_0192_013_c001/ <br> Original: /WAS_DATA/file/{biz_key}/comm_0192_001_c001/ |
| ↳ `FILE_NM` | C | 500 | Y | 파일명 |  |
| ↳ `FILE_DATA` | B | 4000 | Y | 파일데이터 |  |

### Output — 0 fields

_No output payload beyond the common `RSLT_CD` / `RSLT_MSG` envelope._

### Process rules (verbatim from the sheet)

Input section:

```text
1. Notification 'Pinned' when user pinned the post
Table 1: Read 
if PIN_POST_DUE_DT(Table 1) = null and PIN_POST_DUE_DT != null >> 
Table 5: Find all the users on current board.
Table 3:  Insert notification to all users on current board.
sender_id: (session user_id)
sender_name: (wb_get_user_nm(sender_id))
rcv_id: (table 5 user_id)
rcv_nm: (wb_get_user_nm(rcv_id))
noti_dt: YYYYMMDD
noti_tm: HHMMSS
noti_url: bltb_0001_002.act?POST_SEQ_NO={POST_SEQ_NO}&BLTB_CD={BLTB_CD}
actv_yn: 'N'
Table 4: Insert by languages
- VN version
LANG_CD: 'EN', 'KO','VN'
TITL: ''Article' -> 'Pinned', Status has been updated.'
CONT:
- KH version 
LANG_CD: 'EN', 'KO','KH'
TITL: ''Article' -> 'Pinned', Status has been updated.'
CONT:
```

---

<a id="wabooks_mrec_bltb_0004_u002"></a>

## `WABOOKS_MREC_BLTB_0004_U002` — Edit a pending approval

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_BLTB_0004_U002` |
| Sheet name (source) | `WABOOKS_MREC_BLTB_0004_U002` |
| Sheet title (원문) | Update Pending Approval |
| Business area (업무) | Edit Approval(COMM) |
| Screen (source index) | Edit Simple Approval \| Edit Expense Approval |
| Legacy web reference | `comm_0192_013_u002` |
| Legacy tables (테이블) | 1. BLTB_POST_INFM <br> 2. EXPT_RESOL_PAY_EVID_FILE <br> 3. EXPT_RESOL_APPRVR_INFM <br> 4. USER_BLTB_CONN_INFM |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | `PUT /api/v1/payment/approval-box/{apprSeqNo}/edit` |
| Coverage | **PARTIAL** |
| Last spec revision | — |

### Input — 15 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `BLTB_CD` | C | 15 | Y | Bulletin board code | Table 1 |
| `POST_SEQ_NO` | N | 15 | Y | Post sequence number | Table 1 |
| `POST_TITLE` | C | 200 |  | Post title | Table 1 |
| `POST_CONT` | C | 2000 |  | Post content | Table 1 |
| `REC1` | R |  |  | Record 1 | Table 3: Add new Approver |
| ↳ `APPRVR_ID` | C | 100 |  | 원본파일명 | if APPRVR_STTS = 'P' >> <br> insert to table 3 <br> with value from INPUT and other value like below <br> -BIZ_KEY : from Session <br> -APPRVR_STTS : 'P' |
| ↳ `APPRVR_NM` | C | 100 |  | 저장파일명 |  |
| `REC2` | R |  |  | Record 2 | Table 3: Delete Approver |
| ↳ `APPRVR_ID` |  |  |  |  | if APPRVR_STTS = 'P' >> <br> Detele from table 3 |
| `REC3` | R |  |  | Record 3 | (Follow api: resl_0003_001_u003) <br> Table 2: Only Add new file <br> File add new <br> Move from Temp directory to Original directory then delete from Temp directory. <br> Temp: /WAS_DATA/file/{biz_key}/comm_0192_013_c001/ <br> Original: /WAS_DATA/file/{biz_key}/resl_0003_001_u001/ |
| ↳ `FILE_NM` | C | 500 | Y | 파일명 |  |
| ↳ `FILE_DATA` | B | 4000 | Y | 파일데이터 |  |
| `REC4` | R |  |  | Record 4 | Delete File: Table 5 <br> if FIle Type = 'EXPT_RESOL_PAY_EVID_FILE' >> <br> allow delete file |
| ↳ `FILE_TYPE` | C | 100 |  | File Type |  |
| ↳ `FILE_ID` | C | 100 |  | 파일ID |  |

### Output — 0 fields

_No output payload beyond the common `RSLT_CD` / `RSLT_MSG` envelope._

### Process rules (verbatim from the sheet)

Input section:

```text
v2023.1.0.23
1. Validate User Admin permission
Table 4: Select 
Where
BIZ_KEY: (Session)
USER_ID: (Session)
BLTB_CD
if BLTB_USER_TYPE  != 'A' >> throws an exception error "No permission"

2.Validate post owner permission
Table 1: Select
Where
BIZ_KEY: (Session)
USER_ID: (Session)
BLTB_CD
POST_SEQ_NO
if return null >> throws an exception error "Not found"
else  
    if REG_ID != (USER_ID on session) >> throws an exception error "No permission"

3. Allow to update if APPRVR_STTS = 'P' >>
- Update Table 1: BIZ_KEY : session , 
- Insert table 2, 
- Insert and delete table 3 .
```

---

<a id="wabooks_mrec_bltb_0005_r001"></a>

## `WABOOKS_MREC_BLTB_0005_R001` — Article detail

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_BLTB_0005_R001` |
| Sheet name (source) | `WABOOKS_MREC_BLTB_0005_R001` |
| Sheet title (원문) | Article Details |
| Business area (업무) | Article Details(COMM) |
| Screen (source index) | Post Details |
| Legacy web reference | `bltb_0001_002_r001` |
| Legacy tables (테이블) | 1. BLTB_POST_INFM <br> 2. BLTB_POST_FILE <br> 3. BLTB_USER_NEW_POST_INFM <br> 4. BLTB_COMNT_INFM <br> 5. USER_BLTB_CONN_INFM <br> 6. BLTB_NOTI_INFM <br> 7. USER_INFM |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | — (none) |
| Coverage | **MISSING** |
| Last spec revision | Update v2024.1.11 |

### Input — 2 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `POST_SEQ_NO` | N | 15 | Y | Post sequence number |  |
| `BLTB_CD` | C | 15 |  | Bulletin board code |  |

### Output — 22 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `BLTB_CD` | C | 15 |  | Bulletin board code |  |
| `POST_SEQ_NO` | N | 15 |  | Post sequence number | Table 1 |
| `POST_TITLE` | C | 200 |  | Post title |  |
| `POST_TYPE_CD` | C | 2 |  | Post type code | 01' : Article , '02':Approval |
| `POST_STTS` | C | 2 |  | Post status | PL : Public , PV:Private <br> if POST_STTS = PV >> <br> table 1 join table 2 |
| `REG_NM` | C | 100 |  | Register Name | Table 7: USER_NM where USER_ID: REG_ID |
| `REG_ID` | C | 15 |  | 등록자ID |  |
| `REG_DTM` | C | 2 |  | 등록일시 |  |
| `REG_IMG_URL` | C | 200 |  | Register Image Url | Table 7: USER_IMG is not null where USER_ID: REG_ID >> {Base URL}/szon_0012_003_r002.act?USER_ID={REG_ID} |
| `USER_IMG` | C | 500 |  | User Image | v2024.1.11 added >> Table 7 |
| `PIN_POST_TYPE` | C | 2 |  | Pin post type |  |
| `PIN_POST_DUE_DT` | C | 8 |  | Pin post due date |  |
| `POST_CONT` | C | 2000 |  | Post content |  |
| `TOT_CMT` | N | 15 |  | Total Comment | Table 4 |
| `READ_YN` | C | 1 |  | Read Y/N | Table 3: N: Show Icon 'N' |
| `DEL_YN` | C | 1 |  | Delete Y/N | Table 5: Admin and Post owner allow to delete |
| `REC` | R |  |  | Record |  |
| ↳ `FILE_ID` | C | 100 |  | 파일ID |  |
| ↳ `ORG_FILE_NM` | C | 500 |  | 원본파일명 |  |
| ↳ `SAVE_FILE_NM` | C | 500 |  | 저장파일명 |  |
| ↳ `FILE_SIZE` | C | 100 |  | 파일크기 |  |
| ↳ `FILE_URL` | C | 200 |  | File Url | {Base URL}/comm_0192_013_r003.act?{Datetime}&BLTB_CD={BLTB_CD}&POST_SEQ_NO={POST_SEQ_NO}&FILE_ID={FILE_ID} |

### Process rules (verbatim from the sheet)

Input section:

```text
1. Table 1:
Where 
BIZ_KEY: (Session)
POST_TYPE_CD: '01'
POST_SET_NO: (input)
BLTB_CD: (input)

2. Remove 'N' icon if it is a first view detail for each user
Table 3: Select
Where
BIZ_KEY: (Session)
POST_TYPE_CD: '01'
PK
if READ_YN = 'N' >> 
Table 3: Update READ_YN = 'Y' 
Table 6: update ACTV_YN = 'Y'
Where 
BIZ_KEY: (Session)
BLTB_CD: (Input)
NOTI_SEQ_NO
```

---

<a id="wabooks_mrec_bltb_0005_d001"></a>

## `WABOOKS_MREC_BLTB_0005_D001` — Delete a post file

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_BLTB_0005_D001` |
| Sheet name (source) | `WABOOKS_MREC_BLTB_0005_D001` |
| Sheet title (원문) | Delete Post file |
| Business area (업무) | Delete Post File |
| Screen (source index) | Post Detail |
| Legacy web reference | — |
| Legacy tables (테이블) | 1.BLTB_POST_FILE <br> 2.EXPT_RESOL_FILE <br> 3.EXPT_RESOL_PAY_EVID_FILE <br> 4.BLTB_DRAFT_POST_FILE |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | — (none) |
| Coverage | **MISSING** |
| Last spec revision | — |

### Input — 7 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `BLTB_CD` | N | 15 | Y | Bulletin board code |  |
| `POST_SEQ_NO` | N | 15 |  | 파일ID |  |
| `APPR_SEQ_NO` | C | 15 |  | 지출결의번호 |  |
| `POST_TYPE_CD` | C | 2 | Y | Post type code | 01' : Article , '02':Approval, '03': Draft |
| `FILE_ID` | C | 100 | Y | 파일ID |  |
| `FILE_TYPE` | C | 100 |  | File Type | EXPT_RESOL_PAY_EVID_FILE', 'EXPT_RESOL_FILE' |
| `DRAFT_POST_SEQ_NO` | N | 15 |  | Dratf post sequence number |  |

### Output — 0 fields

_No output payload beyond the common `RSLT_CD` / `RSLT_MSG` envelope._

### Process rules (verbatim from the sheet)

Input section:

```text
1. 
1.1 POST_TYPE_CD = '01'>> 
POST_SEQ_NO is required 
Table 1: Find file by biz_key: (Session), bltb_cd, post_seq_no, file_id >> 
if existed >>delete
if not find >> throws an exception error "File not found"

1.2 POST_TYPE_CD = '02' >> 
APPR_SEQ_NO is required
FILE_TYPE is required

if FILE_TYPE = 'EXPT_RESOL_FILE' >>
Table 2: Find file by biz_key: (Session), appr_seq_no, file_id >> 

if FILE_TYPE = 'EXPT_RESOL_PAY_EVID_FILE' >>
Table 3: Find file by biz_key: (Session), appr_seq_no, evid_file_id: (file_id) >> 

if existed >>delete
if not find >> throws an exception error "File not found" 1.3. POST_TYPE_CD = '03' >> 
DRAFT_POST_SEQ_NO is required

Table 4:  Find file by biz_key: (Session), draft_post_seq_no, file_id >> 

if existed >>delete
if not find >> throws an exception error "File not found"
```

---

<a id="wabooks_mrec_bltb_0005_d002"></a>

## `WABOOKS_MREC_BLTB_0005_D002` — Delete a comment file

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_BLTB_0005_D002` |
| Sheet name (source) | `WABOOKS_MREC_BLTB_0005_D002` |
| Sheet title (원문) | Delete Comment File |
| Business area (업무) | Delete Comment File |
| Screen (source index) | Post Detail |
| Legacy web reference | — |
| Legacy tables (테이블) | 1.BLTB_POST_COMNT_FILE |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | — (none) |
| Coverage | **MISSING** |
| Last spec revision | — |

### Input — 4 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `BLTB_CD` | C | 15 | Y | Bulletin board code |  |
| `POST_SEQ_NO` | N | 15 | Y | post sequence number |  |
| `COMNT_SEQ_NO` | C | 15 | Y | comment sequence number | C: 'Comment', R : 'Remark' |
| `FILE_ID` | C | 100 | Y | File Id |  |

### Output — 0 fields

_No output payload beyond the common `RSLT_CD` / `RSLT_MSG` envelope._

### Process rules (verbatim from the sheet)

Input section:

```text
** validation :  
  - if user is not the owner of commnet throw error

1.Table 1:
Find file by biz_key: (Session), bltb_cd, post_seq_no, comnt_seq_no, file_id >>
if existed >> delete data from table 1
if not existed >> throws an error "File not found"
```

---

<a id="wabooks_mrec_bltb_0005_r002"></a>

## `WABOOKS_MREC_BLTB_0005_R002` — Approval detail

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_BLTB_0005_R002` |
| Sheet name (source) | `WABOOKS_MREC_BLTB_0005_R002` |
| Sheet title (원문) | Approval Detail |
| Business area (업무) | Detail Approval |
| Screen (source index) | Post Detail |
| Legacy web reference | `bltb_0001_003_r001` |
| Legacy tables (테이블) | 1. BLTB_POST_INFM <br> 2. USER_BLTB_CONN_INFM <br> 3. BLTB_USER_NEW_POST_INFM <br> 4. EXPT_RESOL_MSTR <br> 5. BLTB_COMNT_INFM <br> 6. BLTB_POST_FILE <br> 7. EXPT_RESOL_FILE <br> 8. EXPT_RESOL_DETL <br> 9. EXPT_RESOL_DETL_INF <br> 10. BLTB_NOTI_INFM <br> 11. USER_INFM <br> 12. EXPT_RESOL_APPRVR_INFM |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | `GET /api/v1/payment/approval-box/detail` |
| Coverage | **PARTIAL** |
| Last spec revision | Update v2024.1.32 |

**Delta.** Also `/{apprSeqNo}/attachments` and `/transactions`. Missing comments, mentions and `APPRVR_IMG_URL`.

### Input — 2 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `POST_SEQ_NO` | N | 15 | Y | Post sequence number |  |
| `BLTB_CD` | C | 15 |  | Bulletin board code |  |

### Output — 56 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `BLTB_CD` | C | 15 |  | Bulletin board code | Table 1 |
| `POST_SEQ_NO` | N | 15 |  | Post sequence number | Table 1 |
| `POST_TITLE` | C | 200 |  | Post title | Table 1 |
| `POST_TYPE_CD` | C | 2 |  | Post type code | 01' : Article , '02':Approval |
| `POST_STTS` | C | 2 |  | Post status | PL : Public , PV:Private <br> if POST_STTS = PV >> <br> table 1 join table 2 |
| `REG_NM` | C | 100 |  | Register Name | Table 11: USER_NM where USER_ID: REG_ID |
| `REG_ID` | C | 15 |  | 등록자ID | Table 1 |
| `REG_DTM` | C | 2 |  | 등록일시 | Table 1 |
| `REG_IMG_URL` | C | 200 |  | Register Image Url | Table11: USER_IMG is not null where USER_ID: REG_ID >> {Base Url}/szon_0012_003_r002.act?USER_ID={REG_ID} |
| `USER_IMG` | C | 500 |  | User Image | v2024.1.11 added >> Table 11 |
| `USER_IMG_2` | C | 500 |  | User Image 2 | v2024.1.27 added >> <br> Table 11: Select user_img where USER_ID: USER_ID(Session); |
| `USER_IMG_URL` | C | 200 |  | Register Image Url | v2024.1.27 added <br> if USER_IMG_2 is not null >> <br> {Base URL}/szon_0012_003_r002.act?USER_ID={USER_ID(Session)}&cmd=getthumb |
| `PIN_POST_TYPE` | C | 2 |  | Pin post type | Table 1 |
| `PIN_POST_DUE_DT` | C | 8 |  | Pin post due date | Table 1 |
| `POST_CONT` | C | 2000 |  | Post content | Table 1 |
| `TOT_CMT` | N | 15 |  | Total Comment | Table 5 |
| `TOT_PAY_LOC_AMT` | N | 20.6 |  | Total Pay Local Amount | v2024.1.32 |
| `TOT_PAY_USD_AMT` | N | 20.6 |  | 결의총USD금액 |  |
| `APPR_SEQ_NO` | C | 15 |  | 지출결의번호 | Table 1 |
| `PAY_END_YN` | C | 1 |  | Approval End Y/N | Table 4: Y: Approved, N: Pending |
| `PAY_YN` | C | 1 |  | Pay Y/N | Table 4: Y: Paid, N: Not Paid, P: Partially Paid |
| `READ_YN` | C | 1 |  | Read Y/N | Table 4: N: Show Icon 'N' |
| `DEL_YN` | C | 1 |  | Delete Y/N | Table 2: Admin and Post owner allow to delete |
| `DOC_NO` | C | 5 |  | Document Number | Table 4 |
| `APPR_DV` | C | 2 |  | 지출결의구분 | Table 4: <br> 00 : 수기등록 <br> 10 : Expense/Bill <br> 20 : 급여 <br> (10/06) 추가 <br> 30 : Expense <br> 40 : Bill <br> 50 : Vietnam e-Invoice (2022/12/26 추가) <br> 60 : Cash Advances Request (v2023.1.10 추가) <br> 70 : ‘Simple’ |
| `PAY_REQ_DATE` | C | 8 |  | 지급요청일 | Table 4 |
| `PAY_DATE` | C | 8 |  | 지급예정일 | Table 4 |
| `REC` |  |  |  | Record |  |
| ↳ `DOC_ID` | C | 50 |  | 문서ID | Table 8 |
| ↳ `ISSU_DT` | C | 8 |  | 발행일자 | Table 8 |
| ↳ `DOC_TYPE` | C | 1 |  | 문서종류 | Table 8 |
| ↳ `TRCO_KEY` | C | 20 |  | 거래처키 | Table 8 |
| ↳ `TRCO_CMNM` | C | 200 |  | 거래처회사명 | Table 8 |
| ↳ `TRCO_CMENM` | C | 200 |  | 거래처회사영문명 | Table 8 |
| ↳ `CCY_CD` | C | 3 |  | 거래처거래통화코드 | Table 8 |
| ↳ `AMT` | N | 20.6 |  | 총금액 | Table 8 |
| ↳ `MEMO` | C | 200 |  | 메모 | Table 8 |
| ↳ `BUSA_USAG_CD` | C | 20 |  | 매입매출용도코드 | Table 8 |
| ↳ `USAG_NM` | C | 100 |  | 용도명 | Table 8 |
| `REC2` | R |  |  | Record 2 | if POST_TYPE_CD = 01 >> <br> File from table 6 <br> if POST_TYPE_CD = 02 >> <br> File from table 7 |
| ↳ `FILE_ID` | C | 100 |  | 파일ID |  |
| ↳ `ORG_FILE_NM` | C | 500 |  | 원본파일명 |  |
| ↳ `SAVE_FILE_NM` | C | 500 |  | 저장파일명 |  |
| ↳ `FILE_SIZE` | C | 100 |  | 파일크기 |  |
| ↳ `FILE_TYPE` | C | 100 |  | File Type |  |
| ↳ `FILE_URL` | C | 200 |  | File Url | {Base Url}/comm_0192_001_r008.act?{Datetime}&APPR_SEQ_NO={APPR_SEQ_NO}&FILE_ID={FILE_ID} |
| `REC3` | R |  |  | 목록3 | table 12 v2023.1.0.1 |
| ↳ `APPRVR_ID` | C | 100 |  | approver ID |  |
| ↳ `APPRVR_IMG_URL` | C | 200 |  | Approver Image Url | v2024.1.7 add <br> Table11: USER_IMG is not null where USER_ID: (APPRVR_ID of table 12) >> {Base Url}/szon_0012_003_r002.act?USER_ID={APPRVR_ID of table 12} |
| ↳ `APPR_DTM` | N | 14 |  | Approval Date Time | v2024.1.7 add <br> table 12 |
| ↳ `APPRVR_NM` | C | 100 |  | approver name |  |
| ↳ `APPRVR_STTS` | C | 1 |  | approver status | ('P':pending , R :'reject' , 'A' : 'approve' ) |
| `APPR_TYPE_CD` | C | 2 |  | Approve type Cd | v2023.1.0.1 <br> '00' : 'Pending' <br> '01' : 'Approve' <br> '02' : 'reject' <br> '03' : 'Partially Approve'" |
| `APPRVR_TOT_CNT` | N | 15 |  | Approver count | v2024.1.34 removed v2024.1.37 restored <br> select count (*) as APPRVR_TOT_CNT <br> from EXPT_RESOL_APPRVR_INFM <br> where BIZ_KEY('session') <br> and APPR_SEQ_NO : OUTPUT(REC > APPR_SEQ_NO) |
| `APPRVR_APPR_TOT_CNT` | N | 15 |  | Approver Approve count | v2024.1.34 removed v2024.1.37 restored <br> select count (*) as APPRVR_TOT_CNT <br> from EXPT_RESOL_APPRVR_INFM <br> where BIZ_KEY('session') <br> and APPR_SEQ_NO : OUTPUT(REC > APPR_SEQ_NO) <br> and apprvr_stts = 'A' |
| `APPR_STTS` | C | 10 |  | Approval Status | **see note 1 below** |

**Note 1 — `APPR_STTS`** (verbatim from the sheet):

```text
v2024.1.34 added
 if  APPR_TYPE_CD = '00' then 'P'
 else if  APPR_TYPE_CD == '01' then 'C'
 else if  APPR_TYPE_CD == '02' then  'R'
 else if  APPR_TYPE_CD == '03' then 
 
  select  
    APPRVR_APPR_TOT_CNT  || '/'|| APPRVR_TOT_CNT as  APPR_STTS
   from (
    select  
     ,count (*) as  APPRVR_TOT_CNT
     ,sum (case when apprvr_stts ='A' then 1 else 0 end)  APPRVR_APPR_TOT_CNT
    from  expt_resol_apprvr_infm erai 
      where  BIZ_KEY('session')  
      and APPR_SEQ_NO : OUTPUT(REC > APPR_SEQ_NO)  
   ) sub
v2024.1.37 added
if (user is Admin or  USER_ID ('session') ==  OUTPUT( REG_ID )) 
          and not  exists (
                select 1 
                  from 
                expt_resol_apprvr_infm 
                    where  biz_key ('session')
                     and apprvr_id  : user_id ('session')
                     and apprvr_stts  = 'P'
                    and apprvr_id  : user_id ('session')|
            )   v2024.1.38 added then 
 if  APPR_TYPE_CD = '00' then 'P'
 else if  APPR_TYPE_CD == '01' then 'C'
 else if  APPR_TYPE_CD == '02' then  'R'
 else if  APPR_TYPE_CD == '03' then  'I'

else 
  select   
      APPR_STTS 
  from expt_resol_apprvr_infm  
  where 
    biz_key ('session')
   and apprvr_id  : user_id ('session')|
   and  APPR_SEQ_NO  : OUTPUT( APPR_SEQ_NO)   
end
```

### Process rules (verbatim from the sheet)

Input section:

```text
1. Table 1:
Where 
BIZ_KEY: (Session)

2. Remove 'N' icon if it is a first view detail for each user
Table 3: Select
Where
BIZ_KEY: (Session)
POST_TYPE_CD: '02'
PK
if READ_YN = 'N' >> 
Table 3: Update READ_YN = 'Y'
Table 10: update ACTV_YN = 'Y'
Where 
BIZ_KEY: (Session)
BLTB_CD: (Input)
NOTI_SEQ_NO ** validation  Approval Display Rule (only for  Approval that has been reqursted) 
 
 - Admin
 - Requestor
 - Normal user   : display only the Approvals where the user is listed as an Approver and the status of  Approver  is  in
       'A' (approved)
      , 'R' (rejected)
      , or 'P' (pending) 
  if the user is not in the case above
      throws exception
```

---

<a id="wabooks_mrec_bltb_0005_u001"></a>

## `WABOOKS_MREC_BLTB_0005_U001` — Approve or reject

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_BLTB_0005_U001` |
| Sheet name (source) | `WABOOKS_MREC_BLTB_0005_U001` |
| Sheet title (원문) | Approve or Reject |
| Business area (업무) | — |
| Screen (source index) | Post Detail |
| Legacy web reference | `bltb_0001_003_u001` |
| Legacy tables (테이블) | 1.EXPT_RESOL_MSTR <br> 2.EXPT_RESOL_APPRVR_INFM <br> 3.BLTB_POST_INFM <br> 4.BLTB_COMNT_INFM <br> 5.BLTB_NOTI_INFM <br> 6.BLTB_NOTI_CONT_INFM <br> 7.BLTB_POST_COMNT_FILE <br> 8. PUSH_NOTICE_INFM <br> 9. MSG_SND_HIS <br> 10. PUSH_DEVICE_MSTR |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | `POST /api/v1/payment/approval-box/{apprSeqNo}/approve` |
| Coverage | **PARTIAL** |
| Last spec revision | Updated v2024.1.26 |

**Delta.** Also `/reject` and `/bulk-approve`. This sheet is missing its `프로그램ID` cell in the workbook.

### Input — 7 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `APPR_SEQ_NO` | C | 15 | Y | 지출결의번호 |  |
| `APPRVR_STTS` | C | 1 | Y | approver status | (R :'reject' , 'A' : 'approve' ) |
| `BLTB_CD` | N | 15 | Y | Bulletin board code | Add v2024.1.3 |
| `COMNT_CONT` | C | 1000 |  | Comment content | Add v2024.1.3 |
| `REC_IN` | R |  |  | 그룹 | Add v2024.1.3 |
| ↳ `FILE_NM` | C | 500 | Y | 파일명 | Add v2024.1.3 |
| ↳ `FILE_DATA` | B | 4000 | Y | 파일데이터 | Add v2024.1.3 <br> 1. Only files with jpg, png, pdf, doc(x), xls(x), and ppt(x) extensions are allowed. <br> 2. Up to 5 MB per file, up to 5 files allowed(follow expense upload rules) <br> 3. The uploaded file is saved in the <br> /WAS_DATA/file/{BIZ_KEY}/comm_0192_008_c001 directory on the server. |

### Output — 0 fields

_No output payload beyond the common `RSLT_CD` / `RSLT_MSG` envelope._

### Process rules (verbatim from the sheet)

Input section:

```text
1/ get row from table num2 
 with condition 
  BIZ_KEY ('SESSOIN'), 
  APPRVR_ID : USER_ID ('SESSION') , 
  APPR_SEQ_NO  : INPUT ('APPR_SEQ_NO')
  APPRVR_STTS  : 'P'   v2024.1.34 added
 if null  throw exception
 ELSE 
   update table 2 
   set  APPRVR_STTS : INPUT ('APPRVR_STTS')
     APPR_DTM   : current date (UTC-NOW)
   WHERE  
   BIZ_KEY ('SESSOIN'), 
   APPRVR_ID : USER_ID ('SESSION') , 
   
    v2024.1.34 added
 if    INPUT ('APPRVR_STTS') == 'R' then
 
  -  update   table 2    
   set  APPRVR_STTS : 'S'
  where  BIZ_KEY =  BIZ_KEY ('SESSOIN')
  APPR_SEQ_NO  : INPUT ('APPR_SEQ_NO')
  and  APPRVR_STTS : 'W'
 else 
   -  update   table 2    
   set  APPRVR_STTS : 'P'
  where  BIZ_KEY =  BIZ_KEY ('SESSOIN')
  APPR_SEQ_NO  : INPUT ('APPR_SEQ_NO')
  and  APPRVR_ID  in  
   (
    select   APPRVR_ID
    from table 2
    where  BIZ_KEY  =  BIZ_KEY ('SESSOIN')
    and APPR_SEQ_NO  : INPUT ('APPR_SEQ_NO')
    and  APPRVR_STTS : 'W'
    order by  APPVR_LINE_NO ASC
    limit 1
   )
   

"2/  select 
  count (*)  TOT_CNT 
 , sum(CASE WHEN  APPRVR_STTS = 'A' then 1 else 0 ) as TOT_APPR_CNT
 , sum(CASE WHEN  APPRVR_STTS = 'R' then 1 else 0 ) as TOT_RJT_CNT
 from table num2 
  with condition  
  BIZ_KEY ('SESSOIN'),
  APPR_SEQ_NO  : INPUT ('APPR_SEQ_NO')
  if TOT_CNT == TOT_APPR_CNT +  TOT_RJT_CNT then 
    update table 1
    PAY_END_YN : 'Y'
    PAY_END_DTM : SYSDATE
    PAY_END_NM : (Logined user name)
    APPR_TYPE_CD: (if  TOT_RJT_CNT == '0' then '01' else '02' end)
else 
  if (TOT_RJT_CNT == '0') then 
    update table 1
    set  APPR_TYPE_CD : (if  TOT_RJT_CNT == '0' then '03' else '02' end) 3. get row from table3
 with where   
   BIZ_KEY('session')
 , APPR_SEQ_NO
 , REF_BLTB_CD, REF_POST_SEQ_NO is blank
4. insert data to table4 with value from process 3  
 and other value like below :
 COMNT_DV_CD : 'R'
 COMNT_TASK_CD : 
   if 'APPR_TYPE_CD' of('EXPT_RESOL_MSTR') = ' 01' then
              'R003'
   else
            if INPUT ('APPRVR_STTS') == 'R'     then 'R002'
           else if INPUT ('APPRVR_STTS') == 'A' then 'R004'"
 v2024.1.15 added (Approve or reject with comment[Mobile only])
 COMNT_CONT: COMNT_CONT('Input')
 
5. get rows from table3
 with where   
   BIZ_KEY('session')
 , APPR_SEQ_NO
 , BLTB_CD = '000000000000000'
  v2024.1.34 removed
6.get rows from table 2  
 with BIZ_KEY('session'), 
 APPR_SEQ_NO ('INPUT')  , 
 APPRVR_ID   <> USER_ID ('session')
     v2024.1.34 added
6.  get all admin users of the current company and requestor of  Approval   v2024.1.45 updated
  , approver of the current approval with status 'P' 
   and exclude requestor
   =====================================
   select   
  T.apprvr_id
  , wb_get_user_nm (T.apprvr_id) as  apprvr_nm
  from (
   select  
      user_id   as apprvr_id
    from busi_admn_infm 
    where biz_key = BIZ_KEY ('session')
   UNION 
    select   
      apprvr_id  as apprvr_id
    from expt_resol_apprvr_infm 
    where biz_key  =  BIZ_KEY ('session')
    and appr_seq_no = appr_seq_no ('INPUT')
     and apprvr_stts  in  (''P') 
   v2024.1.45 updated
    select   
     reg_id  as apprvr_id
    from expt_resol_mstr 
    where biz_key  =  BIZ_KEY ('session')
    and appr_seq_no = appr_seq_no ('INPUT')
  ) as T
  where  apprvr_id <> USER_ID ('session')  
7.0. insert data to table 5 
 - BLTB_CD      ='000000000000000'
 - SND_ID       = USER_ID ('session')
 - SND_NM       = USER_NM (session)
 - RCV_ID       = APPRVR_ID(from proc 6)
 - RCV_NM       = APPRVR_NM (from proc 6)
 - NOTI_URL     ='bltb_0001_003.act?POST_SEQ_NO={POST_SEQ_NO}&BLTB_CD={BLTB_CD}'
 - NOTI_WSVC    = bltb_0001_003.act
     -POST_SEQ_NO  = from proc 5
```

Input section:

```text
v2024.1.15 added
7.1.Table 10: Find each user by PK(BIZ_KEY, USER_ID) != null >> Process 7.2 and 7.3
7.2.Insert into Table 8
BIZ_KEY : BIZ_KEY ('session')
USER_ID: USER_ID ('Process 6') (RCV_ID)
SEQ_NO : Generate Sequence Base on BIZ_KEY, USER_ID 
SND_DATE : YYYYMMDD
SND_TIME : HHMMSS
SND_ID: USER_ID ('session')
SND_NM: ((wb_get_user_nm(SND_ID)))
RCV_ID: USER_ID ('Process 6')
RCV_NM: ((wb_get_user_nm(RCV_ID)))
REG_SEQ_NO: POST_SEQ_NO('Table 3')
COMNT_SEQ_NO: COMNT_SEQ_NO('Table 4')
v2024.1.24 added
BLTB_CD: BLTB_CD('INPUT')
DV_CD: >>
 if APPRVR_STTS = 'A' >>
   if INPUT.COMNT_CONT is NULL >> 
  if POST_APPR_DV = '00' >>
    DV_CD : '13' (13.Simple Approval approved by approver(s))
  else if POST_APPR_DV = '10' >>
       DV_CD : '7' (7.Expense Approval approved by approver(s))
   else >>
     if POST_APPR_DV = '00' >> 
    DV_CD : '14' (14.Simple Approval approved by approver(s) with comment)
       CONT: COMNT_CONT('INPUT')
  else if POST_APPR_DV = '10' >> 
    DV_CD : '8' (8.Expense Approval approved by approver(s) with comment)
       CONT: COMNT_CONT('INPUT')
 else if APPRVR_STTS = 'R' >> 
   if INPUT.COMNT_CONT is NULL >> 
     DV_CD : '9' (9.Rejected by approver) 
   else >>  
    DV_CD : '10' (10.Rejected by approver with comment)
    CONT: COMNT_CONT('INPUT')
** Note:
Reject Simple and Expense Approval are same notification type(9 and 10). v2024.1.15 added
7.3.Insert into Table 9
REG_SEQ_NO: generate Sequence
BIZ_KEY: (Session)
USER_ID: USER_ID ('session')
MSG_TYPE: 'P'
RCV_NO:  USER_ID ('Process 6')
RCV_NM: (wb_get_user_nm(RCV_NO))
v2024.1.26 updated
SND_TITL: 'WABOOKS'
SND_CONT:
 if DV_CD(Table 8) = '7' >> SND_NM(Table 8) + " approved your expense approval request."
 else if DV_CD(Table 8) = '8' >> SND_NM(Table 8) + " approved your expense approval request: " + CONT(Table 8)
 else if DV_CD(Table 8) = '9' >> SND_NM(Table 8) + " rejected your expense approval request."
 else if DV_CD(Table 8) = '10' >> SND_NM(Table 8) + "  rejected your expense approval request: " + CONT(Table 8)
 else if DV_CD(Table 8) = '13' >> SND_NM(Table 8) + " approved your approval request."
 else if DV_CD(Table 8) = '14' >> SND_NM(Table 8) + " approved your approval request: " + CONT(Table 8)
SND_RSLT: 'SND_WAIT'
STTS: '0'
PUSH_ACTION_URL: if Table 8>>DV_CD = 6 to 15 >> 
json string({
DV_CD: DV_CD (Table 8),
REG_SEQ_NO: REG_SEQ_NO (Table 8),
COMNT_SEQ_NO: COMNT_SEQ_NO (Table 8),
v2024.1.24 added
BLTB_CD: BLTB_CD(Table 8)
})

8. insert data to table 6
 -TITLE : from  'COMNT_TASK_CD' of  proc4    
   if     (R002)   'Approval' -> 'Rejected'  
   elseif (R003)   'Approval' → ‘Complete’
   elseif (R004)   'Approval' -> 'Approved'
 -LANG_CD : VN,KH,KO,EN
 -COMNT_CONT: ('INPUT')                                                                                            ** Noted : If REG_ID  of 'EXPT_RESOL_MSTR' not in  proc 5 and  REG_ID != USER_ID  ('session') we also Need to run proc 6,7 Add v2024.1.3
9. Insert data to table 7
BLTB_CD: (Input)
POST_SEQ_NO: (Input)
FILE_ID: (Sequence)
ORG_FILE_NM: Original file name of the attached file (INPUT.FILE_NM)
SAVE_FILE_NM: File name saved on the server of the attached file
FILE_SIZE: Size of the attached file.
```

---

<a id="wabooks_mrec_bltb_0005_r003"></a>

## `WABOOKS_MREC_BLTB_0005_R003` — Read an article / approval draft

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_BLTB_0005_R003` |
| Sheet name (source) | `WABOOKS_MREC_BLTB_0005_R003` |
| Sheet title (원문) | Read Draft Article & Approval |
| Business area (업무) | (COMM) |
| Screen (source index) | Post Detail |
| Legacy web reference | `comm_0192_001_r005` |
| Legacy tables (테이블) | 1. BLTB_DRAFT_POST_MSTR <br> 2. BLTB_DRAFT_POST_FILE <br> 3. BLTB_DRAFT_POST_DETL <br> 4. BLTB_DRAFT_POST_APPRVR_INFM <br> 5. USER_INFM |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | — (none) |
| Coverage | **MISSING** |
| Last spec revision | Update v2024.1.36 |

### Input — 3 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `DRAFT_POST_SEQ_NO` | N | 15 | Y | Post sequence number |  |
| `BLTB_CD` | C | 15 | Y | Bulletin board code |  |
| `POST_TYPE_CD` | C | 2 | Y | Post type code | 01' : Article , '02':Approval |

### Output — 41 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `BLTB_CD` | C | 15 |  | Bulletin board code | Table 1 |
| `DRAFT_POST_SEQ_NO` | N | 15 |  | Dratf post sequence number | Table 1 |
| `POST_TITLE` | C | 200 |  | Post title | Table 1 |
| `POST_TYPE_CD` | C | 2 |  | Post type code | 01' : Article , '02':Approval |
| `POST_STTS` | C | 2 |  | Post status | PL : Public , PV:Private <br> if POST_STTS = PV >> <br> table 1 join table 2 |
| `DRAFT_PIN_POST_TYPE` | C | 2 |  | Pin post type |  |
| `DRAFT_PIN_POST_DUE_DT` | C | 8 |  | Pin post due date |  |
| `DRAFT_REG_PIN_POST_DATE` | C | 14 |  | Dratf Register pin post date |  |
| `POST_CONT` | C | 2000 |  | Post content |  |
| `REC` | R |  |  | Record | Table 2 |
| ↳ `FILE_ID` | C | 100 |  | 파일ID |  |
| ↳ `ORG_FILE_NM` | C | 500 |  | 원본파일명 |  |
| ↳ `SAVE_FILE_NM` | C | 500 |  | 저장파일명 |  |
| ↳ `FILE_SIZE` | C | 100 |  | 파일크기 |  |
| ↳ `FILE_URL` | C | 200 |  | File Url | {Base URL}/comm_0192_001_r006.act?{Datetime}&BLTB_CD={BLTB_CD}&DRAFT_POST_SEQ_NO={DRAFT_POST_SEQ_NO}&FILE_ID={FILE_ID} |
| `REC2` | R |  |  | Record 2 | POST_TYPE_CD = '03' >> Table 3 |
| ↳ `BLTB_CD` | C | 15 |  | Bulletin board code |  |
| ↳ `DRAFT_POST_SEQ_NO` | N | 15 |  | Dratf post sequence number |  |
| ↳ `DRAFT_POST_DETL_SEQ_NO` | N | 15 |  | Dratf post detail sequence number |  |
| ↳ `BUSA_USAG_CD` | C | 20 |  | 매입매출용도코드 |  |
| ↳ `CCY_CD` | C | 3 |  | 통화코드 |  |
| ↳ `DOC_ID` | C | 50 |  | 문서ID |  |
| ↳ `DOC_TYPE` | C | 1 |  | 문서종류 |  |
| ↳ `MEMO` | C | 200 |  | 메모 |  |
| ↳ `TRCO_KEY` | C | 20 |  | 거래처키 |  |
| ↳ `TRCO_CMNM` | C | 200 |  | 거래처회사명 |  |
| ↳ `TRCO_CMENM` | C | 200 |  | 거래처회사영문명 |  |
| ↳ `AMT` | N | 20.6 |  | 총금액 | v2024.1.30 added |
| `PAY_DATE` | C | 8 |  | 지급예정일 | Table 1 |
| `REQ_USER_ID` | C | 100 |  | requestor ID | Table 1 |
| `REQ_USER_NM` | C | 100 |  | Requestor name | Table 1 |
| `POST_APPR_DV` | C | 2 |  | Post Approval DV | Table 1 |
| `DOC_TYPE` | C | 1 |  | Document Type | Table 3 |
| `TOT_PAY_KHR_AMT` | N | 20.6 |  | 결의총KHR금액 | Table 3 : v2024.1.30 added <br> Sum of AMT on Local Currency (KHR or VND) |
| `TOT_PAY_USD_AMT` | N | 20.6 |  | 결의총USD금액 | Table 3 : v2024.1.30 added <br> Sum of AMT on Local Currency (USD) |
| `REC3` | R |  |  | Record 3 | v2024.1.36 <br> Table 4 join Table 5 on user_id where biz_key, bltb_cd, draft_post_seq_no |
| ↳ `APPRVR_ID` | C | 100 |  | Approver ID |  |
| ↳ `APPRVR_NM` | C | 100 |  | Approver Name |  |
| ↳ `APPRVR_STTS` | C | 1 |  | Approver status |  |
| ↳ `APPRVR_IMG_URL` | C | 200 |  | Approver Image URL | if Table 5>> USER_IMG is not null >> {Base URL}/szon_0012_003_r002.act?USER_ID={APPRVR_ID} |
| ↳ `APPRVR_IMG` | C | 500 |  | Approver Image | Table 5>> USER_IMG |

### Process rules (verbatim from the sheet)

Input section:

```text
Added v2024.1.19
1. Table 1:
Where 
BIZ_KEY: (Session)
POST_TYPE_CD
POST_SET_NO
BLTB_CD
```

Output section:

```text
PAY REQ_DATE 지급요청일 C 8 Table 1
```

---

<a id="wabooks_mrec_bltb_0006_r001"></a>

## `WABOOKS_MREC_BLTB_0006_R001` — Board member list

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_BLTB_0006_R001` |
| Sheet name (source) | `WABOOKS_MREC_BLTB_0006_R001` |
| Sheet title (원문) | Inquiry Members |
| Business area (업무) | List Members(All)(COMM) |
| Screen (source index) | Members |
| Legacy web reference | `comm_0192_006_r001` |
| Legacy tables (테이블) | 1. USER_BLTB_CONN_INFM <br> 2. USER_INFM <br> 3. BLTB_INFM |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | `GET /api/v1/users` |
| Coverage | **PARTIAL** |
| Last spec revision | — |

**Delta.** No board membership model (`USER_BLTB_CONN_INFM`).

### Input — 2 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `BLTB_CD` | C | 15 | Y | Bulletin board code |  |
| `SRCH_WD` | C | 100 |  | 검색어 | SRCH_WD Like USER_NM |

### Output — 11 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `TOT_CNT` | N | 15 |  | Total Count | Select count(*) from table 1 where BIZ_KEY: (Session) <br> and BLTB_CD |
| `BLTB_NAME` | C | 50 |  | Bulletin board name | Table 3 |
| `CONT` | C | 200 |  | 내용 | Table 3 |
| `BLTB_USER_TYPE` | C | 1 |  | Bulletin User Type | Table 1 <br> where BIZ_KEY: (Session) <br> USER_ID = (Session USER_ID) <br> A: Admin, M: Member |
| `REC` | R |  |  | Record | Table 3 |
| ↳ `USER_ID` | C | 100 |  | User ID | Table 2 |
| ↳ `USER_NM` | C | 100 |  | User Name | Table 2 |
| ↳ `BLTB_USER_TYPE` | C | 1 |  | Bulletin User Type | Table 1 <br> A: Admin, M: Member |
| ↳ `MMB_TYPE` | C | 1 |  | Member Type | Y: Me, N: Other member <br> if BLTB_USER_TYPE != A and USER_ID = (Session USER_ID) >> MMB_TYPE = 'Y' <br> else MMB_TYPE = 'N' |
| ↳ `USER_IMG_URL` | C | 200 |  | User Image Url | if USER_IMG is not null >> {Base URL}/szon_0012_003_r002.act?USER_ID={USER_ID} |
| ↳ `USER_IMG` | C | 500 |  | User Image | v2024.1.11 added >> Table 2 |

### Process rules (verbatim from the sheet)

Input section:

```text
Table 1: Select
Where
BIZ_KEY: (Session)
USER_ID: (Session)
BLTB_CD
```

Output section:

```text
Sample for USER Image: 
{Base URL}/szon_0012_003_r002.act?USER_ID={USER_ID}&cmd=getthumb
```

Output section:

```text
v2024.1.4 add
**Note
This is the condition for show 'Me','Admin', and ''.

if MMB_TYPE == "Y" && BLTB_USER_TYPE == "A" >> 'Me'
else if BLTB_USER_TYPE == "A" >> 'Admin'
else >> ''
```

---

<a id="wabooks_mrec_bltb_0006_r002"></a>

## `WABOOKS_MREC_BLTB_0006_R002` — Invitable (non-member) user list

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_BLTB_0006_R002` |
| Sheet name (source) | `WABOOKS_MREC_BLTB_0006_R002` |
| Sheet title (원문) | Inquiry New Member |
| Business area (업무) | List new Members (User is not yet a members on current Board) |
| Screen (source index) | Invite Members |
| Legacy web reference | `comm_0192_012_r001` |
| Legacy tables (테이블) | 1. USER_INFM <br> 2. USER_BUSI_CONN_INFM <br> 3. USER_BLTB_CONN_INFM |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | `GET /api/v1/users` |
| Coverage | **PARTIAL** |
| Last spec revision | — |

**Delta.** Same delta as BLTB_0006_R001.

### Input — 2 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `BLTB_CD` | C | 15 | Y | Bulletin board code |  |
| `SRCH_WD` | C | 100 |  | 검색어 | SRCH_WD Like USER_NM |

### Output — 5 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `REC` | R |  |  | Record |  |
| ↳ `USER_ID` | C | 100 |  | User ID | Table 1 |
| ↳ `USER_NM` | C | 100 |  | User Name | Table 1 |
| ↳ `USER_IMG_URL` | C | 200 |  | User Image Url | if USER_IMG is not null >> {Base URL}/szon_0012_003_r002.act?USER_ID={USER_ID} |
| ↳ `USER_IMG` | C | 500 |  | User Image | v2024.1.11 added >> Table 1 |

### Process rules (verbatim from the sheet)

Input section:

```text
1. Table 1 join Table 2: Select
Where
BIZ_KEY: (Session)
DEL_YN = 'N'
and not exist(select 1 from table 3 where 
BIZ_KEY: (Session)
USER_ID: (Table 1)
BLTB_CD: (Input)
)
**Select user on same company and not in current board
```

Output section:

```text
Sample for USER Image: 
szon_0012_003_r002.act?USER_ID={USER_ID}&cmd=getthumb
```

---

<a id="wabooks_mrec_bltb_0006_c001"></a>

## `WABOOKS_MREC_BLTB_0006_C001` — Invite a member to a board

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_BLTB_0006_C001` |
| Sheet name (source) | `WABOOKS_MREC_BLTB_0006_C001` |
| Sheet title (원문) | Add a new member |
| Business area (업무) | Invite Members |
| Screen (source index) | Invite Members |
| Legacy web reference | `comm_0192_012_c001` |
| Legacy tables (테이블) | 1. USER_BLTB_CONN_INFM <br> 2. BLTB_NOTI_INFM <br> 3. BLTB_NOTI_CONT_INFM |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | — (none) |
| Coverage | **MISSING** |
| Last spec revision | Update v2024.1.12 |

### Input — 3 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `REC` | R |  |  | Record | v2024.1.12 added |
| ↳ `BLTB_CD` | C | 15 | Y | Bulletin board code |  |
| ↳ `USER_ID` | C | 100 | Y | User ID |  |

### Output — 0 fields

_No output payload beyond the common `RSLT_CD` / `RSLT_MSG` envelope._

### Process rules (verbatim from the sheet)

Input section:

```text
1. Validate Admin permission
Table 1: Select
Where
BIZ_KEY: (Session)
USER_ID: (Session)
BLTB_CD: (input)
if BLTB_USER_TYPE != A >>  throws an exception "No Permission"

2.Table 1: insert (BLTB_USER_TYPE='M')
Where
BIZ_KEY: (Session)
USER_ID: (input)
BLTB_CD: (input) 3. Table 1: Find a user on current board by USER_ID(Input)
Table 2:  Insert notification to a user on current board by USER_ID(Input).
sender_id: (session user_id)
sender_name: (wb_get_user_nm(sender_id))
rcv_id: (table 1 user_id)
rcv_nm: (wb_get_user_nm(rcv_id))
noti_dt: YYYYMMDD
noti_tm: HHMMSS
noti_url: bltb_0001_001.act?BLTB_CD={BLTB_CD}
actv_yn: 'N'
Table 3: Insert by languages
- VN version
LANG_CD: 'EN', 'KO','VN'
TITL: ''{SND_NM}' is inviting'
CONT:
- KH version 
LANG_CD: 'EN', 'KO','KH'
TITL: ''{SND_NM}' is inviting'
CONT:
```

---

<a id="wabooks_mrec_bltb_0006_u001"></a>

## `WABOOKS_MREC_BLTB_0006_U001` — Promote to / demote from board admin

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_BLTB_0006_U001` |
| Sheet name (source) | `WABOOKS_MREC_BLTB_0006_U001` |
| Sheet title (원문) | Add a new admin or Downgrade to member |
| Business area (업무) | Add as admin or Downgrade to member |
| Screen (source index) | Members > Tab |
| Legacy web reference | `comm_0192_011_u001` |
| Legacy tables (테이블) | 1. USER_BLTB_CONN_INFM <br> 2. BLTB_NOTI_INFM <br> 3. BLTB_NOTI_CONT_INFM |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | — (none) |
| Coverage | **MISSING** |
| Last spec revision | — |

### Input — 3 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `BLTB_CD` | C | 15 | Y | Bulletin board code |  |
| `USER_ID` | C | 100 | Y | User ID |  |
| `BLTB_USER_TYPE` | C | 1 | Y | Bulletin user type | Add : "A", Undo: "M" |

### Output — 0 fields

_No output payload beyond the common `RSLT_CD` / `RSLT_MSG` envelope._

### Process rules (verbatim from the sheet)

Input section:

```text
1. Validate Admin permission
Table 1: Select
Where
BIZ_KEY: (Session)
USER_ID: (Session)
BLTB_CD
if BLTB_USER_TYPE != A >>  throws an exception "No Permission"

2. Validate user already existed
Table 1: Select
Where
BIZ_KEY: (Session)
USER_ID: (input)
BLTB_CD
if if BLTB_USER_TYPE = M  and result = null >> throws an exception "Not found"

3. If BLTB_USER_TYPE = M and there is only one user admin on current board >>  throws an exception "Invalid" (Frontend should disable undo button)

4.Table 1: Update BLTB_USER_TYPE = (BLTB_USER_TYPE of input)
Where
BIZ_KEY: (Session)
USER_ID: (input)
BLTB_CD 5. if BLTB_USER_TYPE = 'A' >> 
Table 1: Find a user on current board by USER_ID(Input)
Table 2:  Insert notification to a user on current board by USER_ID(Input).
sender_id: (session user_id)
sender_name: (wb_get_user_nm(sender_id))
rcv_id: (table 1 user_id)
rcv_nm: (wb_get_user_nm(rcv_id))
noti_dt: YYYYMMDD
noti_tm: HHMMSS
noti_url: bltb_0001_001.act?BLTB_CD={BLTB_CD}
actv_yn: 'N'
Table 3: Insert by languages
- VN version
LANG_CD: 'EN', 'KO','VN'
TITL: 'Added you as admin in {Borad Name} Board.'
CONT:
- KH version 
LANG_CD: 'EN', 'KO','KH'
TITL: 'Added you as admin in {Borad Name} Board.'
CONT:
```

---

<a id="wabooks_mrec_bltb_0006_d001"></a>

## `WABOOKS_MREC_BLTB_0006_D001` — Remove a member from a board

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_BLTB_0006_D001` |
| Sheet name (source) | `WABOOKS_MREC_BLTB_0006_D001` |
| Sheet title (원문) | Remove member |
| Business area (업무) | Remove member |
| Screen (source index) | Members > Tab |
| Legacy web reference | `comm_0192_006_d001` |
| Legacy tables (테이블) | 1. USER_BLTB_CONN_INFM |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | — (none) |
| Coverage | **MISSING** |
| Last spec revision | — |

### Input — 2 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `BLTB_CD` | C | 15 | Y | Bulletin board code |  |
| `USER_ID` | C | 100 | Y | User ID |  |

### Output — 0 fields

_No output payload beyond the common `RSLT_CD` / `RSLT_MSG` envelope._

### Process rules (verbatim from the sheet)

Input section:

```text
1. Validate Admin permission
Table 1: Select
Where
BIZ_KEY: (Session)
USER_ID: (Session)
BLTB_CD
if BLTB_USER_TYPE != A >>  throws an exception "No Permission"

2. Validate user already existed
Table 1: Select
Where
BIZ_KEY: (Session)
USER_ID: (input)
BLTB_CD
if return = null >> throws an exception "Not found"

3.Table 1: Delete
Where
BIZ_KEY: (Session)
USER_ID: (input)
BLTB_CD
```

---

<a id="wabooks_mrec_bltb_0006_d002"></a>

## `WABOOKS_MREC_BLTB_0006_D002` — Leave a board

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_BLTB_0006_D002` |
| Sheet name (source) | `WABOOKS_MREC_BLTB_0006_D002` |
| Sheet title (원문) | Leave Board |
| Business area (업무) | Leave Board |
| Screen (source index) | Members > Tab |
| Legacy web reference | `comm_0192_011_u002` |
| Legacy tables (테이블) | 1. USER_BLTB_CONN_INFM |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | — (none) |
| Coverage | **MISSING** |
| Last spec revision | — |

### Input — 2 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `BLTB_CD` | C | 15 | Y | Bulletin board code |  |
| `USER_ID` | C | 100 | Y | User ID |  |

### Output — 0 fields

_No output payload beyond the common `RSLT_CD` / `RSLT_MSG` envelope._

### Process rules (verbatim from the sheet)

Input section:

```text
1. Validate 
if there is an admin and current user is an admin >> throws an warning error "You are the last admin"
2. table 1: delete 
Where 
BIZ_KEY: (Session)
BLTB_CD: (Input)
USER_ID: (Input)
```

---

<a id="wabooks_mrec_bltb_0006_r003"></a>

## `WABOOKS_MREC_BLTB_0006_R003` — Non-admin member list

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_BLTB_0006_R003` |
| Sheet name (source) | `WABOOKS_MREC_BLTB_0006_R003` |
| Sheet title (원문) | Inquiry members(Non Admin) |
| Business area (업무) | List a new admin(Not an Admin)(COMM) |
| Screen (source index) | Members > Tab > Select a new admin |
| Legacy web reference | `comm_0192_011_r001` |
| Legacy tables (테이블) | 1. USER_BLTB_CONN_INFM <br> 2. USER_INFM <br> 3. BLTB_INFM |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | — (none) |
| Coverage | **MISSING** |
| Last spec revision | — |

### Input — 2 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `BLTB_CD` | C | 15 | Y | Bulletin board code |  |
| `SRCH_WD` | C | 100 |  | 검색어 | SRCH_WD Like USER_NM |

### Output — 11 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `TOT_CNT` | N | 15 |  | Total Count | Select count(*) from table 1 where BIZ_KEY: (Session) <br> and BLTB_CD |
| `BLTB_NAME` | C | 50 |  | Bulletin board name | Table 3 |
| `CONT` | C | 200 |  | 내용 | Table 3 |
| `BLTB_USER_TYPE` | C | 1 |  | Bulletin User Type | Table 1 <br> where BIZ_KEY: (Session) <br> USER_ID = (Session USER_ID) <br> A: Admin, M: Member |
| `REC` | R |  |  | Record | Table 3 |
| ↳ `USER_ID` | C | 100 |  | User ID | Table 2 |
| ↳ `USER_NM` | C | 100 |  | User Name | Table 2 |
| ↳ `BLTB_USER_TYPE` | C | 1 |  | Bulletin User Type | Table 1 <br> A: Admin, M: Member |
| ↳ `MMB_TYPE` | C | 1 |  | Member Type | Y: Me, N: Other member <br> if BLTB_USER_TYPE != A and USER_ID = (Session USER_ID) >> MMB_TYPE = 'Y' <br> else MMB_TYPE = 'N' |
| ↳ `USER_IMG_URL` | C | 200 |  | User Image Url | if USER_IMG is not null >> {Base URL}szon_0012_003_r002.act?USER_ID={USER_ID} |
| ↳ `USER_IMG` | C | 500 |  | User Image | v2024.1.11 added >> Table 2 |

### Process rules (verbatim from the sheet)

Input section:

```text
Table 1: Select
Where
BIZ_KEY: (Session)
USER_ID: (Session)
BLTB_CD
```

Output section:

```text
Sample for USER Image: 
szon_0012_003_r002.act?USER_ID={USER_ID}&cmd=getthumb
```

---

<a id="wabooks_mrec_bltb_0006_u002"></a>

## `WABOOKS_MREC_BLTB_0006_U002` — Assign a new admin and leave

| | |
|---|---|
| Legacy service id | `WABOOKS_MREC_BLTB_0006_U002` |
| Sheet name (source) | `WABOOKS_MREC_BLTB_0006_U002` |
| Sheet title (원문) | Assign and leave |
| Business area (업무) | Assign & Leave |
| Screen (source index) | Members > Tab > Select a new admin |
| Legacy web reference | `comm_0192_011_u002` |
| Legacy tables (테이블) | 1. USER_BLTB_CONN_INFM |
| Called by | iOS ✓ · Android ✓ |
| Refactor endpoint | — (none) |
| Coverage | **MISSING** |
| Last spec revision | Updated v2024.1.15 |

### Input — 4 fields

| Field | Type | Len | Req | Source label | Rule / note |
|---|---|---|---|---|---|
| `REC` | R |  |  | Record | Added v2024.1.15 |
| ↳ `USER_ID` | C | 100 | Y | User ID | Assign User |
| `BLTB_CD` | C | 15 | Y | Bulletin board code |  |
| `USER_ID` | C | 100 | Y | User ID | Leave User |

### Output — 0 fields

_No output payload beyond the common `RSLT_CD` / `RSLT_MSG` envelope._

### Process rules (verbatim from the sheet)

Input section:

```text
1. Validate 
if there is an admin and current user is an admin >> throws an warning error "You are the last admin"
Added v2024.1.15
2.0. table 1: Update all user on INPUT REC (Assign)
set BLTB_USER_TYPE = 'A'
Where 
BIZ_KEY: (Session)
BLTB_CD: (Input)
USER_ID: USER_ID('REC')
BLTB_USER_TYPE: 'M'

2.1. table 1: delete (Leave)
Where 
BIZ_KEY: (Session)
BLTB_CD: (Input)
USER_ID: (Input)
```

---

