package com.kosign.wecms.domain.weinvoice.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.weinvoice.dto.ProviderItem;
import com.kosign.wecms.domain.weinvoice.dto.TagItem;
import com.kosign.wecms.domain.weinvoice.dto.TagRequest;
import com.kosign.wecms.domain.weinvoice.service.CatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** WeInvoice screen 16 (provider catalogue) and the tags of screen 18. */
@RestController
@RequestMapping("/api/v1/weinvoice")
@RequiredArgsConstructor
@Validated
public class WeInvoiceCatalogController {

    private final CatalogService catalog;

    @GetMapping("/providers")
    public ApiResponse<List<ProviderItem>> providers(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "link_status", required = false) String linkStatus) {
        return ApiResponse.ok(catalog.providers(keyword, linkStatus));
    }

    @GetMapping("/tags")
    public ApiResponse<List<TagItem>> tags() {
        return ApiResponse.ok(catalog.tags());
    }

    @PostMapping("/tags")
    public ApiResponse<TagItem> createTag(@Valid @RequestBody TagRequest request) {
        return ApiResponse.ok(catalog.createTag(request));
    }

    @PatchMapping("/tags/{id}")
    public ApiResponse<TagItem> updateTag(@PathVariable("id") String id, @Valid @RequestBody TagRequest request) {
        return ApiResponse.ok(catalog.updateTag(id, request));
    }

    @DeleteMapping("/tags/{id}")
    public ApiResponse<Void> deleteTag(@PathVariable("id") String id) {
        catalog.deleteTag(id);
        return ApiResponse.ok(null);
    }
}
