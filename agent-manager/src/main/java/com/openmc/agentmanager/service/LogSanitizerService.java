package com.openmc.agentmanager.service;

import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Sanitizes server log lines before they are passed to the Anthropic API.
 *
 * <p>Minecraft server logs may contain player IP addresses (e.g. in connection
 * messages such as {@code Steve[/192.168.1.100:12345] logged in}).  These are
 * redacted to avoid exposing player network information to a third-party service.
 */
@Service
public class LogSanitizerService {

    /**
     * Matches dotted-decimal IPv4-like patterns.
     * Intentionally permissive (accepts octets > 255) to err on the side of
     * redacting more rather than less — privacy is the goal here, not validation.
     */
    private static final Pattern IPV4_PATTERN =
            Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");

    /**
     * Matches common IPv6 address formats, including compressed notation.
     * Requires at least 3 colon-separated groups to avoid false-positive matches
     * on Minecraft log timestamps ({@code HH:MM:SS} has only 2 such groups).
     */
    private static final Pattern IPV6_PATTERN =
            Pattern.compile("(?:[0-9a-fA-F]{1,4}:){3,7}[0-9a-fA-F]{0,4}");

    /**
     * Return a copy of {@code line} with all IP addresses replaced by
     * {@code [IP_REDACTED]}.
     *
     * @param line a single log line (may be null)
     * @return sanitized line, or null if input was null
     */
    public String sanitize(String line) {
        if (line == null) {
            return null;
        }
        String s = IPV4_PATTERN.matcher(line).replaceAll("[IP_REDACTED]");
        s = IPV6_PATTERN.matcher(s).replaceAll("[IP_REDACTED]");
        return s;
    }
}
