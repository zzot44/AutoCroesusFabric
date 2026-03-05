package acmod.autocroesus.util;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;

public final class FormatUtil {
    private static final NumberFormat COMMA_FORMAT = NumberFormat.getIntegerInstance(Locale.US);
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,##0.00");

    private FormatUtil() {
    }

    public static String commas(long number) {
        return COMMA_FORMAT.format(number);
    }

    public static String commas(double number) {
        return DECIMAL_FORMAT.format(number);
    }

    public static Integer parseIntLoose(String input) {
        if (input == null) {
            return null;
        }

        String normalized = input.replaceAll("[,_\\.]", "").trim();
        if (normalized.isEmpty()) {
            return null;
        }

        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
