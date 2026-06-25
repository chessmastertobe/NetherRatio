package org.doraji.netherratio.events;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
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
        plugin.getLogger().info("[NetherRatio] Polished Version - Centered Spawn + Sounds");
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
            Location target = findNearestPortal(safeDest, 8);

            Location spawnLoc;
            if (target != null) {
                spawnLoc = target.clone().add(0.5, 1.25, 0.5); // Better centered inside portal
            } else {
                if (isSafeSpot(safeDest.getWorld(), safeDest.getBlockX(), safeDest.getBlockY(), safeDest.getBlockZ())) {
                    createBasicPortal(safeDest.getWorld(), safeDest.getBlockX(), safeDest.getBlockY(), safeDest.getBlockZ());
                    spawnLoc = safeDest.clone().add(0.5, 1.25, 0.5);
                } else {
                    spawnLoc = originalPortal;
                }
            }

            doTeleportWithEffects(player, spawnLoc, originalPortal);
        });
    }

    private void doTeleportWithEffects(Player player, Location target, Location fallback) {
        player.teleportAsync(target).thenAccept(success -> {
            if (success) {
                // Play sounds
                player.playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                player.playSound(target, Sound.BLOCK_PORTAL_TRAVEL, 0.6f, 1.0f);

                player.setNoDamageTicks(160);
                player.setFallDistance(0);
            } else {
                player.teleportAsync(fallback);
            }
        });
    }

    // ==================== Helper Methods (same as before) ====================
    private Location calculateCustomDestination(Location from) { /* same */ }
    private void findSafeLocationAsync(Location target, Consumer<Location> callback) { /* same */ }
    private Location findSafeLocationSync(Location target) { /* same */ }
    private boolean isSafeSpot(World world, int x, int y, int z) { /* same */ }
    private Location findNearestPortal(Location center, int radius) { /* same */ }
    private void createBasicPortal(World world, int x, int y, int z) { /* same */ }
    private String formatLoc(Location loc) { /* same */ }
}
