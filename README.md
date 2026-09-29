# WhatIsHeDoing

WhatIsHeDoing is a client-side Fabric mod prototype that lets one Minecraft client request a live view of another modded client, even when the players are on different Minecraft servers.

## Targets

- Minecraft 1.21.11 — Java 21
- Minecraft 26.2 — Java 25

The two targets share the same application-level signaling protocol, so a 1.21.11 client can communicate with a 26.2 client.

## Architecture

Minecraft server connectivity is not used for the camera stream.

1. Each client connects to the WhatIsHeDoing signaling service and publishes only presence/signaling metadata.
2. /whatishedoing <player> starts a request.
3. Signaling exchanges the WebRTC offer/answer and ICE candidates.
4. The final media path is peer-to-peer when possible, with TURN available as a fallback.
5. /whatishedoing stop closes the session.

The mod is intended to capture the Minecraft render output only, not the operating-system desktop, microphone, webcam, or other applications.

## Current status

This repository is the first implementation slice:

- independent 1.21.11 and 26.2 Fabric projects
- global presence/autocomplete
- cross-version signaling protocol
- WebRTC session abstraction
- signaling relay server
- command lifecycle

The Minecraft framebuffer capture and WebRTC video track implementation are isolated behind a client capture interface so they can be implemented separately for each Minecraft rendering pipeline.

## Building

See the README inside each version directory.

## Signaling server

signaling-server/ contains a small WebSocket relay used only for presence and WebRTC signaling. It never needs to receive the actual video stream.

The development endpoint is ws://127.0.0.1:8787.

## Privacy

The mod is designed around an explicit local camera-sharing setting on the observed client. The video source must never use OS desktop capture APIs.

Not affiliated with Mojang or Microsoft.
