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
    private final Map<UUID, Long> lastPortalUse = new HashMap<>();

    public PortalTravelListener(NetherRatio plugin) {
        this.plugin = plugin;
        this.cm = plugin.getConfigManager();
        plugin.getLogger().info("[NetherRatio] Strict Folia + Config Bounds + Roof Protection");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

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

        // World border check
        WorldBorder border = dest.getWorld().getWorldBorder();
        if (!border.isInside(dest)) {
            player.teleportAsync(original);
            return;
        }

        // Get bounds from config
        boolean boundsEnabled = plugin.getConfig().getBoolean("coordinate-bounds.enabled", false);
        int minX = plugin.getConfig().getInt("coordinate-bounds.min-x", -9999);
        int maxX = plugin.getConfig().getInt("coordinate-bounds.max-x", 9999);
        int minZ = plugin.getConfig().getInt("coordinate-bounds.min-z", -9999);
        int maxZ = plugin.getConfig().getInt("coordinate-bounds.max-z", 9999);
        int overworldBuffer = plugin.getConfig().getInt("coordinate-bounds.overworld-buffer", 750);
        int netherMaxY = plugin.getConfig().getInt("coordinate-bounds.nether-max-y", 120);

        // Apply buffer for Overworld
        if (dest.getWorld().getEnvironment() == World.Environment.NORMAL && boundsEnabled) {
            minX += overworldBuffer;
            maxX -= overworldBuffer;
            minZ += overworldBuffer;
            maxZ -= overworldBuffer;
        }

        // Check against bounds
        if (boundsEnabled) {
            if (dest.getX() < minX || dest.getX() > maxX || dest.getZ() < minZ || dest.getZ() > maxZ) {
                player.teleportAsync(original);
                return;
            }
        }

        int searchRadius = (dest.getWorld().getEnvironment() == World.Environment.NORMAL) ? 128 : 16;

        Bukkit.getRegionScheduler().execute(plugin, dest.getWorld(), 
            dest.getBlockX() >> 4, dest.getBlockZ() >> 4, () -> {

            Location existing = findNearestPortal(dest, searchRadius, boundsEnabled, minX, maxX, minZ, maxZ);
            if (existing != null) {
                Location spawn = existing.clone().add(0.5, 0.85, 0.5);
                doTeleport(player, spawn);
                return;
            }

            attemptSafeLocation(dest.getWorld(), dest.getBlockX(), dest.getBlockZ(), 60, safeLoc -> {
                if (safeLoc != null) {
                    // Apply nether max Y if going to Nether
                    int finalY = safeLoc.getBlockY();
                    if (dest.getWorld().getEnvironment() == World.Environment.NETHER && finalY > netherMaxY) {
                        finalY = netherMaxY;
                    }

                    Bukkit.getRegionScheduler().execute(plugin, safeLoc.getWorld(),
                        safeLoc.getBlockX() >> 4, safeLoc.getBlockZ() >> 4, () -> {
                            createFullLitPortal(safeLoc.getWorld(), safeLoc.getBlockX(), finalY, safeLoc.getBlockZ());
                            Location spawn = new Location(safeLoc.getWorld(), safeLoc.getX() + 0.5, finalY + 0.85, safeLoc.getZ() + 0.5);
                            doTeleport(player, spawn);
                        });
                } else {
                    // Emergency fallback
                    int highY = dest.getWorld().getEnvironment() == World.Environment.NETHER 
                            ? Math.min(netherMaxY, 120) 
                            : dest.getBlockY() + 50;

                    Location fallback = dest.clone().add(0.5, highY, 0.5);
                    Bukkit.getRegionScheduler().execute(plugin, dest.getWorld(),
                        dest.getBlockX() >> 4, dest.getBlockZ() >> 4, () -> {
                            createEmergencyHighPortal(dest.getWorld(), dest.getBlockX(), highY, dest.getBlockZ());
                            doTeleport(player, fallback);
                        });
                }
            });
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

    private Location findNearestPortal(Location center, int radius, boolean boundsEnabled, int minX, int maxX, int minZ, int maxZ) {
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

        // Extra platforms front and back
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
