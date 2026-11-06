package com.kosign.wecms.domain.weinvoice.config;

import com.kosign.wecms.domain.weinvoice.WeInvoiceTestProps;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Fail-fast validation when enabled; dummies accepted when disabled (bare ${VAR} rule). */
class WeInvoicePropertiesTest {

    @Test
    void disabledAcceptsDummiesAndOpensNothing() {
        WeInvoiceProperties p = WeInvoiceTestProps.disabled();
        assertThat(p.enabled()).isFalse();
        WeInvoiceDb db = new WeInvoiceDb(p);
        assertThat(db.enabled()).isFalse();
        assertThat(db.sql("SELECT 1 FROM {eng}.invoices, {app}.winv_tag_infm"))
                .isEqualTo("SELECT 1 FROM public.invoices, weinvoice.winv_tag_infm");
        db.destroy();
    }

    @Test
    void disabledDbRefusesUseWithWbe3000() {
        WeInvoiceDb db = new WeInvoiceDb(WeInvoiceTestProps.disabled());
        assertThatThrownBy(db::jdbc).hasMessage("WBE3000");
        assertThatThrownBy(db::tx).hasMessage("WBE3000");
    }

    @Test
    void enabledRejectsInjectableSchemaName() {
        assertThatThrownBy(() -> new WeInvoiceProperties(true,
                new WeInvoiceProperties.Db("jdbc:postgresql://h/db", "u", "p", 1),
                "public; DROP TABLE x", "weinvoice", "out", "store", WeInvoiceTestProps.KEY_B64, "", "", 5, false, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("engine-schema")
                .hasMessageNotContaining("DROP");
    }

    @Test
    void enabledRejectsUnresolvedPlaceholderAndShortKey() {
        assertThatThrownBy(() -> new WeInvoiceProperties(true,
                new WeInvoiceProperties.Db("${WECMS_WEINVOICE_DB_URL}", "u", "p", 1),
                "public", "weinvoice", "out", "store", WeInvoiceTestProps.KEY_B64, "", "", 5, false, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("db.url");
        assertThatThrownBy(() -> new WeInvoiceProperties(true,
                new WeInvoiceProperties.Db("jdbc:postgresql://h/db", "u", "p", 1),
                "public", "weinvoice", "out", "store", "c2hvcnQ=", "", "", 5, false, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void partnerKeysAreSplitAndTrimmed() {
        WeInvoiceProperties p = WeInvoiceTestProps.enabled(" k1 , ,k2 ", 5);
        assertThat(p.partnerKeyList()).containsExactly("k1", "k2");
        assertThat(WeInvoiceTestProps.enabled("", 5).partnerKeyList()).isEmpty();
        assertThat(WeInvoiceTestProps.enabled("${WECMS_WEINVOICE_PARTNER_API_KEYS}", 5).partnerKeyList()).isEmpty();
    }

    /**
     * The .env*.example templates are keys-only (SchedulerConfigurationContractTest), so a host .env
     * copied from them carries every WECMS_WEINVOICE_* key blank. Binding that through Spring — not the
     * record constructor — must give a disabled module that opens no connection, not a startup failure.
     */
    @Test
    void blankEnvValuesBindToDisabledModule() {
        Map<String, String> blank = new HashMap<>();
        for (String key : List.of("enabled", "db.url", "db.username", "db.password", "db.max-pool-size",
                "engine-schema", "app-schema", "engine-output-dir", "storage-root", "secret-key",
                "partner-api-keys", "mail-domain", "max-companies", "test-data-enabled", "scraping.url", "scraping.api-key")) {
            blank.put("wecms.weinvoice." + key, "");
        }
        WeInvoiceProperties p = new Binder(new MapConfigurationPropertySource(blank))
                .bindOrCreate("wecms.weinvoice", WeInvoiceProperties.class);

        assertThat(p.enabled()).isFalse();
        assertThat(p.testDataEnabled()).isFalse();
        assertThat(p.maxCompanies()).isEqualTo(5);
        assertThat(p.partnerKeyList()).isEmpty();
        WeInvoiceDb db = new WeInvoiceDb(p);
        assertThat(db.enabled()).isFalse();
        assertThatThrownBy(db::jdbc).hasMessage("WBE3000");
        db.destroy();
    }

    @Test
    void maxCompaniesDefaultsToFive() {
        WeInvoiceProperties p = new WeInvoiceProperties(false, null, null, null, null, null, null, null, null, null, false, null);
        assertThat(p.maxCompanies()).isEqualTo(5);
    }
}
