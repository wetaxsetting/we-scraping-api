package com.kosign.wecms.domain.setting.purposesettings.dto;

import java.util.List;

/** Outer envelope for the list endpoint: {@code { list: [...] }}. */
public record PurposeSettingListResponse(List<PurposeSettingRow> list) { }
