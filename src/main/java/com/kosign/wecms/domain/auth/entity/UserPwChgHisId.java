package com.kosign.wecms.domain.auth.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/** Composite key for {@link UserPwChgHis} — (USER_ID, REG_SEQ_NO). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserPwChgHisId implements Serializable {
    private String userId;
    private Long regSeqNo;
}
