package me.orbitium.craftcorpsCosmetics.client.session;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import me.orbitium.craftcorpsCosmetics.client.api.AuthApiClient;
import me.orbitium.craftcorpsCosmetics.client.api.AuthApiClient.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Handles mod-only authentication flows (without launcher).
 * Supports two methods:
 * 1. Mojang Authentication - For premium Minecraft accounts
 * 2. Device Flow - For offline/cracked accounts (web-based login)
 */
public class ModAuthManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModAuthManager.class);
    private static final String MOJANG_SESSION_URL = "https://sessionserver.mojang.com/session/minecraft/join";
    private static final Gson GSON = new Gson();

    private final SessionManager sessionManager;
    private final AuthApiClient authClient;
    private final HttpClient httpClient;
    private ScheduledExecutorService pollScheduler;
    private String currentDeviceCode;
    private boolean polling = false;

    public ModAuthManager(SessionManager sessionManager, AuthApiClient authClient) {
        this.sessionManager = sessionManager;
        this.authClient = authClient;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // ==================== MOJANG AUTHENTICATION ====================

    /**
     * Authenticate using Mojang session server.
     * This is the flow for premium Minecraft accounts.
     *
     * @return CompletableFuture that completes when authentication is done
     */
    public CompletableFuture<AuthResult> authenticateWithMojang() {
        LOGGER.info("[ModAuthManager] Validating Minecraft session for Mojang auth...");
        MinecraftClient client = MinecraftClient.getInstance();
        Session session = client.getSession();

        if (session == null) {
            LOGGER.error("[ModAuthManager] No Minecraft session available (session is null)");
            return CompletableFuture.completedFuture(
                    AuthResult.failure("No Minecraft session available"));
        }

        String uuid = session.getUuidOrNull() != null ? session.getUuidOrNull().toString() : null;
        String username = session.getUsername();
        String accessToken = session.getAccessToken();

        LOGGER.info("[ModAuthManager] Session Details - Username: {}, UUID: {}, Token Present: {}",
                username, uuid, (accessToken != null && !accessToken.isEmpty()));

        if (uuid == null || accessToken == null || accessToken.isEmpty()) {
            LOGGER.error("[ModAuthManager] Invalid Minecraft session (UUID: {}, Token: {})",
                    uuid, accessToken != null ? "present" : "missing/empty");
            return CompletableFuture.completedFuture(
                    AuthResult.failure("Invalid Minecraft session - are you in offline mode?"));
        }

        // Validate token format roughly (simple check)
        if (accessToken.length() < 10) {
            LOGGER.warn("[ModAuthManager] Access token looks suspiciously short/invalid: '{}'", accessToken);
        }

        LOGGER.info("[ModAuthManager] Starting Mojang authentication for user: {}", username);

        final String finalUuid = uuid;
        final String finalUsername = username;
        final String finalAccessToken = accessToken;

        // Step 1: Get server hash challenge from our backend
        LOGGER.info("[ModAuthManager] Step 1: Requesting server hash challenge from backend...");
        return authClient.initMojangAuth(uuid)
                .thenCompose(initResponse -> {
                    LOGGER.info("[ModAuthManager] Step 2: Joining Mojang session server with hash: {}",
                            initResponse.serverHash);

                    // Step 2: Join Mojang's session server with the hash
                    return joinMojangSessionServer(finalAccessToken, finalUuid, initResponse.serverHash)
                            .thenCompose(success -> {
                                if (!success) {
                                    LOGGER.error("[ModAuthManager] Failed to join Mojang session server");
                                    throw new RuntimeException("Failed to join Mojang session server");
                                }
                                LOGGER.info("[ModAuthManager] Step 3: Verifying join with CraftCorps backend...");

                                // Step 3: Verify with our backend
                                return authClient.verifyMojangAuth(finalUuid, finalUsername,
                                        sessionManager.getDeviceId());
                            });
                })
                .thenApply(verifyResponse -> {
                    LOGGER.info("[ModAuthManager] Step 4: Authentication successful, creating mod session");

                    // Create session
                    sessionManager.createSessionFromTokens(
                            verifyResponse.accessToken,
                            verifyResponse.refreshToken,
                            verifyResponse.user.id,
                            verifyResponse.user.username);

                    return AuthResult.success(verifyResponse.user.username);
                })
                .exceptionally(throwable -> {
                    LOGGER.error("[ModAuthManager] Mojang authentication failed at some step", throwable);
                    return AuthResult.failure(throwable.getMessage());
                });
    }

    /**
     * Join Mojang's session server with the provided server hash.
     * This proves to our backend that the player owns this Minecraft account.
     */
    private CompletableFuture<Boolean> joinMojangSessionServer(String accessToken, String uuid, String serverHash) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("accessToken", accessToken);
        requestBody.addProperty("selectedProfile", uuid.replace("-", ""));
        requestBody.addProperty("serverId", serverHash);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(MOJANG_SESSION_URL))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    // Mojang returns 204 No Content on success
                    LOGGER.info("[ModAuthManager] Mojang join response status: {}", response.statusCode());
                    if (response.statusCode() == 204 || response.statusCode() == 200) {
                        return true;
                    } else {
                        LOGGER.error("[ModAuthManager] Mojang session join failed. Status: {} Body: {}",
                                response.statusCode(), response.body());
                        return false;
                    }
                })
                .exceptionally(throwable -> {
                    LOGGER.error("[ModAuthManager] Exception during Mojang session join", throwable);
                    return false;
                });
    }

    // ==================== DEVICE FLOW AUTHENTICATION ====================

    /**
     * Start device flow authentication.
     * The user will need to visit a URL and enter a code.
     *
     * @param onCodeReceived Callback when the user code is received
     * @param onComplete     Callback when authentication completes (success or
     *                       failure)
     */
    public void startDeviceFlowAuth(
            Consumer<DeviceFlowInfo> onCodeReceived,
            Consumer<AuthResult> onComplete) {

        LOGGER.info("[ModAuthManager] Initiating device flow authentication flow...");

        authClient.initDeviceAuth(sessionManager.getDeviceId())
                .thenAccept(initResponse -> {
                    LOGGER.info("[ModAuthManager] Device flow ready. User code: {}, URL: {}",
                            initResponse.userCode, initResponse.verificationUrl);

                    // Notify caller of the code to display
                    DeviceFlowInfo info = new DeviceFlowInfo(
                            initResponse.userCode,
                            initResponse.verificationUrl,
                            initResponse.expiresIn);
                    onCodeReceived.accept(info);

                    // Start polling for authorization
                    this.currentDeviceCode = initResponse.deviceCode;
                    LOGGER.info("[ModAuthManager] Starting polling with {}s interval (Expires in {}s)",
                            initResponse.interval, initResponse.expiresIn);
                    startPolling(initResponse.interval, initResponse.expiresIn, onComplete);
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to initialize device flow", throwable);
                    onComplete.accept(AuthResult.failure(throwable.getMessage()));
                    return null;
                });
    }

    /**
     * Start polling for device flow authorization
     */
    private void startPolling(int intervalSeconds, int expiresInSeconds, Consumer<AuthResult> onComplete) {
        if (polling) {
            LOGGER.warn("Already polling for device auth");
            return;
        }

        polling = true;
        pollScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "CraftCorps-DeviceAuthPoll");
            thread.setDaemon(true);
            return thread;
        });

        long startTime = System.currentTimeMillis();
        long maxDuration = expiresInSeconds * 1000L;

        pollScheduler.scheduleAtFixedRate(() -> {
            // Check if expired
            if (System.currentTimeMillis() - startTime > maxDuration) {
                LOGGER.warn("Device flow expired");
                stopPolling();
                onComplete.accept(AuthResult.failure("Device code expired. Please try again."));
                return;
            }

            // Poll for status
            authClient.pollDeviceAuth(currentDeviceCode)
                    .thenAccept(pollResponse -> {
                        if (pollResponse.isAuthorized()) {
                            LOGGER.info("Device flow authorized!");
                            stopPolling();

                            // Create session
                            sessionManager.createSessionFromTokens(
                                    pollResponse.accessToken,
                                    pollResponse.refreshToken,
                                    pollResponse.user.id,
                                    pollResponse.user.username);

                            onComplete.accept(AuthResult.success(pollResponse.user.username));
                        } else if (pollResponse.isExpired()) {
                            LOGGER.warn("Device code expired");
                            stopPolling();
                            onComplete.accept(AuthResult.failure("Device code expired. Please try again."));
                        } else if (pollResponse.isDenied()) {
                            LOGGER.warn("Device authorization denied");
                            stopPolling();
                            onComplete.accept(AuthResult.failure("Authorization was denied."));
                        }
                        // If pending, just continue polling
                    })
                    .exceptionally(throwable -> {
                        LOGGER.error("Error polling device auth", throwable);
                        // Don't stop polling on transient errors
                        return null;
                    });
        }, 0, intervalSeconds, TimeUnit.SECONDS);
    }

    /**
     * Stop polling for device flow authorization
     */
    public void stopPolling() {
        polling = false;
        currentDeviceCode = null;

        if (pollScheduler != null) {
            pollScheduler.shutdown();
            try {
                if (!pollScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    pollScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                pollScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            pollScheduler = null;
        }
    }

    /**
     * Cancel any ongoing device flow authentication
     */
    public void cancelDeviceFlow() {
        if (polling) {
            LOGGER.info("Cancelling device flow authentication");
            stopPolling();
        }
    }

    /**
     * Check if device flow polling is active
     */
    public boolean isPolling() {
        return polling;
    }

    // ==================== HELPER CLASSES ====================

    /**
     * Information about an active device flow
     */
    public static class DeviceFlowInfo {
        public final String userCode;
        public final String verificationUrl;
        public final int expiresInSeconds;

        public DeviceFlowInfo(String userCode, String verificationUrl, int expiresInSeconds) {
            this.userCode = userCode;
            this.verificationUrl = verificationUrl;
            this.expiresInSeconds = expiresInSeconds;
        }
    }

    /**
     * Result of an authentication attempt
     */
    public static class AuthResult {
        public final boolean success;
        public final String username;
        public final String errorMessage;

        private AuthResult(boolean success, String username, String errorMessage) {
            this.success = success;
            this.username = username;
            this.errorMessage = errorMessage;
        }

        public static AuthResult success(String username) {
            return new AuthResult(true, username, null);
        }

        public static AuthResult failure(String errorMessage) {
            return new AuthResult(false, null, errorMessage);
        }
    }
}
