package com.kosign.wecms.domain.banking.entity;

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
public class AcctMstrId implements Serializable {
    private String bizKey;
    private String bankCd;
    private String acctNo;
}
