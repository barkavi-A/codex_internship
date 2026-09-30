package com.example.urlshortener.service;

import com.example.urlshortener.config.AppProperties;
import com.example.urlshortener.dto.projection.BreakdownProjection;
import com.example.urlshortener.dto.projection.LinkStatsProjection;
import com.example.urlshortener.dto.projection.TopLinkProjection;
import com.example.urlshortener.dto.response.*;
import com.example.urlshortener.entity.DailyLinkStats;
import com.example.urlshortener.entity.ShortLink;
import com.example.urlshortener.exception.BadRequestException;
import com.example.urlshortener.exception.ResourceNotFoundException;
import com.example.urlshortener.repository.ClickEventRepository;
import com.example.urlshortener.repository.DailyLinkStatsRepository;
import com.example.urlshortener.repository.ShortLinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticsService.class);

    private final ShortLinkRepository shortLinkRepository;
    private final ClickEventRepository clickEventRepository;
    private final DailyLinkStatsRepository dailyLinkStatsRepository;
    private final AppProperties appProperties;

    public AnalyticsService(ShortLinkRepository shortLinkRepository,
                             ClickEventRepository clickEventRepository,
                             DailyLinkStatsRepository dailyLinkStatsRepository,
                             AppProperties appProperties) {
        this.shortLinkRepository = shortLinkRepository;
        this.clickEventRepository = clickEventRepository;
        this.dailyLinkStatsRepository = dailyLinkStatsRepository;
        this.appProperties = appProperties;
    }

    /**
     * Gets user overview analytics including metrics, top links, and trend vs previous period.
     */
    @Transactional(readOnly = true)
    public OverviewAnalyticsResponse getOverview(Long userId, LocalDateTime from, LocalDateTime to) {
        LocalDateTime[] dates = validateAndNormalizeDates(from, to);
        LocalDateTime currentFrom = dates[0];
        LocalDateTime currentTo = dates[1];

        long totalLinks = shortLinkRepository.countByUserId(userId);
        long activeLinks = shortLinkRepository.countActiveByUserId(userId, LocalDateTime.now());

        LinkStatsProjection stats = clickEventRepository.getUserOverviewStats(userId, currentFrom, currentTo);
        long totalClicks = stats != null && stats.getTotalClicks() != null ? stats.getTotalClicks() : 0;
        long uniqueVisitors = stats != null && stats.getUniqueVisitors() != null ? stats.getUniqueVisitors() : 0;

        // Clicks today
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        LocalDateTime endOfToday = LocalDate.now().atTime(LocalTime.MAX);
        long clicksToday = clickEventRepository.countUserClicksBetween(userId, startOfToday, endOfToday);

        // Previous period click trend calculation
        Duration duration = Duration.between(currentFrom, currentTo);
        LocalDateTime previousFrom = currentFrom.minus(duration);
        LocalDateTime previousTo = currentFrom;
        long previousClicks = clickEventRepository.countUserClicksBetween(userId, previousFrom, previousTo);

        double clickChangePercent = 0.0;
        if (previousClicks > 0) {
            clickChangePercent = ((double) (totalClicks - previousClicks) / previousClicks) * 100.0;
        } else if (totalClicks > 0) {
            clickChangePercent = 100.0;
        }

        // Top 5 links
        List<TopLinkProjection> topLinkProjections = clickEventRepository.getTopLinksByUser(userId, currentFrom, currentTo, PageRequest.of(0, 5));
        List<OverviewAnalyticsResponse.TopLinkItem> topLinks = topLinkProjections.stream()
                .map(p -> new OverviewAnalyticsResponse.TopLinkItem(
                        p.getLinkId(),
                        p.getShortCode(),
                        p.getTitle(),
                        p.getOriginalUrl(),
                        p.getClicks() != null ? p.getClicks() : 0
                ))
                .collect(Collectors.toList());

        return new OverviewAnalyticsResponse(
                totalLinks,
                activeLinks,
                totalClicks,
                uniqueVisitors,
                clicksToday,
                Math.round(clickChangePercent * 100.0) / 100.0,
                topLinks
        );
    }

    /**
     * Gets link analytics time-series for a specific short link owned by the user.
     */
    @Transactional(readOnly = true)
    public LinkAnalyticsResponse getLinkAnalytics(Long userId, Long linkId, LocalDateTime from, LocalDateTime to, String granularity) {
        ShortLink link = shortLinkRepository.findByIdAndUserId(linkId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Link not found with ID: " + linkId));

        LocalDateTime[] dates = validateAndNormalizeDates(from, to);
        LocalDateTime currentFrom = dates[0];
        LocalDateTime currentTo = dates[1];
        String gran = (granularity != null && granularity.equalsIgnoreCase("hour")) ? "hour" : "day";

        LinkStatsProjection stats = clickEventRepository.getLinkStats(link.getId(), currentFrom, currentTo);
        long totalClicks = stats != null && stats.getTotalClicks() != null ? stats.getTotalClicks() : 0;
        long uniqueVisitors = stats != null && stats.getUniqueVisitors() != null ? stats.getUniqueVisitors() : 0;
        long botClicks = stats != null && stats.getBotClicks() != null ? stats.getBotClicks() : 0;

        List<LocalDateTime> clickTimes = clickEventRepository.findClickTimesForLink(link.getId(), currentFrom, currentTo);
        List<TimeSeriesPoint> timeSeries = buildZeroFilledTimeSeries(currentFrom, currentTo, gran, clickTimes);

        return new LinkAnalyticsResponse(
                link.getId(),
                link.getShortCode(),
                totalClicks,
                uniqueVisitors,
                botClicks,
                gran,
                timeSeries
        );
    }

    /**
     * Gets breakdown distribution by dimension (referrer, browser, os, device, country).
     */
    @Transactional(readOnly = true)
    public BreakdownResponse getBreakdown(Long userId, Long linkId, String dimension, LocalDateTime from, LocalDateTime to, Integer limit) {
        ShortLink link = shortLinkRepository.findByIdAndUserId(linkId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Link not found with ID: " + linkId));

        LocalDateTime[] dates = validateAndNormalizeDates(from, to);
        int maxItems = (limit != null && limit > 0) ? Math.min(limit, 50) : 10;
        String dim = (dimension != null) ? dimension.toLowerCase().trim() : "referrer";

        PageRequest pageable = PageRequest.of(0, maxItems);
        List<BreakdownProjection> projections;

        switch (dim) {
            case "browser" -> projections = clickEventRepository.getBrowserBreakdown(link.getId(), dates[0], dates[1], pageable);
            case "os" -> projections = clickEventRepository.getOsBreakdown(link.getId(), dates[0], dates[1], pageable);
            case "device" -> projections = clickEventRepository.getDeviceBreakdown(link.getId(), dates[0], dates[1], pageable);
            case "country" -> projections = clickEventRepository.getCountryBreakdown(link.getId(), dates[0], dates[1], pageable);
            default -> {
                dim = "referrer";
                projections = clickEventRepository.getReferrerBreakdown(link.getId(), dates[0], dates[1], pageable);
            }
        }

        long totalCount = projections.stream().mapToLong(p -> p.getCount() != null ? p.getCount() : 0).sum();
        List<BreakdownItem> items = projections.stream()
                .map(p -> {
                    long cnt = p.getCount() != null ? p.getCount() : 0;
                    double pct = totalCount > 0 ? (cnt * 100.0 / totalCount) : 0.0;
                    String name = p.getName() != null && !p.getName().isBlank() ? p.getName() : "Unknown";
                    return new BreakdownItem(name, cnt, Math.round(pct * 100.0) / 100.0);
                })
                .collect(Collectors.toList());

        return new BreakdownResponse(dim, totalCount, items);
    }

    /**
     * Gets top performing links for user.
     */
    @Transactional(readOnly = true)
    public List<OverviewAnalyticsResponse.TopLinkItem> getTopLinks(Long userId, LocalDateTime from, LocalDateTime to, Integer limit) {
        LocalDateTime[] dates = validateAndNormalizeDates(from, to);
        int maxItems = (limit != null && limit > 0) ? Math.min(limit, 50) : 10;

        List<TopLinkProjection> projections = clickEventRepository.getTopLinksByUser(userId, dates[0], dates[1], PageRequest.of(0, maxItems));
        return projections.stream()
                .map(p -> new OverviewAnalyticsResponse.TopLinkItem(
                        p.getLinkId(),
                        p.getShortCode(),
                        p.getTitle(),
                        p.getOriginalUrl(),
                        p.getClicks() != null ? p.getClicks() : 0
                ))
                .collect(Collectors.toList());
    }

    /**
     * Generates CSV export of daily stats for a specific link, escaping formula characters for CSV safety.
     */
    @Transactional(readOnly = true)
    public byte[] exportCsv(Long userId, Long linkId) {
        ShortLink link = shortLinkRepository.findByIdAndUserId(linkId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Link not found with ID: " + linkId));

        LocalDateTime from = LocalDateTime.now().minusDays(365);
        LocalDateTime to = LocalDateTime.now();
        List<LocalDateTime> clickTimes = clickEventRepository.findClickTimesForLink(link.getId(), from, to);
        List<TimeSeriesPoint> dailyPoints = buildZeroFilledTimeSeries(from, to, "day", clickTimes);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(out, true, StandardCharsets.UTF_8)) {
            writer.println("Date,Clicks,Unique Visitors");
            for (TimeSeriesPoint point : dailyPoints) {
                writer.printf("%s,%d,%d%n", sanitizeCsvField(point.bucketKey()), point.clicks(), point.uniqueVisitors());
            }
        }

        return out.toByteArray();
    }

    /**
     * Scheduled job to perform daily rollup of click events older than retention cutoff into daily_link_stats table.
     */
    @Scheduled(cron = "0 0 2 * * ?") // 2 AM daily
    @Transactional
    public void runDailyRollupAndRetentionJob() {
        int retentionDays = appProperties.getClickTracking().getRawRetentionDays();
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);

        logger.info("Executing daily click event rollup job for records older than {}", cutoff);
        List<Object[]> aggregatedResults = clickEventRepository.aggregateClickEventsBeforeDate(cutoff);

        for (Object[] row : aggregatedResults) {
            Long linkId = ((Number) row[0]).longValue();
            java.sql.Date sqlDate = (java.sql.Date) row[1];
            LocalDate statDate = sqlDate.toLocalDate();
            long clicks = ((Number) row[2]).longValue();
            long uniqueVisitors = ((Number) row[3]).longValue();
            long botClicks = ((Number) row[4]).longValue();

            Optional<ShortLink> linkOpt = shortLinkRepository.findById(linkId);
            if (linkOpt.isPresent()) {
                DailyLinkStats stats = dailyLinkStatsRepository.findByLinkIdAndStatDate(linkId, statDate)
                        .orElseGet(() -> new DailyLinkStats(linkOpt.get(), statDate, 0, 0, 0));
                stats.setClicks(stats.getClicks() + clicks);
                stats.setUniqueVisitors(stats.getUniqueVisitors() + uniqueVisitors);
                stats.setBotClicks(stats.getBotClicks() + botClicks);
                dailyLinkStatsRepository.save(stats);
            }
        }

        int deletedCount = clickEventRepository.deleteByClickedAtBefore(cutoff);
        logger.info("Daily rollup job finished. Saved {} aggregated daily stats records, deleted {} raw click events.", aggregatedResults.size(), deletedCount);
    }

    private String sanitizeCsvField(String field) {
        if (field == null) return "";
        String trimmed = field.trim();
        if (trimmed.startsWith("=") || trimmed.startsWith("+") || trimmed.startsWith("-") || trimmed.startsWith("@")) {
            return "'" + trimmed;
        }
        return trimmed;
    }

    private LocalDateTime[] validateAndNormalizeDates(LocalDateTime from, LocalDateTime to) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime end = (to != null) ? to : now;
        LocalDateTime start = (from != null) ? from : end.minusDays(30);

        if (start.isAfter(end)) {
            throw new BadRequestException("Start date ('from') cannot be after end date ('to')");
        }
        if (Duration.between(start, end).toDays() > 366) {
            throw new BadRequestException("Date range cannot exceed 1 year (365 days)");
        }

        return new LocalDateTime[]{start, end};
    }

    private List<TimeSeriesPoint> buildZeroFilledTimeSeries(LocalDateTime from, LocalDateTime to, String granularity, List<LocalDateTime> clickTimes) {
        Map<String, Long> countMap = new HashMap<>();
        DateTimeFormatter formatter = "hour".equals(granularity) ?
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00") :
                DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (LocalDateTime dt : clickTimes) {
            String key = dt.format(formatter);
            countMap.put(key, countMap.getOrDefault(key, 0L) + 1);
        }

        List<TimeSeriesPoint> result = new ArrayList<>();
        LocalDateTime current = from;

        if ("hour".equals(granularity)) {
            while (!current.isAfter(to)) {
                String key = current.format(formatter);
                long clicks = countMap.getOrDefault(key, 0L);
                result.add(new TimeSeriesPoint(key, clicks, Math.min(clicks, 1)));
                current = current.plusHours(1);
            }
        } else {
            LocalDate startDate = from.toLocalDate();
            LocalDate endDate = to.toLocalDate();
            LocalDate currDate = startDate;
            while (!currDate.isAfter(endDate)) {
                String key = currDate.format(formatter);
                long clicks = countMap.getOrDefault(key, 0L);
                result.add(new TimeSeriesPoint(key, clicks, Math.min(clicks, 1)));
                currDate = currDate.plusDays(1);
            }
        }

        return result;
    }
}
