package com.kosign.wecms.domain.setting.environmentsettings.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BusiEnvSetInfmId implements Serializable {
    private String bizKey;
    private String envSetInf;
}
