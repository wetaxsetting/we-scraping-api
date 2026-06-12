package com.kosign.wecms.domain.banking.nxisas.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Per-call audit row for the NXISAS gateway client. One row per
 * outbound HTTPS POST to the Webcash smart-gateway. Mirrors legacy
 * JEX {@code wabooks.entity.NxsiasModuleTranLogHis} column-for-column
 * (V87 migration). Composite PK {@code (UUID, REG_DTM)} matches legacy.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "NXSIAS_MODULE_TRAN_LOG_HIS")
@IdClass(NxsiasModuleTranLogHisId.class)
public class NxsiasModuleTranLogHis {

    @Id
    @Column(name = "UUID", length = 32, nullable = false)
    private String uuid;

    @Id
    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;

    @Column(name = "BIZ_KEY", length = 20)
    private String bizKey;

    @Column(name = "NXISAS_CLASS", length = 100, nullable = false)
    private String nxisasClass;

    @Column(name = "NXISAS_MODULE", length = 100, nullable = false)
    private String nxisasModule;

    @Column(name = "NXISAS_JOB", length = 100, nullable = false)
    private String nxisasJob;

    @Column(name = "NXISAS_ERR_CODE", length = 8, nullable = false)
    private String nxisasErrCode;

    @Column(name = "NXISAS_ERR_CONT", length = 1000)
    private String nxisasErrCont;

    @Column(name = "REQ_DATA", length = 1000)
    private String reqData;

    @Column(name = "REG_ID", length = 100)
    private String regId;

    @Column(name = "NXISAS_TYPE", length = 100)
    private String nxisasType;
}
