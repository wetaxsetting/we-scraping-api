package com.kosign.wecms.domain.weinvoice.common;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Id of a bell notification — {@code <TYPE>|<mst or ->|<direction or ->|<bucket>}.
 *
 * <p>Notifications are not stored: the id is derived from the aggregate it names, so the same bucket
 * always has the same id and {@code winv_ntfc_read} can remember it. Buckets:
 * <ul>
 *   <li>counts ({@code NEW_INVOICES}, {@code XML_READY}, {@code PDF_READY}) — the UTC hour {@code yyyyMMddHH},
 *       direction {@code purchase} / {@code sales};</li>
 *   <li>{@code GDT_LOGIN_ERROR} — the 14-digit UTC time the failure was recorded ({@code conn_chk_dtm}), so a
 *       new failure gets a new id and is unread again;</li>
 *   <li>{@code MAILBOX_ERROR} — the same dtm followed by {@code .<mailbox id>}: two mailboxes of one owner can
 *       fail within the same second, and the mailbox id keeps their ids distinct. MST is {@code -} for a
 *       mailbox that serves every tax code of the owner.</li>
 * </ul>
 * Record: docs/features/addon-service/weinvoice/weinvoice-notifications-2026-11-12.md.
 */
public record NotificationKey(Type type, String mst, String direction, String bucket) {

    /** Read-all marker covering every MST of the owner. */
    public static final String ALL_MARKER = "*";
    /** Longest id {@code winv_ntfc_read.ntfc_key} accepts. */
    public static final int MAX_LENGTH = 160;

    private static final String MST = "(\\d{10}(?:-\\d{3})?)";
    private static final Pattern COUNT = Pattern.compile(
            "(NEW_INVOICES|XML_READY|PDF_READY)\\|" + MST + "\\|(purchase|sales)\\|(\\d{10})");
    private static final Pattern GDT = Pattern.compile("GDT_LOGIN_ERROR\\|" + MST + "\\|-\\|(\\d{14})");
    private static final Pattern MAILBOX = Pattern.compile("MAILBOX_ERROR\\|(-|\\d{10}(?:-\\d{3})?)\\|-\\|(\\d{14}\\.\\d{1,18})");

    /** Notification types; the two error types are action items. */
    public enum Type {
        NEW_INVOICES(false),
        XML_READY(false),
        PDF_READY(false),
        GDT_LOGIN_ERROR(true),
        MAILBOX_ERROR(true);

        private final boolean action;

        Type(boolean action) {
            this.action = action;
        }

        /** True for the items the user has to act on ("Cần xử lý"). */
        public boolean action() {
            return action;
        }
    }

    /** The wire id. {@code mst} / {@code direction} {@code null} → {@code -}. */
    public String id() {
        return type.name() + "|" + dash(mst) + "|" + dash(direction) + "|" + bucket;
    }

    /** Marker key for "read all" of one MST ({@code *|<mst>}) or of every MST ({@code *}). */
    public static String marker(String mst) {
        return mst == null ? ALL_MARKER : ALL_MARKER + "|" + mst;
    }

    /** Engine direction ({@code mua} / {@code ban}) → wire direction; anything else → {@code null}. */
    public static String wireDirection(String engineDirection) {
        if ("mua".equals(engineDirection)) return "purchase";
        if ("ban".equals(engineDirection)) return "sales";
        return null;
    }

    /**
     * Strict parse of a client-sent id. Anything that is not exactly the server's own format — wrong type,
     * malformed MST, a marker, wrong bucket shape, too long — is empty, so it can never be stored.
     */
    public static Optional<NotificationKey> parse(String id) {
        if (id == null || id.length() > MAX_LENGTH) return Optional.empty();
        Matcher m = COUNT.matcher(id);
        if (m.matches()) {
            return Optional.of(new NotificationKey(Type.valueOf(m.group(1)), m.group(2), m.group(3), m.group(4)));
        }
        m = GDT.matcher(id);
        if (m.matches()) {
            return Optional.of(new NotificationKey(Type.GDT_LOGIN_ERROR, m.group(1), null, m.group(2)));
        }
        m = MAILBOX.matcher(id);
        if (m.matches()) {
            String mst = "-".equals(m.group(1)) ? null : m.group(1);
            return Optional.of(new NotificationKey(Type.MAILBOX_ERROR, mst, null, m.group(2)));
        }
        return Optional.empty();
    }

    private static String dash(String v) {
        return v == null || v.isEmpty() ? "-" : v;
    }
}
