package com.kosign.wecms.domain.setting.zalooa.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.setting.zalooa.dto.ZaloAuthorizeRequest;
import com.kosign.wecms.domain.setting.zalooa.dto.ZaloOaListResponse;
import com.kosign.wecms.domain.setting.zalooa.dto.ZaloOaRegisterRequest;
import com.kosign.wecms.domain.setting.zalooa.dto.ZaloOaRow;
import com.kosign.wecms.domain.setting.zalooa.dto.ZaloOaUpdateRequest;
import com.kosign.wecms.domain.setting.zalooa.dto.ZaloSendHistoryListResponse;
import com.kosign.wecms.domain.setting.zalooa.dto.ZaloSendRequest;
import com.kosign.wecms.domain.setting.zalooa.dto.ZaloSendResponse;
import com.kosign.wecms.domain.setting.zalooa.dto.ZaloTemplateRow;
import com.kosign.wecms.domain.setting.zalooa.dto.ZaloTemplateUpsertRequest;
import com.kosign.wecms.domain.setting.zalooa.service.ZaloMessageService;
import com.kosign.wecms.domain.setting.zalooa.service.ZaloOaService;
import com.kosign.wecms.domain.setting.zalooa.service.ZaloSendHistoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Settings &gt; Zalo OA — connection, template and send-log surface under
 * {@code /api/v1/settings/zalo-oa}.
 *
 * <table>
 *   <caption>Endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Purpose</th></tr>
 *   <tr><td>GET</td><td>{@code /}</td><td>list the tenant's connections + templates</td></tr>
 *   <tr><td>POST</td><td>{@code /}</td><td>register an OA id (step 1 of OAuth)</td></tr>
 *   <tr><td>POST</td><td>{@code /{oa_id}/authorize}</td><td>exchange the consent code (step 2)</td></tr>
 *   <tr><td>PUT</td><td>{@code /{oa_id}}</td><td>rename / make it the sending OA</td></tr>
 *   <tr><td>DELETE</td><td>{@code /{oa_id}}</td><td>disconnect (soft delete)</td></tr>
 *   <tr><td>PUT</td><td>{@code /{oa_id}/templates}</td><td>record a Zalo-approved template id</td></tr>
 *   <tr><td>DELETE</td><td>{@code /{oa_id}/templates}</td><td>remove a template registration</td></tr>
 *   <tr><td>GET</td><td>{@code /send-history}</td><td>outbound attempts, newest first</td></tr>
 *   <tr><td>POST</td><td>{@code /test-send}</td><td>diagnostic send to an explicit number</td></tr>
 * </table>
 *
 * <p><b>No public endpoint is added by this feature.</b> Zalo's OAuth redirect goes
 * to a <em>frontend</em> route, which then calls {@code /authorize} with the user's
 * JWT — so this is an ordinary authenticated surface. That matters because the one
 * remaining piece of the Zalo integration that <em>would</em> need an
 * unauthenticated endpoint is the delivery webhook, and this repo already carries
 * F-09 (callback hardening) as a PROD blocker for its two existing unauthenticated
 * callbacks. Adding a third before that is closed would be the wrong order.
 *
 * <p>Authorization: class-level
 * {@code @PreAuthorize("@menuAuthz.canAccess('settings-core-zalo-oa')")}, matching
 * the {@code settings-core-*} family. {@code settings-*} codes are
 * product-invariant, so {@code MenuAuthorizationGuard} skips the product-tree gate:
 * an ADMIN passes unconditionally, a normal user needs a {@code USE_YN='Y'} grant
 * for {@code settings-core-zalo-oa} in {@code USER_MENU_AUTH_INFM}. Being
 * class-level, it also satisfies {@code EndpointAuthorizationCoverageTest} without
 * an allow-list entry.
 *
 * <p><b>Menu Entitlement follow-up:</b> until {@code settings-core-zalo-oa} is added
 * to the frontend entitlement tree
 * ({@code wecms-web/components/settings/users/entitlementTree.ts}), no grant can be
 * issued through the UI, so a normal user cannot reach these endpoints and an
 * administrator must. That is the correct failure direction — closed, not open — but
 * it is a real gap, not the finished state.
 *
 * <p>Wire format is lowercase snake_case via the global Jackson naming strategy;
 * path and query parameters are pinned to their snake_case names explicitly.
 */
@RestController
@RequestMapping("/api/v1/settings/zalo-oa")
@RequiredArgsConstructor
@Validated
@PreAuthorize("@menuAuthz.canAccess('settings-core-zalo-oa')")
public class ZaloOaController {

    /**
     * A tenant has a handful of Official Accounts at most — one active by
     * construction — so the cap exists to bound a hostile or buggy {@code size},
     * not to serve a real page that large.
     */
    private static final int MAX_PAGE_SIZE = 100;

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final ZaloOaService oaService;
    private final ZaloMessageService messageService;
    private final ZaloSendHistoryService historyService;

    @GetMapping
    public ApiResponse<ZaloOaListResponse> list(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        return ApiResponse.ok(oaService.list(pageable(page, size)));
    }

    @PostMapping
    public ApiResponse<ZaloOaRow> register(@Valid @RequestBody ZaloOaRegisterRequest request) {
        return ApiResponse.ok(oaService.register(request));
    }

    /**
     * Complete the OAuth handshake for one connection.
     *
     * <p>{@code POST} rather than {@code PATCH}: this is a domain action with a
     * side effect at the vendor (a code is spent, tokens are issued), not a partial
     * update of the resource's fields.
     */
    @PostMapping("/{oa_id}/authorize")
    public ApiResponse<ZaloOaRow> authorize(
            @PathVariable("oa_id") @NotBlank String oaId,
            @Valid @RequestBody ZaloAuthorizeRequest request) {
        return ApiResponse.ok(oaService.authorize(oaId, request));
    }

    @PutMapping("/{oa_id}")
    public ApiResponse<ZaloOaRow> update(
            @PathVariable("oa_id") @NotBlank String oaId,
            @Valid @RequestBody ZaloOaUpdateRequest request) {
        return ApiResponse.ok(oaService.update(oaId, request));
    }

    @DeleteMapping("/{oa_id}")
    public ApiResponse<Void> disconnect(@PathVariable("oa_id") @NotBlank String oaId) {
        oaService.disconnect(oaId);
        return ApiResponse.ok(null);
    }

    /**
     * Record or update one template id. {@code PUT} because the caller names the
     * full identity of the row — {@code (tmpl_type, lang_cd)} plus the path
     * {@code oa_id} — so the write is a full replacement of that row, whether or
     * not it existed.
     */
    @PutMapping("/{oa_id}/templates")
    public ApiResponse<ZaloTemplateRow> upsertTemplate(
            @PathVariable("oa_id") @NotBlank String oaId,
            @Valid @RequestBody ZaloTemplateUpsertRequest request) {
        return ApiResponse.ok(oaService.upsertTemplate(oaId, request));
    }

    @DeleteMapping("/{oa_id}/templates")
    public ApiResponse<Void> deleteTemplate(
            @PathVariable("oa_id") @NotBlank String oaId,
            @RequestParam("tmpl_type") @NotBlank String tmplType,
            @RequestParam("lang_cd") @NotBlank String langCd) {
        oaService.deleteTemplate(oaId, tmplType, langCd);
        return ApiResponse.ok(null);
    }

    @GetMapping("/send-history")
    public ApiResponse<ZaloSendHistoryListResponse> sendHistory(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        return ApiResponse.ok(historyService.list(pageable(page, size)));
    }

    /**
     * Diagnostic send to an explicit number.
     *
     * <p><b>This is not the production send API.</b> Who receives a Zalo message in
     * WeCMS is still an open product decision, so there is nothing to resolve a
     * recipient from yet — see {@link ZaloSendRequest}. It exists so an
     * administrator can verify the whole chain (token exchange → template
     * resolution → Zalo call → send log) against their own phone before any
     * business event is wired to it.
     *
     * <p>Returns HTTP 200 with {@code success=false} when Zalo declines, per this
     * repo's business-outcome contract. Either way the response carries the
     * {@code send_seq} of the logged attempt.
     */
    @PostMapping("/test-send")
    public ApiResponse<ZaloSendResponse> testSend(@Valid @RequestBody ZaloSendRequest request) {
        return ApiResponse.ok(messageService.send(request));
    }

    /**
     * Clamp rather than reject an out-of-range {@code size}. A stale frontend
     * bundle asking for 500 rows should get 100, not a validation error — the same
     * degrade-gracefully stance the sort whitelist takes for an unknown sort field.
     */
    private static Pageable pageable(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize);
    }
}
