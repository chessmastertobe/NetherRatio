package org.doraji.netherratio.events;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
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
        plugin.getLogger().info("[NetherRatio] Debug v15 - Heavy Bounds Debugging");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        if (to == null || to.getBlock().getType() != Material.NETHER_PORTAL) return;

        Location originalPortal = event.getFrom().clone();

        plugin.getLogger().info("[Portal] === PLAYER ENTERED PORTAL ===");
        plugin.getLogger().info("[Portal] Player: " + player.getName());
        plugin.getLogger().info("[Portal] Entry Location: " + formatLoc(to));

        Location customDest = calculateCustomDestination(to);
        if (customDest == null) {
            plugin.getLogger().warning("[Portal] Could not calculate destination");
            player.teleportAsync(originalPortal);
            return;
        }

        plugin.getLogger().info("[Portal] Calculated destination BEFORE bounds check: " + formatLoc(customDest));

        // === Load destination-specific bounds from config ===
        boolean boundsEnabled = plugin.getConfig().getBoolean("coordinate-bounds.enabled", false);

        int minX, maxX, minZ, maxZ;

        if (customDest.getWorld().getEnvironment() == World.Environment.NORMAL) {
            // Going to Overworld
            minX = plugin.getConfig().getInt("coordinate-bounds.overworld.min-x", -19999);
            maxX = plugin.getConfig().getInt("coordinate-bounds.overworld.max-x", 19999);
            minZ = plugin.getConfig().getInt("coordinate-bounds.overworld.min-z", -19999);
            maxZ = plugin.getConfig().getInt("coordinate-bounds.overworld.max-z", 19999);
            plugin.getLogger().info("[Bounds] Using OVERWORLD bounds from config");
        } else {
            // Going to Nether
            minX = plugin.getConfig().getInt("coordinate-bounds.nether.min-x", -9999);
            maxX = plugin.getConfig().getInt("coordinate-bounds.nether.max-x", 9999);
            minZ = plugin.getConfig().getInt("coordinate-bounds.nether.min-z", -9999);
            maxZ = plugin.getConfig().getInt("coordinate-bounds.nether.max-z", 9999);
            plugin.getLogger().info("[Bounds] Using NETHER bounds from config");
        }

        plugin.getLogger().info("[Bounds] Loaded values → minX=" + minX + ", maxX=" + maxX + ", minZ=" + minZ + ", maxZ=" + maxZ);

        // Bounds check
        if (boundsEnabled) {
            boolean withinBounds = customDest.getX() >= minX && customDest.getX() <= maxX &&
                                   customDest.getZ() >= minZ && customDest.getZ() <= maxZ;

            plugin.getLogger().info("[Bounds] Destination within bounds? " + withinBounds);

            if (!withinBounds) {
                plugin.getLogger().warning("[Portal] REJECTED - Destination outside configured bounds");
                player.teleportAsync(originalPortal);
                return;
            }
        }

        plugin.getLogger().info("[Portal] Bounds check passed. Continuing...");

        findSafeLocationAsync(customDest, safeDest -> {
            plugin.getLogger().info("[Portal] Safe destination after search: " + formatLoc(safeDest));

            Location target = findNearestPortal(safeDest, 8);

            Location spawnLoc;
            if (target != null) {
                plugin.getLogger().info("[Portal] Found existing portal at: " + formatLoc(target));
                spawnLoc = target.clone().add(0.5, 1.5, 0.5);
            } else {
                if (isSafeSpot(safeDest.getWorld(), safeDest.getBlockX(), safeDest.getBlockY(), safeDest.getBlockZ())) {
                    createBasicPortal(safeDest.getWorld(), safeDest.getBlockX(), safeDest.getBlockY(), safeDest.getBlockZ());
                    spawnLoc = safeDest.clone().add(0.5, 1.5, 0.5);
                    plugin.getLogger().info("[Portal] Created new portal");
                } else {
                    spawnLoc = originalPortal;
                    plugin.getLogger().warning("[Portal] No safe spot found - falling back to original");
                }
            }

            plugin.getLogger().info("[Teleport] FINAL SPAWN LOCATION: " + formatLoc(spawnLoc));
            teleportWithRetry(player, spawnLoc, originalPortal, 5);
        });
    }

    // ... (rest of the methods remain the same: onPortalCreate, teleportWithRetry, calculateCustomDestination, findSafeLocationAsync, etc.)

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPortalCreate(PortalCreateEvent event) {
        if (event.getReason() == PortalCreateEvent.CreateReason.NETHER_PAIR) {
            event.setCancelled(true);
        }
    }

    private void teleportWithRetry(Player player, Location target, Location fallback, int attemptsLeft) {
        plugin.getLogger().info("[Teleport] Attempt #" + (6 - attemptsLeft) + " → " + formatLoc(target));

        player.teleportAsync(target).thenAccept(success -> {
            Location current = player.getLocation();
            plugin.getLogger().info("[Teleport] Success=" + success + " | Player now at " + formatLoc(current) + " Y=" + current.getY());

            if (success) {
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

        return new Location(toWorld, newX, from.getY(), newZ, from.getYaw(), from.getPitch());
    }

    private void findSafeLocationAsync(Location target, Consumer<Location> callback) {
        World world = target.getWorld();
        if (world == null) {
            callback.accept(target);
            return;
        }

        Bukkit.getRegionScheduler().execute(plugin, world, target.getBlockX() >> 4, target.getBlockZ() >> 4, () -> {
            callback.accept(findSafeLocationSync(target));
        });
    }

    private Location findSafeLocationSync(Location target) {
        World world = target.getWorld();
        int x = target.getBlockX();
        int z = target.getBlockZ();

        for (int y = Math.min(target.getBlockY() + 30, 150); y >= Math.max(target.getBlockY() - 30, 20); y--) {
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

    private Location findNearestPortal(Location center, int radius) {
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cz = center.getBlockZ();

        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                for (int y = center.getBlockY() - 6; y <= center.getBlockY() + 12; y++) {
                    if (world.getBlockAt(x, y, z).getType() == Material.NETHER_PORTAL) {
                        return new Location(world, x + 0.5, y, z + 0.5);
                    }
                }
            }
        }
        return null;
    }

    private void createBasicPortal(World world, int x, int y, int z) {
        for (int dx = -1; dx <= 2; dx++) {
            for (int dy = 0; dy <= 4; dy++) {
                Block block = world.getBlockAt(x + dx, y + dy, z);
                if (dy == 0 || dy == 4 || dx == -1 || dx == 2) {
                    if (block.getType().isAir()) block.setType(Material.OBSIDIAN);
                }
            }
        }
        for (int dy = 1; dy <= 3; dy++) {
            for (int dx = 0; dx <= 1; dx++) {
                world.getBlockAt(x + dx, y + dy, z).setType(Material.NETHER_PORTAL);
            }
        }
    }

    private String formatLoc(Location loc) {
        if (loc == null || loc.getWorld() == null) return "null";
        return loc.getWorld().getName() + " (" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + ")";
    }
}
