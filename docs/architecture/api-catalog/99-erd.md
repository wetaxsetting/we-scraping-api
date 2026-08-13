# 99 — ERD quan hệ bảng (theo cụm)

> Quan hệ logic suy ra từ code (entity/repository/service). Nhiều bảng legacy JEX **không có FK vật lý** ở DB — quan hệ dưới đây là quan hệ nghiệp vụ (qua `BIZ_KEY` + khóa nghiệp vụ). Mọi bảng tenant-scoped đều mang `BIZ_KEY`.

## 1. Cụm AUTH / USER / TENANT

```mermaid
erDiagram
    BUSI_INFM ||--o{ USER_BUSI_CONN_INFM : "BIZ_KEY"
    USER_INFM ||--o{ USER_BUSI_CONN_INFM : "USER_ID"
    USER_INFM ||--o{ USER_LOGIN_HIS : "USER_ID"
    USER_INFM ||--o{ USER_PW_CHG_HIS : "USER_ID"
    BUSI_INFM ||--o{ BUSI_ADMN_INFM : "BIZ_KEY"
    USER_INFM ||--o{ BUSI_ADMN_INFM : "USER_ID"
    USER_BUSI_CONN_INFM ||--o{ USER_MENU_AUTH_INFM : "BIZ_KEY+USER_ID"
    BUSI_INFM ||--o{ EMPL_INFM : "BIZ_KEY"

    BUSI_INFM { string BIZ_KEY PK }
    USER_INFM { string USER_ID PK }
    USER_BUSI_CONN_INFM { string USER_ID PK
                          string BIZ_KEY PK }
    BUSI_ADMN_INFM { string BIZ_KEY PK
                     string USER_ID PK }
    USER_LOGIN_HIS { string USER_ID PK
                     string REG_DTM PK }
    USER_MENU_AUTH_INFM { string BIZ_KEY PK
                          string USER_ID PK
                          string MENU_ID PK }
```
- `BUSI_ADMN_INFM` quyết định ADMIN/USER cho cặp (BIZ_KEY, USER_ID). `USER_BUSI_CONN_INFM` là link user↔company (multi-company exceptional).

## 2. Cụm BANKING (account / transaction / scraping)

```mermaid
erDiagram
    BUSI_INFM ||--o{ ACCT_MSTR : "BIZ_KEY"
    ACCT_MSTR ||--o{ ACCT_AUTH_INFO : "BANK_CD+ACCT_NO"
    ACCT_MSTR ||--o{ ACCT_TRNS_HSTR : "BANK_CD+ACCT_NO"
    ACCT_TRNS_HSTR ||--o{ ACCT_TRNS_RCV_PAY_INFM : "ACCT_SEQ_NO"
    BANK_INFM ||--o{ ACCT_MSTR : "BANK_CD (resolve nm)"
    BANK_CERT_INFO ||--o{ ACCT_MSTR : "BANK_CERT_SEQ"
    BANK_INFM ||--o{ BANK_INFM_DETL : "BANK_CD"
    ACCT_TRNS_HSTR ||--o{ RMRK_ORG_INFM : "match rule (LATERAL)"
    ACCT_TRNS_HSTR ||--o{ BUSI_MATCH_LOG : "auto-match audit"
    CHART_ACCT_INFM ||--o{ ACCT_TRNS_HSTR : "CHART_ACCT_SEQ_NO"
    BANK_EXCH_RATE_INFM }o--|| BANK_INFM : "BANK_CD (global)"
    NAPAS_REMIT_TRNS_HSTR }o--|| ACCT_MSTR : "out acct"
    NAPAS_BANK_INFO ||--o{ NAPAS_REMIT_TRNS_HSTR : "NAPAS code"

    ACCT_MSTR { string BIZ_KEY PK
                string BANK_CD PK
                string ACCT_NO PK }
    ACCT_TRNS_HSTR { string ACCT_SEQ_NO PK }
    ACCT_TRNS_RCV_PAY_INFM { string RCV_PAY_SEQ_NO PK }
    ACCT_AUTH_INFO { string BIZ_KEY PK
                     string BANK_CD PK
                     string ACCT_NO PK }
    BANK_CERT_INFO { string BIZ_KEY PK
                     string SEQ_NO PK }
    CHART_ACCT_INFM { string BIZ_KEY PK
                      string CHART_ACCT_SEQ_NO PK }
```
- `ACCT_AUTH_INFO` (entity từ V245): cờ quyền inquiry/transfer per-account, khóa nghiệp vụ trùng `ACCT_MSTR` (BANK_CD+ACCT_NO).
- Scraping audit: `EXT_TRAN_CALL_HIS`, `NXSIAS_MODULE_TRAN_LOG_HIS` (standalone, scope BIZ_KEY+USER_ID).

## 3. Cụm PAYMENT (scheduled / approval / dispatch)

```mermaid
erDiagram
    SCDUL_PYMN_SUMR ||--o{ SCDUL_PYMN_SUMR_DETL : "SCDUL_PYMN_NO"
    OVRS_PYMN_SUMR ||--o{ OVRS_PYMN_DTLS : "OVRS_PYMN_NO"
    EXPT_RESOL_MSTR ||--o{ EXPT_RESOL_APPRVR_INFM : "APPR_SEQ_NO"
    EXPT_RESOL_MSTR ||--o{ EXPT_RESOL_FILE : "APPR_SEQ_NO"
    SCDUL_PYMN_SUMR }o--o| EXPT_RESOL_MSTR : "APPR_SEQ_NO"
    OVRS_PYMN_SUMR }o--o| EXPT_RESOL_MSTR : "APPR_SEQ_NO (apprDv 34)"
    CARD_TRNS_HSTR }o--o| EXPT_RESOL_MSTR : "APPR_SEQ_NO (apprDv 33)"
    APPR_LINE_INFM ||--o{ APPR_LINE_INFM_DETL : "APPR_LINE_NO"
    SCDUL_PYMN_SUMR ||--o{ TB_PAY_TARGET : "dispatch VND"
    SCDUL_PYMN_SUMR ||--o{ TB_PAY_FILE_MST : "dispatch non-VND"
    TB_PAY_FILE_MST ||--o{ TB_PAY_FILE_DATA : "PAY_FILE_SEQ_NO"
    CARD_MSTR ||--o{ CARD_TRNS_HSTR : "BANK_CD+CARD_NO"
    CARD_TRNS_HSTR ||--o{ CARD_TRNS_RCV_PAY_INFM : "CARD_SEQ_NO"
    BUSA_DOC_MSTR ||--o{ BUSA_DOC_RCV_PAY_HIS : "DOC_ID"
    CARD_TRNS_RCV_PAY_INFM ||--o{ BUSA_DOC_RCV_PAY_HIS : "link"
    USAG_INFM ||--o{ CARD_TRNS_RCV_PAY_INFM : "USAG_CD (mục đích)"
    MEMO_INFM }o--|| CARD_TRNS_HSTR : "ghi chú giao dịch"
    TB_PAY_TARGET ||--o{ SCDU_PYMN_SUMR_DETL_PAY_TARGET_CONN : "PAY_TARGET_SEQ_NO"
    SCDUL_PYMN_SUMR_DETL ||--o{ SCDU_PYMN_SUMR_DETL_PAY_TARGET_CONN : "DETL link"

    SCDUL_PYMN_SUMR { string BIZ_KEY PK
                      string SCDUL_PYMN_NO PK
                      string SCDUL_PYMN_TYPE_CD "00/01/02/03" }
    EXPT_RESOL_MSTR { string APPR_SEQ_NO PK
                      string APPR_DV "33/34/.." }
    CARD_TRNS_HSTR { string CARD_SEQ_NO PK }
    BUSA_DOC_MSTR { string DOC_ID PK }
```
- 4 module thanh toán chia sẻ `SCDUL_PYMN_SUMR(_DETL)` qua `SCDUL_PYMN_TYPE_CD`. Workflow phê duyệt tập trung `EXPT_RESOL_MSTR`/`_APPRVR_INFM`; dispatch: VND→`TB_PAY_TARGET`, non-VND/overseas→`TB_PAY_FILE_MST/_DATA`.
- Cụm dispatch firm-banking (entity `domain/payment/dispatch`): `TB_FIRM_TRAN`, `TB_FIRM_FILE_MST/_DATA/_RSLT`, `TB_FIRM_BAT_API_TRAN`, `TB_BATCH_SCHEDULE_INFO/_DTL`; `SCDU_PYMN_SUMR_DETL_PAY_TARGET_CONN` (refactor-native) nối chi tiết lịch thanh toán ↔ `TB_PAY_TARGET`.
- Bảng cấp số nội bộ (refactor-native, không phải legacy): `INTERNAL_TRANSFER_KEY_SEQ`, `OVERSEAS_PAYMENT_KEY_SEQ` — sinh khóa per-tenant cho internal/overseas payment.

## 4. Cụm SETTINGS (contacts / config / reference)

```mermaid
erDiagram
    TRCO_INFM ||--o{ TRCO_ADDR_INFM : "TRCO_KEY"
    TRCO_INFM ||--o{ TRCO_ACCT_INFO : "TRCO_KEY"
    TRCO_INFM ||--o{ TRCO_OVRS_INFM : "TRCO_KEY"
    TRCO_INFM ||--o{ PGRO_BSAC_LNKN_INFM : "TRCO_KEY"
    TRCO_INFM ||--o{ TRCO_INFO_FILE : "TRCO_KEY"
    BUSI_USAG_SET_INFM ||--o{ BUSI_USAG_SET_INFM : "USAG_GRP_CD (cây 2 cấp)"
    SYS_SCR_OPTION_WECMS ||--o{ USER_SCR_OPTION_WECMS : "WEB_SVC_ID+FIELD_ID"
    SYS_ENV_SET_INFM ||--o{ BUSI_ENV_SET_INFM : "ENV_SET_INF"
    TB_FIRM_BANK_CODE ||--o{ TB_FIRM_CONF : "bank code"
    ACCT_MSTR ||--o{ TB_FIRM_CONF : "WDRL_ACT_NO"

    TRCO_INFM { string TRCO_KEY PK
                string BIZ_KEY
                string TRCO_STTS "C=trash" }
    BUSI_USAG_SET_INFM { string BIZ_KEY PK
                         string USE_USAG_CD PK }
    BUSI_VAS_ACCT_INFM { string BIZ_KEY PK
                         string VAS_ACCT_SEQ_NO PK }
    TB_FIRM_CONF { string BIZ_KEY PK
                   string OUT_BANK_CD PK
                   string WDRL_ACT_NO PK }
    SYS_SCR_OPTION_WECMS { string WEB_SVC_ID PK
                           string FIELD_ID PK }
```
- Reference data toàn cục (không tenant-scoped): `NATN_INFM` (quốc gia), `BANK_INFM`/`BANK_INFM_DETL`, `NAPAS_BANK_INFO`, `TB_FIRM_BANK_CODE`.
- View (read-only, không phải bảng): `V_FIRM_BANKING_SETTING`, `V_FIRM_BANKING_HISTORY`, `V_PURPOSE_IN_USE` — gộp dữ liệu cho list/export; entity map nhưng không sinh schema.

## 5. Cụm E-INVOICE (Add-on hóa đơn điện tử)

```mermaid
erDiagram
    BUSA_DOC_MSTR ||--o{ BUSA_DOC_DETL : "DOC_ID (dòng hóa đơn)"
    BUSA_DOC_MSTR ||--o{ EINV_FEE_DETL : "DOC_ID (phí)"
    BUSA_DOC_MSTR ||--o{ EINV_TAX_DETL : "DOC_ID (thuế)"
    BUSA_DOC_MSTR ||--o{ TRCO_ACTV_HSTR : "auto-register đối tác khi scrape"

    BUSA_DOC_MSTR { string BIZ_KEY PK
                    string DOC_ID PK
                    string DOC_TYPE "B=mua/S=bán" }
    BUSA_DOC_DETL { string BIZ_KEY PK
                    string DOC_ID PK
                    int DISP_ORD PK }
    TRCO_ACTV_HSTR { string BIZ_KEY PK
                     string ACTV_SEQ PK }
```
- `BUSA_DOC_MSTR` dùng chung cho cả e-Invoice (sales/expenses) và link card-transaction (cụm 3). `BUSA_DOC_DETL` (V240), `TRCO_ACTV_HSTR` (V241) là bảng mới của phase-2 detail-save; `TRCO_ACTV_HSTR` thuộc họ Contacts nhưng chỉ e-Invoice tiêu thụ.

## 6. Cụm NAVIGATION / NOTIFICATION (refactor-native)

```mermaid
erDiagram
    MENU_ITEM ||--o{ MENU_ITEM : "PARENT_ID (cây menu)"
    MENU_ITEM ||--o{ PRODUCT_MENU : "MENU_ITEM_ID"
    MENU_ITEM ||--o{ MENU_ITEM_TRANSLATION : "MENU_ITEM_ID"
    USER_MENU_AUTH_INFM }o--|| MENU_ITEM : "PERMISSION_CD (gating)"

    MENU_ITEM { string MENU_ITEM_ID PK
                string PARENT_ID
                string PERMISSION_CD "kebab grant" }
    PRODUCT_MENU { string PRODUCT_TYPE PK
                   string MENU_ITEM_ID PK }
    MENU_ITEM_TRANSLATION { string PRODUCT_TYPE PK
                            string MENU_ITEM_ID PK
                            string LANG_CD PK }
    NOTIFICATION { long NOTIFICATION_ID PK
                   string BIZ_KEY
                   string RECIPIENT_ID }
```
- Catalogue menu (V242/V243) thay thế legacy `MENU_AUTH_ADMN_SYS`: `MENU_ITEM` (node + cây tự tham chiếu), `PRODUCT_MENU` (thành viên + thứ tự theo F/M/H), `MENU_ITEM_TRANSLATION` (nhãn theo product+ngôn ngữ). `PERMISSION_CD` giao với `USER_MENU_AUTH_INFM.MENU_AUTH_CD` để gating.
- `NOTIFICATION` (V238/V239): kho thông báo in-app thống nhất (refactor-native, KHÔNG port column-for-column legacy `BLTB_NOTI_*`); 1 row/người nhận (fan-out), `NOTIFICATION_ID` IDENTITY toàn cục, đọc/chưa đọc qua `READ_AT`. Text resolve bằng `MESSAGE_CODE` + `MESSAGE_PARAMS` ở frontend i18n.

## 7. Cross-cutting

```mermaid
erDiagram
    BUSI_INFM ||--o{ AUDIT_LOGS_INFM : "BIZ_KEY (mọi feature CRUD)"
    BUSI_INFM ||--o{ OPRT_STTS_INFO : "BIZ_KEY"
    GDT_CERT_INFO }o--|| BUSI_INFM : "BIZ_KEY (1/tenant)"

    AUDIT_LOGS_INFM { string BIZ_KEY
                      string AUDIT_LOGS_SEQ_NO
                      string WSVC_ID "route slug"
                      string LANG_CD "1 row/locale" }
```
- `AUDIT_LOGS_INFM`: 1 row/locale (EN/KO/VI/KM) chung `AUDIT_LOGS_SEQ_NO`; `WSVC_ID` = route slug (vd `chart-of-accounts`, không phải legacy WSVC id).
