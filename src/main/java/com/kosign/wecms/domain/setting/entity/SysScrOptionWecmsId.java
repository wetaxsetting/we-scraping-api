package com.kosign.wecms.domain.setting.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/** Composite PK for {@link SysScrOptionWecms} — (WEB_SVC_ID, FIELD_ID). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class SysScrOptionWecmsId implements Serializable {

    private String webSvcId;
    private String fieldId;
}
