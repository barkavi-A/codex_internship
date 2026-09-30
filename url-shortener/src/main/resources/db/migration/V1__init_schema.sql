CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL
);

CREATE TABLE short_links (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    short_code VARCHAR(32) NOT NULL UNIQUE,
    original_url VARCHAR(2048) NOT NULL,
    title VARCHAR(255) NULL,
    is_custom_alias BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at DATETIME(6) NULL,
    max_clicks BIGINT NULL,
    click_count BIGINT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_short_links_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_short_links_user_created ON short_links (user_id, created_at);
CREATE INDEX idx_short_links_code ON short_links (short_code);

CREATE TABLE click_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    link_id BIGINT NOT NULL,
    clicked_at DATETIME(6) NOT NULL,
    referrer_domain VARCHAR(255) NULL,
    browser VARCHAR(100) NULL,
    os VARCHAR(100) NULL,
    device_type VARCHAR(50) NOT NULL DEFAULT 'OTHER',
    is_bot BOOLEAN NOT NULL DEFAULT FALSE,
    visitor_hash VARCHAR(64) NOT NULL,
    country VARCHAR(100) NULL,
    CONSTRAINT fk_click_events_link FOREIGN KEY (link_id) REFERENCES short_links (id) ON DELETE CASCADE
);

CREATE INDEX idx_click_events_link_clicked ON click_events (link_id, clicked_at);
CREATE INDEX idx_click_events_clicked_at ON click_events (clicked_at);

CREATE TABLE daily_link_stats (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    link_id BIGINT NOT NULL,
    stat_date DATE NOT NULL,
    clicks BIGINT NOT NULL DEFAULT 0,
    unique_visitors BIGINT NOT NULL DEFAULT 0,
    bot_clicks BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_daily_stats_link_date UNIQUE (link_id, stat_date),
    CONSTRAINT fk_daily_stats_link FOREIGN KEY (link_id) REFERENCES short_links (id) ON DELETE CASCADE
);
