package me.orbitium.craftcorpsCosmetics.client.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages presence state and server tracking
 */
public class PresenceManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PresenceManager.class);

    private final SessionManager sessionManager;
    private String sessionType = "NONE";
    private String currentServerIp = null;
    private int currentServerPort = 0;
    private String currentServerName = null;
    private String currentRealmId = null;
    private String currentRealmName = null;
    private String currentWorldName = null;

    private double lastX = 0;
    private double lastY = 0;
    private double lastZ = 0;
    private float lastYaw = 0;
    private float lastPitch = 0;
    private boolean isCurrentlyIdle = false;
    private long lastRpcUpdateTime = 0;
    private long sessionStartTime = System.currentTimeMillis();

    public PresenceManager(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    /**
     * Called every client tick to handle idle detection and periodic updates
     */
    public void tick(net.minecraft.client.MinecraftClient client) {
        if (client.player != null) {
            double currentX = client.player.getX();
            double currentY = client.player.getY();
            double currentZ = client.player.getZ();
            float currentYaw = client.player.getYaw();
            float currentPitch = client.player.getPitch();

            // Track physical movement (head and body)
            if (currentX != lastX || currentY != lastY || currentZ != lastZ || currentYaw != lastYaw
                    || currentPitch != lastPitch) {
                me.orbitium.craftcorpsCosmetics.client.util.IdleTracker.recordActivity();
                lastX = currentX;
                lastY = currentY;
                lastZ = currentZ;
                lastYaw = currentYaw;
                lastPitch = currentPitch;
            }
        }

        boolean idleNow = me.orbitium.craftcorpsCosmetics.client.util.IdleTracker.isIdle();
        if (idleNow != isCurrentlyIdle) {
            isCurrentlyIdle = idleNow;
            LOGGER.info("[PresenceManager] Idle state changed to: {}", isCurrentlyIdle ? "IDLE" : "ACTIVE");
            updateDiscordRPC();
        }

        // Periodic update (every 1 minute) while playing to ensure startTimestamp stays
        // relevant
        // and to refresh any small details
        long now = System.currentTimeMillis();
        if (isOnServer() && (now - lastRpcUpdateTime) > 60000) {
            updateDiscordRPC();
        }
    }

    /**
     * Called when the player joins a server/world
     */
    public void onServerJoin(String sessionType, String serverIp, int serverPort, String serverName,
            String realmId, String realmName, String worldName) {

        LOGGER.info("[PresenceManager] Joining session (Type: {})", sessionType);

        this.sessionType = sessionType;
        this.currentServerIp = serverIp;
        this.currentServerPort = serverPort;
        this.currentServerName = serverName;
        this.currentRealmId = realmId;
        this.currentRealmName = realmName;
        this.currentWorldName = worldName;

        // Reset movement tracking
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client.player != null) {
            this.lastX = client.player.getX();
            this.lastY = client.player.getY();
            this.lastZ = client.player.getZ();
            this.lastYaw = client.player.getYaw();
            this.lastPitch = client.player.getPitch();
        }
        me.orbitium.craftcorpsCosmetics.client.util.IdleTracker.recordActivity();
        this.isCurrentlyIdle = false;
        this.sessionStartTime = System.currentTimeMillis();

        // Update Discord RPC
        updateDiscordRPC();

        // Trigger heartbeat to update backend if authenticated
        if (sessionManager.isAuthenticated()) {
            sessionManager.getHeartbeatManager().forceHeartbeat();
        }
    }

    /**
     * Called when the player leaves a server
     */
    public void onServerLeave() {
        LOGGER.info("[PresenceManager] Leaving session. Setting status to NONE");

        // Reset state
        this.sessionType = "NONE";
        this.currentServerIp = null;
        this.currentServerPort = 0;
        this.currentServerName = null;
        this.currentRealmId = null;
        this.currentRealmName = null;
        this.currentWorldName = null;
        this.isCurrentlyIdle = false;
        me.orbitium.craftcorpsCosmetics.client.util.IdleTracker.recordActivity();
        this.sessionStartTime = System.currentTimeMillis();

        // Update Discord RPC
        updateDiscordRPC();

        // Trigger heartbeat to update backend if authenticated
        if (sessionManager.isAuthenticated()) {
            sessionManager.getHeartbeatManager().forceHeartbeat();
        }
    }

    private void updateDiscordRPC() {
        this.lastRpcUpdateTime = System.currentTimeMillis();

        me.orbitium.craftcorpsCosmetics.client.util.ConfigManager.Config config = me.orbitium.craftcorpsCosmetics.client.util.ConfigManager
                .getInstance();

        String version = net.minecraft.client.MinecraftClient.getInstance().getGameVersion();

        if (!isOnServer()) {
            // Main Menu
            String stateKey = isCurrentlyIdle ? "rpc.craftcorps.state.idle" : "rpc.craftcorps.state.menu";
            me.orbitium.craftcorpsCosmetics.client.util.DiscordRPCManager.setActivity(
                    net.minecraft.client.resource.language.I18n.translate("rpc.craftcorps.main_menu"),
                    "idle",
                    net.minecraft.client.resource.language.I18n.translate(stateKey),
                    version,
                    sessionStartTime,
                    "overworld_icon");
            return;
        }

        boolean shouldShow = true;
        if ("SINGLEPLAYER".equals(sessionType)) {
            shouldShow = config.showSingleplayerRPC;
        } else if ("MULTIPLAYER".equals(sessionType) || "REALMS".equals(sessionType)) {
            shouldShow = config.showMultiplayerRPC;
        }

        if (shouldShow) {
            String name = net.minecraft.client.resource.language.I18n.translate("rpc.craftcorps.exploring");
            String address = "local";
            String stateKey = isCurrentlyIdle ? "rpc.craftcorps.state.idle" : "rpc.craftcorps.state.playing";

            if ("MULTIPLAYER".equals(sessionType)) {
                name = (currentServerName != null && !currentServerName.isEmpty()) ? currentServerName
                        : currentServerIp;
                address = currentServerIp + (currentServerPort != 25565 ? ":" + currentServerPort : "");
            } else if ("REALMS".equals(sessionType)) {
                name = currentRealmName != null ? currentRealmName
                        : net.minecraft.client.resource.language.I18n.translate("rpc.craftcorps.realms");
                address = "realms";
            } else if ("SINGLEPLAYER".equals(sessionType)) {
                name = currentWorldName != null ? currentWorldName
                        : net.minecraft.client.resource.language.I18n.translate("rpc.craftcorps.singleplayer");
                address = "singleplayer";
            }

            me.orbitium.craftcorpsCosmetics.client.util.DiscordRPCManager.setActivity(
                    name,
                    address,
                    net.minecraft.client.resource.language.I18n.translate(stateKey),
                    version,
                    sessionStartTime,
                    "overworld_icon");
        } else {
            me.orbitium.craftcorpsCosmetics.client.util.DiscordRPCManager.clearActivity();
        }
    }

    public String getSessionType() {
        return sessionType;
    }

    public String getCurrentServerIp() {
        return currentServerIp;
    }

    public int getCurrentServerPort() {
        return currentServerPort;
    }

    public String getCurrentServerName() {
        return currentServerName;
    }

    public String getCurrentRealmId() {
        return currentRealmId;
    }

    public String getCurrentRealmName() {
        return currentRealmName;
    }

    public String getCurrentWorldName() {
        return currentWorldName;
    }

    public boolean isOnServer() {
        return !"NONE".equals(sessionType);
    }
}
