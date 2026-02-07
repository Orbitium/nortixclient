package me.orbitium.craftcorpsCosmetics.client.session;

/**
 * Represents the mod's session data
 */
public class ModSession {
    private String accessToken;
    private String refreshToken;
    private String userId;
    private String username;
    private long tokenIssuedAt;

    public ModSession(String accessToken, String refreshToken, String userId, String username) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.userId = userId;
        this.username = username;
        this.tokenIssuedAt = System.currentTimeMillis();
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
        this.tokenIssuedAt = System.currentTimeMillis();
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public long getTokenIssuedAt() {
        return tokenIssuedAt;
    }

    /**
     * Check if the access token is likely expired (15 minutes since issue)
     */
    public boolean isAccessTokenExpired() {
        long fifteenMinutes = 15 * 60 * 1000;
        return (System.currentTimeMillis() - tokenIssuedAt) > fifteenMinutes;
    }
}
