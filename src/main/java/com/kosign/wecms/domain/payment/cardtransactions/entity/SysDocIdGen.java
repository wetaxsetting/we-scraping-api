package com.kosign.wecms.domain.payment.cardtransactions.entity;

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
 * Legacy per-day document-number counter ({@code SYS_DOC_ID_GEN}, V90).
 * PK {@code (BIZ_KEY, DOC_TYPE, REQ_DATE)} — exactly one row per tenant +
 * document-type + UTC day; {@code DOC_ID} holds the latest issued id for that
 * day. The {@code wb_get_next_val('sys_doc_id_gen','doc_id', biz_key, doc_type)}
 * branch (V140 #48) reads {@code max(right(doc_id,3))+1} off this row to mint
 * the next receipt id (legacy {@code R-yyyyMMddNNN}); the receipt-create path
 * then upserts the row's {@code DOC_ID} to the newly-issued id so the next
 * same-day call increments (audit finding G7 — matches legacy population;
 * verified on JEX DEV: 334 {@code doc_type='E'} rows).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "SYS_DOC_ID_GEN")
@IdClass(SysDocIdGenId.class)
public class SysDocIdGen {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "DOC_TYPE", length = 1, nullable = false)
    private String docType;

    @Id
    @Column(name = "REQ_DATE", length = 8, nullable = false)
    private String reqDate;

    @Column(name = "DOC_ID", length = 50)
    private String docId;
}
