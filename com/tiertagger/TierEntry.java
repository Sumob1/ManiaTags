package com.tiertagger;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One player's tier in one gamemode.
 *
 * @param code    display code, e.g. "HT3", "LT2", "RHT1" (R = retired)
 * @param tier    tier number (1 = best)
 * @param sub     0 = HT, 1 = MT, 2 = LT, 3 = ST, 9 = no prefix
 * @param retired whether the player is retired in this mode
 */
public record TierEntry(String code, int tier, int sub, boolean retired) {

    private static final String[] PREFIX = {"HT", "MT", "LT", "ST"};
    private static final Pattern CODE = Pattern.compile("^(R)?(HT|MT|LT|ST)?(\\d{1,2})$");

    public static TierEntry of(int tier, int sub, boolean retired) {
        String prefix = sub >= 0 && sub < PREFIX.length ? PREFIX[sub] : "T";
        return new TierEntry((retired ? "R" : "") + prefix + tier, tier, sub, retired);
    }

    /** Parses codes like "HT3", "lt2", "RHT1", "MT4". Returns null if it doesn't look like a tier. */
    public static TierEntry parse(String raw) {
        if (raw == null) return null;
        Matcher m = CODE.matcher(raw.trim().toUpperCase(Locale.ROOT));
        if (!m.matches()) return null;
        boolean retired = m.group(1) != null;
        int sub = 9;
        if (m.group(2) != null) {
            for (int i = 0; i < PREFIX.length; i++) if (PREFIX[i].equals(m.group(2))) sub = i;
        }
        return of(Integer.parseInt(m.group(3)), sub, retired);
    }

    /** Code without the retired prefix, used for colour lookup. */
    public String baseCode() {
        return retired ? code.substring(1) : code;
    }

    /** Lower = better. Active players always beat retired ones. */
    public int sortKey() {
        return (retired ? 10_000 : 0) + tier * 10 + Math.min(sub, 5);
    }
}
