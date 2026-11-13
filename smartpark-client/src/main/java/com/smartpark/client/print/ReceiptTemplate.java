package com.smartpark.client.print;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * The customisable text and structure of the parking receipt.
 * <p>
 * Loaded from {@code /receipt-template.properties} on the classpath, then overridden by an
 * optional {@code receipt-template.properties} in the working directory, so each PC can change
 * the title, header and footer lines, field order and labels, and date/time formats without
 * touching code. The layout itself ({@link ReceiptView}) only reads this record.
 */
public record ReceiptTemplate(String title, String subtitle, List<String> headerLines,
                              boolean slotBanner, String slotCaption,
                              List<String> fields, Map<String, String> labels,
                              DateTimeFormatter dateFormat, DateTimeFormatter timeFormat,
                              List<String> footerLines, String copyMarker) {

    public static final String FILE_NAME = "receipt-template.properties";

    /** Every field key a template may list in {@code receipt.fields}. */
    public static final List<String> FIELD_KEYS =
            List.of("ticket", "vehicle", "category", "slot", "location", "slotType", "date", "time", "operator");

    public ReceiptTemplate {
        headerLines = List.copyOf(headerLines);
        fields = List.copyOf(fields);
        labels = Map.copyOf(labels);
        footerLines = List.copyOf(footerLines);
    }

    /** Built-in template, overridden by {@code receipt-template.properties} in the working directory. */
    public static ReceiptTemplate load() {
        Properties props = new Properties();
        try (InputStream in = ReceiptTemplate.class.getResourceAsStream("/" + FILE_NAME)) {
            if (in != null) {
                try (Reader r = new java.io.InputStreamReader(in, StandardCharsets.UTF_8)) {
                    props.load(r);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read built-in " + FILE_NAME, e);
        }
        Path external = Path.of(FILE_NAME);
        if (Files.isRegularFile(external)) {
            try (Reader reader = Files.newBufferedReader(external, StandardCharsets.UTF_8)) {
                props.load(reader);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read " + external.toAbsolutePath(), e);
            }
        }
        return from(props);
    }

    /** Builds a template from properties; missing keys fall back to sensible defaults. */
    public static ReceiptTemplate from(Properties p) {
        List<String> fields = split(p.getProperty("receipt.fields", "ticket,vehicle,category,slot,date,time,operator"), ",")
                .stream().filter(FIELD_KEYS::contains).toList();
        Map<String, String> labels = new LinkedHashMap<>();
        for (String key : FIELD_KEYS) {
            labels.put(key, p.getProperty("receipt.label." + key, defaultLabel(key)).trim());
        }
        return new ReceiptTemplate(
                p.getProperty("receipt.title", "SmartPark").trim(),
                p.getProperty("receipt.subtitle", "PARKING TICKET").trim(),
                split(p.getProperty("receipt.header.lines", ""), "\\|"),
                Boolean.parseBoolean(p.getProperty("receipt.slot.banner", "true").trim()),
                p.getProperty("receipt.slot.caption", "Your parking slot").trim(),
                fields.isEmpty() ? List.of("ticket", "vehicle", "slot") : fields,
                labels,
                pattern(p.getProperty("receipt.date.pattern", "dd MMM yyyy"), "dd MMM yyyy"),
                pattern(p.getProperty("receipt.time.pattern", "HH:mm:ss"), "HH:mm:ss"),
                split(p.getProperty("receipt.footer.lines", "Keep this ticket and present it at the exit gate."), "\\|"),
                p.getProperty("receipt.copy.marker", "DUPLICATE - REPRINT").trim());
    }

    public String label(String field) {
        return labels.getOrDefault(field, defaultLabel(field));
    }

    private static String defaultLabel(String key) {
        return switch (key) {
            case "ticket" -> "Ticket ID";
            case "vehicle" -> "Vehicle No.";
            case "category" -> "Category";
            case "slot" -> "Slot";
            case "location" -> "Location";
            case "slotType" -> "Slot type";
            case "date" -> "Entry date";
            case "time" -> "Entry time";
            case "operator" -> "Operator";
            default -> key;
        };
    }

    private static List<String> split(String value, String regex) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(regex)).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    /** An invalid pattern in a hand-edited file falls back to the default instead of breaking printing. */
    private static DateTimeFormatter pattern(String value, String fallback) {
        try {
            return DateTimeFormatter.ofPattern(value.trim(), Locale.ENGLISH);
        } catch (IllegalArgumentException e) {
            return DateTimeFormatter.ofPattern(fallback, Locale.ENGLISH);
        }
    }
}
