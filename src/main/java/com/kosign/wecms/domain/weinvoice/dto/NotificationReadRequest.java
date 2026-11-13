package com.kosign.wecms.domain.weinvoice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** {@code POST /notifications/read} — notification ids to mark read (unknown ids are ignored). */
public record NotificationReadRequest(
        @NotNull @Size(max = 100) List<String> ids) {
}
