package io.github.georgwav.cosmetics.sol.music;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * A one-shot web server on 127.0.0.1 for a sign-in's return trip: waits for the browser's {@code GET /callback}, takes
 * its {@code code} and {@code state}, answers with a short page, and closes. Only the loopback address is bound.
 */
final class LoopbackServer implements AutoCloseable {
    record Result(String code, String state, String error) {
    }

    private final ServerSocket socket;

    private LoopbackServer(ServerSocket socket) {
        this.socket = socket;
    }

    static LoopbackServer open(int port) throws IOException {
        return new LoopbackServer(new ServerSocket(port, 4, InetAddress.getLoopbackAddress()));
    }

    int port() {
        return socket.getLocalPort();
    }

    /** The callback's parameters, or null if none came within {@code timeoutMs}. Requests for other paths are answered 404. */
    Result await(long timeoutMs) throws IOException {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            socket.setSoTimeout((int) Math.max(1L, Math.min(Integer.MAX_VALUE, end - System.currentTimeMillis())));
            try (Socket s = socket.accept()) {
                s.setSoTimeout(5000);
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                String request = in.readLine();
                Result r = request == null ? null : parse(request);
                OutputStream out = s.getOutputStream();
                if (r == null) {
                    out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                    continue;
                }
                String page = "<!doctype html><meta charset=utf-8><title>Sol Client</title><body style=\"font-family:sans-serif;"
                        + "background:#0b0b10;color:#eee;display:grid;place-items:center;height:90vh\"><p>"
                        + (r.code() != null ? "Signed in. You can close this tab and go back to the game."
                        : "Sign-in was cancelled. You can close this tab.") + "</p>";
                byte[] body = page.getBytes(StandardCharsets.UTF_8);
                out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + body.length
                        + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                out.write(body);
                out.flush();
                return r;
            } catch (SocketTimeoutException e) {
                return null;
            }
        }
        return null;
    }

    /** A request line ({@code GET /callback?code=..&state=.. HTTP/1.1}) as its result; null for any other path. */
    static Result parse(String requestLine) {
        String[] parts = requestLine.split(" ");
        if (parts.length < 2 || !parts[0].equals("GET")) {
            return null;
        }
        String target = parts[1];
        int q = target.indexOf('?');
        String path = q < 0 ? target : target.substring(0, q);
        if (!path.equals("/callback")) {
            return null;
        }
        String code = null, state = null, error = null;
        if (q >= 0) {
            for (String kv : target.substring(q + 1).split("&")) {
                int eq = kv.indexOf('=');
                String k = eq < 0 ? kv : kv.substring(0, eq);
                String v = eq < 0 ? "" : URLDecoder.decode(kv.substring(eq + 1), StandardCharsets.UTF_8);
                switch (k) {
                    case "code" -> code = v;
                    case "state" -> state = v;
                    case "error" -> error = v;
                    default -> {
                    }
                }
            }
        }
        return new Result(code, state, error);
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
