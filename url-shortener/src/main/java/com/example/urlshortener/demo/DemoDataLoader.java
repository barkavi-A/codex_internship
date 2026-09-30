package com.example.urlshortener.demo;

import com.example.urlshortener.entity.DeviceType;
import com.example.urlshortener.entity.ShortLink;
import com.example.urlshortener.entity.User;
import com.example.urlshortener.repository.ClickEventRepository;
import com.example.urlshortener.repository.ShortLinkRepository;
import com.example.urlshortener.repository.UserRepository;
import com.example.urlshortener.service.ClickTrackingService;
import com.example.urlshortener.service.ShortCodeGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
@Profile("demo")
public class DemoDataLoader implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DemoDataLoader.class);

    private final UserRepository userRepository;
    private final ShortLinkRepository shortLinkRepository;
    private final ClickEventRepository clickEventRepository;
    private final PasswordEncoder passwordEncoder;
    private final ShortCodeGenerator codeGenerator;
    private final ClickTrackingService clickTrackingService;
    private final JdbcTemplate jdbcTemplate;

    public DemoDataLoader(UserRepository userRepository,
                          ShortLinkRepository shortLinkRepository,
                          ClickEventRepository clickEventRepository,
                          PasswordEncoder passwordEncoder,
                          ShortCodeGenerator codeGenerator,
                          ClickTrackingService clickTrackingService,
                          JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.shortLinkRepository = shortLinkRepository;
        this.clickEventRepository = clickEventRepository;
        this.passwordEncoder = passwordEncoder;
        this.codeGenerator = codeGenerator;
        this.clickTrackingService = clickTrackingService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) throws Exception {
        logger.info("Initializing demo dataset for profile 'demo'...");

        if (userRepository.existsByEmail("demo@example.com")) {
            logger.info("Demo user already exists. Skipping demo data initialization.");
            return;
        }

        // 1. Create demo user
        User demoUser = new User("Demo User", "demo@example.com", passwordEncoder.encode("Demo1234!"));
        demoUser = userRepository.save(demoUser);

        // 2. Create 15 short links
        String[] sampleUrls = {
                "https://github.com/spring-projects/spring-boot",
                "https://docs.oracle.com/en/java/javase/17/",
                "https://dev.mysql.com/doc/refman/8.0/en/",
                "https://jwt.io/introduction",
                "https://caffeine.ben-manes.com/",
                "https://zxing.github.io/zxing/",
                "https://springdoc.org/",
                "https://www.docker.com/products/docker-desktop/",
                "https://flywaydb.org/documentation/",
                "https://junit.org/junit5/docs/current/user-guide/",
                "https://site.mockito.org/",
                "https://www.jacoco.org/jacoco/trunk/doc/",
                "https://developer.mozilla.org/en-US/docs/Web/JavaScript",
                "https://css-tricks.com/snippets/css/a-guide-to-flexbox/",
                "https://news.ycombinator.com/"
        };

        String[] sampleTitles = {
                "Spring Boot GitHub Repository",
                "Java 17 Documentation",
                "MySQL 8.0 Reference Manual",
                "JWT.io Introduction",
                "Caffeine Cache Documentation",
                "ZXing Barcode Library",
                "SpringDoc OpenAPI Docs",
                "Docker Desktop Overview",
                "Flyway Migrations Guide",
                "JUnit 5 User Guide",
                "Mockito Framework",
                "JaCoCo Code Coverage",
                "MDN JavaScript Reference",
                "Complete Guide to Flexbox",
                "Hacker News Homepage"
        };

        List<ShortLink> links = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            ShortLink link = new ShortLink();
            link.setUser(demoUser);
            link.setOriginalUrl(sampleUrls[i]);
            link.setTitle(sampleTitles[i]);
            link.setActive(true);
            if (i == 0) {
                link.setShortCode("spring-boot-repo");
                link.setCustomAlias(true);
            } else if (i == 1) {
                link.setShortCode("java17-docs");
                link.setCustomAlias(true);
            } else {
                link.setShortCode("tmp_demo_" + i);
                link.setCustomAlias(false);
            }
            ShortLink saved = shortLinkRepository.save(link);
            if (!saved.isCustomAlias()) {
                saved.setShortCode(codeGenerator.generateCodeFromId(saved.getId()));
                saved = shortLinkRepository.save(saved);
            }
            links.add(saved);
        }

        // 3. Generate 5,000 synthetic click events over the last 60 days
        logger.info("Generating ~5,000 synthetic click events...");
        Random random = new Random(42); // deterministic seed

        String[] referrers = {"direct", "google.com", "twitter.com", "github.com", "reddit.com", "linkedin.com", "facebook.com"};
        String[] browsers = {"Chrome", "Safari", "Firefox", "Edge", "Opera"};
        String[] oses = {"Windows", "macOS", "Linux", "Android", "iOS"};
        String[] countries = {"US", "IN", "DE", "GB", "CA", "FR", "JP", "BR"};

        String sql = "INSERT INTO click_events (link_id, clicked_at, referrer_domain, browser, os, device_type, is_bot, visitor_hash, country) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        List<Object[]> batchArgs = new ArrayList<>();

        LocalDateTime now = LocalDateTime.now();
        int totalClicks = 5000;

        for (int i = 0; i < totalClicks; i++) {
            ShortLink link = links.get(random.nextInt(links.size()));
            int daysAgo = random.nextInt(60);
            int hours = random.nextInt(24);
            int minutes = random.nextInt(60);
            LocalDateTime clickedAt = now.minusDays(daysAgo).withHour(hours).withMinute(minutes);

            String ref = referrers[random.nextInt(referrers.length)];
            String browser = browsers[random.nextInt(browsers.length)];
            String os = oses[random.nextInt(oses.length)];
            String country = countries[random.nextInt(countries.length)];

            boolean isBot = random.nextDouble() < 0.05; // 5% bots
            DeviceType deviceType = isBot ? DeviceType.BOT : (os.equals("Android") || os.equals("iOS") ? DeviceType.MOBILE : DeviceType.DESKTOP);

            String visitorHash = clickTrackingService.computeVisitorHash("192.168.1." + random.nextInt(250), browser + "/" + os);

            batchArgs.add(new Object[]{
                    link.getId(),
                    Timestamp.valueOf(clickedAt),
                    ref,
                    browser,
                    os,
                    deviceType.name(),
                    isBot,
                    visitorHash,
                    country
            });

            link.setClickCount(link.getClickCount() + 1);
        }

        jdbcTemplate.batchUpdate(sql, batchArgs);
        shortLinkRepository.saveAll(links);

        logger.info("Demo data initialization completed successfully! Demo credentials: demo@example.com / Demo1234!");
    }
}
