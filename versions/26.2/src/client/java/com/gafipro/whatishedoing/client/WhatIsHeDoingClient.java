package com.gafipro.whatishedoing.client;

import com.gafipro.whatishedoing.common.PresenceClient;
import com.gafipro.whatishedoing.common.RemoteVideoFrame;
import com.gafipro.whatishedoing.common.SharePolicy;
import com.gafipro.whatishedoing.common.WebRtcCameraSession;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.mojang.blaze3d.pipeline.RenderTarget;

import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;

@Environment(EnvType.CLIENT)
public final class WhatIsHeDoingClient implements ClientModInitializer {
    private static final String SIGNALING_URL =
            System.getProperty("wihd.signalingUrl", "ws://127.0.0.1:8787");
    private static final long CAPTURE_INTERVAL_NS = 100_000_000L;
    private static final int MAX_WIDTH = 640;
    private static final int MAX_HEIGHT = 360;

    private static PresenceClient presence;
    private static WebRtcCameraSession camera;
    private static final SharePolicy sharePolicy = new SharePolicy("26.2");
    private static final RemoteVideoFrame remoteFrame = new RemoteVideoFrame();
    private static final RemoteTexture remoteTexture = new RemoteTexture();
    private static final AtomicBoolean initialized = new AtomicBoolean();
    private static long lastCaptureNs;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(WhatIsHeDoingClient::tick);

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, commandBuildContext) -> {
            dispatcher.register(
                    ClientCommands.literal("whatishedoing")
                            .then(ClientCommands.argument("player", StringArgumentType.word())
                                    .suggests((context, builder) -> {
                                        if (presence != null) {
                                            presence.getOnlinePlayers().forEach(builder::suggest);
                                        }
                                        return builder.buildFuture();
                                    })
                                    .executes(context -> {
                                        startWatching(StringArgumentType.getString(context, "player"));
                                        return 1;
                                    }))
                            .then(ClientCommands.literal("stop")
                                    .executes(context -> {
                                        stopWatching();
                                        return 1;
                                    }))
            );
        });
    }

    private static void tick(Minecraft client) {
        if (client.player == null) {
            initialized.set(false);
            return;
        }

        if (!initialized.getAndSet(true)) {
            try {
                presence = new PresenceClient(
                        URI.create(SIGNALING_URL),
                        new PresenceClient.Listener() {
                            @Override
                            public void onMessage(JsonObject message) {
                                if (camera == null || !message.has("type")) {
                                    return;
                                }

                                String type = message.get("type").getAsString();
                                switch (type) {
                                    case "camera_request" ->
                                            camera.prepareSharer(message.get("from").getAsString());
                                    case "camera_stop" ->
                                            camera.handleStop(message.get("from").getAsString());
                                    case "signal" ->
                                            camera.handleSignal(
                                                    message.get("from").getAsString(),
                                                    message.getAsJsonObject("payload"));
                                    case "camera_unavailable" ->
                                            camera.stop();
                                    default -> {
                                    }
                                }
                            }

                            @Override
                            public void onConnectionChanged(boolean connected) {
                                if (!connected && camera != null) {
                                    camera.stop();
                                }
                            }
                        });

                camera = new WebRtcCameraSession(
                        presence,
                        sharePolicy.allowsRequests(),
                        remoteFrame,
                        new WebRtcCameraSession.Listener() {
                            @Override
                            public void onLive() {
                            }

                            @Override
                            public void onClosed() {
                                remoteTexture.clear();
                            }

                            @Override
                            public void onError(String message) {
                            }
                        });

                presence.connect(client.player.getName().getString(), "26.2");
            } catch (RuntimeException ignored) {
                presence = null;
                camera = null;
            }
        }
    }

    private static void startWatching(String target) {
        if (presence != null && presence.isConnected() && camera != null) {
            camera.startViewer(target);
        }
    }

    private static void stopWatching() {
        if (camera != null) {
            camera.stop();
        }
    }

    public static void afterGameRender() {
        Minecraft client = Minecraft.getInstance();

        if (camera == null
                || camera.role() != WebRtcCameraSession.Role.SHARER
                || !sharePolicy.allowsRequests()) {
            return;
        }

        long now = System.nanoTime();
        if (now - lastCaptureNs < CAPTURE_INTERVAL_NS) {
            return;
        }
        lastCaptureNs = now;

        RenderTarget target = client.gameRenderer.mainRenderTarget();
        target.bindRead();
        NativeFrameCapture.capture(target, MAX_WIDTH, MAX_HEIGHT, camera);
    }

    public static void renderRemoteView(GuiGraphicsExtractor guiGraphics) {
        if (camera == null || camera.role() != WebRtcCameraSession.Role.VIEWER) {
            return;
        }

        RemoteVideoFrame.Snapshot snapshot = remoteFrame.snapshot();
        if (snapshot == null) {
            return;
        }

        remoteTexture.update(snapshot);

        guiGraphics.blit(
                remoteTexture.location(),
                0,
                0,
                guiGraphics.guiWidth(),
                guiGraphics.guiHeight(),
                0.0F,
                0.0F,
                1.0F,
                1.0F);
    }
}
