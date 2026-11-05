package com.tiertagger;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.List;

/** Static and animated gradient text (Discord-style gradient names). */
public final class Gradients {
    private Gradients() {}

    public record Preset(String key, String label, int[] colors) {}

    /** A preset with null colours means "use the custom GRAD1..GRAD3 colours". */
    public static final List<Preset> PRESETS = List.of(
        new Preset("blurple", "Blurple Pink", new int[] {0x5865F2, 0xEB459E}),
        new Preset("sunset", "Sunset", new int[] {0xFF512F, 0xF09819, 0xDD2476}),
        new Preset("ocean", "Ocean", new int[] {0x2193B0, 0x6DD5ED}),
        new Preset("aurora", "Aurora", new int[] {0x00C9FF, 0x92FE9D}),
        new Preset("fire", "Fire", new int[] {0xF12711, 0xF5AF19}),
        new Preset("gold", "Gold", new int[] {0xF7971E, 0xFFD200}),
        new Preset("candy", "Candy", new int[] {0xFF9A9E, 0xA18CD1}),
        new Preset("rainbow", "Rainbow", new int[] {0xFF0000, 0xFFA500, 0xFFFF00, 0x00C800, 0x00BFFF, 0x8A2BE2}),
        new Preset("custom", "Custom (Colors page)", null));

    public static int[] stops(Config cfg) {
        for (Preset p : PRESETS) {
            if (!p.key().equals(cfg.gradientPreset)) continue;
            if (p.colors() != null) return p.colors();
            return new int[] {cfg.colorOf("GRAD1"), cfg.colorOf("GRAD2"), cfg.colorOf("GRAD3")};
        }
        return PRESETS.get(0).colors();
    }

    public static int[] stopsFor(String key, Config cfg) {
        for (Preset p : PRESETS) {
            if (!p.key().equals(key)) continue;
            if (p.colors() != null) return p.colors();
            return new int[] {cfg.colorOf("GRAD1"), cfg.colorOf("GRAD2"), cfg.colorOf("GRAD3")};
        }
        return stops(cfg);
    }

    public static int sample(int[] s, float t) {
        if (s.length == 1) return s[0];
        t = Math.max(0f, Math.min(1f, t));
        float f = t * (s.length - 1);
        int i = Math.min((int) f, s.length - 2);
        float lt = f - i;
        int a = s[i], b = s[i + 1];
        int r = Math.round(((a >> 16) & 255) * (1 - lt) + ((b >> 16) & 255) * lt);
        int g = Math.round(((a >> 8) & 255) * (1 - lt) + ((b >> 8) & 255) * lt);
        int bl = Math.round((a & 255) * (1 - lt) + (b & 255) * lt);
        return (r << 16) | (g << 8) | bl;
    }

    /** 0..1, loops forever. Faster with a higher gradientSpeed. */
    public static float phase(Config cfg) {
        long period = (long) (9000.0 / Math.max(1, cfg.gradientSpeed));
        return (System.currentTimeMillis() % period) / (float) period;
    }

    /** Colours each character of {@code text}. Animated gradients slide smoothly and loop. */
    public static MutableText apply(String text, int[] stops, boolean animated, float phase, boolean bold) {
        MutableText out = Text.empty();
        int n = text.length();
        for (int i = 0; i < n; i++) {
            float t;
            if (animated) {
                float u = (phase + i * 0.22f) % 1f;
                t = 1f - Math.abs(2f * u - 1f);
            } else {
                t = n <= 1 ? 0f : i / (float) (n - 1);
            }
            final int rgb = sample(stops, t);
            out.append(Text.literal(String.valueOf(text.charAt(i))).styled(s -> s.withColor(rgb).withBold(bold)));
        }
        return out;
    }
}
