package io.github.georgwav.cosmetics.sol.music;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;

/**
 * Just enough Socket.IO (Engine.IO v4 over a WebSocket) to hear Cider's events: open ({@code 0{...}}), join the
 * default namespace ({@code 40}), answer pings ({@code 2} → {@code 3}), and hand on events ({@code 42["name",data]}).
 * The framing is pure and unit tested; the connection runs on the JDK's WebSocket.
 */
public final class SocketIo {
    private final URI uri;
    private final BiConsumer<String, String> onEvent;
    private final Runnable onClose;
    private volatile WebSocket socket;

    public SocketIo(String host, int port, BiConsumer<String, String> onEvent, Runnable onClose) {
        this.uri = URI.create("ws://" + host + ":" + port + "/socket.io/?EIO=4&transport=websocket");
        this.onEvent = onEvent;
        this.onClose = onClose;
    }

    /** Connects (asynchronously); events and the close are reported on the WebSocket's threads. */
    public void connect(HttpClient client) {
        client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(3)).buildAsync(uri, new WebSocket.Listener() {
            private final StringBuilder text = new StringBuilder();

            @Override
            public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                text.append(data);
                if (last) {
                    String frame = text.toString();
                    text.setLength(0);
                    String reply = reply(frame);
                    if (reply != null) {
                        ws.sendText(reply, true);
                    }
                    String[] event = event(frame);
                    if (event != null) {
                        onEvent.accept(event[0], event[1]);
                    }
                }
                ws.request(1);
                return null;
            }

            @Override
            public CompletionStage<?> onClose(WebSocket ws, int status, String reason) {
                onClose.run();
                return null;
            }

            @Override
            public void onError(WebSocket ws, Throwable error) {
                onClose.run();
            }
        }).whenComplete((ws, error) -> {
            if (error != null) {
                onClose.run();
            } else {
                socket = ws;
            }
        });
    }

    public void close() {
        WebSocket ws = socket;
        socket = null;
        if (ws != null) {
            ws.abort();
        }
    }

    /** What to answer a frame with: the namespace join after the open, a pong to a ping; else null. */
    static String reply(String frame) {
        if (frame.startsWith("0")) {
            return "40";
        }
        if (frame.equals("2")) {
            return "3";
        }
        return null;
    }

    /** An event frame ({@code 42["name",data]}) as {name, data JSON}; null for anything else. */
    static String[] event(String frame) {
        if (!frame.startsWith("42")) {
            return null;
        }
        String body = frame.substring(2).trim();
        if (!body.startsWith("[")) {
            return null;
        }
        try {
            var array = com.google.gson.JsonParser.parseString(body).getAsJsonArray();
            if (array.isEmpty() || !array.get(0).isJsonPrimitive()) {
                return null;
            }
            String name = array.get(0).getAsString();
            String data = array.size() > 1 ? array.get(1).toString() : "null";
            return new String[]{name, data};
        } catch (RuntimeException e) {
            return null;
        }
    }
}
