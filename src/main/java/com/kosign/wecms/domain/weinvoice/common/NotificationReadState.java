package com.kosign.wecms.domain.weinvoice.common;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read state of one owner's bell, from the rows of {@code winv_ntfc_read}.
 *
 * <p>An item is read when its own id was marked read, or when its time (the latest event of its bucket,
 * {@code yyyyMMddHHmmss} UTC, whole seconds) is at or before the applicable "read all" marker: the newer
 * of {@code *} (every MST) and {@code *|<mst>} (its MST). An item without MST (a mailbox serving every tax
 * code) only has the {@code *} marker. A bucket that receives a new event after the marker therefore turns
 * unread again; a bucket marked read by id stays read.
 */
public final class NotificationReadState {

    private final Set<String> ownKeys;
    private final String allMarker;
    private final Map<String, String> mstMarkers;

    private NotificationReadState(Set<String> ownKeys, String allMarker, Map<String, String> mstMarkers) {
        this.ownKeys = ownKeys;
        this.allMarker = allMarker;
        this.mstMarkers = mstMarkers;
    }

    /** One stored row: the key and its {@code read_dtm}. */
    public record Row(String key, String readDtm) {
    }

    public static NotificationReadState of(List<Row> rows) {
        Set<String> own = new HashSet<>();
        String all = null;
        Map<String, String> perMst = new HashMap<>();
        for (Row r : rows) {
            if (r.key() == null || r.readDtm() == null) continue;
            if (NotificationKey.ALL_MARKER.equals(r.key())) {
                all = max(all, r.readDtm());
            } else if (r.key().startsWith(NotificationKey.ALL_MARKER + "|")) {
                perMst.merge(r.key().substring(2), r.readDtm(), NotificationReadState::max);
            } else {
                own.add(r.key());
            }
        }
        return new NotificationReadState(own, all, perMst);
    }

    /** The marker that applies to {@code mst} ({@code null} = no MST), or {@code null} when none. */
    public String markerFor(String mst) {
        return mst == null ? allMarker : max(allMarker, mstMarkers.get(mst));
    }

    /**
     * @param key   the item id
     * @param mst   the item's MST, {@code null} when it has none
     * @param atDtm the latest event of the item, {@code yyyyMMddHHmmss} UTC
     */
    public boolean isRead(String key, String mst, String atDtm) {
        if (ownKeys.contains(key)) return true;
        String marker = markerFor(mst);
        return marker != null && atDtm != null && atDtm.compareTo(marker) <= 0;
    }

    /** Larger of two 14-digit dtm values ({@code null} ignored). */
    private static String max(String a, String b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.compareTo(b) >= 0 ? a : b;
    }
}
