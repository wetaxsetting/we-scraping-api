package com.kosign.wecms.domain.setting.users.entity;

import com.kosign.wecms.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Mirrors the legacy {@code USER_MENU_AUTH_INFM} table — one row per
 * (tenant, user, menu) triple recording the user's per-menu access
 * grant inside that tenant.
 *
 * <p>Composite PK on {@code (BIZ_KEY, USER_ID, MENU_AUTH_CD)}. Frontend
 * delete-then-insert pattern in {@code MenuEntitlementService.save(...)}
 * means we only ever store {@code USE_YN='Y'} rows; the absence of a
 * row implies denial. The Y-only convention keeps the table sparse and
 * lets the panel render every menu as "off" by default.
 *
 * <p>{@code MENU_AUTH_CD} values are kebab-case slugs that mirror the
 * frontend's static `ENTITLEMENT_SECTIONS` tree (e.g.
 * {@code banking.account_inquiry}, {@code payment.approval_box}). The
 * column is {@code VARCHAR(100)} to comfortably fit the longest slug.
 *
 * <p><b>Schema source:</b> created by Flyway migration
 * {@code V126__user_menu_auth_infm.sql}. All profiles keep
 * {@code ddl-auto=validate}; Flyway is the only sanctioned schema
 * channel (an earlier {@code ddl-auto=update} attempt was reverted —
 * see the V126 header).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "USER_MENU_AUTH_INFM")
@IdClass(UserMenuAuthInfmId.class)
public class UserMenuAuthInfm extends BaseEntity {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "USER_ID", length = 100, nullable = false)
    private String userId;

    @Id
    @Column(name = "MENU_AUTH_CD", length = 100, nullable = false)
    private String menuAuthCd;

    @Column(name = "USE_YN", length = 1, nullable = false)
    private String useYn;
}
