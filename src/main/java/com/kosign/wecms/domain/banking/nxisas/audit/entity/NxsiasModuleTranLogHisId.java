package com.kosign.wecms.domain.banking.nxisas.audit.entity;

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
public class NxsiasModuleTranLogHisId implements Serializable {
    private String uuid;
    private String regDtm;
}
