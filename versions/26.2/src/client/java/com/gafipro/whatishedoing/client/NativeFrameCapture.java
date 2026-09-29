package com.gafipro.whatishedoing.client;

import com.gafipro.whatishedoing.common.WebRtcCameraSession;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.RenderTarget;

public final class NativeFrameCapture {
    private NativeFrameCapture() {}

    public static void capture(
            RenderTarget target,
            int maxWidth,
            int maxHeight,
            WebRtcCameraSession session) {

        int width = target.width;
        int height = target.height;

        if (width <= 0 || height <= 0) {
            return;
        }

        NativeImage image = new NativeImage(NativeImage.Format.RGBA, width, height, false);
        try {
            image.downloadTexture(0, true);

            double scale = Math.min(
                    1.0,
                    Math.min((double) maxWidth / width, (double) maxHeight / height));

            int outputWidth = Math.max(2, (int) Math.round(width * scale));
            int outputHeight = Math.max(2, (int) Math.round(height * scale));

            if ((outputWidth & 1) != 0) outputWidth--;
            if ((outputHeight & 1) != 0) outputHeight--;

            int[] pixels = new int[outputWidth * outputHeight];

            for (int y = 0; y < outputHeight; y++) {
                int sourceY = (int) (((long) y * height) / outputHeight);
                for (int x = 0; x < outputWidth; x++) {
                    int sourceX = (int) (((long) x * width) / outputWidth);
                    pixels[y * outputWidth + x] = image.getPixelRGBA(sourceX, sourceY);
                }
            }

            session.pushArgbFrame(outputWidth, outputHeight, pixels);
        } finally {
            image.close();
        }
    }
}
