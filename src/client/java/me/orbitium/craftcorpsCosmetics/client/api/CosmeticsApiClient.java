package me.orbitium.craftcorpsCosmetics.client.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class CosmeticsApiClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(CosmeticsApiClient.class);
    private static final String API_BASE_URL = "https://api.craftcorps.net/cosmetics";
    private static final Gson GSON = new Gson();

    private final HttpClient httpClient;

    public CosmeticsApiClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Fetches the list of cape cosmetic IDs for a given player UUID
     * 
     * @param playerUuid  The player's UUID
     * @param accessToken Optional access token for authenticated requests
     * @return A CompletableFuture containing the list of cape IDs
     */
    public CompletableFuture<List<String>> fetchPlayerCapes(UUID playerUuid, String accessToken) {
        String url = API_BASE_URL + "/capes?uuid=" + playerUuid.toString();

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET();

        // Add authentication if access token is provided
        if (accessToken != null && !accessToken.isEmpty()) {
            requestBuilder.header("Authorization", "Bearer " + accessToken);
        }

        HttpRequest request = requestBuilder.build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            return parseCapeResponse(response.body());
                        } catch (Exception e) {
                            LOGGER.error("Failed to parse cape response for player {}", playerUuid, e);
                            return new ArrayList<String>();
                        }
                    } else {
                        LOGGER.warn("Failed to fetch capes for player {}. Status code: {}",
                                playerUuid, response.statusCode());
                        return new ArrayList<String>();
                    }
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Error fetching capes for player {}", playerUuid, throwable);
                    return new ArrayList<String>();
                });
    }

    /**
     * Fetches the list of cape cosmetic IDs for a given player UUID
     * (unauthenticated)
     * 
     * @param playerUuid The player's UUID
     * @return A CompletableFuture containing the list of cape IDs
     */
    public CompletableFuture<List<String>> fetchPlayerCapes(UUID playerUuid) {
        return fetchPlayerCapes(playerUuid, null);
    }

    /**
     * Parses the API response to extract cape IDs
     * Expected format: {"capes": ["cape_id_1", "cape_id_2", ...]} or ["cape_id_1",
     * "cape_id_2"]
     */
    private List<String> parseCapeResponse(String responseBody) {
        List<String> capeIds = new ArrayList<>();

        try {
            JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();

            // Check if response has a "capes" array
            if (jsonObject.has("capes") && jsonObject.get("capes").isJsonArray()) {
                jsonObject.getAsJsonArray("capes").forEach(element -> {
                    capeIds.add(element.getAsString());
                });
            }
        } catch (Exception e) {
            // Try parsing as a direct array
            try {
                JsonParser.parseString(responseBody).getAsJsonArray().forEach(element -> {
                    capeIds.add(element.getAsString());
                });
            } catch (Exception ex) {
                LOGGER.error("Failed to parse response body: {}", responseBody, ex);
            }
        }

        return capeIds;
    }

    /**
     * Fetches the active cosmetics profile for a given player UUID.
     * Endpoint: GET /cosmetics/profile?uuid=...
     * 
     * @param playerUuid The player's UUID
     * @return A CompletableFuture containing the cosmetics profile data, or null if
     *         failed
     */
    public CompletableFuture<CosmeticsProfileResponse> fetchCosmeticsProfile(UUID playerUuid) {
        String url = API_BASE_URL + "/profile?uuid=" + playerUuid.toString();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            return GSON.fromJson(response.body(), CosmeticsProfileResponse.class);
                        } catch (Exception e) {
                            LOGGER.error("Failed to parse cosmetics profile for player {}", playerUuid, e);
                            return null;
                        }
                    } else {
                        LOGGER.warn("Failed to fetch cosmetics profile for player {}. Status code: {}",
                                playerUuid, response.statusCode());
                        return null;
                    }
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Error fetching cosmetics profile for player {}", playerUuid, throwable);
                    return null;
                });
    }

    public static class CosmeticsProfileResponse {
        public String activeCapeId;
        public Map<String, String> activeCosmetics;
    }

    public void shutdown() {
        // HttpClient doesn't need explicit shutdown in modern Java
    }
}
