package com.gafipro.whatishedoing.common;

import java.util.Objects;
import java.util.prefs.Preferences;

/**
 * Local policy for the client whose Minecraft view may be shared.
 *
 * It is intentionally stored locally and is not controlled by a Minecraft
 * server. The default is disabled until the user explicitly enables sharing.
 */
public final class SharePolicy {
    private static final String KEY = "allowCameraRequests";
    private final Preferences prefs;

    public SharePolicy(String scope) {
        prefs = Preferences.userRoot().node("WhatIsHeDoing").node(Objects.requireNonNull(scope));
    }

    public boolean allowsRequests() {
        return prefs.getBoolean(KEY, false);
    }

    public void setAllowsRequests(boolean allowed) {
        prefs.putBoolean(KEY, allowed);
    }
}
