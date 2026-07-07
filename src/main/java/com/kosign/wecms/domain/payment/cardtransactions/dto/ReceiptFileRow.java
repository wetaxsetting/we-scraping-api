package com.kosign.wecms.domain.payment.cardtransactions.dto;

/**
 * One attachment row for a receipt / e-Invoice (audit G3/G5). Maps
 * {@link com.kosign.wecms.domain.payment.cardtransactions.entity.BusaDocFile}
 * onto the wire shape the Card-Link Receipt / e-Invoice tab dropdown renders.
 *
 * <p>{@code save_file_nm} is intentionally NOT exposed — the server-side UUID
 * filename never leaves the backend. Each attachment is addressed by
 * {@code file_id} and downloaded via
 * {@code /api/v1/payment/card-transactions/receipts/{doc_id}/files/{file_id}}.
 */
public record ReceiptFileRow(
        String fileId,
        String orgFileNm,
        String fileSize
) { }
