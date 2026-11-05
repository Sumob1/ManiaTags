package com.tiertagger.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tiertagger.TierEntry;

import java.net.http.HttpClient;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** StatTiers: one leaderboard JSON ({players:[{username, region, points, tiers, retired_tiers, peak_tiers, overall_rank}]}). */
public final class StatTiersProvider implements TierProvider {
    private static final String URL = "https://shrill-mode-ce7d.urbruh122.workers.dev/leaderboard";
    private static final long TTL = 5 * 60_000L;

    private final List<Mode> modes;
    private volatile Map<String, JsonObject> byName = Map.of();
    private volatile long loadedAt;

    public StatTiersProvider(List<Mode> modes) { this.modes = modes; }

    @Override public String id() { return "stattiers"; }
    @Override public String name() { return "StatTiers"; }
    @Override public int badgeColor() { return 0x9B6BF2; }
    @Override public List<Mode> modes() { return modes; }

    private synchronized void load(HttpClient http) throws Exception {
        if (System.currentTimeMillis() - loadedAt < TTL && !byName.isEmpty()) return;
        JsonElement el = Http.getJson(http, URL);
        if (el == null || !el.isJsonObject()) throw new Exception("bad leaderboard");
        JsonElement arr = el.getAsJsonObject().get("players");
        if (arr == null || !arr.isJsonArray()) throw new Exception("no players");
        Map<String, JsonObject> m = new HashMap<>();
        for (JsonElement p : (JsonArray) arr) {
            if (!p.isJsonObject()) continue;
            String u = Http.str(p.getAsJsonObject(), "username");
            if (u != null && !u.isEmpty()) m.put(u.toLowerCase(Locale.ROOT), p.getAsJsonObject());
        }
        byName = m;
        loadedAt = System.currentTimeMillis();
    }

    static TierEntry parse(String s, boolean retired) {
        if (s == null) return null;
        String l = s.toLowerCase(Locale.ROOT);
        int sub = l.contains("high") ? 0 : l.contains("mid") ? 1 : l.contains("low") ? 2 : -1;
        int tier = 0;
        for (String part : l.split("[^0-9]+")) if (!part.isEmpty()) tier = Integer.parseInt(part);
        if (tier <= 0) return TierEntry.parse(s);
        return TierEntry.of(tier, sub < 0 ? 9 : sub, retired);
    }

    @Override
    public Profile fetch(HttpClient http, UUID uuid, String playerName) throws Exception {
        load(http);
        JsonObject p = playerName == null ? null : byName.get(playerName.toLowerCase(Locale.ROOT));
        if (p == null) return Profile.EMPTY;
        JsonObject retired = p.has("retired_tiers") && p.get("retired_tiers").isJsonObject() ? p.getAsJsonObject("retired_tiers") : new JsonObject();
        Map<String, TierEntry> out = new LinkedHashMap<>();
        for (String group : new String[] {"tiers", "peak_tiers"}) {
            if (!p.has(group) || !p.get(group).isJsonObject()) continue;
            for (var e : p.getAsJsonObject(group).entrySet()) {
                if (!e.getValue().isJsonPrimitive()) continue;
                boolean r = retired.has(e.getKey()) && retired.get(e.getKey()).isJsonPrimitive() && retired.get(e.getKey()).getAsBoolean();
                TierEntry t = parse(e.getValue().getAsString(), r);
                if (t != null) out.put(e.getKey().toLowerCase(Locale.ROOT), t);
            }
        }
        return new Profile(Map.copyOf(out), Http.str(p, "region"), Http.num(p, "overall_rank"), Http.num(p, "points"));
    }
}
