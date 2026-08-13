# 01 — AUTH (`domain/auth`) + NAVIGATION (`domain/navigation`)

Wire lowercase snake_case; envelope `{status, data}`.

> **Phân quyền (cập nhật `sit`)**: `@EnableMethodSecurity` đã BẬT trong `SecurityConfig`, nên `@PreAuthorize`/`hasRole('ADMIN')` ở các module khác có hiệu lực. Tuy nhiên các endpoint trong file này (`/auth/*`, `/users`, `/navigation`) KHÔNG có `@PreAuthorize` riêng — chỉ gác bằng quy tắc toàn cục `anyRequest().authenticated()` (trừ `login`/`logout` là Public). Việc phân quyền nội dung của `/navigation` được thực hiện trong service (intersect grants), không qua annotation.

---

## POST `/api/v1/auth/login`
- **Auth**: Public
- **Chức năng**: Đăng nhập 2 bước có hỗ trợ multi-company. Trả JWT khi xác thực OK, hoặc trả chooser khi user có ≥2 link active.
- **Table đọc**: `USER_INFM`, `USER_BUSI_CONN_INFM` (link active `DEL_YN='N'`), `BUSI_INFM` (CMPN_NM/PRDT_TYPE/TAX_ID), `BUSI_ADMN_INFM` (ADMIN/USER)
- **Table ghi/update**: `USER_INFM` (UPDATE USER_PW khi nâng SHA-256→BCrypt), `USER_BUSI_CONN_INFM` (UPDATE LST_LGN_DATE), `USER_LOGIN_HIS` (INSERT mỗi attempt FAIL/SUCCESS)
- **Xử lý**:
  - Controller resolve client IP (ưu tiên `X-Forwarded-For`), map `Accept-Language` → LANG_CD.
  - Tìm USER_INFM; thiếu → FAIL + `WBE1009`.
  - Gate cờ: `DEL_YN=Y`→`WBE1128`; `ACTV_YN=N`→`WBE1010`; `LOCK_YN=Y`→`WBE1011`.
  - `passwordEncoder.matches` sai → FAIL + `WBE1009`; hash cũ → `upgradeEncoding` re-hash lưu lại.
  - Lấy tất cả link active; rỗng → `WBE1009`.
  - Resolve tenant: 1 link → auto-pick (biz_key request phải khớp); ≥2 không biz_key → chooser (KHÔNG cấp JWT/ghi audit); ≥2 có biz_key → tìm link khớp.
  - Update LST_LGN_DATE; INSERT SUCCESS; đọc BUSI_INFM + authGrCd; phát hành JWT `{sub,userNm,bizKey,langCd,authGrCd,prdtType?}`.
- **Gọi khác**: `LoginHistoryService.record` (REQUIRES_NEW), `JwtTokenProvider.issue`, `WecmsPasswordEncoder`

## GET `/api/v1/auth/me`
- **Auth**: JWT
- **Chức năng**: Snapshot phiên hiện tại để frontend rehydrate.
- **Table đọc**: `USER_INFM`, `USER_BUSI_CONN_INFM`, `BUSI_INFM` (CMPN_NM/TAX_ID re-query)
- **Table ghi/update**: —
- **Xử lý**: đọc `TenantContext`; thiếu → `WBE1401`; verify user còn hợp lệ + link tới tenant trong JWT còn active; `authGrCd`/`prdtType` lấy từ claim (không re-query). Trả `MeResponse`.

## POST `/api/v1/auth/logout`
- **Auth**: Public — **Chức năng**: Stub (client tự xóa token). Không đọc/ghi DB.

## POST `/api/v1/auth/change-password`
- **Auth**: JWT (userId lấy từ TenantContext)
- **Chức năng**: Tự đổi mật khẩu (port `comm_0400_001_u001`).
- **Table đọc**: `USER_INFM`, `USER_PW_CHG_HIS` (3 row gần nhất + MAX REG_SEQ_NO)
- **Table ghi/update**: `USER_INFM` (USER_PW), `USER_PW_CHG_HIS` (INSERT REG_SEQ_NO=MAX+1, lưu IP)
- **Xử lý**: gate cờ user → `WBE1401`; new≠confirm → `WBE_CHANGE_PASSWORD_MISMATCH`; current sai → `WBE2091`; trùng 3 mật khẩu gần nhất → `WBE_CHANGE_PASSWORD_REUSED`; encode + UPDATE + INSERT history.

## GET `/api/v1/users?q=`
- **Auth**: JWT (chỉ gate JWT, không @PreAuthorize)
- **Chức năng**: Danh bạ user active của tenant (picker approver/signer) — port `comm_0192_001_r001`.
- **Table đọc**: `USER_BUSI_CONN_INFM` ⋈ `USER_INFM` (USER_NM/POSI_NM/DEPT_NM)
- **Table ghi/update**: —
- **Xử lý**: `q` (≤100, optional, ILIKE trên USER_NM); scope `biz_key`+`del_yn='N'`; sort USER_NM/USER_ID ASC; `user_img_url=/api/v1/users/{id}/image` nếu USER_IMG có (endpoint stream ảnh chưa tồn tại — chỉ là template URL, frontend `<Avatar>` tự fallback khi 404).

---

## GET `/api/v1/navigation` (`domain/navigation`)
- **Auth**: JWT (gác bằng `anyRequest().authenticated()`; không `@PreAuthorize`)
- **Chức năng**: Trả cây menu (top nav + sidebar) đã resolve cho phiên hiện tại. Quy tắc: `Visible Menu = Product Menu Tree (theo prdt_type) ∩ User Permission`. Admin (`authGrCd=ADMIN`) bỏ qua phần ∩ grant cá nhân, thấy toàn bộ cây của product (vẫn scope theo product, không union chéo product).
- **Table đọc**: `PRODUCT_MENU` (membership + disp_ord theo product_type), `MENU_ITEM` (catalogue), `MENU_ITEM_TRANSLATION` (label theo lang_cd, EN làm fallback), `USER_MENU_AUTH_INFM` (grant `USE_YN='Y'` của user thường — qua `MenuAuthorizationService`)
- **Table ghi/update**: — (read-only)
- **Xử lý**:
  - Đọc `TenantContext`; thiếu userId/bizKey → `WBE1401`.
  - `prdtType` lấy từ JWT (không nhận từ client). `PrdtType.fromCode` không nhận diện được (null hoặc 11 letter deferred) → trả cây RỖNG, well-formed (không throw).
  - Map `prdt_type` → menu-group id (echo traceability): `F→WE_CMSVN`, `M→WE_CMSVN_LUMENS`, `H→WE_CMSVN_HAENGSUNG`. Không có `PRODUCT_MENU` rows cho product → cây rỗng.
  - Lọc theo `SHOW_YN='Y'`; leaf hiện khi (admin HOẶC có grant `permission_cd`); parent tự thu gọn khi mọi con bị lọc hết (parity `OSMCMenuHelper.isPermittedMenu`).
  - Label theo ngôn ngữ phiên (EN/KO/VI; KM hoặc null → EN), phủ lớp EN fallback.
- **Gọi khác**: `MenuAuthorizationService.grantedMenuCds(bizKey, userId)` (cùng resolver `/auth/me` dùng)
- **Trả về**: `NavigationResponse {product_type, menu_group_id, top_menus[]}`; mỗi node `NavMenuNode` mang `permission_cd`, `parent_id`, `menu_dep`, `disp_ord`, `label`, `route`, `icon`, `legacy_mnu_id`, `legacy_wsvc_id`, `children[]`.
