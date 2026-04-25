package com.kosign.wecms.domain.auth.entity;

import com.kosign.wecms.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Mirrors legacy wecmsvndevdb.USER_INFM column-for-column.
 * Login only reads USER_ID, USER_PW, USER_NM, EML, ACTV_YN, LOCK_YN, DEL_YN —
 * the remaining fields exist to support direct data copy from legacy.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "USER_INFM")
public class UserInfm extends BaseEntity {

    @Id
    @Column(name = "USER_ID", length = 100, nullable = false)
    private String userId;

    @Column(name = "USER_NM", length = 100, nullable = false)
    private String userNm;

    @Column(name = "CLPH_NO", length = 100)
    private String clphNo;

    @Column(name = "EML", length = 100)
    private String eml;

    @Column(name = "USER_PW", length = 100, nullable = false)
    private String userPw;

    @Column(name = "USER_IMG", length = 500)
    private String userImg;

    @Column(name = "LST_LGN_DATE")
    private OffsetDateTime lstLgnDate;

    @Column(name = "MOB_LST_LGN_DATE")
    private OffsetDateTime mobLstLgnDate;

    @Column(name = "ACTV_YN", length = 1, nullable = false)
    private String actvYn;

    @Column(name = "LOCK_YN", length = 1, nullable = false)
    private String lockYn;

    @Column(name = "PW_CHG_REQ_SEQ_NO")
    private BigDecimal pwChgReqSeqNo;

    @Column(name = "DEL_YN", length = 1, nullable = false)
    private String delYn;

    @Column(name = "BIZ_KEY", length = 20)
    private String bizKey;

    @Column(name = "EMPL_NO", length = 20)
    private String emplNo;

    @Column(name = "DEPT_NM", length = 100)
    private String deptNm;

    @Column(name = "POSI_NM", length = 100)
    private String posiNm;

    public boolean isActive()  { return "Y".equals(actvYn); }
    public boolean isLocked()  { return "Y".equals(lockYn); }
    public boolean isDeleted() { return "Y".equals(delYn);  }
}
