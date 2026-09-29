package com.gafipro.whatishedoing.common;

import java.util.Objects;
import java.util.prefs.Preferences;

/**
 * Local sharing policy. Camera sharing is disabled until the player explicitly
 * enables it through the local JVM property or preferences.
 */
public final class SharePolicy {
    private static final String KEY = "allowCameraRequests";
    private final Preferences prefs;

    public SharePolicy(String scope) {
        prefs = Preferences.userRoot().node("WhatIsHeDoing").node(Objects.requireNonNull(scope));
    }

    public boolean allowsRequests() {
        return Boolean.getBoolean("wihd.allowCamera")
                || prefs.getBoolean(KEY, false);
    }

    public void setAllowsRequests(boolean allowed) {
        prefs.putBoolean(KEY, allowed);
    }
}
