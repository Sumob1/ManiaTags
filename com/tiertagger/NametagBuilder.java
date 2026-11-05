package com.tiertagger;

import com.tiertagger.api.Profile;
import com.tiertagger.api.Providers;
import com.tiertagger.api.TierProvider;
import com.tiertagger.api.TierService;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Builds the coloured tag text shown left / right of a player's name (nametag, tab list, chat). */
public final class NametagBuilder {
    private NametagBuilder() {}

    private static final java.util.regex.Pattern VALID_NAME = java.util.regex.Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    public static boolean isRealPlayerName(String name) {
        return name != null && VALID_NAME.matcher(name).matches();
    }

    /** Left / right tag (either may be null). Includes the spacing next to the name. */
    public record Tags(Text left, Text right) {
        static final Tags NONE = new Tags(null, null);
        public boolean isEmpty() { return left == null && right == null; }
    }

    private record Cached(int version, long bucket, Tags tags) {}

    private static final ConcurrentHashMap<UUID, Cached> CACHE = new ConcurrentHashMap<>();

    private static boolean anyAnimated(Config cfg) {
        if ("ANIMATED".equals(cfg.tagColorMode)) return true;
        for (Config.Slot sl : new Config.Slot[] {cfg.left, cfg.right, cfg.head}) if ("ANIMATED".equals(sl.colorMode)) return true;
        for (String v : cfg.tierGradient.values()) if (v != null && v.endsWith(":anim")) return true;
        return false;
    }

    public static Tags tagsFor(UUID uuid, String name) {
        Config cfg = Config.get();
        int v = TierService.version();
        long bucket = anyAnimated(cfg) ? System.currentTimeMillis() / 50 : 0;
        Cached c = CACHE.get(uuid);
        if (c != null && c.version == v && c.bucket == bucket) return c.tags;

        boolean single = "SINGLE".equals(cfg.headMode);
        Text left = tag(single ? cfg.head : cfg.left, uuid, name, true);
        Text right = single ? null : tag(cfg.right, uuid, name, false);
        Tags tags = (left == null && right == null) ? Tags.NONE : new Tags(left, right);
        CACHE.put(uuid, new Cached(v, bucket, tags));
        return tags;
    }

    /** Wraps a display name with tags (and the optional gradient name). Returns {@code original} if nothing applies. */
    public static Text wrap(Text original, UUID uuid, String name) {
        Config cfg = Config.get();
        Tags tags = tagsFor(uuid, name);
        Text base = original;
        if (cfg.nameGradient && original.getString().equals(name)) base = gradientName(name);
        if (tags.isEmpty() && base == original) return original;

        MutableText out = Text.empty();
        if (tags.left() != null) out.append(tags.left());
        out.append(base);
        if (tags.right() != null) out.append(tags.right());
        return out;
    }

    /** The name painted with the current gradient. */
    public static MutableText gradientName(String name) {
        Config cfg = Config.get();
        boolean anim = "ANIMATED".equals(cfg.tagColorMode);
        return Gradients.apply(name, Gradients.stops(cfg), anim, anim ? Gradients.phase(cfg) : 0f, false);
    }

    private static Text tag(Config.Slot slot, UUID uuid, String name, boolean isLeft) {
        if (!slot.enabled) return null;
        Pick pick = pick(slot, uuid, name);
        if (pick == null) return null;
        if (pick.entry.retired() && !slot.showRetired) return null;

        MutableText t = format(slot, pick.providerId, pick.modeKey, pick.modeLabel, pick.entry, pick.rank, pick.extra, pick.providerName);
        return isLeft
            ? Text.empty().append(t).append(Text.literal(" "))
            : Text.empty().append(Text.literal(" ")).append(t);
    }

    /** Shared formatting, also used by the GUI preview. */
    public static MutableText format(Config.Slot slot, String providerId, String modeKey, String modeLabel,
                                     TierEntry entry, int rank) {
        return format(slot, providerId, modeKey, modeLabel, entry, rank, null, null);
    }

    /** Hover text: what the tier is, rating, peak, world rank and since when. */
    public static Text tooltip(String providerName, String modeLabel, TierEntry entry, int rank, com.tiertagger.api.Profile.Extra x) {
        MutableText t = Text.literal((providerName == null ? "" : providerName + " - ") + modeLabel).formatted(Formatting.GOLD, Formatting.BOLD);
        t.append(Text.literal("\nTier: ").formatted(Formatting.GRAY)).append(Text.literal(entry.code()).formatted(Formatting.WHITE));
        if (x != null && x.peakCode() != null) t.append(Text.literal("\n\u2605 Peak: ").formatted(Formatting.GRAY)).append(Text.literal(x.peakCode()).formatted(Formatting.YELLOW));
        if (x != null && x.rating() > 0) t.append(Text.literal("\n\u2726 Rating: ").formatted(Formatting.GRAY)).append(Text.literal(x.rating() + (x.peakRating() > 0 ? " (peak " + x.peakRating() + ")" : "")).formatted(Formatting.AQUA));
        if (rank > 0) t.append(Text.literal("\n\u2316 World rank: ").formatted(Formatting.GRAY)).append(Text.literal("#" + rank).formatted(Formatting.GREEN));
        if (x != null && x.since() > 0) t.append(Text.literal("\n\u25B2 Got it: ").formatted(Formatting.GRAY)).append(Text.literal(java.time.LocalDate.ofInstant(java.time.Instant.ofEpochMilli(x.since()), java.time.ZoneId.systemDefault()).toString()).formatted(Formatting.WHITE));
        if (entry.retired()) t.append(Text.literal("\nRetired").formatted(Formatting.DARK_GRAY));
        return t;
    }

    public static MutableText format(Config.Slot slot, String providerId, String modeKey, String modeLabel,
                                     TierEntry entry, int rank, com.tiertagger.api.Profile.Extra extra, String providerName) {
        Config cfg = Config.get();
        boolean iconAfter = slot == cfg.right;
        MutableText inner = Text.empty();
        MutableText iconText = Text.empty();

        if (slot.showIcon) {
            String id = Icons.resolve(providerId, modeKey);
            if (id != null) {
                switch (cfg.iconMode) {
                    case "TIER" -> iconText.append(Icons.glyph(id, true, cfg.colorFor(entry)));
                    case "CUSTOM" -> iconText.append(Icons.glyph(id, true, cfg.colorOf("ICON")));
                    default -> iconText.append(Icons.glyph(id, false, 0xFFFFFF));
                }
                if (!iconAfter) iconText.append(Text.literal(" "));
            }
        }

        String label = (slot.showMode && modeLabel != null ? modeLabel + " " : "") + entry.code()
            + (slot.showRank && rank > 0 ? " #" + rank : "");
        MutableText body = colorize(label, entry, slot.bold, slot);
        if (iconAfter) { inner.append(body).append(Text.literal(" ")).append(iconText); }
        else { inner.append(iconText).append(body); }
        if (providerName != null) {
            final Text tip = tooltip(providerName, modeLabel, entry, rank, extra);
            inner.styled(st -> st.withHoverEvent(new HoverEvent.ShowText(tip)));
        }

        if (!slot.brackets) return inner;
        return Text.empty()
            .append(Text.literal("[").formatted(Formatting.DARK_GRAY))
            .append(inner)
            .append(Text.literal("]").formatted(Formatting.DARK_GRAY));
    }

    /** Text in the tier colour, or in the gradient if one is selected. */
    public static MutableText colorize(String label, TierEntry entry, boolean bold) {
        return colorize(label, entry, bold, null);
    }

    public static MutableText colorize(String label, TierEntry entry, boolean bold, Config.Slot slot) {
        Config cfg = Config.get();
        String mode = cfg.tagColorMode;
        String preset = cfg.gradientPreset;
        if (slot != null && slot.colorMode != null && !"GLOBAL".equals(slot.colorMode)) {
            mode = slot.colorMode;
            if (slot.gradient != null && !slot.gradient.isEmpty()) preset = slot.gradient;
        } else if (slot != null && slot.gradient != null && !slot.gradient.isEmpty()) {
            preset = slot.gradient;
        }
        String over = cfg.tierGradient.get(entry.baseCode());
        if (over != null && !over.isEmpty()) {
            boolean a = over.endsWith(":anim");
            preset = a ? over.substring(0, over.length() - 5) : over;
            mode = a ? "ANIMATED" : "GRADIENT";
        }
        if ("TIER".equals(mode)) {
            final int rgb = cfg.colorFor(entry);
            return Text.literal(label).styled(s -> s.withColor(rgb).withBold(bold));
        }
        boolean anim = "ANIMATED".equals(mode);
        return Gradients.apply(label, Gradients.stopsFor(preset, cfg), anim, anim ? Gradients.phase(cfg) : 0f, bold);
    }

    private record Pick(String providerId, String modeKey, String modeLabel, TierEntry entry, int rank,
                        com.tiertagger.api.Profile.Extra extra, String providerName) {}

    private static Pick pick(Config.Slot slot, UUID uuid, String name) {
        boolean anyList = "*".equals(slot.provider);
        List<TierProvider> providers;
        if (anyList) {
            providers = Providers.enabled();
        } else {
            TierProvider p = Providers.byId(slot.provider);
            if (p == null || !p.isEnabled()) return null;
            providers = List.of(p);
        }

        boolean best = anyList || "*best".equals(slot.mode);
        Pick result = null;
        for (TierProvider p : providers) {
            TierService.Result r = TierService.get(p, uuid, name);
            if (r.state != TierService.State.READY) continue;
            Profile prof = r.profile;
            if (best) {
                for (var e : prof.modes().entrySet()) {
                    TierProvider.Mode mode = p.mode(e.getKey());
                    if (mode == null) continue;
                    if (result == null || e.getValue().sortKey() < result.entry.sortKey()) {
                        result = new Pick(p.id(), mode.key(), mode.label(), e.getValue(), prof.overall(), prof.extras().get(mode.key()), p.name());
                    }
                }
            } else {
                TierEntry entry = prof.modes().get(slot.mode);
                TierProvider.Mode mode = p.mode(slot.mode);
                if (entry != null && mode != null) {
                    result = new Pick(p.id(), mode.key(), mode.label(), entry, prof.overall(), prof.extras().get(mode.key()), p.name());
                }
            }
        }
        return result;
    }
}
