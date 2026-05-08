package com.kosign.wecms.domain.setting.entity;

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
 * Per-user column-config override for a screen (legacy
 * {@code USER_SCR_OPTION_WECMS}). Rows are written by the View Setting
 * popup's Save action; the Reset action wipes every row matching
 * (BIZ_KEY, USER_ID, WEB_SVC_ID, DYM_AREA_ID), causing the load to fall
 * back to the system defaults in {@link SysScrOptionWecms} via the
 * service-level merge.
 *
 * <p>Schema column-for-column aligned with DEV (V35 migration). Audit
 * columns inherited from {@link BaseEntity} remain nullable on the
 * entity even though DEV declares reg_id/reg_dtm/mod_dtm NOT NULL —
 * {@code @PrePersist} populates them on every JPA write so production
 * traffic never inserts nulls; the entity-level nullability matches the
 * BaseEntity contract.
 *
 * <p>{@code SIZE} is currently nullable on this table (deferred — local
 * lazy-init in {@code ScreenOptionService.initUserRowsFromSys} now
 * copies sys.size into user.size, so a follow-up migration can flip
 * SIZE to NOT NULL once existing rows are backfilled).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "USER_SCR_OPTION_WECMS")
@IdClass(UserScrOptionWecmsId.class)
public class UserScrOptionWecms extends BaseEntity {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "USER_ID", length = 100, nullable = false)
    private String userId;

    @Id
    @Column(name = "WEB_SVC_ID", length = 50, nullable = false)
    private String webSvcId;

    @Id
    @Column(name = "FIELD_ID", length = 100, nullable = false)
    private String fieldId;

    @Column(name = "FIELD_NM", length = 100, nullable = false)
    private String fieldNm;

    @Column(name = "DISP_ORD", nullable = false)
    private Integer dispOrd;

    @Column(name = "DISP_YN", length = 1, nullable = false)
    private String dispYn;

    @Column(name = "USE_YN", length = 1, nullable = false)
    private String useYn;

    @Column(name = "SIZE", length = 20)
    private String size;

    @Column(name = "DYM_AREA_ID", length = 50, nullable = false)
    private String dymAreaId;
}
