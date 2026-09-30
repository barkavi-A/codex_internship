package com.example.urlshortener.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "daily_link_stats", uniqueConstraints = {
        @UniqueConstraint(name = "uk_daily_stats_link_date", columnNames = {"link_id", "stat_date"})
})
public class DailyLinkStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "link_id", nullable = false)
    private ShortLink link;

    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

    @Column(name = "clicks", nullable = false)
    private long clicks = 0;

    @Column(name = "unique_visitors", nullable = false)
    private long uniqueVisitors = 0;

    @Column(name = "bot_clicks", nullable = false)
    private long botClicks = 0;

    public DailyLinkStats() {}

    public DailyLinkStats(ShortLink link, LocalDate statDate, long clicks, long uniqueVisitors, long botClicks) {
        this.link = link;
        this.statDate = statDate;
        this.clicks = clicks;
        this.uniqueVisitors = uniqueVisitors;
        this.botClicks = botClicks;
    }

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

    public LocalDate getStatDate() {
        return statDate;
    }

    public void setStatDate(LocalDate statDate) {
        this.statDate = statDate;
    }

    public long getClicks() {
        return clicks;
    }

    public void setClicks(long clicks) {
        this.clicks = clicks;
    }

    public long getUniqueVisitors() {
        return uniqueVisitors;
    }

    public void setUniqueVisitors(long uniqueVisitors) {
        this.uniqueVisitors = uniqueVisitors;
    }

    public long getBotClicks() {
        return botClicks;
    }

    public void setBotClicks(long botClicks) {
        this.botClicks = botClicks;
    }
}
