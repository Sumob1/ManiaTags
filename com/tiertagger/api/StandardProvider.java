package com.tiertagger.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tiertagger.TierEntry;

import java.net.http.HttpClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Provider for sites that share the MCTiers JSON shape:
 * <pre>{ "region": "NA", "points": 330, "overall": 1,
 *   "rankings": { "sword": { "tier": 3, "pos": 0, "retired": false, ... }, ... } }</pre>
 * where pos 0 = High tier, anything else = Low tier.
 * Used for MCTiers, PvPTiers and Subtiers.
 */
public final class StandardProvider implements TierProvider {

    private final String id;
    private final String name;
    private final int badge;
    private final String urlPrefix;
    private final List<Mode> modes;
    private final boolean undashedFirst;

    public StandardProvider(String id, String name, int badge, String urlPrefix, List<Mode> modes) {
        this.id = id;
        this.name = name;
        this.badge = badge;
        this.urlPrefix = urlPrefix;
        this.modes = new CopyOnWriteArrayList<>(modes);
        this.undashedFirst = true;
    }

    @Override public String id() { return id; }
    @Override public String name() { return name; }
    @Override public int badgeColor() { return badge; }
    @Override public List<Mode> modes() { return modes; }

    @Override
    public Profile fetch(HttpClient http, UUID uuid, String playerName) throws Exception {
        JsonElement rootEl = Http.getJson(http, urlPrefix + uuid.toString().replace("-", ""));
        if (rootEl == null) rootEl = Http.getJson(http, urlPrefix + uuid);
        if (rootEl == null || !rootEl.isJsonObject()) return Profile.EMPTY;
        JsonObject root = rootEl.getAsJsonObject();

        Map<String, TierEntry> out = new LinkedHashMap<>();
        Map<String, Profile.Extra> extras = new LinkedHashMap<>();
        JsonElement rankingsEl = root.get("rankings");
        if (rankingsEl != null && rankingsEl.isJsonObject()) {
            JsonObject rankings = rankingsEl.getAsJsonObject();
            for (String k : rankings.keySet()) {
                if (mode(k) == null) modes.add(new Mode(k, pretty(k)));
            }
            for (Mode m : modes) {
                JsonElement e = rankings.get(m.key());
                if (e == null || !e.isJsonObject()) continue;
                JsonObject o = e.getAsJsonObject();
                if (!o.has("tier") || o.get("tier").isJsonNull()) continue;
                int tier = o.get("tier").getAsInt();
                boolean high = o.has("pos") && !o.get("pos").isJsonNull() && o.get("pos").getAsInt() == 0;
                boolean retired = o.has("retired") && !o.get("retired").isJsonNull()
                    && o.get("retired").getAsBoolean();
                out.put(m.key(), TierEntry.of(tier, high ? 0 : 2, retired));
                String peak = null;
                if (o.has("peak_tier") && !o.get("peak_tier").isJsonNull()) {
                    boolean ph = o.has("peak_pos") && !o.get("peak_pos").isJsonNull() && o.get("peak_pos").getAsInt() == 0;
                    peak = TierEntry.of(o.get("peak_tier").getAsInt(), ph ? 0 : 2, false).code();
                }
                long since = 0;
                for (String f : new String[] {"attained", "updated", "since"}) {
                    if (o.has(f) && !o.get(f).isJsonNull()) {
                        try { since = o.get(f).getAsLong(); } catch (Exception ignored) {}
                        break;
                    }
                }
                if (since > 0 && since < 10_000_000_000L) since *= 1000;
                extras.put(m.key(), new Profile.Extra(0, 0, peak, since));
            }
        }
        return new Profile(Map.copyOf(out), Http.str(root, "region"), Http.num(root, "overall"),
            Http.num(root, "points"), Map.copyOf(extras));
    }

    private static String pretty(String k) {
        StringBuilder sb = new StringBuilder();
        for (String p : k.split("[_\\-\\s]+")) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }
}
