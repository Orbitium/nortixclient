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

public class NametagManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(NametagManager.class);
    private static final String COSMETICS_BASE_URL = "https://api.nortixlabs.com/cosmetics/";

    // Maps nametag ID -> Resource Identifier (texture path)
    private static final Map<String, Identifier> nametagTextures = new ConcurrentHashMap<>();

    // Track pending downloads to avoid duplicate requests
    private static final Set<String> downloadingNametags = Collections.synchronizedSet(new HashSet<>());

    // Maps Player UUID -> Active Nametag ID
    private static final Map<UUID, String> playerActiveNametags = new ConcurrentHashMap<>();

    // Maps Player UUID -> Last Fetch Time
    private static final Map<UUID, Long> playerLastFetchTime = new ConcurrentHashMap<>();

    // Set of UUIDs currently being fetched to avoid duplicate requests
    private static final Set<UUID> fetchingUuids = Collections.synchronizedSet(new HashSet<>());

    private static long cacheTtlMs = 5 * 60 * 1000; // 5 minutes default

    // Debug mode
    private static final boolean DEBUG_MODE = Boolean.getBoolean("nortix.cosmetics.debug");

    private static CosmeticsApiClient apiClient;

    public static void initialize() {
        if (DEBUG_MODE) {
            LOGGER.warn("NametagManager: DEBUG MODE ENABLED");
            // Add any debug nametags here if needed
        } else {
            apiClient = new CosmeticsApiClient();
            LOGGER.info("NametagManager initialized");
        }
    }

    public static void shutdown() {
        nametagTextures.clear();
        playerActiveNametags.clear();
        playerLastFetchTime.clear();
        fetchingUuids.clear();
        downloadingNametags.clear();
    }

    public static Identifier getNametagIcon(UUID uuid) {
        if (uuid == null)
            return null;

        // For testing purposes as requested
        String nametagId = playerActiveNametags.get(uuid);
        if (nametagId != null) {
            if (nametagTextures.containsKey(nametagId)) {
                return nametagTextures.get(nametagId);
            } else {
                downloadNametag(nametagId);
            }
        }

        // Fallback to test icon
        return Identifier.of("nortix-cosmetics", "textures/icons/Add.png");
    }

    public static void ensureNametag(UUID uuid) {
        if (uuid == null || DEBUG_MODE)
            return;

        if (shouldFetch(uuid)) {
            fetchPlayerNametags(uuid);
        }
    }

    public static void removePlayer(UUID uuid) {
        if (uuid != null) {
            playerActiveNametags.remove(uuid);
            playerLastFetchTime.remove(uuid);
        }
    }

    public static void clearAll() {
        playerActiveNametags.clear();
        playerLastFetchTime.clear();
        fetchingUuids.clear();
        downloadingNametags.clear();
    }

    private static void downloadNametag(String nametagId) {
        if (downloadingNametags.contains(nametagId) || nametagTextures.containsKey(nametagId)) {
            return;
        }

        downloadingNametags.add(nametagId);
        LOGGER.info("Starting download for nametag: {}", nametagId);

        CompletableFuture.runAsync(() -> {
            try {
                URL url = new URL(COSMETICS_BASE_URL + nametagId + "/texture");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.connect();

                if (connection.getResponseCode() / 100 == 2) {
                    try (InputStream inputStream = connection.getInputStream()) {
                        NativeImage image = NativeImage.read(inputStream);

                        MinecraftClient.getInstance().execute(() -> {
                            try {
                                NativeImageBackedTexture texture = new NativeImageBackedTexture(
                                        () -> "downloaded_nametag_" + nametagId, image);
                                Identifier identifier = Identifier.of("nortix-cosmetics",
                                        "downloaded_nametag_" + nametagId);
                                MinecraftClient.getInstance().getTextureManager().registerTexture(identifier, texture);
                                nametagTextures.put(nametagId, identifier);
                                LOGGER.info("Successfully downloaded and registered nametag: {}", nametagId);
                            } catch (Exception e) {
                                LOGGER.error("Failed to register texture for nametag: {}", nametagId, e);
                            } finally {
                                downloadingNametags.remove(nametagId);
                            }
                        });
                    }
                } else {
                    LOGGER.warn("Failed to download nametag {}: HTTP {}", nametagId, connection.getResponseCode());
                    downloadingNametags.remove(nametagId);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to download nametag: {}", nametagId, e);
                downloadingNametags.remove(nametagId);
            }
        });
    }

    private static boolean shouldFetch(UUID uuid) {
        if (fetchingUuids.contains(uuid))
            return false;

        var networkHandler = MinecraftClient.getInstance().getNetworkHandler();
        if (networkHandler != null && networkHandler.getPlayerListEntry(uuid) == null) {
            if (MinecraftClient.getInstance().player == null
                    || !MinecraftClient.getInstance().player.getUuid().equals(uuid)) {
                return false;
            }
        }

        if (!playerLastFetchTime.containsKey(uuid))
            return true;

        long lastTime = playerLastFetchTime.get(uuid);
        return (System.currentTimeMillis() - lastTime) > cacheTtlMs;
    }

    public static void fetchPlayerNametags(UUID uuid) {
        if (apiClient == null)
            return;

        fetchingUuids.add(uuid);

        apiClient.fetchCosmeticsProfile(uuid).thenAccept(profile -> {
            handleProfileResponse(uuid, profile);
        }).exceptionally(throwable -> {
            LOGGER.error("Failed to fetch nametag for player {}", uuid, throwable);
            fetchingUuids.remove(uuid);
            return null;
        });
    }

    public static void handleProfileResponse(UUID uuid, CosmeticsApiClient.CosmeticsProfileResponse profile) {
        try {
            if (profile != null && profile.activeCosmetics != null) {
                String nametagId = profile.activeCosmetics.get("nametag");
                if (nametagId != null) {
                    playerActiveNametags.put(uuid, nametagId);
                    if (!nametagTextures.containsKey(nametagId)) {
                        downloadNametag(nametagId);
                    }
                } else {
                    playerActiveNametags.remove(uuid);
                }
            } else {
                playerActiveNametags.remove(uuid);
            }
            playerLastFetchTime.put(uuid, System.currentTimeMillis());
        } finally {
            fetchingUuids.remove(uuid);
        }
    }
}
