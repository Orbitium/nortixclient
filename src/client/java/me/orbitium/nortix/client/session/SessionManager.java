package me.orbitium.nortix.client.session;

import me.orbitium.nortix.client.api.AuthApiClient;
import me.orbitium.nortix.client.api.AuthApiClient.ExchangeRedeemResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.net.URI;
import net.minecraft.util.Util;

/**
 * Manages the mod's session lifecycle including authentication and token
 * management
 */
public class SessionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionManager.class);
    private static final String EXCHANGE_CODE_PROPERTY = "craftcorps.exchange_code";
    private static final String DEVICE_ID_FILE = "craftcorps_device.dat";
    private static final String REFRESH_TOKEN_FILE = "craftcorps_refresh.dat";

    private static SessionManager instance;
    private AuthApiClient authClient;
    private ModSession session;
    private String deviceId;
    private HeartbeatManager heartbeatManager;
    private PresenceManager presenceManager;
    private ModAuthManager modAuthManager;

    private SessionManager() {
        this.authClient = new AuthApiClient();
        this.heartbeatManager = new HeartbeatManager(this);
        this.presenceManager = new PresenceManager(this);
        this.modAuthManager = new ModAuthManager(this, authClient);
    }

    public static SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
    }

    /**
     * Initialize the session by attempting to redeem an exchange code
     * 
     * @return CompletableFuture that completes when initialization is done
     */
    public CompletableFuture<Boolean> initialize() {
        LOGGER.info("Initializing SessionManager...");

        // Load or generate device ID
        this.deviceId = loadOrGenerateDeviceId();
        LOGGER.info("Device ID: {}", deviceId);

        // Check for exchange code (from launcher)
        String exchangeCode = System.getProperty(EXCHANGE_CODE_PROPERTY);
        if (exchangeCode == null || exchangeCode.trim().isEmpty()) {
            // Check environment variable as fallback
            exchangeCode = System.getenv("CRAFTCORPS_EXCHANGE_CODE");
        }

        if (exchangeCode != null && !exchangeCode.trim().isEmpty()) {
            LOGGER.info("Exchange code found, redeeming...");
            return redeemExchangeCode(exchangeCode);
        }

        // Try to restore from saved refresh token
        String savedRefreshToken = loadRefreshToken();
        if (savedRefreshToken != null) {
            LOGGER.info("Saved refresh token found, attempting to restore session...");
            return refreshSession(savedRefreshToken)
                    .thenApply(success -> {
                        if (success) {
                            LOGGER.info("Session restored from saved refresh token!");
                            return true;
                        } else {
                            LOGGER.warn("Saved refresh token invalid, attempting fallback auth...");
                            attemptFallbackAuthentication();
                            return false;
                        }
                    });
        }

        // No exchange code, no saved refresh token - try fallback
        LOGGER.info("No exchange code or saved token. Attempting fallback authentication...");
        return attemptFallbackAuthentication();
    }

    /**
     * Attempt fallback authentication methods (Mojang -> Manual Device Flow)
     */
    private CompletableFuture<Boolean> attemptFallbackAuthentication() {
        LOGGER.info("Attempting fallback authentication (Premium -> Device Flow)...");
        return modAuthManager.authenticateWithMojang()
                .thenApply(result -> {
                    if (result.success) {
                        LOGGER.info("Mojang authentication successful for: {}", result.username);
                        return true;
                    } else {
                        LOGGER.info("==========================================");
                        LOGGER.info("MOJANG AUTH NOT AVAILABLE");
                        LOGGER.info("Reason: {}", result.errorMessage);
                        LOGGER.info("Device Flow available via Sign In button");
                        LOGGER.info("==========================================");
                        // Do NOT automatically start Device Flow
                        // User must click "Click To Sign In" button
                        return false;
                    }
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Mojang authentication failed EXCEPTIONALLY", throwable);
                    return false;
                });
    }

    /**
     * Start the device flow authentication
     */
    private void startDeviceFlow() {
        modAuthManager.startDeviceFlowAuth(
                info -> {
                    LOGGER.info("==========================================");
                    LOGGER.info("DEVICE AUTHENTICATION REQUIRED");
                    LOGGER.info("Please visit: {}", info.verificationUrl);
                    Util.getOperatingSystem().open(URI.create(info.verificationUrl));
                    LOGGER.info("Enter code: {}", info.userCode);
                    LOGGER.info("Expires in: {} seconds", info.expiresInSeconds);
                    LOGGER.info("==========================================");
                },
                result -> {
                    if (result.success) {
                        LOGGER.info("Device flow authentication successful for: {}", result.username);
                    } else {
                        LOGGER.warn("Device flow authentication failed: {}", result.errorMessage);
                    }
                });
    }

    /**
     * Redeem an exchange code to obtain session tokens
     */
    private CompletableFuture<Boolean> redeemExchangeCode(String exchangeCode) {
        return authClient.redeemExchangeCode(exchangeCode, deviceId)
                .thenApply(response -> {
                    LOGGER.info("Successfully redeemed exchange code");
                    createSession(response);
                    return true;
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to redeem exchange code", throwable);
                    return false;
                });
    }

    /**
     * Create a session from the exchange redeem response
     */
    private void createSession(ExchangeRedeemResponse response) {
        String logUsername = response.user != null ? response.user.username
                : response.accessToken.substring(0, Math.min(5, response.accessToken.length()));
        LOGGER.info("[SessionManager] Creating new session for user: {}", logUsername);
        this.session = new ModSession(
                response.accessToken,
                response.refreshToken,
                response.user.id,
                response.user.username);

        LOGGER.info("[SessionManager] Session established successfully. Starting heartbeat...");

        // Save refresh token for future restoration
        saveRefreshToken(response.refreshToken);

        // Start heartbeat
        heartbeatManager.start();
    }

    /**
     * Refresh the current session tokens
     */
    public CompletableFuture<Boolean> refreshSession() {
        if (session == null) {
            LOGGER.warn("Cannot refresh session: no active session");
            return CompletableFuture.completedFuture(false);
        }

        return refreshSession(session.getRefreshToken());
    }

    /**
     * Refresh session using a specific refresh token
     */
    private CompletableFuture<Boolean> refreshSession(String refreshToken) {
        LOGGER.info("Refreshing session tokens...");
        return authClient.refreshSession(refreshToken)
                .thenApply(response -> {
                    // Create/update session with new tokens
                    if (session != null) {
                        session.setAccessToken(response.accessToken);
                        session.setRefreshToken(response.refreshToken);
                    } else {
                        // Session was null, recreate it (happens during restore)
                        // Note: We don't have user info from refresh, so we'll use a portion of the
                        // token as username
                        String fallbackName = response.accessToken.substring(0,
                                Math.min(5, response.accessToken.length()));
                        LOGGER.warn(
                                "Session recreated from refresh token (userId/username unknown). Using fallback: {}",
                                fallbackName);
                        this.session = new ModSession(response.accessToken, response.refreshToken, fallbackName,
                                fallbackName);
                    }

                    // Save the new refresh token
                    saveRefreshToken(response.refreshToken);
                    LOGGER.info("Session tokens refreshed successfully");

                    // Start heartbeat if not already running
                    if (!heartbeatManager.isRunning()) {
                        heartbeatManager.start();
                    }

                    return true;
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to refresh session", throwable);
                    // Session is now invalid, clear saved token
                    invalidateSession();
                    clearRefreshToken();
                    return false;
                });
    }

    /**
     * Get the current access token, refreshing if needed
     */
    public CompletableFuture<String> getAccessToken() {
        if (session == null) {
            LOGGER.info("[SessionManager] getAccessToken called but no session exists");
            return CompletableFuture.completedFuture(null);
        }

        // Check if token needs refresh
        if (session.isAccessTokenExpired()) {
            LOGGER.info("[SessionManager] Access token expired, initiating auto-refresh");
            return refreshSession().thenApply(success -> {
                if (success) {
                    LOGGER.info("[SessionManager] Auto-refresh successful, returning new token");
                    return session.getAccessToken();
                } else {
                    LOGGER.error("[SessionManager] Auto-refresh failed");
                    return null;
                }
            });
        }

        return CompletableFuture.completedFuture(session.getAccessToken());
    }

    /**
     * Get the current session (may be null if not authenticated)
     */
    public ModSession getSession() {
        return session;
    }

    /**
     * Check if the mod is currently authenticated
     */
    public boolean isAuthenticated() {
        return session != null;
    }

    /**
     * Invalidate the current session
     */
    public void invalidateSession() {
        if (session != null) {
            LOGGER.info("Session invalidated for user: {}", session.getUsername());
            heartbeatManager.stop();
            session = null;
        }
        // Note: We don't clear refresh token here - it may still be valid
    }

    /**
     * Shutdown the session manager
     */
    public void shutdown() {
        LOGGER.info("Shutting down SessionManager");
        heartbeatManager.stop();
        invalidateSession();
        authClient.shutdown();
    }

    /**
     * Load device ID from file or generate a new one
     */
    private String loadOrGenerateDeviceId() {
        Path deviceIdPath = getDeviceIdPath();

        try {
            if (Files.exists(deviceIdPath)) {
                String id = Files.readString(deviceIdPath).trim();
                if (!id.isEmpty()) {
                    LOGGER.info("Loaded existing device ID");
                    return id;
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to load device ID from file", e);
        }

        // Generate new device ID
        String newId = "mod-" + UUID.randomUUID().toString();
        try {
            Files.createDirectories(deviceIdPath.getParent());
            Files.writeString(deviceIdPath, newId);
            LOGGER.info("Generated and saved new device ID");
        } catch (IOException e) {
            LOGGER.error("Failed to save device ID to file", e);
        }

        return newId;
    }

    /**
     * Get the path to the device ID file
     */
    private Path getDeviceIdPath() {
        // Store in .minecraft/craftcorps/
        String minecraftDir = System.getProperty("user.dir");
        return Paths.get(minecraftDir, "craftcorps", DEVICE_ID_FILE);
    }

    /**
     * Load refresh token from disk
     */
    private String loadRefreshToken() {
        Path tokenPath = getRefreshTokenPath();
        try {
            if (Files.exists(tokenPath)) {
                String token = Files.readString(tokenPath).trim();
                if (!token.isEmpty()) {
                    LOGGER.info("Loaded saved refresh token from disk");
                    return token;
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to load refresh token from disk", e);
        }
        return null;
    }

    /**
     * Save refresh token to disk
     */
    private void saveRefreshToken(String refreshToken) {
        Path tokenPath = getRefreshTokenPath();
        try {
            Files.createDirectories(tokenPath.getParent());
            Files.writeString(tokenPath, refreshToken);
            LOGGER.info("Saved refresh token to disk");
        } catch (IOException e) {
            LOGGER.error("Failed to save refresh token to disk", e);
        }
    }

    /**
     * Clear saved refresh token
     */
    private void clearRefreshToken() {
        Path tokenPath = getRefreshTokenPath();
        try {
            if (Files.exists(tokenPath)) {
                Files.delete(tokenPath);
                LOGGER.info("Cleared saved refresh token");
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to delete refresh token file", e);
        }
    }

    /**
     * Get the path to the refresh token file
     */
    private Path getRefreshTokenPath() {
        String minecraftDir = System.getProperty("user.dir");
        return Paths.get(minecraftDir, "craftcorps", REFRESH_TOKEN_FILE);
    }

    AuthApiClient getAuthClient() {
        return authClient;
    }

    /**
     * Get the presence manager
     */
    public PresenceManager getPresenceManager() {
        return presenceManager;
    }

    /**
     * Get the mod authentication manager for direct auth flows
     */
    public ModAuthManager getModAuthManager() {
        return modAuthManager;
    }

    /**
     * Get the device ID (public accessor for ModAuthManager)
     */
    public String getDeviceId() {
        return deviceId;
    }

    /**
     * Create a session from tokens (used by ModAuthManager for direct auth)
     *
     * @param accessToken  The access token
     * @param refreshToken The refresh token
     * @param userId       The user's ID
     * @param username     The user's username
     */
    public void createSessionFromTokens(String accessToken, String refreshToken, String userId, String username) {
        LOGGER.info("[SessionManager] Direct token session creation for user: {}", username);
        this.session = new ModSession(accessToken, refreshToken, userId, username);
        LOGGER.info("[SessionManager] Mod session established for: {} (ID: {})", username, userId);

        // Start heartbeat
        LOGGER.info("[SessionManager] Starting heartbeat manager...");
        heartbeatManager.start();

        // Save refresh token for future session restoration
        saveRefreshToken(refreshToken);
    }

    /**
     * Manually start Device Flow authentication (triggered by user clicking button)
     */
    public void startDeviceFlowManually() {
        LOGGER.info("[SessionManager] User manually triggered Device Flow authentication");
        startDeviceFlow();
    }

    /**
     * Get the heartbeat manager
     */
    public HeartbeatManager getHeartbeatManager() {
        return heartbeatManager;
    }

    /**
     * Handle session expiry by invalidating current session and re-authenticating
     */
    public void handleSessionExpiry() {
        LOGGER.warn("[SessionManager] Handling session expiry...");

        // Invalidate current session (stops heartbeat)
        invalidateSession();

        // Attempt to re-authenticate using fallback auth
        LOGGER.info("[SessionManager] Attempting automatic re-authentication...");
        attemptFallbackAuthentication()
                .thenAccept(success -> {
                    if (success) {
                        LOGGER.info("[SessionManager] ✓ Session restored successfully!");
                    } else {
                        LOGGER.warn("[SessionManager] ✗ Failed to restore session automatically");
                        LOGGER.warn("[SessionManager] User may need to manually authenticate");
                    }
                })
                .exceptionally(throwable -> {
                    LOGGER.error("[SessionManager] Exception during auto re-auth", throwable);
                    return null;
                });
    }
}
