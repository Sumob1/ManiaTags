package com.tiertagger.mixin;

import com.tiertagger.Config;
import com.tiertagger.NametagBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/** Wraps a player's display name with the configured left / right tier tags. */
@Mixin(PlayerEntity.class)
public abstract class PlayerNametagMixin {

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void tiertagger$addTags(CallbackInfoReturnable<Text> cir) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (!(self instanceof AbstractClientPlayerEntity)) return;

        Config cfg = Config.get();
        if (!cfg.enabled || !cfg.showNametag) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (!cfg.showOwn && mc.player != null && self.getUuid().equals(mc.player.getUuid())) return;

        String name = self.getNameForScoreboard();
        if (!NametagBuilder.isRealPlayerName(name)) return; // skips NPCs / fake players

        UUID uuid = self.getUuid();
        cir.setReturnValue(NametagBuilder.wrap(cir.getReturnValue(), uuid, name));
    }
}
