package com.gafipro.whatishedoing.mixin;

import com.gafipro.whatishedoing.client.WhatIsHeDoingClient;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public final class GuiMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void whatIsHeDoing$renderRemote(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        WhatIsHeDoingClient.renderRemoteView(guiGraphics);
    }
}
