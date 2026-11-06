package com.kosign.wecms.domain.weinvoice.service;

import com.kosign.wecms.common.exception.BusinessException;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Reads the GDT key of an e-invoice XML (QĐ 1233): template ({@code TTChung/KHMSHDon}), symbol
 * ({@code KHHDon}), number ({@code SHDon}), seller and buyer MST ({@code NBan/MST},
 * {@code NMua/MST}). Only what the "XML key must match the open invoice" rule (UI Spec 3.5)
 * needs; the full parse stays in the engine.
 *
 * <p>DTDs and external entities are disabled — the file comes from a user.
 */
final class XmlKeyReader {

    private XmlKeyReader() {
    }

    static XmlKey read(byte[] xml) {
        XMLInputFactory f = XMLInputFactory.newFactory();
        f.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        f.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        String template = null;
        String symbol = null;
        String number = null;
        String seller = null;
        String buyer = null;
        Deque<String> path = new ArrayDeque<>();
        XMLStreamReader r = null;
        try {
            r = f.createXMLStreamReader(new ByteArrayInputStream(xml));
            while (r.hasNext()) {
                int ev = r.next();
                if (ev == XMLStreamConstants.START_ELEMENT) {
                    String name = r.getLocalName();
                    String parent = path.peek();
                    path.push(name);
                    if ("TTChung".equals(parent)) {
                        switch (name) {
                            case "KHMSHDon" -> template = r.getElementText().trim();
                            case "KHHDon" -> symbol = r.getElementText().trim();
                            case "SHDon" -> number = r.getElementText().trim();
                            default -> {
                                continue;
                            }
                        }
                        path.pop();   // getElementText consumed the END_ELEMENT
                    } else if ("MST".equals(name) && ("NBan".equals(parent) || "NMua".equals(parent))) {
                        String v = r.getElementText().trim();
                        if ("NBan".equals(parent) && seller == null) seller = v;
                        if ("NMua".equals(parent) && buyer == null) buyer = v;
                        path.pop();
                    }
                } else if (ev == XMLStreamConstants.END_ELEMENT) {
                    path.poll();
                }
            }
        } catch (XMLStreamException | RuntimeException e) {
            throw new BusinessException("WBE3009");
        } finally {
            if (r != null) {
                try {
                    r.close();
                } catch (XMLStreamException ignored) {
                    // nothing to release
                }
            }
        }
        if (symbol == null || number == null || seller == null) {
            throw new BusinessException("WBE3009");
        }
        long no;
        try {
            no = Long.parseLong(number.replaceFirst("^0+(?=\\d)", ""));
        } catch (NumberFormatException e) {
            throw new BusinessException("WBE3009");
        }
        Integer tpl = null;
        if (template != null && template.matches("\\d{1,2}")) tpl = Integer.parseInt(template);
        return new XmlKey(tpl, symbol, no, seller, buyer);
    }

    /** {@code templateNo} is {@code null} when the XML omits it. */
    record XmlKey(Integer templateNo, String symbol, long number, String sellerTaxId, String buyerTaxId) {

        boolean matches(int templateNo, String symbol, long number, String sellerTaxId) {
            return (this.templateNo == null || this.templateNo == templateNo)
                    && this.symbol.equalsIgnoreCase(symbol)
                    && this.number == number
                    && this.sellerTaxId.equals(sellerTaxId);
        }
    }
}
