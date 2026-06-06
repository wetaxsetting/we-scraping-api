package com.kosign.wecms.domain.navigation.repository;

import com.kosign.wecms.domain.navigation.entity.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link MenuItem}. The catalogue is small (~42 rows for the
 * whole product family) and product-independent, so the navigation service
 * loads it whole once per request via {@link #findAll()} and joins it to the
 * per-product {@code PRODUCT_MENU} membership in memory.
 */
public interface MenuItemRepository extends JpaRepository<MenuItem, String> {

    /** True when any catalogue node carries this {@code permission_cd} — i.e.
     *  the cd is a product-tree leaf (in some product). Used by the
     *  server-side menu guard to decide whether the product-tree gate applies
     *  (Settings cds are not catalogue leaves, so this returns false for them). */
    boolean existsByPermissionCd(String permissionCd);
}
