package com.tiertagger.gui;

import com.tiertagger.Config;
import com.tiertagger.Gradients;
import com.tiertagger.NametagBuilder;
import com.tiertagger.TierEntry;
import com.tiertagger.api.CustomProvider;
import com.tiertagger.api.Providers;
import com.tiertagger.api.TierProvider;
import com.tiertagger.api.TierService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Settings: sidebar pages, search, reset, animated tabs, themes, colour wheel + hex editor. */
public final class SettingsScreen extends Screen {

    private enum Page {
        GENERAL("General", "\u2699"), LEFT("Left Tag", "\u25C0"), RIGHT("Right Tag", "\u25B6"), HEAD("Above Head", "\u2660"),
        STYLE("Style", "\u2726"), GRADIENTS("Gradients", "\u2738"), LISTS("Tier Lists", "\u2630"),
        COLORS("Colors", "\u2764"), APPEARANCE("Appearance", "\u2740");
        final String title;
        final String emoji;
        Page(String title, String emoji) { this.title = title; this.emoji = emoji; }
    }

    private enum Drag { NONE, SLIDER, WHEEL, BAR }

    private record Choice(String key, String label) {}

    private static final class Row {
        enum Kind { TOGGLE, SLIDER, CHOICE, BUTTON, TEXT }

        final Kind kind;
        final String section;
        final String label;
        Supplier<String> desc;
        BooleanSupplier getB;
        Consumer<Boolean> setB;
        int min, max;
        IntSupplier getI;
        IntConsumer setI;
        Supplier<List<Choice>> options;
        Supplier<String> getKey;
        Consumer<String> setKey;
        String buttonLabel;
        Runnable action;
        Ui.TextBox box;
        Runnable reset;
        float anim = -1f;
        int y, h, cx, cy, cw, ch;

        Row(Kind kind, String section, String label, Supplier<String> desc) {
            this.kind = kind;
            this.section = section;
            this.label = label;
            this.desc = desc;
        }

        static Row toggle(String sec, String label, Supplier<String> desc, BooleanSupplier get, Consumer<Boolean> set) {
            Row r = new Row(Kind.TOGGLE, sec, label, desc);
            r.getB = get;
            r.setB = set;
            return r;
        }

        static Row slider(String sec, String label, Supplier<String> desc, int min, int max,
                          IntSupplier get, IntConsumer set) {
            Row r = new Row(Kind.SLIDER, sec, label, desc);
            r.min = min;
            r.max = max;
            r.getI = get;
            r.setI = set;
            return r;
        }

        static Row choice(String sec, String label, Supplier<String> desc, Supplier<List<Choice>> options,
                          Supplier<String> get, Consumer<String> set) {
            Row r = new Row(Kind.CHOICE, sec, label, desc);
            r.options = options;
            r.getKey = get;
            r.setKey = set;
            return r;
        }

        static Row button(String sec, String label, Supplier<String> desc, String buttonLabel, Runnable action) {
            Row r = new Row(Kind.BUTTON, sec, label, desc);
            r.buttonLabel = buttonLabel;
            r.action = action;
            return r;
        }

        static Row text(String sec, String label, Supplier<String> desc, Supplier<String> get, Consumer<String> set) {
            Row r = new Row(Kind.TEXT, sec, label, desc);
            r.box = new Ui.TextBox();
            r.box.maxLen = 300;
            r.box.text = get.get();
            r.box.onChange = set;
            return r;
        }

        boolean matches(String q) {
            if (label.toLowerCase(Locale.ROOT).contains(q) || section.toLowerCase(Locale.ROOT).contains(q)) return true;
            String d = desc == null ? null : desc.get();
            return d != null && d.toLowerCase(Locale.ROOT).contains(q);
        }
    }

    // ------------------------------------------------------------------ state

    private final Screen parent;
    private Page page = Page.GENERAL;
    private final EnumMap<Page, List<Row>> pages = new EnumMap<>(Page.class);
    private final Ui.TextBox search = new Ui.TextBox();
    private final Ui.TextBox hex = new Ui.TextBox();
    private float scroll;
    private boolean dirty;

    private int px, py, pw, ph, sbX, sbY, sbW, sbH, cX, cW, hdrY, ctTop, ctBottom;
    private int resetX, resetY, resetW = 46, resetH = 18;

    // animation
    private long openT = System.nanoTime();
    private long pageT0 = System.nanoTime();
    private long lastFrame = System.nanoTime();
    private float hlY = -1f;
    private float dt;

    private Drag drag = Drag.NONE;
    private Row dragRow;

    private Row ddRow;
    private List<Choice> ddOptions = List.of();
    private int ddScroll, ddX, ddY, ddW, ddH;
    private static final int DD_ITEM = 16;
    private static final int DD_MAX = 8;

    private static final int WHEEL_R = 60;
    private static final int WHEEL_CELL = 3;
    private static final int WHEEL_CELLS = WHEEL_R * 2 / WHEEL_CELL;
    private static final int BAR_W = 120;
    private static final int BAR_H = 10;
    private final int[] wheel = new int[WHEEL_CELLS * WHEEL_CELLS];
    private float wheelVal = -1f;
    private String selKey;
    private float hue, sat, val = 1f;
    private int wheelCX, wheelCY, barX, barY;
    private int defX, defY, defW = 70, defH = 16;
    private final List<Object[]> swatches = new ArrayList<>();

    public SettingsScreen(Screen parent) {
        super(Text.literal("Mania Tags"));
        this.parent = parent;
        search.placeholder = "Search";
        search.maxLen = 24;
        search.onChange = s -> scroll = 0;
        hex.maxLen = 7;
        hex.filter = cp -> cp == '#' || Character.digit(cp, 16) >= 0;
        hex.onChange = this::onHexChanged;
        buildPages();
    }

    // ------------------------------------------------------------------ pages

    private static List<Choice> providerChoices() {
        List<Choice> out = new ArrayList<>();
        out.add(new Choice("*", "All lists (best tier)"));
        for (TierProvider p : Providers.all()) out.add(new Choice(p.id(), p.name() + (p.isEnabled() ? "" : " (off)")));
        return out;
    }

    private static List<Choice> modeChoices(String providerId) {
        List<Choice> out = new ArrayList<>();
        out.add(new Choice("*best", "Highest tier"));
        TierProvider p = Providers.byId(providerId);
        if (p != null) for (TierProvider.Mode m : p.modes()) out.add(new Choice(m.key(), m.label()));
        return out;
    }

    private void buildPages() {
        String g = "General";
        List<Row> general = new ArrayList<>();
        general.add(Row.toggle(g, "Show tier tags", () -> "Master switch for all tags", () -> Config.get().enabled, v -> Config.get().enabled = v));
        general.add(Row.toggle(g, "Show on my own name", () -> "Your nametag in third person", () -> Config.get().showOwn, v -> Config.get().showOwn = v));
        general.add(Row.toggle(g, "In nametags", () -> "Tags above players' heads", () -> Config.get().showNametag, v -> Config.get().showNametag = v));
        general.add(Row.toggle(g, "In the tab list", () -> "Tags in the player list", () -> Config.get().showTab, v -> Config.get().showTab = v));
        general.add(Row.toggle(g, "In chat", () -> "Tags next to names in chat messages", () -> Config.get().showChat, v -> Config.get().showChat = v));
        general.add(Row.toggle(g, "Gray out retired tiers", () -> "Retired tiers use the Retired colour", () -> Config.get().retiredUsesGray, v -> Config.get().retiredUsesGray = v));
        general.add(Row.slider(g, "Cache time (min)", () -> "How long a player's tiers are remembered", 1, 120, () -> Config.get().cacheMinutes, v -> Config.get().cacheMinutes = v));
        general.add(Row.choice(g, "Profile layout", () -> "Layout of the /tiers screen", () -> List.of(
            new Choice("COLUMNS", "Columns"), new Choice("CARDS", "Cards"), new Choice("LIST", "List")),
            () -> Config.get().profileLayout, v -> Config.get().profileLayout = v));
        general.add(Row.choice(g, "Skin dance (/tiers)", () -> "Your skin dances on the profile screen", () -> List.of(
            new Choice("NONE", "None"), new Choice("BOUNCE", "Bounce"), new Choice("FLOSS", "Floss"), new Choice("SPIN", "Spin"),
            new Choice("WIGGLE", "Wiggle"), new Choice("TAKE_THE_L", "Take the L"), new Choice("BEST_MATES", "Best Mates"),
            new Choice("HEADBANG", "Headbang"), new Choice("ORANGE_JUSTICE", "Orange Justice")),
            () -> Config.get().profileDance, v -> Config.get().profileDance = v));
        general.add(Row.button(g, "Cached players", () -> "Forget everything and fetch again", "Clear", TierService::clearCache));
        pages.put(Page.GENERAL, general);

        pages.put(Page.LEFT, slotRows(0));
        pages.put(Page.RIGHT, slotRows(1));
        List<Row> head = new ArrayList<>();
        head.add(Row.choice("Above Head", "Tag layout", () -> "Left + right tags, or ONE tag like PvPHQ", () -> List.of(
            new Choice("SIDES", "Left + Right tags"), new Choice("SINGLE", "One tag (this page)")),
            () -> Config.get().headMode, v -> Config.get().headMode = v));
        head.addAll(slotRows(2));
        pages.put(Page.HEAD, head);

        String s = "Style";
        List<Row> style = new ArrayList<>();
        style.add(Row.choice(s, "Tag color", () -> "Tier colours, or a gradient", () -> List.of(
            new Choice("TIER", "Tier colors"), new Choice("GRADIENT", "Gradient"), new Choice("ANIMATED", "Animated gradient")),
            () -> Config.get().tagColorMode, v -> Config.get().tagColorMode = v));
        style.add(Row.choice(s, "Gradient", () -> "Pick Custom to use the 3 gradient colours", () -> {
            List<Choice> l = new ArrayList<>();
            for (Gradients.Preset p : Gradients.PRESETS) l.add(new Choice(p.key(), p.label()));
            return l;
        }, () -> Config.get().gradientPreset, v -> Config.get().gradientPreset = v));
        style.add(Row.slider(s, "Animation speed", () -> "How fast animated gradients move", 1, 20, () -> Config.get().gradientSpeed, v -> Config.get().gradientSpeed = v));
        style.add(Row.toggle(s, "Gradient player name", () -> "Also paint the name itself", () -> Config.get().nameGradient, v -> Config.get().nameGradient = v));
        style.add(Row.choice(s, "Icon color", () -> "Gamemode icons: own colours, tier colour or custom", () -> List.of(
            new Choice("ORIGINAL", "Original"), new Choice("TIER", "Tier color"), new Choice("CUSTOM", "Custom color")),
            () -> Config.get().iconMode, v -> Config.get().iconMode = v));
        pages.put(Page.STYLE, style);

        String gs = "Gradients";
        List<Row> grad = new ArrayList<>();
        String[] slotNames = {"Left tag", "Right tag", "Single tag"};
        for (int i = 0; i < 3; i++) {
            final int w = i;
            Supplier<Config.Slot> sl = () -> w == 0 ? Config.get().left : w == 1 ? Config.get().right : Config.get().head;
            grad.add(Row.choice(gs, slotNames[i] + " color", () -> "Own color mode for this tag", () -> List.of(
                new Choice("GLOBAL", "Follow Style page"), new Choice("TIER", "Tier colors"),
                new Choice("GRADIENT", "Gradient"), new Choice("ANIMATED", "Animated gradient")),
                () -> sl.get().colorMode, v -> sl.get().colorMode = v));
            Row gr = Row.choice(gs, slotNames[i] + " gradient", () -> "Shift+Click = back to global", () -> gradientChoices(false),
                () -> sl.get().gradient, v -> sl.get().gradient = v);
            gr.reset = () -> sl.get().gradient = "";
            grad.add(gr);
        }
        for (String grp : new String[] {"HT", "MT", "LT"}) {
            for (int t = 1; t <= 5; t++) {
                final String key = grp + t;
                Row r = Row.choice(gs, key + " tier", () -> "Gradient just for " + key + "  (Shift+Click = reset)", () -> gradientChoices(true),
                    () -> Config.get().tierGradient.getOrDefault(key, ""), v -> {
                        if (v.isEmpty()) Config.get().tierGradient.remove(key);
                        else Config.get().tierGradient.put(key, v);
                    });
                r.reset = () -> Config.get().tierGradient.remove(key);
                grad.add(r);
            }
        }
        pages.put(Page.GRADIENTS, grad);

        List<Row> provs = new ArrayList<>();
        for (TierProvider p : Providers.all()) {
            boolean custom = p instanceof CustomProvider;
            provs.add(Row.toggle("Tier Lists", p.name(),
                () -> (custom && !p.isConfigured() ? "No endpoint set yet (config/tiertagger.json)  -  " : "") + p.modes().size() + " gamemodes",
                () -> Config.get().providers.getOrDefault(p.id(), false),
                v -> Config.get().providers.put(p.id(), v)));
        }
        pages.put(Page.LISTS, provs);
        pages.put(Page.COLORS, new ArrayList<>());

        String a = "Appearance";
        List<Row> app = new ArrayList<>();
        app.add(Row.choice(a, "Theme", () -> "Colours of this settings window", () -> {
            List<Choice> l = new ArrayList<>();
            for (Ui.Theme t : Ui.THEMES) l.add(new Choice(t.key(), t.label()));
            return l;
        }, () -> Config.get().uiTheme, v -> {
            Config cfg = Config.get();
            cfg.uiTheme = v;
            for (Ui.Theme t : Ui.THEMES) {
                if (t.key().equals(v)) {
                    cfg.tierColors.put("UI_ACCENT", Config.toHex(t.accent()));
                    cfg.tierColors.put("UI_PANEL", Config.toHex(t.panel()));
                }
            }
        }));
        app.add(Row.choice(a, "Style", () -> "Flat panels or glassy rounded bubbles", () -> List.of(
            new Choice("FLAT", "Flat"), new Choice("BUBBLE", "Bubble (glass)")),
            () -> Config.get().uiStyle, v -> Config.get().uiStyle = v));
        app.add(Row.choice(a, "Font", () -> "Font used in Mania Tags menus", () -> List.of(
            new Choice("DEFAULT", "Default"), new Choice("UNIFORM", "Uniform"), new Choice("ALT", "Enchanting"), new Choice("ILLAGER", "Illager")),
            () -> Config.get().uiFont, v -> Config.get().uiFont = v));
        app.add(Row.toggle(a, "Animations", () -> "Smooth tab switching and fades", () -> Config.get().uiAnimations, v -> Config.get().uiAnimations = v));
        app.add(Row.slider(a, "Animation speed", () -> "Higher is faster", 1, 10, () -> Config.get().uiAnimSpeed, v -> Config.get().uiAnimSpeed = v));
        app.add(Row.choice(a, "Background", () -> "Picture or GIF behind the window", () -> List.of(
            new Choice("OFF", "None"), new Choice("FILE", "File on my PC"), new Choice("URL", "Link (url)")),
            () -> Config.get().bgMode, v -> Config.get().bgMode = v));
        app.add(Row.text(a, "Background source", () -> "File: path or name in config/tiertagger/backgrounds  |  Link: https://...",
            () -> Config.get().bgSource, v -> { Config.get().bgSource = v; dirty = true; }));
        app.add(Row.slider(a, "Background darkness (%)", () -> "Makes text easier to read", 0, 90, () -> Config.get().bgDim, v -> Config.get().bgDim = v));
        app.add(Row.slider(a, "Window opacity (%)", null, 40, 100, () -> Config.get().uiOpacity, v -> Config.get().uiOpacity = v));
        pages.put(Page.APPEARANCE, app);
    }

    private static List<Choice> gradientChoices(boolean tier) {
        List<Choice> l = new ArrayList<>();
        l.add(new Choice("", tier ? "Tier color (off)" : "Global gradient"));
        for (Gradients.Preset p : Gradients.PRESETS) l.add(new Choice(p.key(), p.label()));
        for (Gradients.Preset p : Gradients.PRESETS) l.add(new Choice(p.key() + ":anim", p.label() + " (animated)"));
        return l;
    }

    private List<Row> slotRows(int which) {
        boolean isLeft = which != 1;
        String sec = which == 0 ? "Left Tag" : which == 1 ? "Right Tag" : "Above Head";
        Supplier<Config.Slot> s = () -> which == 0 ? Config.get().left : which == 1 ? Config.get().right : Config.get().head;
        List<Row> rows = new ArrayList<>();
        rows.add(Row.toggle(sec, "Enabled", () -> "Show a tag " + (which == 2 ? "by" : isLeft ? "before" : "after") + " the player name", () -> s.get().enabled, v -> s.get().enabled = v));
        rows.add(Row.choice(sec, "Tier list", () -> "Which website to read tiers from", SettingsScreen::providerChoices, () -> s.get().provider, v -> {
            s.get().provider = v;
            s.get().mode = "*best";
        }));
        rows.add(Row.choice(sec, "Gamemode", () -> "Which gamemode's tier this tag shows", () -> modeChoices(s.get().provider), () -> s.get().mode, v -> s.get().mode = v));
        rows.add(Row.toggle(sec, "Show icon", () -> "Gamemode icon before the tier", () -> s.get().showIcon, v -> s.get().showIcon = v));
        rows.add(Row.toggle(sec, "Show world rank", () -> "Adds the player's world position, e.g. #6777", () -> s.get().showRank, v -> s.get().showRank = v));
        rows.add(Row.toggle(sec, "Show gamemode name", () -> "e.g. [Sword HT3] instead of [HT3]", () -> s.get().showMode, v -> s.get().showMode = v));
        rows.add(Row.toggle(sec, "Brackets", null, () -> s.get().brackets, v -> s.get().brackets = v));
        rows.add(Row.toggle(sec, "Bold", null, () -> s.get().bold, v -> s.get().bold = v));
        rows.add(Row.toggle(sec, "Show retired tiers", () -> "Hide the tag if the player is retired there", () -> s.get().showRetired, v -> s.get().showRetired = v));
        return rows;
    }

    // ------------------------------------------------------------------ layout

    private void layout() {
        pw = Math.min(width - 20, 700);
        ph = Math.min(height - 20, 400);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        sbX = px + 8;
        sbY = py + 8;
        sbW = 122;
        sbH = ph - 16;
        cX = sbX + sbW + 14;
        cW = px + pw - 12 - cX;
        hdrY = py + 12;
        ctTop = py + 44;
        ctBottom = py + ph - 10;
        search.w = 120;
        search.h = 18;
        search.x = px + pw - 12 - search.w;
        search.y = hdrY - 1;
        resetX = search.x - 8 - resetW;
        resetY = hdrY - 1;
    }

    private int pageStep() {
        return Math.max(18, Math.min(26, (sbH - 44 - 22) / Page.values().length));
    }

    private boolean searching() { return !search.text.trim().isEmpty(); }
    private boolean showPreview() { return !searching() && page != Page.LISTS && page != Page.APPEARANCE; }
    private int rowsTop() { return ctTop + (showPreview() ? 30 : 0); }
    private boolean colorsVisible() { return page == Page.COLORS && !searching(); }

    private List<Row> visibleRows() {
        String q = search.text.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return pages.getOrDefault(page, List.of());
        List<Row> out = new ArrayList<>();
        for (Page p : Page.values()) for (Row r : pages.getOrDefault(p, List.of())) if (r.matches(q)) out.add(r);
        return out;
    }

    private float animMs() {
        return 330f - Config.get().uiAnimSpeed * 26f;
    }

    private float progress(long t0) {
        if (!Config.get().uiAnimations) return 1f;
        return Ui.ease((System.nanoTime() - t0) / 1_000_000f / animMs());
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        long now = System.nanoTime();
        dt = Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
        lastFrame = now;

        Ui.applyTheme();
        this.renderBackground(c, mx, my, delta);
        layout();

        float open = progress(openT);
        Ui.alpha = open;
        Ui.panelBox(c, px, py, pw, ph);
        Backgrounds.draw(c, px + 3, py + 3, pw - 6, ph - 6);
        drawSidebar(c, mx, my);
        drawHeader(c, mx, my);

        float pg = progress(pageT0);
        Ui.alpha = open * pg;
        int slide = Math.round((1f - pg) * 20f);
        if (showPreview()) drawPreview(c, slide);

        c.enableScissor(cX, rowsTop(), cX + cW, ctBottom);
        if (colorsVisible()) drawColors(c, mx, my, slide);
        else drawRows(c, mx, my, slide);
        c.disableScissor();

        Ui.alpha = open;
        drawDropdown(c, mx, my);
        Ui.alpha = 1f;
    }

    private void drawSidebar(DrawContext c, int mx, int my) {
        Ui.rrect(c, sbX, sbY, sbW, sbH, 8, Ui.sidebar);
        Ui.text(c, textRenderer, Text.literal("Mania Tags").formatted(Formatting.BOLD), sbX + 12, sbY + 12, Ui.TEXT, true);
        Ui.text(c, textRenderer, "Settings", sbX + 12, sbY + 24, Ui.DIM, false);

        float target = sbY + 44 + page.ordinal() * pageStep();
        if (hlY < 0 || !Config.get().uiAnimations) hlY = target;
        else hlY += (target - hlY) * Math.min(1f, dt * (6f + Config.get().uiAnimSpeed * 2f));
        if (!searching()) {
            Ui.rrect(c, sbX + 6, Math.round(hlY), sbW - 12, 20, Ui.bubble() ? 10 : 6, Ui.activeBg);
            Ui.fill(c, sbX + 6, Math.round(hlY) + 5, sbX + 8, Math.round(hlY) + 17, Ui.accent);
        }
        int y = sbY + 44;
        for (Page p : Page.values()) {
            boolean active = p == page && !searching();
            boolean hover = mx >= sbX + 6 && mx < sbX + sbW - 6 && my >= y && my < y + 20;
            if (hover && !active) Ui.rrect(c, sbX + 6, y, sbW - 12, 22, 6, Ui.rowB);
            Ui.text(c, textRenderer, p.emoji, sbX + 14, y + 6, active ? Ui.accent : Ui.DIM, false);
            Ui.text(c, textRenderer, p.title, sbX + 28, y + 6, active ? Ui.TEXT : Ui.DIM, active);
            y += pageStep();
        }
        Ui.text(c, textRenderer, "Esc to close", sbX + 12, sbY + sbH - 16, 0xFF5B6475, false);
    }

    private void drawHeader(DrawContext c, int mx, int my) {
        String title = searching() ? "Search results" : page.title;
        Ui.text(c, textRenderer, Text.literal(title).formatted(Formatting.BOLD), cX, hdrY + 5, Ui.TEXT, true);
        boolean hover = mx >= resetX && mx < resetX + resetW && my >= resetY && my < resetY + resetH;
        Ui.rrect(c, resetX, resetY, resetW, resetH, 8, hover ? Ui.controlHover : Ui.control);
        Ui.centered(c, textRenderer, "Reset", resetX + resetW / 2, resetY + 5, Ui.TEXT);
        search.draw(c, textRenderer);
    }

    private void drawPreview(DrawContext c, int slide) {
        int x = cX + slide;
        Ui.rrect(c, x, ctTop, cW, 24, 6, Ui.rowA);
        Ui.text(c, textRenderer, "Preview", x + 10, ctTop + 8, Ui.DIM, false);
        Config cfg = Config.get();
        MutableText t = Text.empty();
        if (cfg.left.enabled) t.append(sample(cfg.left, TierEntry.of(3, 0, false))).append(Text.literal(" "));
        t.append(cfg.nameGradient ? NametagBuilder.gradientName("AwesomeTbotDude") : Text.literal("AwesomeTbotDude").formatted(Formatting.WHITE));
        if (cfg.right.enabled) t.append(Text.literal(" ")).append(sample(cfg.right, TierEntry.of(2, 2, false)));
        int w = textRenderer.getWidth(t);
        c.drawText(textRenderer, t, x + cW - 10 - w, ctTop + 8, 0xFFFFFFFF, true);
    }

    private static MutableText sample(Config.Slot s, TierEntry e) {
        String pid = "*".equals(s.provider) ? "mctiers" : s.provider;
        String key = "sword";
        String label = "Sword";
        TierProvider p = Providers.byId(pid);
        if (p != null) {
            TierProvider.Mode m = p.mode(s.mode);
            if (m == null && !p.modes().isEmpty()) m = p.modes().get(0);
            if (m != null) {
                key = m.key();
                label = m.label();
            }
        }
        return NametagBuilder.format(s, pid, key, label, e, 1234);
    }

    private int rowHeight(Row r) { return r.desc != null && r.desc.get() != null ? 36 : 26; }

    private void drawRows(DrawContext c, int mx, int my, int slide) {
        List<Row> rows = visibleRows();
        boolean searching = searching();
        int top = rowsTop();
        int x0 = cX + slide;
        int y = top - Math.round(scroll);
        int total = 0;
        int i = 0;
        for (Row r : rows) {
            r.y = y;
            r.h = rowHeight(r);
            place(r, x0);
            if (r.y + r.h >= top && r.y <= ctBottom) drawRow(c, r, x0, i % 2 == 1, searching, mx, my);
            y += r.h;
            total += r.h;
            i++;
        }
        if (rows.isEmpty()) Ui.text(c, textRenderer, "Nothing matches your search.", x0 + 10, top + 10, Ui.DIM, false);
        float maxScroll = Math.max(0, total - (ctBottom - top));
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        if (maxScroll > 0) {
            int trackH = ctBottom - top;
            int barH = Math.max(18, (int) (trackH * (trackH / (float) total)));
            int barY = top + (int) ((trackH - barH) * (scroll / maxScroll));
            Ui.rrect(c, cX + cW - 3, barY, 3, barH, 1, Ui.border);
        }
    }

    private void place(Row r, int x0) {
        int right = x0 + cW - 12;
        switch (r.kind) {
            case TOGGLE -> { r.cw = 24; r.ch = 12; r.cx = right - r.cw; }
            case SLIDER -> { r.cw = 110; r.ch = 12; r.cx = right - 34 - 8 - r.cw; }
            case CHOICE -> { r.cw = 134; r.ch = 16; r.cx = right - r.cw; }
            case BUTTON -> { r.cw = 64; r.ch = 16; r.cx = right - r.cw; }
            case TEXT -> { r.cw = 170; r.ch = 18; r.cx = right - r.cw; }
        }
        r.cy = r.y + (26 - r.ch) / 2;
    }

    private void drawRow(DrawContext c, Row r, int x0, boolean alt, boolean searching, int mx, int my) {
        Ui.row(c, x0, r.y, cW, r.h, alt ? Ui.rowB : Ui.rowA);
        String label = searching ? r.section + " · " + r.label : r.label;
        boolean hasDesc = r.h > 30;
        Ui.text(c, textRenderer, label, x0 + 10, r.y + (hasDesc ? 6 : 9), Ui.TEXT, true);
        if (hasDesc) Ui.text(c, textRenderer, Ui.trim(textRenderer, r.desc.get(), cW - 190), x0 + 10, r.y + 20, Ui.DIM, false);

        boolean hover = mx >= r.cx && mx < r.cx + r.cw && my >= r.cy - 2 && my < r.cy + r.ch + 2;
        switch (r.kind) {
            case TOGGLE -> {
                boolean on = r.getB.getAsBoolean();
                float target = on ? 1f : 0f;
                if (r.anim < 0 || !Config.get().uiAnimations) r.anim = target;
                else r.anim += (target - r.anim) * Math.min(1f, dt * 18f);
                int bg = Ui.mix(hover ? Ui.controlHover & 0xFFFFFF : Ui.control & 0xFFFFFF, Ui.accent & 0xFFFFFF, r.anim);
                Ui.rrect(c, r.cx, r.cy, r.cw, r.ch, 6, 0xFF000000 | bg);
                int kx = r.cx + 1 + Math.round((r.cw - r.ch) * r.anim);
                Ui.rrect(c, kx, r.cy + 1, r.ch - 2, r.ch - 2, 5, 0xFFE8ECF4);
            }
            case SLIDER -> {
                int v = r.getI.getAsInt();
                float t = (v - r.min) / (float) (r.max - r.min);
                Ui.rrect(c, r.cx, r.cy + 4, r.cw, 4, 2, Ui.control);
                Ui.rrect(c, r.cx, r.cy + 4, Math.max(4, (int) (r.cw * t)), 4, 2, Ui.accent);
                Ui.rrect(c, r.cx + (int) ((r.cw - 8) * t), r.cy, 8, 12, 4, 0xFFE8ECF4);
                int bx = r.cx + r.cw + 8;
                Ui.rrect(c, bx, r.cy - 2, 34, 16, 5, Ui.control);
                Ui.centered(c, textRenderer, String.valueOf(v), bx + 17, r.cy + 2, Ui.TEXT);
            }
            case CHOICE -> {
                Ui.rrect(c, r.cx, r.cy, r.cw, r.ch, 6, hover || ddRow == r ? Ui.controlHover : Ui.control);
                String cur = r.getKey.get();
                for (Choice ch : r.options.get()) if (ch.key().equals(cur)) cur = ch.label();
                Ui.text(c, textRenderer, Ui.trim(textRenderer, cur, r.cw - 24), r.cx + 8, r.cy + 4, Ui.TEXT, false);
                Ui.chevron(c, r.cx + r.cw - 10, r.cy + 6, Ui.DIM);
            }
            case TEXT -> {
                r.box.x = r.cx; r.box.y = r.cy; r.box.w = r.cw; r.box.h = r.ch;
                r.box.draw(c, textRenderer);
            }
            case BUTTON -> {
                Ui.rrect(c, r.cx, r.cy, r.cw, r.ch, 6, hover ? Ui.controlHover : Ui.control);
                Ui.centered(c, textRenderer, r.buttonLabel, r.cx + r.cw / 2, r.cy + 4, Ui.TEXT);
            }
        }
    }

    private void drawDropdown(DrawContext c, int mx, int my) {
        if (ddRow == null) return;
        int widest = ddRow.cw;
        for (Choice ch : ddOptions) widest = Math.max(widest, textRenderer.getWidth(ch.label()) + 20);
        ddW = Math.min(widest, 200);
        int visible = Math.min(ddOptions.size(), DD_MAX);
        ddH = visible * DD_ITEM + 4;
        ddX = Math.min(ddRow.cx, px + pw - ddW - 6);
        ddY = ddRow.cy + ddRow.ch + 2;
        if (ddY + ddH > height - 4) ddY = ddRow.cy - ddH - 2;
        ddScroll = Math.max(0, Math.min(Math.max(0, ddOptions.size() - visible), ddScroll));
        Ui.rrect(c, ddX - 1, ddY - 1, ddW + 2, ddH + 2, 6, Ui.border);
        Ui.rrect(c, ddX, ddY, ddW, ddH, 6, Ui.sidebar);
        String cur = ddRow.getKey.get();
        for (int i = 0; i < visible; i++) {
            Choice ch = ddOptions.get(ddScroll + i);
            int iy = ddY + 2 + i * DD_ITEM;
            boolean hover = mx >= ddX && mx < ddX + ddW && my >= iy && my < iy + DD_ITEM;
            if (hover) Ui.rrect(c, ddX + 2, iy, ddW - 4, DD_ITEM, 4, Ui.controlHover);
            boolean sel = ch.key().equals(cur);
            if (sel) Ui.fill(c, ddX + 3, iy + 4, ddX + 5, iy + DD_ITEM - 4, Ui.accent);
            Ui.text(c, textRenderer, Ui.trim(textRenderer, ch.label(), ddW - 16), ddX + 9, iy + 4, sel ? Ui.TEXT : 0xFFC3CAD6, false);
        }
    }

    // ---- colours page

    private static String swatchLabel(String key) {
        return switch (key) {
            case "UI_ACCENT" -> "ACCENT";
            case "UI_PANEL" -> "PANEL";
            default -> key;
        };
    }

    private void drawColors(DrawContext c, int mx, int my, int slide) {
        swatches.clear();
        int x0 = cX + slide;
        int y0 = rowsTop() + 4;
        int cw = 56, chh = 20, gap = 6;
        String[] cols = {"HT", "MT", "LT"};
        for (int col = 0; col < cols.length; col++) {
            Ui.text(c, textRenderer, cols[col], x0 + col * (cw + gap) + cw / 2 - 6, y0, Ui.DIM, false);
        }
        int gy = y0 + 12;
        for (int tier = 1; tier <= 5; tier++) {
            for (int col = 0; col < cols.length; col++) {
                drawSwatch(c, cols[col] + tier, x0 + col * (cw + gap), gy + (tier - 1) * (chh + gap), cw, chh, mx, my);
            }
        }
        String[][] extra = {{"RETIRED", "OTHER", "ICON"}, {"GRAD1", "GRAD2", "GRAD3"}, {"UI_ACCENT", "UI_PANEL"}};
        int ey = gy + 5 * (chh + gap) + 4;
        for (String[] row : extra) {
            for (int i = 0; i < row.length; i++) drawSwatch(c, row[i], x0 + i * (cw + gap), ey, cw, chh, mx, my);
            ey += chh + gap;
        }

        int pickX = x0 + 3 * (cw + gap) + 22;
        if (selKey == null) {
            Ui.text(c, textRenderer, "Pick a color on the left to edit it.", pickX, y0 + 40, Ui.DIM, false);
            Ui.text(c, textRenderer, "Shift+Click a color = back to original", pickX, y0 + 54, Ui.DIM, false);
            return;
        }
        Ui.text(c, textRenderer, Text.literal("Editing " + swatchLabel(selKey)).formatted(Formatting.BOLD), pickX, y0, Ui.TEXT, true);
        wheelCX = pickX + WHEEL_R;
        wheelCY = y0 + 14 + WHEEL_R;
        drawWheel(c);

        barX = pickX;
        barY = wheelCY + WHEEL_R + 10;
        for (int i = 0; i < BAR_W; i++) {
            c.fill(barX + i, barY, barX + i + 1, barY + BAR_H, 0xFF000000 | hsv(hue, sat, i / (float) (BAR_W - 1)));
        }
        int kx = barX + Math.round(val * (BAR_W - 1));
        c.fill(kx - 1, barY - 2, kx + 2, barY + BAR_H + 2, 0xFFFFFFFF);
        Ui.text(c, textRenderer, "Brightness", barX, barY + BAR_H + 4, Ui.DIM, false);

        int sideX = pickX + 2 * WHEEL_R + 18;
        Ui.rrect(c, sideX, y0 + 14, 70, 34, 6, 0xFF000000 | hsv(hue, sat, val));
        Ui.text(c, textRenderer, "Hex", sideX, y0 + 54, Ui.DIM, false);
        hex.x = sideX;
        hex.y = y0 + 64;
        hex.w = 70;
        hex.h = 18;
        hex.draw(c, textRenderer);
        defX = sideX;
        defY = y0 + 90;
        boolean hover = mx >= defX && mx < defX + defW && my >= defY && my < defY + defH;
        Ui.rrect(c, defX, defY, defW, defH, 6, hover ? Ui.controlHover : Ui.control);
        Ui.centered(c, textRenderer, "Default", defX + defW / 2, defY + 4, Ui.TEXT);
    }

    private void drawSwatch(DrawContext c, String key, int x, int y, int w, int h, int mx, int my) {
        int rgb = Config.get().colorOf(key);
        boolean sel = key.equals(selKey);
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        if (sel) Ui.rrect(c, x - 2, y - 2, w + 4, h + 4, 7, Ui.accent);
        else if (hover) Ui.rrect(c, x - 1, y - 1, w + 2, h + 2, 6, 0xFF6B7488);
        Ui.rrect(c, x, y, w, h, 5, 0xFF000000 | rgb);
        int lum = (int) (0.299 * ((rgb >> 16) & 255) + 0.587 * ((rgb >> 8) & 255) + 0.114 * (rgb & 255));
        Ui.centered(c, textRenderer, swatchLabel(key), x + w / 2, y + 6, lum > 140 ? 0xFF111111 : 0xFFFFFFFF);
        swatches.add(new Object[] {key, x, y, w, h});
    }

    private void drawWheel(DrawContext c) {
        if (wheelVal != val) {
            wheelVal = val;
            float half = WHEEL_CELLS / 2f;
            for (int i = 0; i < WHEEL_CELLS; i++) {
                for (int j = 0; j < WHEEL_CELLS; j++) {
                    float dx = i + 0.5f - half;
                    float dy = j + 0.5f - half;
                    float d = (float) Math.sqrt(dx * dx + dy * dy) / half;
                    if (d > 1f) {
                        wheel[i * WHEEL_CELLS + j] = 0;
                    } else {
                        float h = (float) (Math.atan2(dy, dx) / (2 * Math.PI));
                        if (h < 0) h += 1f;
                        wheel[i * WHEEL_CELLS + j] = 0xFF000000 | hsv(h, d, val);
                    }
                }
            }
        }
        int ox = wheelCX - WHEEL_R;
        int oy = wheelCY - WHEEL_R;
        for (int i = 0; i < WHEEL_CELLS; i++) {
            for (int j = 0; j < WHEEL_CELLS; j++) {
                int col = wheel[i * WHEEL_CELLS + j];
                if (col == 0) continue;
                int x = ox + i * WHEEL_CELL;
                int y = oy + j * WHEEL_CELL;
                c.fill(x, y, x + WHEEL_CELL, y + WHEEL_CELL, col);
            }
        }
        double ang = hue * 2 * Math.PI;
        int mxp = wheelCX + (int) Math.round(Math.cos(ang) * sat * WHEEL_R);
        int myp = wheelCY + (int) Math.round(Math.sin(ang) * sat * WHEEL_R);
        c.fill(mxp - 3, myp - 3, mxp + 4, myp + 4, 0xFFFFFFFF);
        c.fill(mxp - 2, myp - 2, mxp + 3, myp + 3, 0xFF000000 | hsv(hue, sat, val));
    }

    // ------------------------------------------------------------------ colour logic

    private void selectKey(String key) {
        selKey = key;
        int rgb = Config.get().colorOf(key);
        float[] f = rgbToHsv(rgb);
        hue = f[0];
        sat = f[1];
        val = f[2];
        hex.text = Config.toHex(rgb);
        hex.focused = false;
    }

    private void applyColor() {
        if (selKey == null) return;
        String h = Config.toHex(hsv(hue, sat, val));
        Config.get().tierColors.put(selKey, h);
        if (!hex.focused) hex.text = h;
        markDirty();
    }

    private void onHexChanged(String s) {
        if (selKey == null) return;
        String t = s.startsWith("#") ? s.substring(1) : s;
        if (t.length() != 6) return;
        int rgb = Config.parseHex(t, -1);
        if (rgb < 0) return;
        float[] f = rgbToHsv(rgb);
        hue = f[0];
        sat = f[1];
        val = f[2];
        Config.get().tierColors.put(selKey, Config.toHex(rgb));
        markDirty();
    }

    private static int hsv(float h, float s, float v) {
        h = h - (float) Math.floor(h);
        float hh = h * 6f;
        int i = (int) Math.floor(hh);
        float f = hh - i;
        float p = v * (1 - s);
        float q = v * (1 - f * s);
        float t = v * (1 - (1 - f) * s);
        float r, g, b;
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
    }

    private static float[] rgbToHsv(int rgb) {
        float r = ((rgb >> 16) & 255) / 255f;
        float g = ((rgb >> 8) & 255) / 255f;
        float b = (rgb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h;
        if (d == 0) h = 0;
        else if (max == r) h = ((g - b) / d) % 6f;
        else if (max == g) h = (b - r) / d + 2f;
        else h = (r - g) / d + 4f;
        h /= 6f;
        if (h < 0) h += 1f;
        return new float[] {h, max == 0 ? 0 : d / max, max};
    }

    private void updateWheel(double mx, double my) {
        double dx = mx - wheelCX;
        double dy = my - wheelCY;
        double len = Math.sqrt(dx * dx + dy * dy);
        sat = (float) Math.min(1.0, len / WHEEL_R);
        if (len > 0.5) {
            double a = Math.atan2(dy, dx) / (2 * Math.PI);
            if (a < 0) a += 1;
            hue = (float) a;
        }
        applyColor();
    }

    private void updateBar(double mx) {
        val = (float) Math.max(0, Math.min(1, (mx - barX) / (BAR_W - 1)));
        applyColor();
    }

    // ------------------------------------------------------------------ input

    private void markDirty() {
        dirty = true;
        TierService.bump();
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void setPage(Page p) {
        if (p == page && !searching()) return;
        page = p;
        search.text = "";
        scroll = 0;
        ddRow = null;
        pageT0 = System.nanoTime();
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = Compat.x(click);
        double my = Compat.y(click);
        if (Compat.button(click) != 0) return super.mouseClicked(click, doubled);

        if (ddRow != null) {
            if (in(mx, my, ddX, ddY, ddW, ddH)) {
                int idx = ddScroll + (int) ((my - ddY - 2) / DD_ITEM);
                if (idx >= 0 && idx < ddOptions.size()) {
                    ddRow.setKey.accept(ddOptions.get(idx).key());
                    markDirty();
                }
            }
            ddRow = null;
            return true;
        }

        for (Row r : visibleRows()) if (r.box != null) r.box.focused = false;
        search.focused = search.contains(mx, my);
        hex.focused = colorsVisible() && selKey != null && hex.contains(mx, my);
        if (search.focused || hex.focused) return true;

        int y = sbY + 44;
        for (Page p : Page.values()) {
            if (in(mx, my, sbX + 6, y, sbW - 12, 20)) {
                setPage(p);
                return true;
            }
            y += pageStep();
        }

        if (in(mx, my, resetX, resetY, resetW, resetH)) {
            resetPage();
            return true;
        }
        if (my < rowsTop() || my > ctBottom) return super.mouseClicked(click, doubled);

        if (colorsVisible()) {
            for (Object[] s : swatches) {
                if (in(mx, my, (int) s[1], (int) s[2], (int) s[3], (int) s[4])) {
                    if (Ui.shift()) {
                        String def = Config.DEFAULT_COLORS.get((String) s[0]);
                        if (def != null) Config.get().tierColors.put((String) s[0], def);
                        selectKey((String) s[0]);
                        markDirty();
                        return true;
                    }
                    selectKey((String) s[0]);
                    return true;
                }
            }
            if (selKey != null) {
                if (in(mx, my, defX, defY, defW, defH)) {
                    String def = Config.DEFAULT_COLORS.get(selKey);
                    if (def != null) {
                        Config.get().tierColors.put(selKey, def);
                        selectKey(selKey);
                        markDirty();
                    }
                    return true;
                }
                double dx = mx - wheelCX, dy = my - wheelCY;
                if (dx * dx + dy * dy <= (WHEEL_R + 3) * (WHEEL_R + 3)) {
                    drag = Drag.WHEEL;
                    updateWheel(mx, my);
                    return true;
                }
                if (in(mx, my, barX - 2, barY - 3, BAR_W + 4, BAR_H + 6)) {
                    drag = Drag.BAR;
                    updateBar(mx);
                    return true;
                }
            }
            return super.mouseClicked(click, doubled);
        }

        for (Row r : visibleRows()) {
            int hw = r.cw + (r.kind == Row.Kind.SLIDER ? 42 : 0);
            if (!in(mx, my, r.cx, r.cy - 2, hw, r.ch + 4)) continue;
            switch (r.kind) {
                case TEXT -> r.box.focused = true;
                case TOGGLE -> {
                    r.setB.accept(!r.getB.getAsBoolean());
                    markDirty();
                }
                case SLIDER -> {
                    drag = Drag.SLIDER;
                    dragRow = r;
                    updateSlider(r, mx);
                }
                case CHOICE -> {
                    if (Ui.shift() && r.reset != null) {
                        r.reset.run();
                        markDirty();
                        return true;
                    }
                    ddRow = r;
                    ddOptions = r.options.get();
                    ddScroll = 0;
                    for (int i = 0; i < ddOptions.size(); i++) {
                        if (ddOptions.get(i).key().equals(r.getKey.get())) ddScroll = Math.max(0, i - 3);
                    }
                }
                case BUTTON -> {
                    r.action.run();
                    markDirty();
                }
            }
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    private void updateSlider(Row r, double mx) {
        float t = (float) Math.max(0, Math.min(1, (mx - r.cx) / (double) r.cw));
        r.setI.accept(r.min + Math.round(t * (r.max - r.min)));
        markDirty();
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        double mx = Compat.x(click);
        double my = Compat.y(click);
        switch (drag) {
            case SLIDER -> { if (dragRow != null) updateSlider(dragRow, mx); return true; }
            case WHEEL -> { updateWheel(mx, my); return true; }
            case BAR -> { updateBar(mx); return true; }
            default -> { return super.mouseDragged(click, offsetX, offsetY); }
        }
    }

    @Override
    public boolean mouseReleased(Click click) {
        drag = Drag.NONE;
        dragRow = null;
        if (dirty) {
            Config.save();
            dirty = false;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (ddRow != null) {
            ddScroll -= (int) Math.signum(vertical);
            return true;
        }
        scroll -= (float) (vertical * 22);
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = Compat.key(input);
        Ui.TextBox tb = focusedBox();
        if (tb != null) {
            if (key == 256 || key == 257 || key == 335) tb.focused = false;
            else if (key == 259) tb.backspace();
            else if (key == 86 && (Compat.mods(input) & (2 | 8)) != 0) {
                String clip = Compat.clipboard();
                if (clip != null) for (int i = 0; i < clip.length(); i++) tb.type(clip.charAt(i));
            }
            return true;
        }
        if (key == 256 && ddRow != null) {
            ddRow = null;
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        Ui.TextBox tb = focusedBox();
        if (tb != null) {
            tb.type(Compat.codepoint(input));
            return true;
        }
        return super.charTyped(input);
    }

    private Ui.TextBox focusedBox() {
        if (search.focused) return search;
        if (hex.focused) return hex;
        for (Row r : pages.get(Page.APPEARANCE)) if (r.box != null && r.box.focused) return r.box;
        return null;
    }

    private void resetPage() {
        Config cfg = Config.get();
        switch (page) {
            case GENERAL -> cfg.resetGeneral();
            case LEFT -> cfg.resetSlot(true);
            case RIGHT -> cfg.resetSlot(false);
            case STYLE -> cfg.resetStyle();
            case HEAD -> cfg.resetHead();
            case GRADIENTS -> cfg.resetGradients();
            case LISTS -> cfg.resetProviders();
            case COLORS -> {
                cfg.resetColors();
                if (selKey != null) selectKey(selKey);
            }
            case APPEARANCE -> {
                cfg.resetAppearance();
                for (Row r : pages.get(Page.APPEARANCE)) if (r.box != null) r.box.text = "";
            }
        }
        markDirty();
    }

    @Override
    public void removed() {
        Config.save();
        super.removed();
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    /** Minecraft 1.21.9+ replaced raw mouse/key/char parameters with small input objects. */
    static final class Compat {
        static double x(Click c) { return c.x(); }
        static double y(Click c) { return c.y(); }
        static int button(Click c) { return c.button(); }
        static int key(KeyInput k) { return k.key(); }
        static int mods(KeyInput k) { return k.modifiers(); }
        static int codepoint(CharInput c) { return c.codepoint(); }
        static String clipboard() { return MinecraftClient.getInstance().keyboard.getClipboard(); }
    }
}
