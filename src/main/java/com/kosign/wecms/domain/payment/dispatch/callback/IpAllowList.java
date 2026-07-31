package com.kosign.wecms.domain.payment.dispatch.callback;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A parsed, validated set of source-IP rules — literal addresses and CIDR blocks — used by
 * {@link CallbackIpWhitelistFilter} to decide whether an inbound VAN callback may proceed.
 *
 * <p><b>Validation is eager and strict.</b> {@link #parse(List)} throws on any malformed entry
 * rather than skipping it. A silently-dropped rule is the dangerous failure mode: an operator who
 * typo'd a gateway IP would otherwise get an allow-list that looks configured but rejects the real
 * gateway, or — before the fail-closed change — one that was empty and therefore allowed everyone.
 *
 * <p>Both IPv4 and IPv6 literals are accepted. CIDR is matched on the raw address bytes, so an
 * IPv4 rule never matches an IPv6 address or vice versa (different address lengths).
 *
 * <p>Hostnames are rejected: resolving them would make the allow-list depend on DNS at request
 * time, which is both a performance and a spoofing concern.
 */
public final class IpAllowList {

    private final List<Rule> rules;

    private IpAllowList(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    /** An empty list — matches nothing. Callers decide what "no rules" means for their contract. */
    public static IpAllowList empty() {
        return new IpAllowList(List.of());
    }

    /**
     * Parse and validate raw configuration entries.
     *
     * <p>Blank entries are skipped (a trailing comma in a comma-separated env var is a formatting
     * artefact, not an operator error). Everything else must be a valid literal IP or CIDR block.
     *
     * @throws IllegalArgumentException on a malformed address, a malformed CIDR, or an out-of-range
     *                                  prefix length — with the offending entry named
     */
    public static IpAllowList parse(List<String> raw) {
        List<Rule> parsed = new ArrayList<>();
        if (raw == null) {
            return empty();
        }
        for (String entry : raw) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            parsed.add(Rule.of(entry.trim()));
        }
        return new IpAllowList(parsed);
    }

    public boolean isEmpty() {
        return rules.isEmpty();
    }

    public int size() {
        return rules.size();
    }

    /** {@code true} when {@code ip} is a well-formed address matched by at least one rule. */
    public boolean matches(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        byte[] candidate = toBytes(ip.trim());
        if (candidate == null) {
            return false;
        }
        for (Rule rule : rules) {
            if (rule.matches(candidate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Literal address bytes for a textual IP, or {@code null} when it is not a valid literal.
     *
     * <p>Uses {@link InetAddress#getByName(String)} but rejects anything that is not already a
     * literal, so no DNS lookup can be triggered by request data.
     */
    static byte[] toBytes(String ip) {
        if (!isLiteral(ip)) {
            return null;
        }
        try {
            return InetAddress.getByName(ip).getAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }

    /** Cheap literal check — digits/dots for IPv4, hex/colons for IPv6. Keeps DNS out of the path. */
    private static boolean isLiteral(String ip) {
        boolean sawColon = ip.indexOf(':') >= 0;
        for (int i = 0; i < ip.length(); i++) {
            char c = ip.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || c == '.' || c == ':'
                    || (sawColon && ((c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')))
                    || (sawColon && c == '%');   // IPv6 zone id
            if (!ok) {
                return false;
            }
        }
        return !ip.isEmpty();
    }

    /** One allow-list rule: an address plus a prefix length (full length for a literal). */
    private record Rule(byte[] address, int prefixBits) {

        static Rule of(String entry) {
            int slash = entry.indexOf('/');
            if (slash < 0) {
                byte[] addr = toBytes(entry);
                if (addr == null) {
                    throw new IllegalArgumentException(
                            "Invalid callback allow-list entry (not a literal IP address): '" + entry + "'");
                }
                return new Rule(addr, addr.length * 8);
            }

            String addrPart = entry.substring(0, slash).trim();
            String bitsPart = entry.substring(slash + 1).trim();
            byte[] addr = toBytes(addrPart);
            if (addr == null) {
                throw new IllegalArgumentException(
                        "Invalid callback allow-list entry (CIDR network is not a literal IP): '" + entry + "'");
            }
            int bits;
            try {
                bits = Integer.parseInt(bitsPart);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Invalid callback allow-list entry (CIDR prefix is not a number): '" + entry + "'");
            }
            int maxBits = addr.length * 8;
            if (bits < 0 || bits > maxBits) {
                throw new IllegalArgumentException(
                        "Invalid callback allow-list entry (CIDR prefix must be 0.." + maxBits + "): '"
                                + entry + "'");
            }
            return new Rule(addr, bits);
        }

        boolean matches(byte[] candidate) {
            if (candidate.length != address.length) {
                return false;   // never cross-match IPv4 against IPv6
            }
            int fullBytes = prefixBits / 8;
            int remainingBits = prefixBits % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (candidate[i] != address[i]) {
                    return false;
                }
            }
            if (remainingBits == 0) {
                return true;
            }
            int mask = (0xFF << (8 - remainingBits)) & 0xFF;
            return (candidate[fullBytes] & mask) == (address[fullBytes] & mask);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Rule r && prefixBits == r.prefixBits && Arrays.equals(address, r.address);
        }

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(address), prefixBits);
        }
    }
}
