package com.kosign.wecms.domain.setting.zalooa.entity;

import com.kosign.wecms.common.entity.BaseEntity;
import com.kosign.wecms.common.entity.LegacyDtmConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Settings &gt; Zalo OA — one row per outbound Zalo template-message call,
 * written on both success and failure. Maps {@code ZALO_SEND_HIS} (V254
 * migration).
 *
 * <p>The reference implementation's log table stores only the request and
 * response blobs, so answering "did this recipient get the message, under which
 * template?" means parsing JSON. The columns that matter for support are lifted
 * out here: {@link #rcvrNo}, {@link #tmplId}, {@link #tmplType}, {@link #langCd},
 * {@link #trackingId}, {@link #rsltCd}.
 *
 * <p>{@link #trackingId} is the UUID handed to Zalo as {@code tracking_id}.
 * Nothing consumes it yet — it is persisted so a delivery-report webhook can
 * correlate later without a schema change.
 *
 * <p>No foreign key to {@link ZaloOaInfm}: the log must outlive a disconnected
 * OA, and a send that failed before any OA was resolved still deserves a row.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ZALO_SEND_HIS")
public class ZaloSendHis extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "zaloSendSeqGen")
    @SequenceGenerator(name = "zaloSendSeqGen", sequenceName = "SQ_ZALO_SEND_SEQ", allocationSize = 1)
    @Column(name = "SEND_SEQ", nullable = false)
    private BigDecimal sendSeq;

    /** Tenant that owns the send. */
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    /** Official Account the message was sent from. Null if the send failed
     *  before an OA could be resolved. */
    @Column(name = "OA_ID", length = 50)
    private String oaId;

    @Column(name = "TMPL_ID", length = 50)
    private String tmplId;

    @Column(name = "TMPL_TYPE", length = 20)
    private String tmplType;

    @Column(name = "LANG_CD", length = 3)
    private String langCd;

    /** Recipient phone in E.164 digits, exactly as sent to Zalo. */
    @Column(name = "RCVR_NO", length = 50)
    private String rcvrNo;

    /** UUID passed to Zalo as {@code tracking_id}. */
    @Column(name = "TRACKING_ID", length = 50)
    private String trackingId;

    /** Serialized request body. */
    @Column(name = "REQ_DATA", columnDefinition = "text")
    private String reqData;

    @Convert(converter = LegacyDtmConverter.class)
    @Column(name = "REQ_DTM", length = 14)
    private OffsetDateTime reqDtm;

    /** Serialized Zalo response, or a local error marker when the call threw
     *  before a response arrived. */
    @Column(name = "RES_DATA", columnDefinition = "text")
    private String resData;

    @Convert(converter = LegacyDtmConverter.class)
    @Column(name = "RES_DTM", length = 14)
    private OffsetDateTime resDtm;

    /** Zalo's {@code error} value as text ({@code 0} = success), or an HTTP
     *  status / local failure marker. */
    @Column(name = "RSLT_CD", length = 20)
    private String rsltCd;

    @Column(name = "RSLT_MSG", length = 500)
    private String rsltMsg;
}
