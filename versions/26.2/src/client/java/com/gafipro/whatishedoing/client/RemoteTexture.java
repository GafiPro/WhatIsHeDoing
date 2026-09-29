package com.gafipro.whatishedoing.client;

import com.gafipro.whatishedoing.common.RemoteVideoFrame;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

public final class RemoteTexture {
    private static final Identifier LOCATION =
            Identifier.fromNamespaceAndPath("whatishedoing", "remote_view");

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
            texture = new DynamicTexture(
                    () -> "WhatIsHeDoing remote view",
                    width,
                    height,
                    false);
            client.getTextureManager().register(LOCATION, texture);
        }

        NativeImage pixels = texture.getPixels();
        if (pixels == null) {
            return;
        }

        int[] argb = snapshot.argb();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels.setPixel(x, y, ARGB.toABGR(argb[y * width + x]));
            }
        }

        texture.upload();
        lastSequence = snapshot.sequence();
    }

    public Identifier location() {
        return LOCATION;
    }

    public void clear() {
        lastSequence = -1L;
    }
}
