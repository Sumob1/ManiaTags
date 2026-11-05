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
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** /tiercompare: two players side by side, gamemode by gamemode, with a winner for each row and in total. */
public final class CompareScreen extends Screen {
    private final Screen parent;
    private String nameA, nameB;
    private volatile UUID idA, idB;
    private volatile boolean missA, missB;
    private final Ui.TextBox boxA = new Ui.TextBox();
    private final Ui.TextBox boxB = new Ui.TextBox();
    private float scroll, scrollCur;
    private final long openT = System.nanoTime();
    private long lastFrame = System.nanoTime();
    private int px, py, pw, ph, top, bottom;

    public CompareScreen(String a, String b, Screen parent) {
        super(Text.literal("Mania Tags Compare"));
        this.parent = parent;
        for (Ui.TextBox t : new Ui.TextBox[] {boxA, boxB}) {
            t.maxLen = 16;
            t.placeholder = "Player name";
            t.filter = cp -> Character.isLetterOrDigit(cp) || cp == '_';
        }
        setA(a);
        setB(b);
    }

    private void setA(String n) {
        nameA = n;
        boxA.text = n == null ? "" : n;
        idA = null;
        missA = false;
        if (n != null && n.length() >= 3) TierService.uuidOf(n).whenComplete((u, e) -> { if (u == null) missA = true; else idA = u; });
    }

    private void setB(String n) {
        nameB = n;
        boxB.text = n == null ? "" : n;
        idB = null;
        missB = false;
        if (n != null && n.length() >= 3) TierService.uuidOf(n).whenComplete((u, e) -> { if (u == null) missB = true; else idB = u; });
    }

    private int winner(TierEntry a, TierEntry b) {
        if (a == null && b == null) return 0;
        if (a == null) return 2;
        if (b == null) return 1;
        return Integer.compare(a.sortKey(), b.sortKey()) < 0 ? 1 : Integer.compare(a.sortKey(), b.sortKey()) > 0 ? 2 : 0;
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        Ui.applyTheme();
        renderBackground(c, mx, my, delta);
        pw = Math.min(width - 20, 700);
        ph = Math.min(height - 20, 430);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        float open = Config.get().uiAnimations ? Ui.ease((now - openT) / 300_000_000f) : 1f;
        Ui.alpha = open;
        py += Math.round((1f - open) * 14f);
        top = py + 150;
        bottom = py + ph - 8;
        Ui.panelBox(c, px, py, pw, ph);
        Backgrounds.draw(c, px + 3, py + 3, pw - 6, ph - 6);
        Ui.text(c, textRenderer, Text.literal("⚔ Compare").formatted(Formatting.BOLD), px + 14, py + 12, Ui.TEXT, true);

        int half = pw / 2;
        int cxA = px + half / 2, cxB = px + half + half / 2;
        boxA.w = 110; boxA.h = 18; boxA.x = cxA - 55; boxA.y = py + 10;
        boxB.w = 110; boxB.h = 18; boxB.x = cxB - 55; boxB.y = py + 10;
        boxA.draw(c, textRenderer);
        boxB.draw(c, textRenderer);
        Ui.text(c, textRenderer, "VS", px + half - 6, py + 15, Ui.accent, true);

        UUID a = idA, b = idB;
        skin(c, a, cxA - 22, py + 34, 100);
        skin(c, b, cxB - 22, py + 34, 100);

        List<TierProvider> ls = new ArrayList<>();
        for (TierProvider p : Providers.all()) if (p.isEnabled()) ls.add(p);
        if (a == null || b == null) {
            Ui.text(c, textRenderer, missA || missB ? "A player was not found." : "Pick two players (type a name and press Enter).", px + 16, top + 4, Ui.DIM, false);
            Ui.alpha = 1f;
            return;
        }

        scrollCur += (scroll - scrollCur) * Math.min(1f, dt * 16f);
        int y = top - Math.round(scrollCur);
        int winsA = 0, winsB = 0, ptsA = 0, ptsB = 0;
        c.enableScissor(px, top - 2, px + pw, bottom);
        int idx = 0;
        for (TierProvider p : ls) {
            TierService.Result ra = TierService.get(p, a, nameA);
            TierService.Result rb = TierService.get(p, b, nameB);
            Profile pa = ra.profile, pb = rb.profile;
            ptsA += pa.points();
            ptsB += pb.points();
            float st = Config.get().uiAnimations ? Ui.ease(((now - openT) / 1e6f - idx++ * 80f) / 320f) : 1f;
            float outer = Ui.alpha;
            Ui.alpha = outer * st;
            int yy = y + Math.round((1 - st) * 14f);
            Ui.row(c, px + 10, yy, pw - 20, 22, Ui.rowB);
            Ui.text(c, textRenderer, p.name(), px + 20, yy + 7, Ui.TEXT, true);
            String sa = (pa.overall() > 0 ? "⌖ #" + pa.overall() : "-") + (pa.points() > 0 ? "  ✦ " + pa.points() : "");
            String sb = (pb.overall() > 0 ? "⌖ #" + pb.overall() : "-") + (pb.points() > 0 ? "  ✦ " + pb.points() : "");
            Ui.text(c, textRenderer, sa, cxA - textRenderer.getWidth(sa) / 2, yy + 7, pa.overall() > 0 && (pb.overall() == 0 || pa.overall() < pb.overall()) ? 0xFF55FF77 : Ui.DIM, false);
            Ui.text(c, textRenderer, sb, cxB - textRenderer.getWidth(sb) / 2, yy + 7, pb.overall() > 0 && (pa.overall() == 0 || pb.overall() < pa.overall()) ? 0xFF55FF77 : Ui.DIM, false);
            yy += 25;
            for (TierProvider.Mode m : p.modes()) {
                TierEntry ea = pa.modes().get(m.key()), eb = pb.modes().get(m.key());
                if (ea == null && eb == null) continue;
                int w = winner(ea, eb);
                if (w == 1) winsA++;
                if (w == 2) winsB++;
                Ui.row(c, px + 10, yy, pw - 20, 17, Ui.rowA);
                String icon = Icons.resolve(p.id(), m.key());
                int mid = px + half;
                if (icon != null) Ui.tex(c, Icons.gui(icon), mid - 7, yy + 1, 14, 14, 128, 128);
                String lab = m.label();
                Ui.text(c, textRenderer, lab, mid + 10, yy + 5, Ui.DIM, false);
                side(c, ea, pa.extras().get(m.key()), cxA, yy, w == 1, w == 2);
                side(c, eb, pb.extras().get(m.key()), cxB, yy, w == 2, w == 1);
                yy += 18;
            }
            y = yy + 4;
        }
        c.disableScissor();
        int total = y - (top - Math.round(scrollCur));
        scroll = Math.max(0, Math.min(Math.max(0, total - (bottom - top)), scroll));

        // result banner
        String res = winsA == winsB ? "Tied" : (winsA > winsB ? nameA : nameB) + " leads";
        String sum = nameA + " " + winsA + " : " + winsB + " " + nameB + "   |   points " + ptsA + " : " + ptsB + "   |   " + res;
        Ui.alpha = open;
        Ui.centered(c, textRenderer, "★ " + sum, px + pw / 2, py + 138, 0xFFFFD84D);
        Ui.alpha = 1f;
    }

    private void side(DrawContext c, TierEntry e, Profile.Extra x, int cx, int y, boolean win, boolean lose) {
        if (e == null) {
            Ui.text(c, textRenderer, "-", cx - 2, y + 5, Ui.DIM, false);
            return;
        }
        Text t = NametagBuilder.colorize(e.code(), e, true);
        String pk = x != null && x.peakCode() != null && !x.peakCode().equals(e.code()) ? "  ★" + x.peakCode() : "";
        int w = textRenderer.getWidth(t) + textRenderer.getWidth(pk);
        int sx = cx - w / 2;
        if (win) Ui.text(c, textRenderer, "▲", sx - 12, y + 5, 0xFF55FF77, true);
        c.drawText(textRenderer, t, sx, y + 5, lose ? 0x99FFFFFF : 0xFFFFFFFF, true);
        if (!pk.isEmpty()) Ui.text(c, textRenderer, pk, sx + textRenderer.getWidth(t), y + 5, 0xFFFFD84D, false);
    }

    private void skin(DrawContext c, UUID id, int x, int y, int h) {
        if (id == null) return;
        String url = "https://visage.surgeplay.com/full/432/" + id.toString().replace("-", "");
        ImageCache.Img img = ImageCache.get("skin/" + id, () -> Http.getBytes(TierService.HTTP, url));
        if (img == null) return;
        float s = h / (float) img.h();
        int w = Math.round(img.w() * s);
        ProfileScreen.danceSkin(c, img, x + 22 - w / 2, y, w, h, id.hashCode() % 7 * 0.1f);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        boxA.focused = boxA.contains(click.x(), click.y());
        boxB.focused = !boxA.focused && boxB.contains(click.x(), click.y());
        return boxA.focused || boxB.focused || super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        scroll -= (float) (v * 26);
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int k = input.key();
        Ui.TextBox tb = boxA.focused ? boxA : boxB.focused ? boxB : null;
        if (tb != null) {
            if (k == 257 || k == 335) {
                if (tb.text.length() >= 3) { if (tb == boxA) setA(tb.text); else setB(tb.text); }
                tb.focused = false;
            } else if (k == 259) tb.backspace();
            else if (k == 256) tb.focused = false;
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        Ui.TextBox tb = boxA.focused ? boxA : boxB.focused ? boxB : null;
        if (tb != null) {
            tb.type(input.codepoint());
            return true;
        }
        return super.charTyped(input);
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
