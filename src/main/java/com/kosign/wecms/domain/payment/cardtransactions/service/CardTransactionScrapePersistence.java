package com.kosign.wecms.domain.payment.cardtransactions.service;

import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasCardUsingEntry;
import com.kosign.wecms.domain.payment.cardtransactions.entity.CardMstr;
import com.kosign.wecms.domain.payment.cardtransactions.repository.CardMstrRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Short-transaction persistence boundary for the async Card Transactions
 * Update flow (Phase 2). The card twin of
 * {@code BankTransactionSyncPersistence}: it exists so
 * {@link CardTransactionScrapeService}'s background worker can run Coocon
 * gateway calls <b>outside</b> any DB transaction and commit each card's
 * scraped rows in its own short {@code @Transactional} unit — never holding a
 * DB connection open across a gateway round-trip or an OTP/QNA/CAPTCHA wait.
 *
 * <p>All persistence logic (the D5 legacy composite-key dedup + the Shinhan /
 * Woori purchase-status mapping) lives in the shared
 * {@link CardTransactionPersister}; this bean only supplies the transaction
 * boundary + the {@code TRN_LST_SUC_DTM} stamp, mirroring the pre-Phase-2
 * behaviour of {@code CardTransactionScrapeService.processOneCard} (which ran
 * inside one whole-batch {@code @Transactional}). The stamp is written in the
 * same tx as the inserts, only when the scrape returned rows — legacy
 * {@code fnsh_0001_001_c001_act.jsp:1413/1422} holds the cursor on an empty
 * result so a re-sync re-checks the same window.
 */
@Component
@RequiredArgsConstructor
public class CardTransactionScrapePersistence {

    private final CardTransactionPersister cardPersister;
    private final CardMstrRepository cardMstrRepo;

    /**
     * Persist one card's scraped rows + stamp its last-success marker, in one
     * short transaction. Returns {@code [inserted, skipped]}.
     *
     * @param bizKey  tenant scope
     * @param userId  audit user
     * @param now     {@code yyyyMMddHHmmss} audit timestamp
     * @param card    the {@code CARD_MSTR} row the scrape ran for (bizKey +
     *                bankCd + cardNo populated by the caller)
     * @param scraped Coocon CARD_USING entries for this card
     */
    @Transactional
    public int[] persistCard(String bizKey, String userId, String now,
                             CardMstr card, List<NxisasCardUsingEntry> scraped) {
        int[] counts = cardPersister.persist(bizKey, userId, now, card, scraped);
        // Advance the per-card cursor only when the scrape actually returned
        // rows (legacy fnsh_0001_001_c001_act.jsp:1413/1422 — an empty result
        // holds the cursor so tomorrow re-checks today).
        if (!scraped.isEmpty()) {
            cardMstrRepo.patchTrnLstSucDtm(bizKey, card.getBankCd(), card.getCardNo(), now, userId, now);
        }
        return counts;
    }
}
