package com.gafipro.whatishedoing.client;

import com.gafipro.whatishedoing.common.RemoteVideoFrame;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

public final class RemoteTexture {
    private static final ResourceLocation LOCATION =
            ResourceLocation.fromNamespaceAndPath("whatishedoing", "remote_view");

    private DynamicTexture texture;
    private int width;
    private int height;
    private long lastSequence = -1L;

    public void update(RemoteVideoFrame.Snapshot snapshot) {
        if (snapshot.sequence() == lastSequence) {
            return;
        }

        Minecraft client = Minecraft.getInstance();

        if (texture == null || width != snapshot.width() || height != snapshot.height()) {
            if (texture != null) {
                texture.close();
            }

            width = snapshot.width();
            height = snapshot.height();
            texture = new DynamicTexture(width, height, false);
            client.getTextureManager().register(LOCATION, texture);
        }

        NativeImage pixels = texture.getPixels();
        if (pixels == null) {
            return;
        }

        int[] argb = snapshot.argb();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int color = argb[y * width + x];
                int a = (color >>> 24) & 0xFF;
                int r = (color >>> 16) & 0xFF;
                int g = (color >>> 8) & 0xFF;
                int b = color & 0xFF;

                int abgr = (a << 24) | (b << 16) | (g << 8) | r;
                pixels.setPixelRGBA(x, y, abgr);
            }
        }

        texture.upload();
        lastSequence = snapshot.sequence();
    }

    public ResourceLocation location() {
        return LOCATION;
    }

    public void clear() {
        lastSequence = -1L;
    }
}
