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

    public PortalTravelListener(NetherRatio plugin) {
        this.plugin = plugin;
        this.cm = plugin.getConfigManager();
        plugin.getLogger().info("[NetherRatio] Strict Folia + Strong Anti-Loop (Full File)");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Strong anti-loop protection (4 seconds)
        long now = System.currentTimeMillis();
        if (lastPortalUse.containsKey(uuid)) {
            if (now - lastPortalUse.get(uuid) < 4000) {
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

        Bukkit.getRegionScheduler().execute(plugin, dest.getWorld(), 
            dest.getBlockX() >> 4, dest.getBlockZ() >> 4, () -> {

            Location existing = findNearestPortal(dest, 40);
            if (existing != null) {
                Location spawn = existing.clone().add(0.5, 0.85, 0.5);
                doTeleport(player, spawn);
                return;
            }

            attemptSafeLocation(dest.getWorld(), dest.getBlockX(), dest.getBlockZ(), 50, safeLoc -> {
                Location finalSpawn;

                if (safeLoc != null) {
                    createBasicPortal(safeLoc.getWorld(), safeLoc.getBlockX(), safeLoc.getBlockY(), safeLoc.getBlockZ());
                    finalSpawn = safeLoc.clone().add(0.5, 0.85, 0.5);
                } else {
                    finalSpawn = dest.clone().add(0.5, 40, 0.5);
                    createBasicPortal(dest.getWorld(), dest.getBlockX(), (int)finalSpawn.getY(), dest.getBlockZ());
                }

                doTeleport(player, finalSpawn);
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

    private Location findNearestPortal(Location center, int radius) {
        World world = center.getWorld();
        if (world == null) return null;

        for (int x = center.getBlockX() - radius; x <= center.getBlockX() + radius; x++) {
            for (int z = center.getBlockZ() - radius; z <= center.getBlockZ() + radius; z++) {
                for (int y = center.getBlockY() - 15; y <= center.getBlockY() + 35; y++) {
                    if (world.getBlockAt(x, y, z).getType() == Material.NETHER_PORTAL) {
                        return new Location(world, x, y, z);
                    }
                }
            }
        }
        return null;
    }

    private void createBasicPortal(World world, int x, int y, int z) {
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
}
