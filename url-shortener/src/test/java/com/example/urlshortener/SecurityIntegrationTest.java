package com.example.urlshortener;

import com.example.urlshortener.dto.request.LoginRequest;
import com.example.urlshortener.entity.ShortLink;
import com.example.urlshortener.entity.User;
import com.example.urlshortener.repository.ShortLinkRepository;
import com.example.urlshortener.repository.UserRepository;
import com.example.urlshortener.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShortLinkRepository shortLinkRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    private User userA;
    private User userB;
    private String tokenA;
    private String tokenB;
    private ShortLink linkUserB;

    @BeforeEach
    void setUp() {
        userA = userRepository.save(new User("User A", "usera@example.com", passwordEncoder.encode("Password123!")));
        userB = userRepository.save(new User("User B", "userb@example.com", passwordEncoder.encode("Password123!")));

        tokenA = jwtService.generateTokenForUser(userA.getId(), userA.getEmail(), userA.getName());
        tokenB = jwtService.generateTokenForUser(userB.getId(), userB.getEmail(), userB.getName());

        linkUserB = new ShortLink();
        linkUserB.setUser(userB);
        linkUserB.setOriginalUrl("https://userb-target.com");
        linkUserB.setShortCode("codeB");
        linkUserB = shortLinkRepository.save(linkUserB);
    }

    @Test
    @DisplayName("Unauthenticated request to /api/links returns 401 Unauthorized")
    void testUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/links"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("User A accessing User B's link details returns 404 (Cross-user isolation)")
    void testUserCannotAccessOtherUserLink() throws Exception {
        mockMvc.perform(get("/api/links/" + linkUserB.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("User A updating User B's link returns 404 Not Found")
    void testUserCannotUpdateOtherUserLink() throws Exception {
        mockMvc.perform(put("/api/links/" + linkUserB.getId())
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hacked Title\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("User A deleting User B's link returns 404 Not Found")
    void testUserCannotDeleteOtherUserLink() throws Exception {
        mockMvc.perform(delete("/api/links/" + linkUserB.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("User A viewing User B's analytics returns 404 Not Found")
    void testUserCannotViewOtherUserAnalytics() throws Exception {
        mockMvc.perform(get("/api/links/" + linkUserB.getId() + "/analytics")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Login rate limit: 5 failed login attempts triggers 429 Too Many Requests")
    void testLoginRateLimitExceeded() throws Exception {
        LoginRequest badRequest = new LoginRequest("ratelimit@example.com", "WrongPassword1");

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badRequest)))
                    .andExpect(status().isUnauthorized());
        }

        // 6th attempt should be blocked by RateLimiter -> 429
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }
}
