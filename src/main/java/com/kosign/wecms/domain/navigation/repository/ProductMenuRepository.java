package com.kosign.wecms.domain.navigation.repository;

import com.kosign.wecms.domain.navigation.entity.ProductMenu;
import com.kosign.wecms.domain.navigation.entity.ProductMenuId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Repository for {@link ProductMenu}. The navigation service selects the
 * tree for the session's {@code prdt_type} by loading that product's
 * membership rows (slug + per-product display order).
 */
public interface ProductMenuRepository extends JpaRepository<ProductMenu, ProductMenuId> {

    /** Every membership row for a product type — the menu universe the
     *  product can ever show, ordered into the tree by {@code DISP_ORD}. */
    List<ProductMenu> findAllByProductType(String productType);

    /**
     * True when {@code permissionCd} is a leaf in the given product's tree —
     * i.e. a {@link ProductMenu} row for {@code productType} references a
     * {@link com.kosign.wecms.domain.navigation.entity.MenuItem} whose
     * {@code permission_cd} matches. Backs the server-side menu guard's
     * product-tree gate (an H tenant has no Cash-Flow / Daily-Cash leaf, so
     * those cds return false → 403). {@code MenuItem} and {@code ProductMenu}
     * have no JPA relationship, so the join is expressed in the predicate.
     */
    @Query("select case when count(pm) > 0 then true else false end "
            + "from ProductMenu pm, com.kosign.wecms.domain.navigation.entity.MenuItem mi "
            + "where pm.menuItemId = mi.menuItemId "
            + "and pm.productType = :productType "
            + "and mi.permissionCd = :permissionCd")
    boolean existsInProductTree(@Param("productType") String productType,
                                @Param("permissionCd") String permissionCd);
}
