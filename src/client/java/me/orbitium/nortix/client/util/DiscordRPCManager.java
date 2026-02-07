package me.orbitium.nortix.client.util;

import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;

public class DiscordRPCManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DiscordRPCManager.class);
    private static final int PORT = 14545;
    private static final String HOST = "127.0.0.1";

    public static void setActivity(String name, String address, String state, String version, long startTimestamp,
            String largeImageKey) {
        JsonObject activity = new JsonObject();
        activity.addProperty("name", name);
        activity.addProperty("address", address);
        activity.addProperty("state", state);
        activity.addProperty("version", version);
        activity.addProperty("startTimestamp", startTimestamp);
        activity.addProperty("largeImageKey", largeImageKey);

        JsonObject root = new JsonObject();
        root.addProperty("type", "setActivity");
        root.add("activity", activity);

        send(root.toString());
    }

    public static void clearActivity() {
        JsonObject root = new JsonObject();
        root.addProperty("type", "clearActivity");
        send(root.toString());
    }

    private static void send(String json) {
        CompletableFuture.runAsync(() -> {
            try (Socket socket = new Socket(HOST, PORT);
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                out.println(json);
                out.flush();
                LOGGER.debug("Sent Discord RPC update: {}", json);
            } catch (IOException e) {
                LOGGER.error("Failed to send Discord RPC update to launcher: {}", e.getMessage());
            }
        });
    }
}
