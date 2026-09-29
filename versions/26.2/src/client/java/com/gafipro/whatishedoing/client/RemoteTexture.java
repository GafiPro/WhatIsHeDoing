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
    private long lastSequence = -1L;

    public void update(RemoteVideoFrame.Snapshot snapshot) {
        if (snapshot.sequence() == lastSequence) {
            return;
        }

        NativeImage image = new NativeImage(
                NativeImage.Format.RGBA,
                snapshot.width(),
                snapshot.height(),
                false);

        int[] pixels = snapshot.argb();
        for (int y = 0; y < snapshot.height(); y++) {
            for (int x = 0; x < snapshot.width(); x++) {
                image.setPixelRGBA(x, y, pixels[y * snapshot.width() + x]);
            }
        }

        Minecraft client = Minecraft.getInstance();

        if (texture == null) {
            texture = new DynamicTexture(image);
            client.getTextureManager().register(LOCATION, texture);
        } else {
            texture.setPixels(image);
            texture.upload();
        }

        lastSequence = snapshot.sequence();
    }

    public ResourceLocation location() {
        return LOCATION;
    }

    public void clear() {
        lastSequence = -1L;
    }
}
