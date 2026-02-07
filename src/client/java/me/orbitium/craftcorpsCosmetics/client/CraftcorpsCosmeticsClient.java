package me.orbitium.craftcorpsCosmetics.client;

import me.orbitium.craftcorpsCosmetics.client.session.SessionManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CraftcorpsCosmeticsClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(CraftcorpsCosmeticsClient.class);

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing CraftCorps Cosmetics Mod");

        // Initialize session management first
        SessionManager.getInstance().initialize().thenAccept(success -> {
            if (success) {
                LOGGER.info("Session initialized successfully");
            } else {
                LOGGER.info("Running in unauthenticated mode");
            }
        });

        // Initialize the Managers to enable API fetching
        CapeManager.initialize();
        NametagManager.initialize();

        // Hook into entity load events to fetch capes for players when they appear in
        // the world
        ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof AbstractClientPlayerEntity player) {
                // Only fetch capes for real players, ignore NPCs
                if (me.orbitium.craftcorpsCosmetics.client.util.PlayerUtils.isRealPlayer(player)) {
                    CapeManager.ensureCape(player.getUuid());
                    NametagManager.ensureNametag(player.getUuid());
                }
            }
        });

        // Optional: Clean up cape data when players leave the client's view/world
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof AbstractClientPlayerEntity player) {
                CapeManager.removePlayer(player.getUuid());
                NametagManager.removePlayer(player.getUuid());
            }
        });

        // Track server join events for presence
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            CapeManager.clearAll();
            NametagManager.clearAll();

            // Extract server information
            String serverIp = getServerIp(client);
            int serverPort = getServerPort(client);
            String serverName = getServerName(client);
            String activityType = getActivityType(client);

            // Extract extended context
            String worldName = null;
            if (client.isIntegratedServerRunning() && client.getServer() != null) {
                worldName = client.getServer().getSaveProperties().getLevelName();
            }

            String realmId = null;
            String realmName = null;
            if (client.getCurrentServerEntry() != null && client.getCurrentServerEntry().isRealm()) {
                realmName = client.getCurrentServerEntry().name;
                // Realm ID is not easily accessible from ServerInfo
            }

            LOGGER.info("Joined session: Type={}, IP={}:{}, World={}", activityType, serverIp, serverPort, worldName);

            // Notify backend via PresenceManager (triggering heartbeat)
            SessionManager.getInstance().getPresenceManager()
                    .onServerJoin(activityType, serverIp, serverPort, serverName, realmId, realmName, worldName);
        });

        // Track server leave events when disconnecting
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            LOGGER.info("Disconnecting - cleaning up cosmetics data");

            // Notify backend about server leave
            SessionManager.getInstance().getPresenceManager().onServerLeave();

            CapeManager.clearAll();
            NametagManager.clearAll();
            // Do not shutdown session on disconnect, keep it alive for main menu
        });

        // Periodic maintenance: re-check nearby players every 5 minutes to ensure sync
        // This handles cases where players stay in view longer than the cache TTL
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world != null && client.world.getTime() % (20 * 60 * 5) == 0) {
                LOGGER.debug("[CraftcorpsCosmeticsClient] Periodic cosmetics check for nearby players...");
                for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
                    CapeManager.ensureCape(player.getUuid());
                    NametagManager.ensureNametag(player.getUuid());
                }
            }

            // Update presence manager (idle tracking, etc.)
            SessionManager.getInstance().getPresenceManager().tick(client);
        });

        // Clear Discord activity when the game closes
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            LOGGER.info("Game closing - clearing Discord activity");
            me.orbitium.craftcorpsCosmetics.client.util.DiscordRPCManager.clearActivity();
        });

        // Register GeckoLib Renderers

    }

    /**
     * Determine the activity type (SINGLEPLAYER, MULTIPLAYER, or REALMS)
     */
    private String getActivityType(net.minecraft.client.MinecraftClient client) {
        net.minecraft.client.network.ServerInfo server = client.getCurrentServerEntry();
        if (server != null) {
            return server.isRealm() ? "REALMS" : "MULTIPLAYER";
        } else if (client.isIntegratedServerRunning()) {
            return "SINGLEPLAYER";
        }
        return "UNKNOWN";
    }

    /**
     * Extract server IP/Hostname from the client
     * Returns "singleplayer" or "realms" for non-multiplayer connections
     */
    private String getServerIp(net.minecraft.client.MinecraftClient client) {
        net.minecraft.client.network.ServerInfo server = client.getCurrentServerEntry();
        if (server != null) {
            if (server.isRealm()) {
                return "realms";
            }
            String address = server.address;
            if (address.contains(":")) {
                return address.substring(0, address.lastIndexOf(":"));
            }
            return address;
        } else if (client.isIntegratedServerRunning()) {
            return "singleplayer";
        }
        return "unknown";
    }

    /**
     * Extract server Port from the client
     * Returns 25565 by default or the parsed port
     */
    private int getServerPort(net.minecraft.client.MinecraftClient client) {
        net.minecraft.client.network.ServerInfo server = client.getCurrentServerEntry();
        if (server != null) {
            String address = server.address;
            if (address.contains(":")) {
                try {
                    return Integer.parseInt(address.substring(address.lastIndexOf(":") + 1));
                } catch (NumberFormatException e) {
                    return 25565;
                }
            }
        }
        return 25565;
    }

    /**
     * Extract server name from the client
     * Returns descriptive names for singleplayer and realms
     */
    private String getServerName(net.minecraft.client.MinecraftClient client) {
        net.minecraft.client.network.ServerInfo server = client.getCurrentServerEntry();
        if (server != null) {
            if (server.isRealm()) {
                return "Minecraft Realms";
            }
            String name = server.name;
            // If name is same as address, it might be auto-generated, so return null
            if (name != null && !name.equals(server.address)) {
                return name;
            }
        } else if (client.isIntegratedServerRunning()) {
            return "Singleplayer World";
        }
        return null;
    }
}
