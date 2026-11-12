package com.kosign.wecms.domain.weinvoice.common;

import com.kosign.wecms.domain.weinvoice.common.NotificationReadState.Row;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Read rule: own id, or the item's latest event at/before the applicable "read all" marker — the newer of
 * {@code *} and {@code *|<mst>}; an item without MST only sees {@code *}.
 */
class NotificationReadStateTest {

    private static final String MST = "1201496252";
    private static final String OTHER = "0101243150";
    private static final String KEY = "NEW_INVOICES|" + MST + "|purchase|2026111208";

    @Test
    void nothingStoredMeansUnread() {
        NotificationReadState s = NotificationReadState.of(List.of());
        assertThat(s.isRead(KEY, MST, "20261112081500")).isFalse();
        assertThat(s.markerFor(MST)).isNull();
    }

    @Test
    void ownKeyIsReadWhateverItsTime() {
        NotificationReadState s = NotificationReadState.of(List.of(new Row(KEY, "20261112080000")));
        assertThat(s.isRead(KEY, MST, "20261112085959")).isTrue();
        assertThat(s.isRead("XML_READY|" + MST + "|purchase|2026111208", MST, "20261112081500")).isFalse();
    }

    @Test
    void allMarkerCoversEveryMstUpToItsTime() {
        NotificationReadState s = NotificationReadState.of(List.of(new Row("*", "20261112090000")));
        assertThat(s.isRead(KEY, MST, "20261112085959")).isTrue();
        assertThat(s.isRead(KEY, MST, "20261112090000")).as("equal = read").isTrue();
        assertThat(s.isRead(KEY, MST, "20261112090001")).as("newer event = unread again").isFalse();
        assertThat(s.isRead("MAILBOX_ERROR|-|-|20261112080000.1", null, "20261112080000")).isTrue();
    }

    @Test
    void mstMarkerCoversOnlyItsMst() {
        NotificationReadState s = NotificationReadState.of(List.of(new Row("*|" + MST, "20261112090000")));
        assertThat(s.isRead(KEY, MST, "20261112085959")).isTrue();
        assertThat(s.isRead("NEW_INVOICES|" + OTHER + "|purchase|2026111208", OTHER, "20261112085959")).isFalse();
        assertThat(s.isRead("MAILBOX_ERROR|-|-|20261112080000.1", null, "20261112080000"))
                .as("an item without MST is not covered by a per-MST marker").isFalse();
    }

    @Test
    void theNewerOfTheTwoMarkersApplies() {
        NotificationReadState olderAll = NotificationReadState.of(List.of(
                new Row("*", "20261110000000"), new Row("*|" + MST, "20261112090000")));
        assertThat(olderAll.markerFor(MST)).isEqualTo("20261112090000");
        assertThat(olderAll.isRead(KEY, MST, "20261111000000")).isTrue();
        assertThat(olderAll.isRead("NEW_INVOICES|" + OTHER + "|purchase|2026111100", OTHER, "20261111000000")).isFalse();

        NotificationReadState newerAll = NotificationReadState.of(List.of(
                new Row("*", "20261112100000"), new Row("*|" + MST, "20261112090000")));
        assertThat(newerAll.markerFor(MST)).isEqualTo("20261112100000");
        assertThat(newerAll.isRead(KEY, MST, "20261112095959")).isTrue();
    }
}
