package com.kosign.wecms.domain.weinvoice.common;

import com.kosign.wecms.domain.weinvoice.common.NotificationKey.Type;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Notification id format (record weinvoice-notifications-2026-11-12.md, "Item key and read state"). */
class NotificationKeyTest {

    @Test
    void idFormatPerType() {
        assertThat(new NotificationKey(Type.NEW_INVOICES, "1201496252", "purchase", "2026111208").id())
                .isEqualTo("NEW_INVOICES|1201496252|purchase|2026111208");
        assertThat(new NotificationKey(Type.GDT_LOGIN_ERROR, "0106026495-001", null, "20261112083015").id())
                .isEqualTo("GDT_LOGIN_ERROR|0106026495-001|-|20261112083015");
        assertThat(new NotificationKey(Type.MAILBOX_ERROR, null, null, "20261112083015.7").id())
                .isEqualTo("MAILBOX_ERROR|-|-|20261112083015.7");
    }

    @Test
    void parseRoundTripsEveryServerId() {
        for (String id : List.of(
                "NEW_INVOICES|1201496252|purchase|2026111208",
                "XML_READY|1201496252|sales|2026111123",
                "PDF_READY|0106026495-001|purchase|2026110100",
                "GDT_LOGIN_ERROR|1201496252|-|20261112083015",
                "MAILBOX_ERROR|1201496252|-|20261112083015.12",
                "MAILBOX_ERROR|-|-|20261112083015.3")) {
            assertThat(NotificationKey.parse(id)).as(id).hasValueSatisfying(k -> assertThat(k.id()).isEqualTo(id));
        }
        assertThat(NotificationKey.parse("MAILBOX_ERROR|-|-|20261112083015.3")).get()
                .satisfies(k -> assertThat(k.mst()).isNull());
    }

    @Test
    void parseRejectsAnythingElse() {
        for (String id : List.of(
                "*", "*|1201496252",                                  // markers are never accepted from a client
                "NEW_INVOICES|1201496252|both|2026111208",            // bad direction
                "NEW_INVOICES|-|purchase|2026111208",                 // counts need an MST
                "NEW_INVOICES|1201496252|purchase|20261112",          // bucket too short
                "XML_READY|12014962|sales|2026111208",                // malformed MST
                "GDT_LOGIN_ERROR|1201496252|purchase|20261112083015", // errors have no direction
                "GDT_LOGIN_ERROR|-|-|20261112083015",                 // GDT error needs an MST
                "MAILBOX_ERROR|1201496252|-|20261112083015",          // mailbox id missing
                "SOMETHING|1201496252|-|20261112083015",
                "NEW_INVOICES|1201496252|purchase|2026111208' OR 1=1",
                "")) {
            assertThat(NotificationKey.parse(id)).as(id).isEmpty();
        }
        assertThat(NotificationKey.parse(null)).isEmpty();
        assertThat(NotificationKey.parse("MAILBOX_ERROR|-|-|20261112083015." + "9".repeat(200))).isEmpty();
    }

    @Test
    void markersAndDirections() {
        assertThat(NotificationKey.marker(null)).isEqualTo("*");
        assertThat(NotificationKey.marker("1201496252")).isEqualTo("*|1201496252");
        assertThat(NotificationKey.wireDirection("mua")).isEqualTo("purchase");
        assertThat(NotificationKey.wireDirection("ban")).isEqualTo("sales");
        assertThat(NotificationKey.wireDirection(null)).isNull();
    }

    @Test
    void onlyTheTwoErrorTypesAreActionItems() {
        assertThat(Type.GDT_LOGIN_ERROR.action()).isTrue();
        assertThat(Type.MAILBOX_ERROR.action()).isTrue();
        assertThat(Type.NEW_INVOICES.action()).isFalse();
        assertThat(Type.XML_READY.action()).isFalse();
        assertThat(Type.PDF_READY.action()).isFalse();
    }
}
