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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    /** Snapshot of {@code Map<(bankCd|pymnType|fieldCd), orderedBlockedChars>}
     *  with the instant it was loaded. The value is the ordered, deduped
     *  configured blocked-char string (union of all matching rules). Refreshed
     *  when older than {@link #TTL_MS}. */
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

        String blocked = lookup(bankCd.trim(), pymnType.code(), fieldCd.code());
        if (blocked.isEmpty()) return Optional.empty();

        // Detection: does the value contain ANY configured blocked char?
        // Per-character membership, NOT regex (legacy parity).
        boolean anyBlocked = false;
        for (int i = 0; i < value.length(); i++) {
            if (blocked.indexOf(value.charAt(i)) >= 0) { anyBlocked = true; break; }
        }
        if (!anyBlocked) return Optional.empty();

        // Message content = the FULL configured blocked set (ordered, deduped),
        // NOT only the characters found — legacy parity with
        // wabooks.validator.SpecialCharValidator:130-132 ("show ALL blocked
        // chars, not just found ones"). Single space between each character.
        String display = spaceSeparated(blocked);
        String fieldLabel = messageResolver.resolve("payment.upload.field." + fieldCd.code(), fieldCd.code());
        String message = messageResolver.resolve(
                "payment.upload.error.blocked_chars",
                fieldLabel, display);
        return Optional.of(message);
    }

    /**
     * Dispatch-time defense-in-depth (audit task-44508, P0). Does {@code value}
     * contain any character blocked for {@code (bankCd, fieldCd)} under ANY
     * payment type? At dispatch the caller only knows the withdrawal bank + the
     * field being written to the bank file (not the originating payment type),
     * so this unions the blocked sets across all {@link PymnType}s — a strict
     * superset of the per-type check (bank+field rules are identical across
     * types in practice). Returns the localized "{field} must not contain:
     * {full set}" message, or empty when clean / no rule / blank input.
     *
     * @param bankCd the INTERNAL withdrawal {@code BANK_CD} (rules are keyed on
     *               it — resolve the NAPAS {@code OUT_BANK_CD} first).
     */
    public Optional<String> validateAnyType(String bankCd, FieldCd fieldCd, String value) {
        if (value == null || value.isEmpty()) return Optional.empty();
        if (bankCd == null || bankCd.isBlank()) return Optional.empty();

        Snapshot s = currentSnapshot();
        LinkedHashSet<Character> union = new LinkedHashSet<>();
        for (PymnType t : PymnType.values()) {
            String chars = s.rules.get(key(bankCd.trim(), t.code(), fieldCd.code()));
            if (chars != null) {
                for (int i = 0; i < chars.length(); i++) union.add(chars.charAt(i));
            }
        }
        if (union.isEmpty()) return Optional.empty();

        boolean any = false;
        for (int i = 0; i < value.length(); i++) {
            if (union.contains(value.charAt(i))) { any = true; break; }
        }
        if (!any) return Optional.empty();

        StringBuilder disp = new StringBuilder(union.size() * 2);
        for (Character c : union) {
            if (disp.length() > 0) disp.append(' ');
            disp.append(c.charValue());
        }
        String fieldLabel = messageResolver.resolve("payment.upload.field." + fieldCd.code(), fieldCd.code());
        return Optional.of(messageResolver.resolve(
                "payment.upload.error.blocked_chars", fieldLabel, disp.toString()));
    }

    /** Render an ordered blocked-char string with one space between chars
     *  (e.g. {@code "@$'\"<>"} → {@code "@ $ ' \" < >"}). */
    private static String spaceSeparated(String chars) {
        StringBuilder sb = new StringBuilder(chars.length() * 2);
        for (int i = 0; i < chars.length(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(chars.charAt(i));
        }
        return sb.toString();
    }

    /* ───────────────── Snapshot ───────────────── */

    /** Ordered, deduped configured blocked chars for the tuple, or "" when no rule. */
    private String lookup(String bankCd, String pymnTypeCode, String fieldCdCode) {
        Snapshot s = currentSnapshot();
        String chars = s.rules.get(key(bankCd, pymnTypeCode, fieldCdCode));
        return chars != null ? chars : "";
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
        // Union all rules that share a (bank|type|field) key into one ordered,
        // deduped char sequence. LinkedHashSet preserves configured order and
        // dedups; legacy unions the same way (SpecialCharValidator:95-104).
        Map<String, LinkedHashSet<Character>> acc = new HashMap<>(rows.size());
        for (BlockedCharsRule r : rows) {
            String bc = r.getBlockedChars();
            if (bc == null || bc.isEmpty()) continue;
            LinkedHashSet<Character> set = acc.computeIfAbsent(
                    key(r.getBankCd(), r.getPymnType(), r.getFieldCd()),
                    k -> new LinkedHashSet<>());
            for (int i = 0; i < bc.length(); i++) set.add(bc.charAt(i));
        }
        Map<String, String> map = new HashMap<>(acc.size());
        for (Map.Entry<String, LinkedHashSet<Character>> e : acc.entrySet()) {
            StringBuilder sb = new StringBuilder(e.getValue().size());
            for (Character c : e.getValue()) sb.append(c.charValue());
            map.put(e.getKey(), sb.toString());
        }
        log.info("feature=special-char-validator snapshot loaded asOf={} rules={}", today, rows.size());
        return new Snapshot(map, now);
    }

    private static String key(String bankCd, String pymnType, String fieldCd) {
        return bankCd + "|" + pymnType + "|" + fieldCd;
    }

    private record Snapshot(Map<String, String> rules, long loadedAt) {}
}
