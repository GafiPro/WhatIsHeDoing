package com.gafipro.whatishedoing.client;

import com.gafipro.whatishedoing.common.PresenceClient;
import com.gafipro.whatishedoing.common.RemoteVideoFrame;
import com.gafipro.whatishedoing.common.SharePolicy;
import com.gafipro.whatishedoing.common.WebRtcCameraSession;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.pipeline.RenderTarget;

import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WhatIsHeDoingClient implements ClientModInitializer {
    private static final String SIGNALING_URL =
            System.getProperty("wihd.signalingUrl", "ws://127.0.0.1:8787");
    private static final long CAPTURE_INTERVAL_NS = 33_333_333L;
    private static final int MAX_WIDTH = 1280;
    private static final int MAX_HEIGHT = 720;

    private static PresenceClient presence;
    private static WebRtcCameraSession camera;
    private static final SharePolicy sharePolicy = new SharePolicy("1.21.11");
    private static final RemoteVideoFrame remoteFrame = new RemoteVideoFrame();
    private static final RemoteTexture remoteTexture = new RemoteTexture();
    private static final AtomicBoolean initialized = new AtomicBoolean();
    private static long lastCaptureNs;
    private static long lastPresenceRefreshNs;
    private static long lastConnectAttemptNs;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(WhatIsHeDoingClient::tick);

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                    ClientCommandManager.literal("whatishedoing")
                            .then(ClientCommandManager.argument("player", StringArgumentType.word())
                                    .suggests((context, builder) -> {
                                        if (presence != null && presence.isConnected()) {
                                            presence.requestPresence();
                                            presence.getOnlinePlayers().forEach(builder::suggest);
                                        }
                                        return builder.buildFuture();
                                    })
                                    .executes(context -> {
                                        startWatching(StringArgumentType.getString(context, "player"));
                                        return 1;
                                    }))
                            .then(ClientCommandManager.literal("stop")
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

        long now = System.nanoTime();

        if (!initialized.get() && now - lastConnectAttemptNs >= 5_000_000_000L) {
            lastConnectAttemptNs = now;
            initialized.set(true);
            try {
                presence = new PresenceClient(
                        URI.create(SIGNALING_URL),
                        new PresenceClient.Listener() {
                            @Override
                            public void onMessage(JsonObject message) {
                                if (camera == null || !message.has("type")) {
                                    return;
                                }

                                switch (message.get("type").getAsString()) {
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
                                if (!connected) {
                                    if (camera != null) {
                                        camera.stop();
                                    }
                                    initialized.set(false);
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

                presence.connect(client.player.getName().getString(), "1.21.11");
            } catch (RuntimeException ignored) {
                presence = null;
                camera = null;
                initialized.set(false);
            }
        }

        if (presence != null && presence.isConnected()
                && now - lastPresenceRefreshNs >= 3_000_000_000L) {
            lastPresenceRefreshNs = now;
            presence.requestPresence();
        }
    }

    private static void startWatching(String target) {
        Minecraft client = Minecraft.getInstance();

        if (presence == null || !presence.isConnected() || camera == null) {
            client.player.sendSystemMessage(Component.literal("VeilCull: session service is offline."));
            return;
        }

        if (target == null || target.isBlank()) {
            client.player.sendSystemMessage(Component.literal("VeilCull: enter a player name."));
            return;
        }

        if (!presence.getOnlinePlayers().contains(target)) {
            client.player.sendSystemMessage(Component.literal("VeilCull: player '" + target + "' is not online."));
            return;
        }

        camera.startViewer(target);
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

        RenderTarget target = client.getMainRenderTarget();
        NativeFrameCapture.capture(target, MAX_WIDTH, MAX_HEIGHT, camera);
    }

    public static void renderRemoteView(GuiGraphics graphics) {
        if (camera == null || camera.role() != WebRtcCameraSession.Role.VIEWER) {
            return;
        }

        RemoteVideoFrame.Snapshot snapshot = remoteFrame.snapshot();
        if (snapshot == null) {
            return;
        }

        remoteTexture.update(snapshot);
        graphics.blit(
                net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                remoteTexture.location(),
                0,
                0,
                0.0F,
                0.0F,
                Minecraft.getInstance().getWindow().getGuiScaledWidth(),
                Minecraft.getInstance().getWindow().getGuiScaledHeight(),
                snapshot.width(),
                snapshot.height());
    }
}
