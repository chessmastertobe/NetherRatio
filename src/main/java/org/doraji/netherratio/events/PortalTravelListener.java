package org.doraji.netherratio.events;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.doraji.netherratio.NetherRatio;
import org.doraji.netherratio.ConfigManager;
import org.doraji.netherratio.util.CoordinateMath;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class PortalTravelListener implements Listener {

    private final NetherRatio plugin;
    private final ConfigManager cm;

    // Cooldown to prevent spam
    private final Map<UUID, Long> lastPortalUse = new HashMap<>();

    // Protection flag: tracks when we last teleported this player via our plugin
    private final Map<UUID, Long> justTeleportedByPlugin = new HashMap<>();

    public PortalTravelListener(NetherRatio plugin) {
        this.plugin = plugin;
        this.cm = plugin.getConfigManager();
        plugin.getLogger().info("[NetherRatio] Strict Folia + Smart Arrival Protection");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // === NEW PROTECTION SYSTEM ===
        // If we recently teleported this player and they are still in a portal, ignore the event
        if (justTeleportedByPlugin.containsKey(uuid)) {
            long timeSinceTeleport = System.currentTimeMillis() - justTeleportedByPlugin.get(uuid);
            if (timeSinceTeleport < 5000) { // 5 seconds protection
                if (player.getLocation().getBlock().getType() == Material.NETHER_PORTAL) {
                    return; // Still inside portal after our teleport → ignore
                } else {
                    // Player has stepped out → clear protection
                    justTeleportedByPlugin.remove(uuid);
                }
            } else {
                // Protection expired
                justTeleportedByPlugin.remove(uuid);
            }
        }

        // Normal cooldown check
        long now = System.currentTimeMillis();
        if (lastPortalUse.containsKey(uuid)) {
            if (now - lastPortalUse.get(uuid) < 6000) {
                return;
            }
        }

        Location to = event.getTo();
        if (to == null || to.getBlock().getType() != Material.NETHER_PORTAL) return;

        lastPortalUse.put(uuid, now);

        Location original = event.getFrom().clone();
        Location dest = calculateCustomDestination(to);

        if (dest == null) {
            player.teleportAsync(original);
            return;
        }

        WorldBorder border = dest.getWorld().getWorldBorder();
        if (!border.isInside(dest)) {
            player.teleportAsync(original);
            return;
        }

        boolean boundsEnabled = plugin.getConfig().getBoolean("coordinate-bounds.enabled", false);
        int configMinX = plugin.getConfig().getInt("coordinate-bounds.min-x", -9999);
        int configMaxX = plugin.getConfig().getInt("coordinate-bounds.max-x", 9999);
        int configMinZ = plugin.getConfig().getInt("coordinate-bounds.min-z", -9999);
        int configMaxZ = plugin.getConfig().getInt("coordinate-bounds.max-z", 9999);
        int overworldBuffer = plugin.getConfig().getInt("coordinate-bounds.overworld-buffer", 750);
        int netherMaxY = plugin.getConfig().getInt("coordinate-bounds.nether-max-y", 120);

        final int effectiveMinX;
        final int effectiveMaxX;
        final int effectiveMinZ;
        final int effectiveMaxZ;

        if (dest.getWorld().getEnvironment() == World.Environment.NORMAL && boundsEnabled) {
            effectiveMinX = configMinX + overworldBuffer;
            effectiveMaxX = configMaxX - overworldBuffer;
            effectiveMinZ = configMinZ + overworldBuffer;
            effectiveMaxZ = configMaxZ - overworldBuffer;
        } else {
            effectiveMinX = configMinX;
            effectiveMaxX = configMaxX;
            effectiveMinZ = configMinZ;
            effectiveMaxZ = configMaxZ;
        }

        if (boundsEnabled) {
            if (dest.getX() < effectiveMinX || dest.getX() > effectiveMaxX ||
                dest.getZ() < effectiveMinZ || dest.getZ() > effectiveMaxZ) {
                player.teleportAsync(original);
                return;
            }
        }

        int searchRadius = (dest.getWorld().getEnvironment() == World.Environment.NORMAL) ? 128 : 16;

        Bukkit.getRegionScheduler().execute(plugin, dest.getWorld(), 
            dest.getBlockX() >> 4, dest.getBlockZ() >> 4, () -> {

            Location existing = findNearestPortal(dest, searchRadius, boundsEnabled, 
                    effectiveMinX, effectiveMaxX, effectiveMinZ, effectiveMaxZ);

            if (existing != null) {
                Location spawn = existing.clone().add(0.5, 0.85, 0.5);
                doTeleportWithProtection(player, spawn);
                return;
            }

            attemptSafeLocation(dest.getWorld(), dest.getBlockX(), dest.getBlockZ(), 60, safeLoc -> {
                if (safeLoc != null) {
                    int portalY = safeLoc.getBlockY();
                    if (dest.getWorld().getEnvironment() == World.Environment.NETHER && portalY > netherMaxY) {
                        portalY = netherMaxY;
                    }
                    final int finalPortalY = portalY;

                    Bukkit.getRegionScheduler().execute(plugin, safeLoc.getWorld(),
                        safeLoc.getBlockX() >> 4, safeLoc.getBlockZ() >> 4, () -> {
                            createFullLitPortal(safeLoc.getWorld(), safeLoc.getBlockX(), finalPortalY, safeLoc.getBlockZ());
                            Location spawn = new Location(safeLoc.getWorld(), safeLoc.getX() + 0.5, finalPortalY + 0.85, safeLoc.getZ() + 0.5);
                            doTeleportWithProtection(player, spawn);
                        });
                } else {
                    int highY = dest.getWorld().getEnvironment() == World.Environment.NETHER 
                            ? Math.min(netherMaxY, 120) 
                            : dest.getBlockY() + 50;

                    Location fallback = dest.clone().add(0.5, highY, 0.5);
                    Bukkit.getRegionScheduler().execute(plugin, dest.getWorld(),
                        dest.getBlockX() >> 4, dest.getBlockZ() >> 4, () -> {
                            createEmergencyHighPortal(dest.getWorld(), dest.getBlockX(), highY, dest.getBlockZ());
                            doTeleportWithProtection(player, fallback);
                        });
                }
            });
        });
    }

    // New method that sets the protection flag after teleport
    private void doTeleportWithProtection(Player player, Location target) {
        player.teleportAsync(target).thenAccept(success -> {
            if (success) {
                justTeleportedByPlugin.put(player.getUniqueId(), System.currentTimeMillis());
                player.playSound(target, Sound.BLOCK_PORTAL_TRAVEL, 0.7f, 1.0f);
                player.setNoDamageTicks(200);
                player.setFallDistance(0);
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

    private void attemptSafeLocation(World world, int x, int z, int maxAttempts, Consumer<Location> callback) {
        attemptSafeLocation(world, x, z, maxAttempts, 0, callback);
    }

    private void attemptSafeLocation(World world, int x, int z, int maxAttempts, int attempt, Consumer<Location> callback) {
        if (attempt >= maxAttempts) {
            callback.accept(null);
            return;
        }

        int y = 35 + (int)(Math.random() * 90);

        Bukkit.getRegionScheduler().execute(plugin, world, x >> 4, z >> 4, () -> {
            Block feet = world.getBlockAt(x, y, z);
            Block head = world.getBlockAt(x, y + 1, z);
            Block below = world.getBlockAt(x, y - 1, z);

            if (feet.getType().isAir() && head.getType().isAir() && below.getType().isSolid()) {
                callback.accept(new Location(world, x + 0.5, y, z + 0.5));
            } else {
                Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t ->
                    attemptSafeLocation(world, x, z, maxAttempts, attempt + 1, callback), 1L);
            }
        });
    }

    private Location calculateCustomDestination(Location from) {
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

    private Location findNearestPortal(Location center, int radius, boolean boundsEnabled, 
                                       int minX, int maxX, int minZ, int maxZ) {
        World world = center.getWorld();
        if (world == null) return null;

        for (int x = center.getBlockX() - radius; x <= center.getBlockX() + radius; x++) {
            for (int z = center.getBlockZ() - radius; z <= center.getBlockZ() + radius; z++) {
                if (boundsEnabled && (x < minX || x > maxX || z < minZ || z > maxZ)) continue;

                for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
                    if (world.getBlockAt(x, y, z).getType() == Material.NETHER_PORTAL) {
                        return new Location(world, x, y, z);
                    }
                }
            }
        }
        return null;
    }

    private void createFullLitPortal(World world, int x, int y, int z) {
        for (int dx = -1; dx <= 2; dx++) {
            for (int dy = 0; dy <= 5; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    world.getBlockAt(x + dx, y + dy, z + dz).setType(Material.AIR);
                }
            }
        }

        for (int dx = 0; dx <= 1; dx++) {
            for (int dy = 0; dy <= 4; dy++) {
                Block block = world.getBlockAt(x + dx, y + dy, z);
                if (dy == 0 || dy == 4) {
                    block.setType(Material.OBSIDIAN);
                } else {
                    block.setType(Material.NETHER_PORTAL);
                }
            }
        }

        for (int dy = 0; dy <= 4; dy++) {
            world.getBlockAt(x - 1, y + dy, z).setType(Material.OBSIDIAN);
            world.getBlockAt(x + 2, y + dy, z).setType(Material.OBSIDIAN);
        }
    }

    private void createEmergencyHighPortal(World world, int x, int y, int z) {
        for (int dx = -1; dx <= 2; dx++) {
            for (int dy = 0; dy <= 5; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    world.getBlockAt(x + dx, y + dy, z + dz).setType(Material.AIR);
                }
            }
        }

        for (int dx = 0; dx <= 1; dx++) {
            for (int dy = 0; dy <= 4; dy++) {
                Block block = world.getBlockAt(x + dx, y + dy, z);
                if (dy == 0 || dy == 4) {
                    block.setType(Material.OBSIDIAN);
                } else {
                    block.setType(Material.NETHER_PORTAL);
                }
            }
        }

        for (int dx = 0; dx <= 1; dx++) {
            world.getBlockAt(x + dx, y - 1, z - 1).setType(Material.OBSIDIAN);
            world.getBlockAt(x + dx, y - 1, z + 1).setType(Material.OBSIDIAN);
        }

        for (int dy = 0; dy <= 4; dy++) {
            world.getBlockAt(x - 1, y + dy, z).setType(Material.OBSIDIAN);
            world.getBlockAt(x + 2, y + dy, z).setType(Material.OBSIDIAN);
        }
    }
}
