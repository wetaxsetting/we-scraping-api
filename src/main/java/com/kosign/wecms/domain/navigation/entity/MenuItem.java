package com.kosign.wecms.domain.navigation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Product-independent navigation node — one row per distinct refactor slug.
 * Replaces the structural half of the lost JexAdmin MenuManager trees
 * (hierarchy, route, icon, show flag) so the refactor owns the menu
 * definition as data rather than depending on Jex Studio.
 *
 * <p>Hierarchy is the self-referencing {@code PARENT_ID} ({@code null} =
 * top menu). {@code ROUTE} and {@code PERMISSION_CD} are {@code null} for
 * pure parents. {@code PERMISSION_CD} is the kebab-case menuAuth grant key
 * — the intersection key against {@code USER_MENU_AUTH_INFM.MENU_AUTH_CD}
 * (verified identical in
 * {@code permission-code-normalization-verification.md}). {@code LEGACY_*}
 * columns are traceability only and never drive routing or gating.
 *
 * <p>Refactor-owned reference table (schema {@code V242}); seeded by Flyway
 * ({@code V243}) and never mutated at runtime — no audit columns.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "MENU_ITEM")
public class MenuItem {

    @Id
    @Column(name = "MENU_ITEM_ID", length = 100, nullable = false)
    private String menuItemId;

    @Column(name = "PARENT_ID", length = 100)
    private String parentId;

    @Column(name = "MENU_DEP", nullable = false)
    private Integer menuDep;

    @Column(name = "ROUTE", length = 200)
    private String route;

    @Column(name = "ICON", length = 100)
    private String icon;

    @Column(name = "SHOW_YN", length = 1, nullable = false)
    private String showYn;

    @Column(name = "PERMISSION_CD", length = 100)
    private String permissionCd;

    @Column(name = "LEGACY_MNU_ID", length = 100)
    private String legacyMnuId;

    @Column(name = "LEGACY_WSVC_ID", length = 100)
    private String legacyWsvcId;

    /** True for a pure parent (grouping) node — no route, no grant key. */
    public boolean isParent() {
        return permissionCd == null;
    }
}
