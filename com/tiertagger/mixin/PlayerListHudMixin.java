package com.tiertagger.mixin;

import com.tiertagger.Config;
import com.tiertagger.NametagBuilder;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds tier tags to the tab list (player list). */
@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {

    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true, require = 0)
    private void tiertagger$addTags(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        Config cfg = Config.get();
        if (!cfg.enabled || !cfg.showTab) return;
        String name = entry.getProfile().name();
        if (!NametagBuilder.isRealPlayerName(name)) return;
        cir.setReturnValue(NametagBuilder.wrap(cir.getReturnValue(), entry.getProfile().id(), name));
    }
}
