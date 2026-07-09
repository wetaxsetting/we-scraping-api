package com.kosign.wecms.common.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the shared R-08 download primitive. Locks the behaviour
 * the ERP transaction-history download endpoint relied on before the
 * extraction (octet-stream, RFC 5987 filename, the standard binary
 * headers) plus the path-traversal guard now shared across future
 * download endpoints.
 */
class SecureFileDownloadTest {

    /* ---------------- attachment() — response construction ---------------- */

    @Test
    void attachment_buildsOctetStreamWithStandardDownloadHeaders() {
        byte[] payload = "hello erp world".getBytes();
        Resource body = new ByteArrayResource(payload);

        ResponseEntity<Resource> res =
                SecureFileDownload.attachment(body, "saved.xlsx", payload.length);

        HttpHeaders headers = res.getHeaders();
        assertThat(headers.getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        assertThat(headers.getContentLength()).isEqualTo(payload.length);
        assertThat(headers.getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment")
                .contains("saved.xlsx");
        assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(headers.getFirst("Access-Control-Expose-Headers"))
                .contains(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(res.getBody()).isSameAs(body);
    }

    @Test
    void attachment_encodesNonAsciiFilenameAsRfc5987() {
        // Korean filename — must round-trip via RFC 5987 `filename*=UTF-8''…`
        // so KR/KH/VN filenames survive the Content-Disposition header.
        Resource body = new ByteArrayResource("x".getBytes());

        ResponseEntity<Resource> res =
                SecureFileDownload.attachment(body, "계좌조회.xlsx", 1L);

        String disposition = res.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(disposition).contains("filename*=UTF-8''");
        // The raw non-ASCII bytes are percent-encoded, never emitted literally.
        assertThat(disposition).doesNotContain("계좌조회");
    }

    @Test
    void attachment_preservesResourceSubtypeInReturnType() {
        // Generic signature keeps the caller's static type (the ERP
        // endpoint returns ResponseEntity<InputStreamResource>).
        ByteArrayResource body = new ByteArrayResource("x".getBytes());
        ResponseEntity<ByteArrayResource> res =
                SecureFileDownload.attachment(body, "f.bin", 1L);
        assertThat(res.getBody()).isInstanceOf(ByteArrayResource.class);
    }

    /* ---------------- assertWithinRoot() — traversal guard ---------------- */

    @Test
    void assertWithinRoot_allowsPathInsideRoot(@TempDir Path root) {
        Path child = root.resolve("biz").resolve("erp-transaction-history").resolve("file.xlsx");
        // Should not throw.
        SecureFileDownload.assertWithinRoot(root, child);
    }

    @Test
    void assertWithinRoot_allowsRootItself(@TempDir Path root) {
        SecureFileDownload.assertWithinRoot(root, root);
    }

    @Test
    void assertWithinRoot_rejectsParentTraversal(@TempDir Path root) {
        Path escaped = root.resolve("biz").resolve("..").resolve("..").resolve("etc").resolve("passwd");
        assertThatThrownBy(() -> SecureFileDownload.assertWithinRoot(root, escaped))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("escapes the permitted root");
    }

    @Test
    void assertWithinRoot_rejectsSiblingDirectory(@TempDir Path root) {
        Path sibling = root.resolveSibling("other-tenant").resolve("secret.xlsx");
        assertThatThrownBy(() -> SecureFileDownload.assertWithinRoot(root, sibling))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void assertWithinRoot_rejectsNullInputs(@TempDir Path root) {
        assertThatThrownBy(() -> SecureFileDownload.assertWithinRoot(null, root))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SecureFileDownload.assertWithinRoot(root, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
