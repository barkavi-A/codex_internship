package com.example.urlshortener.service;

import com.example.urlshortener.config.CacheConfig;
import com.example.urlshortener.entity.ShortLink;
import com.example.urlshortener.exception.LinkGoneException;
import com.example.urlshortener.exception.ResourceNotFoundException;
import com.example.urlshortener.repository.ShortLinkRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedirectServiceTest {

    @Mock
    private ShortLinkRepository shortLinkRepository;

    @Mock
    private ClickTrackingService clickTrackingService;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache redirectCache;

    @Mock
    private Cache negativeCache;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private RedirectService redirectService;

    private ShortLink activeLink;

    @BeforeEach
    void setUp() {
        activeLink = new ShortLink();
        activeLink.setId(100L);
        activeLink.setShortCode("validCode");
        activeLink.setOriginalUrl("https://destination.com");
        activeLink.setActive(true);

        lenient().when(cacheManager.getCache(CacheConfig.REDIRECT_CACHE)).thenReturn(redirectCache);
        lenient().when(cacheManager.getCache(CacheConfig.NEGATIVE_CACHE)).thenReturn(negativeCache);
    }

    @Test
    @DisplayName("Resolve active short code returns original URL and queues click async")
    void testResolveActiveLinkSuccess() {
        when(shortLinkRepository.findByShortCode("validCode")).thenReturn(Optional.of(activeLink));
        when(shortLinkRepository.incrementClickCountIfUnderLimit(100L)).thenReturn(1);

        String url = redirectService.resolveAndTrackRedirect("validCode", request);

        assertEquals("https://destination.com", url);
        verify(clickTrackingService).recordClickAsync(100L, request);
        verify(redirectCache).put("validCode", "https://destination.com");
    }

    @Test
    @DisplayName("Unknown short code throws ResourceNotFoundException (404) and caches negative lookup")
    void testUnknownCodeNotFound() {
        when(shortLinkRepository.findByShortCode("unknownCode")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> redirectService.resolveAndTrackRedirect("unknownCode", request));
        verify(negativeCache).put("unknownCode", Boolean.TRUE);
    }

    @Test
    @DisplayName("Inactive short link throws LinkGoneException (410)")
    void testInactiveLinkGone() {
        activeLink.setActive(false);
        when(shortLinkRepository.findByShortCode("validCode")).thenReturn(Optional.of(activeLink));

        assertThrows(LinkGoneException.class, () -> redirectService.resolveAndTrackRedirect("validCode", request));
    }

    @Test
    @DisplayName("Expired short link throws LinkGoneException (410)")
    void testExpiredLinkGone() {
        activeLink.setExpiresAt(LocalDateTime.now().minusHours(1));
        when(shortLinkRepository.findByShortCode("validCode")).thenReturn(Optional.of(activeLink));

        assertThrows(LinkGoneException.class, () -> redirectService.resolveAndTrackRedirect("validCode", request));
    }

    @Test
    @DisplayName("Max clicks limit enforced atomically under concurrent requests")
    void testConcurrentMaxClicksEnforcement() throws InterruptedException {
        activeLink.setMaxClicks(5L);
        activeLink.setClickCount(0L);

        when(shortLinkRepository.findByShortCode("validCode")).thenReturn(Optional.of(activeLink));

        AtomicInteger currentCount = new AtomicInteger(0);
        when(shortLinkRepository.incrementClickCountIfUnderLimit(100L)).thenAnswer(inv -> {
            int current = currentCount.get();
            if (current < 5) {
                currentCount.incrementAndGet();
                return 1;
            } else {
                return 0;
            }
        });

        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger goneCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    redirectService.resolveAndTrackRedirect("validCode", request);
                    successCount.incrementAndGet();
                } catch (LinkGoneException e) {
                    goneCount.incrementAndGet();
                } catch (Exception e) {
                    fail("Unexpected exception: " + e.getMessage());
                }
            });
        }

        latch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS));

        assertEquals(5, successCount.get(), "Exactly 5 requests should succeed up to maxClicks limit");
        assertEquals(5, goneCount.get(), "Remaining 5 requests must receive LinkGoneException 410");
    }
}
