package com.tiertagger;

import net.minecraft.text.MutableText;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/** Gamemode icons: as font glyphs (nametag / tab / chat) and as GUI textures. */
public final class Icons {
    private Icons() {}

    private static final Identifier FONT = Identifier.of("tiertagger", "icons");
    private static final Map<String, Integer> INDEX = new HashMap<>();
    private static final Map<String, String> ALIAS = new HashMap<>();

    static {
        for (int i = 0; i < IconIds.IDS.length; i++) INDEX.put(IconIds.IDS[i], i);

        String[][] a = {
            {"crystal", "pvptiers/crystal"}, {"sword", "pvptiers/sword"}, {"pot", "pvptiers/pot"},
            {"diapot", "pvptiers/pot"}, {"nethpot", "pvptiers/neth_pot"}, {"netheritepot", "pvptiers/neth_pot"},
            {"nethop", "mctiers/neth_op"}, {"smp", "pvptiers/smp"}, {"mace", "pvptiers/mace"},
            {"spearmace", "pvptiers/mace"}, {"uhc", "pvptiers/uhc"}, {"axe", "pvptiers/axe"},
            {"vanilla", "mctiers/vanilla"}, {"speed", "subtiers/speed"}, {"speedop", "subtiers/speed"},
            {"diacrystal", "pvptiers/crystal"}, {"ironcrystal", "pvptiers/crystal"},
            {"diasmp", "subtiers/diamond_smp"}, {"diamondsmp", "subtiers/diamond_smp"},
            {"ogv", "subtiers/og_vanilla"}, {"goldogv", "subtiers/og_vanilla"}, {"ogvanilla", "subtiers/og_vanilla"},
            {"diamondvanilla", "subtiers/diamond_vanilla"}, {"creeper", "subtiers/creeper"},
            {"elytra", "subtiers/elytra"}, {"trident", "subtiers/trident"}, {"bed", "subtiers/bed"},
            {"minecart", "subtiers/minecart"}, {"cart", "subtiers/minecart"}, {"bow", "subtiers/bow"},
            {"debuff", "subtiers/debuff"}, {"manhunt", "subtiers/manhunt"}, {"soup", "mctiers/pot"},
            {"nethsword", "pvptiers/sword"}, {"shield", "pvptiers/smp"}, {"spear", "subtiers/trident"},
            {"earlygame", "subtiers/og_vanilla"}, {"lategame", "subtiers/diamond_vanilla"},
            {"endgame", "pvptiers/neth_pot"}
        };
        for (String[] e : a) ALIAS.put(e[0], e[1]);
    }

    /** Icon id like "pvptiers/sword" for a list + gamemode, or null if there is none. */
    public static String resolve(String providerId, String modeKey) {
        String exact = providerId + "/" + modeKey;
        if (INDEX.containsKey(exact)) return exact;
        String norm = modeKey.toLowerCase().replace("_", "");
        if ("nethop".equals(norm) && INDEX.containsKey("mctiers/neth_op")) return "mctiers/neth_op";
        return ALIAS.get(norm);
    }

    /**
     * The icon as a text glyph. {@code mono} glyphs are white shading that takes {@code rgb};
     * the normal glyph keeps its own colours.
     */
    public static MutableText glyph(String id, boolean mono, int rgb) {
        Integer idx = INDEX.get(id);
        if (idx == null) return Text.empty();
        char ch = (char) (0xE000 + idx * 2 + (mono ? 1 : 0));
        final int color = mono ? rgb : 0xFFFFFF;
        return Text.literal(String.valueOf(ch))
            .styled(s -> s.withFont(new StyleSpriteSource.Font(FONT)).withColor(color));
    }

    /** 32x32 texture for drawing the icon in screens. */
    public static Identifier gui(String id) {
        return Identifier.of("tiertagger", "textures/ui/" + id + ".png");
    }
}
