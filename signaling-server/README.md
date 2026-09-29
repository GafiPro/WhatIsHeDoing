# WhatIsHeDoing signaling server

This server provides two small pieces of infrastructure:

- global presence, so /whatishedoing <player> can autocomplete players who have the mod online
- WebRTC signaling, so clients can exchange offers, answers and ICE candidates

It does not receive the Minecraft video stream.

## Run locally

Requires Node.js.

    npm install
    npm start

The default port is 8787.

Set the system property wihd.signalingUrl in the Minecraft launch JVM properties to point clients at the server, for example:

    -Dwihd.signalingUrl=wss://your-domain.example/ws
