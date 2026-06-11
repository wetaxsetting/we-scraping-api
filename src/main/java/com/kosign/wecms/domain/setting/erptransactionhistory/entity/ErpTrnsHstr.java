package com.kosign.wecms.domain.setting.erptransactionhistory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Settings &gt; ERP Transaction History — audit row of every file
 * exchanged between WeCMS and the customer's ERP. Mirrors legacy
 * {@code WECMS DEV/WECMS/src/wabooks/entity/ErpTrnsHstr.java} +
 * {@code IDO.ERP_TRNS_HSTR_R001} column-for-column.
 *
 * <p>The refactor is read-only — list + 4 tab counts + file download.
 * Writes happen on the JEX side (legacy {@code comm_0219_001_c001}
 * upload action is intentionally NOT ported; future Payment / Transfer /
 * Account-Inquiry features that produce a file write their own audit
 * row from their own service). See
 * {@code docs/refactor-wecms/features/erp-transaction-history.md §2}.
 *
 * <p>Tenant scoping is the caller's responsibility — every repository
 * method must constrain on {@code BIZ_KEY} from
 * {@link com.kosign.wecms.common.tenant.TenantContext#currentBizKey()}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ERP_TRNS_HSTR")
@IdClass(ErpTrnsHstrId.class)
public class ErpTrnsHstr {

    /** Tenant key. Always part of the WHERE clause; never exposed on the wire. */
    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    /** Per-tenant monotonic sequence (legacy {@code wb_get_next_val}
     *  output on the JEX side). JEX stores {@code NUMERIC(10,0)};
     *  {@code @JdbcTypeCode(SqlTypes.NUMERIC)} maps the Java {@code Long}
     *  onto it so Hibernate {@code validate} accepts the numeric column
     *  against the live JEX schema. */
    @Id
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "SEQ_NO", precision = 10, scale = 0, nullable = false)
    private Long seqNo;

    /** Original filename as supplied by the user / batch writer. */
    @Column(name = "ORG_FILE_NM", length = 500, nullable = false)
    private String orgFileNm;

    /** Filename on NAS / local storage root. For rows written by a
     *  batch with no physical file this is a synthetic placeholder
     *  (e.g. {@code Batch_WeCMS_ERP_<epoch>.txt}). */
    @Column(name = "SAVE_FILE_NM", length = 500, nullable = false)
    private String saveFileNm;

    /** File size in bytes. {@code 0} for batch rows with no physical
     *  file — the frontend hides the download link in that case.
     *  JEX stores {@code NUMERIC(10,0)}; {@code @JdbcTypeCode(SqlTypes.NUMERIC)}
     *  maps the Java {@code Long} onto it for validate parity (same as
     *  {@link #seqNo}). */
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "FILE_SIZE", precision = 10, scale = 0, nullable = false)
    private Long fileSize;

    /** Direction code:
     *  <ul>
     *    <li>{@code W} — WeCMS → ERP (export sent to ERP, or batch row)</li>
     *    <li>{@code E} — ERP → WeCMS (uploaded ERP file ingested by WeCMS)</li>
     *    <li>{@code D} — Deleted (soft-delete of an {@code E} row)</li>
     *  </ul>
     *
     *  <p>{@code @JdbcTypeCode(CHAR)} so Hibernate maps the Java
     *  {@code String} to the DEV-parity {@code CHAR(1)} column instead
     *  of {@code VARCHAR}. Same pattern as
     *  {@code SysScrOptionWecms.sortYn}.
     */
    @Column(name = "ERP_TYPE", length = 1, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String erpType;

    /** Transaction type code — {@code "01"..."17"}. See
     *  {@code WECMS DEV/WECMS/src/wabooks/enums/ErpTypeCd.java} for the
     *  full set. The refactor never enum-maps this column; the frontend
     *  resolves the display label via the {@code trns_type_key} field
     *  emitted on the wire. */
    @Column(name = "TRNS_TYPE_CD", length = 2, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String trnsTypeCd;

    /** Login id of the user that triggered the row (or {@code SYSTEM}
     *  for batch writes). */
    @Column(name = "REG_ID", length = 100, nullable = false)
    private String regId;

    /** UTC timestamp as {@code yyyyMMddHHmmss}. Filter target for
     *  {@code SUBSTRING(REG_DTM FROM 1 FOR 8) BETWEEN ? AND ?}
     *  (legacy {@code comm_0219_001_r001_act.jsp:46}). */
    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;
}
