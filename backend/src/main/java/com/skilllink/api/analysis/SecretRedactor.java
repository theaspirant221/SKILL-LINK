package com.skilllink.api.analysis;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SecretRedactor {
    private static final Pattern PRIVATE_KEY = Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]+?-----END [A-Z ]*PRIVATE KEY-----");
    private static final Pattern ASSIGNMENT_SECRET = Pattern.compile("(?i)(\\b(?:api[_-]?key|secret|password|passwd|token|access[_-]?key|client[_-]?secret|jwt[_-]?secret)\\b\\s*[:=]\\s*[\\\"']?)([A-Za-z0-9_./+=\\-]{8,})");
    private static final Pattern JWT = Pattern.compile("\\beyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\b");

    public Redacted redact(String input) {
        if (input == null || input.isBlank()) return new Redacted(input == null ? "" : input, 0);
        int[] count = {0};
        String result = PRIVATE_KEY.matcher(input).replaceAll(match -> { count[0]++; return "[REDACTED_PRIVATE_KEY]"; });
        result = replace(ASSIGNMENT_SECRET, result, count, "[REDACTED_SECRET]");
        result = JWT.matcher(result).replaceAll(match -> { count[0]++; return "[REDACTED_JWT]"; });
        return new Redacted(result, count[0]);
    }

    private String replace(Pattern pattern, String input, int[] count, String replacement) {
        Matcher matcher = pattern.matcher(input);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) { count[0]++; matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group(1) + replacement)); }
        matcher.appendTail(out);
        return out.toString();
    }
    public record Redacted(String content, int count) {}
}
