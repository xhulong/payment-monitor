package org.dromara.payment.security;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class WebhookLogSanitizer {
    private static final Pattern AUTHORIZATION = Pattern.compile(
        "(?i)(authorization|cookie|set-cookie|token|secret|api[-_]?key)\\s*[:=]\\s*([^\\s,;\"}]+)");
    private static final Pattern PHONE = Pattern.compile(
        "(?<!\\d)(1[3-9]\\d)\\d{4}(\\d{4})(?!\\d)");
    private static final Pattern BEARER = Pattern.compile(
        "(?i)bearer\\s+[a-z0-9._~+/=-]{8,}");

    public String sanitize(String value, int maximumLength) {
        if (value == null) {
            return null;
        }
        String result = BEARER.matcher(value).replaceAll("Bearer [REDACTED]");
        result = AUTHORIZATION.matcher(result).replaceAll("$1=[REDACTED]");
        result = PHONE.matcher(result).replaceAll("$1****$2");
        if (result.length() > maximumLength) {
            return result.substring(0, maximumLength) + "…[truncated]";
        }
        return result;
    }
}
