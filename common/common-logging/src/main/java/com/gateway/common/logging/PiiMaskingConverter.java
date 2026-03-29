package com.gateway.common.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Logback converter that masks Personally Identifiable Information (PII) in log messages.
 *
 * <p>Masking rules (per Section 10.4 — Logging Standards):
 * <ul>
 *   <li>Email: {@code j***@example.com} — keep first character + domain</li>
 *   <li>Phone: {@code +1-***-***-4567} — keep last 4 digits</li>
 *   <li>API key: {@code gw_live_****c3d4} — keep prefix + last 4 characters</li>
 * </ul>
 *
 * <p>Usage in logback-spring.xml:
 * <pre>{@code
 * <conversionRule conversionWord="maskedMsg"
 *     converterClass="com.gateway.common.logging.PiiMaskingConverter" />
 *
 * <pattern>%d{HH:mm:ss.SSS} %-5level [%thread] %logger{36} - %maskedMsg%n</pattern>
 * }</pre>
 */
public class PiiMaskingConverter extends ClassicConverter {

    // Email: local-part@domain  — captures first char of local part, rest of local part, and domain
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("([A-Za-z0-9])[A-Za-z0-9._%+\\-]*@([A-Za-z0-9.\\-]+\\.[A-Za-z]{2,})");

    // Phone numbers in various formats:
    //   +1-234-567-8901, +1 234 567 8901, (234) 567-8901, 234-567-8901, +12345678901
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(\\+?\\d{1,3}[\\s.-]?)?(\\(?\\d{2,4}\\)?[\\s.-]?)(\\d{3,4}[\\s.-]?)(\\d{4})");

    // API key: prefix like "gw_live_" or "gw_test_" followed by hex/alphanum chars
    private static final Pattern API_KEY_PATTERN =
            Pattern.compile("(gw_(?:live|test|sandbox)_)([A-Za-z0-9]{4,})");

    @Override
    public String convert(ILoggingEvent event) {
        String message = event.getFormattedMessage();
        if (message == null) {
            return "";
        }

        message = maskApiKeys(message);
        message = maskEmails(message);
        message = maskPhones(message);

        return message;
    }

    /**
     * Masks emails: {@code john.doe@example.com} becomes {@code j***@example.com}.
     */
    static String maskEmails(String input) {
        Matcher matcher = EMAIL_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String firstChar = matcher.group(1);
            String domain = matcher.group(2);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(firstChar + "***@" + domain));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Masks phone numbers: keeps only the last 4 digits.
     * {@code +1-234-567-8901} becomes {@code ***-***-8901}.
     */
    static String maskPhones(String input) {
        Matcher matcher = PHONE_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String lastFour = matcher.group(4);
            matcher.appendReplacement(sb, Matcher.quoteReplacement("***-***-" + lastFour));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Masks API keys: {@code gw_live_abcd1234c3d4} becomes {@code gw_live_****c3d4}.
     */
    static String maskApiKeys(String input) {
        Matcher matcher = API_KEY_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String prefix = matcher.group(1);   // e.g. "gw_live_"
            String secret = matcher.group(2);   // e.g. "abcd1234c3d4"
            String lastFour = secret.length() >= 4
                    ? secret.substring(secret.length() - 4)
                    : secret;
            matcher.appendReplacement(sb, Matcher.quoteReplacement(prefix + "****" + lastFour));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
