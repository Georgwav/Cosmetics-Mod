#!/usr/bin/env python3
"""A stand-in for Cider (the Apple Music client) and LRCLIB, for the in-game music shots and self-checks.

Stdlib only. One HTTP server answers Cider's REST routes (/api/v1/playback/*, /api/v1/amapi/run-v3 with the
storefront and syllable lyrics as Apple's TTML), the cover (a generated 300 px PNG), LRCLIB's /api/get, and
Cider's Socket.IO (Engine.IO v4 over a WebSocket: the open packet, the namespace join, pings, and
API:Playback events every second). The song loops; its lyrics cover the whole song, with duet sides and
background vocals, so any moment of a shot is mid-line.

    python3 ci/mock_cider.py [port]        # serve until Ctrl+C
    import mock_cider; s = mock_cider.start(10767); ...; s.shutdown()
"""
import base64
import hashlib
import json
import socket
import struct
import sys
import threading
import time
import zlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

TITLE, ARTIST, ALBUM = "Sol Sunset", "The Pixel Suns", "Ember Solstice"
DURATION_MS = 200_000
START_S = 62.0
CATALOG_ID = "1789001234"
LINES = [
    "The sun goes down on the ember sea",
    "Every block is burning gold for me",
    "Hold the light before it fades away",
    "Pixel skies at the end of the day",
    "We were dancing where the embers rise",
    "Square sun sinking in your eyes",
    "Stay a little longer in the glow",
    "Count the stars as they start to show",
]
LINE_MS = 5_000
_started = time.time()
_playing = True


def position_s():
    return (START_S + (time.time() - _started)) % (DURATION_MS / 1000.0) if _playing else START_S


def ttml():
    """Apple's syllable lyrics: a <p> per line (5 s), a <span> per word, v1/v2 sides, an x-bg line every third."""
    def t(ms):
        return "%d:%06.3f" % (ms // 60000, (ms % 60000) / 1000.0)
    body = []
    for i, start in enumerate(range(0, DURATION_MS - LINE_MS, LINE_MS)):
        text = LINES[i % len(LINES)]
        words = text.split(" ")
        span = (LINE_MS - 600) // len(words)
        parts = []
        for k, w in enumerate(words):
            ws = start + k * span
            parts.append('<span begin="%s" end="%s">%s</span>' % (t(ws), t(ws + span), w))
        main = " ".join(parts)
        if i % 3 == 2:
            bs = start + LINE_MS - 1500
            main += ' <span ttm:role="x-bg"><span begin="%s" end="%s">(ooh,</span> <span begin="%s" end="%s">ooh)</span></span>' % (
                t(bs), t(bs + 600), t(bs + 600), t(bs + 1200))
        body.append('<p begin="%s" end="%s" ttm:agent="%s">%s</p>' % (t(start), t(start + LINE_MS), "v1" if i % 2 == 0 else "v2", main))
    return ('<tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata" xml:lang="en">'
            '<head><metadata><ttm:agent type="person" xml:id="v1"/><ttm:agent type="person" xml:id="v2"/></metadata></head>'
            '<body><div>%s</div></body></tt>' % "".join(body))


def lrc():
    out = []
    for i, start in enumerate(range(0, DURATION_MS - LINE_MS, LINE_MS)):
        out.append("[%02d:%05.2f] %s" % (start // 60000, (start % 60000) / 1000.0, LINES[i % len(LINES)]))
    return "\n".join(out)


def cover_png(size=300):
    """A sunset cover: a deep violet to orange sky, a square sun, dark dunes."""
    rows = []
    for y in range(size):
        row = bytearray([0])
        k = y / (size - 1)
        sky = (int(40 + 215 * k), int(16 + 110 * k * k), int(80 - 40 * k))
        for x in range(size):
            r, g, b = sky
            if abs(x - size * 0.5) < size * 0.14 and abs(y - size * 0.55) < size * 0.14:
                r, g, b = 255, 214, 120
            dune = size * (0.78 + 0.05 * ((x / size - 0.5) ** 2) * 4)
            if y > dune:
                r, g, b = 60, 22, 18
            row += bytes((r, g, b))
        rows.append(bytes(row))
    raw = zlib.compress(b"".join(rows), 6)

    def chunk(kind, data):
        c = struct.pack(">I", len(data)) + kind + data
        return c + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", raw) + chunk(b"IEND", b""))


COVER = cover_png()
TTML = ttml()


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        pass

    def _json(self, obj, status=200):
        body = json.dumps(obj).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _info(self):
        port = self.server.server_address[1]
        return {"name": TITLE, "artistName": ARTIST, "albumName": ALBUM, "durationInMillis": DURATION_MS,
                "currentPlaybackTime": position_s(), "playParams": {"id": "i.mock", "catalogId": CATALOG_ID},
                "artwork": {"url": "http://127.0.0.1:%d/artwork/{w}x{h}.png" % port, "width": 300, "height": 300}}

    def do_GET(self):
        path = self.path.split("?")[0]
        if path.startswith("/socket.io/") and "websocket" in (self.headers.get("Upgrade") or "").lower():
            return self._socket()
        if path == "/api/v1/playback/now-playing":
            return self._json({"status": "ok", "info": self._info()})
        if path == "/api/v1/playback/is-playing":
            return self._json({"status": "ok", "is_playing": _playing})
        if path.startswith("/artwork/"):
            self.send_response(200)
            self.send_header("Content-Type", "image/png")
            self.send_header("Content-Length", str(len(COVER)))
            self.end_headers()
            self.wfile.write(COVER)
            return
        if path == "/api/get":
            return self._json({"trackName": TITLE, "artistName": ARTIST, "instrumental": False,
                               "plainLyrics": "\n".join(LINES), "syncedLyrics": lrc()})
        if path == "/api/search":
            return self._json([])
        self._json({"error": "not found"}, 404)

    def do_POST(self):
        global _playing, _started
        length = int(self.headers.get("Content-Length") or 0)
        body = self.rfile.read(length) if length else b""
        path = self.path.split("?")[0]
        if path == "/api/v1/amapi/run-v3":
            want = json.loads(body or b"{}").get("path", "")
            if "storefront" in want:
                return self._json({"data": {"data": [{"id": "us", "type": "storefronts"}]}})
            if "/syllable-lyrics" in want or "/lyrics" in want:
                return self._json({"data": {"data": [{"id": CATALOG_ID, "attributes": {"ttml": TTML}}]}})
            return self._json({"data": {"data": []}})
        if path == "/api/v1/playback/playpause":
            pos = position_s()
            _playing = not _playing
            if _playing:
                _started = time.time() - (pos - START_S)
            return self._json({"status": "ok"})
        if path in ("/api/v1/playback/next", "/api/v1/playback/previous"):
            return self._json({"status": "ok"})
        self._json({"error": "not found"}, 404)

    # ---------------------------------------------------------------- Socket.IO (Engine.IO v4) over a WebSocket

    def _socket(self):
        key = self.headers.get("Sec-WebSocket-Key", "")
        accept = base64.b64encode(hashlib.sha1((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").encode()).digest()).decode()
        self.send_response(101, "Switching Protocols")
        self.send_header("Upgrade", "websocket")
        self.send_header("Connection", "Upgrade")
        self.send_header("Sec-WebSocket-Accept", accept)
        self.end_headers()
        self.wfile.flush()
        sock = self.connection
        lock = threading.Lock()
        alive = [True]

        def send(text):
            data = text.encode()
            head = bytes([0x81])
            n = len(data)
            head += bytes([n]) if n < 126 else bytes([126]) + struct.pack(">H", n) if n < 65536 else bytes([127]) + struct.pack(">Q", n)
            with lock:
                sock.sendall(head + data)

        def recv_exact(n):
            buf = b""
            while len(buf) < n:
                part = sock.recv(n - len(buf))
                if not part:
                    raise ConnectionError
                buf += part
            return buf

        def reader():
            try:
                while alive[0]:
                    b0, b1 = recv_exact(2)
                    op, n = b0 & 0x0F, b1 & 0x7F
                    if n == 126:
                        n = struct.unpack(">H", recv_exact(2))[0]
                    elif n == 127:
                        n = struct.unpack(">Q", recv_exact(8))[0]
                    mask = recv_exact(4) if b1 & 0x80 else b"\0\0\0\0"
                    payload = bytes(c ^ mask[i % 4] for i, c in enumerate(recv_exact(n)))
                    if op == 8:
                        break
                    if op == 9:
                        continue
                    text = payload.decode(errors="replace")
                    if text.startswith("40"):
                        send('40{"sid":"mock"}')
                        send('42["API:Playback",' + json.dumps({"type": "playbackStatus.nowPlayingItemDidChange",
                                                                 "data": self._info()}) + "]")
            except (OSError, ConnectionError, ValueError):
                pass
            alive[0] = False

        send('0{"sid":"mock","upgrades":[],"pingInterval":25000,"pingTimeout":20000,"maxPayload":1000000}')
        threading.Thread(target=reader, daemon=True).start()
        ticks = 0
        try:
            while alive[0]:
                time.sleep(1.0)
                ticks += 1
                send('42["API:Playback",' + json.dumps({"type": "playbackStatus.playbackTimeDidChange", "data": {
                    "isPlaying": _playing, "currentPlaybackTime": position_s(),
                    "currentPlaybackTimeRemaining": DURATION_MS / 1000.0 - position_s()}}) + "]")
                if ticks % 20 == 0:
                    send("2")
        except OSError:
            pass
        alive[0] = False
        self.close_connection = True


def start(port=10767):
    server = ThreadingHTTPServer(("127.0.0.1", port), Handler)
    server.daemon_threads = True
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server


if __name__ == "__main__":
    s = start(int(sys.argv[1]) if len(sys.argv) > 1 else 10767)
    print("mock Cider on http://127.0.0.1:%d" % s.server_address[1])
    try:
        while True:
            time.sleep(3600)
    except KeyboardInterrupt:
        s.shutdown()
