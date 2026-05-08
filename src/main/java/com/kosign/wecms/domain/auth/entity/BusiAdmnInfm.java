package com.kosign.wecms.domain.auth.entity;

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
 * Mirrors legacy wabooksdev.BUSI_ADMN_INFM. Records the platform admin(s)
 * for a tenant — written as the final row in the bootstrap chain so login
 * authorisation can resolve "who can manage this BIZ_KEY".
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "BUSI_ADMN_INFM")
@IdClass(BusiAdmnInfmId.class)
public class BusiAdmnInfm extends BaseEntity {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "USER_ID", length = 100, nullable = false)
    private String userId;

    @Column(name = "USER_NM", length = 100, nullable = false)
    private String userNm;
}
