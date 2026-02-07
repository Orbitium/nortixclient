package me.orbitium.craftcorpsCosmetics.client.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Handles communication with the CraftCorps Auth Service
 */
public class AuthApiClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthApiClient.class);
    private static final String AUTH_BASE_URL = "https://auth.craftcorps.net/auth";
    private static final String API_BASE_URL = "https://api.craftcorps.net";
    private static final Gson GSON = new Gson();

    private final HttpClient httpClient;

    public AuthApiClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Redeems an exchange code to obtain session tokens
     * 
     * @param exchangeCode The exchange code from the launcher
     * @param deviceId     The mod's device identifier
     * @return A CompletableFuture containing the session response
     */
    public CompletableFuture<ExchangeRedeemResponse> redeemExchangeCode(String exchangeCode, String deviceId) {
        String url = AUTH_BASE_URL + "/exchange/redeem";

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("exchangeCode", exchangeCode);
        requestBody.addProperty("deviceId", deviceId);

        LOGGER.info("[AuthApiClient] Redeeming exchange code for device: {}", deviceId);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    LOGGER.info("[AuthApiClient] Exchange redeem status: {}", response.statusCode());
                    if (response.statusCode() == 200) {
                        try {
                            ExchangeRedeemResponse redeemResponse = GSON.fromJson(response.body(),
                                    ExchangeRedeemResponse.class);
                            LOGGER.info("[AuthApiClient] Successfully parsed exchange tokens for user: {}",
                                    redeemResponse.user != null ? redeemResponse.user.username : "unknown");
                            return redeemResponse;
                        } catch (Exception e) {
                            LOGGER.error("[AuthApiClient] Failed to parse exchange redeem response", e);
                            throw new RuntimeException("Failed to parse response", e);
                        }
                    } else {
                        LOGGER.error("[AuthApiClient] Failed to redeem exchange code. Status: {} Body: {}",
                                response.statusCode(), response.body());
                        throw new RuntimeException("Failed to redeem exchange code: " + response.statusCode());
                    }
                });
    }

    /**
     * Refreshes the session using the refresh token
     * 
     * @param refreshToken The current refresh token
     * @return A CompletableFuture containing new tokens
     */
    public CompletableFuture<RefreshTokenResponse> refreshSession(String refreshToken) {
        String url = AUTH_BASE_URL + "/refresh";

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("refreshToken", refreshToken);

        LOGGER.info("[AuthApiClient] Refreshing session tokens...");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    LOGGER.info("[AuthApiClient] Session refresh status: {}", response.statusCode());
                    if (response.statusCode() == 200) {
                        try {
                            RefreshTokenResponse refreshResponse = GSON.fromJson(response.body(),
                                    RefreshTokenResponse.class);
                            LOGGER.info("[AuthApiClient] Session tokens refreshed successfully");
                            return refreshResponse;
                        } catch (Exception e) {
                            LOGGER.error("[AuthApiClient] Failed to parse refresh token response", e);
                            throw new RuntimeException("Failed to parse response", e);
                        }
                    } else {
                        LOGGER.error("[AuthApiClient] Failed to refresh token. Status: {}", response.statusCode());
                        throw new RuntimeException("Failed to refresh token: " + response.statusCode());
                    }
                });
    }

    /**
     * Sends a heartbeat to maintain presence status
     *
     * @param accessToken The current access token
     * @param payload     The heartbeat payload containing session info
     * @return A CompletableFuture containing the heartbeat result
     */
    public CompletableFuture<HeartbeatResult> sendHeartbeat(String accessToken, HeartbeatPayload payload) {
        String url = API_BASE_URL + "/presence/heartbeat";

        LOGGER.info("[AuthApiClient] Sending heartbeat (Type: {})...", payload.sessionType);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload)))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        LOGGER.info("[AuthApiClient] Heartbeat sent successfully");
                        JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
                        String cosmeticsVersion = json.has("cosmeticsVersion")
                                ? json.get("cosmeticsVersion").getAsString()
                                : null;

                        List<String> updatedUuids = new ArrayList<>();
                        if (json.has("updatedUuids") && json.get("updatedUuids").isJsonArray()) {
                            json.getAsJsonArray("updatedUuids").forEach(e -> updatedUuids.add(e.getAsString()));
                        }

                        Long ttl = json.has("ttl") ? json.get("ttl").getAsLong() : null;

                        return HeartbeatResult.success(cosmeticsVersion, updatedUuids, ttl);
                    } else if (response.statusCode() == 401 || response.statusCode() == 403) {
                        LOGGER.warn("[AuthApiClient] Heartbeat rejected - Session expired ({})", response.statusCode());
                        return HeartbeatResult.sessionExpired();
                    } else {
                        LOGGER.warn("[AuthApiClient] Failed to send heartbeat. Status: {}", response.statusCode());
                        return HeartbeatResult.failure();
                    }
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Error sending heartbeat", throwable);
                    return HeartbeatResult.failure();
                });
    }

    public static class HeartbeatPayload {
        public String sessionType; // SINGLEPLAYER, MULTIPLAYER, REALMS, NONE
        public String playerUuid;

        // Optional Context Fields
        public String serverIp;
        public Integer serverPort;
        public String serverName;
        public String realmId;
        public String realmName;
        public String worldName;
        public String accessToken; // Optional redundant access token field

        public HeartbeatPayload(String sessionType, String playerUuid) {
            this.sessionType = sessionType;
            this.playerUuid = playerUuid;
        }
    }

    /**
     * Notifies the backend that the player joined a server
     * 
     * @param accessToken  The current access token (JWT)
     * @param serverIp     The server IP/Hostname
     * @param serverPort   The server port
     * @param serverName   Optional server name
     * @param activityType The activity type (SINGLEPLAYER, MULTIPLAYER, REALMS)
     * @return A CompletableFuture indicating success or failure
     */
    public CompletableFuture<Boolean> sendServerJoin(String accessToken, String serverIp, int serverPort,
            String serverName, String activityType) {
        String url = API_BASE_URL + "/presence/server/join";

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("accessToken", accessToken); // Explicitly include JWT key in body
        requestBody.addProperty("serverIp", serverIp);
        requestBody.addProperty("serverPort", serverPort);
        requestBody.addProperty("activityType", activityType);

        if (serverName != null && !serverName.isEmpty()) {
            requestBody.addProperty("serverName", serverName);
        }

        LOGGER.info("[AuthApiClient] Sending server join event for {}:{} (Type: {})", serverIp, serverPort,
                activityType);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        LOGGER.info("[AuthApiClient] Server join event confirmed by backend");
                        return true;
                    } else {
                        LOGGER.warn("[AuthApiClient] Failed to send server join event. Status: {}",
                                response.statusCode());
                        return false;
                    }
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Error sending server join event", throwable);
                    return false;
                });
    }

    /**
     * Notifies the backend that the player left a server
     * 
     * @param accessToken The current access token (JWT)
     * @return A CompletableFuture indicating success or failure
     */
    public CompletableFuture<Boolean> sendServerLeave(String accessToken) {
        String url = API_BASE_URL + "/presence/server/leave";

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("accessToken", accessToken); // Explicitly include JWT key in body

        LOGGER.info("[AuthApiClient] Sending server leave event...");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        LOGGER.info("[AuthApiClient] Server leave event confirmed by backend");
                        return true;
                    } else {
                        LOGGER.warn("[AuthApiClient] Failed to send server leave event. Status: {}",
                                response.statusCode());
                        return false;
                    }
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Error sending server leave event", throwable);
                    return false;
                });
    }

    // ========== MOD DIRECT AUTHENTICATION (Mojang) ==========

    /**
     * Initialize Mojang authentication by requesting a server hash challenge.
     * The mod must then join Mojang's session server with this hash.
     *
     * @param uuid The player's Minecraft UUID
     * @return A CompletableFuture containing the server hash challenge
     */
    public CompletableFuture<MojangInitResponse> initMojangAuth(String uuid) {
        String url = AUTH_BASE_URL + "/mod/mojang/init";

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("uuid", uuid);

        LOGGER.info("[AuthApiClient] Initiating Mojang auth for UUID: {}", uuid);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    LOGGER.info("[AuthApiClient] Mojang init status: {}", response.statusCode());
                    if (response.statusCode() == 200) {
                        return GSON.fromJson(response.body(), MojangInitResponse.class);
                    } else {
                        LOGGER.error("[AuthApiClient] Failed to init Mojang auth. Status: {} Body: {}",
                                response.statusCode(), response.body());
                        throw new RuntimeException("Failed to init Mojang auth: " + response.statusCode());
                    }
                });
    }

    /**
     * Verify the Mojang authentication after the player has joined the session
     * server.
     *
     * @param uuid     The player's Minecraft UUID
     * @param username The player's Minecraft username
     * @param deviceId The mod's device identifier
     * @return A CompletableFuture containing session tokens
     */
    public CompletableFuture<MojangVerifyResponse> verifyMojangAuth(String uuid, String username, String deviceId) {
        String url = AUTH_BASE_URL + "/mod/mojang/verify";

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("uuid", uuid);
        requestBody.addProperty("username", username);
        requestBody.addProperty("deviceId", deviceId);

        LOGGER.info("[AuthApiClient] Verifying Mojang auth for user: {} (UUID: {})", username, uuid);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    LOGGER.info("[AuthApiClient] Mojang verify status: {}", response.statusCode());
                    if (response.statusCode() == 200) {
                        return GSON.fromJson(response.body(), MojangVerifyResponse.class);
                    } else {
                        LOGGER.error("[AuthApiClient] Failed to verify Mojang auth. Status: {} Body: {}",
                                response.statusCode(), response.body());
                        throw new RuntimeException("Failed to verify Mojang auth: " + response.statusCode());
                    }
                });
    }

    // ========== MOD WEB-BASED LOGIN (Device Flow) ==========

    /**
     * Initialize device flow authentication.
     * Returns a user code to display and a device code for polling.
     *
     * @param installId The mod's installation ID
     * @return A CompletableFuture containing the device flow codes
     */
    public CompletableFuture<DeviceInitResponse> initDeviceAuth(String installId) {
        String url = AUTH_BASE_URL + "/mod/device/init";

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("installId", installId);

        LOGGER.info("[AuthApiClient] Initiating device flow auth for install ID: {}", installId);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    LOGGER.info("[AuthApiClient] Device init status: {}", response.statusCode());
                    if (response.statusCode() == 200) {
                        return GSON.fromJson(response.body(), DeviceInitResponse.class);
                    } else {
                        LOGGER.error("[AuthApiClient] Failed to init device auth. Status: {} Body: {}",
                                response.statusCode(), response.body());
                        throw new RuntimeException("Failed to init device auth: " + response.statusCode());
                    }
                });
    }

    /**
     * Poll for device flow authorization status.
     * Returns tokens if authorized, or a pending/expired status.
     *
     * @param deviceCode The device code from initDeviceAuth
     * @return A CompletableFuture containing the poll result
     */
    public CompletableFuture<DevicePollResponse> pollDeviceAuth(String deviceCode) {
        String url = AUTH_BASE_URL + "/mod/device/poll";

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("deviceCode", deviceCode);

        // Use DEBUG to avoid spamming the console every 5 seconds
        LOGGER.debug("[AuthApiClient] Polling device auth status...");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        LOGGER.info("[AuthApiClient] Device authorized!");
                        return GSON.fromJson(response.body(), DevicePollResponse.class);
                    } else if (response.statusCode() == 202) {
                        // 202 Accepted = still pending
                        DevicePollResponse pending = new DevicePollResponse();
                        pending.status = "pending";
                        return pending;
                    } else if (response.statusCode() == 400) {
                        // Check for known "pending" errors (authorization_pending, slow_down)
                        try {
                            JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
                            if (json.has("error")) {
                                String error = json.get("error").getAsString();
                                if ("authorization_pending".equals(error) || "slow_down".equals(error)) {
                                    DevicePollResponse pending = new DevicePollResponse();
                                    pending.status = "pending";
                                    // Don't log anything for normal pending state
                                    return pending;
                                }
                            }
                        } catch (Exception ignored) {
                            // If parsing fails, fall through to error logging
                        }

                        LOGGER.warn("[AuthApiClient] Device poll returned 400: {}", response.body());
                        throw new RuntimeException("Device poll failed: " + response.body());
                    } else if (response.statusCode() == 410) {
                        LOGGER.warn("[AuthApiClient] Device flow expired (410)");
                        DevicePollResponse expired = new DevicePollResponse();
                        expired.status = "expired";
                        return expired;
                    } else {
                        LOGGER.error("[AuthApiClient] Failed to poll device auth. Status: {} Body: {}",
                                response.statusCode(), response.body());
                        throw new RuntimeException("Failed to poll device auth: " + response.statusCode());
                    }
                });
    }

    public void shutdown() {
        // HttpClient doesn't need explicit shutdown in modern Java
    }

    // ========== RESPONSE DATA CLASSES ==========

    public static class ExchangeRedeemResponse {
        public String accessToken;
        public String refreshToken;
        public UserInfo user;

        public static class UserInfo {
            public String id;
            public String username;
        }
    }

    public static class RefreshTokenResponse {
        public String accessToken;
        public String refreshToken;
    }

    // Mojang Auth Responses
    public static class MojangInitResponse {
        public String serverHash;
        public String serverId; // May be used for session server join
        public long expiresIn; // Seconds until challenge expires
    }

    public static class MojangVerifyResponse {
        public String accessToken;
        public String refreshToken;
        public UserInfo user;

        public static class UserInfo {
            public String id;
            public String username;
        }
    }

    // Device Flow Responses
    public static class DeviceInitResponse {
        public String userCode; // Code to display to user (e.g., "ABCD-1234")
        public String deviceCode; // Code for polling (internal)
        public String verificationUrl; // URL where user enters the code
        public int expiresIn; // Seconds until codes expire
        public int interval; // Recommended polling interval in seconds
    }

    public static class DevicePollResponse {
        public String status; // "pending", "authorized", "expired", "denied"
        public String accessToken; // Only present when status = "authorized"
        public String refreshToken; // Only present when status = "authorized"
        public UserInfo user; // Only present when status = "authorized"

        public static class UserInfo {
            public String id;
            public String username;
        }

        public boolean isPending() {
            return "pending".equals(status);
        }

        public boolean isAuthorized() {
            return "authorized".equals(status);
        }

        public boolean isExpired() {
            return "expired".equals(status);
        }

        public boolean isDenied() {
            return "denied".equals(status);
        }
    }

    /**
     * Result of a heartbeat request
     */
    public static class HeartbeatResult {
        public final boolean success;
        public final boolean sessionExpired;
        public final String cosmeticsVersion;
        public final List<String> updatedUuids;
        public final Long ttl;

        private HeartbeatResult(boolean success, boolean sessionExpired, String cosmeticsVersion,
                List<String> updatedUuids, Long ttl) {
            this.success = success;
            this.sessionExpired = sessionExpired;
            this.cosmeticsVersion = cosmeticsVersion;
            this.updatedUuids = updatedUuids;
            this.ttl = ttl;
        }

        public static HeartbeatResult success(String cosmeticsVersion, List<String> updatedUuids, Long ttl) {
            return new HeartbeatResult(true, false, cosmeticsVersion, updatedUuids, ttl);
        }

        public static HeartbeatResult failure() {
            return new HeartbeatResult(false, false, null, Collections.emptyList(), null);
        }

        public static HeartbeatResult sessionExpired() {
            return new HeartbeatResult(false, true, null, Collections.emptyList(), null);
        }
    }
}
