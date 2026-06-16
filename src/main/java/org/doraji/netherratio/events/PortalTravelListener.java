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
import org.bukkit.event.entity.EntityInsideBlockEvent;

public class PortalTravelListener implements Listener {

    private final NetherRatio plugin;
    private final ConfigManager cm;

    public PortalTravelListener(NetherRatio plugin) {
        this.plugin = plugin;
        this.cm = plugin.getConfigManager();
        plugin.getLogger().info("[NetherRatio] PortalTravelListener registered - Folia EntityInsideBlock mode");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityInsideBlock(EntityInsideBlockEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getBlock().getType() != Material.NETHER_PORTAL) return;

        plugin.getLogger().info("[NetherRatio DEBUG] Player " + player.getName() + " inside Nether Portal block!");

        Location from = player.getLocation();
        Location newTo = calculatePortalDestination(from);

        if (newTo != null) {
            plugin.getLogger().info("[NetherRatio DEBUG] Teleporting to custom coords: " + formatLoc(newTo));
            player.teleportAsync(newTo).thenAccept(success -> {
                if (success) {
                    plugin.getLogger().info("[NetherRatio DEBUG] ✅ Teleport successful");
                } else {
                    plugin.getLogger().warning("[NetherRatio DEBUG] ❌ Teleport failed");
                }
            });
        } else {
            plugin.getLogger().warning("[NetherRatio DEBUG] Failed to calculate destination");
        }
    }

    private String formatLoc(Location loc) {
        if (loc == null || loc.getWorld() == null) return "null";
        return loc.getWorld().getName() + " (" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")";
    }

    private Location calculatePortalDestination(Location from) {
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

        // Apply coordinate bounds
        if (cm.areBoundsEnabled() && !cm.areCoordinatesWithinBounds(newX, newZ)) {
            double[] clamped = cm.clampCoordinates(newX, newZ);
            newX = clamped[0];
            newZ = clamped[1];
        }

        return new Location(toWorld, newX, from.getY(), newZ, from.getYaw(), from.getPitch());
    }
}
