package org.doraji.netherratio.events;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
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
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Location to = event.getTo();

        if (to == null || to.getBlock().getType() != Material.NETHER_PORTAL) return;

        // Protection against loops / re-triggers
        if (justTeleportedByPlugin.containsKey(uuid)) {
            long timeSince = System.currentTimeMillis() - justTeleportedByPlugin.get(uuid);
            if (timeSince < 8000) {
                if (player.getLocation().getBlock().getType() == Material.NETHER_PORTAL) {
                    return;
                } else {
                    justTeleportedByPlugin.remove(uuid);
                }
            } else {
                justTeleportedByPlugin.remove(uuid);
            }
        }

        if (lastPortalUse.containsKey(uuid) && System.currentTimeMillis() - lastPortalUse.get(uuid) < 4000) {
            return;
        }

        player.setPortalCooldown(300);
        lastPortalUse.put(uuid, System.currentTimeMillis());

        Location originalPortal = event.getFrom().clone();
        Location customDest = calculateCustomDestination(to);

        if (customDest == null) {
            lastPortalUse.remove(uuid);
            return;
        }

        plugin.getLogger().info("[NetherRatio] Taking control for 2:1 → X=" + customDest.getBlockX() + " Z=" + customDest.getBlockZ());

        int searchRadius = (customDest.getWorld().getEnvironment() == World.Environment.NORMAL) ? 64 : 16;

        Bukkit.getRegionScheduler().execute(plugin, customDest.getWorld(),
                customDest.getBlockX() >> 4, customDest.getBlockZ() >> 4, () -> {

            Location existing = findNearestPortal(customDest, searchRadius);
            if (existing != null) {
                teleportWithRetry(player, existing.clone().add(0.5, 0.85, 0.5), originalPortal, 4);
                return;
            }

            findSafeLocationAsync(customDest, safeLoc -> {
                // safeLoc is now treated as a good place to BUILD the portal
                createBasicPortal(safeLoc.getWorld(), safeLoc.getBlockX(), safeLoc.getBlockY(), safeLoc.getBlockZ());

                // Teleport player directly into the middle of the portal we just built
                Location portalCenter = new Location(safeLoc.getWorld(),
                        safeLoc.getX() + 0.5,
                        safeLoc.getY() + 1.5,
                        safeLoc.getZ() + 0.5);

                teleportWithRetry(player, portalCenter, originalPortal, 4);
            });
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPortalCreate(PortalCreateEvent event) {
        if (event.getReason() == PortalCreateEvent.CreateReason.NETHER_PAIR) {
            event.setCancelled(true);
        }
    }

    private void teleportWithRetry(Player player, Location target, Location fallback, int attemptsLeft) {
        player.teleportAsync(target).thenAccept(success -> {
            if (success) {
                justTeleportedByPlugin.put(player.getUniqueId(), System.currentTimeMillis());
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
                return new Location(world, x + 0.5, y, z + 0.5);
            }
        }
        return target; // fallback (will still try to build)
    }

    private boolean isSafeSpot(World world, int x, int y, int z) {
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        Block below = world.getBlockAt(x, y - 1, z);
        return feet.getType().isAir() && head.getType().isAir() && below.getType().isSolid();
    }

    private Location findNearestPortal(Location center, int radius) {
        World world = center.getWorld();
        if (world == null) return null;

        int cx = center.getBlockX();
        int cz = center.getBlockZ();
        Location nearest = null;
        double bestDist = Double.MAX_VALUE;

        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                for (int y = center.getBlockY() - 8; y <= center.getBlockY() + 16; y++) {
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

    private void createBasicPortal(World world, int x, int y, int z) {
        // Bottom obsidian
        for (int dx = 0; dx <= 1; dx++) {
            world.getBlockAt(x + dx, y, z).setType(Material.OBSIDIAN);
        }
        // Side pillars
        for (int dy = 0; dy <= 4; dy++) {
            world.getBlockAt(x - 1, y + dy, z).setType(Material.OBSIDIAN);
            world.getBlockAt(x + 2, y + dy, z).setType(Material.OBSIDIAN);
        }
        // Top obsidian
        for (int dx = 0; dx <= 1; dx++) {
            world.getBlockAt(x + dx, y + 4, z).setType(Material.OBSIDIAN);
        }
        // Portal blocks with correct axis
        for (int dx = 0; dx <= 1; dx++) {
            for (int dy = 1; dy <= 3; dy++) {
                Block block = world.getBlockAt(x + dx, y + dy, z);
                BlockData data = Material.NETHER_PORTAL.createBlockData();
                if (data instanceof Orientable orientable) {
                    orientable.setAxis(org.bukkit.Axis.X);
                }
                block.setBlockData(data);
            }
        }
    }

    private String formatLoc(Location loc) {
        if (loc == null || loc.getWorld() == null) return "null";
        return loc.getWorld().getName() + " (" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + ")";
    }
}
