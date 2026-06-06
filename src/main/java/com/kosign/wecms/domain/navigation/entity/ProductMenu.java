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
 * Per-product membership + ordering for a {@link MenuItem}. Replaces the
 * legacy {@code MENU_AUTH_ADMN_SYS} per-product default catalogue — this is
 * where the F / M / H difference is encoded (H omits the Add-on subtree,
 * Daily Cash, standard Daily Financial and Cash Flow, and carries the HS
 * Daily Financial node instead; M is structurally identical to F).
 *
 * <p>{@code DISP_ORD} is the legacy {@code MNU_SEQ} for that product — sibling
 * order within a parent. Composite PK on {@code (PRODUCT_TYPE, MENU_ITEM_ID)}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "PRODUCT_MENU")
@IdClass(ProductMenuId.class)
public class ProductMenu {

    @Id
    @Column(name = "PRODUCT_TYPE", length = 1, nullable = false)
    private String productType;

    @Id
    @Column(name = "MENU_ITEM_ID", length = 100, nullable = false)
    private String menuItemId;

    @Column(name = "DISP_ORD", nullable = false)
    private Integer dispOrd;
}
