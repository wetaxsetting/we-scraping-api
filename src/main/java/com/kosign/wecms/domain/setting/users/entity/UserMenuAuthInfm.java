package com.kosign.wecms.domain.setting.users.entity;

import com.kosign.wecms.common.entity.LegacyDtmConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Mirrors the legacy {@code USER_MENU_AUTH_INFM} table — one row per
 * (tenant, user, menu) triple recording the user's per-menu access
 * grant inside that tenant.
 *
 * <p>Composite PK on {@code (BIZ_KEY, USER_ID, WEB_SVC_ID)} — JEX parity.
 * Frontend delete-then-insert pattern in {@code MenuEntitlementService.save(...)}
 * means we only ever store {@code USE_YN='Y'} rows; the absence of a
 * row implies denial.
 *
 * <p>The grant code (kebab slug mirroring the frontend
 * {@code ENTITLEMENT_SECTIONS} tree, e.g. {@code banking.account_inquiry})
 * is stored in the legacy JEX {@code WEB_SVC_ID} column ({@code VARCHAR(50)}) —
 * the refactor repurposes that column to hold slug-based permission codes
 * (per the identifier-naming rule). The Java field stays {@code menuAuthCd}
 * to keep the slug semantics in code.
 *
 * <p><b>Audit:</b> JEX {@code user_menu_auth_infm} carries only
 * {@code MOD_ID}/{@code MOD_DTM} (both nullable) — NO {@code REG_*}
 * columns — so this entity does NOT extend {@code BaseEntity} (which
 * would map phantom {@code REG_ID}/{@code REG_DTM}). 2026-06-09 JEX-parity
 * alignment.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "USER_MENU_AUTH_INFM")
@IdClass(UserMenuAuthInfmId.class)
public class UserMenuAuthInfm {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "USER_ID", length = 100, nullable = false)
    private String userId;

    /** Kebab-slug grant code, stored in the legacy {@code WEB_SVC_ID}
     *  column (JEX {@code VARCHAR(50)}). */
    @Id
    @Column(name = "WEB_SVC_ID", length = 50, nullable = false)
    private String menuAuthCd;

    @Column(name = "USE_YN", length = 1, nullable = false)
    private String useYn;

    /** JEX audit — nullable; mod-only (no REG_* on this table). */
    @Column(name = "MOD_ID", length = 100)
    private String modId;

    /** JEX VARCHAR(14) yyyyMMddHHmmss — converted from OffsetDateTime. */
    @Convert(converter = LegacyDtmConverter.class)
    @Column(name = "MOD_DTM", length = 14)
    private OffsetDateTime modDtm;
}
