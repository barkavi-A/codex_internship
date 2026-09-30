package com.example.urlshortener.service;

import com.example.urlshortener.entity.DeviceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserAgentParserTest {

    private UserAgentParser parser;

    @BeforeEach
    void setUp() {
        parser = new UserAgentParser();
    }

    @Test
    @DisplayName("Parse Chrome on Windows Desktop")
    void testChromeWindows() {
        String ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36";
        UserAgentParser.UserAgentInfo info = parser.parse(ua);

        assertEquals("Chrome", info.browser());
        assertEquals("Windows", info.os());
        assertEquals(DeviceType.DESKTOP, info.deviceType());
        assertFalse(info.isBot());
    }

    @Test
    @DisplayName("Parse Safari on iPhone Mobile")
    void testSafariIPhone() {
        String ua = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1";
        UserAgentParser.UserAgentInfo info = parser.parse(ua);

        assertEquals("Safari", info.browser());
        assertEquals("iOS", info.os());
        assertEquals(DeviceType.MOBILE, info.deviceType());
        assertFalse(info.isBot());
    }

    @Test
    @DisplayName("Parse Firefox on Android Tablet")
    void testFirefoxTablet() {
        String ua = "Mozilla/5.0 (Android 13; Tablet; rv:124.0) Gecko/124.0 Firefox/124.0";
        UserAgentParser.UserAgentInfo info = parser.parse(ua);

        assertEquals("Firefox", info.browser());
        assertEquals("Android", info.os());
        assertEquals(DeviceType.TABLET, info.deviceType());
        assertFalse(info.isBot());
    }

    @Test
    @DisplayName("Parse Googlebot Crawler")
    void testGooglebot() {
        String ua = "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)";
        UserAgentParser.UserAgentInfo info = parser.parse(ua);

        assertTrue(info.isBot());
        assertEquals(DeviceType.BOT, info.deviceType());
    }

    @Test
    @DisplayName("Parse curl and python-requests command line bots")
    void testCurlAndPythonBots() {
        UserAgentParser.UserAgentInfo curlInfo = parser.parse("curl/7.68.0");
        assertTrue(curlInfo.isBot());
        assertEquals("curl", curlInfo.browser());

        UserAgentParser.UserAgentInfo pythonInfo = parser.parse("python-requests/2.28.1");
        assertTrue(pythonInfo.isBot());
    }
}
