package com.example.urlshortener.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String REDIRECT_CACHE = "redirectCache";
    public static final String NEGATIVE_CACHE = "negativeCache";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        
        cacheManager.registerCustomCache(REDIRECT_CACHE,
                Caffeine.newBuilder()
                        .maximumSize(10_000)
                        .expireAfterWrite(10, TimeUnit.MINUTES)
                        .build());

        cacheManager.registerCustomCache(NEGATIVE_CACHE,
                Caffeine.newBuilder()
                        .maximumSize(5_000)
                        .expireAfterWrite(30, TimeUnit.SECONDS)
                        .build());

        return cacheManager;
    }
}
