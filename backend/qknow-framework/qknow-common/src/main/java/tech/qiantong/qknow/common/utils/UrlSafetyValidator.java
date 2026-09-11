package tech.qiantong.qknow.common.utils;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

/**
 * Phase 03: SSRF guard for outbound HTTP from agent tools.
 * Deny-list per OWASP SSRF Prevention Cheat Sheet (loopback / RFC1918 / link-local / metadata / multicast).
 */
public final class UrlSafetyValidator {

    private UrlSafetyValidator() {
    }

    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "localhost",
            "metadata.google.internal",
            "metadata.goog",
            "instance-data");

    public static void validateOrThrow(String rawUrl) {
        String reason = check(rawUrl);
        if (reason != null) {
            throw new IllegalArgumentException("URL rejected: " + reason);
        }
    }

    /** @return null if allowed; otherwise rejection reason. */
    public static String check(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return "blank url";
        }
        String trimmed = rawUrl.trim();
        if (trimmed.contains("\n") || trimmed.contains("\r") || trimmed.contains(" ")) {
            return "illegal characters";
        }
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (Exception e) {
            return "unparsable uri";
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            return "scheme not allowed: " + scheme;
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return "missing host";
        }
        String hostLower = host.toLowerCase(Locale.ROOT);
        if (BLOCKED_HOSTS.contains(hostLower) || hostLower.endsWith(".localhost") || hostLower.endsWith(".internal")) {
            return "blocked host: " + host;
        }
        if (hostLower.equals("0.0.0.0") || hostLower.equals("[::]")) {
            return "wildcard host";
        }
        // Strip IPv6 brackets for InetAddress
        String hostForResolve = hostLower;
        if (hostForResolve.startsWith("[") && hostForResolve.endsWith("]")) {
            hostForResolve = hostForResolve.substring(1, hostForResolve.length() - 1);
        }
        InetAddress addr;
        try {
            addr = InetAddress.getByName(hostForResolve);
        } catch (UnknownHostException e) {
            return "unresolvable host";
        }
        if (addr.isAnyLocalAddress() || addr.isLoopbackAddress() || addr.isLinkLocalAddress()
                || addr.isSiteLocalAddress() || addr.isMulticastAddress()) {
            return "private or local address: " + addr.getHostAddress();
        }
        // AWS/GCP/Azure metadata often 169.254.169.254 (link-local) already covered; extra explicit check
        if (addr.getHostAddress().startsWith("169.254.")) {
            return "link-local metadata range";
        }
        return null;
    }
}
