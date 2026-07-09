package com.medconsult.ai.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BusinessIds {
    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Pattern DIGITS = Pattern.compile("(\\d+)$");

    private BusinessIds() {
    }

    public static String next(String prefix) {
        int random = ThreadLocalRandom.current().nextInt(1000, 10000);
        return prefix + LocalDate.now().format(BASIC_DATE) + random;
    }

    public static Long numericId(String businessId) {
        if (businessId == null || businessId.isBlank()) {
            return null;
        }
        Matcher matcher = DIGITS.matcher(businessId);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static String businessOrEmpty(String value) {
        return Objects.toString(value, "");
    }
}
