package com.kosign.wecms.domain.setting.contacts.dto;

import java.util.List;

/**
 * Wire envelope for
 * {@code GET /api/v1/settings/contacts/{trcoKey}/files}. Single-list
 * envelope — attachments are bounded per-contact so pagination is not
 * needed.
 */
public record ContactFileListResponse(List<ContactFileRow> rows) { }
