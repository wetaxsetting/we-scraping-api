package com.kosign.wecms.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Audit columns shared by every entity that maps to a legacy JEX table.
 * Column names match legacy exactly: REG_ID, REG_DTM, MOD_ID, MOD_DTM.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntity {

    @Column(name = "REG_ID", length = 100, updatable = false)
    private String regId;

    @Convert(converter = LegacyDtmConverter.class)
    @Column(name = "REG_DTM", length = 14, updatable = false)
    private OffsetDateTime regDtm;

    @Column(name = "MOD_ID", length = 100)
    private String modId;

    @Convert(converter = LegacyDtmConverter.class)
    @Column(name = "MOD_DTM", length = 14)
    private OffsetDateTime modDtm;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (regDtm == null) regDtm = now;
        if (modDtm == null) modDtm = now;
    }

    @PreUpdate
    protected void onUpdate() {
        modDtm = OffsetDateTime.now();
    }
}
