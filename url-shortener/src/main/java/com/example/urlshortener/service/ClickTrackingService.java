package com.example.urlshortener.service;

import com.example.urlshortener.config.AppProperties;
import com.example.urlshortener.entity.DeviceType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

@Service
public class ClickTrackingService {

    private static final Logger logger = LoggerFactory.getLogger(ClickTrackingService.class);

    private final AppProperties appProperties;
    private final UserAgentParser userAgentParser;
    private final JdbcTemplate jdbcTemplate;
    private final MeterRegistry meterRegistry;

    private BlockingQueue<PendingClickEvent> eventQueue;
    private Counter recordedCounter;
    private Counter droppedCounter;

    public ClickTrackingService(AppProperties appProperties,
                                UserAgentParser userAgentParser,
                                JdbcTemplate jdbcTemplate,
                                MeterRegistry meterRegistry) {
        this.appProperties = appProperties;
        this.userAgentParser = userAgentParser;
        this.jdbcTemplate = jdbcTemplate;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void init() {
        int capacity = appProperties.getClickTracking().getQueueCapacity();
        this.eventQueue = new ArrayBlockingQueue<>(capacity);

        this.recordedCounter = Counter.builder("click_tracking_recorded_total")
                .description("Total click events recorded successfully")
                .register(meterRegistry);
        this.droppedCounter = Counter.builder("click_tracking_dropped_total")
                .description("Total click events dropped due to queue saturation")
                .register(meterRegistry);

        Gauge.builder("click_tracking_queue_size", eventQueue, BlockingQueue::size)
                .description("Current number of pending click events in queue")
                .register(meterRegistry);
    }

    public void recordClickAsync(Long linkId, HttpServletRequest request) {
        if (appProperties.getClickTracking().isRespectDnt()) {
            String dnt = request.getHeader("DNT");
            String secGpc = request.getHeader("Sec-GPC");
            if ("1".equals(dnt) || "1".equals(secGpc)) {
                logger.debug("Skipping click tracking event due to DNT/Sec-GPC header for linkId={}", linkId);
                return;
            }
        }

        String rawIp = extractClientIp(request);
        String userAgentStr = request.getHeader("User-Agent");
        String referrerStr = request.getHeader("Referer");
        String country = request.getHeader("CF-IPCountry");

        UserAgentParser.UserAgentInfo uaInfo = userAgentParser.parse(userAgentStr);
        String referrerDomain = extractReferrerDomain(referrerStr);
        String visitorHash = computeVisitorHash(rawIp, userAgentStr);
        LocalDateTime now = LocalDateTime.now();

        PendingClickEvent pendingEvent = new PendingClickEvent(
                linkId,
                now,
                referrerDomain,
                uaInfo.browser(),
                uaInfo.os(),
                uaInfo.deviceType(),
                uaInfo.isBot(),
                visitorHash,
                country
        );

        boolean offered = eventQueue.offer(pendingEvent);
        if (!offered) {
            droppedCounter.increment();
            logger.warn("Click tracking queue full. Dropping click event for linkId={}", linkId);
        }
    }

    @Scheduled(fixedDelayString = "${app.click-tracking.flush-interval-ms:2000}")
    public void flushBatchScheduled() {
        flushQueue();
    }

    @PreDestroy
    public void shutdownFlush() {
        logger.info("Application shutting down. Flushing remaining {} click events...", eventQueue.size());
        flushQueue();
    }

    public synchronized void flushQueue() {
        if (eventQueue.isEmpty()) {
            return;
        }

        List<PendingClickEvent> batch = new ArrayList<>();
        int batchSize = appProperties.getClickTracking().getBatchSize();
        eventQueue.drainTo(batch, batchSize > 0 ? batchSize : 100);

        if (batch.isEmpty()) {
            return;
        }

        String sql = "INSERT INTO click_events (link_id, clicked_at, referrer_domain, browser, os, device_type, is_bot, visitor_hash, country) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try {
            jdbcTemplate.batchUpdate(sql, batch, batch.size(), (ps, event) -> {
                ps.setLong(1, event.linkId());
                ps.setTimestamp(2, Timestamp.valueOf(event.clickedAt()));
                ps.setString(3, event.referrerDomain());
                ps.setString(4, event.browser());
                ps.setString(5, event.os());
                ps.setString(6, event.deviceType().name());
                ps.setBoolean(7, event.isBot());
                ps.setString(8, event.visitorHash());
                ps.setString(9, event.country());
            });
            recordedCounter.increment(batch.size());
            logger.debug("Successfully flushed batch of {} click events to database", batch.size());
        } catch (Exception e) {
            logger.error("Failed to insert batch of click events", e);
        }
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            String[] ips = xForwardedFor.split(",");
            return ips[0].trim();
        }
        return request.getRemoteAddr();
    }

    public String extractReferrerDomain(String referrerUrl) {
        if (referrerUrl == null || referrerUrl.trim().isEmpty()) {
            return "direct";
        }
        try {
            URI uri = new URI(referrerUrl.trim());
            String host = uri.getHost();
            if (host != null && !host.isEmpty()) {
                return host.toLowerCase();
            }
        } catch (Exception ignored) {}
        return "direct";
    }

    public String computeVisitorHash(String ip, String userAgent) {
        String safeIp = ip != null ? ip : "unknown_ip";
        String safeUa = userAgent != null ? userAgent : "unknown_ua";
        String salt = appProperties.getIpHashSalt();
        String date = LocalDate.now().toString();

        String raw = safeIp + ":" + safeUa + ":" + salt + ":" + date;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    public int getQueueSize() {
        return eventQueue.size();
    }

    public record PendingClickEvent(
            Long linkId,
            LocalDateTime clickedAt,
            String referrerDomain,
            String browser,
            String os,
            DeviceType deviceType,
            boolean isBot,
            String visitorHash,
            String country
    ) {}
}
