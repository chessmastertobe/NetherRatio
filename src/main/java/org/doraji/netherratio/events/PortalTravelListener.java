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
        plugin.getLogger().info("[NetherRatio] Heavy Debug Stable Version");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        if (to == null || to.getBlock().getType() != Material.NETHER_PORTAL) return;

        Location original = event.getFrom().clone();
        plugin.getLogger().info("[Portal] Player entered portal at " + format(original));

        Location dest = calculateCustomDestination(to);
        if (dest == null) {
            player.teleportAsync(original);
            return;
        }

        // Try to find existing portal first
        Location existing = findNearestPortal(dest, 16);
        if (existing != null) {
            Location spawn = existing.clone().add(0.5, 0.9, 0.5);
            plugin.getLogger().info("[Portal] Found existing portal → teleporting to " + format(spawn));
            teleportSafe(player, spawn);
            return;
        }

        // No existing portal → try to create one safely
        Location safeSpot = findSafeY(dest);
        if (safeSpot != null) {
            createBasicPortal(safeSpot.getWorld(), safeSpot.getBlockX(), safeSpot.getBlockY(), safeSpot.getBlockZ());
            Location spawn = safeSpot.clone().add(0.5, 0.9, 0.5);
            plugin.getLogger().info("[Portal] Created new portal → teleporting to " + format(spawn));
            teleportSafe(player, spawn);
        } else {
            plugin.getLogger().warning("[Portal] Could not find safe location. Falling back to high Y.");
            Location highSpawn = dest.clone().add(0, 20, 0);
            createSafetyPlatform(highSpawn);
            teleportSafe(player, highSpawn);
        }
    }

    private void teleportSafe(Player player, Location target) {
        player.teleportAsync(target).thenAccept(success -> {
            if (success) {
                player.playSound(target, Sound.BLOCK_PORTAL_TRAVEL, 0.7f, 1.0f);
                player.setNoDamageTicks(200);
                player.setFallDistance(0);
            }
        });
    }

    private Location findSafeY(Location base) {
        World world = base.getWorld();
        int x = base.getBlockX();
        int z = base.getBlockZ();

        for (int y = base.getBlockY() + 20; y >= base.getBlockY() - 20; y--) {
            if (isSafeSpot(world, x, y, z)) {
                return new Location(world, x, y, z);
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
        // Same as before (keep your existing logic)
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

    private Location findNearestPortal(Location center, int radius) {
        // Same improved version from before
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

    private void createBasicPortal(World world, int x, int y, int z) {
        // Same as previous version
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

    private String format(Location loc) {
        if (loc == null || loc.getWorld() == null) return "null";
        return loc.getWorld().getName() + " (" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + ")";
    }
}
