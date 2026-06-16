package org.doraji.netherratio.events;

import org.doraji.netherratio.NetherRatio;
import org.doraji.netherratio.ConfigManager;
import org.doraji.netherratio.util.CoordinateMath;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PortalTravelListener implements Listener {

    private final NetherRatio plugin;
    private final ConfigManager cm;
    private final Map<UUID, Long> lastTeleport = new HashMap<>();

    public PortalTravelListener(NetherRatio plugin) {
        this.plugin = plugin;
        this.cm = plugin.getConfigManager();
        plugin.getLogger().info("§a[NetherRatio] Final safe version loaded");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        if (to == null) return;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        if (lastTeleport.containsKey(uuid) && now - lastTeleport.get(uuid) < 10000) return; // 10s cooldown

        if (to.getBlockX() == event.getFrom().getBlockX() &&
            to.getBlockY() == event.getFrom().getBlockY() &&
            to.getBlockZ() == event.getFrom().getBlockZ()) return;

        if (to.getBlock().getType() != Material.NETHER_PORTAL) return;

        plugin.getLogger().info("§e[NetherRatio] Portal detected for " + player.getName());

        Location newTo = calculateSafeDestination(to);
        if (newTo != null) {
            plugin.getLogger().info("§a[NetherRatio] Teleporting to " + formatLoc(newTo));

            lastTeleport.put(uuid, now);

            plugin.getServer().getRegionScheduler().execute(plugin, newTo, () -> {
                player.teleportAsync(newTo).thenAccept(success -> {
                    if (success) plugin.getLogger().info("§a[NetherRatio] ✅ Teleport completed");
                });
            });
        }
    }

    private Location calculateSafeDestination(Location from) {
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
        } else if (fromWorld.getEnvironment() == World.Environment.NETHER) {
            toWorld = cm.getLinkedOverworld(fromWorld.getName());
            if (toWorld == null) return null;
            scale = cm.getRatioForNetherWorld(fromWorld.getName());
            newX = CoordinateMath.toOverworld(from.getX(), scale, cm.getOffsetXForNetherWorld(fromWorld.getName()));
            newZ = CoordinateMath.toOverworld(from.getZ(), scale, cm.getOffsetZForNetherWorld(fromWorld.getName()));
        } else {
            return null;
        }

        // Clamp to bounds if enabled
        if (cm.areBoundsEnabled() && !cm.areCoordinatesWithinBounds(newX, newZ)) {
            double[] clamped = cm.clampCoordinates(newX, newZ);
            newX = clamped[0];
            newZ = clamped[1];
        }

        // Start very high
        Location base = new Location(toWorld, newX + 0.5, 110, newZ + 0.5, from.getYaw(), from.getPitch());
        return findSafeY(base);
    }

    private Location findSafeY(Location loc) {
        World world = loc.getWorld();
        int x = loc.getBlockX();
        int z = loc.getBlockZ();

        for (int y = 110; y >= 40; y--) {
            Block feet = world.getBlockAt(x, y, z);
            Block head = world.getBlockAt(x, y + 1, z);
            if (feet.getType().isAir() && head.getType().isAir()) {
                return new Location(world, x + 0.5, y + 0.2, z + 0.5, loc.getYaw(), loc.getPitch());
            }
        }

        // Ultimate fallback - spawn at Y=80 and hope for the best
        return new Location(world, loc.getX(), 80, loc.getZ(), loc.getYaw(), loc.getPitch());
    }

    private String formatLoc(Location loc) {
        if (loc == null || loc.getWorld() == null) return "null";
        return loc.getWorld().getName() + " (" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")";
    }
}
