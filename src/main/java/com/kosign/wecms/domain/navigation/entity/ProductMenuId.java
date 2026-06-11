package com.kosign.wecms.domain.navigation.entity;

import java.io.Serializable;
import java.util.Objects;

/** Composite key for {@link ProductMenu} — {@code (PRODUCT_TYPE, MENU_ITEM_ID)}. */
public class ProductMenuId implements Serializable {

    private String productType;
    private String menuItemId;

    public ProductMenuId() { }

    public ProductMenuId(String productType, String menuItemId) {
        this.productType = productType;
        this.menuItemId = menuItemId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProductMenuId that)) return false;
        return Objects.equals(productType, that.productType)
                && Objects.equals(menuItemId, that.menuItemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productType, menuItemId);
    }
}
