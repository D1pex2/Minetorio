package dev.minetorio.core.orefield;

import java.util.Locale;

/** Big ore amounts the way Factorio's map shows them: 950, 1.5k, 190k, 3.2M, 12G. */
public final class AmountFormat {
    private static final String[] SUFFIXES = {"k", "M", "G", "T", "P", "E"};

    private AmountFormat() {}

    /**
     * Below 1000 the plain number; above, at most three significant digits with a suffix: one
     * decimal below 10 (a trailing ".0" dropped), whole numbers above. Truncated, never rounded up,
     * so 999 999 reads "999k" rather than "1000k". Negative values read as 0.
     */
    public static String compact(long n) {
        if (n < 1000) return Long.toString(Math.max(0, n));
        int i = -1;
        long unit = 1;
        while (i + 1 < SUFFIXES.length && n / unit >= 1000) {
            unit *= 1000;
            i++;
        }
        long whole = n / unit;
        if (whole >= 10) return whole + SUFFIXES[i];
        long tenths = (n % unit) / (unit / 10);
        return tenths == 0 ? whole + SUFFIXES[i] : String.format(Locale.ROOT, "%d.%d%s", whole, tenths, SUFFIXES[i]);
    }
}
