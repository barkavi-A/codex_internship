package com.example.urlshortener.service;

import com.example.urlshortener.config.AppProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClickTrackingServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private HttpServletRequest request;

    private UserAgentParser userAgentParser;
    private AppProperties appProperties;
    private MeterRegistry meterRegistry;
    private ClickTrackingService clickTrackingService;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.getClickTracking().setQueueCapacity(5); // small capacity for saturation test
        appProperties.getClickTracking().setBatchSize(10);
        appProperties.getClickTracking().setRespectDnt(true);
        appProperties.setIpHashSalt("test-salt");

        userAgentParser = new UserAgentParser();
        meterRegistry = new SimpleMeterRegistry();

        clickTrackingService = new ClickTrackingService(appProperties, userAgentParser, jdbcTemplate, meterRegistry);
        clickTrackingService.init();
    }

    @Test
    @DisplayName("Extract referrer domain correctly")
    void testExtractReferrerDomain() {
        assertEquals("google.com", clickTrackingService.extractReferrerDomain("https://google.com/search?q=url"));
        assertEquals("github.com", clickTrackingService.extractReferrerDomain("http://github.com/profile"));
        assertEquals("direct", clickTrackingService.extractReferrerDomain(null));
        assertEquals("direct", clickTrackingService.extractReferrerDomain("   "));
    }

    @Test
    @DisplayName("Visitor hash should be SHA-256 and never reveal raw IP")
    void testVisitorHashAnonymization() {
        String hash1 = clickTrackingService.computeVisitorHash("192.168.1.50", "Mozilla/5.0");
        String hash2 = clickTrackingService.computeVisitorHash("192.168.1.50", "Mozilla/5.0");
        String hash3 = clickTrackingService.computeVisitorHash("10.0.0.1", "Mozilla/5.0");

        assertNotNull(hash1);
        assertEquals(64, hash1.length(), "SHA-256 hex string must be 64 characters long");
        assertEquals(hash1, hash2, "Identical IP + UA on same day must yield identical hash");
        assertNotEquals(hash1, hash3, "Different IPs must yield different hashes");
        assertFalse(hash1.contains("192.168.1.50"), "Raw IP must never be present in the visitor hash");
    }

    @Test
    @DisplayName("Respect DNT header skips recording click event")
    void testRespectDntHeader() {
        when(request.getHeader("DNT")).thenReturn("1");

        clickTrackingService.recordClickAsync(1L, request);

        assertEquals(0, clickTrackingService.getQueueSize(), "Queue must remain empty when DNT=1");
    }

    @Test
    @DisplayName("Queue full drops event safely without blocking or throwing exception")
    void testQueueFullDrop() {
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(request.getRemoteAddr()).thenReturn("1.2.3.4");

        // Fill capacity (5 items)
        for (int i = 0; i < 5; i++) {
            clickTrackingService.recordClickAsync((long) i, request);
        }
        assertEquals(5, clickTrackingService.getQueueSize());

        // 6th item should be dropped without exception
        assertDoesNotThrow(() -> clickTrackingService.recordClickAsync(999L, request));
        assertEquals(5, clickTrackingService.getQueueSize());
    }

    @Test
    @DisplayName("Batch flush executes JDBC batch update and clears queue")
    void testBatchFlush() {
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(request.getRemoteAddr()).thenReturn("1.2.3.4");

        clickTrackingService.recordClickAsync(1L, request);
        clickTrackingService.recordClickAsync(2L, request);
        assertEquals(2, clickTrackingService.getQueueSize());

        clickTrackingService.flushQueue();

        assertEquals(0, clickTrackingService.getQueueSize());
        verify(jdbcTemplate).batchUpdate(anyString(), anyList(), anyInt(), any());
    }

    @Test
    @DisplayName("Shutdown flush (@PreDestroy) drains remaining items")
    void testShutdownFlush() {
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(request.getRemoteAddr()).thenReturn("1.2.3.4");

        clickTrackingService.recordClickAsync(1L, request);
        clickTrackingService.shutdownFlush();

        assertEquals(0, clickTrackingService.getQueueSize());
    }
}
