package com.gafipro.whatishedoing.mixin;

import com.gafipro.whatishedoing.client.WhatIsHeDoingClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public final class MinecraftRenderFrameMixin {
    @Inject(
            method = "renderFrame",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/renderpearl/api/device/GpuSurface;present()V"))
    private void whatIsHeDoing$beforePresent(boolean advanceGameTime, CallbackInfo ci) {
        WhatIsHeDoingClient.afterGameRender();
    }
}
