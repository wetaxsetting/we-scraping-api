package com.kosign.wecms.domain.auth.entity;

import com.kosign.wecms.common.entity.BaseEntity;
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
 * Mirrors legacy wecmsvndevdb.USER_BUSI_CONN_INFM column-for-column.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "USER_BUSI_CONN_INFM")
@IdClass(UserBusiConnInfmId.class)
public class UserBusiConnInfm extends BaseEntity {

    @Id
    @Column(name = "USER_ID", length = 100, nullable = false)
    private String userId;

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Column(name = "EMPL_NO", length = 20)
    private String emplNo;

    @Column(name = "USER_IMG", length = 500)
    private String userImg;

    /** JEX stores this as VARCHAR(14) yyyyMMddHHmmss — converted from
     *  OffsetDateTime so validate passes against the live JEX column
     *  (same LegacyDtmConverter as BaseEntity's REG_DTM/MOD_DTM). */
    @Convert(converter = LegacyDtmConverter.class)
    @Column(name = "LST_LGN_DATE", length = 14)
    private OffsetDateTime lstLgnDate;

    @Convert(converter = LegacyDtmConverter.class)
    @Column(name = "MOB_LST_LGN_DATE", length = 14)
    private OffsetDateTime mobLstLgnDate;

    @Column(name = "DEL_YN", length = 1, nullable = false)
    private String delYn;
}
