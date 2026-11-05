package com.kosign.wecms.domain.weinvoice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.weinvoice.common.CompanyAccessGuard;
import com.kosign.wecms.domain.weinvoice.common.WeInvoiceIds;
import com.kosign.wecms.domain.weinvoice.common.WeInvoiceJson;
import com.kosign.wecms.domain.weinvoice.dto.ProviderItem;
import com.kosign.wecms.domain.weinvoice.dto.TagItem;
import com.kosign.wecms.domain.weinvoice.dto.TagRequest;
import com.kosign.wecms.domain.weinvoice.repository.ProviderRepository;
import com.kosign.wecms.domain.weinvoice.repository.ProviderRepository.ProviderRow;
import com.kosign.wecms.domain.weinvoice.repository.TagRepository;
import com.kosign.wecms.domain.weinvoice.repository.TagRepository.TagRow;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Screen 16 (provider catalogue) and the tag part of screen 18. */
@Service
public class CatalogService {

    private static final List<String> LINK_STATES = List.of("ok", "check", "missing");

    private final CompanyAccessGuard guard;
    private final ProviderRepository providers;
    private final TagRepository tags;
    private final WeInvoiceJson json;

    public CatalogService(CompanyAccessGuard guard, ProviderRepository providers, TagRepository tags, WeInvoiceJson json) {
        this.guard = guard;
        this.providers = providers;
        this.tags = tags;
        this.json = json;
    }

    public List<ProviderItem> providers(String keyword, String linkStatus) {
        String state = linkStatus == null || linkStatus.isBlank() ? null : linkStatus.trim().toLowerCase(Locale.ROOT);
        if (state != null && !LINK_STATES.contains(state)) {
            throw new IllegalArgumentException("link_status: must be ok, check or missing");
        }
        return providers.list(guard.ownedMsts(), keyword, state).stream().map(this::toItem).toList();
    }

    public List<TagItem> tags() {
        return tags.list(guard.ownerId()).stream().map(CatalogService::toItem).toList();
    }

    public TagItem createTag(TagRequest req) {
        String ownerId = guard.ownerId();
        String name = req.name() == null ? "" : req.name().trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("name: is required");
        }
        if (tags.nameTaken(ownerId, name, null)) {
            throw new BusinessException("WBE3014");
        }
        long id = tags.insert(ownerId, name, req.color(), guard.userId());
        return tags.find(ownerId, id).map(CatalogService::toItem).orElseThrow();
    }

    public TagItem updateTag(String tagId, TagRequest req) {
        String ownerId = guard.ownerId();
        long id = WeInvoiceIds.parse(tagId, "id");
        tags.find(ownerId, id).orElseThrow(() -> new BusinessException("WBE3013"));
        String name = req.name() == null ? null : req.name().trim();
        if (name != null && name.isEmpty()) {
            throw new IllegalArgumentException("name: must not be blank");
        }
        if (name != null && tags.nameTaken(ownerId, name, id)) {
            throw new BusinessException("WBE3014");
        }
        tags.update(ownerId, id, name, req.color(), guard.userId());
        return tags.find(ownerId, id).map(CatalogService::toItem).orElseThrow();
    }

    public void deleteTag(String tagId) {
        long id = WeInvoiceIds.parse(tagId, "id");
        if (tags.delete(guard.ownerId(), id) == 0) {
            throw new BusinessException("WBE3013");
        }
    }

    private ProviderItem toItem(ProviderRow p) {
        List<String> alt = new ArrayList<>();
        JsonNode node = json.tree(p.altLookupUrlsJson());
        for (JsonNode u : node) {
            if (u.isTextual()) alt.add(u.asText());
        }
        String state = p.lookupUrl() == null ? "missing" : p.verifyNote() != null ? "check" : "ok";
        return new ProviderItem(p.order(), p.taxId(), p.name(), p.fullName(), p.web(), p.lookupUrl(), p.captchaUrl(), alt,
                p.verifyNote(), state, p.invoiceCount(), p.autoPdf());
    }

    private static TagItem toItem(TagRow t) {
        return new TagItem(String.valueOf(t.id()), t.name(), t.color(), t.invoiceCount());
    }
}
