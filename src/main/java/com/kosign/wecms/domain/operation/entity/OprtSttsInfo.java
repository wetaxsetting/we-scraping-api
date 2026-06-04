package com.kosign.wecms.domain.operation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Mirrors legacy wecmsvndev.OPRT_STTS_INFO. Backs the Add-on > Operation
 * Status Inquiry list. Rows are read by the list endpoint, written by the
 * Inquire-popup Save ({@code aosv_0001_002_c001}) and the bulk Update
 * re-poll ({@code aosv_0001_001_u001}) — both of which fetch live data
 * through the wired OSMC → GDT bridge.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "OPRT_STTS_INFO")
@IdClass(OprtSttsInfoId.class)
public class OprtSttsInfo {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "TAXCODE", length = 100, nullable = false)
    private String taxcode;

    @Column(name = "COMPANYNAME", length = 100)
    private String companyname;

    @Column(name = "COMPANYSTATUS", length = 255)
    private String companystatus;

    @Column(name = "OFFICEADD", length = 255)
    private String officeadd;

    @Column(name = "SEARCHDATETIME", length = 14, nullable = false)
    private String searchdatetime;

    @Column(name = "REPRESENTATIVEFIRSTNAME", length = 255, nullable = false)
    private String representativefirstname;

    @Column(name = "REG_ID", length = 100, nullable = false)
    private String regId;

    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;

    @Column(name = "MOD_ID", length = 100, nullable = false)
    private String modId;

    @Column(name = "MOD_DTM", length = 14)
    private String modDtm;
}
