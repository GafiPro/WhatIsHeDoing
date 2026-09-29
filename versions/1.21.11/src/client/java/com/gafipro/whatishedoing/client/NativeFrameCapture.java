package com.gafipro.whatishedoing.client;

import com.gafipro.whatishedoing.common.WebRtcCameraSession;
import net.minecraft.client.renderer.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;

public final class NativeFrameCapture {
    private NativeFrameCapture() {}

    public static void capture(
            RenderTarget target,
            int maxWidth,
            int maxHeight,
            WebRtcCameraSession session) {

        int width = target.width;
        int height = target.height;

        NativeImage image = new NativeImage(NativeImage.Format.RGBA, width, height, false);
        try {
            image.downloadTexture(0, true);

            int outputWidth = width;
            int outputHeight = height;

            double scale = Math.min(
                    1.0,
                    Math.min((double) maxWidth / width, (double) maxHeight / height));

            outputWidth = Math.max(2, (int) Math.round(width * scale));
            outputHeight = Math.max(2, (int) Math.round(height * scale));

            if ((outputWidth & 1) != 0) outputWidth--;
            if ((outputHeight & 1) != 0) outputHeight--;

            int[] pixels = new int[outputWidth * outputHeight];

            for (int y = 0; y < outputHeight; y++) {
                int sy = (int) (((long) y * height) / outputHeight);
                for (int x = 0; x < outputWidth; x++) {
                    int sx = (int) (((long) x * width) / outputWidth);
                    pixels[y * outputWidth + x] = image.getPixelRGBA(sx, sy);
                }
            }

            session.pushArgbFrame(outputWidth, outputHeight, pixels);
        } finally {
            image.close();
        }
    }
}
