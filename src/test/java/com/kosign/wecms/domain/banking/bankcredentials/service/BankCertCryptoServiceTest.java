package com.kosign.wecms.domain.banking.bankcredentials.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Golden-vector parity test for {@link BankCertCryptoService}.
 *
 * <p>The expected ciphertexts were produced by the LIVE JEX DB function
 * {@code public.encrypt(txt)} (read-only probe, 2026-06-16):
 * <pre>
 *   SELECT encrypt('hello');             -- aar2zbZgdHgLL3wET5lgSw==
 *   SELECT encrypt('P@ssw0rd-Test_123'); -- 5hW0ri+AYAFEbost3RQ+AJSRiWsvylsI0z8SObqzPh8=
 *   SELECT encrypt('webcash việt nam ✓');-- gcTCmXhciMX69rEXBrmWjS9d/ZArRO7yTbP1xWdZJNA=
 *   SELECT encrypt('');                  -- RqbX0tqtt/5zrEcF/FYCuw==
 *   SELECT encrypt('0123456789ABCDEF');  -- jRjUOCMpfA+XExsz31rCX3bJQAZ0UbcHqSwyo/vA9sg=
 * </pre>
 * Asserting byte-equality both ways proves:
 * <ul>
 *   <li>the refactor can READ every legacy {@code CERT_INFO_DETL_CON}
 *       ciphertext already in the JEX DB, and</li>
 *   <li>ciphertext the refactor WRITES is readable by the live JEX
 *       {@code decrypt()} (identical bytes) — true coexistence.</li>
 * </ul>
 */
class BankCertCryptoServiceTest {

    private BankCertCryptoService crypto;

    @BeforeEach
    void setUp() {
        crypto = new BankCertCryptoService("wabooksvn");
        crypto.init(); // same-package call (package-private @PostConstruct)
    }

    @Test
    @DisplayName("encrypt() is byte-identical to the legacy pgcrypto encrypt()")
    void encryptMatchesLegacy() {
        assertEquals("aar2zbZgdHgLL3wET5lgSw==", crypto.encrypt("hello"));
        assertEquals("5hW0ri+AYAFEbost3RQ+AJSRiWsvylsI0z8SObqzPh8=",
                crypto.encrypt("P@ssw0rd-Test_123"));
        assertEquals("gcTCmXhciMX69rEXBrmWjS9d/ZArRO7yTbP1xWdZJNA=",
                crypto.encrypt("webcash việt nam ✓"));
        assertEquals("RqbX0tqtt/5zrEcF/FYCuw==", crypto.encrypt(""));
        assertEquals("jRjUOCMpfA+XExsz31rCX3bJQAZ0UbcHqSwyo/vA9sg=",
                crypto.encrypt("0123456789ABCDEF"));
    }

    @Test
    @DisplayName("decrypt() reads legacy BANK_CERT_INFO / GDT_CERT_INFO ciphertext")
    void decryptReadsLegacyCiphertext() {
        assertEquals("hello", crypto.decrypt("aar2zbZgdHgLL3wET5lgSw=="));
        assertEquals("P@ssw0rd-Test_123",
                crypto.decrypt("5hW0ri+AYAFEbost3RQ+AJSRiWsvylsI0z8SObqzPh8="));
        assertEquals("webcash việt nam ✓",
                crypto.decrypt("gcTCmXhciMX69rEXBrmWjS9d/ZArRO7yTbP1xWdZJNA="));
        assertEquals("", crypto.decrypt("RqbX0tqtt/5zrEcF/FYCuw=="));
    }

    @Test
    @DisplayName("encrypt → decrypt round-trips arbitrary input")
    void roundTrips() {
        for (String s : new String[]{"Secret#9", "a", "longer pass phrase 123 !@#",
                "한국어비밀", "16bytes-exactly!"}) {
            assertEquals(s, crypto.decrypt(crypto.encrypt(s)));
        }
    }

    @Test
    @DisplayName("null in → null out (matches STRICT legacy SQL function)")
    void nullSafe() {
        assertNull(crypto.encrypt(null));
        assertNull(crypto.decrypt(null));
    }

    @Test
    @DisplayName("decrypt() echoes a non-base64 / plaintext value verbatim")
    void decryptTolerantOnPlaintext() {
        // CERT_INFO_DETL_CON2 / CON3 are stored plaintext; defensive
        // decrypt of them must not corrupt or throw.
        assertEquals("Corporate ID 12345", crypto.decrypt("Corporate ID 12345"));
    }
}
