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
        plugin.getLogger().info("[NetherRatio] Debug v15 - Delayed Teleport + Max Safety");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        if (to == null || to.getBlock().getType() != Material.NETHER_PORTAL) return;

        Location originalPortal = event.getFrom().clone();

        plugin.getLogger().info("[Portal] === PLAYER ENTERED PORTAL ===");
        plugin.getLogger().info("[Portal] Player: " + player.getName());
        plugin.getLogger().info("[Portal] Entry: " + formatLoc(to));

        // Delay the teleport slightly to let Folia finish its internal portal logic
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
            processPortalTeleport(player, originalPortal);
        }, 5L); // 0.25 second delay
    }

    private void processPortalTeleport(Player player, Location originalPortal) {
        Location customDest = calculateCustomDestination(player.getLocation());
        if (customDest == null) {
            player.teleportAsync(originalPortal);
            return;
        }

        findSafeLocationAsync(customDest, safeDest -> {
            plugin.getLogger().info("[Portal] Calculated safe dest: " + formatLoc(safeDest));

            Location target = findNearestPortal(safeDest, 8);

            Location spawnLoc;
            if (target != null) {
                plugin.getLogger().info("[Portal] Found existing portal at: " + formatLoc(target));
                spawnLoc = target.clone().add(0.5, 2.2, 0.5);   // Middle of portal
            } else {
                if (isSafeSpot(safeDest.getWorld(), safeDest.getBlockX(), safeDest.getBlockY(), safeDest.getBlockZ())) {
                    createBasicPortal(safeDest.getWorld(), safeDest.getBlockX(), safeDest.getBlockY(), safeDest.getBlockZ());
                    spawnLoc = safeDest.clone().add(0.5, 2.2, 0.5);
                    plugin.getLogger().info("[Portal] Created new portal at: " + formatLoc(spawnLoc));
                } else {
                    spawnLoc = originalPortal;
                    plugin.getLogger().warning("[Portal] No safe spot - falling back");
                }
            }

            plugin.getLogger().info("[Teleport] FINAL SPAWN: " + formatLoc(spawnLoc));
            doFinalTeleport(player, spawnLoc, originalPortal);
        });
    }

    private void doFinalTeleport(Player player, Location target, Location fallback) {
        plugin.getLogger().info("[Teleport] Sending player to " + formatLoc(target));

        player.teleportAsync(target).thenAccept(success -> {
            Location current = player.getLocation();
            plugin.getLogger().info("[Teleport] Success=" + success + " | Now at " + formatLoc(current) + " Y=" + current.getY());

            if (success) {
                player.setNoDamageTicks(200);
                player.setFallDistance(0);
            } else {
                player.teleportAsync(fallback);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPortalCreate(PortalCreateEvent event) {
        if (event.getReason() == PortalCreateEvent.CreateReason.NETHER_PAIR) {
            event.setCancelled(true);
        }
    }

    // ==================== Helper Methods (unchanged) ====================
    private Location calculateCustomDestination(Location from) { /* same as before */ }
    private void findSafeLocationAsync(Location target, Consumer<Location> callback) { /* same */ }
    private Location findSafeLocationSync(Location target) { /* same */ }
    private boolean isSafeSpot(World world, int x, int y, int z) { /* same */ }
    private Location findNearestPortal(Location center, int radius) { /* same */ }
    private void createBasicPortal(World world, int x, int y, int z) { /* same */ }
    private String formatLoc(Location loc) { /* same */ }
}
