package com.kosign.wecms.domain.setting.preferences.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.setting.preferences.dto.PreferencesResponse;
import com.kosign.wecms.domain.setting.preferences.dto.PreferencesUpdateRequest;
import com.kosign.wecms.domain.setting.preferences.service.PreferencesService;
import com.kosign.wecms.domain.setting.preferences.service.PreferencesService.LogoDownload;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * Settings &gt; Preferences — company-profile read + update + logo
 * upload / delete / stream endpoints.
 *
 * <p>Single-row resource keyed on the JWT-derived tenant. Six routes:
 * <ul>
 *   <li>{@code GET    /api/v1/settings/preferences}        — read profile</li>
 *   <li>{@code PUT    /api/v1/settings/preferences}        — save profile</li>
 *   <li>{@code POST   /api/v1/settings/preferences/logo}   — upload logo (multipart)</li>
 *   <li>{@code DELETE /api/v1/settings/preferences/logo}   — clear logo</li>
 *   <li>{@code GET    /api/v1/settings/preferences/logo}   — stream raw bytes (for &lt;img src&gt;)</li>
 * </ul>
 *
 * <p>The two preference toggles (Use Purpose / Use VAS Account) are
 * NOT here — they route through the generic env-settings endpoint
 * pair with codes {@code 00077} / {@code 00074}.
 */
@RestController
@RequestMapping("/api/v1/settings/preferences")
@RequiredArgsConstructor
public class PreferencesController {

    private final PreferencesService service;

    @GetMapping
    public ApiResponse<PreferencesResponse> get() {
        return ApiResponse.ok(service.get());
    }

    @PutMapping
    public ApiResponse<PreferencesResponse> update(
            @Valid @RequestBody PreferencesUpdateRequest body) {
        return ApiResponse.ok(service.update(body));
    }

    /**
     * Upload a new company logo. Multipart field name is {@code file}.
     * Service caps at 2 MB and the extension allow-list
     * (JPG / JPEG / PNG / GIF / SVG). On success the response carries
     * the refreshed profile with a non-null {@code logo_url} pointing
     * at the streaming GET below. The cache-busting suffix on
     * {@code logo_url} flips on every upload so the browser refreshes
     * the image without manual reload.
     */
    @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PreferencesResponse> uploadLogo(
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(service.uploadLogo(file));
    }

    /**
     * Clear the company logo. Idempotent — a tenant with no logo set
     * gets a 200 and the unchanged profile back.
     */
    @DeleteMapping("/logo")
    public ApiResponse<PreferencesResponse> deleteLogo() {
        return ApiResponse.ok(service.deleteLogo());
    }

    /**
     * Stream the raw logo bytes back. Used by the frontend's
     * {@code <img src>}. {@code WBE2082} when no logo is set;
     * {@code WBE2083} when the row points at a missing physical file.
     *
     * <p>The handler returns binary, not the standard JSON envelope —
     * the {@code ApiResponse} wrapper is reserved for JSON content.
     * Errors still go through the {@code GlobalExceptionHandler} which
     * emits the JSON error envelope on the same content type the
     * client requested, so the frontend's existing
     * {@code ApiCallError} parsing keeps working.
     */
    @GetMapping("/logo")
    public ResponseEntity<InputStreamResource> streamLogo() throws IOException {
        LogoDownload logo = service.openLogo();
        InputStream stream = Files.newInputStream(logo.path());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(logo.contentType()))
                .contentLength(logo.size())
                .header("X-Content-Type-Options", "nosniff")
                // Allow the browser to cache the bytes; the URL on
                // PreferencesResponse.logoUrl carries a ?v=<filename>
                // cache-buster that flips on every upload so a stale
                // cache cannot survive a logo change.
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofMinutes(5)).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(new InputStreamResource(stream));
    }
}
