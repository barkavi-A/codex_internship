package com.example.urlshortener.service;

import com.example.urlshortener.config.AppProperties;
import com.example.urlshortener.exception.InvalidUrlException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UrlValidatorTest {

    private UrlValidator validator;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties();
        appProperties.setBaseUrl("http://localhost:8080");
        appProperties.getUrl().setDomainBlocklist(List.of("malicious.com", "phishing.net"));
        validator = new UrlValidator(appProperties);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://example.com",
            "https://google.com/search?q=java",
            "https://sub.domain.org/path/to/resource#fragment",
            "HTTP://EXAMPLE.COM/UPPERCASE"
    })
    @DisplayName("Valid HTTP/HTTPS URLs should pass validation and normalize")
    void testValidUrls(String url) {
        String normalized = validator.validateAndNormalize(url);
        assertNotNull(normalized);
        assertTrue(normalized.startsWith("http://") || normalized.startsWith("https://"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)",
            "data:text/html,<script>alert(1)</script>",
            "file:///etc/passwd",
            "ftp://files.example.com",
            "gopher://gopher.example.com"
    })
    @DisplayName("Invalid schemes (javascript, data, file, ftp, gopher) must be rejected")
    void testInvalidSchemes(String url) {
        assertThrows(InvalidUrlException.class, () -> validator.validateAndNormalize(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost",
            "http://localhost:8080",
            "http://127.0.0.1",
            "http://127.0.0.1:3306",
            "http://10.0.0.1",
            "http://192.168.1.1",
            "http://172.16.0.1",
            "http://169.254.169.254",
            "http://[::1]",
            "http://server.internal",
            "http://service.local"
    })
    @DisplayName("SSRF protection: Localhost, private IPs, loopbacks, and internal hostnames must be rejected")
    void testSsrfProtectionPrivateIps(String url) {
        assertThrows(InvalidUrlException.class, () -> validator.validateAndNormalize(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://2130706433",        // Decimal IP for 127.0.0.1
            "http://0x7f000001",        // Hex IP for 127.0.0.1
            "http://0177.0.0.1",        // Octal IP for 127.0.0.1
            "http://[::ffff:127.0.0.1]" // IPv4-mapped IPv6
    })
    @DisplayName("SSRF protection: Encoded IPs (decimal, hex, octal, IPv4-mapped IPv6) must be rejected")
    void testSsrfProtectionEncodedIps(String url) {
        assertThrows(InvalidUrlException.class, () -> validator.validateAndNormalize(url));
    }

    @Test
    @DisplayName("Self-referencing URLs matching own BASE_URL host must be rejected")
    void testSelfRedirectProtection() {
        assertThrows(InvalidUrlException.class, () -> validator.validateAndNormalize("http://localhost:8080/somecode"));
    }

    @Test
    @DisplayName("Blocklisted domains must be rejected")
    void testBlocklistedDomains() {
        assertThrows(InvalidUrlException.class, () -> validator.validateAndNormalize("https://malicious.com/payload"));
        assertThrows(InvalidUrlException.class, () -> validator.validateAndNormalize("https://sub.phishing.net/login"));
    }

    @Test
    @DisplayName("URLs exceeding 2048 characters must be rejected")
    void testOverlengthUrl() {
        String longUrl = "https://example.com/" + "a".repeat(2050);
        assertThrows(InvalidUrlException.class, () -> validator.validateAndNormalize(longUrl));
    }
}
