package com.kosign.wecms.domain.navigation.entity;

import java.io.Serializable;
import java.util.Objects;

/** Composite key for {@link MenuItemTranslation} — {@code (PRODUCT_TYPE, MENU_ITEM_ID, LANG_CD)}. */
public class MenuItemTranslationId implements Serializable {

    private String productType;
    private String menuItemId;
    private String langCd;

    public MenuItemTranslationId() { }

    public MenuItemTranslationId(String productType, String menuItemId, String langCd) {
        this.productType = productType;
        this.menuItemId = menuItemId;
        this.langCd = langCd;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MenuItemTranslationId that)) return false;
        return Objects.equals(productType, that.productType)
                && Objects.equals(menuItemId, that.menuItemId)
                && Objects.equals(langCd, that.langCd);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productType, menuItemId, langCd);
    }
}
