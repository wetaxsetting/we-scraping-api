package com.kosign.wecms.domain.payment.validation;

import com.kosign.wecms.common.i18n.MessageResolver;
import com.kosign.wecms.domain.payment.validation.entity.BlockedCharsRule;
import com.kosign.wecms.domain.payment.validation.repository.BlockedCharsRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Excel-upload special-character validator. Mirrors legacy
 * {@code wabooks.validator.SpecialCharValidator}.
 *
 * <p>The validator is consulted per cell in the Excel parsers
 * (Internal Transfer / Vendor / Expense / Payroll / Overseas) for the
 * fields covered by {@link FieldCd}. It loads the active {@code
 * BLOCKED_CHARS_RULE} set once and serves all lookups against a
 * per-instance snapshot guarded by a TTL — so a single
 * Excel-parse call (up to 500 rows × 2 fields = 1,000 lookups) does
 * not hammer the DB.
 *
 * <p>Mismatch semantics: substring-membership, NOT regex. A rule with
 * {@code BLOCKED_CHARS = "<>'"@$&"} rejects any cell containing any of
 * those characters anywhere in the string.
 *
 * <p>Snapshot TTL is short (60 s) because rule edits go through the
 * admin UI and need to take effect within a session.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpecialCharValidator {

    private static final DateTimeFormatter DT8     = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final ZoneId            VN_TZ   = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final long              TTL_MS  = 60_000L;

    private final BlockedCharsRuleRepository ruleRepository;
    private final MessageResolver            messageResolver;

    /** Snapshot of {@code Map<(bankCd|pymnType|fieldCd), Set<Character>>}
     *  with the instant it was loaded. Refreshed when older than
     *  {@link #TTL_MS}. */
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(null);

    /**
     * Validate one cell value against the active rule (if any) for the
     * given {@code (bankCd, pymnType, fieldCd)}. Returns the localized
     * error message when the value contains a blocked character;
     * returns {@link Optional#empty()} when the cell is clean OR when
     * no rule is registered for the tuple.
     *
     * <p>Empty / null inputs always pass — required-field validation
     * is the parser's responsibility, not the special-char check.
     */
    public Optional<String> validate(
            String   bankCd,
            PymnType pymnType,
            FieldCd  fieldCd,
            String   value) {

        if (value == null || value.isEmpty()) return Optional.empty();
        if (bankCd == null || bankCd.isBlank()) return Optional.empty();

        Set<Character> blocked = lookup(bankCd.trim(), pymnType.code(), fieldCd.code());
        if (blocked.isEmpty()) return Optional.empty();

        StringBuilder hit = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (blocked.contains(c) && hit.indexOf(String.valueOf(c)) < 0) {
                if (hit.length() > 0) hit.append(' ');
                hit.append(c);
            }
        }
        if (hit.length() == 0) return Optional.empty();

        String fieldLabel = messageResolver.resolve("payment.upload.field." + fieldCd.code(), fieldCd.code());
        String message = messageResolver.resolve(
                "payment.upload.error.blocked_chars",
                fieldLabel, hit.toString());
        return Optional.of(message);
    }

    /* ───────────────── Snapshot ───────────────── */

    private Set<Character> lookup(String bankCd, String pymnTypeCode, String fieldCdCode) {
        Snapshot s = currentSnapshot();
        Set<Character> chars = s.rules.get(key(bankCd, pymnTypeCode, fieldCdCode));
        return chars != null ? chars : Set.of();
    }

    private Snapshot currentSnapshot() {
        Snapshot s = snapshot.get();
        long now = System.currentTimeMillis();
        if (s != null && now - s.loadedAt < TTL_MS) {
            return s;
        }
        Snapshot reloaded = loadSnapshot(now);
        // Race-tolerant: any thread can publish; readers see a consistent
        // (bank, type, field) → chars map within one validate() call.
        snapshot.compareAndSet(s, reloaded);
        return reloaded;
    }

    private Snapshot loadSnapshot(long now) {
        String today = LocalDate.now(VN_TZ).format(DT8);
        List<BlockedCharsRule> rows = ruleRepository.findActiveRules(today);
        Map<String, Set<Character>> map = new HashMap<>(rows.size());
        for (BlockedCharsRule r : rows) {
            Set<Character> chars = new HashSet<>(r.getBlockedChars().length());
            for (int i = 0; i < r.getBlockedChars().length(); i++) {
                chars.add(r.getBlockedChars().charAt(i));
            }
            map.put(key(r.getBankCd(), r.getPymnType(), r.getFieldCd()), chars);
        }
        log.info("feature=special-char-validator snapshot loaded asOf={} rules={}", today, rows.size());
        return new Snapshot(map, now);
    }

    private static String key(String bankCd, String pymnType, String fieldCd) {
        return bankCd + "|" + pymnType + "|" + fieldCd;
    }

    private record Snapshot(Map<String, Set<Character>> rules, long loadedAt) {}
}
