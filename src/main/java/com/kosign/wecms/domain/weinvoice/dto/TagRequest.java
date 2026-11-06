package com.kosign.wecms.domain.weinvoice.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Create ({@code name} required) or patch a tag. {@code color} is a CSS hex colour. */
public record TagRequest(
        @Size(min = 1, max = 50) String name,
        @Pattern(regexp = "#[0-9a-fA-F]{3,8}", message = "must be a hex colour such as #1e7b45") String color) {
}
