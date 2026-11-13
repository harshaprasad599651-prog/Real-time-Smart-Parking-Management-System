package com.smartpark.client.print;

/**
 * Paper the receipt is laid out for. Sizes are in points (1/72 inch), the unit JavaFX printing
 * uses, so a layout prints at its true physical size.
 * <p>
 * Thermal receipt printers use 80 mm or 58 mm paper rolls with roughly 72 mm or 48 mm
 * printable; the receipt is laid out for that width with proportionally smaller fonts, rather
 * than shrinking an A4-sized layout. On standard paper the receipt keeps its full size and is
 * centred at the top of the page.
 */
public enum PaperSize {

    AUTO("Automatic (from the printer's paper)", 0, 0, 1.0),
    THERMAL_80MM("Thermal roll 80 mm", 80, mm(72), 0.80),
    THERMAL_58MM("Thermal roll 58 mm", 58, mm(48), 0.58),
    STANDARD("Standard paper (A4 / Letter)", 0, 300, 1.0);

    private final String displayName;
    private final int rollWidthMm;
    private final double contentWidth;
    private final double fontScale;

    PaperSize(String displayName, int rollWidthMm, double contentWidth, double fontScale) {
        this.displayName = displayName;
        this.rollWidthMm = rollWidthMm;
        this.contentWidth = contentWidth;
        this.fontScale = fontScale;
    }

    public String displayName() {
        return displayName;
    }

    /** Paper roll width in mm for thermal sizes, 0 otherwise. */
    public int rollWidthMm() {
        return rollWidthMm;
    }

    /** Width of the receipt layout in points. */
    public double contentWidth() {
        return contentWidth;
    }

    /** Multiplier for every font size and spacing of the layout. */
    public double fontScale() {
        return fontScale;
    }

    public boolean isThermal() {
        return rollWidthMm > 0;
    }

    /** {@link #AUTO} resolved from the width of the printer's paper (points). */
    public static PaperSize forPaperWidth(double paperWidthPoints) {
        double mm = paperWidthPoints / 72.0 * 25.4;
        if (mm <= 65) {
            return THERMAL_58MM;
        }
        if (mm <= 90) {
            return THERMAL_80MM;
        }
        return STANDARD;
    }

    /** Parses a setting such as {@code THERMAL_80MM}; unknown or blank values give {@link #AUTO}. */
    public static PaperSize parse(String value) {
        if (value == null || value.isBlank()) {
            return AUTO;
        }
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return AUTO;
        }
    }

    static double mm(double millimetres) {
        return millimetres / 25.4 * 72.0;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
