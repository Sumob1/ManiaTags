package com.tiertagger.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tiertagger.TierEntry;

import java.net.http.HttpClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** PvPHQ: GET https://pvphq.com/api/ranked/{player} -> { data: { ladder: {grantedTier, rating, peakRating, tr, ...} }, globalPosition } */
public final class PvpHqProvider implements TierProvider {
    private static final String BASE = "https://pvphq.com/api/ranked/";
    private static final List<Mode> MODES = List.of(
        new Mode("sword", "Sword"), new Mode("axe", "Axe"), new Mode("mace", "Mace"),
        new Mode("spear_mace", "Spear Mace"), new Mode("uhc", "UHC"), new Mode("pot", "Pot"),
        new Mode("netherite_pot", "Netherite Pot"), new Mode("smp", "SMP"), new Mode("diamond_smp", "Diamond SMP"),
        new Mode("vanilla", "Vanilla"), new Mode("ht_cart", "HT Cart"));

    @Override public String id() { return "pvphq"; }
    @Override public String name() { return "PvPHQ"; }
    @Override public int badgeColor() { return 0xD94A4A; }
    @Override public List<Mode> modes() { return MODES; }

    @Override
    public Profile fetch(HttpClient http, UUID uuid, String playerName) throws Exception {
        JsonElement el = Http.getJson(http, BASE + uuid);
        if (el == null) el = Http.getJson(http, BASE + uuid.toString().replace("-", ""));
        if (el == null && playerName != null) el = Http.getJson(http, BASE + playerName);
        if (el == null || !el.isJsonObject()) return Profile.EMPTY;
        JsonObject root = el.getAsJsonObject();
        JsonElement dataEl = root.get("data");
        if (dataEl == null || !dataEl.isJsonObject()) return Profile.EMPTY;

        Map<String, TierEntry> out = new LinkedHashMap<>();
        Map<String, Profile.Extra> extras = new LinkedHashMap<>();
        int points = 0;
        for (var e : dataEl.getAsJsonObject().entrySet()) {
            if (!e.getValue().isJsonObject()) continue;
            String key = e.getKey().toLowerCase().replace('-', '_').replace(' ', '_');
            Mode mode = mode(key);
            if (mode == null) continue;
            JsonObject o = e.getValue().getAsJsonObject();
            String granted = Http.str(o, "grantedTier");
            TierEntry t = TierEntry.parse(granted);
            if (t == null) continue;
            out.put(mode.key(), t);
            int rating = Http.num(o, "rating");
            int peak = Http.num(o, "peakRating");
            points += Http.num(o, "tr");
            extras.put(mode.key(), new Profile.Extra(rating, peak, null, 0));
        }
        long gp = root.has("globalPosition") && !root.get("globalPosition").isJsonNull()
            ? root.get("globalPosition").getAsLong() : 0;
        return new Profile(Map.copyOf(out), "", (int) gp, points, Map.copyOf(extras));
    }
}
