package com.gafipro.whatishedoing.mixin;

import com.gafipro.whatishedoing.client.WhatIsHeDoingClient;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public final class GameRendererMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void whatIsHeDoing$captureAndRender(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        WhatIsHeDoingClient.afterGameRender();
    }
}
