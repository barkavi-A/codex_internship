package com.example.urlshortener.service;

import com.example.urlshortener.config.AppProperties;
import com.example.urlshortener.dto.request.BulkCreateRequest;
import com.example.urlshortener.dto.request.CreateLinkRequest;
import com.example.urlshortener.dto.request.UpdateLinkRequest;
import com.example.urlshortener.dto.response.BulkCreateResponse;
import com.example.urlshortener.dto.response.LinkResponse;
import com.example.urlshortener.entity.ShortLink;
import com.example.urlshortener.entity.User;
import com.example.urlshortener.exception.AliasConflictException;
import com.example.urlshortener.exception.ResourceNotFoundException;
import com.example.urlshortener.repository.ShortLinkRepository;
import com.example.urlshortener.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LinkServiceTest {

    @Mock
    private ShortLinkRepository shortLinkRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UrlValidator urlValidator;

    @Mock
    private ShortCodeGenerator codeGenerator;

    @Mock
    private RateLimiter rateLimiter;

    @Mock
    private AppProperties appProperties;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    @InjectMocks
    private LinkService linkService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User("Test User", "test@example.com", "password");
        testUser.setId(1L);
        lenient().when(appProperties.getBaseUrl()).thenReturn("http://localhost:8080");
        lenient().when(cacheManager.getCache(anyString())).thenReturn(cache);
    }

    @Test
    @DisplayName("Create link successfully with auto-generated short code")
    void testCreateLinkSuccess() {
        CreateLinkRequest request = new CreateLinkRequest("https://example.com", null, "My Title", null, null, false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(urlValidator.validateAndNormalize("https://example.com")).thenReturn("https://example.com");
        when(shortLinkRepository.findByUserIdAndOriginalUrlAndCustomAliasFalse(1L, "https://example.com")).thenReturn(Optional.empty());

        ShortLink linkWithId = new ShortLink();
        linkWithId.setId(10L);
        linkWithId.setUser(testUser);
        linkWithId.setOriginalUrl("https://example.com");
        linkWithId.setShortCode("tmp_code");

        when(shortLinkRepository.save(any(ShortLink.class))).thenReturn(linkWithId);
        when(codeGenerator.generateCodeFromId(10L)).thenReturn("abc123");
        when(shortLinkRepository.existsByShortCode("abc123")).thenReturn(false);

        LinkResponse response = linkService.createLink(1L, request);

        assertNotNull(response);
        assertEquals("http://localhost:8080/abc123", response.shortUrl());
        verify(rateLimiter).checkLinkCreationRateLimit(1L);
    }

    @Test
    @DisplayName("Idempotent duplicate link creation returns existing link when forceNew=false")
    void testIdempotentDuplicateLink() {
        CreateLinkRequest request = new CreateLinkRequest("https://example.com", null, null, null, null, false);
        ShortLink existing = new ShortLink();
        existing.setId(5L);
        existing.setUser(testUser);
        existing.setShortCode("exist1");
        existing.setOriginalUrl("https://example.com");

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(urlValidator.validateAndNormalize("https://example.com")).thenReturn("https://example.com");
        when(shortLinkRepository.findByUserIdAndOriginalUrlAndCustomAliasFalse(1L, "https://example.com")).thenReturn(Optional.of(existing));

        LinkResponse response = linkService.createLink(1L, request);

        assertEquals(5L, response.id());
        assertEquals("exist1", response.shortCode());
        verify(shortLinkRepository, never()).save(any());
    }

    @Test
    @DisplayName("Custom alias conflict throws 409 AliasConflictException")
    void testCustomAliasConflict() {
        CreateLinkRequest request = new CreateLinkRequest("https://example.com", "mycode", null, null, null, false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(urlValidator.validateAndNormalize("https://example.com")).thenReturn("https://example.com");
        when(codeGenerator.isReservedWord("mycode")).thenReturn(false);
        when(shortLinkRepository.existsByShortCode("mycode")).thenReturn(true);

        assertThrows(AliasConflictException.class, () -> linkService.createLink(1L, request));
    }

    @Test
    @DisplayName("Reserved word custom alias throws 409 AliasConflictException")
    void testReservedAliasRejection() {
        CreateLinkRequest request = new CreateLinkRequest("https://example.com", "admin", null, null, null, false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(urlValidator.validateAndNormalize("https://example.com")).thenReturn("https://example.com");
        when(codeGenerator.isReservedWord("admin")).thenReturn(true);

        assertThrows(AliasConflictException.class, () -> linkService.createLink(1L, request));
    }

    @Test
    @DisplayName("Updating destination URL invalidates Caffeine cache")
    void testUpdateLinkInvalidatesCache() {
        ShortLink link = new ShortLink();
        link.setId(10L);
        link.setUser(testUser);
        link.setShortCode("code10");
        link.setOriginalUrl("https://old.com");

        when(shortLinkRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(link));
        when(urlValidator.validateAndNormalize("https://new.com")).thenReturn("https://new.com");
        when(shortLinkRepository.save(any(ShortLink.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateLinkRequest updateReq = new UpdateLinkRequest("https://new.com", "New Title", null, null, true);
        linkService.updateLink(1L, 10L, updateReq);

        verify(cache).evict("code10");
    }

    @Test
    @DisplayName("Delete link evicts Caffeine cache and deletes entity")
    void testDeleteLink() {
        ShortLink link = new ShortLink();
        link.setId(10L);
        link.setUser(testUser);
        link.setShortCode("code10");

        when(shortLinkRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(link));

        linkService.deleteLink(1L, 10L);

        verify(shortLinkRepository).delete(link);
        verify(cache).evict("code10");
    }

    @Test
    @DisplayName("Bulk link creation handles partial failures gracefully")
    void testBulkCreationPartialFailure() {
        CreateLinkRequest req1 = new CreateLinkRequest("https://valid1.com", null, null, null, null, false);
        CreateLinkRequest req2 = new CreateLinkRequest("https://malicious.com", null, null, null, null, false);
        BulkCreateRequest bulkReq = new BulkCreateRequest(List.of(req1, req2));

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(urlValidator.validateAndNormalize("https://valid1.com")).thenReturn("https://valid1.com");
        when(urlValidator.validateAndNormalize("https://malicious.com")).thenThrow(new IllegalArgumentException("Blocked domain"));

        ShortLink link = new ShortLink();
        link.setId(1L);
        link.setUser(testUser);
        link.setShortCode("code1");
        link.setOriginalUrl("https://valid1.com");
        when(shortLinkRepository.save(any())).thenReturn(link);
        when(codeGenerator.generateCodeFromId(1L)).thenReturn("code1");

        BulkCreateResponse response = linkService.createBulkLinks(1L, bulkReq);

        assertEquals(2, response.total());
        assertEquals(1, response.successCount());
        assertEquals(1, response.failureCount());
        assertTrue(response.results().get(0).success());
        assertFalse(response.results().get(1).success());
    }
}
