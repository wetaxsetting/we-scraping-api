package com.kosign.wecms.domain.navigation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Per-product, per-language label for a {@link MenuItem}.
 *
 * <p>Keyed by {@code (PRODUCT_TYPE, MENU_ITEM_ID, LANG_CD)} — the product
 * dimension is required because M's localized VI/KO labels diverge from F
 * for the same node (verified in the JexAdmin export: F often carries an
 * English string in its VI field, M carries the real Vietnamese). A
 * {@code (MENU_ITEM_ID, LANG_CD)}-only key could not represent that, so this
 * deliberately refines the design doc's translation-table sketch.
 *
 * <p>{@code LANG_CD} is {@code EN} / {@code KO} / {@code VI}. KM has no rows —
 * the read path falls back to EN.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "MENU_ITEM_TRANSLATION")
@IdClass(MenuItemTranslationId.class)
public class MenuItemTranslation {

    @Id
    @Column(name = "PRODUCT_TYPE", length = 1, nullable = false)
    private String productType;

    @Id
    @Column(name = "MENU_ITEM_ID", length = 100, nullable = false)
    private String menuItemId;

    @Id
    @Column(name = "LANG_CD", length = 2, nullable = false)
    private String langCd;

    @Column(name = "LABEL", length = 200, nullable = false)
    private String label;
}
