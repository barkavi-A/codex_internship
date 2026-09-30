package com.example.urlshortener.service;

import com.example.urlshortener.config.AppProperties;
import com.example.urlshortener.config.CacheConfig;
import com.example.urlshortener.dto.request.BulkCreateRequest;
import com.example.urlshortener.dto.request.CreateLinkRequest;
import com.example.urlshortener.dto.request.UpdateLinkRequest;
import com.example.urlshortener.dto.response.BulkCreateResponse;
import com.example.urlshortener.dto.response.LinkResponse;
import com.example.urlshortener.entity.ShortLink;
import com.example.urlshortener.entity.User;
import com.example.urlshortener.exception.AliasConflictException;
import com.example.urlshortener.exception.BadRequestException;
import com.example.urlshortener.exception.ResourceNotFoundException;
import com.example.urlshortener.repository.ShortLinkRepository;
import com.example.urlshortener.repository.UserRepository;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class LinkService {

    private final ShortLinkRepository shortLinkRepository;
    private final UserRepository userRepository;
    private final UrlValidator urlValidator;
    private final ShortCodeGenerator codeGenerator;
    private final RateLimiter rateLimiter;
    private final AppProperties appProperties;
    private final CacheManager cacheManager;

    public LinkService(ShortLinkRepository shortLinkRepository,
                       UserRepository userRepository,
                       UrlValidator urlValidator,
                       ShortCodeGenerator codeGenerator,
                       RateLimiter rateLimiter,
                       AppProperties appProperties,
                       CacheManager cacheManager) {
        this.shortLinkRepository = shortLinkRepository;
        this.userRepository = userRepository;
        this.urlValidator = urlValidator;
        this.codeGenerator = codeGenerator;
        this.rateLimiter = rateLimiter;
        this.appProperties = appProperties;
        this.cacheManager = cacheManager;
    }

    /**
     * Creates a new short link for the specified user.
     */
    @Transactional
    public LinkResponse createLink(Long userId, CreateLinkRequest request) {
        rateLimiter.checkLinkCreationRateLimit(userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String normalizedUrl = urlValidator.validateAndNormalize(request.url());

        // Idempotency check: if user passed no alias and forceNew != true, return existing link if available
        if ((request.customAlias() == null || request.customAlias().isBlank()) && !Boolean.TRUE.equals(request.forceNew())) {
            Optional<ShortLink> existing = shortLinkRepository.findByUserIdAndOriginalUrlAndCustomAliasFalse(userId, normalizedUrl);
            if (existing.isPresent()) {
                return mapToResponse(existing.get());
            }
        }

        ShortLink link = new ShortLink();
        link.setUser(user);
        link.setOriginalUrl(normalizedUrl);
        link.setTitle(request.title() != null ? request.title().trim() : null);
        link.setExpiresAt(request.expiresAt());
        link.setMaxClicks(request.maxClicks());
        link.setActive(true);

        if (request.customAlias() != null && !request.customAlias().isBlank()) {
            String alias = request.customAlias().trim();
            if (codeGenerator.isReservedWord(alias)) {
                throw new AliasConflictException("Custom alias '" + alias + "' is a reserved keyword");
            }
            if (shortLinkRepository.existsByShortCode(alias)) {
                throw new AliasConflictException("Custom alias '" + alias + "' is already in use");
            }
            link.setShortCode(alias);
            link.setCustomAlias(true);
        } else {
            // First save entity to obtain auto-increment ID
            link.setShortCode("tmp_" + System.nanoTime());
            link.setCustomAlias(false);
        }

        ShortLink saved = shortLinkRepository.save(link);

        if (!saved.isCustomAlias()) {
            String generatedCode = codeGenerator.generateCodeFromId(saved.getId());
            // Double check if code collides
            int attempt = 0;
            while (shortLinkRepository.existsByShortCode(generatedCode)) {
                attempt++;
                generatedCode = codeGenerator.generateCodeFromId(saved.getId(), attempt);
            }
            saved.setShortCode(generatedCode);
            saved = shortLinkRepository.save(saved);
        }

        return mapToResponse(saved);
    }

    /**
     * Retrieves paginated links for the user with optional searching and filtering.
     */
    @Transactional(readOnly = true)
    public Page<LinkResponse> getUserLinks(Long userId, int page, int size, String search, Boolean active, Boolean expired) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        LocalDateTime now = LocalDateTime.now();
        String searchPattern = (search != null && !search.trim().isEmpty()) ? search.trim() : null;

        return shortLinkRepository.findUserLinks(userId, searchPattern, active, expired, now, pageable)
                .map(this::mapToResponse);
    }

    /**
     * Gets a single link by ID for the user. Returns 404 if not found or owned by another user.
     */
    @Transactional(readOnly = true)
    public LinkResponse getLinkById(Long userId, Long linkId) {
        ShortLink link = shortLinkRepository.findByIdAndUserId(linkId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Link not found with ID: " + linkId));
        return mapToResponse(link);
    }

    /**
     * Updates an existing link. Invalidates cache if target URL or active status changes.
     */
    @Transactional
    public LinkResponse updateLink(Long userId, Long linkId, UpdateLinkRequest request) {
        ShortLink link = shortLinkRepository.findByIdAndUserId(linkId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Link not found with ID: " + linkId));

        boolean invalidateCacheNeeded = false;

        if (request.originalUrl() != null && !request.originalUrl().isBlank()) {
            String newNormalized = urlValidator.validateAndNormalize(request.originalUrl());
            if (!newNormalized.equals(link.getOriginalUrl())) {
                link.setOriginalUrl(newNormalized);
                invalidateCacheNeeded = true;
            }
        }

        if (request.title() != null) {
            link.setTitle(request.title().trim());
        }

        if (request.expiresAt() != null) {
            link.setExpiresAt(request.expiresAt());
        }

        if (request.maxClicks() != null) {
            link.setMaxClicks(request.maxClicks());
        }

        if (request.active() != null && request.active() != link.isActive()) {
            link.setActive(request.active());
            invalidateCacheNeeded = true;
        }

        ShortLink updated = shortLinkRepository.save(link);

        if (invalidateCacheNeeded) {
            evictCache(updated.getShortCode());
        }

        return mapToResponse(updated);
    }

    /**
     * Deletes a short link and evicts its cached redirect.
     */
    @Transactional
    public void deleteLink(Long userId, Long linkId) {
        ShortLink link = shortLinkRepository.findByIdAndUserId(linkId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Link not found with ID: " + linkId));

        String code = link.getShortCode();
        shortLinkRepository.delete(link);
        evictCache(code);
    }

    /**
     * Creates up to 50 links in bulk.
     */
    @Transactional
    public BulkCreateResponse createBulkLinks(Long userId, BulkCreateRequest request) {
        if (request.links() == null || request.links().isEmpty()) {
            throw new BadRequestException("Bulk link list cannot be empty");
        }
        if (request.links().size() > 50) {
            throw new BadRequestException("Bulk request exceeds limit of 50 links");
        }

        List<BulkCreateResponse.BulkItemResult> results = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;

        for (int i = 0; i < request.links().size(); i++) {
            CreateLinkRequest itemReq = request.links().get(i);
            try {
                LinkResponse created = createLink(userId, itemReq);
                results.add(new BulkCreateResponse.BulkItemResult(i, true, created, null));
                successCount++;
            } catch (Exception e) {
                results.add(new BulkCreateResponse.BulkItemResult(i, false, null, e.getMessage()));
                failureCount++;
            }
        }

        return new BulkCreateResponse(results, request.links().size(), successCount, failureCount);
    }

    public void evictCache(String shortCode) {
        if (shortCode != null) {
            var cache = cacheManager.getCache(CacheConfig.REDIRECT_CACHE);
            if (cache != null) {
                cache.evict(shortCode);
            }
        }
    }

    public LinkResponse mapToResponse(ShortLink link) {
        String shortUrl = appProperties.getBaseUrl() + "/" + link.getShortCode();
        return new LinkResponse(
                link.getId(),
                link.getShortCode(),
                shortUrl,
                link.getOriginalUrl(),
                link.getTitle(),
                link.isCustomAlias(),
                link.getExpiresAt(),
                link.getMaxClicks(),
                link.getClickCount(),
                link.isActive(),
                link.getCreatedAt(),
                link.getUpdatedAt()
        );
    }
}
