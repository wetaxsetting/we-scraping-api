package com.kosign.wecms.domain.payment.cardtransactions.service;

import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasCardUsingEntry;
import com.kosign.wecms.domain.payment.cardtransactions.entity.CardMstr;
import com.kosign.wecms.domain.payment.cardtransactions.entity.CardTrnsHstr;
import com.kosign.wecms.domain.payment.cardtransactions.repository.CardTrnsHstrRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Card-number attribution + dedup coverage for {@link CardTransactionPersister}
 * (the shared single-writer used by both card-scrape orchestrators).
 *
 * <p>Regression guard for the 2026-07-02 bug where one scraped merchant
 * transaction was inserted once per registered card (4372 / 3861 / 8524 /
 * 1167) instead of only under the card it actually belongs to. The fix
 * attributes each scraped row by its own {@code cardNum}
 * ({@code fnsh_0001_001_c001_act.jsp:1327-1330} parity) and drops rows
 * that belong to a different card — they land during that card's own
 * persist call.
 *
 * <p>Pure Mockito: a real {@link CardTransactionPersister} over a mocked
 * {@link CardTrnsHstrRepository} (dedup probe + save) and a mocked
 * {@link LegacyFunctionRepository} (key allocation).
 */
@ExtendWith(MockitoExtension.class)
class CardTransactionPersisterTest {

    private static final String BIZ  = "VN_VNM0004_000002";
    private static final String USER = "tester";
    private static final String NOW  = "20260702225105";
    private static final String BANK = "11704088"; // Shinhan Vietnam

    private static final String CARD_4372 = "4696********4372";
    private static final String CARD_3861 = "4696********3861";
    private static final String CARD_8524 = "4696********8524";
    private static final String CARD_1167 = "4696********1167";

    @Mock private CardTrnsHstrRepository   cardTrnsRepo;
    @Mock private LegacyFunctionRepository legacyFns;

    private CardTransactionPersister newPersister() {
        // Unique, monotonically increasing seq so each inserted row is distinct.
        AtomicInteger seq = new AtomicInteger(1000);
        // lenient: the dedup-skip case never reaches key allocation.
        lenient().when(legacyFns.nextCardSeqNo(anyString()))
                .thenAnswer(inv -> String.valueOf(seq.getAndIncrement()));
        return new CardTransactionPersister(cardTrnsRepo, legacyFns);
    }

    private CardMstr card(String cardNo) {
        CardMstr c = new CardMstr();
        c.setBizKey(BIZ);
        c.setBankCd(BANK);
        c.setCardNo(cardNo);
        c.setCardNm("Shinhan " + cardNo.substring(cardNo.length() - 4));
        return c;
    }

    /** One scraped FACEBK tx that actually belongs to card 4372. */
    private NxisasCardUsingEntry facebk(String scrapedCardNo) {
        NxisasCardUsingEntry e = new NxisasCardUsingEntry();
        e.setCardNum(scrapedCardNo);
        e.setCardName("Shinhan(Vietnam)");
        e.setUsingDate("20260630");
        e.setUsingTime("225105");
        e.setFranchise("FACEBK *49D6XUDPJ2");
        e.setUsingAmt("550000");
        e.setCurrency("VND");
        e.setAuthCode("012345");
        return e;
    }

    /**
     * Case 1 — 4 registered cards, the CARD_USING response for EACH card
     * carries the same one scraped transaction (whose real card is 4372,
     * Coocon's cardArr[0] over-return). Expected: exactly ONE inserted row,
     * under card 4372 only. The other three per-card persist calls skip it.
     */
    @Test
    void oneTransaction_insertedOnlyUnderItsOwnCard_notAllFour() {
        CardTransactionPersister persister = newPersister();
        when(cardTrnsRepo.existsLegacyScrapeDuplicate(
                anyString(), anyString(), anyString(), any(), anyString(),
                any(), anyString(), any())).thenReturn(false);

        // The gateway returns the SAME single tx (belongs to 4372) for every
        // registered card's CARD_USING call — the over-return that caused the bug.
        List<NxisasCardUsingEntry> scraped = List.of(facebk(CARD_4372));

        int[] c4372 = persister.persist(BIZ, USER, NOW, card(CARD_4372), scraped);
        int[] c3861 = persister.persist(BIZ, USER, NOW, card(CARD_3861), scraped);
        int[] c8524 = persister.persist(BIZ, USER, NOW, card(CARD_8524), scraped);
        int[] c1167 = persister.persist(BIZ, USER, NOW, card(CARD_1167), scraped);

        // Only the matching card inserts; the other three attribute-skip.
        assertThat(c4372).containsExactly(1, 0);
        assertThat(c3861).containsExactly(0, 1);
        assertThat(c8524).containsExactly(0, 1);
        assertThat(c1167).containsExactly(0, 1);

        ArgumentCaptor<CardTrnsHstr> saved = ArgumentCaptor.forClass(CardTrnsHstr.class);
        verify(cardTrnsRepo, times(1)).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getCardNo()).isEqualTo(CARD_4372);
        assertThat(saved.getValue().getMerchNm()).isEqualTo("FACEBK *49D6XUDPJ2");
    }

    /**
     * Case 2 — the same transaction scraped again (re-run). The dedup probe
     * on the legacy composite natural key returns true, so no second row is
     * inserted even though the card matches.
     */
    @Test
    void sameTransactionScrapedAgain_notReinserted() {
        CardTransactionPersister persister = newPersister();
        when(cardTrnsRepo.existsLegacyScrapeDuplicate(
                anyString(), eq(CARD_4372), anyString(), any(), anyString(),
                any(), anyString(), any())).thenReturn(true); // already persisted

        int[] counts = persister.persist(
                BIZ, USER, NOW, card(CARD_4372), List.of(facebk(CARD_4372)));

        assertThat(counts).containsExactly(0, 1); // 0 inserted, 1 dedup-skipped
        verify(cardTrnsRepo, never()).saveAndFlush(any());
    }

    /**
     * Case 3 — one CARD_USING response carrying transactions for several
     * different card numbers. Each transaction is inserted only under its
     * own card, across the per-card persist calls.
     */
    @Test
    void multipleTransactionsForDifferentCards_eachUnderItsOwnCardOnly() {
        CardTransactionPersister persister = newPersister();
        when(cardTrnsRepo.existsLegacyScrapeDuplicate(
                anyString(), anyString(), anyString(), any(), anyString(),
                any(), anyString(), any())).thenReturn(false);

        // A mixed batch: one tx each for 4372, 3861, 8524, 1167.
        List<NxisasCardUsingEntry> mixed = List.of(
                facebk(CARD_4372), facebk(CARD_3861), facebk(CARD_8524), facebk(CARD_1167));

        int[] c4372 = persister.persist(BIZ, USER, NOW, card(CARD_4372), mixed);
        int[] c3861 = persister.persist(BIZ, USER, NOW, card(CARD_3861), mixed);
        int[] c8524 = persister.persist(BIZ, USER, NOW, card(CARD_8524), mixed);
        int[] c1167 = persister.persist(BIZ, USER, NOW, card(CARD_1167), mixed);

        // Each call inserts exactly its own card's tx and skips the other three.
        assertThat(c4372).containsExactly(1, 3);
        assertThat(c3861).containsExactly(1, 3);
        assertThat(c8524).containsExactly(1, 3);
        assertThat(c1167).containsExactly(1, 3);

        ArgumentCaptor<CardTrnsHstr> saved = ArgumentCaptor.forClass(CardTrnsHstr.class);
        verify(cardTrnsRepo, times(4)).saveAndFlush(saved.capture());
        assertThat(saved.getAllValues())
                .extracting(CardTrnsHstr::getCardNo)
                .containsExactlyInAnyOrder(CARD_4372, CARD_3861, CARD_8524, CARD_1167);
    }

    /**
     * Defensive fallback — a scraped tx with a blank {@code cardNum} is
     * attributed to the requested card (we asked Coocon for it). Guards
     * against a Coocon response that omits the field silently dropping
     * every row. Documented divergence from legacy's skip-on-null.
     */
    @Test
    void blankScrapedCardNum_fallsBackToRequestedCard() {
        CardTransactionPersister persister = newPersister();
        when(cardTrnsRepo.existsLegacyScrapeDuplicate(
                anyString(), anyString(), anyString(), any(), anyString(),
                any(), anyString(), any())).thenReturn(false);

        int[] counts = persister.persist(
                BIZ, USER, NOW, card(CARD_4372), List.of(facebk(null)));

        assertThat(counts).containsExactly(1, 0);
        ArgumentCaptor<CardTrnsHstr> saved = ArgumentCaptor.forClass(CardTrnsHstr.class);
        verify(cardTrnsRepo, times(1)).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getCardNo()).isEqualTo(CARD_4372);
    }

    /** Unit coverage for the matcher: masking / spacing / case tolerance. */
    @Test
    void cardNoMatches_toleratesSpacingAndCase_keepsMask() {
        assertThat(CardTransactionPersister.cardNoMatches("4696********4372", "4696********4372")).isTrue();
        assertThat(CardTransactionPersister.cardNoMatches("4696 ******** 4372", "4696********4372")).isTrue();
        assertThat(CardTransactionPersister.cardNoMatches("4696********4372", "4696********3861")).isFalse();
        assertThat(CardTransactionPersister.cardNoMatches(null, "4696********4372")).isTrue();
        assertThat(CardTransactionPersister.cardNoMatches("  ", "4696********4372")).isTrue();
    }
}
