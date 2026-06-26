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

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class PortalTravelListener implements Listener {

    private final NetherRatio plugin;
    private final ConfigManager cm;

    private final Map<UUID, Long> lastPortalUse = new ConcurrentHashMap<>();
    private final Map<UUID, Long> justTeleportedByPlugin = new ConcurrentHashMap<>();

    public PortalTravelListener(NetherRatio plugin) {
        this.plugin = plugin;
        this.cm = plugin.getConfigManager();
        plugin.getLogger().info("[NetherRatio] Stable + Retry Loop vs Folia + Per-Destination Bounds + Vanilla Cancel");
    }

@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
public void onPortalCreate(PortalCreateEvent e) {
    // Only cancel the automatic "exit portal" creation that vanilla does during travel
    // (the one that would use the old 8:1 ratio)
    // Keep FIRE reason so players can still ignite normal portals with flint & steel
    if (e.getReason() == PortalCreateEvent.CreateReason.NETHER_PAIR ||
        e.getReason().toString().contains("NETHER")) {   // safe fallback for any version
        e.setCancelled(true);
        if (!e.getBlocks().isEmpty()) {
            plugin.getLogger().fine("[NetherRatio] Cancelled vanilla travel portal creation at " + e.getBlocks().get(0).getLocation());
        }
    }
    // FIRE reason (player lighting a new portal) is allowed through
}

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Tag protection
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

        // Kick player slightly out of the portal block to interrupt vanilla travel timer
        Location originalPortal = event.getFrom().clone();
        player.teleportAsync(originalPortal.clone().add(0, 0.1, 0));

        Location customDest = calculateCustomDestination(to);
        if (customDest == null || !isWithinConfiguredBounds(customDest)) {
            player.teleportAsync(originalPortal);
            return;
        }

        int searchRadius = (customDest.getWorld().getEnvironment() == World.Environment.NORMAL) ? 48 : 24;

        Bukkit.getRegionScheduler().execute(plugin, customDest.getWorld(),
                customDest.getBlockX() >> 4, customDest.getBlockZ() >> 4, () -> {

            Location existing = findNearestPortal(customDest, searchRadius);
            if (existing != null) {
                teleportWithRetry(player, existing.clone().add(0.5, 0.85, 0.5), originalPortal, 4);
                return;
            }

            attemptSafeLocation(customDest.getWorld(), customDest.getBlockX(), customDest.getBlockZ(), 80, safeLoc -> {
                if (safeLoc != null) {
                    createProperPortal(safeLoc.getWorld(), safeLoc.getBlockX(), safeLoc.getBlockY(), safeLoc.getBlockZ());
                    teleportWithRetry(player, safeLoc.clone().add(0.5, 0.85, 0.5), originalPortal, 4);
                } else {
                    int highY = customDest.getWorld().getEnvironment() == World.Environment.NETHER ? 120 : customDest.getBlockY() + 60;
                    createEmergencyHighPortal(customDest.getWorld(), customDest.getBlockX(), highY, customDest.getBlockZ());
                    teleportWithRetry(player, new Location(customDest.getWorld(), customDest.getX() + 0.5, highY + 1.5, customDest.getZ() + 0.5), originalPortal, 4);
                }
            });
        });
    }

    // ==================== Retry Loop to Fight Folia ====================
    private void teleportWithRetry(Player player, Location target, Location fallback, int attemptsLeft) {
        player.teleportAsync(target).thenAccept(success -> {
            if (success) {
                Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
                    justTeleportedByPlugin.put(player.getUniqueId(), System.currentTimeMillis());
                    player.playSound(target, Sound.BLOCK_PORTAL_TRAVEL, 0.7f, 1.0f);
                    player.setNoDamageTicks(200);
                    player.setFallDistance(0);
                });
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

        Bukkit.getRegionScheduler().execute(plugin, world, x >> 4, z >> 4, () -> {
            int startY = world.getEnvironment() == World.Environment.NETHER ? 65 : 75;
            for (int dy = 0; dy < 55; dy += 3) {
                int y = startY + dy + (attempt % 8) * 2;
                Block below = world.getBlockAt(x, y - 1, z);
                Block feet = world.getBlockAt(x, y, z);
                Block head = world.getBlockAt(x, y + 1, z);
                if (below.getType().isSolid() && !below.getType().name().contains("LAVA") &&
                    feet.getType().isAir() && head.getType().isAir()) {
                    callback.accept(new Location(world, x + 0.5, y, z + 0.5));
                    return;
                }
            }
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t ->
                attemptSafeLocation(world, x, z, maxAttempts, attempt + 1, callback), 1L);
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
        int minY = world.getEnvironment() == World.Environment.NETHER ? 30 : 50;
        int maxY = world.getEnvironment() == World.Environment.NETHER ? 125 : 200;
        int searchRadius = Math.min(radius, 48);

        for (int r = 0; r <= searchRadius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) == r || Math.abs(dz) == r) {
                        int x = cx + dx;
                        int z = cz + dz;
                        for (int y = minY; y < maxY; y += 2) {
                            if (world.getBlockAt(x, y, z).getType() == Material.NETHER_PORTAL) {
                                return new Location(world, x, y, z);
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

private void createProperPortal(World world, int x, int y, int z) {
    // Clear ONLY the portal area — DO NOT clear the ground below
    for (int dx = -2; dx <= 3; dx++) {
        for (int dy = 0; dy <= 5; dy++) {          // changed from -1 → 0
            for (int dz = -2; dz <= 2; dz++) {
                world.getBlockAt(x + dx, y + dy, z + dz).setType(Material.AIR);
            }
        }
    }

    // Solid wide platform underneath (this stops floating forever)
    for (int dx = -2; dx <= 3; dx++) {
        for (int dz = -1; dz <= 1; dz++) {
            world.getBlockAt(x + dx, y - 1, z + dz).setType(Material.OBSIDIAN);
        }
    }

    // Full connected obsidian frame
    for (int dx = -1; dx <= 2; dx++) {
        world.getBlockAt(x + dx, y, z).setType(Material.OBSIDIAN);      // bottom
        world.getBlockAt(x + dx, y + 4, z).setType(Material.OBSIDIAN); // top
    }
    for (int dy = 0; dy <= 4; dy++) {
        world.getBlockAt(x - 1, y + dy, z).setType(Material.OBSIDIAN);
        world.getBlockAt(x + 2, y + dy, z).setType(Material.OBSIDIAN);
    }

    // Portal blocks
    for (int dx = 0; dx <= 1; dx++) {
        for (int dy = 1; dy <= 3; dy++) {
            world.getBlockAt(x + dx, y + dy, z).setType(Material.NETHER_PORTAL);
        }
    }

    // Strong auto-lighting (two fires + delay so Folia registers it)
    world.getBlockAt(x, y + 1, z).setType(Material.FIRE);
    world.getBlockAt(x + 1, y + 1, z).setType(Material.FIRE);

    Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> {
        // Clean fires
        if (world.getBlockAt(x, y + 1, z).getType() == Material.FIRE) {
            world.getBlockAt(x, y + 1, z).setType(Material.AIR);
        }
        if (world.getBlockAt(x + 1, y + 1, z).getType() == Material.FIRE) {
            world.getBlockAt(x + 1, y + 1, z).setType(Material.AIR);
        }
        // Refresh portal blocks (extra safety)
        world.getBlockAt(x, y + 1, z).setType(Material.NETHER_PORTAL);
        world.getBlockAt(x + 1, y + 1, z).setType(Material.NETHER_PORTAL);
    }, 5L);
}

private void createEmergencyHighPortal(World world, int x, int y, int z) {
    createProperPortal(world, x, y, z);   // reuse the new method

    // Extra thick safety floor for high Y
    for (int dx = -2; dx <= 3; dx++) {
        for (int dz = -2; dz <= 2; dz++) {
            world.getBlockAt(x + dx, y - 1, z + dz).setType(Material.OBSIDIAN);
        }
    }
}
}
