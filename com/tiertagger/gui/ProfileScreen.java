package com.tiertagger.gui;

import com.tiertagger.Config;
import com.tiertagger.Icons;
import com.tiertagger.NametagBuilder;
import com.tiertagger.TierEntry;
import com.tiertagger.api.Http;
import com.tiertagger.api.Profile;
import com.tiertagger.api.Providers;
import com.tiertagger.api.TierProvider;
import com.tiertagger.api.TierService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** /tiers <player>: skin (can dance) plus every tier on every enabled list. Three layouts, smooth animations. */
public final class ProfileScreen extends Screen {
    private static final String[] LAYOUTS = {"COLUMNS", "CARDS", "LIST"};
    private static final String[] LAYOUT_NAMES = {"Columns", "Cards", "List"};
    static final String[] DANCES = {"NONE", "BOUNCE", "FLOSS", "SPIN", "WIGGLE", "TAKE_THE_L", "BEST_MATES", "HEADBANG", "ORANGE_JUSTICE"};
    static final String[] DANCE_NAMES = {"None", "Bounce", "Floss", "Spin", "Wiggle", "Take the L", "Best Mates", "Headbang", "Orange Justice"};

    private final Screen parent;
    private String name;
    private volatile UUID uuid;
    private volatile boolean notFound;
    private final Ui.TextBox box = new Ui.TextBox();
    private float scroll, scrollCur;
    private int contentH;
    private final long openT = System.nanoTime();
    private long animT0 = System.nanoTime();
    private long lastFrame = System.nanoTime();
    private float sel = -1f;
    private int px, py, pw, ph, top, bottom;
    private Map<String, String> seenBefore = new HashMap<>();
    private final List<Object[]> tips = new ArrayList<>();
    private int btnUpdateX, btnCompareX, btnDanceX, btnY = 0, swX;
    private static final int SW = 52;

    public ProfileScreen(String name, Screen parent) {
        super(Text.literal("Mania Tags Profile"));
        this.parent = parent;
        box.placeholder = "Player name";
        box.maxLen = 16;
        box.filter = cp -> Character.isLetterOrDigit(cp) || cp == '_';
        load(name);
    }

    private void load(String n) {
        name = n;
        uuid = null;
        notFound = false;
        scroll = 0;
        scrollCur = 0;
        animT0 = System.nanoTime();
        box.text = n;
        seenBefore = new HashMap<>(Config.get().seen);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() != null) {
            for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) {
                if (e.getProfile().name().equalsIgnoreCase(n)) {
                    uuid = e.getProfile().id();
                    name = e.getProfile().name();
                    return;
                }
            }
        }
        TierService.uuidOf(n).whenComplete((u, ex) -> {
            if (u == null || ex != null) notFound = true;
            else uuid = u;
        });
    }

    private int layoutIndex() {
        for (int i = 0; i < LAYOUTS.length; i++) if (LAYOUTS[i].equals(Config.get().profileLayout)) return i;
        return 0;
    }

    private List<TierProvider> lists() {
        List<TierProvider> out = new ArrayList<>();
        for (TierProvider p : Providers.all()) if (p.isEnabled()) out.add(p);
        return out;
    }

    private float stagger(int i) {
        if (!Config.get().uiAnimations) return 1f;
        return Ui.ease(((System.nanoTime() - animT0) / 1_000_000f - i * 70f) / 320f);
    }

    // ------------------------------------------------------------ render

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
        lastFrame = now;
        Ui.applyTheme();
        renderBackground(c, mx, my, delta);
        pw = Math.min(width - 20, 740);
        ph = Math.min(height - 20, 430);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        top = py + 40;
        bottom = py + ph - 22;
        boolean anim = Config.get().uiAnimations;
        float open = anim ? Ui.ease((now - openT) / 300_000_000f) : 1f;
        Ui.alpha = open;
        int lift = Math.round((1f - open) * 14f);
        py += lift;
        top += lift;
        bottom += lift;
        tips.clear();

        Ui.panelBox(c, px, py, pw, ph);
        Backgrounds.draw(c, px + 3, py + 3, pw - 6, ph - 6);
        Ui.text(c, textRenderer, Text.literal(name).formatted(Formatting.BOLD), px + 14, py + 15, Ui.TEXT, true);

        // header controls (right to left)
        int hy = py + 9;
        swX = px + pw - 12 - SW * 3;
        Ui.rrect(c, swX, hy, SW * 3, 20, Ui.bubble() ? 10 : 8, Ui.control);
        float target = layoutIndex();
        sel = sel < 0 || !anim ? target : sel + (target - sel) * Math.min(1f, dt * 14f);
        Ui.rrect(c, swX + Math.round(sel * SW) + 1, hy + 1, SW - 2, 18, Ui.bubble() ? 9 : 7, Ui.accent);
        for (int i = 0; i < 3; i++) Ui.centered(c, textRenderer, LAYOUT_NAMES[i], swX + i * SW + SW / 2, hy + 6, Ui.TEXT);
        btnY = hy;
        btnDanceX = swX - 8 - 84;
        button(c, btnDanceX, hy, 84, "♫ " + danceName(), mx, my);
        btnCompareX = btnDanceX - 6 - 66;
        button(c, btnCompareX, hy, 66, "⚔ Compare", mx, my);
        btnUpdateX = btnCompareX - 6 - 58;
        button(c, btnUpdateX, hy, 58, "⟳ Update", mx, my);
        box.w = 100;
        box.h = 18;
        box.x = btnUpdateX - 8 - box.w;
        box.y = hy + 1;
        box.draw(c, textRenderer);

        // legend
        Ui.text(c, textRenderer, "★ peak   ▲ new tier   ✦ rating   ⌖ world rank   HT = High   MT = Mid   LT = Low   (hover a tier)", px + 14, bottom + 8, Ui.DIM, false);

        UUID id = uuid;
        if (notFound) {
            Ui.text(c, textRenderer, "Player not found.", px + 16, top + 10, Ui.DIM, false);
            Ui.alpha = 1f;
            return;
        }
        if (id == null) {
            Ui.text(c, textRenderer, "Looking up player...", px + 16, top + 10, Ui.DIM, false);
            Ui.alpha = 1f;
            return;
        }

        scrollCur += (scroll - scrollCur) * (anim ? Math.min(1f, dt * 16f) : 1f);
        List<TierProvider> ls = lists();
        c.enableScissor(px, top, px + pw, bottom);
        int layout = layoutIndex();
        float s = scrollCur;
        int h = layout == 0 ? columns(c, id, ls, s) : layout == 1 ? cards(c, id, ls, s) : rows(c, id, ls, s);
        c.disableScissor();
        contentH = h;
        scroll = Math.max(0, Math.min(Math.max(0, h - (bottom - top)), scroll));
        Ui.alpha = 1f;

        if (my >= top && my < bottom) {
            for (Object[] t : tips) {
                if (mx >= (int) t[0] && mx < (int) t[0] + (int) t[2] && my >= (int) t[1] && my < (int) t[1] + (int) t[3]) {
                    List<Text> lines = new ArrayList<>();
                    for (String line : ((Text) t[4]).getString().split("\n")) lines.add(Text.literal(line));
                    c.drawTooltip(textRenderer, lines, mx, my);
                    break;
                }
            }
        }
    }

    private void button(DrawContext c, int x, int y, int w, String label, int mx, int my) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + 20;
        Ui.rrect(c, x, y, w, 20, Ui.bubble() ? 10 : 8, hov ? Ui.controlHover : Ui.control);
        Ui.centered(c, textRenderer, label, x + w / 2, y + 6, Ui.TEXT);
    }

    private String danceName() {
        String d = Config.get().profileDance;
        for (int i = 0; i < DANCES.length; i++) if (DANCES[i].equals(d)) return DANCE_NAMES[i];
        return "None";
    }

    // ------------------------------------------------------------ skin + dances

    /** Draws a skin image with the selected dance applied (the skin is a flat picture, so dances move the whole figure). */
    static void danceSkin(DrawContext c, ImageCache.Img img, int x, int y, int w, int h, float phase) {
        String d = Config.get().profileDance;
        float t = (System.nanoTime() / 1_000_000_000f) + phase;
        float dx = 0, dy = 0, rot = 0, sx = 1, sy = 1;
        switch (d == null ? "NONE" : d) {
            case "BOUNCE" -> { dy = -Math.abs((float) Math.sin(t * 6)) * 12; sy = 1 + (float) Math.sin(t * 12) * 0.03f; }
            case "FLOSS" -> { dx = (float) Math.sin(t * 9) * 12; rot = (float) Math.sin(t * 9) * -0.12f; dy = -Math.abs((float) Math.sin(t * 9)) * 3; }
            case "SPIN" -> sx = (float) Math.cos(t * 3.5f);
            case "WIGGLE" -> { rot = (float) Math.sin(t * 14) * 0.13f; dx = (float) Math.sin(t * 14) * 3; }
            case "TAKE_THE_L" -> { rot = (float) Math.sin(t * 3) * 0.2f - 0.1f; dy = (float) Math.abs(Math.sin(t * 3)) * 4; }
            case "BEST_MATES" -> { dy = -Math.abs((float) Math.sin(t * 5)) * 9; rot = (float) Math.sin(t * 5) * 0.06f; }
            case "HEADBANG" -> { rot = (float) Math.sin(t * 10) * 0.18f; sy = 1 + (float) Math.sin(t * 20) * 0.02f; }
            case "ORANGE_JUSTICE" -> { dx = (float) Math.sin(t * 7) * 10; dy = -Math.abs((float) Math.sin(t * 14)) * 7; rot = (float) Math.sin(t * 7) * 0.1f; sx = 1 + (float) Math.sin(t * 14) * 0.04f; }
            default -> { }
        }
        var m = c.getMatrices();
        m.pushMatrix();
        m.translate(x + w / 2f + dx, y + h + dy);
        if (rot != 0) m.rotate(rot);
        m.scale(sx, sy);
        m.translate(-w / 2f, -h);
        Ui.tex(c, img.id(), 0, 0, w, h, img.w(), img.h());
        m.popMatrix();
    }

    private ImageCache.Img skinImg(UUID id) {
        String url = "https://visage.surgeplay.com/full/432/" + id.toString().replace("-", "");
        return ImageCache.get("skin/" + id, () -> Http.getBytes(TierService.HTTP, url));
    }

    private void skin(DrawContext c, UUID id, int x, int y, int maxH) {
        ImageCache.Img img = skinImg(id);
        if (img == null) {
            Ui.text(c, textRenderer, "Loading skin...", x, y + maxH / 2, Ui.DIM, false);
            return;
        }
        float s = Math.min(maxH / (float) img.h(), 1f);
        int w = Math.round(img.w() * s), h = Math.round(img.h() * s);
        String d = Config.get().profileDance;
        if ("BEST_MATES".equals(d)) {
            danceSkin(c, img, x - w / 3, y, w, h, 0f);
            danceSkin(c, img, x + w / 3, y, w, h, 0.4f);
            if (Math.sin(System.nanoTime() / 4e8) > -0.2) Ui.text(c, textRenderer, "♥", x + w / 2 - 3, y - 2, 0xFFFF5577, true);
            return;
        }
        danceSkin(c, img, x, y, w, h, 0f);
        if ("TAKE_THE_L".equals(d)) Ui.text(c, textRenderer, Text.literal("L").formatted(Formatting.BOLD, Formatting.RED), x + w - 4, y + h / 3, 0xFFFF5555, true);
    }

    private void logo(DrawContext c, TierProvider p, int x, int y, int w, int h) {
        Identifier builtin = Identifier.of("tiertagger", "textures/logos/" + p.id() + ".png");
        var res = MinecraftClient.getInstance().getResourceManager().getResource(builtin);
        if (res.isPresent()) {
            var r = res.get();
            ImageCache.Img bi = ImageCache.get("builtin/" + p.id(), () -> {
                try (var in = r.getInputStream()) { return in.readAllBytes(); }
            });
            if (bi != null) {
                float sc = Math.min(w / (float) bi.w(), h / (float) bi.h());
                int dw = Math.round(bi.w() * sc), dh = Math.round(bi.h() * sc);
                Ui.tex(c, bi.id(), x, y + (h - dh) / 2, dw, dh, bi.w(), bi.h());
            }
            return;
        }
        Path file = Config.dir().resolve("logos").resolve(p.id() + ".png");
        String url = Config.get().logoUrls.get(p.id());
        ImageCache.Img img = null;
        if (Files.exists(file)) img = ImageCache.get("logo/" + p.id(), () -> Files.readAllBytes(file));
        else if (url != null && !url.isBlank()) img = ImageCache.get("logourl/" + p.id(), () -> Http.getBytes(TierService.HTTP, url));
        if (img != null) {
            float s = Math.min(w / (float) img.w(), h / (float) img.h());
            int dw = Math.round(img.w() * s), dh = Math.round(img.h() * s);
            Ui.tex(c, img.id(), x, y + (h - dh) / 2, dw, dh, img.w(), img.h());
            return;
        }
        Ui.rrect(c, x, y, h, h, 5, 0xFF000000 | p.badgeColor());
        Ui.centered(c, textRenderer, p.name().substring(0, 1).toUpperCase(), x + h / 2, y + h / 2 - 4, 0xFFFFFFFF);
        Ui.text(c, textRenderer, p.name(), x + h + 6, y + h / 2 - 4, Ui.TEXT, true);
    }

    private record ModeRow(TierProvider.Mode mode, TierEntry e, Profile.Extra x) {}

    private List<ModeRow> modeRows(TierService.Result r, TierProvider p) {
        List<ModeRow> out = new ArrayList<>();
        for (TierProvider.Mode m : p.modes()) {
            TierEntry e = r.profile.modes().get(m.key());
            if (e != null) out.add(new ModeRow(m, e, r.profile.extras().get(m.key())));
        }
        out.sort((a, b) -> Integer.compare(a.e().sortKey(), b.e().sortKey()));
        return out;
    }

    private int modeLine(DrawContext c, TierProvider p, TierService.Result r, ModeRow mr, int x, int y, int w) {
        String icon = Icons.resolve(p.id(), mr.mode().key());
        if (icon != null) Ui.tex(c, Icons.gui(icon), x, y, 14, 14, 128, 128);
        Text t = NametagBuilder.colorize(mr.e().code(), mr.e(), true);
        int tw = textRenderer.getWidth(t);
        int tx = x + w - tw;
        c.drawText(textRenderer, t, tx, y + 3, 0xFFFFFFFF, true);
        int cursor = tx - 4;
        int peakW = 0;
        if (mr.x() != null && mr.x().peakCode() != null && !mr.x().peakCode().equals(mr.e().code())) {
            String pk = "\u2605" + mr.x().peakCode();
            peakW = textRenderer.getWidth(pk);
            Ui.text(c, textRenderer, pk, cursor - peakW, y + 3, 0xFFFFD84D, false);
            cursor -= peakW + 4;
        }
        String key = uuid + "|" + p.id() + "|" + mr.mode().key();
        String prev = seenBefore.get(key);
        if (prev != null && !prev.equals(mr.e().code())) {
            Ui.text(c, textRenderer, "\u25B2", cursor - 8, y + 3, 0xFF55FF77, true);
        }
        int avail = (cursor - 12) - (x + 19);
        if (avail >= 26) Ui.text(c, textRenderer, Ui.trim(textRenderer, mr.mode().label(), avail), x + 19, y + 3, Ui.TEXT, false);
        tips.add(new Object[] {x, y, w, 15, NametagBuilder.tooltip(p.name(), mr.mode().label(), mr.e(), r.profile.overall(), mr.x())});
        return 17;
    }

    private int stats(DrawContext c, TierService.Result r, int x, int y, int maxW) {
        Profile pr = r.profile;
        String s = (pr.region() == null || pr.region().isEmpty() ? "" : pr.region() + "   ")
            + (pr.overall() > 0 ? "⌖ #" + pr.overall() + "   " : "")
            + (pr.points() > 0 ? "✦ " + pr.points() : "");
        Ui.text(c, textRenderer, Ui.trim(textRenderer, s.trim(), maxW), x, y, Ui.DIM, false);
        return 12;
    }

    private int status(DrawContext c, TierService.Result r, int x, int y) {
        String s = switch (r.state) {
            case LOADING -> "Loading...";
            case NONE -> "Unranked";
            case ERROR -> "Unavailable";
            default -> null;
        };
        if (s != null) Ui.text(c, textRenderer, s, x, y, Ui.DIM, false);
        return s == null ? 0 : 14;
    }

    private int columns(DrawContext c, UUID id, List<TierProvider> ls, float s) {
        int base = top - Math.round(s);
        int y = base + 6;
        skin(c, id, px + pw / 2 - 40, y, 120);
        y += 128;
        int n = Math.max(1, ls.size());
        int perRow = Math.max(1, Math.min(n, (pw - 24) / 175));
        int colW = (pw - 24) / perRow;
        int x0 = px + 12;
        float outer = Ui.alpha;
        int idx = 0;
        for (int rs = 0; rs < ls.size(); rs += perRow) {
            int rowMax = 0;
            for (int k = 0; k < perRow && rs + k < ls.size(); k++) {
                TierProvider p = ls.get(rs + k);
                TierService.Result r = TierService.get(p, id, name);
                List<ModeRow> mrs = modeRows(r, p);
                int x = x0 + k * colW + 4, w = colW - 8;
                int h = 40 + 12 + (r.state == TierService.State.READY ? 0 : 14) + mrs.size() * 17 + 8;
                float st = stagger(idx++);
                Ui.alpha = outer * st;
                int cy = y + Math.round((1 - st) * 16f);
                Ui.row(c, x, cy, w, h, Ui.rowA);
                logo(c, p, x + 8, cy + 6, w - 16, 22);
                cy += 32;
                cy += stats(c, r, x + 10, cy, w - 20);
                cy += status(c, r, x + 10, cy);
                for (ModeRow mr : mrs) cy += modeLine(c, p, r, mr, x + 10, cy, w - 20);
                rowMax = Math.max(rowMax, h);
            }
            y += rowMax + 8;
        }
        Ui.alpha = outer;
        return y + 4 - base;
    }

    private int cards(DrawContext c, UUID id, List<TierProvider> ls, float s) {
        int base = top - Math.round(s);
        int y0 = base + 6;
        skin(c, id, px + 18, y0, Math.min(210, bottom - top - 12));
        int gx = px + 140, gw = px + pw - 12 - gx, cw = gw / 2 - 4;
        int[] colY = {y0, y0};
        float outer = Ui.alpha;
        for (int i = 0; i < ls.size(); i++) {
            TierProvider p = ls.get(i);
            int col = colY[0] <= colY[1] ? 0 : 1;
            int x = gx + col * (cw + 8), cy0 = colY[col];
            TierService.Result r = TierService.get(p, id, name);
            List<ModeRow> mrs = modeRows(r, p);
            int h = 44 + (r.state == TierService.State.READY ? 0 : 14) + mrs.size() * 17 + 8;
            float st = stagger(i);
            Ui.alpha = outer * st;
            int cy = cy0 + Math.round((1 - st) * 16f);
            Ui.row(c, x, cy, cw, h, Ui.rowA);
            logo(c, p, x + 10, cy + 6, cw - 20, 22);
            int yy = cy + 30;
            yy += stats(c, r, x + 10, yy, cw - 20);
            yy += status(c, r, x + 10, yy);
            for (ModeRow mr : mrs) yy += modeLine(c, p, r, mr, x + 10, yy, cw - 20);
            colY[col] = cy0 + h + 6;
        }
        Ui.alpha = outer;
        return Math.max(colY[0], colY[1]) - base + 4;
    }

    private int rows(DrawContext c, UUID id, List<TierProvider> ls, float s) {
        int base = top - Math.round(s);
        int y = base + 6;
        skin(c, id, px + 14, y, 90);
        int x = px + 90, w = px + pw - 12 - x;
        int yy = y;
        float outer = Ui.alpha;
        int i = 0;
        for (TierProvider p : ls) {
            TierService.Result r = TierService.get(p, id, name);
            float st = stagger(i++);
            Ui.alpha = outer * st;
            int ry = yy + Math.round((1 - st) * 12f);
            Ui.row(c, x, ry, w, 24, Ui.rowB);
            logo(c, p, x + 8, ry + 3, 110, 18);
            Profile pr = r.profile;
            String sx = r.state == TierService.State.READY
                ? (pr.overall() > 0 ? "⌖ #" + pr.overall() + "  " : "") + (pr.points() > 0 ? "✦ " + pr.points() + "  " : "") + (pr.region() == null ? "" : pr.region())
                : r.state == TierService.State.LOADING ? "Loading..." : r.state == TierService.State.NONE ? "Unranked" : "Unavailable";
            Ui.text(c, textRenderer, sx, x + w - 10 - textRenderer.getWidth(sx), ry + 8, Ui.DIM, false);
            ry += 26;
            int mx2 = x + 10, my2 = ry;
            for (ModeRow mr : modeRows(r, p)) {
                String icon = Icons.resolve(p.id(), mr.mode().key());
                String extra = (mr.x() != null && mr.x().peakCode() != null && !mr.x().peakCode().equals(mr.e().code()) ? " ★" + mr.x().peakCode() : "");
                int cw = 20 + textRenderer.getWidth(mr.e().code() + extra) + 12;
                if (mx2 + cw > x + w) { mx2 = x + 10; my2 += 17; }
                if (icon != null) Ui.tex(c, Icons.gui(icon), mx2, my2, 14, 14, 128, 128);
                Text t = NametagBuilder.colorize(mr.e().code(), mr.e(), true);
                c.drawText(textRenderer, t, mx2 + 17, my2 + 3, 0xFFFFFFFF, true);
                if (!extra.isEmpty()) Ui.text(c, textRenderer, extra, mx2 + 17 + textRenderer.getWidth(t), my2 + 3, 0xFFFFD84D, false);
                String key = uuid + "|" + p.id() + "|" + mr.mode().key();
                String prev = seenBefore.get(key);
                if (prev != null && !prev.equals(mr.e().code())) Ui.text(c, textRenderer, "▲", mx2 + cw - 10, my2 + 3, 0xFF55FF77, true);
                tips.add(new Object[] {mx2, my2, cw - 4, 15, NametagBuilder.tooltip(p.name(), mr.mode().label(), mr.e(), pr.overall(), mr.x())});
                mx2 += cw;
            }
            yy = (mx2 > x + 10 ? my2 + 17 : my2) + 8 + Math.round((1 - st) * 12f) - Math.round((1 - st) * 12f);
        }
        Ui.alpha = outer;
        return Math.max(yy, y + 100) - base;
    }

    // ------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        box.focused = box.contains(mx, my);
        if (box.focused) return true;
        if (my >= btnY && my < btnY + 20) {
            if (mx >= swX && mx < swX + SW * 3) {
                Config.get().profileLayout = LAYOUTS[Math.min(2, (int) ((mx - swX) / SW))];
                Config.save();
                scroll = 0;
                animT0 = System.nanoTime();
                return true;
            }
            if (mx >= btnDanceX && mx < btnDanceX + 84) {
                int i = 0;
                for (int k = 0; k < DANCES.length; k++) if (DANCES[k].equals(Config.get().profileDance)) i = k;
                Config.get().profileDance = DANCES[(i + (Ui.shift() ? DANCES.length - 1 : 1)) % DANCES.length];
                Config.save();
                return true;
            }
            if (mx >= btnCompareX && mx < btnCompareX + 66) {
                MinecraftClient mc = MinecraftClient.getInstance();
                String self = mc.player != null ? mc.player.getNameForScoreboard() : name;
                mc.setScreen(new CompareScreen(self, name.equalsIgnoreCase(self) ? "" : name, this));
                return true;
            }
            if (mx >= btnUpdateX && mx < btnUpdateX + 58) {
                if (uuid != null) {
                    TierService.forget(uuid);
                    animT0 = System.nanoTime();
                }
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        scroll -= (float) (v * 26);
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int k = input.key();
        if (box.focused) {
            if (k == 257 || k == 335) {
                if (box.text.length() >= 3) load(box.text);
                box.focused = false;
            } else if (k == 259) box.backspace();
            else if (k == 256) box.focused = false;
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (box.focused) {
            box.type(input.codepoint());
            return true;
        }
        return super.charTyped(input);
    }

    /** Remember what we saw, so next time /tiers can mark new or promoted tiers. */
    @Override
    public void removed() {
        UUID id = uuid;
        if (id != null) {
            for (TierProvider p : lists()) {
                TierService.Result r = TierService.get(p, id, name);
                if (r.state != TierService.State.READY) continue;
                for (var e : r.profile.modes().entrySet()) Config.get().seen.put(id + "|" + p.id() + "|" + e.getKey(), e.getValue().code());
            }
            Config.save();
        }
        super.removed();
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
