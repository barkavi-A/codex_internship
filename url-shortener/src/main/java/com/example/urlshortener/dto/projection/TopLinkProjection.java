package com.example.urlshortener.dto.projection;

public interface TopLinkProjection {
    Long getLinkId();
    String getShortCode();
    String getTitle();
    String getOriginalUrl();
    Long getClicks();
}
