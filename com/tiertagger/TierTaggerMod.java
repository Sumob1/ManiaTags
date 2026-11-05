package com.tiertagger;

import com.mojang.brigadier.Command;
import com.tiertagger.api.TierService;
import com.tiertagger.gui.CompareScreen;
import com.tiertagger.gui.ProfileScreen;
import com.tiertagger.gui.SettingsScreen;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.CommandSource;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TierTaggerMod implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("TierTagger");

    private static final KeyBinding.Category CATEGORY =
        KeyBinding.Category.create(Identifier.of("tiertagger", "general"));

    private static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        Config.load();

        openKey = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.tiertagger.open", GLFW.GLFW_KEY_RIGHT_SHIFT, CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                client.setScreen(new SettingsScreen(client.currentScreen));
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("tiertagger")
                .executes(ctx -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    mc.send(() -> mc.setScreen(new SettingsScreen(null)));
                    return Command.SINGLE_SUCCESS;
                })
                .then(ClientCommandManager.literal("clear").executes(ctx -> {
                    TierService.clearCache();
                    ctx.getSource().sendFeedback(Text.literal("Tier cache cleared."));
                    return Command.SINGLE_SUCCESS;
                }))));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("tiers")
                .executes(ctx -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    String self = mc.player != null ? mc.player.getNameForScoreboard() : "";
                    mc.send(() -> mc.setScreen(new ProfileScreen(self, null)));
                    return Command.SINGLE_SUCCESS;
                })
                .then(ClientCommandManager.argument("player", StringArgumentType.word())
                    .suggests((ctx, b) -> CommandSource.suggestMatching(ctx.getSource().getPlayerNames(), b))
                    .executes(ctx -> {
                        String n = StringArgumentType.getString(ctx, "player");
                        MinecraftClient mc = MinecraftClient.getInstance();
                        mc.send(() -> mc.setScreen(new ProfileScreen(n, null)));
                        return Command.SINGLE_SUCCESS;
                    })));
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("tiercompare")
                .executes(ctx -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    String self = mc.player != null ? mc.player.getNameForScoreboard() : "";
                    mc.send(() -> mc.setScreen(new CompareScreen(self, "", null)));
                    return Command.SINGLE_SUCCESS;
                })
                .then(ClientCommandManager.argument("player", StringArgumentType.word())
                    .suggests((ctx, b) -> CommandSource.suggestMatching(ctx.getSource().getPlayerNames(), b))
                    .executes(ctx -> {
                        String n = StringArgumentType.getString(ctx, "player");
                        MinecraftClient mc = MinecraftClient.getInstance();
                        String self = mc.player != null ? mc.player.getNameForScoreboard() : "";
                        mc.send(() -> mc.setScreen(new CompareScreen(self, n, null)));
                        return Command.SINGLE_SUCCESS;
                    })
                    .then(ClientCommandManager.argument("other", StringArgumentType.word())
                        .suggests((ctx, b) -> CommandSource.suggestMatching(ctx.getSource().getPlayerNames(), b))
                        .executes(ctx -> {
                            String n1 = StringArgumentType.getString(ctx, "player");
                            String n2 = StringArgumentType.getString(ctx, "other");
                            MinecraftClient mc = MinecraftClient.getInstance();
                            mc.send(() -> mc.setScreen(new CompareScreen(n1, n2, null)));
                            return Command.SINGLE_SUCCESS;
                        })))));

        LOGGER.info("Mania Tags loaded");
    }
}
