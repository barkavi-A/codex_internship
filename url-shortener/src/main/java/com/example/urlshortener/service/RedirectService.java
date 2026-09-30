package com.example.urlshortener.service;

import com.example.urlshortener.config.CacheConfig;
import com.example.urlshortener.entity.ShortLink;
import com.example.urlshortener.exception.LinkGoneException;
import com.example.urlshortener.exception.ResourceNotFoundException;
import com.example.urlshortener.repository.ShortLinkRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class RedirectService {

    private static final Logger logger = LoggerFactory.getLogger(RedirectService.class);

    private final ShortLinkRepository shortLinkRepository;
    private final ClickTrackingService clickTrackingService;
    private final CacheManager cacheManager;

    public RedirectService(ShortLinkRepository shortLinkRepository,
                           ClickTrackingService clickTrackingService,
                           CacheManager cacheManager) {
        this.shortLinkRepository = shortLinkRepository;
        this.clickTrackingService = clickTrackingService;
        this.cacheManager = cacheManager;
    }

    /**
     * Resolves short code redirect URL, enforces limits atomically, and queues async click tracking.
     */
    @Transactional
    public String resolveAndTrackRedirect(String shortCode, HttpServletRequest request) {
        Cache redirectCache = cacheManager.getCache(CacheConfig.REDIRECT_CACHE);
        Cache negativeCache = cacheManager.getCache(CacheConfig.NEGATIVE_CACHE);

        // 1. Check negative cache first to block scans of unknown shortcodes
        if (negativeCache != null && negativeCache.get(shortCode) != null) {
            throw new ResourceNotFoundException("Short code '" + shortCode + "' not found.");
        }

        // 2. Fetch ShortLink entity
        ShortLink link = shortLinkRepository.findByShortCode(shortCode)
                .orElse(null);

        if (link == null) {
            if (negativeCache != null) {
                negativeCache.put(shortCode, Boolean.TRUE);
            }
            throw new ResourceNotFoundException("Short code '" + shortCode + "' not found.");
        }

        // 3. Validate Active Status
        if (!link.isActive()) {
            throw new LinkGoneException("This short link is currently inactive.");
        }

        // 4. Validate Expiry Date
        if (link.getExpiresAt() != null && link.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new LinkGoneException("This short link expired on " + link.getExpiresAt());
        }

        // 5. Enforce Max Clicks Limit atomically
        if (link.getMaxClicks() != null) {
            if (link.getClickCount() >= link.getMaxClicks()) {
                throw new LinkGoneException("This short link has reached its maximum allowed click limit (" + link.getMaxClicks() + ").");
            }
            int updatedRows = shortLinkRepository.incrementClickCountIfUnderLimit(link.getId());
            if (updatedRows == 0) {
                throw new LinkGoneException("This short link has reached its maximum allowed click limit.");
            }
        } else {
            // Atomic increment for uncapped links as well
            shortLinkRepository.incrementClickCountIfUnderLimit(link.getId());
        }

        // 6. Asynchronously record click event without blocking response
        clickTrackingService.recordClickAsync(link.getId(), request);

        // 7. Store in positive cache
        if (redirectCache != null) {
            redirectCache.put(shortCode, link.getOriginalUrl());
        }

        return link.getOriginalUrl();
    }
}
