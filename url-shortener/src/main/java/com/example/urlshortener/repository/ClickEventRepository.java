package com.example.urlshortener.repository;

import com.example.urlshortener.dto.projection.BreakdownProjection;
import com.example.urlshortener.dto.projection.LinkStatsProjection;
import com.example.urlshortener.dto.projection.TopLinkProjection;
import com.example.urlshortener.entity.ClickEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    @Query("SELECT COUNT(c) AS totalClicks, COUNT(DISTINCT c.visitorHash) AS uniqueVisitors, " +
           "SUM(CASE WHEN c.bot = true THEN 1 ELSE 0 END) AS botClicks " +
           "FROM ClickEvent c WHERE c.link.user.id = :userId " +
           "AND c.clickedAt >= :from AND c.clickedAt <= :to")
    LinkStatsProjection getUserOverviewStats(@Param("userId") Long userId,
                                              @Param("from") LocalDateTime from,
                                              @Param("to") LocalDateTime to);

    @Query("SELECT COUNT(c) FROM ClickEvent c WHERE c.link.user.id = :userId " +
           "AND c.clickedAt >= :from AND c.clickedAt <= :to")
    long countUserClicksBetween(@Param("userId") Long userId,
                                @Param("from") LocalDateTime from,
                                @Param("to") LocalDateTime to);

    @Query("SELECT COUNT(c) AS totalClicks, COUNT(DISTINCT c.visitorHash) AS uniqueVisitors, " +
           "SUM(CASE WHEN c.bot = true THEN 1 ELSE 0 END) AS botClicks " +
           "FROM ClickEvent c WHERE c.link.id = :linkId " +
           "AND c.clickedAt >= :from AND c.clickedAt <= :to")
    LinkStatsProjection getLinkStats(@Param("linkId") Long linkId,
                                      @Param("from") LocalDateTime from,
                                      @Param("to") LocalDateTime to);

    @Query("SELECT c.link.id AS linkId, c.link.shortCode AS shortCode, c.link.title AS title, " +
           "c.link.originalUrl AS originalUrl, COUNT(c) AS clicks " +
           "FROM ClickEvent c WHERE c.link.user.id = :userId " +
           "AND c.clickedAt >= :from AND c.clickedAt <= :to " +
           "GROUP BY c.link.id, c.link.shortCode, c.link.title, c.link.originalUrl " +
           "ORDER BY COUNT(c) DESC")
    List<TopLinkProjection> getTopLinksByUser(@Param("userId") Long userId,
                                               @Param("from") LocalDateTime from,
                                               @Param("to") LocalDateTime to,
                                               Pageable pageable);

    @Query("SELECT c.referrerDomain AS name, COUNT(c) AS count " +
           "FROM ClickEvent c WHERE c.link.id = :linkId " +
           "AND c.clickedAt >= :from AND c.clickedAt <= :to " +
           "GROUP BY c.referrerDomain ORDER BY COUNT(c) DESC")
    List<BreakdownProjection> getReferrerBreakdown(@Param("linkId") Long linkId,
                                                   @Param("from") LocalDateTime from,
                                                   @Param("to") LocalDateTime to,
                                                   Pageable pageable);

    @Query("SELECT c.browser AS name, COUNT(c) AS count " +
           "FROM ClickEvent c WHERE c.link.id = :linkId " +
           "AND c.clickedAt >= :from AND c.clickedAt <= :to " +
           "GROUP BY c.browser ORDER BY COUNT(c) DESC")
    List<BreakdownProjection> getBrowserBreakdown(@Param("linkId") Long linkId,
                                                  @Param("from") LocalDateTime from,
                                                  @Param("to") LocalDateTime to,
                                                  Pageable pageable);

    @Query("SELECT c.os AS name, COUNT(c) AS count " +
           "FROM ClickEvent c WHERE c.link.id = :linkId " +
           "AND c.clickedAt >= :from AND c.clickedAt <= :to " +
           "GROUP BY c.os ORDER BY COUNT(c) DESC")
    List<BreakdownProjection> getOsBreakdown(@Param("linkId") Long linkId,
                                             @Param("from") LocalDateTime from,
                                             @Param("to") LocalDateTime to,
                                             Pageable pageable);

    @Query("SELECT CAST(c.deviceType AS string) AS name, COUNT(c) AS count " +
           "FROM ClickEvent c WHERE c.link.id = :linkId " +
           "AND c.clickedAt >= :from AND c.clickedAt <= :to " +
           "GROUP BY c.deviceType ORDER BY COUNT(c) DESC")
    List<BreakdownProjection> getDeviceBreakdown(@Param("linkId") Long linkId,
                                                 @Param("from") LocalDateTime from,
                                                 @Param("to") LocalDateTime to,
                                                 Pageable pageable);

    @Query("SELECT c.country AS name, COUNT(c) AS count " +
           "FROM ClickEvent c WHERE c.link.id = :linkId " +
           "AND c.clickedAt >= :from AND c.clickedAt <= :to " +
           "GROUP BY c.country ORDER BY COUNT(c) DESC")
    List<BreakdownProjection> getCountryBreakdown(@Param("linkId") Long linkId,
                                                  @Param("from") LocalDateTime from,
                                                  @Param("to") LocalDateTime to,
                                                  Pageable pageable);

    @Query("SELECT c.clickedAt FROM ClickEvent c WHERE c.link.id = :linkId AND c.clickedAt >= :from AND c.clickedAt <= :to")
    List<LocalDateTime> findClickTimesForLink(@Param("linkId") Long linkId,
                                              @Param("from") LocalDateTime from,
                                              @Param("to") LocalDateTime to);

    @Query("SELECT c.clickedAt FROM ClickEvent c WHERE c.link.user.id = :userId AND c.clickedAt >= :from AND c.clickedAt <= :to")
    List<LocalDateTime> findClickTimesForUser(@Param("userId") Long userId,
                                              @Param("from") LocalDateTime from,
                                              @Param("to") LocalDateTime to);

    @Query(value = "SELECT c.link_id AS linkId, CAST(c.clicked_at AS DATE) AS statDate, " +
                   "COUNT(*) AS clicks, COUNT(DISTINCT c.visitor_hash) AS uniqueVisitors, " +
                   "SUM(CASE WHEN c.is_bot = 1 THEN 1 ELSE 0 END) AS botClicks " +
                   "FROM click_events c WHERE c.clicked_at < :cutoffDate " +
                   "GROUP BY c.link_id, CAST(c.clicked_at AS DATE)", nativeQuery = true)
    List<Object[]> aggregateClickEventsBeforeDate(@Param("cutoffDate") LocalDateTime cutoffDate);

    @Modifying
    @Query("DELETE FROM ClickEvent c WHERE c.clickedAt < :cutoffDate")
    int deleteByClickedAtBefore(@Param("cutoffDate") LocalDateTime cutoffDate);
}
