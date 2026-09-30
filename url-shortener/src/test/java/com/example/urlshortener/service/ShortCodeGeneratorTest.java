package com.example.urlshortener.service;

import com.example.urlshortener.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class ShortCodeGeneratorTest {

    private ShortCodeGenerator generator;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties();
        appProperties.getUrl().setScrambleKey(987654321L);
        generator = new ShortCodeGenerator(appProperties);
    }

    @Test
    @DisplayName("Base62 encode and decode round trip should match original number")
    void testBase62RoundTrip() {
        long[] testIds = {0L, 1L, 12345L, 9999999L, Long.MAX_VALUE / 10000};
        for (long id : testIds) {
            String encoded = generator.encodeBase62(id);
            long decoded = generator.decodeBase62(encoded);
            assertEquals(id, decoded, "Decoded value must equal original for " + id);
        }
    }

    @Test
    @DisplayName("Generated codes must have minimum length of 6 characters")
    void testMinimumCodeLength() {
        String code = generator.generateCodeFromId(1L);
        assertTrue(code.length() >= 6, "Code length must be at least 6 chars");
    }

    @Test
    @DisplayName("Reserved keywords must be recognized and avoided")
    void testReservedWords() {
        assertTrue(generator.isReservedWord("api"));
        assertTrue(generator.isReservedWord("ADMIN"));
        assertTrue(generator.isReservedWord("swagger-ui"));
        assertFalse(generator.isReservedWord("my-custom-link"));
    }

    @Test
    @DisplayName("Generated codes should not follow sequential patterns")
    void testNonSequentialScrambling() {
        String code1 = generator.generateCodeFromId(100L);
        String code2 = generator.generateCodeFromId(101L);
        assertNotEquals(code1, code2);
        assertFalse(code2.startsWith(code1.substring(0, 4)), "Codes for consecutive IDs should be scrambled");
    }

    @Test
    @DisplayName("Concurrency test: 500 links created from 20 threads must produce 500 distinct short codes")
    void testConcurrentCodeGenerationUniqueness() throws InterruptedException {
        int threadCount = 20;
        int linksPerThread = 25; // total 500
        int totalLinks = threadCount * linksPerThread;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        Set<String> generatedCodes = Collections.newSetFromMap(new ConcurrentHashMap<>());

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    latch.await();
                    for (int i = 0; i < linksPerThread; i++) {
                        long dummyId = threadId * 1000L + i + 1L;
                        String code = generator.generateCodeFromId(dummyId);
                        generatedCodes.add(code);
                    }
                } catch (Exception e) {
                    fail("Thread execution failed: " + e.getMessage());
                }
            });
        }

        latch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS));

        assertEquals(totalLinks, generatedCodes.size(), "All 500 generated codes must be distinct under concurrency");
    }
}
