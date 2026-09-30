package com.example.urlshortener.service;

import com.example.urlshortener.config.AppProperties;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class ShortCodeGenerator {

    private static final String BASE62_ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = BASE62_ALPHABET.length();
    private static final int MIN_LENGTH = 6;

    private static final Set<String> RESERVED_WORDS = Set.of(
            "api", "admin", "swagger-ui", "swagger-ui.html", "v3", "actuator",
            "static", "login", "register", "dashboard", "health", "favicon.ico",
            "style.css", "app.js", "expired.html", "index.html", "link.html"
    );

    private final long scrambleKey;

    public ShortCodeGenerator(AppProperties appProperties) {
        this.scrambleKey = appProperties.getUrl().getScrambleKey();
    }

    public String generateCodeFromId(long id) {
        return generateCodeFromId(id, 0);
    }

    public String generateCodeFromId(long id, int attempt) {
        long scrambled = scrambleId(id + attempt * 1000000L);
        String code = encodeBase62(scrambled);
        if (isReservedWord(code)) {
            return generateCodeFromId(id, attempt + 1);
        }
        return code;
    }

    public boolean isReservedWord(String code) {
        return RESERVED_WORDS.contains(code.toLowerCase());
    }

    public long scrambleId(long id) {
        // Reversible bitwise scramble using multiplication with a large prime and XOR
        long prime = 2654435761L; // Knuth multiplicative hash constant
        long value = (id * prime) ^ scrambleKey;
        return Math.abs(value);
    }

    public String encodeBase62(long num) {
        if (num == 0) {
            return padToMinLength("0");
        }
        StringBuilder sb = new StringBuilder();
        while (num > 0) {
            int remainder = (int) (num % BASE);
            sb.append(BASE62_ALPHABET.charAt(remainder));
            num /= BASE;
        }
        return padToMinLength(sb.reverse().toString());
    }

    public long decodeBase62(String code) {
        long num = 0;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            int digit = BASE62_ALPHABET.indexOf(c);
            if (digit == -1) {
                throw new IllegalArgumentException("Invalid Base62 character: " + c);
            }
            num = num * BASE + digit;
        }
        return num;
    }

    private String padToMinLength(String str) {
        if (str.length() >= MIN_LENGTH) {
            return str;
        }
        StringBuilder sb = new StringBuilder();
        while (sb.length() + str.length() < MIN_LENGTH) {
            sb.append("0");
        }
        sb.append(str);
        return sb.toString();
    }
}
