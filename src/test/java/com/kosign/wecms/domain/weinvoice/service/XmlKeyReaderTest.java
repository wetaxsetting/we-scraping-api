package com.kosign.wecms.domain.weinvoice.service;

import com.kosign.wecms.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** GDT key of an uploaded XML (QĐ 1233 layout), hostile input refused. */
class XmlKeyReaderTest {

    static final String XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <HDon><DLHDon Id="abc">
              <TTChung><PBan>2.0.1</PBan><THDon>Hóa đơn GTGT</THDon><KHMSHDon>1</KHMSHDon><KHHDon>C26TAA</KHHDon>
                <SHDon>0000123</SHDon><NLap>2026-10-01</NLap></TTChung>
              <NDHDon>
                <NBan><Ten>Bên bán</Ten><MST>0101243150</MST></NBan>
                <NMua><Ten>Bên mua</Ten><MST>1201496252</MST></NMua>
                <DSHHDVu><HHDVu><THHDVu>Item</THHDVu></HHDVu></DSHHDVu>
              </NDHDon>
            </DLHDon><MCCQT>00ABCDEF</MCCQT></HDon>
            """;

    @Test
    void readsKeyAndStripsLeadingZeros() {
        XmlKeyReader.XmlKey k = XmlKeyReader.read(XML.getBytes(StandardCharsets.UTF_8));
        assertThat(k.templateNo()).isEqualTo(1);
        assertThat(k.symbol()).isEqualTo("C26TAA");
        assertThat(k.number()).isEqualTo(123L);
        assertThat(k.sellerTaxId()).isEqualTo("0101243150");
        assertThat(k.buyerTaxId()).isEqualTo("1201496252");
        assertThat(k.matches(1, "c26taa", 123L, "0101243150")).isTrue();
        assertThat(k.matches(1, "C26TAA", 124L, "0101243150")).isFalse();
        assertThat(k.matches(1, "C26TAA", 123L, "0101243151")).isFalse();
    }

    @Test
    void externalEntityIsRefused() {
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE HDon [<!ENTITY e SYSTEM \"file:///etc/passwd\">]>"
                + "<HDon><DLHDon><TTChung><KHHDon>&e;</KHHDon><SHDon>1</SHDon></TTChung>"
                + "<NDHDon><NBan><MST>0101243150</MST></NBan></NDHDon></DLHDon></HDon>";
        assertThatThrownBy(() -> XmlKeyReader.read(xxe.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(BusinessException.class).hasMessage("WBE3009");
    }

    @Test
    void notAnInvoiceIsWbe3009() {
        assertThatThrownBy(() -> XmlKeyReader.read("<root><a>1</a></root>".getBytes(StandardCharsets.UTF_8)))
                .hasMessage("WBE3009");
        assertThatThrownBy(() -> XmlKeyReader.read("not xml".getBytes(StandardCharsets.UTF_8)))
                .hasMessage("WBE3009");
    }
}
