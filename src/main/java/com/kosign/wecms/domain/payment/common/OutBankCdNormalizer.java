package com.kosign.wecms.domain.payment.common;

import com.kosign.wecms.domain.banking.entity.NapasBankInfo;
import com.kosign.wecms.domain.banking.repository.NapasBankInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Shared resolver that folds a user-supplied bank code into the
 * canonical NAPAS bank code before it gets persisted to
 * {@code SCDUL_PYMN_SUMR.OUT_BANK_CD} (or {@code OVRS_PYMN_SUMR.OUT_BANK_CD}).
 *
 * <p>Mirrors legacy {@code PymnHelper.uploadPaymentExcel} at
 * {@code wabooks/helper/PymnHelper.java:1315-1317}:
 * <pre>
 *   NapasBankInfo napasBankInfo = NapasBankInfoDAO.getInstance()
 *       .findByDynamic(util, idoCon,
 *           IdoUtils.getDynamic(" and bank_cd = ? or napas_bank_cd = ?",
 *               input.getString("BANK_CD"),
 *               input.getString("BANK_CD")));
 *   scdulPymnSumr.setOutBankCd(napasBankInfo.getNapasBankCd());
 * </pre>
 *
 * <p>The legacy OR query matches the user input against either the
 * internal {@code BANK_CD} (e.g. {@code 11704088}) or the public NAPAS
 * code (e.g. {@code 970424}) and writes back the canonical NAPAS code.
 * Refactor splits that into two finder calls — same outcome.
 *
 * <p>Downstream consumers of {@code OUT_BANK_CD} (cut-off validator,
 * dispatch executor, audit log queries) all assume the canonical NAPAS
 * form; persisting the raw user input was a family-wide regression
 * caught in the 2026-05-29 cross-module audit (gap U1).
 *
 * <p>Behaviour on miss: returns the trimmed user input. Mirrors legacy
 * implicit fall-through (legacy would NPE on {@code getNapasBankCd()}
 * when the lookup misses, which doubles as a guard; refactor logs a
 * warn instead so refactor R&D flows without seeding
 * {@code NAPAS_BANK_INFO} for every test bank code). The downstream
 * cut-off + dispatch paths handle unknown codes gracefully
 * ({@code CutoffValidator} silent-passes; dispatch hits a different
 * error path).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutBankCdNormalizer {

    private final NapasBankInfoRepository napasRepo;

    /**
     * Resolve the canonical NAPAS bank code for a user-supplied code.
     *
     * <p>The input may be in either form:
     * <ul>
     *   <li>The public NAPAS code itself ({@code 970xxx}) — found via PK
     *       lookup on {@code NAPAS_BANK_INFO.NAPAS_BANK_CD}.</li>
     *   <li>The internal {@code BANK_CD} ({@code 1170xxxx}) — found via
     *       the secondary index on {@code NAPAS_BANK_INFO.BANK_CD}.</li>
     * </ul>
     *
     * <p>Returns the trimmed input verbatim when neither lookup hits —
     * logs a warn so the operator can spot an unseeded bank.
     *
     * @param userBankCd raw bank code from a Create Draft / Upload form;
     *                   may be {@code null} or blank.
     * @return canonical NAPAS code, or the trimmed input on miss.
     *         {@code null} / blank inputs pass through unchanged.
     */
    public String normalize(String userBankCd) {
        if (userBankCd == null || userBankCd.isBlank()) {
            return userBankCd;
        }
        String code = userBankCd.trim();

        // Try as NAPAS code (PK).
        Optional<NapasBankInfo> hit = napasRepo.findById(code);
        if (hit.isPresent()) {
            return hit.get().getNapasBankCd();
        }

        // Try as internal BANK_CD.
        hit = napasRepo.findByBankCd(code);
        if (hit.isPresent()) {
            return hit.get().getNapasBankCd();
        }

        log.warn("feature=payment OUT_BANK_CD normalize: no NAPAS_BANK_INFO row for code='{}'; persisting raw input",
                code);
        return code;
    }
}
