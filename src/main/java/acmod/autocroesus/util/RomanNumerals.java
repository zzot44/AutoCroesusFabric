package acmod.autocroesus.util;

import java.util.Map;

public final class RomanNumerals {
    private static final Map<Character, Integer> VALUES = Map.of(
        'I', 1,
        'V', 5,
        'X', 10,
        'L', 50,
        'C', 100,
        'D', 500,
        'M', 1000
    );

    private RomanNumerals() {
    }

    public static Integer decode(String value) {
        if (value == null || value.isBlank() || !value.matches("^[IVXLCDM]+$")) {
            return null;
        }

        int sum = 0;
        for (int i = 0; i < value.length(); i++) {
            int current = VALUES.get(value.charAt(i));
            int next = i + 1 < value.length() ? VALUES.get(value.charAt(i + 1)) : 0;
            if (current < next) {
                sum += next - current;
                i++;
                continue;
            }
            sum += current;
        }

        return sum;
    }
}
