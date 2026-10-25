package com.ppln.automation.core.dns;

import com.ppln.automation.core.config.ConfigManager;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.CNAMERecord;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Record;
import org.xbill.DNS.Section;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

/**
 * Queries a specific nameserver directly (never the JVM's/host's system resolver, which {@link
 * java.net.InetAddress} cannot be pointed at a particular server). Built for ThreatIDR's RPZ
 * enforcement checks: Blocked Domains writes a {@code CNAME .} rule that must produce a real
 * NXDOMAIN from bind9, and that is the only trustworthy signal that the UI's "Active" status
 * reflects reality - see {@code core-layer}/{@code BlockedDomainsSteps}.
 *
 * <p>Uses a raw query + response ({@link SimpleResolver#send}) rather than dnsjava's higher-level
 * {@code Lookup} class, which follows CNAMEs/retries/caches internally and would obscure the raw
 * RCODE an {@code nslookup}/{@code dig} user actually sees.
 */
public final class DnsClient {

    /**
     * @param rcode the raw DNS response code (see {@link Rcode}); {@link Rcode#NXDOMAIN} is RPZ's
     *     block signal.
     */
    public record DnsResult(int rcode, List<String> addresses) {
        public boolean isNxDomain() {
            return rcode == Rcode.NXDOMAIN;
        }

        public boolean isSuccessWithAddresses() {
            return rcode == Rcode.NOERROR && !addresses.isEmpty();
        }
    }

    /**
     * Answer to a CNAME query.
     *
     * @param targets CNAME targets in the answer section, absolute ({@code "."}, {@code
     *     "rpz-passthru."}); empty when the name has no CNAME
     */
    public record CnameResult(int rcode, List<String> targets) {}

    private final String host;
    private final int port;
    private final Duration timeout;

    public DnsClient() {
        this(
                ConfigManager.optional("dns.host")
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "No dns.host configured - set it in"
                                                        + " config/<env>.local.properties to the"
                                                        + " nameserver under test (e.g. the bind9"
                                                        + " container's reachable address)")),
                Integer.parseInt(ConfigManager.optional("dns.port").orElse("53")),
                Duration.ofMillis(
                        Long.parseLong(ConfigManager.optional("dns.timeout.ms").orElse("5000"))));
    }

    public DnsClient(String host, int port, Duration timeout) {
        this.host = host;
        this.port = port;
        this.timeout = timeout;
    }

    /** Resolves an A record for {@code domain} directly against the configured nameserver. */
    public DnsResult resolveA(String domain) {
        Message response = send(domain, Type.A);
        List<String> addresses = new ArrayList<>();
        for (Record record : response.getSection(Section.ANSWER)) {
            if (record instanceof ARecord aRecord) {
                addresses.add(aRecord.getAddress().getHostAddress());
            }
        }
        return new DnsResult(response.getRcode(), addresses);
    }

    /**
     * Queries the CNAME of {@code name} directly. ThreatIDR's RPZ zones are ordinary zones on bind9
     * ({@code pplnbl.host.ppln}, {@code whitelisted.host.ppln}), so asking for {@code
     * <domain>.<zone>} returns the zone's own rule ({@code CNAME .} = block, {@code CNAME
     * rpz-passthru.} = allow) - unlike an A lookup of the domain itself, which cannot tell an RPZ
     * block from a name that simply does not exist. A literal {@code *} label reads the wildcard
     * rule.
     */
    public CnameResult queryCname(String name) {
        Message response = send(name, Type.CNAME);
        List<String> targets = new ArrayList<>();
        for (Record record : response.getSection(Section.ANSWER)) {
            if (record instanceof CNAMERecord cname) {
                targets.add(cname.getTarget().toString());
            }
        }
        return new CnameResult(response.getRcode(), targets);
    }

    /**
     * True when this nameserver answers the zone's SOA authoritatively or by forwarding, i.e. it is
     * (or forwards to) the server that hosts the zone. False on any error or empty answer.
     */
    public boolean querySoa(String zone) {
        try {
            Message response = send(zone, Type.SOA);
            return response.getRcode() == Rcode.NOERROR
                    && response.getSection(Section.ANSWER).stream()
                            .anyMatch(r -> r.getType() == Type.SOA);
        } catch (IllegalStateException unreachable) {
            return false;
        }
    }

    /**
     * One raw query and its raw response; dnsjava's higher-level {@code Lookup} would follow CNAMEs
     * and cache, hiding the rcode {@code dig} shows.
     */
    private Message send(String domain, int type) {
        try {
            Name name = Name.fromString(domain, Name.root);
            Message query = Message.newQuery(Record.newRecord(name, type, DClass.IN));
            SimpleResolver resolver = new SimpleResolver(host);
            resolver.setPort(port);
            resolver.setTimeout(timeout);
            return resolver.send(query);
        } catch (TextParseException e) {
            throw new IllegalArgumentException("Not a valid domain name: " + domain, e);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "DNS query to %s:%d for %s failed".formatted(host, port, domain), e);
        }
    }
}
