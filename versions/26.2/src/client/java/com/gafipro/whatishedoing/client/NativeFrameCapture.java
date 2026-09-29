package com.gafipro.whatishedoing.client;

import com.gafipro.whatishedoing.common.WebRtcCameraSession;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
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

        GpuTexture texture = target.getColorTexture();
        GpuDevice device = RenderSystem.getDevice();
        GpuBuffer buffer = device.createBuffer(
                null,
                GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ,
                (long) width * height * 4L);

        CommandEncoder encoder = device.createCommandEncoder();
        encoder.copyTextureToBuffer(texture, buffer, 0, () -> {
            try (GpuBufferSlice.MappedView view = buffer.map(true, false)) {
                ByteBuffer data = view.data();
                int[] pixels = new int[outputWidth * outputHeight];

                for (int y = 0; y < outputHeight; y++) {
                    int sourceY = height - 1 - (int) (((long) y * height) / outputHeight);
                    for (int x = 0; x < outputWidth; x++) {
                        int sourceX = (int) (((long) x * width) / outputWidth);
                        int raw = data.getInt((sourceX + sourceY * width) * 4);
                        pixels[y * outputWidth + x] =
                                ARGB.fromABGR(0xFF000000 | raw);
                    }
                }

                session.pushArgbFrame(outputWidth, outputHeight, pixels);
            } finally {
                buffer.close();
            }
        }, 0);
    }
}
