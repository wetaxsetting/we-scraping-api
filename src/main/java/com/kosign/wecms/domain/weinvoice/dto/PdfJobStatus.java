package com.kosign.wecms.domain.weinvoice.dto;

import java.util.List;

/**
 * State of a bulk PDF job (UI Spec v1.1 §3.12, screen 05 "Tải PDF hàng loạt"). Polled by the web every few seconds.
 *
 * @param jobId      job id (opaque)
 * @param mode       packaging mode
 * @param state      {@code running} · {@code done} · {@code cancelled} · {@code failed}
 * @param step       {@code xml} (step 1, missing XML from GDT) · {@code pdf} (step 2) · {@code pack} · {@code done}
 * @param xmlDone    step 1 progress — invoices whose XML request finished
 * @param xmlTotal   step 1 size (0 = nothing to fetch)
 * @param total      invoices in the job
 * @param done       invoices with a PDF in the pack (original, or rendered copy in mode {@code rendered})
 * @param fallback   invoices packed with a rendered copy because no original PDF exists
 * @param failed     invoices left out (temporary failure — retry)
 * @param loading    invoices being fetched from the provider right now
 * @param items      per-invoice rows for the progress list (at most 500, active rows first)
 * @param failedGroups   failures by reason, each with its invoices (for "Thử lại" per group)
 * @param fallbackGroups invoices without original PDF by reason
 * @param fileName   ZIP file name once packed
 * @param fileReady  the ZIP can be downloaded
 * @param error      message when {@code state = failed}
 */
public record PdfJobStatus(
        String jobId,
        String mode,
        String state,
        String step,
        int xmlDone,
        int xmlTotal,
        int total,
        int done,
        int fallback,
        int failed,
        int loading,
        List<Item> items,
        List<Group> failedGroups,
        List<Group> fallbackGroups,
        String fileName,
        boolean fileReady,
        String error) {

    /**
     * @param phase  {@code xml} · {@code queued} · {@code loading} · {@code done} · {@code fallback} · {@code failed}
     * @param source where the PDF came from ({@code provider name}, {@code email}, {@code upload}, {@code rendered})
     * @param reason fallback reason ({@code no_code_invoice}, {@code no_lookup_code}, {@code not_connected},
     *               {@code no_provider}) or failure reason ({@code gateway}, {@code pending}, {@code not_on_server},
     *               {@code xml_missing})
     */
    public record Item(String id, String partner, String partnerTaxId, String symbol, String number, String issueDate,
                       String phase, String source, String reason) {
    }

    public record Group(String reason, int count, List<Item> items) {
    }
}
