package com.kosign.wecms.domain.weinvoice.dto;

/**
 * One file of the invoice record (UI Spec 3.5).
 *
 * @param id        {@code xml} / {@code pdf} for the engine files, {@code u<n>} for uploads
 * @param kind      {@code xml} / {@code pdf} / {@code attachment}
 * @param origin    {@code gdt} / {@code vendor} / {@code email} / {@code upload}
 * @param size      bytes; {@code null} when the engine file is not on disk
 * @param deletable only uploads can be deleted (engine files are never written by the backend)
 * @param url       download path relative to the API root
 */
public record InvoiceFileItem(
        String id,
        String kind,
        String name,
        Long size,
        String origin,
        String addedAt,
        boolean deletable,
        String url) {
}
