package me.orbitium.nortix.client.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Manages periodic heartbeat sending to maintain presence status
 */
public class HeartbeatManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(HeartbeatManager.class);
    private static final long HEARTBEAT_INTERVAL_SECONDS = 30;

    private final SessionManager sessionManager;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> heartbeatTask;
    private boolean running = false;
    private String currentCosmeticsVersion = null;

    public HeartbeatManager(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    /**
     * Start sending heartbeats
     */
    public synchronized void start() {
        if (running) {
            LOGGER.info("Heartbeat already running");
            return;
        }

        LOGGER.info("Starting heartbeat manager (interval: {}s)", HEARTBEAT_INTERVAL_SECONDS);
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "CraftCorps-Heartbeat");
            thread.setDaemon(true);
            return thread;
        });

        startSchedule();
        running = true;
    }

    private void startSchedule() {
        if (heartbeatTask != null && !heartbeatTask.isCancelled()) {
            heartbeatTask.cancel(false);
        }

        // Send first heartbeat immediately, then every 30 seconds
        heartbeatTask = scheduler.scheduleAtFixedRate(
                this::sendHeartbeat,
                0,
                HEARTBEAT_INTERVAL_SECONDS,
                TimeUnit.SECONDS);
    }

    /**
     * Stop sending heartbeats
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }

        LOGGER.info("Stopping heartbeat manager");
        running = false;

        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
            heartbeatTask = null;
        }

        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            scheduler = null;
        }
    }

    /**
     * Forces an immediate heartbeat and resets the schedule
     */
    public synchronized void forceHeartbeat() {
        if (!running || scheduler == null || scheduler.isShutdown()) {
            LOGGER.info("Force heartbeat requested but manager is not running. Starting it...");
            // If not running, start it (which sends one immediately)
            start();
            return;
        }

        LOGGER.info("Forcing immediate heartbeat due to state change...");

        // Cancel current schedule
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
        }

        // Restart schedule (sends one immediately)
        startSchedule();
    }

    /**
     * Send a single heartbeat
     */
    private void sendHeartbeat() {
        if (!sessionManager.isAuthenticated()) {
            LOGGER.info("Skipping heartbeat: not authenticated");
            return;
        }

        sessionManager.getAccessToken()
                .thenCompose(accessToken -> {
                    if (accessToken == null) {
                        LOGGER.warn("Failed to get access token for heartbeat");
                        return null;
                    }

                    // Construct payload
                    PresenceManager pm = sessionManager.getPresenceManager();
                    net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
                    String uuid = client.getSession().getUuidOrNull() != null
                            ? client.getSession().getUuidOrNull().toString()
                            : "";

                    me.orbitium.nortix.client.api.AuthApiClient.HeartbeatPayload payload = new me.orbitium.nortix.client.api.AuthApiClient.HeartbeatPayload(
                            pm.getSessionType(), uuid);

                    payload.serverIp = pm.getCurrentServerIp();
                    payload.serverPort = pm.getCurrentServerPort();
                    payload.serverName = pm.getCurrentServerName();
                    payload.realmId = pm.getCurrentRealmId();
                    payload.realmName = pm.getCurrentRealmName();
                    payload.worldName = pm.getCurrentWorldName();
                    payload.accessToken = accessToken;

                    return sessionManager.getAuthClient().sendHeartbeat(accessToken, payload);
                })
                .thenAccept(result -> {
                    if (result == null) {
                        return;
                    }

                    if (result.sessionExpired) {
                        LOGGER.warn("==========================================");
                        LOGGER.warn("SESSION EXPIRED - SERVER INVALIDATED");
                        LOGGER.warn("Gap in heartbeats exceeded 2 minutes");
                        LOGGER.warn("Triggering automatic re-authentication...");
                        LOGGER.warn("==========================================");

                        // Stop current heartbeat and trigger re-auth
                        stop();
                        sessionManager.handleSessionExpiry();
                    } else if (result.success) {
                        LOGGER.info("Heartbeat sent successfully");

                        // Check for cosmetics update
                        if (result.cosmeticsVersion != null) {
                            if (currentCosmeticsVersion == null) {
                                // First time receiving version, just set it
                                currentCosmeticsVersion = result.cosmeticsVersion;
                            } else if (!result.cosmeticsVersion.equals(currentCosmeticsVersion)) {
                                LOGGER.info("[HeartbeatManager] Cosmetics update detected ({} -> {}), refreshing...",
                                        currentCosmeticsVersion, result.cosmeticsVersion);

                                currentCosmeticsVersion = result.cosmeticsVersion;

                                // Refresh local player's cosmetics
                                me.orbitium.nortix.client.CapeManager.refreshLocalPlayer();
                            }
                        }

                        // Process other updated players
                        if (result.updatedUuids != null && !result.updatedUuids.isEmpty()) {
                            LOGGER.info("[HeartbeatManager] Detected cosmetics updates for {} players",
                                    result.updatedUuids.size());
                            for (String uuidStr : result.updatedUuids) {
                                try {
                                    java.util.UUID u = java.util.UUID.fromString(uuidStr);
                                    me.orbitium.nortix.client.CapeManager.refreshPlayer(u);
                                } catch (IllegalArgumentException e) {
                                    LOGGER.warn("[HeartbeatManager] Invalid UUID received from server: {}", uuidStr);
                                }
                            }
                        }

                        // Update TTL if provided
                        if (result.ttl != null) {
                            me.orbitium.nortix.client.CapeManager.setCacheTtl(result.ttl);
                        }
                    } else {
                        LOGGER.warn("Heartbeat failed (network/server error)");
                    }
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Error sending heartbeat", throwable);
                    return null;
                });
    }

    public boolean isRunning() {
        return running;
    }
}
