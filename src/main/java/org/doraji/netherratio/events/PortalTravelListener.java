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
        plugin.getLogger().info("[NetherRatio] MINIMAL RACE WINNER v2");
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
        if (to == null) return;
        if (to.getBlock().getType() != Material.NETHER_PORTAL) return;

        // Mark as handled immediately
        justTeleportedByPlugin.put(uuid, System.currentTimeMillis());

        plugin.getLogger().info("[RACE] DETECTED - Player entered portal: " + player.getName());

        // === HARDCODED TEST DESTINATION ===
        World targetWorld = Bukkit.getWorld("world");
        Location hardcodedDest = new Location(targetWorld, 10010.483, 84, -288.528);

        if (targetWorld == null) {
            plugin.getLogger().severe("[RACE] Target world not found!");
            return;
        }

        player.teleportAsync(hardcodedDest).thenAccept(success -> {
            if (success) {
                plugin.getLogger().info("[RACE] SUCCESS - We won the race");
                player.playSound(hardcodedDest, Sound.BLOCK_PORTAL_TRAVEL, 0.8f, 1.0f);
            } else {
                plugin.getLogger().warning("[RACE] Teleport failed");
            }
        });
    }
}
