package com.example.urlshortener.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "click_events", indexes = {
        @Index(name = "idx_click_events_link_clicked", columnList = "link_id, clicked_at"),
        @Index(name = "idx_click_events_clicked_at", columnList = "clicked_at")
})
public class ClickEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "link_id", nullable = false)
    private ShortLink link;

    @Column(name = "clicked_at", nullable = false)
    private LocalDateTime clickedAt;

    @Column(name = "referrer_domain")
    private String referrerDomain;

    @Column(name = "browser")
    private String browser;

    @Column(name = "os")
    private String os;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", columnDefinition = "VARCHAR(50)", nullable = false)
    private DeviceType deviceType = DeviceType.OTHER;

    @Column(name = "is_bot", nullable = false)
    private boolean bot = false;

    @Column(name = "visitor_hash", length = 64, nullable = false)
    private String visitorHash;

    @Column(name = "country")
    private String country;

    public ClickEvent() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ShortLink getLink() {
        return link;
    }

    public void setLink(ShortLink link) {
        this.link = link;
    }

    public LocalDateTime getClickedAt() {
        return clickedAt;
    }

    public void setClickedAt(LocalDateTime clickedAt) {
        this.clickedAt = clickedAt;
    }

    public String getReferrerDomain() {
        return referrerDomain;
    }

    public void setReferrerDomain(String referrerDomain) {
        this.referrerDomain = referrerDomain;
    }

    public String getBrowser() {
        return browser;
    }

    public void setBrowser(String browser) {
        this.browser = browser;
    }

    public String getOs() {
        return os;
    }

    public void setOs(String os) {
        this.os = os;
    }

    public DeviceType getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(DeviceType deviceType) {
        this.deviceType = deviceType;
    }

    public boolean isBot() {
        return bot;
    }

    public void setBot(boolean bot) {
        this.bot = bot;
    }

    public String getVisitorHash() {
        return visitorHash;
    }

    public void setVisitorHash(String visitorHash) {
        this.visitorHash = visitorHash;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }
}
