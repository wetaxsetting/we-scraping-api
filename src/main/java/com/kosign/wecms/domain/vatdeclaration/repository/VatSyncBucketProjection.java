package com.kosign.wecms.domain.vatdeclaration.repository;

import java.math.BigDecimal;

/**
 * One aggregate bucket for the Sync flow: the period's invoice VAT lines
 * ({@code EINV_TAX_DETL}) summed by {@code (DOC_TYPE, EINV_TAXRATE)}. Feeds
 * {@code VatDeclarationService.sync} which distributes the buckets into the
 * invoice-source indicators ([23]/[24] purchases; [26]/[29]/[30]/[31]/[32]/
 * [33]/[32a] sales) per the Business-Rules rate→indicator table.
 *
 * <p>{@code amt} = tax-exclusive value (base), {@code taxAmt} = VAT amount.
 */
public interface VatSyncBucketProjection {
    String getDocType();     // 'I' sales / 'B' purchase
    String getTaxRate();     // EINV_TAXRATE string: '0%' '5%' '8%' '10%' 'KCT' 'KKKNT'
    BigDecimal getAmt();
    BigDecimal getTaxAmt();
}
