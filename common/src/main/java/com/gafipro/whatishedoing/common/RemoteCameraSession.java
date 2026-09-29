package com.gafipro.whatishedoing.common;

import com.google.gson.JsonObject;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Version-neutral lifecycle for one remote camera session.
 *
 * The actual WebRTC media implementation is supplied by the version-specific
 * client module because rendering and frame extraction are Minecraft-version-specific.
 */
public final class RemoteCameraSession {
    public enum State {
        IDLE,
        REQUESTING,
        CONNECTING,
        LIVE,
        STOPPING
    }

    private final PresenceClient signaling;
    private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);
    private volatile String target;

    public RemoteCameraSession(PresenceClient signaling) {
        this.signaling = signaling;
    }

    public State state() {
        return state.get();
    }

    public String target() {
        return target;
    }

    public void start(String target) {
        this.target = target;
        state.set(State.REQUESTING);
        signaling.requestCamera(target);
    }

    public void onSignal(JsonObject payload) {
        state.compareAndSet(State.REQUESTING, State.CONNECTING);
    }

    public void markLive() {
        state.set(State.LIVE);
    }

    public void stop() {
        String current = target;
        state.set(State.STOPPING);
        if (current != null) {
            signaling.stopCamera(current);
        }
        target = null;
        state.set(State.IDLE);
    }
}
