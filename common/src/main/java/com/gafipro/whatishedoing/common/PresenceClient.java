package com.gafipro.whatishedoing.common;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Presence + WebRTC signaling client.
 *
 * Uses only the JDK WebSocket client so there is no Minecraft-server dependency.
 */
public final class PresenceClient implements WebSocket.Listener {
    public interface Listener {
        void onMessage(JsonObject message);
        void onConnectionChanged(boolean connected);
    }

    private final URI endpoint;
    private final Listener listener;
    private final AtomicReference<WebSocket> socket = new AtomicReference<>();
    private final AtomicBoolean connected = new AtomicBoolean();
    private final Set<String> onlinePlayers = new CopyOnWriteArraySet<>();
    private volatile String localPlayer;

    public PresenceClient(URI endpoint, Listener listener) {
        this.endpoint = endpoint;
        this.listener = listener;
    }

    public void connect(String playerName, String clientVersion) {
        localPlayer = playerName;
        HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(endpoint, this)
                .thenAccept(ws -> {
                    socket.set(ws);
                    ws.sendText(WihdProtocol.hello(playerName, clientVersion), true);
                    ws.sendText(WihdProtocol.presenceRequest(), true);
                    connected.set(true);
                    listener.onConnectionChanged(true);
                })
                .exceptionally(error -> {
                    connected.set(false);
                    listener.onConnectionChanged(false);
                    return null;
                });
    }

    public boolean isConnected() {
        return connected.get();
    }

    public List<String> getOnlinePlayers() {
        return onlinePlayers.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public void requestCamera(String target) {
        send(WihdProtocol.cameraRequest(target));
    }

    public void stopCamera(String target) {
        send(WihdProtocol.cameraStop(target));
    }

    public void sendSignal(String target, JsonObject payload) {
        send(WihdProtocol.signal(target, payload));
    }

    private void send(String message) {
        WebSocket ws = socket.get();
        if (ws != null && !ws.isOutputClosed()) {
            ws.sendText(message, true);
        }
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        JsonObject message = WihdProtocol.parse(data.toString());
        String type = message.has("type") ? message.get("type").getAsString() : "";

        if ("presence".equals(type)) {
            onlinePlayers.clear();
            JsonArray users = message.getAsJsonArray("users");
            for (JsonElement user : users) {
                onlinePlayers.add(user.getAsString());
            }
        } else {
            listener.onMessage(message);
        }

        webSocket.request(1);
        return null;
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        connected.set(false);
        socket.compareAndSet(webSocket, null);
        listener.onConnectionChanged(false);
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        connected.set(false);
        listener.onConnectionChanged(false);
    }

    public void close() {
        WebSocket ws = socket.getAndSet(null);
        if (ws != null) {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "stop").join();
        }
        connected.set(false);
        onlinePlayers.clear();
    }
}
