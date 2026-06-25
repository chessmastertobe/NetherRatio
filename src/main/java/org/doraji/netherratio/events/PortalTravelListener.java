package org.doraji.netherratio.events;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.doraji.netherratio.NetherRatio;
import org.doraji.netherratio.ConfigManager;
import org.doraji.netherratio.util.CoordinateMath;

import java.util.function.Consumer;

public class PortalTravelListener implements Listener {

    private final NetherRatio plugin;
    private final ConfigManager cm;

    public PortalTravelListener(NetherRatio plugin) {
        this.plugin = plugin;
        this.cm = plugin.getConfigManager();
        plugin.getLogger().info("[NetherRatio] Debug v8 - Maximum Coordinate Logging Loaded");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        if (to == null || to.getBlock().getType() != Material.NETHER_PORTAL) return;

        Location originalPortal = event.getFrom().clone();

        Location customDest = calculateCustomDestination(to);
        if (customDest == null) {
            player.teleportAsync(originalPortal);
            return;
        }

        findSafeLocationAsync(customDest, safeDest -> {
            plugin.getLogger().info("[Portal] Calculated safe dest: X=" + safeDest.getX() + " Y=" + safeDest.getY() + " Z=" + safeDest.getZ());

            Location existing = findNearestPortal(safeDest, 8);

            if (existing != null) {
                plugin.getLogger().info("[Portal] Found existing portal at X=" + existing.getX() + 
                    " Y=" + existing.getY() + " Z=" + existing.getZ());

                // Spawn player in the middle/upper part of the portal
                Location spawnLoc = existing.clone().add(0, 1.1, 0);
                plugin.getLogger().info("[Teleport] Sending player to spawnLoc X=" + spawnLoc.getX() + 
                    " Y=" + spawnLoc.getY() + " Z=" + spawnLoc.getZ());

                teleportWithRetry(player, spawnLoc, originalPortal, 4);
            } else {
                if (isSafeSpot(safeDest.getWorld(), safeDest.getBlockX(), safeDest.getBlockY(), safeDest.getBlockZ())) {
                    createBasicPortal(safeDest.getWorld(), safeDest.getBlockX(), safeDest.getBlockY(), safeDest.getBlockZ());
                    plugin.getLogger().info("[Portal] Created new portal at X=" + safeDest.getX() + 
                        " Y=" + safeDest.getY() + " Z=" + safeDest.getZ());

                    Location spawnLoc = safeDest.clone().add(0, 1.1, 0);
                    plugin.getLogger().info("[Teleport] Sending player to new portal spawnLoc X=" + spawnLoc.getX() + 
                        " Y=" + spawnLoc.getY() + " Z=" + spawnLoc.getZ());

                    teleportWithRetry(player, spawnLoc, originalPortal, 4);
                } else {
                    player.teleportAsync(originalPortal);
                    player.sendMessage("§cCould not find safe portal location.");
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPortalCreate(PortalCreateEvent event) {
        if (event.getReason() == PortalCreateEvent.CreateReason.NETHER_PAIR) {
            event.setCancelled(true);
        }
    }

    private void teleportWithRetry(Player player, Location target, Location fallback, int attemptsLeft) {
        player.teleportAsync(target).thenAccept(success -> {
            plugin.getLogger().info("[Teleport] Success=" + success + 
                " | Player now at X=" + player.getLocation().getX() + 
                " Y=" + player.getLocation().getY() + 
                " Z=" + player.getLocation().getZ());

            if (success) {
                player.setNoDamageTicks(100);
            } else if (attemptsLeft > 0) {
                Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> 
                    teleportWithRetry(player, target, fallback, attemptsLeft - 1), 3L);
            } else {
                player.teleportAsync(fallback);
            }
        });
    }

    // Keep all helper methods from previous version (calculateCustomDestination, findSafeLocationAsync, etc.)
    private Location calculateCustomDestination(Location from) { /* same */ }
    private void findSafeLocationAsync(Location target, Consumer<Location> callback) { /* same */ }
    private Location findSafeLocationSync(Location target) { /* same */ }
    private boolean isSafeSpot(World world, int x, int y, int z) { /* same */ }
    private Location findNearestPortal(Location center, int radius) { /* same */ }
    private void createBasicPortal(World world, int x, int y, int z) { /* same */ }
    private String formatLoc(Location loc) { /* same */ }
}
