package com.kosign.wecms.domain.setting.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/** Composite PK for {@link UserScrOptionWecms} — (BIZ_KEY, USER_ID, WEB_SVC_ID, FIELD_ID). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserScrOptionWecmsId implements Serializable {

    private String bizKey;
    private String userId;
    private String webSvcId;
    private String fieldId;
}
