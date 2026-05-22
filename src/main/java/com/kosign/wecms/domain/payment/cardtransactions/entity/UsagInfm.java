package com.kosign.wecms.domain.payment.cardtransactions.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Purpose dictionary master. Powers the Purpose picker on the
 * New Receipt popup (legacy {@code prch_0011_001}) and the
 * row-edit Purpose popover on the Card Transactions list (legacy
 * {@code card_0001_001_u001}). Filtered by
 * {@code USAG_GRP_CD} (the New Receipt popup uses {@code "6"}).
 *
 * <p>Localized names live on the sibling
 * {@code USAG_INFM_LANG_DETL} table — see {@link UsagInfmLangDetl}.
 *
 * <p>Schema landed in V201; DEV parity verified via postgres_dev
 * MCP 2026-05-22 (11 columns; single-key PK on {@code USE_USAG_CD}).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "USAG_INFM")
public class UsagInfm {

    @Id
    @Column(name = "USE_USAG_CD", length = 20, nullable = false)
    private String useUsagCd;

    @Column(name = "USAG_GRP_CD", length = 3, nullable = false)
    private String usagGrpCd;

    @Column(name = "DEL_YN", length = 1)
    private String delYn;

    @Column(name = "USAG_TYPE", length = 2)
    private String usagType;

    @Column(name = "HAND_USE_USAG_CD", length = 20)
    private String handUseUsagCd;
}
