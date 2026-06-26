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

    // Thread-safe protection (fixes the crazy loop)
    private final ConcurrentHashMap<UUID, Long> lastPortalUse = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> justTeleportedByPlugin = new ConcurrentHashMap<>();

    public PortalTravelListener(NetherRatio plugin) {
        this.plugin = plugin;
        this.cm = plugin.getConfigManager();
        plugin.getLogger().info("[NetherRatio] Debug v15 - Loop protection added");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Location to = event.getTo();

        if (to == null || to.getBlock().getType() != Material.NETHER_PORTAL) return;

        // === LOOP PROTECTION ===
        if (justTeleportedByPlugin.containsKey(uuid)) {
            long timeSince = System.currentTimeMillis() - justTeleportedByPlugin.get(uuid);
            if (timeSince < 8000) {
                if (player.getLocation().getBlock().getType() == Material.NETHER_PORTAL) {
                    return; // Still inside our newly created portal → skip
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

        Location originalPortal = event.getFrom().clone();

        plugin.getLogger().info("[Portal] Player: " + player.getName());
        plugin.getLogger().info("[Portal] Entry detected at: " + formatLoc(to));

        // Interrupt vanilla early
        player.setPortalCooldown(200);
        lastPortalUse.put(uuid, System.currentTimeMillis());

        // Your original delay idea
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
            processPortalTeleport(player, originalPortal, to);
        }, 5L);
    }

    private void processPortalTeleport(Player player, Location originalPortal, Location entryLocation) {
        Location customDest = calculateCustomDestination(entryLocation);
        if (customDest == null) {
            plugin.getLogger().warning("[Portal] Failed to calculate destination");
            player.teleportAsync(originalPortal);
            lastPortalUse.remove(player.getUniqueId());
            return;
        }

        plugin.getLogger().info("[Portal] Calculated 2:1 destination: " + formatLoc(customDest));

        findSafeLocationAsync(customDest, safeDest -> {
            plugin.getLogger().info("[Portal] Safe location result: " + formatLoc(safeDest));

            Location targetLoc = findNearestPortal(safeDest, 8);
            Location spawnLoc;

            if (targetLoc != null) {
                spawnLoc = targetLoc.clone().add(0.5, 0.85, 0.5);
                plugin.getLogger().info("[Portal] Found existing portal nearby");
            } else {
                if (isSafeSpot(safeDest.getWorld(), safeDest.getBlockX(), safeDest.getBlockY(), safeDest.getBlockZ())) {
                    createBasicPortal(safeDest.getWorld(), safeDest.getBlockX(), safeDest.getBlockY(), safeDest.getBlockZ());
                    spawnLoc = safeDest.clone().add(0.5, 1.2, 0.5);
                    plugin.getLogger().info("[Portal] Created new portal at: " + formatLoc(spawnLoc));
                } else {
                    spawnLoc = originalPortal;
                    plugin.getLogger().warning("[Portal] No safe spot found - falling back");
                }
            }

            plugin.getLogger().info("[Teleport] FINAL SPAWN: " + formatLoc(spawnLoc));
            teleportWithRetry(player, spawnLoc, originalPortal, 5);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPortalCreate(PortalCreateEvent event) {
        if (event.getReason() == PortalCreateEvent.CreateReason.NETHER_PAIR) {
            event.setCancelled(true);
            plugin.getLogger().info("[Portal] Cancelled vanilla NETHER_PAIR creation");
        }
    }

    private void teleportWithRetry(Player player, Location target, Location fallback, int attemptsLeft) {
        player.teleportAsync(target).thenAccept(success -> {
            if (success) {
                // Set protection tag so we don't re-trigger on the new portal
                justTeleportedByPlugin.put(player.getUniqueId(), System.currentTimeMillis());
                player.setNoDamageTicks(200);
                player.setFallDistance(0);
                plugin.getLogger().info("[Teleport] Success");
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
        // Bottom
        for (int dx = 0; dx <= 1; dx++) {
            world.getBlockAt(x + dx, y, z).setType(Material.OBSIDIAN);
        }
        // Sides
        for (int dy = 0; dy <= 4; dy++) {
            world.getBlockAt(x - 1, y + dy, z).setType(Material.OBSIDIAN);
            world.getBlockAt(x + 2, y + dy, z).setType(Material.OBSIDIAN);
        }
        // Top
        for (int dx = 0; dx <= 1; dx++) {
            world.getBlockAt(x + dx, y + 4, z).setType(Material.OBSIDIAN);
        }
        // Portal blocks with correct axis
        for (int dx = 0; dx <= 1; dx++) {
            for (int dy = 1; dy <= 3; dy++) {
                Block block =
