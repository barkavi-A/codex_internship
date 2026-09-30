package com.example.urlshortener;

import com.example.urlshortener.dto.request.CreateLinkRequest;
import com.example.urlshortener.dto.request.LoginRequest;
import com.example.urlshortener.dto.request.RegisterRequest;
import com.example.urlshortener.service.ClickTrackingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class WebIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ClickTrackingService clickTrackingService;

    @Test
    @DisplayName("End-to-End Flow: Register -> Login -> Create Link -> Follow Redirect -> Flush Click -> Verify Analytics")
    void testEndToEndFlow() throws Exception {
        // 1. Register User
        RegisterRequest registerReq = new RegisterRequest("E2E User", "e2e@example.com", "SecurePass1");
        MvcResult regResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").exists())
                .andReturn();

        JsonNode regNode = objectMapper.readTree(regResult.getResponse().getContentAsString());
        String token = regNode.get("token").asText();
        assertNotNull(token);

        // 2. Login User
        LoginRequest loginReq = new LoginRequest("e2e@example.com", "SecurePass1");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());

        // 3. Create Short Link
        CreateLinkRequest createReq = new CreateLinkRequest("https://example.org/destination", "e2elink", "E2E Link", null, null, false);
        MvcResult linkResult = mockMvc.perform(post("/api/links")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value("e2elink"))
                .andReturn();

        JsonNode linkNode = objectMapper.readTree(linkResult.getResponse().getContentAsString());
        long linkId = linkNode.get("id").asLong();

        // 4. Follow Short Link Public Redirect (302)
        mockMvc.perform(get("/e2elink")
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .header("Referer", "https://google.com/search"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.org/destination"))
                .andExpect(header().string("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0"));

        // 5. Synchronously flush async click tracking queue to database
        clickTrackingService.flushQueue();

        // 6. Verify Overview Analytics shows recorded click
        mockMvc.perform(get("/api/analytics/overview")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLinks").value(1))
                .andExpect(jsonPath("$.totalClicks").value(1));

        // 7. Verify QR code endpoint returns valid PNG image (magic signature bytes 0x89 'P' 'N' 'G')
        MvcResult qrResult = mockMvc.perform(get("/api/links/" + linkId + "/qr?size=256")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andReturn();

        byte[] qrBytes = qrResult.getResponse().getContentAsByteArray();
        assertTrue(qrBytes.length > 100);
        assertEquals((byte) 0x89, qrBytes[0]);
        assertEquals((byte) 'P', qrBytes[1]);
        assertEquals((byte) 'N', qrBytes[2]);
        assertEquals((byte) 'G', qrBytes[3]);
    }

    @Test
    @DisplayName("Validation failure returns 400 Bad Request with detailed fieldErrors")
    void testValidationErrorFormat() throws Exception {
        RegisterRequest invalidReq = new RegisterRequest("", "invalid-email", "short");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors.length()").value(3));
    }
}
