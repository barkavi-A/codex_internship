package com.example.urlshortener.service;

import com.example.urlshortener.entity.DeviceType;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class UserAgentParser {

    public UserAgentInfo parse(String userAgentHeader) {
        if (userAgentHeader == null || userAgentHeader.trim().isEmpty()) {
            return new UserAgentInfo("Unknown", "Unknown", DeviceType.OTHER, false);
        }

        String ua = userAgentHeader.trim();
        String uaLower = ua.toLowerCase(Locale.ROOT);

        boolean isBot = isBotUserAgent(uaLower);
        String browser = parseBrowser(ua, uaLower);
        String os = parseOs(uaLower);
        DeviceType deviceType = parseDeviceType(uaLower, isBot);

        return new UserAgentInfo(browser, os, deviceType, isBot);
    }

    private boolean isBotUserAgent(String uaLower) {
        return uaLower.contains("bot") ||
               uaLower.contains("crawler") ||
               uaLower.contains("spider") ||
               uaLower.contains("googlebot") ||
               uaLower.contains("bingbot") ||
               uaLower.contains("facebookexternalhit") ||
               uaLower.contains("slackbot") ||
               uaLower.contains("twitterbot") ||
               uaLower.contains("whatsapp") ||
               uaLower.contains("curl") ||
               uaLower.contains("python-requests") ||
               uaLower.contains("wget") ||
               uaLower.contains("postmanruntime");
    }

    private String parseBrowser(String ua, String uaLower) {
        if (uaLower.contains("edg/") || uaLower.contains("edge/")) {
            return "Edge";
        } else if (uaLower.contains("opr/") || uaLower.contains("opera")) {
            return "Opera";
        } else if (uaLower.contains("chrome") && !uaLower.contains("chromium")) {
            return "Chrome";
        } else if (uaLower.contains("firefox")) {
            return "Firefox";
        } else if (uaLower.contains("safari") && !uaLower.contains("chrome")) {
            return "Safari";
        } else if (uaLower.contains("curl")) {
            return "curl";
        } else if (uaLower.contains("postmanruntime")) {
            return "Postman";
        } else if (uaLower.contains("trident/") || uaLower.contains("msie")) {
            return "Internet Explorer";
        }
        return "Other";
    }

    private String parseOs(String uaLower) {
        if (uaLower.contains("windows")) {
            return "Windows";
        } else if (uaLower.contains("android")) {
            return "Android";
        } else if (uaLower.contains("iphone") || uaLower.contains("ipad") || uaLower.contains("ipod")) {
            return "iOS";
        } else if (uaLower.contains("mac os x") || uaLower.contains("macintosh")) {
            return "macOS";
        } else if (uaLower.contains("linux")) {
            return "Linux";
        } else if (uaLower.contains("cros")) {
            return "ChromeOS";
        }
        return "Other";
    }

    private DeviceType parseDeviceType(String uaLower, boolean isBot) {
        if (isBot) {
            return DeviceType.BOT;
        }
        if (uaLower.contains("ipad") || uaLower.contains("tablet") || uaLower.contains("playbook") || uaLower.contains("silk")) {
            return DeviceType.TABLET;
        }
        if (uaLower.contains("mobile") || uaLower.contains("iphone") || uaLower.contains("ipod") || uaLower.contains("android")) {
            return DeviceType.MOBILE;
        }
        if (uaLower.contains("windows") || uaLower.contains("macintosh") || uaLower.contains("linux")) {
            return DeviceType.DESKTOP;
        }
        return DeviceType.OTHER;
    }

    public record UserAgentInfo(
            String browser,
            String os,
            DeviceType deviceType,
            boolean isBot
    ) {}
}
