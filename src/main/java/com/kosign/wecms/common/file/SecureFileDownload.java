package com.kosign.wecms.common.file;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Shared safety primitive for binary file-download endpoints (audit
 * finding R-08). Two concerns that every download handler previously
 * re-implemented by hand now live in one place:
 *
 * <ol>
 *   <li><b>Path-traversal protection</b> — {@link #assertWithinRoot} —
 *       normalise the resolved path and reject anything that escapes the
 *       permitted root subtree. Extracted from the defence-in-depth check
 *       originally inlined in {@code ErpTrnsHistoryFileResolver}.</li>
 *   <li><b>Attachment response construction</b> — {@link #attachment} —
 *       build a {@code ResponseEntity} carrying the binary body with an
 *       RFC 5987-encoded {@code Content-Disposition} filename plus the
 *       standard download headers.</li>
 * </ol>
 *
 * <p><b>Binary only.</b> These responses are raw {@link Resource} bodies,
 * never the {@code ApiResponse<T>} envelope — see
 * {@code wecms-api/CLAUDE.md} §"Binary download/export". JSON endpoints
 * must not use this class, and the frontend consumes the result through
 * {@code lib/file.ts} / {@code lib/excel.ts}, never through {@code unwrap()}.
 *
 * <p>This is a safety-primitive extraction, not an API redesign: the
 * headers emitted by {@link #attachment} reproduce exactly what the ERP
 * transaction-history download endpoint emitted before R-08
 * (octet-stream, content-length, attachment disposition, {@code nosniff},
 * and a CORS expose-header so the browser can read the filename).
 */
public final class SecureFileDownload {

    private SecureFileDownload() {
    }

    /**
     * Path-traversal guard. After normalising both paths, asserts that
     * {@code resolved} stays inside {@code root}. Callers compose the
     * physical path however their storage layout requires, then pass the
     * tenant/root directory and the resolved file path here as a last line
     * of defence — a {@code "../"} component (or a future writer mistake)
     * that escapes the subtree is rejected rather than served.
     *
     * @throws IllegalArgumentException if either path is {@code null} or
     *         if {@code resolved} escapes {@code root}.
     */
    public static void assertWithinRoot(Path root, Path resolved) {
        if (root == null || resolved == null) {
            throw new IllegalArgumentException("root and resolved paths are required");
        }
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedResolved = resolved.toAbsolutePath().normalize();
        if (!normalizedResolved.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException(
                    "Resolved path escapes the permitted root: " + normalizedResolved);
        }
    }

    /**
     * Build an {@code attachment} download response for a binary body.
     * Sets, in order: {@code Content-Type: application/octet-stream},
     * {@code Content-Length}, an RFC 5987 ({@code filename*=UTF-8''…})
     * {@code Content-Disposition} so non-ASCII filenames (Korean / Khmer /
     * Vietnamese) round-trip, {@code X-Content-Type-Options: nosniff}, and
     * an {@code Access-Control-Expose-Headers: Content-Disposition} so a
     * cross-origin browser fetch can read the filename.
     *
     * <p>Generic over the {@link Resource} subtype so the caller's static
     * return type is preserved (e.g. {@code InputStreamResource} for the
     * ERP download endpoint), keeping the migration behaviour-exact.
     *
     * @param body             the binary payload (never the JSON envelope)
     * @param downloadFilename the filename shown to the user / saved by the
     *                         browser (e.g. the legacy {@code SAVE_FILE_NM})
     * @param contentLength    byte length of {@code body}
     */
    public static <R extends Resource> ResponseEntity<R> attachment(
            R body, String downloadFilename, long contentLength) {
        ContentDisposition cd = ContentDisposition.builder("attachment")
                .filename(downloadFilename, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(contentLength)
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Access-Control-Expose-Headers", HttpHeaders.CONTENT_DISPOSITION)
                .body(body);
    }
}
