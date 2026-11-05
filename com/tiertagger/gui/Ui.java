package com.tiertagger.gui;

import com.tiertagger.Config;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntPredicate;

/** Shared theme + drawing helpers for the settings and profile screens. Everything is plain fills. */
final class Ui {
    private Ui() {}

    record Theme(String key, String label, int accent, int panel) {}

    static final List<Theme> THEMES = List.of(
        new Theme("midnight", "Midnight Blue", 0x3B82F6, 0x141820),
        new Theme("violet", "Violet", 0x8B5CF6, 0x17141F),
        new Theme("emerald", "Emerald", 0x10B981, 0x121A17),
        new Theme("crimson", "Crimson", 0xEF4444, 0x1C1416),
        new Theme("sunset", "Sunset", 0xF97316, 0x1C1714),
        new Theme("slate", "Slate", 0x94A3B8, 0x16181D),
        new Theme("rose", "Rose", 0xEC4899, 0x1D1419));

    static final int TEXT = 0xFFE6E9EF;
    static final int DIM = 0xFF8A93A3;

    /** Multiplies every colour's alpha (used for fade-in animations). */
    static float alpha = 1f;

    static int accent, panel, sidebar, rowA, rowB, control, controlHover, activeBg, border;

    static void applyTheme() {
        Config c = Config.get();
        int p = c.colorOf("UI_PANEL");
        int ac = c.colorOf("UI_ACCENT");
        int pa = Math.round(255 * Math.max(40, Math.min(100, c.uiOpacity)) / 100f);
        accent = 0xFF000000 | ac;
        panel = (pa << 24) | p;
        int ba = Backgrounds.active() ? 0xA8 : 0xFF;
        sidebar = (Backgrounds.active() ? 0xC0 : 0xFF) << 24 | shade(p, 7);
        rowA = (ba << 24) | shade(p, 10);
        rowB = (ba << 24) | shade(p, 16);
        control = 0xFF000000 | shade(p, 30);
        controlHover = 0xFF000000 | shade(p, 48);
        border = 0xFF000000 | shade(p, 40);
        activeBg = 0xFF000000 | mix(shade(p, 10), ac, 0.22f);
    }

    static int shade(int rgb, int d) {
        int r = Math.max(0, Math.min(255, ((rgb >> 16) & 255) + d));
        int g = Math.max(0, Math.min(255, ((rgb >> 8) & 255) + d));
        int b = Math.max(0, Math.min(255, (rgb & 255) + d));
        return (r << 16) | (g << 8) | b;
    }

    /** Blend two RGB colours (no alpha). */
    static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    static float ease(float t) {
        t = Math.max(0f, Math.min(1f, t));
        float u = 1f - t;
        return 1f - u * u * u;
    }

    static int a(int argb) {
        int al = Math.round(((argb >>> 24) & 255) * alpha);
        return (al << 24) | (argb & 0xFFFFFF);
    }

    // ------------------------------------------------------------------ drawing

    static void fill(DrawContext c, int x1, int y1, int x2, int y2, int col) {
        c.fill(x1, y1, x2, y2, a(col));
    }

    static void rrect(DrawContext c, int x, int y, int w, int h, int r, int col) {
        col = a(col);
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            c.fill(x + inset, y + i, x + w - inset, y + i + 1, col);
            c.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, col);
        }
        c.fill(x, y + r, x + w, y + h - r, col);
    }
    static boolean shift() {
        long h = net.minecraft.client.MinecraftClient.getInstance().getWindow().getHandle();
        return org.lwjgl.glfw.GLFW.glfwGetKey(h, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == 1
                || org.lwjgl.glfw.GLFW.glfwGetKey(h, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) == 1;
    }
    static boolean bubble() { return "BUBBLE".equals(Config.get().uiStyle); }

    /** Applies the chosen UI font (default, uniform, alt, illager). */
    static MutableText styled(String s) {
        String f = Config.get().uiFont;
        MutableText t = Text.literal(s == null ? "" : s);
        Identifier id = switch (f == null ? "" : f) {
            case "UNIFORM" -> Identifier.of("minecraft", "uniform");
            case "ALT" -> Identifier.of("minecraft", "alt");
            case "ILLAGER" -> Identifier.of("minecraft", "illageralt");
            default -> null;
        };
        return id == null ? t : t.styled(st -> st.withFont(new StyleSpriteSource.Font(id)));
    }
    static void rrectRaw(DrawContext c, int x, int y, int w, int h, int r, int col) {
        float keep = alpha;
        alpha = 1f;
        rrect(c, x, y, w, h, r, col);
        alpha = keep;
    }
    static void text(DrawContext c, TextRenderer tr, String s, int x, int y, int col, boolean shadow) {
        c.drawText(tr, styled(s), x, y, a(col), shadow);
    }

    /** A list row: plain strip, or a glassy rounded bubble with an outline in BUBBLE style. */
    static void row(DrawContext c, int x, int y, int w, int h, int col) {
        if (!bubble()) { fill(c, x, y, x + w, y + h, col); return; }
        rrect(c, x + 2, y + 1, w - 4, h - 2, 9, border);
        rrect(c, x + 3, y + 2, w - 6, h - 4, 8, col);
    }

    static void panelBox(DrawContext c, int x, int y, int w, int h) {
        if (bubble()) {
            rrect(c, x - 1, y - 1, w + 2, h + 2, 20, border);
            rrect(c, x, y, w, h, 19, panel);
        } else {
            rrect(c, x, y, w, h, 10, panel);
        }
    }

    static void text(DrawContext c, TextRenderer tr, Text t, int x, int y, int col, boolean shadow) {
        c.drawText(tr, t, x, y, a(col), shadow);
    }

    static void centered(DrawContext c, TextRenderer tr, String s, int cx, int y, int col) {
        Text t = styled(s);
        c.drawText(tr, t, cx - tr.getWidth(t) / 2, y, a(col), false);
    }

    static String trim(TextRenderer tr, String s, int maxW) {
        if (s == null) return "";
        if (tr.getWidth(styled(s)) <= maxW) return s;
        String ell = "..";
        while (!s.isEmpty() && tr.getWidth(styled(s + ell)) > maxW) s = s.substring(0, s.length() - 1);
        return s + ell;
    }

    static void chevron(DrawContext c, int cx, int y, int col) {
        col = a(col);
        for (int i = 0; i < 4; i++) {
            c.fill(cx - 4 + i, y + i, cx - 3 + i, y + i + 1, col);
            c.fill(cx + 3 - i, y + i, cx + 4 - i, y + i + 1, col);
        }
    }

    /** Draws a whole texture scaled into w x h. */
    static void tex(DrawContext c, Identifier id, int x, int y, int w, int h, int texW, int texH) {
        c.drawTexture(RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, w, h, texW, texH, texW, texH);
    }

    // ------------------------------------------------------------------ text box

    static final class TextBox {
        String text = "";
        String placeholder = "";
        boolean focused;
        int maxLen = 32;
        IntPredicate filter = cp -> cp >= 32 && cp != 127;
        Consumer<String> onChange = s -> {};
        int x, y, w, h;

        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }

        void type(int cp) {
            if (text.length() < maxLen && filter.test(cp)) {
                text += new String(Character.toChars(cp));
                onChange.accept(text);
            }
        }

        void backspace() {
            if (!text.isEmpty()) {
                text = text.substring(0, text.length() - 1);
                onChange.accept(text);
            }
        }

        void draw(DrawContext c, TextRenderer tr) {
            rrect(c, x, y, w, h, 6, focused ? accent : border);
            rrect(c, x + 1, y + 1, w - 2, h - 2, 5, control);
            int ty = y + (h - 8) / 2;
            if (text.isEmpty() && !focused) {
                Ui.text(c, tr, placeholder, x + 8, ty, DIM, false);
            } else {
                String shown = trim(tr, text, w - 16);
                Ui.text(c, tr, shown, x + 8, ty, TEXT, false);
                if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
                    int cx = x + 8 + tr.getWidth(shown);
                    fill(c, cx, ty - 1, cx + 1, ty + 9, TEXT);
                }
            }
        }
    }
}
