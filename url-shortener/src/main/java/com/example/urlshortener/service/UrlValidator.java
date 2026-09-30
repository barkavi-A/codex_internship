package com.example.urlshortener.service;

import com.example.urlshortener.config.AppProperties;
import com.example.urlshortener.exception.InvalidUrlException;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;

import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class UrlValidator {

    private static final int MAX_URL_LENGTH = 2048;
    private static final Pattern DECIMAL_IP_PATTERN = Pattern.compile("^\\d+$");
    private static final Pattern HEX_OCTAL_IP_PATTERN = Pattern.compile("^(0x[0-9a-fA-F]+|0[0-7]+)(\\.(0x[0-9a-fA-F]+|0[0-7]+))*$");

    private final AppProperties appProperties;

    public UrlValidator(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public String validateAndNormalize(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            throw new InvalidUrlException("URL must not be empty");
        }

        String trimmed = rawUrl.trim();
        if (trimmed.length() > MAX_URL_LENGTH) {
            throw new InvalidUrlException("URL exceeds maximum length of " + MAX_URL_LENGTH + " characters");
        }

        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (Exception e) {
            throw new InvalidUrlException("Malformed URL structure: " + e.getMessage());
        }

        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new InvalidUrlException("URL missing scheme (http or https required)");
        }
        scheme = scheme.toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new InvalidUrlException("Invalid URL scheme '" + scheme + "'. Only http and https are allowed.");
        }

        String host = uri.getHost();
        if (host == null || host.trim().isEmpty()) {
            throw new InvalidUrlException("URL missing valid host");
        }
        host = host.toLowerCase(Locale.ROOT);

        // Check for suspicious IP encodings (decimal, hex, octal, IPv4-mapped IPv6)
        validateHostForSsrf(host);

        // Check against own BASE_URL host
        String ownHost = getOwnHost();
        if (ownHost != null && ownHost.equalsIgnoreCase(host)) {
            throw new InvalidUrlException("Self-referencing URLs pointing to this shortener service are not allowed.");
        }

        // Check domain blocklist
        List<String> blocklist = appProperties.getUrl().getDomainBlocklist();
        if (blocklist != null) {
            for (String blocked : blocklist) {
                if (host.equals(blocked) || host.endsWith("." + blocked)) {
                    throw new InvalidUrlException("Domain '" + host + "' is blocked by security policy.");
                }
            }
        }

        // Build normalized URL string
        try {
            URI normalizedUri = new URI(
                    scheme,
                    uri.getUserInfo(),
                    host,
                    uri.getPort(),
                    uri.getPath(),
                    uri.getQuery(),
                    uri.getFragment()
            );
            return normalizedUri.toString();
        } catch (Exception e) {
            throw new InvalidUrlException("Failed to normalize URL: " + e.getMessage());
        }
    }

    private void validateHostForSsrf(String host) {
        if ("localhost".equals(host) || host.endsWith(".internal") || host.endsWith(".local")) {
            throw new InvalidUrlException("Access to local/internal host '" + host + "' is forbidden.");
        }

        // Clean IPv6 brackets if present
        String cleanHost = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;

        // Decimal IP representation check (e.g. 2130706433)
        if (DECIMAL_IP_PATTERN.matcher(cleanHost).matches()) {
            throw new InvalidUrlException("Numeric/decimal IP format '" + host + "' is forbidden.");
        }

        // Hex/Octal IP format check (e.g. 0x7f000001, 0177.0.0.1, 0x7f.0.0.1)
        if (cleanHost.startsWith("0x") || cleanHost.contains(".0x") || Pattern.compile("(^|\\.)0[0-7]+").matcher(cleanHost).find()) {
            throw new InvalidUrlException("Hexadecimal or octal IP format '" + host + "' is forbidden.");
        }

        // IPv4-mapped IPv6 check (e.g. ::ffff:127.0.0.1)
        if (cleanHost.toLowerCase(Locale.ROOT).startsWith("::ffff:") || cleanHost.toLowerCase(Locale.ROOT).startsWith("0:0:0:0:0:ffff:")) {
            throw new InvalidUrlException("IPv4-mapped IPv6 address '" + host + "' is forbidden.");
        }

        // Try IP parsing and check for loopback/private/link-local/multicast
        try {
            InetAddress address = InetAddress.getByName(cleanHost);
            if (address.isLoopbackAddress() ||
                address.isSiteLocalAddress() ||
                address.isLinkLocalAddress() ||
                address.isMulticastAddress() ||
                address.isAnyLocalAddress()) {
                throw new InvalidUrlException("Access to private/loopback/local IP address '" + host + "' is forbidden.");
            }
        } catch (InvalidUrlException iue) {
            throw iue;
        } catch (Exception ignored) {
            // Unresolvable domain or standard hostname - handled at connect time if needed
        }
    }

    private String getOwnHost() {
        try {
            String baseUrl = appProperties.getBaseUrl();
            if (baseUrl != null && !baseUrl.isEmpty()) {
                URI uri = new URI(baseUrl);
                return uri.getHost();
            }
        } catch (Exception ignored) {}
        return null;
    }
}
