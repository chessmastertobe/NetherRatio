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
    private final Map<UUID, Long> lastPortalUse = new HashMap<>();
    private final Map<UUID, Long> justTeleportedByPlugin = new HashMap<>();

    public PortalTravelListener(NetherRatio plugin) {
        this.plugin = plugin;
        this.cm = plugin.getConfigManager();
        plugin.getLogger().info("[NetherRatio] Stable + Strong RTP + Reliable Lighting");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (justTeleportedByPlugin.containsKey(uuid)) {
            long timeSince = System.currentTimeMillis() - justTeleportedByPlugin.get(uuid);
            if (timeSince < 6000) {
                if (player.getLocation().getBlock().getType() == Material.NETHER_PORTAL) {
                    return;
                } else {
                    justTeleportedByPlugin.remove(uuid);
                }
            } else {
                justTeleportedByPlugin.remove(uuid);
            }
        }

        long now = System.currentTimeMillis();
        if (lastPortalUse.containsKey(uuid) && now - lastPortalUse.get(uuid) < 4000) {
            return;
        }

        Location to = event.getTo();
        if (to == null || to.getBlock().getType() != Material.NETHER_PORTAL) return;

        lastPortalUse.put(uuid, now);

        Location original = event.getFrom().clone();
        Location dest = calculateCustomDestination(to);

        if (dest == null || !isWithinConfiguredBounds(dest)) {
            return;
        }

        int searchRadius = (dest.getWorld().getEnvironment() == World.Environment.NORMAL) ? 128 : 16;

        Bukkit.getRegionScheduler().execute(plugin, dest.getWorld(), 
            dest.getBlockX() >> 4, dest.getBlockZ() >> 4, () -> {

            Location existing = findNearestPortal(dest, searchRadius);
            if (existing != null) {
                doTeleportWithTag(player, existing.clone().add(0.5, 0.85, 0.5));
                return;
            }

            attemptSafeLocation(dest.getWorld(), dest.getBlockX(), dest.getBlockZ(), 100, safeLoc -> {
                if (safeLoc != null) {
                    createReliablePortal(safeLoc.getWorld(), safeLoc.getBlockX(), safeLoc.getBlockY(), safeLoc.getBlockZ());
                    doTeleportWithTag(player, safeLoc.clone().add(0.5, 0.85, 0.5));
                } else {
                    int highY = dest.getWorld().getEnvironment() == World.Environment.NETHER ? 120 : dest.getBlockY() + 60;
                    createEmergencyHighPortal(dest.getWorld(), dest.getBlockX(), highY, dest.getBlockZ());
                    doTeleportWithTag(player, new Location(dest.getWorld(), dest.getX() + 0.5, highY + 1.5, dest.getZ() + 0.5));
                }
            });
        });
    }

    private void doTeleportWithTag(Player player, Location target) {
        player.teleportAsync(target).thenAccept(success -> {
            if (success) {
                justTeleportedByPlugin.put(player.getUniqueId(), System.currentTimeMillis());
                player.playSound(target, Sound.BLOCK_PORTAL_TRAVEL, 0.7f, 1.0f);
                player.setNoDamageTicks(200);
                player.setFallDistance(0);
            }
        });
    }

    private boolean isWithinConfiguredBounds(Location loc) {
        boolean enabled = plugin.getConfig().getBoolean("coordinate-bounds.enabled", false);
        if (!enabled) return true;

        int minX, maxX, minZ, maxZ;

        if (loc.getWorld().getEnvironment() == World.Environment.NORMAL) {
            minX = plugin.getConfig().getInt("coordinate-bounds.overworld.min-x", -19999);
            maxX = plugin.getConfig().getInt("coordinate-bounds.overworld.max-x", 19999);
            minZ = plugin.getConfig().getInt("coordinate-bounds.overworld.min-z", -19999);
            maxZ = plugin.getConfig().getInt("coordinate-bounds.overworld.max-z", 19999);
        } else {
            minX = plugin.getConfig().getInt("coordinate-bounds.nether.min-x", -9999);
            maxX = plugin.getConfig().getInt("coordinate-bounds.nether.max-x", 9999);
            minZ = plugin.getConfig().getInt("coordinate-bounds.nether.min-z", -9999);
            maxZ = plugin.getConfig().getInt("coordinate-bounds.nether.max-z", 9999);
        }

        return loc.getX() >= minX && loc.getX() <= maxX &&
               loc.getZ() >= minZ && loc.getZ() <= maxZ;
    }

    private void attemptSafeLocation(World world, int x, int z, int maxAttempts, Consumer<Location> callback) {
        attemptSafeLocation(world, x, z, maxAttempts, 0, callback);
    }

    private void attemptSafeLocation(World world, int x, int z, int maxAttempts, int attempt, Consumer<Location> callback) {
        if (attempt >= maxAttempts) {
            callback.accept(null);
            return;
        }

        int y = 35 + (int)(Math.random() * 120); // Wider search

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

    private void createReliablePortal(World world, int x, int y, int z) {
        // Minimal clearing
        for (int dx = 0; dx <= 1; dx++) {
            for (int dy = 0; dy <= 4; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    world.getBlockAt(x + dx, y + dy, z + dz).setType(Material.AIR);
                }
            }
        }

        // Build frame
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

        // Force lighting twice
        for (int dx = 0; dx <= 1; dx++) {
            for (int dy = 1; dy <= 3; dy++) {
                world.getBlockAt(x + dx, y + dy, z).setType(Material.NETHER_PORTAL);
            }
        }

        // Side pillars
        for (int dy = 0; dy <= 4; dy++) {
            world.getBlockAt(x - 1, y + dy, z).setType(Material.OBSIDIAN);
            world.getBlockAt(x + 2, y + dy, z).setType(Material.OBSIDIAN);
        }
    }

    private void createEmergencyHighPortal(World world, int x, int y, int z) {
        createReliablePortal(world, x, y, z);

        // Extra platforms
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                world.getBlockAt(x + dx, y - 1, z + dz).setType(Material.OBSIDIAN);
            }
        }
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

    private Location findNearestPortal(Location center, int radius) {
        World world = center.getWorld();
        if (world == null) return null;

        for (int x = center.getBlockX() - radius; x <= center.getBlockX() + radius; x++) {
            for (int z = center.getBlockZ() - radius; z <= center.getBlockZ() + radius; z++) {
                for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
                    if (world.getBlockAt(x, y, z).getType() == Material.NETHER_PORTAL) {
                        return new Location(world, x, y, z);
                    }
                }
            }
        }
        return null;
    }
}
