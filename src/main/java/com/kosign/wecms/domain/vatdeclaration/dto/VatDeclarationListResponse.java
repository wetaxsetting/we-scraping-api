package com.kosign.wecms.domain.vatdeclaration.dto;

import java.util.List;

public record VatDeclarationListResponse(
        List<VatDeclarationRow> rows,
        long totalCount
) {
}
