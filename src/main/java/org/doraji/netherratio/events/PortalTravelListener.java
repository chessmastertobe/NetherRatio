package org.doraji.netherratio.events;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.doraji.netherratio.NetherRatio;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PortalTravelListener implements Listener {

    private final NetherRatio plugin;
    private final Map<UUID, Long> justTeleportedByPlugin = new ConcurrentHashMap<>();

    public PortalTravelListener(NetherRatio plugin) {
        this.plugin = plugin;
        plugin.getLogger().info("[NetherRatio] KICK-OUT FIRST - Clean Teleport Test");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPortalCreate(PortalCreateEvent event) {
        if (event.getReason() == PortalCreateEvent.CreateReason.NETHER_PAIR) {
            event.setCancelled(true);
            plugin.getLogger().info("[RACE] Cancelled vanilla portal creation");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        Location to = event.getTo();
        if (to == null || to.getBlock().getType() != Material.NETHER_PORTAL) return;

        justTeleportedByPlugin.put(uuid, System.currentTimeMillis());

        plugin.getLogger().info("[RACE] DETECTED - Kicking player out of portal first");

        // Step 1: Immediately kick player slightly upward to exit the portal block
        Location kickOut = player.getLocation().clone().add(0, 1.0, 0);

        player.teleportAsync(kickOut).thenAccept(kickSuccess -> {
            if (!kickSuccess) {
                plugin.getLogger().warning("[RACE] Failed to kick player out of portal");
                return;
            }

            plugin.getLogger().info("[RACE] Player kicked out of portal. Now teleporting to target...");

            // Step 2: Teleport to the final hardcoded destination
            World targetWorld = Bukkit.getWorld("world");
            Location target = new Location(targetWorld, 10010.483, 84, -288.528);

            if (targetWorld == null) {
                plugin.getLogger().severe("[RACE] Target world not found!");
                return;
            }

            player.teleportAsync(target).thenAccept(success -> {
                if (success) {
                    plugin.getLogger().info("[RACE] SUCCESS - Clean teleport completed");
                    player.playSound(target, Sound.BLOCK_PORTAL_TRAVEL, 0.8f, 1.0f);
                } else {
                    plugin.getLogger().warning("[RACE] Final teleport failed");
                }
            });
        });
    }
}
