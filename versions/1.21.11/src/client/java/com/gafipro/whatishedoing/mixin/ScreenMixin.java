package com.gafipro.whatishedoing.mixin;

import com.gafipro.whatishedoing.client.WhatIsHeDoingClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Inject(method = "renderWithTooltipAndSubtitles", at = @At("TAIL"))
    private void whatIsHeDoing$renderRemote(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        WhatIsHeDoingClient.renderRemoteView(guiGraphics);
    }
}
