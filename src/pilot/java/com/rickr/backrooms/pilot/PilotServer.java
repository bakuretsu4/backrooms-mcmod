package com.rickr.backrooms.pilot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Localhost-only line protocol: one JSON object per request line, one JSON object per response
 * line. Socket threads never touch game state themselves; every request is handed to the client
 * thread (the only thread allowed to read the client world or press keys) and the answer is
 * written back once it has run.
 */
final class PilotServer {
    private PilotServer() {}

    static void start(int port, Pilot pilot) {
        Thread acceptor = new Thread(() -> acceptLoop(port, pilot), "backrooms-pilot-accept");
        acceptor.setDaemon(true); // must never keep the game process alive
        acceptor.start();
    }

    private static void acceptLoop(int port, Pilot pilot) {
        // Loopback only: this is a remote control for the local test harness, not a network service.
        // Explicit IPv4: getLoopbackAddress() can resolve to ::1, which the Python client's 127.0.0.1 default can't reach.
        try (ServerSocket server = new ServerSocket(port, 4, InetAddress.getByName("127.0.0.1"))) {
            PilotMod.LOG.info("[pilot] test pilot listening on 127.0.0.1:{}", port);
            while (true) {
                Socket socket = server.accept();
                Thread t = new Thread(() -> serve(socket, pilot), "backrooms-pilot-conn");
                t.setDaemon(true);
                t.start();
            }
        } catch (IOException e) {
            PilotMod.LOG.error("[pilot] test pilot server stopped", e);
        }
    }

    private static void serve(Socket socket, Pilot pilot) {
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            socket.setTcpNoDelay(true);
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                out.write(answer(line, pilot).toString());
                out.write('\n');
                out.flush();
            }
        } catch (IOException ignored) {
            // client went away; nothing to clean up beyond the socket itself
        }
    }

    private static JsonObject answer(String line, Pilot pilot) {
        JsonObject req;
        try {
            JsonElement parsed = JsonParser.parseString(line);
            if (!parsed.isJsonObject()) {
                return Pilot.error("request must be a JSON object");
            }
            req = parsed.getAsJsonObject();
        } catch (RuntimeException e) {
            return Pilot.error("bad JSON: " + e.getMessage());
        }
        JsonObject res;
        try {
            // Runs on the client thread between ticks/frames, also while menus or loading screens show.
            res = Minecraft.getInstance().submit(() -> pilot.handle(req)).get(10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            res = Pilot.error("client thread did not answer within 10s (frozen or loading?)");
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            PilotMod.LOG.warn("[pilot] request failed: {}", line, cause);
            res = Pilot.error(cause.getClass().getSimpleName() + ": " + cause.getMessage());
        }
        if (req.has("id")) {
            res.add("id", req.get("id")); // lets a client match answers to requests
        }
        return res;
    }
}
