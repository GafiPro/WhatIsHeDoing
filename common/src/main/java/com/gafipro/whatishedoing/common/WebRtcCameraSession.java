package com.gafipro.whatishedoing.common;

import com.google.gson.JsonObject;
import dev.onvoid.webrtc.CreateSessionDescriptionObserver;
import dev.onvoid.webrtc.PeerConnectionFactory;
import dev.onvoid.webrtc.PeerConnectionObserver;
import dev.onvoid.webrtc.RTCAnswerOptions;
import dev.onvoid.webrtc.RTCConfiguration;
import dev.onvoid.webrtc.RTCIceCandidate;
import dev.onvoid.webrtc.RTCIceServer;
import dev.onvoid.webrtc.RTCOfferOptions;
import dev.onvoid.webrtc.RTCPeerConnection;
import dev.onvoid.webrtc.RTCPeerConnectionState;
import dev.onvoid.webrtc.RTCRtpTransceiver;
import dev.onvoid.webrtc.RTCRtpTransceiverDirection;
import dev.onvoid.webrtc.RTCRtpTransceiverInit;
import dev.onvoid.webrtc.RTCSdpType;
import dev.onvoid.webrtc.RTCSessionDescription;
import dev.onvoid.webrtc.SetSessionDescriptionObserver;
import dev.onvoid.webrtc.media.MediaStreamTrack;
import dev.onvoid.webrtc.media.video.CustomVideoSource;
import dev.onvoid.webrtc.media.video.I420Buffer;
import dev.onvoid.webrtc.media.video.NativeI420Buffer;
import dev.onvoid.webrtc.media.video.VideoFrame;
import dev.onvoid.webrtc.media.video.VideoTrack;
import dev.onvoid.webrtc.media.video.VideoTrackSink;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Version-neutral WebRTC camera session.
 *
 * Viewer: receives exactly one video track.
 * Sharer: sends exactly one video track populated from the Minecraft framebuffer.
 */
public final class WebRtcCameraSession {
    public enum Role {
        NONE, VIEWER, SHARER
    }

    public interface Listener {
        void onLive();
        void onClosed();
        void onError(String message);
    }

    private final PresenceClient signaling;
    private final boolean allowIncomingRequests;
    private final Listener listener;
    private final RemoteVideoFrame remoteFrame;

    private final PeerConnectionFactory factory;
    private final AtomicReference<Role> role = new AtomicReference<>(Role.NONE);
    private final List<RTCIceCandidate> pendingCandidates = new ArrayList<>();

    private RTCPeerConnection peer;
    private CustomVideoSource videoSource;
    private VideoTrack videoTrack;
    private VideoTrackSink remoteSink;
    private String peerName;
    private boolean remoteDescriptionSet;

    public WebRtcCameraSession(
            PresenceClient signaling,
            boolean allowIncomingRequests,
            RemoteVideoFrame remoteFrame,
            Listener listener) {
        this.signaling = signaling;
        this.allowIncomingRequests = allowIncomingRequests;
        this.remoteFrame = remoteFrame;
        this.listener = listener;
        this.factory = new PeerConnectionFactory();
    }

    public Role role() {
        return role.get();
    }

    public RemoteVideoFrame remoteFrame() {
        return remoteFrame;
    }

    public synchronized void startViewer(String target) {
        stopInternal(false);

        if (target == null || target.isBlank()) {
            return;
        }

        role.set(Role.VIEWER);
        peerName = target;

        try {
            createPeer(false);
            signaling.requestCamera(target);
            createOffer();
        } catch (Throwable throwable) {
            fail("Unable to create viewer connection: " + safeMessage(throwable));
        }
    }

    public synchronized void prepareSharer(String requester) {
        if (!allowIncomingRequests || requester == null || requester.isBlank()) {
            return;
        }

        stopInternal(false);

        role.set(Role.SHARER);
        peerName = requester;

        try {
            createPeer(true);
        } catch (Throwable throwable) {
            fail("Unable to prepare camera sharing: " + safeMessage(throwable));
        }
    }

    public synchronized void handleSignal(String from, JsonObject payload) {
        if (from == null || !from.equals(peerName) || payload == null) {
            return;
        }

        String kind = payload.has("kind") ? payload.get("kind").getAsString() : "";

        try {
            switch (kind) {
                case "offer" -> handleOffer(payload);
                case "answer" -> handleAnswer(payload);
                case "candidate" -> handleCandidate(payload);
                default -> {
                }
            }
        } catch (Throwable throwable) {
            fail("WebRTC signaling error: " + safeMessage(throwable));
        }
    }

    public synchronized void handleStop(String from) {
        if (from != null && from.equals(peerName)) {
            stopInternal(false);
        }
    }

    public synchronized void pushArgbFrame(int width, int height, int[] argb) {
        if (role.get() != Role.SHARER || videoSource == null || argb == null
                || argb.length < width * height) {
            return;
        }

        NativeI420Buffer buffer = null;
        VideoFrame frame = null;

        try {
            buffer = NativeI420Buffer.allocate(width, height);
            writeArgbToI420(width, height, argb, buffer);
            frame = new VideoFrame(buffer, System.nanoTime());
            videoSource.pushFrame(frame);
        } catch (Throwable throwable) {
            listener.onError("Video frame error: " + safeMessage(throwable));
        } finally {
            if (frame != null) {
                frame.release();
            } else if (buffer != null) {
                buffer.release();
            }
        }
    }

    public synchronized void stop() {
        stopInternal(true);
    }

    private void createPeer(boolean sender) {
        RTCConfiguration config = new RTCConfiguration();
        RTCIceServer stun = new RTCIceServer();
        stun.urls = new ArrayList<>();
        stun.urls.add("stun:stun.l.google.com:19302");
        config.iceServers = new ArrayList<>();
        config.iceServers.add(stun);

        peer = factory.createPeerConnection(config, new PeerConnectionObserver() {
            @Override
            public void onIceCandidate(RTCIceCandidate candidate) {
                if (peerName != null) {
                    signaling.sendSignal(peerName, candidateJson(candidate));
                }
            }

            @Override
            public void onConnectionChange(RTCPeerConnectionState state) {
                if (state == RTCPeerConnectionState.CONNECTED) {
                    listener.onLive();
                } else if (state == RTCPeerConnectionState.FAILED
                        || state == RTCPeerConnectionState.CLOSED
                        || state == RTCPeerConnectionState.DISCONNECTED) {
                    listener.onClosed();
                }
            }

            @Override
            public void onTrack(RTCRtpTransceiver transceiver) {
                attachIncomingTrack(transceiver.getReceiver().getTrack());
            }
        });

        if (peer == null) {
            throw new IllegalStateException("PeerConnection creation returned null");
        }

        videoSource = new CustomVideoSource();
        videoTrack = factory.createVideoTrack(
                sender ? "wihd-camera" : "wihd-recvonly",
                videoSource
        );

        RTCRtpTransceiverInit init = new RTCRtpTransceiverInit();
        init.direction = sender
                ? RTCRtpTransceiverDirection.SEND_ONLY
                : RTCRtpTransceiverDirection.RECV_ONLY;
        init.streamIds = List.of("wihd");

        peer.addTransceiver(videoTrack, init);
    }

    private void attachIncomingTrack(MediaStreamTrack track) {
        if (track instanceof VideoTrack incoming) {
            remoteSink = remoteFrame::publish;
            incoming.addSink(remoteSink);
        }
    }

    private void createOffer() {
        peer.createOffer(new RTCOfferOptions(), new CreateSessionDescriptionObserver() {
            @Override
            public void onSuccess(RTCSessionDescription description) {
                peer.setLocalDescription(description, new SetSessionDescriptionObserver() {
                    @Override
                    public void onSuccess() {
                        signaling.sendSignal(peerName, sessionDescriptionJson("offer", description));
                    }

                    @Override
                    public void onFailure(String error) {
                        fail("setLocalDescription(offer): " + error);
                    }
                });
            }

            @Override
            public void onFailure(String error) {
                fail("createOffer: " + error);
            }
        });
    }

    private void handleOffer(JsonObject payload) {
        if (role.get() != Role.SHARER) {
            return;
        }

        RTCSessionDescription description = descriptionFromJson(payload);

        peer.setRemoteDescription(description, new SetSessionDescriptionObserver() {
            @Override
            public void onSuccess() {
                remoteDescriptionSet = true;
                flushPendingCandidates();

                peer.createAnswer(new RTCAnswerOptions(), new CreateSessionDescriptionObserver() {
                    @Override
                    public void onSuccess(RTCSessionDescription answer) {
                        peer.setLocalDescription(answer, new SetSessionDescriptionObserver() {
                            @Override
                            public void onSuccess() {
                                signaling.sendSignal(peerName, sessionDescriptionJson("answer", answer));
                            }

                            @Override
                            public void onFailure(String error) {
                                fail("setLocalDescription(answer): " + error);
                            }
                        });
                    }

                    @Override
                    public void onFailure(String error) {
                        fail("createAnswer: " + error);
                    }
                });
            }

            @Override
            public void onFailure(String error) {
                fail("setRemoteDescription(offer): " + error);
            }
        });
    }

    private void handleAnswer(JsonObject payload) {
        if (role.get() != Role.VIEWER) {
            return;
        }

        RTCSessionDescription description = descriptionFromJson(payload);

        peer.setRemoteDescription(description, new SetSessionDescriptionObserver() {
            @Override
            public void onSuccess() {
                remoteDescriptionSet = true;
                flushPendingCandidates();
            }

            @Override
            public void onFailure(String error) {
                fail("setRemoteDescription(answer): " + error);
            }
        });
    }

    private void handleCandidate(JsonObject payload) {
        RTCIceCandidate candidate = new RTCIceCandidate(
                payload.has("sdpMid") ? payload.get("sdpMid").getAsString() : null,
                payload.get("sdpMLineIndex").getAsInt(),
                payload.get("sdp").getAsString()
        );

        if (peer == null || !remoteDescriptionSet) {
            pendingCandidates.add(candidate);
        } else {
            peer.addIceCandidate(candidate);
        }
    }

    private void flushPendingCandidates() {
        if (peer == null || !remoteDescriptionSet) {
            return;
        }

        for (RTCIceCandidate candidate : pendingCandidates) {
            peer.addIceCandidate(candidate);
        }
        pendingCandidates.clear();
    }

    private static JsonObject sessionDescriptionJson(String kind, RTCSessionDescription description) {
        JsonObject payload = new JsonObject();
        payload.addProperty("kind", kind);
        payload.addProperty("sdpType", description.sdpType.name());
        payload.addProperty("sdp", description.sdp);
        return payload;
    }

    private static JsonObject candidateJson(RTCIceCandidate candidate) {
        JsonObject payload = new JsonObject();
        payload.addProperty("kind", "candidate");
        if (candidate.sdpMid != null) {
            payload.addProperty("sdpMid", candidate.sdpMid);
        }
        payload.addProperty("sdpMLineIndex", candidate.sdpMLineIndex);
        payload.addProperty("sdp", candidate.sdp);
        return payload;
    }

    private static RTCSessionDescription descriptionFromJson(JsonObject payload) {
        RTCSdpType type = RTCSdpType.valueOf(payload.get("sdpType").getAsString());
        return new RTCSessionDescription(type, payload.get("sdp").getAsString());
    }

    private static void writeArgbToI420(
            int width,
            int height,
            int[] argb,
            NativeI420Buffer output) {
        ByteBuffer y = output.getDataY();
        ByteBuffer u = output.getDataU();
        ByteBuffer v = output.getDataV();

        int yStride = output.getStrideY();
        int uStride = output.getStrideU();
        int vStride = output.getStrideV();

        for (int py = 0; py < height; py++) {
            int row = py * width;
            int yOffset = py * yStride;

            for (int px = 0; px < width; px++) {
                int rgb = argb[row + px];
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int value = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
                y.put(yOffset + px, (byte) clamp(value));
            }
        }

        for (int py = 0; py < height; py += 2) {
            int uvRow = py >> 1;

            for (int px = 0; px < width; px += 2) {
                int sumU = 0;
                int sumV = 0;
                int count = 0;

                for (int dy = 0; dy < 2 && py + dy < height; dy++) {
                    int row = (py + dy) * width;
                    for (int dx = 0; dx < 2 && px + dx < width; dx++) {
                        int rgb = argb[row + px + dx];
                        int r = (rgb >> 16) & 0xFF;
                        int g = (rgb >> 8) & 0xFF;
                        int b = rgb & 0xFF;

                        sumU += ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
                        sumV += ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;
                        count++;
                    }
                }

                int index = uvRow * uStride + (px >> 1);
                u.put(index, (byte) clamp(sumU / count));
                v.put(uvRow * vStride + (px >> 1), (byte) clamp(sumV / count));
            }
        }
    }

    private void stopInternal(boolean notifyPeer) {
        String oldPeer = peerName;

        if (notifyPeer && oldPeer != null) {
            signaling.stopCamera(oldPeer);
        }

        peerName = null;
        role.set(Role.NONE);
        remoteDescriptionSet = false;
        pendingCandidates.clear();

        if (remoteSink != null && peer != null) {
            for (RTCRtpTransceiver transceiver : peer.getTransceivers()) {
                MediaStreamTrack track = transceiver.getReceiver().getTrack();
                if (track instanceof VideoTrack incoming) {
                    incoming.removeSink(remoteSink);
                }
            }
        }

        remoteSink = null;

        if (videoTrack != null) {
            videoTrack.dispose();
            videoTrack = null;
        }

        if (videoSource != null) {
            videoSource.dispose();
            videoSource = null;
        }

        if (peer != null) {
            peer.close();
            peer = null;
        }

        listener.onClosed();
    }

    private void fail(String message) {
        listener.onError(message);
        stopInternal(false);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static String safeMessage(Throwable throwable) {
        return throwable.getMessage() == null
                ? throwable.getClass().getSimpleName()
                : throwable.getMessage();
    }
}
