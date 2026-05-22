package com.kosign.wecms.domain.payment.cardtransactions.repository;

import com.kosign.wecms.domain.payment.cardtransactions.entity.CardTrnsRcvPayInfm;
import com.kosign.wecms.domain.payment.cardtransactions.entity.CardTrnsRcvPayInfmId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Card-link master rows ({@code CARD_TRNS_RCV_PAY_INFM}). One row per
 * linked card transaction; child documents on
 * {@code BUSA_DOC_RCV_PAY_HIS}.
 */
public interface CardTrnsRcvPayInfmRepository
        extends JpaRepository<CardTrnsRcvPayInfm, CardTrnsRcvPayInfmId> {

    /** "Is this card transaction already linked?" guard — the legacy
     *  {@code WSE0409} check before allocating a new {@code RCV_PAY_SEQ_NO}. */
    boolean existsByBizKeyAndCardSeqNo(String bizKey, String cardSeqNo);

    /** Find every link row for one card transaction — used by the
     *  unlink path to drop the master row alongside its children. */
    List<CardTrnsRcvPayInfm> findByBizKeyAndCardSeqNo(String bizKey, String cardSeqNo);
}
