package org.doraji.netherratio.events;

import org.doraji.netherratio.NetherRatio;
import org.doraji.netherratio.ConfigManager;
import org.doraji.netherratio.util.CoordinateMath;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
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
        plugin.getLogger().info("§a[NetherRatio] Listener active - Strong loop protection");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        if (to == null) return;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        // 8 second cooldown after any custom teleport
        if (lastTeleport.containsKey(uuid) && now - lastTeleport.get(uuid) < 8000) {
            return;
        }

        // Only check when actually moving to a new block
        if (to.getBlockX() == event.getFrom().getBlockX() &&
            to.getBlockY() == event.getFrom().getBlockY() &&
            to.getBlockZ() == event.getFrom().getBlockZ()) {
            return;
        }

        if (to.getBlock().getType() != Material.NETHER_PORTAL) return;

        plugin.getLogger().info("§e[NetherRatio] Portal detected for " + player.getName());

        Location newTo = calculateDestination(to);
        if (newTo != null) {
            plugin.getLogger().info("§a[NetherRatio] Teleporting to " + formatLoc(newTo));

            lastTeleport.put(uuid, now);

            plugin.getServer().getRegionScheduler().execute(plugin, newTo, () -> {
                player.teleportAsync(newTo).thenAccept(success -> {
                    if (success) {
                        plugin.getLogger().info("§a[NetherRatio] ✅ Teleport completed");
                    }
                });
            });
        }
    }

    private Location calculateDestination(Location from) {
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

        if (cm.areBoundsEnabled() && !cm.areCoordinatesWithinBounds(newX, newZ)) {
            double[] clamped = cm.clampCoordinates(newX, newZ);
            newX = clamped[0];
            newZ = clamped[1];
        }

        return new Location(toWorld, newX + 0.5, 70, newZ + 0.5, from.getYaw(), from.getPitch());
    }

    private String formatLoc(Location loc) {
        if (loc == null || loc.getWorld() == null) return "null";
        return loc.getWorld().getName() + " (" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")";
    }
}
