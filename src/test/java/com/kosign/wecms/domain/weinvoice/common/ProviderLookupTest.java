package com.kosign.wecms.domain.weinvoice.common;

import com.kosign.wecms.domain.weinvoice.repository.InvoiceCriteria;
import com.kosign.wecms.domain.weinvoice.repository.InvoiceQueryRepositoryTestAccess;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "Needs a lookup code" by provider (docs/issues/weinvoice-needs-lookup-code-by-provider-2026-11-18.md): after the engine
 * read codes from TTKhac by meaning, 8,550 invoices still showed "!" — 4,327 of them already had the original PDF, and
 * BKAV / Vĩnh Hy / Minh Khang / My Software / VININVOICE / Mắt Bão look invoices up without a code.
 */
class ProviderLookupTest {

    @Test
    void needsCodeOnlyForACatalogueProviderThatNeedsOneWhileNothingIsThereYet() {
        assertThat(ProviderLookup.needsCode("0104128565", true, false, false)).isTrue();          // FPT: code only in mail
        assertThat(ProviderLookup.needsCode("0106026495-001", true, false, false)).isTrue();      // branch suffix
        assertThat(ProviderLookup.needsCode("0104128565", true, false, true)).isFalse();          // original PDF stored
        assertThat(ProviderLookup.needsCode("0104128565", true, true, false)).isFalse();          // code / link present
        assertThat(ProviderLookup.needsCode("0104128565", false, false, false)).isFalse();        // not in the catalogue
        assertThat(ProviderLookup.needsCode(null, true, false, false)).isFalse();
        for (String mst : List.of("0101360697", "0314743623", "0314209362", "0108971656", "0109282176", "0302712571")) {
            assertThat(ProviderLookup.needsCode(mst, true, false, false)).as(mst).isFalse();      // looked up without a code
        }
    }

    @Test
    void theFilterAndFacetSqlApplyTheSameRule() {
        String sql = InvoiceQueryRepositoryTestAccess.needsCodeSql();
        assertThat(sql).contains("NOT c.has_pdf", "split_part(c.prvd_mst_xml, '-', 1) NOT IN (", "'0101360697'", "'0302712571'",
                "NULLIF(btrim(c.ma_tra_cuu), '') IS NULL", "{app}.winv_lkup_cd lc3");
        assertThat(ProviderLookup.WITHOUT_CODE_SQL).doesNotContain(";").matches("('[0-9]{10}'(, )?)+");
        // the provider filter uses it
        InvoiceCriteria c = new InvoiceCriteria("u@x", "1201496252", null, LocalDate.of(2026, 1, 1), null, null, null, null,
                List.of(), null, List.of(), "needs_code", List.of(), List.of(), null, true);
        assertThat(InvoiceQueryRepositoryTestAccess.filterWhere(c)).contains("NOT c.has_pdf");
    }
}
