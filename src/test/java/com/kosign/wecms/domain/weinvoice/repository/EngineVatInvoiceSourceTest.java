package com.kosign.wecms.domain.weinvoice.repository;

import com.kosign.wecms.domain.vatdeclaration.entity.VatDeclaration;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceDb;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Query contract of {@link EngineVatInvoiceSource} (no embedded DB in this repository). The SQL itself was run
 * read-only against the engine DB on 2026-11-25 — see the test-result record of
 * docs/features/addon-service/weinvoice/weinvoice-vat-declaration-engine-invoices-2026-11-25.md.
 */
class EngineVatInvoiceSourceTest {

    private static VatDeclaration decl() {
        VatDeclaration d = VatDeclaration.builder()
                .bizKey("WI:0123456789abcdef0").tpTaxCode("0319232069")
                .fromDt(LocalDate.of(2026, 10, 1)).toDt(LocalDate.of(2026, 10, 31)).status("DRAFT")
                .build();
        d.setSid(5L);
        return d;
    }

    @Test
    @DisplayName("scope: the declaration's MST and period; WeCMS doc types I/B become directions ban/mua")
    void params() {
        MapSqlParameterSource p = EngineVatInvoiceSource.params(decl(), List.of("I", "B"), "8%", "  cước ");
        assertThat(p.getValue("mst")).isEqualTo("0319232069");
        assertThat(p.getValue("fromDt")).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(p.getValue("toDt")).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(p.getValue("directions")).isEqualTo(List.of("ban", "mua"));
        assertThat(p.getValue("rate")).isEqualTo("8%");
        assertThat(p.getValue("search")).isEqualTo("cước");

        assertThat(EngineVatInvoiceSource.params(decl(), List.of("B"), null, " ").getValue("directions"))
                .isEqualTo(List.of("mua"));
        assertThat(EngineVatInvoiceSource.params(decl(), List.of("B"), null, " ").getValue("search")).isNull();
        // an empty direction list must not produce "IN ()"
        assertThat(EngineVatInvoiceSource.params(decl(), List.of(), null, null).getValue("directions"))
                .isEqualTo(List.of("-"));
    }

    @Test
    @DisplayName("SQL: replaced / cancelled excluded, per-rate summary → lines → header, VND conversion")
    void sqlContract() {
        for (String sql : List.of(EngineVatInvoiceSource.SQL_SYNC, EngineVatInvoiceSource.SQL_SALES_VAT,
                EngineVatInvoiceSource.SQL_GROUPS, EngineVatInvoiceSource.SQL_ROWS)) {
            assertThat(sql)
                    .contains("i.company_mst = :mst")
                    .contains("i.ngay_lap BETWEEN :fromDt AND :toDt")
                    .contains("i.direction IN (:directions)")
                    .contains("NOT IN (4, 6)")
                    .contains("'theo_thue_suat'")
                    .contains("{eng}.invoice_details")
                    .contains("NOT EXISTS (SELECT 1 FROM summ WHERE summ.id = inv.id)")
                    .contains("NOT EXISTS (SELECT 1 FROM lines WHERE lines.id = inv.id)")
                    .contains("'ty_gia'")
                    .doesNotContain("busa_doc");
        }
        assertThat(EngineVatInvoiceSource.SQL_ROWS).contains("OFFSET :offset LIMIT :size");
        assertThat(EngineVatInvoiceSource.SQL_GROUPS).contains("floor(p.sply * 2 / 100)");
    }

    @Test
    @DisplayName("lock / unlock never write engine rows")
    void markersAreNoOps() {
        WeInvoiceDb db = mock(WeInvoiceDb.class);
        EngineVatInvoiceSource source = new EngineVatInvoiceSource(db);
        source.markProcessed(decl(), List.of("I", "B"), List.of());
        source.clearProcessed(decl());
        verifyNoInteractions(db);
    }
}
