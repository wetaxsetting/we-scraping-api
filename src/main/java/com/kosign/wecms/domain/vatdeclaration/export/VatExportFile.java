package com.kosign.wecms.domain.vatdeclaration.export;

/**
 * A built binary export (XML / PDF) for a VAT declaration — the bytes plus the
 * download filename and MIME type. Returned by {@link VatXmlExporter} /
 * {@link VatPdfExporter} and streamed by the controller as
 * {@code ResponseEntity<byte[]>} (the §0 binary-download exception to the
 * {@code ApiResponse<T>} envelope rule).
 */
public record VatExportFile(String fileName, String mediaType, byte[] content) {
}
