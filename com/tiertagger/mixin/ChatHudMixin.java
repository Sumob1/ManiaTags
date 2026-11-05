package com.tiertagger.mixin;

import com.tiertagger.ChatTagger;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Adds tier tags next to player names in chat messages. */
@Mixin(ChatHud.class)
public abstract class ChatHudMixin {

    @ModifyVariable(
        method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
        at = @At("HEAD"), argsOnly = true, require = 0)
    private Text tiertagger$tagNames(Text message) {
        return ChatTagger.process(message);
    }
}
