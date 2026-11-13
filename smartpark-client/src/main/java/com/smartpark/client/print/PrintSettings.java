package com.smartpark.client.print;

import java.util.Properties;

/**
 * Per-PC printer preferences from {@code smartpark-client.properties}:
 * <pre>
 *   smartpark.printer.name=EPSON TM-T20III Receipt   (empty = Windows default printer)
 *   smartpark.printer.paper=AUTO                       (AUTO, THERMAL_80MM, THERMAL_58MM, STANDARD)
 * </pre>
 * The operator can still choose another printer and paper in the receipt preview.
 */
public record PrintSettings(String printerName, PaperSize paper) {

    public static PrintSettings from(Properties props) {
        String name = props.getProperty("smartpark.printer.name", "").trim();
        return new PrintSettings(name.isEmpty() ? null : name, PaperSize.parse(props.getProperty("smartpark.printer.paper")));
    }
}
