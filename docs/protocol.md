# WhatIsHeDoing protocol

The Minecraft versions deliberately share an application protocol instead of Minecraft packet code.

## Messages

hello

    {"type":"hello","player":"Gabriel","clientVersion":"1.21.11"}

presence_request

    {"type":"presence_request"}

presence

    {"type":"presence","users":["Alex","Gabriel"]}

camera_request

    {"type":"camera_request","target":"Alex"}

camera_stop

    {"type":"camera_stop","target":"Alex"}

signal

    {"type":"signal","target":"Alex","payload":{...}}

The payload is intentionally opaque to the relay. It carries WebRTC signaling data such as SDP descriptions and ICE candidates.

## Media

The intended media path is:

    Minecraft render framebuffer
            |
            v
      custom WebRTC video source
            |
            v
        peer connection
            |
       direct/relay path

The signaling server only establishes the peer connection. TURN is the fallback for clients that cannot establish a direct path.
