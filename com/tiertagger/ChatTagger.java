package com.tiertagger;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Inserts tier tags next to online players' names inside chat messages. */
public final class ChatTagger {
    private ChatTagger() {}

    private static final Pattern WORD = Pattern.compile("[A-Za-z0-9_]{3,16}");

    public static Text process(Text message) {
        Config cfg = Config.get();
        if (!cfg.enabled || !cfg.showChat) return message;

        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayNetworkHandler handler = mc.getNetworkHandler();
        if (handler == null) return message;

        Map<String, UUID> online = new HashMap<>();
        for (PlayerListEntry e : handler.getPlayerList()) {
            String name = Compat.name(e);
            if (NametagBuilder.isRealPlayerName(name)) online.put(name.toLowerCase(Locale.ROOT), Compat.id(e));
        }
        if (online.isEmpty()) return message;

        // quick reject: does the message mention any online name at all?
        String plain = message.getString();
        boolean mentions = false;
        Matcher probe = WORD.matcher(plain);
        while (probe.find()) {
            if (online.containsKey(probe.group().toLowerCase(Locale.ROOT))) {
                mentions = true;
                break;
            }
        }
        if (!mentions) return message;

        MutableText out = Text.empty();
        boolean[] changed = {false};
        message.<Void>visit((Style style, String piece) -> {
            Matcher m = WORD.matcher(piece);
            int last = 0;
            while (m.find()) {
                UUID uuid = online.get(m.group().toLowerCase(Locale.ROOT));
                if (uuid == null) continue;
                NametagBuilder.Tags tags = NametagBuilder.tagsFor(uuid, m.group());
                if (tags.isEmpty()) continue;
                if (m.start() > last) out.append(Text.literal(piece.substring(last, m.start())).setStyle(style));
                if (tags.left() != null) out.append(tags.left());
                MutableText nameText = cfg.nameGradient
                    ? NametagBuilder.gradientName(m.group())
                    : Text.literal(m.group()).setStyle(style);
                out.append(nameText);
                if (tags.right() != null) out.append(tags.right());
                last = m.end();
                changed[0] = true;
            }
            if (last < piece.length()) out.append(Text.literal(piece.substring(last)).setStyle(style));
            return Optional.empty();
        }, Style.EMPTY);

        return changed[0] ? out : message;
    }

    /** Things that differ between Minecraft versions live here. */
    static final class Compat {
        static String name(PlayerListEntry e) { return e.getProfile().name(); }
        static UUID id(PlayerListEntry e) { return e.getProfile().id(); }
    }
}
