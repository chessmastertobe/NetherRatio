package org.doraji.netherratio.events;

import org.doraji.netherratio.NetherRatio;
import org.doraji.netherratio.ConfigManager;
import org.doraji.netherratio.util.CoordinateMath;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class PortalTravelListener implements Listener {

    private final NetherRatio plugin;
    private final ConfigManager cm;

    public PortalTravelListener(NetherRatio plugin) {
        this.plugin = plugin;
        this.cm = plugin.getConfigManager();
        plugin.getLogger().info("[NetherRatio] PortalTravelListener registered - DEBUG MODE ENABLED");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();

        plugin.getLogger().info("[NetherRatio DEBUG] === PlayerPortalEvent START for " + player.getName() + " ===");
        plugin.getLogger().info("[NetherRatio DEBUG] Cause: " + event.getCause() + " | From: " + formatLoc(from));

        if (event.getCause() != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            plugin.getLogger().info("[NetherRatio DEBUG] Ignored - not NETHER_PORTAL cause");
            return;
        }

        Location newTo = calculatePortalDestination(from);
        if (newTo != null) {
            plugin.getLogger().info("[NetherRatio DEBUG] Setting custom destination: " + formatLoc(newTo));
            event.setTo(newTo);
            event.setCancelled(false);
            plugin.getLogger().info("[NetherRatio DEBUG] setTo() + setCancelled(false) applied");
        } else {
            plugin.getLogger().warning("[NetherRatio DEBUG] Failed to calculate destination!");
        }
        plugin.getLogger().info("[NetherRatio DEBUG] === PlayerPortalEvent END ===");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        plugin.getLogger().info("[NetherRatio DEBUG] EntityPortalEvent for " + event.getEntity().getType());
        Location newTo = calculatePortalDestination(event.getFrom());
        if (newTo != null) {
            event.setTo(newTo);
            event.setCancelled(false);
        }
    }

    private String formatLoc(Location loc) {
        if (loc == null || loc.getWorld() == null) return "null";
        return loc.getWorld().getName() + " (" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")";
    }

    private Location calculatePortalDestination(Location from) {
        World fromWorld = from.getWorld();
        if (fromWorld == null) {
            plugin.getLogger().warning("[NetherRatio DEBUG] fromWorld is null");
            return null;
        }

        World toWorld;
        double newX, newZ;
        double scale;

        if (fromWorld.getEnvironment() == World.Environment.NORMAL) {
            toWorld = cm.getLinkedNetherWorld(fromWorld.getName());
            if (toWorld == null) {
                plugin.getLogger().warning("[NetherRatio DEBUG] No linked Nether world for " + fromWorld.getName());
                return null;
            }
            scale = cm.getRatioForWorld(fromWorld.getName());
            newX = CoordinateMath.toNether(from.getX(), scale, cm.getOffsetXForWorld(fromWorld.getName()));
            newZ = CoordinateMath.toNether(from.getZ(), scale, cm.getOffsetZForWorld(fromWorld.getName()));
            plugin.getLogger().info("[NetherRatio DEBUG] OW→Nether ratio=" + scale + " newX=" + newX + " newZ=" + newZ);
        } else if (fromWorld.getEnvironment() == World.Environment.NETHER) {
            toWorld = cm.getLinkedOverworld(fromWorld.getName());
            if (toWorld == null) {
                plugin.getLogger().warning("[NetherRatio DEBUG] No linked Overworld for " + fromWorld.getName());
                return null;
            }
            scale = cm.getRatioForNetherWorld(fromWorld.getName());
            newX = CoordinateMath.toOverworld(from.getX(), scale, cm.getOffsetXForNetherWorld(fromWorld.getName()));
            newZ = CoordinateMath.toOverworld(from.getZ(), scale, cm.getOffsetZForNetherWorld(fromWorld.getName()));
            plugin.getLogger().info("[NetherRatio DEBUG] Nether→OW ratio=" + scale + " newX=" + newX + " newZ=" + newZ);
        } else {
            return null;
        }

        if (cm.areBoundsEnabled() && !cm.areCoordinatesWithinBounds(newX, newZ)) {
            double[] clamped = cm.clampCoordinates(newX, newZ);
            newX = clamped[0];
            newZ = clamped[1];
            plugin.getLogger().info("[NetherRatio DEBUG] Clamped to " + newX + ", " + newZ);
        }

        return new Location(toWorld, newX, from.getY(), newZ, from.getYaw(), from.getPitch());
    }
}
