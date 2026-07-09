# View Setting — Business Flow (Backend)

**Part of:** Common → view-setting
**Last verified:** 2026-06-23

End-to-end flow for the four operations. Source of truth:
`ScreenOptionController.java` + `ScreenOptionService.java`.

## Layering

```
ScreenOptionController  (@RestController, /api/v1/screen-options, @Validated)
        │  @Pattern guards on webSvcId / dymAreaId
        ▼
ScreenOptionService     (@Service, @Transactional per operation)
        │
        ├── SysScrOptionWecmsRepository   → SYS_SCR_OPTION_WECMS  (read-only at runtime)
        └── UserScrOptionWecmsRepository  → USER_SCR_OPTION_WECMS (read + delete + insert)
        │
        └── TenantContext.currentBizKey() / currentUserId()  (per-request identity)
```

`SYS_SCR_OPTION_WECMS` is never written by user popup actions — only Flyway / admin migrations
touch it (`SysScrOptionWecmsRepository` exposes no write path beyond inherited JPA save, which the
service does not call). All user writes go to `USER_SCR_OPTION_WECMS`.

## Tenant scope

Every user row is scoped to `(BIZ_KEY, USER_ID, WEB_SVC_ID, DYM_AREA_ID)`. `bizKey` + `userId` come
exclusively from `TenantContext` (signed JWT), never from the request. When either is `null`
(no authenticated identity), the service degrades safely: `load` skips the lazy-init and returns
pure sys defaults; `currentUserOverrides` returns an empty map.

## Load — GET `/{webSvcId}/{dymAreaId}` (`ScreenOptionService.load`)

1. `sysRepo.findByWebSvcIdAndDymAreaIdOrderByDispOrdAsc(webSvcId, dymAreaId)`.
2. Empty sys rows → throw `BusinessException("WBE2001", webSvcId+"/"+dymAreaId)`.
3. Read `currentUserOverrides(...)` (map FIELD_ID → user row).
4. **Lazy-init:** if overrides empty AND bizKey/userId non-null → `initUserRowsFromSys(...)` copies
   every sys row into `USER_SCR_OPTION_WECMS` (FIELD_ID, FIELD_NM, DISP_ORD, USE_YN, DISP_YN, SIZE,
   DYM_AREA_ID, REG_ID/MOD_ID = current user), then re-reads overrides. Mirrors legacy
   `comm_0108_001_r001`.
5. `buildMergedResponse(...)` — per sys row, overlay the user's `DISP_ORD` + `USE_YN` when present;
   `DISP_YN`, `SIZE`, `SORT_YN`, `HTML_CONT` always come from the sys row. Sort by effective
   `DISP_ORD` (nulls last via `Integer.MAX_VALUE`).

## Save — PUT `/{webSvcId}/{dymAreaId}` (`ScreenOptionService.save`)

1. `sysByFieldId(...)` — load sys rows (empty → `WBE2001`), index by FIELD_ID.
2. `userRepo.deleteAllForScope(bizKey, userId, webSvcId, dymAreaId)` — wipe the user scope.
3. For each incoming item: skip if FIELD_ID not in the sys map (unknown FIELD_IDs silently dropped).
   - Special FIELD_ID (`CHECKBOX` / `ETC`) → `USE_YN='Y'` + `DISP_ORD` pinned to the sys seed.
   - Non-special → `USE_YN` normalised (`Y`/`N`), `DISP_ORD` = incoming or `autoOrder++` fallback.
   - `FIELD_NM`, `DISP_YN`, `SIZE` copied from the sys row (user cannot set these).
4. `userRepo.saveAll(rows)` then `return load(...)` — response is the freshly merged list.
   Atomic via `@Transactional`. Mirrors legacy `comm_0108_001_u002` (delete-then-insert).

## Reset — DELETE `/{webSvcId}/{dymAreaId}` (`ScreenOptionService.reset`)

1. Load sys rows (empty → `WBE2001`).
2. `userRepo.deleteAllForScope(...)`.
3. If bizKey/userId non-null → `initUserRowsFromSys(...)` immediately re-inits from sys, re-read
   overrides; else overrides = empty.
4. `buildMergedResponse(...)` from the freshly-init'd rows. The user table is always populated
   post-reset. Mirrors legacy `comm_0108_001_r001` lazy-init + `u001` delete in one transaction.

## Areas — GET `/{webSvcId}/areas` (`ScreenOptionService.listAreas`, `@Transactional(readOnly)`)

1. `sysRepo.countColumnsByDymAreaId(webSvcId)` — JPQL `GROUP BY dymAreaId` → `[dymAreaId, count]`.
2. Empty → `WBE2001` (webSvcId only).
3. Map each row to `ScreenAreaListResponse.Area(dymAreaId, columnCount)`. Legacy `SYS_SCR_OPTION_WECMS_R002`.
