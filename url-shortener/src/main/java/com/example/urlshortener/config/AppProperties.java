package com.example.urlshortener.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String baseUrl = "http://localhost:8080";
    private Jwt jwt = new Jwt();
    private String ipHashSalt = "default-ip-hash-salt-value-for-privacy-protection";
    private ClickTracking clickTracking = new ClickTracking();
    private Url url = new Url();

    public static class Jwt {
        private String secret = "super-secret-jwt-key-for-url-shortener-service-2026-min-256-bits";
        private long expirationMs = 86400000; // 24 hours

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public long getExpirationMs() {
            return expirationMs;
        }

        public void setExpirationMs(long expirationMs) {
            this.expirationMs = expirationMs;
        }
    }

    public static class ClickTracking {
        private int queueCapacity = 10000;
        private int batchSize = 100;
        private long flushIntervalMs = 2000;
        private int rawRetentionDays = 365;
        private boolean respectDnt = true;

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public long getFlushIntervalMs() {
            return flushIntervalMs;
        }

        public void setFlushIntervalMs(long flushIntervalMs) {
            this.flushIntervalMs = flushIntervalMs;
        }

        public int getRawRetentionDays() {
            return rawRetentionDays;
        }

        public void setRawRetentionDays(int rawRetentionDays) {
            this.rawRetentionDays = rawRetentionDays;
        }

        public boolean isRespectDnt() {
            return respectDnt;
        }

        public void setRespectDnt(boolean respectDnt) {
            this.respectDnt = respectDnt;
        }
    }

    public static class Url {
        private List<String> domainBlocklist = new ArrayList<>();
        private long scrambleKey = 987654321L;

        public List<String> getDomainBlocklist() {
            return domainBlocklist;
        }

        public void setDomainBlocklist(List<String> domainBlocklist) {
            this.domainBlocklist = domainBlocklist;
        }

        public long getScrambleKey() {
            return scrambleKey;
        }

        public void setScrambleKey(long scrambleKey) {
            this.scrambleKey = scrambleKey;
        }
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public void setJwt(Jwt jwt) {
        this.jwt = jwt;
    }

    public String getIpHashSalt() {
        return ipHashSalt;
    }

    public void setIpHashSalt(String ipHashSalt) {
        this.ipHashSalt = ipHashSalt;
    }

    public ClickTracking getClickTracking() {
        return clickTracking;
    }

    public void setClickTracking(ClickTracking clickTracking) {
        this.clickTracking = clickTracking;
    }

    public Url getUrl() {
        return url;
    }

    public void setUrl(Url url) {
        this.url = url;
    }
}
