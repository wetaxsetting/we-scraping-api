package com.kosign.wecms.domain.weinvoice.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.weinvoice.dto.EngineRequestResponse;
import com.kosign.wecms.domain.weinvoice.dto.RequestAccepted;
import com.kosign.wecms.domain.weinvoice.dto.SyncRunCreateRequest;
import com.kosign.wecms.domain.weinvoice.dto.SyncRunItem;
import com.kosign.wecms.domain.weinvoice.dto.SyncRunProgress;
import com.kosign.wecms.domain.weinvoice.dto.WeInvoicePage;
import com.kosign.wecms.domain.weinvoice.service.SyncRunService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * WeInvoice screen 10 — sync history and bulk sync. The "Đồng bộ" buttons of screens 03, 04
 * and 06 call {@code POST /sync-runs} too.
 */
@RestController
@RequestMapping("/api/v1/weinvoice/sync-runs")
@RequiredArgsConstructor
@Validated
public class WeInvoiceSyncRunController {

    private final SyncRunService syncRuns;

    /** One run per MST, queued for the engine; returns the run ids to poll. */
    @PostMapping
    public ApiResponse<List<RequestAccepted>> create(@Valid @RequestBody SyncRunCreateRequest request) {
        return ApiResponse.ok(syncRuns.create(request));
    }

    @GetMapping("/{runId}")
    public ApiResponse<SyncRunProgress> progress(@PathVariable("runId") String runId) {
        return ApiResponse.ok(syncRuns.progress(runId));
    }

    @PostMapping("/{runId}/cancel")
    public ApiResponse<EngineRequestResponse> cancel(@PathVariable("runId") String runId) {
        return ApiResponse.ok(syncRuns.cancel(runId));
    }

    /** History of the user (or of one MST): requested runs and the engine's scheduled runs. */
    @GetMapping
    public ApiResponse<WeInvoicePage<SyncRunItem>> history(
            @RequestParam(value = "company_mst", required = false) String companyMst,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "0") int size) {
        return ApiResponse.ok(syncRuns.history(companyMst, status, page, size));
    }
}
