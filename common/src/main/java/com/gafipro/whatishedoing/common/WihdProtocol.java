package com.gafipro.whatishedoing.common;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Small version-neutral protocol used by both Minecraft targets.
 *
 * The Minecraft versions do not need to share mappings or Minecraft packets:
 * only this JSON protocol is shared.
 */
public final class WihdProtocol {
    private static final Gson GSON = new Gson();

    private WihdProtocol() {}

    public static String hello(String playerName, String clientVersion) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "hello");
        o.addProperty("player", playerName);
        o.addProperty("clientVersion", clientVersion);
        return GSON.toJson(o);
    }

    public static String presenceRequest() {
        JsonObject o = new JsonObject();
        o.addProperty("type", "presence_request");
        return GSON.toJson(o);
    }

    public static String cameraRequest(String target) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "camera_request");
        o.addProperty("target", target);
        return GSON.toJson(o);
    }

    public static String cameraStop(String target) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "camera_stop");
        o.addProperty("target", target);
        return GSON.toJson(o);
    }

    public static String signal(String target, JsonObject payload) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "signal");
        o.addProperty("target", target);
        o.add("payload", payload);
        return GSON.toJson(o);
    }

    public static JsonObject parse(String message) {
        return JsonParser.parseString(message).getAsJsonObject();
    }
}
