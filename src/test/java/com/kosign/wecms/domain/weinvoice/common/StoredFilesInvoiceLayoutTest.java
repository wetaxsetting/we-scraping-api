package com.kosign.wecms.domain.weinvoice.common;

import com.kosign.wecms.domain.weinvoice.config.WeInvoiceProperties;
import com.kosign.wecms.domain.weinvoice.WeInvoiceTestProps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Uploaded invoice XML / PDF are kept in the engine's layout: company / yyyy / MM (invoice date) / seller / name. */
class StoredFilesInvoiceLayoutTest {

    @TempDir
    Path root;

    private StoredFiles storage() {
        return new StoredFiles(new WeInvoiceProperties(true,
                new WeInvoiceProperties.Db("jdbc:postgresql://h/db", "u", "p", 1),
                "public", "weinvoice", root.resolve("out").toString(), root.resolve("store").toString(),
                WeInvoiceTestProps.KEY_B64, "", "", 5, false, null));
    }

    @Test
    void savedUnderTheInvoiceDateAndSeller() throws Exception {
        StoredFiles.Saved s = storage().saveInvoiceFile("lamine@gmail.com", "1201496252", "2026-11-02",
                "1201496252", "1_C26TWE_4.pdf", "%PDF-1.4".getBytes());
        assertThat(s.relativePath()).isEqualTo("lamine_gmail_com/invoices/1201496252/2026/11/1201496252/1_C26TWE_4.pdf");
        assertThat(Files.readString(root.resolve("store").resolve(s.relativePath()))).isEqualTo("%PDF-1.4");
    }

    @Test
    void secondUploadOfTheSameInvoiceKeepsTheFirstFile() throws Exception {
        StoredFiles st = storage();
        StoredFiles.Saved a = st.saveInvoiceFile("o", "1201496252", "2026-11-02", "0101243150", "1_C26TAA_123.pdf", "a".getBytes());
        StoredFiles.Saved b = st.saveInvoiceFile("o", "1201496252", "2026-11-02", "0101243150", "1_C26TAA_123.pdf", "b".getBytes());
        assertThat(b.relativePath()).endsWith("/0101243150/1_C26TAA_123_2.pdf");
        assertThat(Files.readString(root.resolve("store").resolve(a.relativePath()))).isEqualTo("a");
    }
}
