package com.kosign.wecms.domain.vatdeclaration.service;

import com.kosign.wecms.domain.vatdeclaration.dto.AnnexInvoiceProjection;
import com.kosign.wecms.domain.vatdeclaration.entity.VatDeclaration;
import com.kosign.wecms.domain.vatdeclaration.repository.AnnexTaxGroupProjection;
import com.kosign.wecms.domain.vatdeclaration.repository.VatSyncBucketProjection;

import java.util.List;

/**
 * Where a declaration's invoices come from. Scope is the declaration itself: its book key, tax code and
 * {@code FROM_DT..TO_DT} period. {@code docTypes} use the WeCMS codes {@code I} = output / sales and
 * {@code B} = input / purchase; {@code excludedStatuses} are the WeCMS status labels (an implementation over
 * another store maps them to its own codes).
 *
 * <p>The WeCMS implementation is the e-invoice tables ({@code VatAnnexInvoiceRepository}); the WeInvoice one
 * reads the engine tables ({@code EngineVatInvoiceSource}). Semantics of every method are those documented on
 * {@code VatAnnexInvoiceRepository}.
 */
public interface VatInvoiceSource {

    /** Base + VAT per (direction, rate) — the indicator buckets [23]/[24], [26]/[29]/[30]/[32]/[32a]. */
    List<VatSyncBucketProjection> syncBuckets(VatDeclaration decl, List<String> docTypes, List<String> excludedStatuses);

    /** Real output VAT per rate — pins [31]/[33]. */
    List<VatSyncBucketProjection> salesVatByRate(VatDeclaration decl, List<String> excludedStatuses);

    /** Annex section footers, one row per rate. */
    List<AnnexTaxGroupProjection> groupTotals(VatDeclaration decl, List<String> docTypes, String rate, String search,
                                              List<String> excludedStatuses);

    /** Annex rows, one per (invoice, rate), paged. */
    List<AnnexInvoiceProjection> findRows(VatDeclaration decl, List<String> docTypes, String rate, String search,
                                          List<String> excludedStatuses, long offset, int size);

    /** Lock: mark the period's invoices as closed into {@code decl} (may be a no-op for a read-only store). */
    void markProcessed(VatDeclaration decl, List<String> docTypes, List<String> excludedStatuses);

    /** Unlock / delete: release what {@link #markProcessed} marked. */
    void clearProcessed(VatDeclaration decl);
}
