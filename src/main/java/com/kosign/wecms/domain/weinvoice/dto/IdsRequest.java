package com.kosign.wecms.domain.weinvoice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Body carrying invoice ids, scoped to the working MST. */
public record IdsRequest(
        @NotBlank String companyMst,
        @NotEmpty @Size(max = 5000) List<String> ids) {
}
