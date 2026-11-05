package com.tiertagger.api;

import com.tiertagger.Config;

import java.net.http.HttpClient;
import java.util.List;
import java.util.UUID;

/** A tier list website we can query for a player's tiers. */
public interface TierProvider {

    /** One gamemode / kit on this list. {@code key} is the key used in the site's JSON. */
    record Mode(String key, String label) {}

    String id();

    String name();

    /** Badge colour (RGB) used when no logo image is available. */
    int badgeColor();

    List<Mode> modes();

    /**
     * Blocking lookup, always called from a background thread.
     *
     * @return the player's profile. {@link Profile#EMPTY} (or no modes) if unranked on this list.
     * @throws Exception on network / parse failure (the caller will retry later)
     */
    Profile fetch(HttpClient http, UUID uuid, String playerName) throws Exception;

    /** False when the provider still needs setup (e.g. no URL configured). */
    default boolean isConfigured() {
        return true;
    }

    default boolean isEnabled() {
        return Config.get().providers.getOrDefault(id(), false) && isConfigured();
    }

    default Mode mode(String key) {
        for (Mode m : modes()) if (m.key().equals(key)) return m;
        return null;
    }
}
