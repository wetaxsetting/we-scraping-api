# Preferences — Business Flow (Backend)

End-to-end request flow for every Preferences operation. Source of truth: the implemented Spring code
under `com.kosign.wecms.domain.setting.preferences`. Legacy behaviour reference: live JEX
`comm_0215_001` (`_r001` read, `_u001` save) for the `BUSI_INFM` half.

```
User (tenant admin)
      │
      ▼
Settings modal → Preferences panel
      │   (every request carries the JWT; @menuAuthz.canAccess('settings-core-preference') gates the controller)
      ▼
PreferencesController  ──►  PreferencesService
      │                          │
      │                          ├─ TenantContext.currentBizKey()   (tenant scope, never a param)
      │                          ├─ BusiInfmRepository.findById(bizKey)   (the single tenant row; reused from auth domain)
      │                          ├─ PreferencesLogoResolver               (per-tenant filesystem subtree + read-fallback)
      │                          └─ AuditLogService.append(...)           (profile Update only)
      ▼
ApiResponse<PreferencesResponse>  (lowercase snake_case envelope)
     └─ except GET /logo, which streams raw image bytes (not the JSON envelope)
```

---

## 1. Read profile

```
GET /api/v1/settings/preferences
  → service.get()  @Transactional(readOnly = true)
      → bizKey = requireBizKey()                         (WBE9999 if no tenant context)
      → row = busiInfmRepository.findById(bizKey)        (or WBE2080 — should never happen post-login)
      → toResponse(row)
  → PreferencesResponse { vietnamese_name, display_name, tax_id, representative_name,
                          representative_email, tel_no, fax_no, address{…}, logo_url,
                          modified_by, modified_at }
```

- `logo_url` is `null` when `CMPN_LOGO` is blank; otherwise it is the streaming GET URI with a
  cache-busting `?v=<save_file_nm>` suffix (`/api/v1/settings/preferences/logo?v=…`) so the browser
  refreshes the image after every upload.
- `modified_at` is `MOD_DTM` formatted ISO-8601-with-offset; `modified_by` is `MOD_ID`.

## 2. Save profile

```
PUT /api/v1/settings/preferences   body: PreferencesUpdateRequest
  → @Valid (bean validation: @NotBlank vietnamese_name / tax_id, @Email representative_email,
            @NotNull @Valid address → nested @NotBlank post_address / city / state)
  → service.update(req)  @Transactional
      1. guard: req.address() != null                    → WBE_VALIDATION ("address: is required") if null
      2. bizKey = requireBizKey()
      3. row = busiInfmRepository.findById(bizKey)        (or WBE2080)
      4. apply editable columns (trim required, trimToNull optional):
           CMPN_NM      ← vietnamese_name (trim)
           CMPN_ENM     ← display_name (trimToNull)
           TAX_ID       ← tax_id (trim)
           RPPR_NM      ← representative_name (trimToNull)
           EML          ← representative_email (trim)
           TEL_NO       ← tel_no (trimToNull)
           FXNO         ← fax_no (trimToNull)
           RPRS_BSUN_POST_ADRS ← address.post_address (trim)
           RPRS_BSUN_DTL_ADRS  ← address.detail_address (trimToNull)
           RPRS_BSUN_CITY      ← address.city (trim)
           RPRS_BSUN_ST        ← address.state (trim)
           MOD_ID       ← TenantContext.currentUserId()
           (MOD_DTM bumped by BaseEntity.@PreUpdate)
      5. busiInfmRepository.save(row)
      6. auditLogService.append(UPDATE, wsvc_id='preferences', ref_no='BUSI_INFM', ref_no1=bizKey)
  → PreferencesResponse (refreshed echo)
```

- The logo column (`CMPN_LOGO`) is **intentionally NOT touched** here — it has its own upload/delete
  endpoints so the profile save stays JSON-only. Mirrors legacy `comm_0215_001_u001_act.jsp:48-95`
  (pure UPDATE on the single `BUSI_INFM` row scoped to `BIZ_KEY`).
- The two preference toggles are **not** in this body — they go through the env-settings endpoint.

## 3. Upload logo

```
POST /api/v1/settings/preferences/logo   multipart: file
  → service.uploadLogo(file)  @Transactional
      1. bizKey = requireBizKey(); row = findById(bizKey)            (or WBE2080)
      2. validateFilename(file)                                      → WBE2084 (no usable filename)
      3. validateSize(file)                                          → WBE2085 (empty) / WBE2086 (> 2 MB, {0}=cap)
      4. extension = extensionOf(orgFileNm); validateExtension(...)  → WBE2087 (not in allow-list, {0}=allowed list)
      5. saveFileNm = generateOpaqueFilename(bizKey, extension)
           = base64url(SHA-256(bizKey + System.currentTimeMillis() + 16 random bytes)) + "." + ext
      6. destination = logoResolver.resolve(bizKey, saveFileNm)      (per-tenant subtree)
         Files.createDirectories(parent); file.transferTo(destination)   → WBE2081 on IO failure
      7. previous = row.cmpnLogo;  row.cmpnLogo = saveFileNm;  row.modId = currentUserId();  save(row)
      8. best-effort delete previous file (write-new-before-delete-old ordering)
  → PreferencesResponse (refreshed; logo_url now non-null with the new ?v=)
```

- **Write-new-before-delete-old**: a transient IO failure leaves the previous logo intact rather than
  blanking the display while the DB is inconsistent. Cleanup of the old file is best-effort and never
  fails the user call (an orphaned file is harmless; a stale DB pointer would render a broken image).
- Logo upload writes **no** audit row (legacy parity — JEX does not audit `_c001`/`_c005`).

## 4. Delete (clear) logo

```
DELETE /api/v1/settings/preferences/logo
  → service.deleteLogo()  @Transactional
      1. bizKey = requireBizKey(); row = findById(bizKey)            (or WBE2080)
      2. if CMPN_LOGO is set:
           row.cmpnLogo = null; row.modId = currentUserId(); save(row)
           best-effort delete the disk file (resolveForRead — handles per-tenant + legacy-flat)
      3. (idempotent — no-op when no logo is set)
  → PreferencesResponse (refreshed; logo_url now null)
```

## 5. Stream logo bytes

```
GET /api/v1/settings/preferences/logo
  → service.openLogo()  @Transactional(readOnly = true)
      1. bizKey = requireBizKey(); row = findById(bizKey)            (or WBE2080)
      2. saveFileNm = row.cmpnLogo; if blank                         → WBE2082 ("no logo set")
      3. path = logoResolver.resolveForRead(bizKey, saveFileNm)      (per-tenant, fallback to legacy flat)
      4. if !readable / !regular file                                → WBE2083 ("logo file unavailable")
      5. contentType = MIME_BY_EXT[ext] (deterministic per extension)
      → LogoDownload { path, contentType, size }
  → controller: 200, Content-Type = resolved MIME, Content-Length, inline, cacheControl 5min private,
                X-Content-Type-Options: nosniff, body = InputStreamResource(file)
```

- The handler returns **binary, not the JSON envelope**. Errors still go through
  `GlobalExceptionHandler` (JSON error envelope) so the frontend's `ApiCallError` parsing keeps working
  — the frontend reads the blob, and on `WBE2082` treats it as "no logo set" → placeholder card.
- The streaming GET is JWT-protected; browsers omit the `Authorization` header on a plain `<img src>`,
  so the frontend fetches the blob and pins it to an object URL.

---

## Tenant + audit invariants (every path)

- **Tenant** comes only from `TenantContext.currentBizKey()` (signed JWT) — never a request
  body/param. `BIZ_KEY` is the `BUSI_INFM` primary key.
- **Audit** is written for the profile **Update only**, with `WSVC_ID = 'preferences'`,
  `REF_NO = 'BUSI_INFM'`, `REF_NO1 = bizKey`, and localized strings resolved from
  `audit.preferences.activity` / `audit.preferences.update.detail`. Read, logo upload, and logo delete
  write **no** audit row.
- **Path-escape guard**: `PreferencesLogoResolver` normalizes the resolved path and throws if it
  escapes the tenant subtree (or the storage root for the legacy-flat fallback) — defence in depth on
  top of the opaque, unforgeable save filename.
- See [functional-specification.md §Audit](functional-specification.md) for the column map.
