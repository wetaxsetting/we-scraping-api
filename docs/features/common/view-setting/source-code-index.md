# View Setting — Source Code Index

**Part of:** Common → view-setting
**Last verified:** 2026-06-23

All paths absolute-from-repo-root. Verified present 2026-06-23.

## Backend (`wecms-api`)

| Layer | File |
|---|---|
| Controller | `src/main/java/com/kosign/wecms/domain/setting/controller/ScreenOptionController.java` |
| Service | `src/main/java/com/kosign/wecms/domain/setting/service/ScreenOptionService.java` |
| Entity (sys) | `src/main/java/com/kosign/wecms/domain/setting/entity/SysScrOptionWecms.java` |
| Entity (user) | `src/main/java/com/kosign/wecms/domain/setting/entity/UserScrOptionWecms.java` |
| PK class (sys) | `src/main/java/com/kosign/wecms/domain/setting/entity/SysScrOptionWecmsId.java` — `(webSvcId, fieldId)` |
| PK class (user) | `src/main/java/com/kosign/wecms/domain/setting/entity/UserScrOptionWecmsId.java` — `(bizKey, userId, webSvcId, fieldId)` |
| Repository (sys) | `src/main/java/com/kosign/wecms/domain/setting/repository/SysScrOptionWecmsRepository.java` |
| Repository (user) | `src/main/java/com/kosign/wecms/domain/setting/repository/UserScrOptionWecmsRepository.java` |
| DTO — item | `src/main/java/com/kosign/wecms/domain/setting/dto/ScreenOptionItem.java` |
| DTO — list resp | `src/main/java/com/kosign/wecms/domain/setting/dto/ScreenOptionListResponse.java` |
| DTO — areas resp | `src/main/java/com/kosign/wecms/domain/setting/dto/ScreenAreaListResponse.java` |
| DTO — save req | `src/main/java/com/kosign/wecms/domain/setting/dto/SaveScreenOptionRequest.java` |
| Schema/seed | `src/main/resources/db/migration/V30..V42` + per-feature seeds (V153/V154/V88) — see database-mapping.md |

> Neither `SysScrOptionWecmsId` nor `UserScrOptionWecmsId` includes `dymAreaId` (⚠️ see
> database-mapping.md / refactor-gap-analysis.md #13).

## Frontend (`wecms-web`)

| Layer | File |
|---|---|
| Popover | `components/shared/view-setting/ViewSettingPopover.tsx` |
| Hook | `components/shared/view-setting/useViewSetting.ts` (also exports `VIEW_SETTING_SPECIAL_FIELD_IDS`, `isSpecialViewSettingField`) |
| Columns hook | `components/shared/view-setting/useViewSettingColumns.ts` |
| API client | `lib/api/screenOption.ts` (`fetchScreenOptions` / `fetchScreenAreas` / `saveScreenOptions` / `resetScreenOptions`) |
| screenId registry | `lib/viewSettingScreens.ts` (`SCREEN_IDS`, `DEFAULT_AREA_ID`, `SCREEN_ID_TO_ROUTE`, `isKnownScreenId`) |
| Toolbar trigger | `components/shared/PageActionButtons.tsx` (`showList` + `onList(rect)`) |
| Reference page | `components/shared/AccountInquiryPage.tsx` (first consumer) |

> FE `VIEW_SETTING_SPECIAL_FIELD_IDS` = `{CHECKBOX, CHECKBOX_1, ETC, MEMO}` — wider than backend
> `SPECIAL_FIELD_IDS = {CHECKBOX, ETC}` (the `_1` / `MEMO` variants are the V237 Card Transactions
> two-area schema). Backend only locks the canonical two; FE additionally hides the variants.

## JEX (read-only reference)

| Item | Path |
|---|---|
| Functional spec | `docs/jex/features/view-setting.md` |
| Legacy WSVC | `comm_0108_001_r001_osmc` / `_u002_osmc` / `_u001_osmc` / `SYS_SCR_OPTION_WECMS_R002` |
| Legacy IDO (popover filter) | `IDO.USER_SCR_OPTION_WECMS_R002.xml` (`WHERE USR.DISP_YN='Y'`) |

## Tests

`⚠️ UNVERIFIED / NOT FOUND` — no `ScreenOptionServiceTest` (or any View Setting test) was found under
`wecms-api/src/test/**` as of 2026-06-23 (glob `**/ScreenOption*.java` → no matches). The prompt
listed `ScreenOptionServiceTest` as expected; it does not exist on disk. See test-scenarios.md for
the coverage gap.
