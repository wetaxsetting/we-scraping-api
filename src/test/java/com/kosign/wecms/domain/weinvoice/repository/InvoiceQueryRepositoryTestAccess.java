package com.kosign.wecms.domain.weinvoice.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/** Test-only access to the package-private SQL of {@link InvoiceQueryRepository} from other test packages. */
public final class InvoiceQueryRepositoryTestAccess {

    private InvoiceQueryRepositoryTestAccess() {
    }

    public static String needsCodeSql() {
        return InvoiceQueryRepository.NEEDS_CODE;
    }

    public static String filterWhere(InvoiceCriteria c) {
        return InvoiceQueryRepository.filterWhere(c, new MapSqlParameterSource());
    }
}
