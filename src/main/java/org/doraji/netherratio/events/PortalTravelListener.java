package org.doraji.netherratio.events;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.Axis;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.doraji.netherratio.NetherRatio;
import org.doraji.netherratio.ConfigManager;
import org.doraji.netherratio.util.CoordinateMath;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class PortalTravelListener implements Listener {

    private final NetherRatio plugin;
    private final ConfigManager cm;

    private final ConcurrentHashMap<UUID, Long> lastPortalUse = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> justTeleportedByPlugin = new ConcurrentHashMap<>();

    public PortalTravelListener(NetherRatio plugin) {
        this.plugin = plugin;
        this.cm = plugin.getConfigManager();
        plugin.getLogger().info("[NetherRatio] Fixed version - Full custom logic in PlayerMoveEvent + PortalCreateEvent cancel");
    }

    // ==================== DETECT PORTAL ENTRY ====================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Protection against re-trigger after our teleport
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

        Location customDest = calculateCustomDestination(to);
        if (customDest == null || !isWithinConfiguredBounds(customDest)) {
            player.teleportAsync(event.getFrom().clone());
            lastPortalUse.remove(uuid);
            return;
        }

        int searchRadius = (customDest.getWorld().getEnvironment() == World.Environment.NORMAL) ? 64 : 16;

        // Do the real work on the target region thread
        Bukkit.getRegionScheduler().execute(plugin, customDest.getWorld(),
                customDest.getBlockX() >> 4, customDest.getBlockZ() >> 4, () -> {

            Location existing = findNearestPortal(customDest, searchRadius);
            if (existing != null) {
                teleportWithRetry(player, existing.clone().add(0.5, 0.85, 0.5), event.getFrom().clone(), 4);
                return;
            }

            attemptSafeLocation(customDest.getWorld(), customDest.getBlockX(), customDest.getBlockZ(), 60, safeLoc -> {
                if (safeLoc != null) {
                    createProperPortal(safeLoc.getWorld(), safeLoc.getBlockX(), safeLoc.getBlockY(), safeLoc.getBlockZ());
                    teleportWithRetry(player, safeLoc.clone().add(0, 0.85, 0), event.getFrom().clone(), 4);
                } else {
                    int highY = customDest.getWorld().getEnvironment() == World.Environment.NETHER ? 120 : customDest.getBlockY() + 60;
                    createEmergencyHighPortal(customDest.getWorld(), customDest.getBlockX(), highY, customDest.getBlockZ());
                    teleportWithRetry(player,
                            new Location(customDest.getWorld(), customDest.getX() + 0.5, highY + 1.2, customDest.getZ() + 0.5),
                            event.getFrom().clone(), 4);
                }
            });
        });
    }

    // ==================== CANCEL VANILLA 8:1 CREATION ====================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent event) {
        if (event.getReason() != PortalCreateEvent.CreateReason.NETHER_PAIR) return;

        Entity entity = event.getEntity();
        if (!(entity instanceof Player player)) return;

        UUID uuid = player.getUniqueId();
        if (!lastPortalUse.containsKey(uuid)) return;

        // Cancel vanilla creation at 8:1
        event.setCancelled(true);
        lastPortalUse.remove(uuid); // Clean up
    }

    // ==================== TELEPORT WITH RETRY ====================
    private void teleportWithRetry(Player player, Location target, Location fallback, int attemptsLeft) {
        player.teleportAsync(target).thenAccept(success -> {
            if (success) {
                justTeleportedByPlugin.put(player.getUniqueId(), System.currentTimeMillis());
                player.playSound(target, Sound.BLOCK_PORTAL_TRAVEL, 0.7f, 1.0f);
                player.setNoDamageTicks(200);
                player.setFallDistance(0);
            } else if (attemptsLeft > 0) {
                Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t ->
                        teleportWithRetry(player, target, fallback, attemptsLeft - 1), 3L);
            } else {
                player.teleportAsync(fallback);
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
        return loc.getX() >= minX && loc.getX() <= maxX && loc.getZ() >= minZ && loc.getZ() <= maxZ;
    }

    private void attemptSafeLocation(World world, int x, int z, int maxAttempts, Consumer<Location> callback) {
        attemptSafeLocation(world, x, z, maxAttempts, 0, callback);
    }

    private void attemptSafeLocation(World world, int x, int z, int maxAttempts, int attempt, Consumer<Location> callback) {
        if (attempt >= maxAttempts) {
            callback.accept(null);
            return;
        }
        int y = 35 + (int)(Math.random() * 110);

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

    private Location findNearestPortal(Location center, int radius) {
        World world = center.getWorld();
        if (world == null) return null;

        int cx = center.getBlockX();
        int cz = center.getBlockZ();
        int cy = center.getBlockY();

        Location nearest = null;
        double bestDist = Double.MAX_VALUE;

        int minY = Math.max(world.getMinHeight(), cy - 48);
        int maxY = Math.min(world.getMaxHeight(), cy + 48);

        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                for (int y = minY; y <= maxY; y++) {
                    if (world.getBlockAt(x, y, z).getType() == Material.NETHER_PORTAL) {
                        double dist = center.distanceSquared(new Location(world, x + 0.5, y + 0.5, z + 0.5));
                        if (dist < bestDist) {
                            bestDist = dist;
                            nearest = new Location(world, x, y, z);
                        }
                    }
                }
            }
        }
        return nearest;
    }

    private void createProperPortal(World world, int x, int y, int z) {
        for (int dx = -2; dx <= 3; dx++) {
            for (int dy = -1; dy <= 6; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
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
                    BlockData data = Material.NETHER_PORTAL.createBlockData();
                    if (data instanceof Orientable orientable) {
                        orientable.setAxis(Axis.X);
                    }
                    block.setBlockData(data);
                }
            }
        }

        for (int dy = 0; dy <= 4; dy++) {
            world.getBlockAt(x - 1, y + dy, z).setType(Material.OBSIDIAN);
            world.getBlockAt(x + 2, y + dy, z).setType(Material.OBSIDIAN);
        }
    }

    private void createEmergencyHighPortal(World world, int x, int y, int z) {
        createProperPortal(world, x, y, z);
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                world.getBlockAt(x + dx, y - 1, z + dz).setType(Material.OBSIDIAN);
            }
        }
    }
}
