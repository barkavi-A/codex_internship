package com.example.urlshortener.repository;

import com.example.urlshortener.entity.ShortLink;
import com.example.urlshortener.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class ShortLinkRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ShortLinkRepository shortLinkRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = entityManager.persist(new User("Test User", "testrepo@example.com", "hash"));
    }

    @Test
    @DisplayName("Duplicate short_code violates unique constraint")
    void testUniqueShortCodeConstraint() {
        ShortLink link1 = new ShortLink();
        link1.setUser(testUser);
        link1.setShortCode("duplicateCode");
        link1.setOriginalUrl("https://link1.com");
        shortLinkRepository.saveAndFlush(link1);

        ShortLink link2 = new ShortLink();
        link2.setUser(testUser);
        link2.setShortCode("duplicateCode");
        link2.setOriginalUrl("https://link2.com");

        assertThrows(DataIntegrityViolationException.class, () -> {
            shortLinkRepository.saveAndFlush(link2);
        });
    }

    @Test
    @DisplayName("findUserLinks applies search filters correctly")
    void testFindUserLinksSearchFilter() {
        ShortLink link1 = new ShortLink();
        link1.setUser(testUser);
        link1.setShortCode("code1");
        link1.setOriginalUrl("https://spring.io");
        link1.setTitle("Spring Framework");
        shortLinkRepository.save(link1);

        ShortLink link2 = new ShortLink();
        link2.setUser(testUser);
        link2.setShortCode("code2");
        link2.setOriginalUrl("https://oracle.com");
        link2.setTitle("Java Docs");
        shortLinkRepository.save(link2);

        Page<ShortLink> result = shortLinkRepository.findUserLinks(
                testUser.getId(), "spring", null, null, LocalDateTime.now(), PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
        assertEquals("code1", result.getContent().get(0).getShortCode());
    }
}
