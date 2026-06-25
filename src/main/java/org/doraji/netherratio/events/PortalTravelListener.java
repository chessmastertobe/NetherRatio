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

public class PortalTravelListener implements Listener {

    private final NetherRatio plugin;
    private final ConfigManager cm;

    public PortalTravelListener(NetherRatio plugin) {
        this.plugin = plugin;
        this.cm = plugin.getConfigManager();
        plugin.getLogger().info("[NetherRatio] Strict Folia Compliant Version");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        if (to == null || to.getBlock().getType() != Material.NETHER_PORTAL) return;

        Location original = event.getFrom().clone();

        Location dest = calculateCustomDestination(to);
        if (dest == null) {
            player.teleportAsync(original);
            return;
        }

        // Schedule work on the DESTINATION world's region thread (Folia safe)
        Bukkit.getRegionScheduler().execute(plugin, dest.getWorld(), 
            dest.getBlockX() >> 4, dest.getBlockZ() >> 4, () -> {

            Location existing = findNearestPortalSafe(dest, 16);

            if (existing != null) {
                Location spawn = existing.clone().add(0.5, 0.9, 0.5);
                doTeleport(player, spawn);
            } else {
                Location safe = findSafeY(dest);
                if (safe != null) {
                    createBasicPortal(safe.getWorld(), safe.getBlockX(), safe.getBlockY(), safe.getBlockZ());
                    Location spawn = safe.clone().add(0.5, 0.9, 0.5);
                    doTeleport(player, spawn);
                } else {
                    Location high = dest.clone().add(0, 25, 0);
                    createSafetyPlatform(high);
                    doTeleport(player, high);
                }
            }
        });
    }

    private void doTeleport(Player player, Location target) {
        player.teleportAsync(target).thenAccept(success -> {
            if (success) {
                player.playSound(target, Sound.BLOCK_PORTAL_TRAVEL, 0.7f, 1.0f);
                player.setNoDamageTicks(200);
                player.setFallDistance(0);
            }
        });
    }

    // ==================== Folia-Safe Helper Methods ====================

    private Location findNearestPortalSafe(Location center, int radius) {
        World world = center.getWorld();
        if (world == null) return null;

        for (int x = center.getBlockX() - radius; x <= center.getBlockX() + radius; x++) {
            for (int z = center.getBlockZ() - radius; z <= center.getBlockZ() + radius; z++) {
                for (int y = center.getBlockY() - 10; y <= center.getBlockY() + 20; y++) {
                    if (world.getBlockAt(x, y, z).getType() == Material.NETHER_PORTAL) {
                        return new Location(world, x, y, z);
                    }
                }
            }
        }
        return null;
    }

    private Location findSafeY(Location base) {
        World world = base.getWorld();
        if (world == null) return null;

        for (int y = base.getBlockY() + 25; y >= base.getBlockY() - 25; y--) {
            if (isSafeSpot(world, base.getBlockX(), y, base.getBlockZ())) {
                return new Location(world, base.getBlockX(), y, base.getBlockZ());
            }
        }
        return null;
    }

    private boolean isSafeSpot(World world, int x, int y, int z) {
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        Block ground = world.getBlockAt(x, y - 1, z);
        return feet.getType().isAir() && head.getType().isAir() && ground.getType().isSolid();
    }

    private void createSafetyPlatform(Location loc) {
        World world = loc.getWorld();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.getBlockAt(loc.getBlockX() + dx, loc.getBlockY() - 1, loc.getBlockZ() + dz)
                     .setType(Material.OBSIDIAN);
            }
        }
    }

    private Location calculateCustomDestination(Location from) {
        // Keep your existing logic here
        World fromWorld = from.getWorld();
        if (fromWorld == null) return null;

        World toWorld;
        double newX, newZ;
        double scale;

        if (fromWorld.getEnvironment() == World.Environment.NORMAL) {
            toWorld = cm.getLinkedNetherWorld(fromWorld.getName());
            if (toWorld == null) return null;
            scale = cm.getRatioForWorld(fromWorld.getName());
            newX = CoordinateMath.toNether(from.getX(), scale, cm.getOffsetXForWorld(fromWorld.getName()));
            newZ = CoordinateMath.toNether(from.getZ(), scale, cm.getOffsetZForWorld(fromWorld.getName()));
        } else {
            toWorld = cm.getLinkedOverworld(fromWorld.getName());
            if (toWorld == null) return null;
            scale = cm.getRatioForNetherWorld(fromWorld.getName());
            newX = CoordinateMath.toOverworld(from.getX(), scale, cm.getOffsetXForNetherWorld(fromWorld.getName()));
            newZ = CoordinateMath.toOverworld(from.getZ(), scale, cm.getOffsetZForNetherWorld(fromWorld.getName()));
        }
        return new Location(toWorld, newX, from.getY(), newZ);
    }

    private void createBasicPortal(World world, int x, int y, int z) {
        for (int dx = -1; dx <= 2; dx++) {
            for (int dy = 0; dy <= 4; dy++) {
                Block b = world.getBlockAt(x + dx, y + dy, z);
                if (dy == 0 || dy == 4 || dx == -1 || dx == 2) {
                    if (b.getType().isAir()) b.setType(Material.OBSIDIAN);
                }
            }
        }
        for (int dy = 1; dy <= 3; dy++) {
            for (int dx = 0; dx <= 1; dx++) {
                world.getBlockAt(x + dx, y + dy, z).setType(Material.NETHER_PORTAL);
            }
        }
    }
}
