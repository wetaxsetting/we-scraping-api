package com.kosign.wecms.domain.payment.cardtransactions.service;

import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasCardUsingEntry;
import com.kosign.wecms.domain.payment.cardtransactions.entity.CardMstr;
import com.kosign.wecms.domain.payment.cardtransactions.entity.CardTrnsHstr;
import com.kosign.wecms.domain.payment.cardtransactions.repository.CardTrnsHstrRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Single source of truth for persisting scraped {@code CARD_USING} rows
 * into {@code CARD_TRNS_HSTR}. Shared by both card-scrape orchestrators:
 * the legacy headless {@link CardTransactionSyncService} (deprecated
 * {@code POST /sync}) and the new session/challenge-aware
 * {@code CardTransactionScrapeService} ({@code POST /sync-session}).
 *
 * <p><b>Extracted 2026-06-19</b> from
 * {@code CardTransactionSyncService.persistScrapedRows} so the
 * <b>D5 legacy-composite-key dedup</b> + the Shinhan / Woori
 * purchase-status mapping live in exactly one place and can never drift
 * between the two flows. The dedup key, normalisation, truncation, and
 * status mapping are byte-for-byte the same as the audited D5 fix —
 * do not change them here without re-auditing
 * {@code fnsh_0001_001_c001_act.jsp:1335-1357}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CardTransactionPersister {

    /** Woori Vietnam — its CARD_USING response returns Vietnamese
     *  purchase-status TEXT, mapped to single-digit codes below. */
    private static final String WOORI_VN_BANK_CD = "11704020";
    /** Shinhan Vietnam — same Vietnamese-status-text handling. */
    private static final String SHINHAN_VN_BANK_CD = "11704088";

    private final CardTrnsHstrRepository cardTrnsRepo;
    private final LegacyFunctionRepository legacyFns;

    /**
     * Persist one card's scraped transaction rows. Returns
     * {@code [inserted, skipped]}. Each scraped row is probed against the
     * legacy composite natural key; a hit increments {@code skipped}, a
     * miss inserts a fresh {@code CARD_TRNS_HSTR} row (key allocated via
     * {@code wb_get_next_val} through {@link LegacyFunctionRepository}).
     *
     * @param bizKey tenant scope
     * @param userId audit user
     * @param now    {@code yyyyMMddHHmmss} audit timestamp
     * @param card   the {@code CARD_MSTR} row the scrape ran for
     * @param scraped the Coocon CARD_USING entries for this card
     */
    public int[] persist(String bizKey, String userId, String now,
                         CardMstr card, List<NxisasCardUsingEntry> scraped) {
        int inserted = 0;
        int skipped  = 0;
        for (NxisasCardUsingEntry tx : scraped) {
            String trscDt = normYyyymmdd(tx.getUsingDate());
            if (trscDt == null) {
                // Tx without a date is unusable — Coocon shouldn't
                // emit this but tolerate it to avoid breaking the
                // batch on one bad row.
                continue;
            }
            String authCode = blankToNull(tx.getAuthCode());
            String trscTm   = normHhmmss(tx.getUsingTime());
            BigDecimal amt  = parseAmount(tx.getUsingAmt());
            // Normalised/truncated values shared by the dedup probe and
            // the insert below, so a re-scrape of an already-persisted
            // row matches the stored form (merch_nm 50, auth_code 15).
            String ccyCd          = normCcy(tx.getCurrency());
            String merchNm        = truncate(tx.getFranchise(), 50);
            String storedAuthCode = truncate(authCode, 15);

            // Dedup probe — matches the legacy composite natural key
            // exactly (fnsh_0001_001_c001_act.jsp:1335-1357; audit D5):
            // biz_key, card_no, trsc_dt, trsc_tm, ccy_cd, trsc_amt,
            // merch_nm, coalesce(trim(auth_code),'').
            boolean dup = cardTrnsRepo.existsLegacyScrapeDuplicate(
                    bizKey, card.getCardNo(), trscDt, trscTm, ccyCd, amt, merchNm, storedAuthCode);
            if (dup) {
                skipped++;
                continue;
            }

            String seqNo = legacyFns.nextCardSeqNo(bizKey);
            CardTrnsHstr row = new CardTrnsHstr();
            row.setBizKey(bizKey);
            row.setCardSeqNo(seqNo);
            row.setBankCd(card.getBankCd());
            row.setCardNo(card.getCardNo());
            row.setTrscDt(trscDt);
            row.setTrscTm(trscTm);
            // Prefer the scraped card name when present (legacy
            // fnsh_0001_001_c001_act.jsp:1369); fall back to the
            // CARD_MSTR row's CARD_NM when Coocon omits the field.
            String cardNm = tx.getCardName() != null && !tx.getCardName().isBlank()
                    ? tx.getCardName()
                    : card.getCardNm();
            row.setCardNm(truncate(cardNm, 50));
            row.setMerchNm(merchNm);
            row.setCcyCd(ccyCd);
            // Card transactions are always debits — Coocon's CARD_USING
            // response has no inoutDv, so stamp '2' (OUT) per legacy parity.
            row.setInoutDv("2");
            row.setTrscAmt(amt);
            row.setUsingType(truncate(tx.getUsingType(), 50));
            row.setPrchStts(truncate(mapPurchaseStatus(card.getBankCd(), tx.getPurchaseStatus()), 50));
            // Coocon's CARD_USING response carries no `note` field — null
            // (legacy parity: the action never calls setNote).
            row.setNote(null);
            row.setAuthCode(storedAuthCode);
            row.setCity(truncate(tx.getCity(), 100));
            row.setRegId(userId);
            row.setRegDtm(now);
            row.setModId(userId);
            row.setModDtm(now);
            row.setErpSndYn("N");

            try {
                // saveAndFlush, not save: the next nextCardSeqNo() call is
                // a native SELECT through wb_get_next_val(...) which
                // Hibernate cannot detect as touching CARD_TRNS_HSTR, so
                // auto-flush wouldn't fire and MAX+1 would see pre-batch
                // state → PK collision at commit.
                cardTrnsRepo.saveAndFlush(row);
                inserted++;
            } catch (DataIntegrityViolationException ex) {
                // Concurrent sync collision — count as skip and move on.
                skipped++;
            }
        }
        return new int[] { inserted, skipped };
    }

    /* ------------------------------------------------------------------
     *  Normalisation helpers (moved verbatim from CardTransactionSyncService)
     * ---------------------------------------------------------------- */

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String normYyyymmdd(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("\\D+", "");
        if (digits.length() < 8) return null;
        return digits.substring(0, 8);
    }

    private static String normHhmmss(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("\\D+", "");
        if (digits.isEmpty()) return null;
        return digits.length() > 6 ? digits.substring(0, 6) : digits;
    }

    private static String normCcy(String raw) {
        if (raw == null || raw.isBlank()) return "VND";
        String u = raw.trim().toUpperCase();
        return u.length() > 3 ? u.substring(0, 3) : u;
    }

    private static BigDecimal parseAmount(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            String cleaned = raw.replaceAll("[^0-9.\\-]+", "");
            return cleaned.isEmpty() ? null : new BigDecimal(cleaned);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** Per-bank purchase-status normaliser. Shinhan VN / Woori VN return
     *  Vietnamese status TEXT; map to the single-digit codes the legacy
     *  save action wrote (fnsh_0001_001_c001_act.jsp:1378-1402). Every
     *  other bank passes through unchanged. */
    static String mapPurchaseStatus(String bankCd, String raw) {
        if (raw == null || raw.isBlank()) return raw;
        String trimmed = raw.trim();
        if (SHINHAN_VN_BANK_CD.equals(bankCd)) {
            switch (trimmed) {
                case "Chấp nhận":  return "1";
                case "Hoàn thành": return "2";
                case "Đã hủy":     return "9";
                default:           return trimmed;
            }
        }
        if (WOORI_VN_BANK_CD.equals(bankCd)) {
            if ("Giao dịch được duyệt".equals(trimmed)) return "1";
            return trimmed;
        }
        return trimmed;
    }
}
