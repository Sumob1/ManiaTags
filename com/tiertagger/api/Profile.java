package com.tiertagger.api;

import com.tiertagger.TierEntry;

import java.util.Map;

/** Everything one tier list knows about a player. */
public record Profile(Map<String, TierEntry> modes, String region, int overall, int points, Map<String, Extra> extras) {
    /** Optional extra info per gamemode: rating, peak and when it was reached. */
    public record Extra(int rating, int peakRating, String peakCode, long since) {}

    public static final Profile EMPTY = new Profile(Map.of(), "", 0, 0);

    public Profile(Map<String, TierEntry> modes, String region, int overall, int points) {
        this(modes, region, overall, points, Map.of());
    }
}
