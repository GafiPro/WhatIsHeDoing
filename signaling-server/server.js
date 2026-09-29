import { WebSocketServer } from "ws";

const PORT = Number(process.env.PORT || 8787);
const HOST = process.env.HOST || "0.0.0.0";

const wss = new WebSocketServer({ host: HOST, port: PORT });
const clients = new Map();

function send(ws, object) {
  if (ws.readyState === ws.OPEN) {
    ws.send(JSON.stringify(object));
  }
}

function broadcastPresence() {
  const users = [...clients.keys()].sort((a, b) => a.localeCompare(b));
  for (const ws of clients.values()) {
    send(ws, { type: "presence", users });
  }
}

function unregister(ws) {
  const name = ws.wihdPlayer;
  if (name && clients.get(name) === ws) {
    clients.delete(name);
    broadcastPresence();
  }
}

wss.on("connection", ws => {
  ws.wihdPlayer = null;

  ws.on("message", raw => {
    let message;
    try {
      message = JSON.parse(raw.toString());
    } catch {
      return;
    }

    if (message.type === "hello") {
      const player = String(message.player || "").trim();
      const clientVersion = String(message.clientVersion || "unknown").trim();

      if (!/^[A-Za-z0-9_]{1,16}$/.test(player)) {
        send(ws, { type: "error", code: "invalid_name" });
        ws.close(1008, "invalid player name");
        return;
      }

      const old = clients.get(player);
      if (old && old !== ws) {
        old.close(4001, "duplicate player session");
      }

      ws.wihdPlayer = player;
      ws.wihdClientVersion = clientVersion;
      clients.set(player, ws);
      broadcastPresence();
      return;
    }

    if (message.type === "presence_request") {
      const users = [...clients.keys()].sort((a, b) => a.localeCompare(b));
      send(ws, { type: "presence", users });
      return;
    }

    if (message.type === "camera_request") {
      if (!ws.wihdPlayer) return;

      const target = String(message.target || "");
      const targetSocket = clients.get(target);
      if (!targetSocket) {
        send(ws, { type: "camera_unavailable", target });
        return;
      }

      send(targetSocket, {
        type: "camera_request",
        from: ws.wihdPlayer,
        fromClientVersion: ws.wihdClientVersion
      });
      return;
    }

    if (message.type === "camera_stop") {
      if (!ws.wihdPlayer) return;

      const target = String(message.target || "");
      const targetSocket = clients.get(target);
      if (targetSocket) {
        send(targetSocket, {
          type: "camera_stop",
          from: ws.wihdPlayer
        });
      }
      return;
    }

    if (message.type === "signal") {
      if (!ws.wihdPlayer) return;

      const target = String(message.target || "");
      const targetSocket = clients.get(target);
      if (!targetSocket) {
        send(ws, { type: "signal_unavailable", target });
        return;
      }

      send(targetSocket, {
        type: "signal",
        from: ws.wihdPlayer,
        payload: message.payload || {}
      });
    }
  });

  ws.on("close", () => unregister(ws));
  ws.on("error", () => unregister(ws));
});

console.log("WhatIsHeDoing signaling server listening on ws://" + HOST + ":" + PORT);
