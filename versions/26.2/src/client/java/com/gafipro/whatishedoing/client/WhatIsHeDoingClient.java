package com.gafipro.whatishedoing.client;

import com.gafipro.whatishedoing.common.PresenceClient;
import com.gafipro.whatishedoing.common.RemoteCameraSession;
import com.google.gson.JsonObject;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import com.mojang.brigadier.arguments.StringArgumentType;

import java.net.URI;

@Environment(EnvType.CLIENT)
public final class WhatIsHeDoingClient implements ClientModInitializer {
    private static final String SIGNALING_URL =
            System.getProperty("wihd.signalingUrl", "ws://127.0.0.1:8787");

    private static PresenceClient presence;
    private static RemoteCameraSession camera;
    private static boolean announced;

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
            announced = false;
            return;
        }

        if (!announced) {
            announced = true;
            try {
                presence = new PresenceClient(
                        URI.create(SIGNALING_URL),
                        new PresenceClient.Listener() {
                            @Override
                            public void onMessage(JsonObject message) {
                                if ("signal".equals(message.get("type").getAsString()) && camera != null) {
                                    camera.onSignal(message.getAsJsonObject("payload"));
                                }
                            }

                            @Override
                            public void onConnectionChanged(boolean connected) {
                                // No chat spam.
                            }
                        });
                camera = new RemoteCameraSession(presence);
                presence.connect(client.player.getName().getString(), "26.2");
            } catch (RuntimeException ignored) {
                presence = null;
                camera = null;
            }
        }
    }

    private static void startWatching(String target) {
        if (presence == null || !presence.isConnected() || camera == null) {
            return;
        }
        camera.start(target);
    }

    private static void stopWatching() {
        if (camera != null) {
            camera.stop();
        }
    }
}
