package org.doraji.netherratio.events;

import org.doraji.netherratio.NetherRatio;
import org.doraji.netherratio.ConfigManager;
import org.doraji.netherratio.util.CoordinateMath;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class PortalTravelListener implements Listener {

    private final NetherRatio plugin;
    private final ConfigManager cm;

    public PortalTravelListener(NetherRatio plugin) {
        this.plugin = plugin;
        this.cm = plugin.getConfigManager();
        plugin.getLogger().info("[NetherRatio] Debug listener loaded");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        plugin.getLogger().info("[NetherRatio Debug] Portal event triggered for " + event.getPlayer().getName());
        plugin.getLogger().info("[NetherRatio Debug] Cause: " + event.getCause());

        if (event.getCause() != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            plugin.getLogger().info("[NetherRatio Debug] Not a nether portal, ignoring.");
            return;
        }

        Player player = event.getPlayer();
        Location from = event.getFrom();

        plugin.getLogger().info("[NetherRatio Debug] From world: " + from.getWorld().getName());
        plugin.getLogger().info("[NetherRatio Debug] From coords: " + from.getBlockX() + ", " + from.getBlockY() + ", " + from.getBlockZ());

        Location destination = calculateDestination(from);

        if (destination == null) {
            plugin.getLogger().warning("[NetherRatio Debug] Destination was null - falling back to vanilla");
            return;
        }

        plugin.getLogger().info("[NetherRatio Debug] Calculated destination world: " + destination.getWorld().getName());
        plugin.getLogger().info("[NetherRatio Debug] Calculated destination: " + destination.getBlockX() + ", " + destination.getBlockY() + ", " + destination.getBlockZ());

        Location safeDestination = findSafeLocation(destination);
        plugin.getLogger().info("[NetherRatio Debug] Safe destination: " + safeDestination.getBlockX() + ", " + safeDestination.getBlockY() + ", " + safeDestination.getBlockZ());

        ensurePortalExists(safeDestination);
        event.setTo(safeDestination);
    }

    private Location calculateDestination(Location from) {
        World fromWorld = from.getWorld();
        if (fromWorld == null) {
            plugin.getLogger().warning("[NetherRatio Debug] fromWorld is null");
            return null;
        }

        World toWorld;
        double newX, newZ;
        double scale;

        plugin.getLogger().info("[NetherRatio Debug] Environment: " + fromWorld.getEnvironment());

        if (fromWorld.getEnvironment() == World.Environment.NORMAL) {
            toWorld = cm.getLinkedNetherWorld(fromWorld.getName());
            plugin.getLogger().info("[NetherRatio Debug] Linked Nether world: " + (toWorld != null ? toWorld.getName() : "NULL"));

            if (toWorld == null) return null;

            scale = cm.getRatioForWorld(fromWorld.getName());
            plugin.getLogger().info("[NetherRatio Debug] Using ratio: " + scale);

            newX = CoordinateMath.toNether(from.getX(), scale, cm.getOffsetXForWorld(fromWorld.getName()));
            newZ = CoordinateMath.toNether(from.getZ(), scale, cm.getOffsetZForWorld(fromWorld.getName()));

        } else if (fromWorld.getEnvironment() == World.Environment.NETHER) {
            toWorld = cm.getLinkedOverworld(fromWorld.getName());
            plugin.getLogger().info("[NetherRatio Debug] Linked Overworld world: " + (toWorld != null ? toWorld.getName() : "NULL"));

            if (toWorld == null) return null;

            scale = cm.getRatioForNetherWorld(fromWorld.getName());
            plugin.getLogger().info("[NetherRatio Debug] Using ratio: " + scale);

            newX = CoordinateMath.toOverworld(from.getX(), scale, cm.getOffsetXForNetherWorld(fromWorld.getName()));
            newZ = CoordinateMath.toOverworld(from.getZ(), scale, cm.getOffsetZForNetherWorld(fromWorld.getName()));

        } else {
            plugin.getLogger().info("[NetherRatio Debug] Not Overworld or Nether - ignoring");
            return null;
        }

        if (cm.areBoundsEnabled() && !cm.areCoordinatesWithinBounds(newX, newZ)) {
            plugin.getLogger().info("[NetherRatio Debug] Coordinates out of bounds - clamping");
            double[] clamped = cm.clampCoordinates(newX, newZ);
            newX = clamped[0];
            newZ = clamped[1];
        }

        return new Location(toWorld, newX, from.getY(), newZ, from.getYaw(), from.getPitch());
    }

    private Location findSafeLocation(Location target) {
        // (same as previous version)
        World world = target.getWorld();
        if (world == null) return target;

        int x = target.getBlockX();
        int z = target.getBlockZ();

        for (int y = Math.min(target.getBlockY() + 8, 120); y >= Math.max(target.getBlockY() - 8, 30); y--) {
            if (isSafeSpot(world, x, y, z)) {
                return new Location(world, x + 0.5, y, z + 0.5, target.getYaw(), target.getPitch());
            }
        }
        return target;
    }

    private boolean isSafeSpot(World world, int x, int y, int z) {
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        Block below = world.getBlockAt(x, y - 1, z);
        return feet.getType().isAir() && head.getType().isAir() && below.getType().isSolid();
    }

    private void ensurePortalExists(Location location) {
        // (same basic portal creation logic as before)
        World world = location.getWorld();
        if (world == null) return;

        int x = location.getBlockX();
        int z = location.getBlockZ();
        int y = location.getBlockY();

        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -2; dy <= 5; dy++) {
                    if (world.getBlockAt(x + dx, y + dy, z + dz).getType() == Material.NETHER_PORTAL) {
                        plugin.getLogger().info("[NetherRatio Debug] Portal already exists nearby");
                        return;
                    }
                }
            }
        }

        plugin.getLogger().info("[NetherRatio Debug] No portal found - creating basic portal");
        createBasicPortal(world, x, y, z);
    }

    private void createBasicPortal(World world, int x, int y, int z) {
        for (int dx = -1; dx <= 2; dx++) {
            for (int dy = 0; dy <= 4; dy++) {
                Block block = world.getBlockAt(x + dx, y + dy, z);
                if (dy == 0 || dy == 4 || dx == -1 || dx == 2) {
                    if (block.getType().isAir()) {
                        block.setType(Material.OBSIDIAN);
                    }
                }
            }
        }

        for (int dy = 1; dy <= 3; dy++) {
            for (int dx = 0; dx <= 1; dx++) {
                world.getBlockAt(x + dx, y + dy, z).setType(Material.NETHER_PORTAL);
            }
        }
        plugin.getLogger().info("[NetherRatio Debug] Basic portal created at " + x + ", " + y + ", " + z);
    }
}
