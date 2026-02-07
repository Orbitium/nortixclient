package me.orbitium.craftcorpsCosmetics.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;

/**
 * Utility class for player-related checks
 */
public class PlayerUtils {

    /**
     * Checks if a player entity is likely a real player or an NPC.
     * Heuristic: Real players exist in the client's tab list.
     * 
     * @param player The player entity to check
     * @return true if it's likely a real player, false if it's an NPC or bot
     */
    public static boolean isRealPlayer(AbstractClientPlayerEntity player) {
        if (player == null)
            return false;

        // 1. Check if the player is the local player (always real)
        if (player == MinecraftClient.getInstance().player)
            return true;

        // 2. Tab List Check
        // Most NPC plugins (Citizens, ZNPCs) don't create tab list entries
        var networkHandler = MinecraftClient.getInstance().getNetworkHandler();
        if (networkHandler == null)
            return false;

        PlayerListEntry entry = networkHandler.getPlayerListEntry(player.getUuid());

        // If they aren't in the tab list, they are almost certainly an NPC
        if (entry == null)
            return false;

        // 3. Latency Heuristic (Optional)
        // Many NPCs have a constant 0 or -1 ping
        // if (entry.getLatency() <= 0) return false;

        return true;
    }
}
