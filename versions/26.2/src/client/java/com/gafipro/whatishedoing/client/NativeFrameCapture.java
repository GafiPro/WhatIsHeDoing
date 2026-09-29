package com.gafipro.whatishedoing.client;

import com.gafipro.whatishedoing.common.WebRtcCameraSession;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import net.minecraft.util.ARGB;

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

        double scale = Math.min(
                1.0,
                Math.min((double) maxWidth / width, (double) maxHeight / height));

        int outputWidth = Math.max(2, (int) Math.round(width * scale));
        int outputHeight = Math.max(2, (int) Math.round(height * scale));

        if ((outputWidth & 1) != 0) outputWidth--;
        if ((outputHeight & 1) != 0) outputHeight--;

        GpuDevice device = RenderSystem.getDevice();
        CommandEncoder encoder = device.createCommandEncoder();
        long byteSize = (long) width * height * 4L;

        try (GpuBuffer buffer = device.createBuffer(
                null,
                GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ,
                byteSize)) {

            encoder.copyTextureToBuffer(
                    target.getColorTexture(),
                    buffer,
                    0,
                    () -> {},
                    0);

            GpuFence fence = encoder.createFence();
            encoder.submit();
            fence.awaitCompletion(Long.MAX_VALUE);
            fence.close();

            try (GpuBufferSlice.MappedView view = buffer.map(true, false)) {
                ByteBuffer data = view.data();
                int[] pixels = new int[outputWidth * outputHeight];

                for (int y = 0; y < outputHeight; y++) {
                    int sourceY = (int) (((long) y * height) / outputHeight);
                    int flippedY = height - sourceY - 1;

                    for (int x = 0; x < outputWidth; x++) {
                        int sourceX = (int) (((long) x * width) / outputWidth);
                        int raw = data.getInt((sourceX + flippedY * width) * 4);
                        pixels[y * outputWidth + x] =
                                ARGB.fromABGR(0xFF000000 | raw);
                    }
                }

                session.pushArgbFrame(outputWidth, outputHeight, pixels);
            }
        }
    }
}
