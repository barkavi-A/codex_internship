package com.example.urlshortener.service;

import com.example.urlshortener.config.AppProperties;
import com.example.urlshortener.dto.projection.BreakdownProjection;
import com.example.urlshortener.dto.projection.LinkStatsProjection;
import com.example.urlshortener.dto.response.BreakdownResponse;
import com.example.urlshortener.dto.response.LinkAnalyticsResponse;
import com.example.urlshortener.dto.response.OverviewAnalyticsResponse;
import com.example.urlshortener.entity.ShortLink;
import com.example.urlshortener.exception.BadRequestException;
import com.example.urlshortener.repository.ClickEventRepository;
import com.example.urlshortener.repository.DailyLinkStatsRepository;
import com.example.urlshortener.repository.ShortLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private ShortLinkRepository shortLinkRepository;

    @Mock
    private ClickEventRepository clickEventRepository;

    @Mock
    private DailyLinkStatsRepository dailyLinkStatsRepository;

    @Mock
    private AppProperties appProperties;

    @InjectMocks
    private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        lenient().when(appProperties.getClickTracking()).thenReturn(new AppProperties.ClickTracking());
    }

    @Test
    @DisplayName("Overview analytics hand-calculated totals and metrics")
    void testOverviewAnalyticsTotals() {
        when(shortLinkRepository.countByUserId(1L)).thenReturn(10L);
        when(shortLinkRepository.countActiveByUserId(eq(1L), any())).thenReturn(8L);

        LinkStatsProjection mockStats = mock(LinkStatsProjection.class);
        when(mockStats.getTotalClicks()).thenReturn(500L);
        when(mockStats.getUniqueVisitors()).thenReturn(350L);
        when(clickEventRepository.getUserOverviewStats(eq(1L), any(), any())).thenReturn(mockStats);

        when(clickEventRepository.countUserClicksBetween(eq(1L), any(), any())).thenReturn(50L, 400L);

        OverviewAnalyticsResponse response = analyticsService.getOverview(1L, null, null);

        assertEquals(10L, response.totalLinks());
        assertEquals(8L, response.activeLinks());
        assertEquals(500L, response.totalClicks());
        assertEquals(350L, response.uniqueVisitors());
        assertEquals(50L, response.clicksToday());
    }

    @Test
    @DisplayName("Link analytics builds zero-filled time series buckets")
    void testZeroFilledTimeSeries() {
        ShortLink link = new ShortLink();
        link.setId(10L);
        link.setShortCode("testCode");

        when(shortLinkRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(link));

        LocalDateTime from = LocalDateTime.of(2026, 9, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 9, 5, 23, 59);

        // Click events on Sep 2 and Sep 4 only
        List<LocalDateTime> clickTimes = List.of(
                LocalDateTime.of(2026, 9, 2, 10, 0),
                LocalDateTime.of(2026, 9, 2, 14, 0),
                LocalDateTime.of(2026, 9, 4, 18, 0)
        );
        when(clickEventRepository.findClickTimesForLink(eq(10L), any(), any())).thenReturn(clickTimes);

        LinkAnalyticsResponse response = analyticsService.getLinkAnalytics(1L, 10L, from, to, "day");

        assertNotNull(response.timeSeries());
        assertEquals(5, response.timeSeries().size(), "Must contain 5 daily buckets for Sep 1-5");
        assertEquals(0, response.timeSeries().get(0).clicks()); // Sep 1 zero-filled
        assertEquals(2, response.timeSeries().get(1).clicks()); // Sep 2
        assertEquals(0, response.timeSeries().get(2).clicks()); // Sep 3 zero-filled
        assertEquals(1, response.timeSeries().get(3).clicks()); // Sep 4
        assertEquals(0, response.timeSeries().get(4).clicks()); // Sep 5 zero-filled
    }

    @Test
    @DisplayName("Breakdown response calculates items and correct percentages")
    void testBreakdownPercentages() {
        ShortLink link = new ShortLink();
        link.setId(10L);

        when(shortLinkRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(link));

        BreakdownProjection p1 = mock(BreakdownProjection.class);
        when(p1.getName()).thenReturn("Chrome");
        when(p1.getCount()).thenReturn(75L);

        BreakdownProjection p2 = mock(BreakdownProjection.class);
        when(p2.getName()).thenReturn("Firefox");
        when(p2.getCount()).thenReturn(25L);

        when(clickEventRepository.getBrowserBreakdown(eq(10L), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(p1, p2));

        BreakdownResponse response = analyticsService.getBreakdown(1L, 10L, "browser", null, null, 10);

        assertEquals("browser", response.dimension());
        assertEquals(100L, response.totalCount());
        assertEquals(2, response.items().size());
        assertEquals(75.0, response.items().get(0).percentage());
        assertEquals(25.0, response.items().get(1).percentage());
    }

    @Test
    @DisplayName("Invalid date range (from > to or > 365 days) throws BadRequestException")
    void testDateRangeValidation() {
        LocalDateTime now = LocalDateTime.now();
        assertThrows(BadRequestException.class, () ->
                analyticsService.getOverview(1L, now, now.minusDays(5)));

        assertThrows(BadRequestException.class, () ->
                analyticsService.getOverview(1L, now.minusDays(400), now));
    }
}
