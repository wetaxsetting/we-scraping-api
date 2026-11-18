package com.kosign.wecms.domain.weinvoice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** {@code PUT /invoices/tags}: add ({@code on = true}) or remove one tag on many invoices. */
public record TagAssignRequest(
        @NotEmpty @Size(max = 5000) List<String> ids,
        @NotBlank String tagId,
        @NotNull Boolean on) {
}
