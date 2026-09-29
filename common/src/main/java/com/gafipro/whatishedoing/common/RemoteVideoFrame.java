package com.gafipro.whatishedoing.common;

import dev.onvoid.webrtc.media.video.I420Buffer;
import dev.onvoid.webrtc.media.video.VideoFrame;

import java.nio.ByteBuffer;

/**
 * Thread-safe latest-frame store. WebRTC callbacks never touch Minecraft rendering
 * objects; they only publish a CPU-side ARGB copy.
 */
public final class RemoteVideoFrame {
    private final Object lock = new Object();
    private int width;
    private int height;
    private int[] argb;
    private long sequence;

    public void publish(VideoFrame frame) {
        I420Buffer i420 = frame.buffer.toI420();
        int width = i420.getWidth();
        int height = i420.getHeight();

        int[] pixels = new int[width * height];
        ByteBuffer yPlane = i420.getDataY();
        ByteBuffer uPlane = i420.getDataU();
        ByteBuffer vPlane = i420.getDataV();

        int yStride = i420.getStrideY();
        int uStride = i420.getStrideU();
        int vStride = i420.getStrideV();

        for (int y = 0; y < height; y++) {
            int uvY = y >> 1;
            for (int x = 0; x < width; x++) {
                int yy = yPlane.get(y * yStride + x) & 0xFF;
                int uu = uPlane.get(uvY * uStride + (x >> 1)) & 0xFF;
                int vv = vPlane.get(uvY * vStride + (x >> 1)) & 0xFF;

                int c = yy - 16;
                int d = uu - 128;
                int e = vv - 128;

                int r = clamp((298 * c + 409 * e + 128) >> 8);
                int g = clamp((298 * c - 100 * d - 208 * e + 128) >> 8);
                int b = clamp((298 * c + 516 * d + 128) >> 8);

                pixels[y * width + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }

        synchronized (lock) {
            this.width = width;
            this.height = height;
            this.argb = pixels;
            this.sequence++;
        }
    }

    public Snapshot snapshot() {
        synchronized (lock) {
            if (argb == null) {
                return null;
            }
            return new Snapshot(width, height, argb.clone(), sequence);
        }
    }

    public record Snapshot(int width, int height, int[] argb, long sequence) {}

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
