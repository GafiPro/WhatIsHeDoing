package com.gafipro.whatishedoing.client;

import com.gafipro.whatishedoing.common.WebRtcCameraSession;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Screenshot;

public final class NativeFrameCapture {
    private NativeFrameCapture() {}

    public static void capture(
            RenderTarget target,
            int maxWidth,
            int maxHeight,
            WebRtcCameraSession session) {

        if (target == null || target.width <= 0 || target.height <= 0) {
            return;
        }

        NativeImage image = Screenshot.takeScreenshot(target);
        try {
            int width = image.getWidth();
            int height = image.getHeight();

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

                    int abgr = image.getPixelRGBA(sourceX, sourceY);
                    int a = (abgr >>> 24) & 0xFF;
                    int b = (abgr >>> 16) & 0xFF;
                    int g = (abgr >>> 8) & 0xFF;
                    int r = abgr & 0xFF;

                    pixels[y * outputWidth + x] =
                            (a << 24) | (r << 16) | (g << 8) | b;
                }
            }

            session.pushArgbFrame(outputWidth, outputHeight, pixels);
        } finally {
            image.close();
        }
    }
}
