package com.tiertagger.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.tiertagger.Config;
import com.tiertagger.TierEntry;

import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Configurable provider for lists without a built-in API (PvPHQ, MCPVP, StatTiers).
 * The URL and JSON field names come from config/tiertagger.json -> "custom" -> id.
 * Accepts either an object keyed by gamemode, or an array of per-gamemode objects.
 * The tier can be a string like "HT3" / "MT2", or a number (+ optional pos field).
 */
public final class CustomProvider implements TierProvider {

    private final String id;
    private final String name;
    private final int badge;
    private final List<Mode> modes;

    public CustomProvider(String id, String name, int badge, List<Mode> modes) {
        this.id = id;
        this.name = name;
        this.badge = badge;
        this.modes = modes;
    }

    @Override public String id() { return id; }
    @Override public String name() { return name; }
    @Override public int badgeColor() { return badge; }
    @Override public List<Mode> modes() { return modes; }

    private Config.Custom cfg() {
        return Config.get().custom.computeIfAbsent(id, k -> new Config.Custom());
    }

    @Override
    public boolean isConfigured() {
        String u = cfg().url;
        return u != null && !u.isBlank();
    }

    @Override
    public Profile fetch(HttpClient http, UUID uuid, String playerName) throws Exception {
        Config.Custom c = cfg();
        String url = c.url
            .replace("{uuid}", uuid.toString().replace("-", ""))
            .replace("{uuid_dashed}", uuid.toString())
            .replace("{name}", URLEncoder.encode(playerName, StandardCharsets.UTF_8));

        JsonElement root = Http.getJson(http, url);
        if (root == null) return Profile.EMPTY;

        String region = "";
        int overall = 0, points = 0;
        if (root.isJsonObject()) {
            JsonObject ro = root.getAsJsonObject();
            region = Http.str(ro, c.regionField);
            overall = Http.num(ro, c.overallField);
            points = Http.num(ro, c.pointsField);
        }

        Map<String, TierEntry> out = new LinkedHashMap<>();
        JsonElement node = root;
        if (c.rankingsPath != null && !c.rankingsPath.isBlank()) {
            for (String seg : c.rankingsPath.split("\\.")) {
                if (node == null || !node.isJsonObject()) return new Profile(Map.of(), region, overall, points);
                node = node.getAsJsonObject().get(seg);
            }
        }

        if (node != null && node.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : node.getAsJsonObject().entrySet()) {
                add(out, normalise(e.getKey()), e.getValue(), c);
            }
        } else if (node != null && node.isJsonArray()) {
            JsonArray arr = node.getAsJsonArray();
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                JsonElement key = o.get(c.modeField);
                if (key == null || !key.isJsonPrimitive()) continue;
                add(out, normalise(key.getAsString()), o, c);
            }
        }
        return new Profile(Map.copyOf(out), region, overall, points);
    }

    private void add(Map<String, TierEntry> out, String key, JsonElement value, Config.Custom c) {
        if (mode(key) == null || value == null || value.isJsonNull()) return;
        TierEntry entry = null;
        if (value.isJsonPrimitive()) {
            entry = fromPrimitive(value.getAsJsonPrimitive(), null, false);
        } else if (value.isJsonObject()) {
            JsonObject o = value.getAsJsonObject();
            JsonElement t = o.get(c.tierField);
            if (t == null || !t.isJsonPrimitive()) return;
            Integer pos = null;
            if (c.posField != null && !c.posField.isBlank() && o.has(c.posField) && o.get(c.posField).isJsonPrimitive()) {
                pos = o.get(c.posField).getAsInt();
            }
            boolean retired = false;
            if (c.retiredField != null && !c.retiredField.isBlank() && o.has(c.retiredField)
                && o.get(c.retiredField).isJsonPrimitive()) {
                retired = Boolean.parseBoolean(o.get(c.retiredField).getAsString());
            }
            entry = fromPrimitive(t.getAsJsonPrimitive(), pos, retired);
        }
        if (entry != null) out.put(key, entry);
    }

    private static TierEntry fromPrimitive(JsonPrimitive p, Integer pos, boolean retired) {
        if (p.isString()) {
            TierEntry parsed = TierEntry.parse(p.getAsString());
            if (parsed == null) return null;
            return retired && !parsed.retired() ? TierEntry.of(parsed.tier(), parsed.sub(), true) : parsed;
        }
        if (p.isNumber()) {
            int tier = p.getAsInt();
            int sub = pos == null ? 9 : (pos == 0 ? 0 : 2);
            return TierEntry.of(tier, sub, retired);
        }
        return null;
    }

    private static String normalise(String s) {
        return s.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    }
}
