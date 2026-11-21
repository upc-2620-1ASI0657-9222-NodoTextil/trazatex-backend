package com.nodotextil.trazatex.apigateway.infrastructure.ratelimit;

import org.springframework.http.server.reactive.ServerHttpRequest;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.regex.Pattern;

public class ClientIpResolver {

    private static final String FORWARDED_FOR = "X-Forwarded-For";
    private static final Pattern IPV4 = Pattern.compile("(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})");
    private static final Pattern IPV6_CHARS = Pattern.compile("[0-9a-fA-F:.]+");

    private final boolean trustForwardedFor;
    private final int trustedHops;

    public ClientIpResolver(boolean trustForwardedFor, int trustedHops) {
        this.trustForwardedFor = trustForwardedFor;
        this.trustedHops = trustedHops;
    }

    public String resolve(ServerHttpRequest request) {
        if (trustForwardedFor) {
            InetAddress forwarded = fromForwardedFor(request.getHeaders().get(FORWARDED_FOR));
            if (forwarded != null) {
                return normalize(forwarded);
            }
        }
        InetSocketAddress remote = request.getRemoteAddress();
        return remote == null || remote.getAddress() == null ? "desconocida" : normalize(remote.getAddress());
    }

    private InetAddress fromForwardedFor(List<String> headerLines) {
        if (headerLines == null || headerLines.isEmpty()) {
            return null;
        }
        String[] hops = String.join(",", headerLines).split(",");
        if (hops.length < trustedHops) {
            return null;
        }
        return parseLiteral(hops[hops.length - trustedHops].trim());
    }

    static InetAddress parseLiteral(String value) {
        try {
            var v4 = IPV4.matcher(value);
            if (v4.matches()) {
                byte[] bytes = new byte[4];
                for (int i = 0; i < 4; i++) {
                    int octet = Integer.parseInt(v4.group(i + 1));
                    if (octet > 255) {
                        return null;
                    }
                    bytes[i] = (byte) octet;
                }
                return InetAddress.getByAddress(bytes);
            }
            if (value.indexOf(':') >= 0 && IPV6_CHARS.matcher(value).matches()) {
                return InetAddress.getByName(value);
            }
        } catch (UnknownHostException | NumberFormatException e) {
            return null;
        }
        return null;
    }

    static String normalize(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            return address.getHostAddress();
        }
        return "%02x%02x:%02x%02x:%02x%02x:%02x%02x::/64".formatted(bytes[0], bytes[1], bytes[2], bytes[3], bytes[4], bytes[5], bytes[6], bytes[7]);
    }
}
