package com.tiertagger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** User settings, stored in config/tiertagger.json. */
public final class Config {

    /** One tag position (left of the name or right of the name). */
    public static final class Slot {
        public boolean enabled = true;
        /** Provider id, or "*" for "best tier across all enabled lists". */
        public String provider = "mctiers";
        /** Mode key, or "*best" for the player's highest tier in that list. */
        public String mode = "*best";
        public boolean showIcon = true;
        public boolean showMode = false;
        /** Append the player's world rank, e.g. "#6777". */
        public boolean showRank = false;
        public boolean brackets = true;
        public boolean bold = false;
        public boolean showRetired = true;
        /** GLOBAL = follow Style page; otherwise TIER / GRADIENT / ANIMATED just for this tag. */
        public String colorMode = "GLOBAL";
        /** Gradient preset for this tag (empty = the global one). */
        public String gradient = "";

        public Slot() {}

        public Slot(boolean enabled, String provider, String mode) {
            this.enabled = enabled;
            this.provider = provider;
            this.mode = mode;
        }
    }

    /** Settings for a list whose API is not built in (PvPHQ, MCPVP, StatTiers). */
    public static final class Custom {
        /** URL template. Placeholders: {uuid} (no dashes), {uuid_dashed}, {name}. */
        public String url = "";
        /** Dot path to the rankings object/array inside the JSON ("" = root). */
        public String rankingsPath = "rankings";
        /** If the rankings are an array: the field holding the gamemode key. */
        public String modeField = "mode";
        public String tierField = "tier";
        /** Optional: field with 0 = high / 1 = low. Leave empty if the tier is already like "HT3". */
        public String posField = "pos";
        public String retiredField = "retired";
        /** Top-level fields for the profile header. Leave empty if the API doesn't have them. */
        public String regionField = "region";
        public String overallField = "overall";
        public String pointsField = "points";
    }

    public static final Map<String, String> DEFAULT_COLORS = new LinkedHashMap<>();
    static {
        String[][] c = {
            {"HT1", "#FFD84D"}, {"MT1", "#EEC742"}, {"LT1", "#E0B63A"},
            {"HT2", "#D7E3F0"}, {"MT2", "#C3CDD9"}, {"LT2", "#AEB8C4"},
            {"HT3", "#E08A5A"}, {"MT3", "#CE7B52"}, {"LT3", "#B8694A"},
            {"HT4", "#C8454F"}, {"MT4", "#B33F49"}, {"LT4", "#9C3540"},
            {"HT5", "#BDBDC8"}, {"MT5", "#A6A6B1"}, {"LT5", "#8E8E99"},
            {"RETIRED", "#6B6B75"}, {"OTHER", "#AAAAAA"}, {"ICON", "#8AB4FF"},
            {"GRAD1", "#5865F2"}, {"GRAD2", "#EB459E"}, {"GRAD3", "#FEE75C"},
            {"UI_ACCENT", "#3B82F6"}, {"UI_PANEL", "#141820"}
        };
        for (String[] e : c) DEFAULT_COLORS.put(e[0], e[1]);
    }

    // ---- general
    public boolean enabled = true;
    public boolean showOwn = true;
    public int cacheMinutes = 30;
    /** If true, retired tiers use the RETIRED colour instead of their tier colour. */
    public boolean retiredUsesGray = true;

    // ---- where tags appear
    public boolean showNametag = true;
    public boolean showTab = true;
    public boolean showChat = true;

    // ---- tag style
    /** TIER = colour by tier, GRADIENT = static gradient, ANIMATED = moving gradient. */
    public String tagColorMode = "TIER";
    public String gradientPreset = "blurple";
    public int gradientSpeed = 6;
    /** Also paint the player's name with the gradient. */
    public boolean nameGradient = false;
    /** ORIGINAL = icon's own colours, TIER = tier colour, CUSTOM = the ICON colour. */
    public String iconMode = "ORIGINAL";

    // ---- profile screen
    /** COLUMNS, CARDS or LIST. */
    public String profileLayout = "COLUMNS";

    // ---- settings screen look
    public String uiTheme = "midnight";
    public boolean uiAnimations = true;
    public int uiAnimSpeed = 5;
    public int uiOpacity = 95;
    public String bgMode = "OFF";
    public String bgSource = "";
    public int bgDim = 55;
    public int cfgVersion = 0;

    public Slot left = new Slot(true, "mctiers", "*best");
    public Slot right = new Slot(true, "pvptiers", "crystal");
    /** Single tag shown when headMode is SINGLE (PvPHQ-style: just one tier by the name). */
    public Slot head = new Slot(true, "mctiers", "*best");
    /** SIDES = left + right tags, SINGLE = one tag only. */
    public String headMode = "SIDES";
    /** Per-tier gradient override: "HT3" -> "sunset" or "sunset:anim". */
    public Map<String, String> tierGradient = new LinkedHashMap<>();
    /** BUBBLE (glassy rounded) or FLAT. */
    public String uiStyle = "FLAT";
    /** DEFAULT, UNIFORM, ALT or ILLAGER. */
    public String uiFont = "DEFAULT";
    /** Skin dance on the /tiers screen. */
    public String profileDance = "NONE";
    /** Last seen tiers per player, so /tiers can show what changed. */
    public Map<String, String> seen = new LinkedHashMap<>();

    public Map<String, Boolean> providers = new LinkedHashMap<>();
    public Map<String, Custom> custom = new LinkedHashMap<>();
    /** Optional logo image URL per list id (shown in the profile screen). */
    public Map<String, String> logoUrls = new LinkedHashMap<>();
    public Map<String, String> tierColors = new LinkedHashMap<>();

    // ---------------------------------------------------------------- singleton

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config instance = new Config();

    public static Config get() {
        return instance;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("tiertagger.json");
    }

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("tiertagger");
    }

    public static void load() {
        Path f = file();
        if (Files.exists(f)) {
            try (Reader r = Files.newBufferedReader(f)) {
                Config loaded = GSON.fromJson(r, Config.class);
                if (loaded != null) instance = loaded;
            } catch (Exception e) {
                TierTaggerMod.LOGGER.warn("Could not read config, using defaults", e);
            }
        }
        instance.fillDefaults();
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(file().getParent());
            try (Writer w = Files.newBufferedWriter(file())) {
                GSON.toJson(instance, w);
            }
        } catch (IOException e) {
            TierTaggerMod.LOGGER.warn("Could not save config", e);
        }
    }

    private void fillDefaults() {
        if (left == null) left = new Slot(true, "mctiers", "*best");
        if (right == null) right = new Slot(true, "pvptiers", "crystal");
        if (providers == null) providers = new LinkedHashMap<>();
        if (custom == null) custom = new LinkedHashMap<>();
        if (logoUrls == null) logoUrls = new LinkedHashMap<>();
        if (tierColors == null) tierColors = new LinkedHashMap<>();
        if (tagColorMode == null) tagColorMode = "TIER";
        if (iconMode == null) iconMode = "ORIGINAL";
        if (bgMode == null) bgMode = "OFF";
        if (head == null) head = new Slot(true, "mctiers", "*best");
        if (headMode == null) headMode = "SIDES";
        if (tierGradient == null) tierGradient = new LinkedHashMap<>();
        if (uiStyle == null) uiStyle = "FLAT";
        if (uiFont == null) uiFont = "DEFAULT";
        if (profileDance == null) profileDance = "NONE";
        if (seen == null) seen = new LinkedHashMap<>();
        for (Slot sl : new Slot[] {left, right, head}) {
            if (sl.colorMode == null) sl.colorMode = "GLOBAL";
            if (sl.gradient == null) sl.gradient = "";
        }
        if (bgSource == null) bgSource = "";
        if (cfgVersion < 2) { iconMode = "TIER"; cfgVersion = 2; }
        if (profileLayout == null) profileLayout = "COLUMNS";
        if (gradientPreset == null) gradientPreset = "blurple";
        if (uiTheme == null) uiTheme = "midnight";
        resetProvidersIfMissing();
        for (Map.Entry<String, String> e : DEFAULT_COLORS.entrySet()) {
            tierColors.putIfAbsent(e.getKey(), e.getValue());
        }
        for (String id : new String[] {"pvphq", "mcpvp", "stattiers"}) custom.putIfAbsent(id, new Custom());
    }

    private void resetProvidersIfMissing() {
        providers.putIfAbsent("mctiers", true);
        providers.putIfAbsent("pvptiers", true);
        providers.putIfAbsent("subtiers", true);
        providers.putIfAbsent("pvphq", false);
        providers.putIfAbsent("mcpvp", false);
        providers.putIfAbsent("stattiers", false);
    }

    // ---------------------------------------------------------------- resets (used by the GUI "Reset" button)

    public void resetGeneral() {
        enabled = true;
        showOwn = true;
        cacheMinutes = 30;
        retiredUsesGray = true;
        showNametag = true;
        showTab = true;
        showChat = true;
    }

    public void resetSlot(boolean isLeft) {
        if (isLeft) left = new Slot(true, "mctiers", "*best");
        else right = new Slot(true, "pvptiers", "crystal");
    }

    public void resetStyle() {
        tagColorMode = "TIER";
        gradientPreset = "blurple";
        gradientSpeed = 6;
        nameGradient = false;
        iconMode = "TIER";
    }

    public void resetProviders() {
        providers.clear();
        resetProvidersIfMissing();
    }

    public void resetColors() {
        tierColors.clear();
        tierColors.putAll(DEFAULT_COLORS);
    }

    public void resetHead() {
        head = new Slot(true, "mctiers", "*best");
        headMode = "SIDES";
    }

    public void resetGradients() {
        tierGradient.clear();
        for (Slot sl : new Slot[] {left, right, head}) {
            sl.colorMode = "GLOBAL";
            sl.gradient = "";
        }
    }

    public void resetAppearance() {
        uiStyle = "FLAT";
        uiFont = "DEFAULT";
        uiTheme = "midnight";
        uiAnimations = true;
        uiAnimSpeed = 5;
        uiOpacity = 95;
        bgMode = "OFF";
        bgSource = "";
        bgDim = 55;
        tierColors.put("UI_ACCENT", DEFAULT_COLORS.get("UI_ACCENT"));
        tierColors.put("UI_PANEL", DEFAULT_COLORS.get("UI_PANEL"));
    }

    // ---------------------------------------------------------------- colours

    public static int parseHex(String s, int fallback) {
        if (s == null) return fallback;
        String t = s.trim();
        if (t.startsWith("#")) t = t.substring(1);
        if (t.length() != 6) return fallback;
        try {
            return Integer.parseInt(t, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static String toHex(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    public int colorOf(String key) {
        String fallbackHex = DEFAULT_COLORS.getOrDefault(key, "#AAAAAA");
        return parseHex(tierColors.getOrDefault(key, fallbackHex), parseHex(fallbackHex, 0xAAAAAA));
    }

    /** RGB colour for a tier entry (respects the "retired uses gray" option). */
    public int colorFor(TierEntry e) {
        if (e.retired() && retiredUsesGray) return colorOf("RETIRED");
        String key = e.baseCode();
        if (tierColors.containsKey(key)) return colorOf(key);
        if (e.tier() >= 1 && e.tier() <= 5) return colorOf("HT" + e.tier());
        return colorOf("OTHER");
    }
}
