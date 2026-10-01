package com.gafipro.whatishedoing.client;

import com.gafipro.whatishedoing.common.PresenceClient;
import com.gafipro.whatishedoing.common.RemoteVideoFrame;
import com.gafipro.whatishedoing.common.SharePolicy;
import com.gafipro.whatishedoing.common.WebRtcCameraSession;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;

@EventBusSubscriber(
        modid = "whatishedoing",
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.GAME)
public final class VeilCullClient {
    private static final String SIGNALING_URL =
            System.getProperty("wihd.signalingUrl", "ws://127.0.0.1:8787");
    private static final long CAPTURE_INTERVAL_NS = 33_333_333L;
    private static final int MAX_WIDTH = 1280;
    private static final int MAX_HEIGHT = 720;

    private static PresenceClient presence;
    private static WebRtcCameraSession camera;
    private static final SharePolicy sharePolicy = new SharePolicy("1.21.1-neoforge");
    private static final RemoteVideoFrame remoteFrame = new RemoteVideoFrame();
    private static final RemoteTexture remoteTexture = new RemoteTexture();
    private static final AtomicBoolean initialized = new AtomicBoolean();

    private static long lastCaptureNs;
    private static long lastPresenceRefreshNs;
    private static long lastConnectAttemptNs;

    private VeilCullClient() {
    }

    @SubscribeEvent
    public static void registerClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                com.mojang.brigadier.builder.LiteralArgumentBuilder.<net.minecraft.commands.CommandSourceStack>literal("whatishedoing")
                        .then(com.mojang.brigadier.builder.RequiredArgumentBuilder
                                .<net.minecraft.commands.CommandSourceStack, String>argument(
                                        "player", StringArgumentType.word())
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
                        .then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<net.minecraft.commands.CommandSourceStack>literal("stop")
                                .executes(context -> {
                                    stopWatching();
                                    return 1;
                                }))
        );
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();

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

                presence.connect(
                        client.player.getName().getString(),
                        "1.21.1-neoforge");
            } catch (RuntimeException ignored) {
                presence = null;
                camera = null;
                initialized.set(false);
            }
        }

        if (presence != null
                && presence.isConnected()
                && now - lastPresenceRefreshNs >= 3_000_000_000L) {
            lastPresenceRefreshNs = now;
            presence.requestPresence();
        }
    }

    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Post event) {
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

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        renderRemoteView(event.getGuiGraphics());
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        renderRemoteView(event.getGuiGraphics());
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

    private static void renderRemoteView(GuiGraphics graphics) {
        if (camera == null || camera.role() != WebRtcCameraSession.Role.VIEWER) {
            return;
        }

        RemoteVideoFrame.Snapshot snapshot = remoteFrame.snapshot();
        if (snapshot == null) {
            return;
        }

        remoteTexture.update(snapshot);

        int width = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int height = Minecraft.getInstance().getWindow().getGuiScaledHeight();

        graphics.blit(
                remoteTexture.location(),
                0,
                0,
                width,
                height,
                0,
                0,
                snapshot.width(),
                snapshot.height(),
                snapshot.width(),
                snapshot.height());
    }
}
