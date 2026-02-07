package me.orbitium.nortix.client;

import me.orbitium.nortix.client.api.CosmeticsApiClient;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class CapeManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(CapeManager.class);
    private static final String COSMETICS_BASE_URL = "https://api.craftcorps.net/cosmetics/";

    // Maps cape ID -> Resource Identifier (texture path)
    private static final Map<String, Identifier> capeTextures = new ConcurrentHashMap<>();

    // Track pending downloads to avoid duplicate requests
    private static final Set<String> downloadingCapes = Collections.synchronizedSet(new HashSet<>());

    // Maps Player UUID -> Active Cape ID
    private static final Map<UUID, String> playerActiveCapes = new ConcurrentHashMap<>();

    // Maps Player UUID -> Last Fetch Time
    private static final Map<UUID, Long> playerLastFetchTime = new ConcurrentHashMap<>();

    // Set of UUIDs currently being fetched to avoid duplicate requests
    private static final Set<UUID> fetchingUuids = Collections.synchronizedSet(new HashSet<>());

    private static long cacheTtlMs = 5 * 60 * 1000; // 5 minutes default

    // Debug mode: Set -Dcraftcorps.cosmetics.debug=true to enable
    private static final boolean DEBUG_MODE = Boolean.getBoolean("craftcorps.cosmetics.debug");

    private static CosmeticsApiClient apiClient;

    /**
     * Initialize the CapeManager with the API client
     */
    public static void initialize() {
        if (DEBUG_MODE) {
            LOGGER.warn("===================================");
            LOGGER.warn("DEBUG MODE ENABLED - API DISABLED");
            LOGGER.warn("All capes unlocked with developer cape active");
            LOGGER.warn("===================================");
            preloadDebugTextures();
        } else {
            apiClient = new CosmeticsApiClient();
            LOGGER.info("CapeManager initialized");
        }
    }

    /**
     * Shutdown the CapeManager and cleanup resources
     */
    public static void shutdown() {
        if (apiClient != null) {
            apiClient.shutdown();
        }
        capeTextures.clear();
        playerActiveCapes.clear();
        playerLastFetchTime.clear();
        fetchingUuids.clear();
        downloadingCapes.clear();
    }

    /**
     * Get the cape for a player.
     * 
     * @param uuid The player's UUID
     * @return The cape Identifier, or null if no cape or still loading
     */
    public static Identifier getCape(UUID uuid) {
        if (uuid == null)
            return null;

        // In debug mode, return developer cape
        if (DEBUG_MODE) {
            return capeTextures.get("developer");
        }

        String capeId = playerActiveCapes.get(uuid);
        if (capeId != null) {
            if (capeTextures.containsKey(capeId)) {
                return capeTextures.get(capeId);
            } else {
                LOGGER.debug("[CapeManager] Cape {} for player {} not loaded yet, triggering download", capeId, uuid);
                // If not loaded and not downloading, start download (for non-debug/non-local
                // capes)
                downloadCape(capeId);
            }
        }

        return null;
    }

    /**
     * Ensure the cape for a player is loaded.
     * Triggers a fetch if the data is missing or expired.
     * 
     * @param uuid The player's UUID
     */
    public static void ensureCape(UUID uuid) {
        if (uuid == null)
            return;

        if (DEBUG_MODE) {
            return;
        }

        if (shouldFetch(uuid)) {
            LOGGER.info("[CapeManager] Data for Minecraft UUID {} is missing or expired, fetching from API...", uuid);
            fetchPlayerCapes(uuid);
        }
    }

    /**
     * Clear state for a specific player (e.g. on unload or disconnect)
     */
    public static void removePlayer(UUID uuid) {
        if (uuid != null) {
            playerActiveCapes.remove(uuid);
            playerLastFetchTime.remove(uuid);
        }
    }

    /**
     * Force a refresh of the local player's cosmetics
     */
    public static void refreshLocalPlayer() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }

        refreshPlayer(client.player.getUuid());
    }

    /**
     * Force a refresh of a specific player's cosmetics
     */
    public static void refreshPlayer(UUID uuid) {
        if (uuid == null) {
            return;
        }

        LOGGER.info("[CapeManager] Invalidating cosmetics cache for player: {}", uuid);

        // Clear local cache to allow new fetch
        playerLastFetchTime.remove(uuid);
        playerActiveCapes.remove(uuid);

        // Trigger safe fetch
        ensureCape(uuid);
    }

    /**
     * Update the cosmetic cache TTL
     * 
     * @param seconds New TTL in seconds
     */
    public static void setCacheTtl(long seconds) {
        cacheTtlMs = seconds * 1000L;
        LOGGER.info("[CapeManager] Cosmetic cache TTL updated to {} seconds", seconds);
    }

    /**
     * Clear all state (e.g. on server disconnect)
     */
    public static void clearAll() {
        playerActiveCapes.clear();
        playerLastFetchTime.clear();
        fetchingUuids.clear();
        downloadingCapes.clear();
    }

    /**
     * Helper to create a local identifier (used for known/debug capes)
     */
    private static Identifier createCapeIdentifier(String capeId) {
        return Identifier.of("craftcorps-cosmetics", "textures/capes/" + capeId + ".png");
    }

    /**
     * Download and register a cape texture
     */
    private static void downloadCape(String capeId) {
        if (downloadingCapes.contains(capeId) || capeTextures.containsKey(capeId)) {
            return;
        }

        downloadingCapes.add(capeId);
        LOGGER.info("Starting download for cape: {}", capeId);

        CompletableFuture.runAsync(() -> {
            try {
                URL url = new URL(COSMETICS_BASE_URL + capeId + "/texture");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000); // 5 sec is plenty for a small PNG
                connection.connect();

                if (connection.getResponseCode() / 100 == 2) {
                    try (InputStream inputStream = connection.getInputStream()) {
                        NativeImage image = NativeImage.read(inputStream);

                        // Register texture on the main client thread
                        MinecraftClient.getInstance().execute(() -> {
                            try {
                                NativeImageBackedTexture texture = new NativeImageBackedTexture(
                                        () -> "downloaded_cape_" + capeId, image);
                                Identifier identifier = Identifier.of("craftcorps-cosmetics",
                                        "downloaded_cape_" + capeId);
                                MinecraftClient.getInstance().getTextureManager().registerTexture(identifier, texture);
                                capeTextures.put(capeId, identifier);
                                LOGGER.info("Successfully downloaded and registered cape: {}", capeId);
                            } catch (Exception e) {
                                LOGGER.error("Failed to register texture for cape: {}", capeId, e);
                            } finally {
                                downloadingCapes.remove(capeId);
                            }
                        });
                    }
                } else {
                    LOGGER.warn("Failed to download cape {}: HTTP {}", capeId, connection.getResponseCode());
                    downloadingCapes.remove(capeId);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to download cape: {}", capeId, e);
                downloadingCapes.remove(capeId);
            }
        });
    }

    /**
     * Check if we should fetch cape data for this UUID
     */
    private static boolean shouldFetch(UUID uuid) {
        if (fetchingUuids.contains(uuid)) {
            return false;
        }

        // Second layer NPC filtering: Check if the UUID exists in the tab list
        var networkHandler = MinecraftClient.getInstance().getNetworkHandler();
        if (networkHandler != null && networkHandler.getPlayerListEntry(uuid) == null) {
            // Local player is always "real" but might not be in tab list immediately during
            // join
            if (MinecraftClient.getInstance().player == null
                    || !MinecraftClient.getInstance().player.getUuid().equals(uuid)) {
                return false;
            }
        }

        if (!playerLastFetchTime.containsKey(uuid)) {
            return true;
        }

        long lastTime = playerLastFetchTime.get(uuid);
        return (System.currentTimeMillis() - lastTime) > cacheTtlMs;
    }

    /**
     * Fetch cape data from API for a player
     */
    private static void fetchPlayerCapes(UUID uuid) {
        if (apiClient == null) {
            return;
        }

        LOGGER.info("[CapeManager] Requesting cosmetics profile for Minecraft UUID: {}", uuid);
        fetchingUuids.add(uuid);

        // Use the new public profile endpoint to get the active cape
        apiClient.fetchCosmeticsProfile(uuid).thenAccept(profile -> {
            processCosmeticsResponse(uuid, profile);
        }).exceptionally(throwable -> {
            LOGGER.error("[CapeManager] Failed to fetch cosmetics for player {}", uuid, throwable);
            fetchingUuids.remove(uuid);
            return null;
        });
    }

    /**
     * Process the cosmetics response from the API
     */
    private static void processCosmeticsResponse(UUID uuid, CosmeticsApiClient.CosmeticsProfileResponse profile) {
        try {
            if (profile != null) {
                int count = 0;
                if (profile.activeCapeId != null)
                    count++;
                if (profile.activeCosmetics != null)
                    count += profile.activeCosmetics.size();

                LOGGER.info("[CapeManager] Found {} cosmetics for Minecraft UUID: {}", count, uuid);

                if (profile.activeCapeId != null) {
                    String activeId = profile.activeCapeId;
                    LOGGER.info("[CapeManager] Received active cape '{}' for Minecraft UUID: {}", activeId, uuid);
                    playerActiveCapes.put(uuid, activeId);

                    // Check if we need to download it
                    if (!capeTextures.containsKey(activeId)) {
                        downloadCape(activeId);
                    }
                } else {
                    LOGGER.info("[CapeManager] Minecraft UUID {} has no active cape equipped", uuid);
                    playerActiveCapes.remove(uuid);
                }

                if (profile.activeCosmetics != null && !profile.activeCosmetics.isEmpty()) {
                    LOGGER.info("[CapeManager] Other active cosmetics for Minecraft UUID {}: {}", uuid,
                            profile.activeCosmetics);
                    // Pass profiling data to NametagManager to extract icons
                    NametagManager.handleProfileResponse(uuid, profile);
                }
            } else {
                LOGGER.info("[CapeManager] Found 0 cosmetics for Minecraft UUID: {}", uuid);
                LOGGER.warn("[CapeManager] Received null profile response for Minecraft UUID: {}", uuid);
                playerActiveCapes.remove(uuid);
                NametagManager.handleProfileResponse(uuid, null);
            }

            playerLastFetchTime.put(uuid, System.currentTimeMillis());
        } finally {
            fetchingUuids.remove(uuid);
        }
    }

    /**
     * Preload debug textures
     */
    private static void preloadDebugTextures() {
        String[] debugCapeIds = { "default", "vip", "staff", "developer" };
        for (String capeId : debugCapeIds) {
            capeTextures.put(capeId, createCapeIdentifier(capeId));
        }
    }
}