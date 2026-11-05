package com.kosign.wecms.domain.weinvoice.dto;

import jakarta.validation.constraints.Size;

/** {@code PATCH /companies/{mst}}: label and / or default flag; {@code null} keeps the value. */
public record CompanyPatchRequest(
        @Size(max = 100) String label,
        Boolean defaultCompany) {
}
