package com.kosign.wecms.domain.vatdeclaration.entity;

import com.kosign.wecms.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;

/**
 * VAT declaration header — {@code TAX_DECLARATIONS} (logical {@code VAT_DECL}).
 * One row = one declaration for one tax period, carrying the full HTKK
 * 01/GTGT (TT80) XML metadata block (taxpayer, tax office, agent, dependent
 * unit, extension) plus the raw {@code indicators} JSONB snapshot.
 *
 * <p>Source of truth: PO design sheet 2026-07-15 (prototype-normalized).
 * Two sheet inconsistencies resolved here pending PO confirmation:
 * <ul>
 *   <li>PK was {@code SID NUMBER} while the items FK was {@code UUID} —
 *       unified as {@code BIGINT IDENTITY} on both tables.</li>
 *   <li>The sheet's UK is (biz_key, period_type, period_value, period_year);
 *       it must also include {@code suppl_no} once supplementary
 *       declarations (loai_tkhai = 'B') coexist with the official filing.</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "TAX_DECLARATIONS")
public class VatDeclaration extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SID")
    private Long sid;

    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    /** {@code 'month'} / {@code 'quarter'} — UK member with value/year. */
    @Column(name = "PERIOD_TYPE", length = 10, nullable = false)
    private String periodType;

    /** 1..12 (month) or 1..4 (quarter). */
    @Column(name = "PERIOD_VALUE", nullable = false)
    private Integer periodValue;

    @Column(name = "PERIOD_YEAR", nullable = false)
    private Integer periodYear;

    /** maTKhai — HTKK form code (e.g. {@code 842}). */
    @Column(name = "FORM_CODE", length = 3, nullable = false)
    private String formCode;

    /** pbanTKhaiXML — declaration XML version (e.g. {@code 2.6.8}). */
    @Column(name = "XML_VER", length = 10, nullable = false)
    private String xmlVer;

    /** [01a] ten_NganhNghe. */
    @Column(name = "ACTIVITY_NAME", length = 150, nullable = false)
    private String activityName;

    /** ma_NganhNghe — main business-line code. */
    @Column(name = "ACTIVITY_CODE", length = 2, nullable = false)
    private String activityCode;

    /** tieuMucHachToan (default {@code 1701}). */
    @Column(name = "ACCT_ITEM", length = 10, nullable = false)
    private String acctItem;

    /** [01b] kyKKhai — declaration period label. */
    @Column(name = "PERIOD", length = 10, nullable = false)
    private String period;

    /** [02] soLan — supplement number. */
    @Column(name = "SUPPL_NO", nullable = false)
    private Integer supplNo;

    /** [03] loaiTKhai — {@code 'C'} official / {@code 'B'} supplementary. */
    @Column(name = "DECL_TYPE", length = 1, nullable = false)
    private String declType;

    /** [04] tenNNT — taxpayer name. */
    @Column(name = "TP_NAME", length = 400, nullable = false)
    private String tpName;

    /** [05] mst — taxpayer tax code. */
    @Column(name = "TP_TAX_CODE", length = 14, nullable = false)
    private String tpTaxCode;

    /** dchiNNT — head-office address. */
    @Column(name = "TP_ADDR", length = 255)
    private String tpAddr;

    @Column(name = "TP_WARD")
    private String tpWard;

    @Column(name = "TP_WARD_CODE")
    private String tpWardCode;

    @Column(name = "TP_DIST")
    private String tpDist;

    @Column(name = "TP_DIST_CODE")
    private String tpDistCode;

    @Column(name = "TP_PROV", length = 200, nullable = false)
    private String tpProv;

    @Column(name = "TP_PROV_CODE")
    private String tpProvCode;

    @Column(name = "TP_PHONE", length = 30)
    private String tpPhone;

    @Column(name = "TP_FAX", length = 30)
    private String tpFax;

    @Column(name = "TP_EMAIL", length = 50)
    private String tpEmail;

    /** kieuKy — {@code 'Q'} / {@code 'M'}. */
    @Column(name = "PERIOD_KIND", length = 1, nullable = false)
    private String periodKind;

    /** kyKKhaiTuNgay. */
    @Column(name = "FROM_DT", nullable = false)
    private LocalDate fromDt;

    /** kyKKhaiDenNgay. */
    @Column(name = "TO_DT", nullable = false)
    private LocalDate toDt;

    /** maCQTNoiNop — receiving tax-office code. */
    @Column(name = "OFFICE_CODE", length = 10, nullable = false)
    private String officeCode;

    /** tenCQTNoiNop. */
    @Column(name = "OFFICE_NAME", length = 150, nullable = false)
    private String officeName;

    /** ngayLapTKhai. */
    @Column(name = "DECL_DT", nullable = false)
    private LocalDate declDt;

    /** maLyDoGiaHan. */
    @Column(name = "EXT_REASON_CODE", length = 10)
    private String extReasonCode;

    /** lyDoGiaHan. */
    @Column(name = "EXT_REASON", length = 255)
    private String extReason;

    /** [06] tax-agent name. */
    @Column(name = "AGENT_NAME", length = 400)
    private String agentName;

    /** [07] tax-agent tax code. */
    @Column(name = "AGENT_TAX_CODE", length = 14)
    private String agentTaxCode;

    /** [08] agent contract number. */
    @Column(name = "AGENT_CTR_NO", length = 100)
    private String agentCtrNo;

    @Column(name = "AGENT_CTR_DT")
    private LocalDate agentCtrDt;

    /** [09] dependent unit / out-of-province business-location name. */
    @Column(name = "DU_NAME", length = 400)
    private String duName;

    /** [10] dependent-unit tax code / location code. */
    @Column(name = "DU_TAX_CODE", length = 14)
    private String duTaxCode;

    /** [11] out-of-province business address (full string). */
    @Column(name = "DU_ADDR", length = 255)
    private String duAddr;

    /** [11c] ct11c_tinhTP_ma. */
    @Column(name = "DU_PROV_CODE")
    private String duProvCode;

    /** [11c] ct11c_tinhTP_ten. */
    @Column(name = "DU_PROV")
    private String duProv;

    /** [11b] ct11b_quanHuyen_ma. */
    @Column(name = "DU_DIST_CODE")
    private String duDistCode;

    /** [11b] ct11b_quanHuyen_ten. */
    @Column(name = "DU_DIST")
    private String duDist;

    /** [11a] ct11a_phuongXa_ma. */
    @Column(name = "DU_WARD_CODE")
    private String duWardCode;

    /** [11a] ct11a_phuongXa_ten. */
    @Column(name = "DU_WARD")
    private String duWard;

    /** Indicator + metadata snapshot, e.g. {@code {"22":152434817,"43":151049691,…}}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "INDICATORS", columnDefinition = "jsonb", nullable = false)
    private String indicators;

    @Column(name = "DEL_YN", length = 1, nullable = false)
    private String delYn;

    /** Declaration lifecycle status (values pending PO — e.g. DRAFT / LOCKED). */
    @Column(name = "STATUS", length = 20, nullable = false)
    private String status;

    // --- Attached-annex flags (create-form checkboxes; prototype has 4) -------
    /** BKBR 01-1/GTGT — output invoice listing (default ticked in prototype). */
    @Column(name = "ANNEX_BKBR_YN", length = 1, nullable = false)
    private String annexBkbrYn;

    /** BKMV 01-2/GTGT — input invoice listing (default ticked in prototype). */
    @Column(name = "ANNEX_BKMV_YN", length = 1, nullable = false)
    private String annexBkmvYn;

    /** PL 01-6/GTGT (CSSX) — allocation annex. */
    @Column(name = "ANNEX_ALLOC_YN", length = 1, nullable = false)
    private String annexAllocYn;

    /** 8% VAT-reduction annex (Resolution 204/2025/QH15). */
    @Column(name = "ANNEX_REDUCE8_YN", length = 1, nullable = false)
    private String annexReduce8Yn;

    @Builder
    public VatDeclaration(String bizKey, String periodType, Integer periodValue, Integer periodYear,
                          String formCode, String xmlVer, String activityName, String activityCode,
                          String acctItem, String period, Integer supplNo, String declType,
                          String tpName, String tpTaxCode, String tpAddr,
                          String tpWard, String tpWardCode, String tpDist, String tpDistCode,
                          String tpProv, String tpProvCode, String tpPhone, String tpFax, String tpEmail,
                          String periodKind, LocalDate fromDt, LocalDate toDt,
                          String officeCode, String officeName, LocalDate declDt,
                          String extReasonCode, String extReason,
                          String agentName, String agentTaxCode, String agentCtrNo, LocalDate agentCtrDt,
                          String duName, String duTaxCode, String duAddr,
                          String duProvCode, String duProv, String duDistCode, String duDist,
                          String duWardCode, String duWard,
                          String indicators, String delYn, String status,
                          String annexBkbrYn, String annexBkmvYn, String annexAllocYn, String annexReduce8Yn) {
        this.bizKey = bizKey;
        this.periodType = periodType;
        this.periodValue = periodValue;
        this.periodYear = periodYear;
        this.formCode = formCode;
        this.xmlVer = xmlVer;
        this.activityName = activityName;
        this.activityCode = activityCode;
        this.acctItem = acctItem;
        this.period = period;
        this.supplNo = supplNo;
        this.declType = declType;
        this.tpName = tpName;
        this.tpTaxCode = tpTaxCode;
        this.tpAddr = tpAddr;
        this.tpWard = tpWard;
        this.tpWardCode = tpWardCode;
        this.tpDist = tpDist;
        this.tpDistCode = tpDistCode;
        this.tpProv = tpProv;
        this.tpProvCode = tpProvCode;
        this.tpPhone = tpPhone;
        this.tpFax = tpFax;
        this.tpEmail = tpEmail;
        this.periodKind = periodKind;
        this.fromDt = fromDt;
        this.toDt = toDt;
        this.officeCode = officeCode;
        this.officeName = officeName;
        this.declDt = declDt;
        this.extReasonCode = extReasonCode;
        this.extReason = extReason;
        this.agentName = agentName;
        this.agentTaxCode = agentTaxCode;
        this.agentCtrNo = agentCtrNo;
        this.agentCtrDt = agentCtrDt;
        this.duName = duName;
        this.duTaxCode = duTaxCode;
        this.duAddr = duAddr;
        this.duProvCode = duProvCode;
        this.duProv = duProv;
        this.duDistCode = duDistCode;
        this.duDist = duDist;
        this.duWardCode = duWardCode;
        this.duWard = duWard;
        this.indicators = indicators;
        this.delYn = delYn == null ? "N" : delYn;
        this.status = status;
        this.annexBkbrYn = annexBkbrYn == null ? "N" : annexBkbrYn;
        this.annexBkmvYn = annexBkmvYn == null ? "N" : annexBkmvYn;
        this.annexAllocYn = annexAllocYn == null ? "N" : annexAllocYn;
        this.annexReduce8Yn = annexReduce8Yn == null ? "N" : annexReduce8Yn;
    }
}
