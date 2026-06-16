package org.doraji.netherratio.events;

import org.doraji.netherratio.NetherRatio;
import org.doraji.netherratio.ConfigManager;
import org.doraji.netherratio.util.CoordinateMath;
import org.bukkit.Bukkit;
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

        plugin.getLogger().info("[NetherRatio DEBUG] === PlayerPortalEvent START ===");
        plugin.getLogger().info("[NetherRatio DEBUG] Player: " + player.getName() + " | Cause: " + event.getCause());
        plugin.getLogger().info("[NetherRatio DEBUG] From: " + formatLoc(from));

        if (event.getCause() != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            plugin.getLogger().info("[NetherRatio DEBUG] Ignored - Not a NETHER_PORTAL");
            return;
        }

        Location newTo = calculatePortalDestination(from);
        if (newTo != null) {
            plugin.getLogger().info("[NetherRatio DEBUG] Custom destination calculated: " + formatLoc(newTo));

            event.setTo(newTo);
            event.setCancelled(false);

            plugin.getLogger().info("[NetherRatio DEBUG] setTo() + setCancelled(false) applied - hoping Folia respects it");
        } else {
            plugin.getLogger().warning("[NetherRatio DEBUG] calculatePortalDestination returned null!");
        }

        plugin.getLogger().info("[NetherRatio DEBUG] === PlayerPortalEvent END ===");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        plugin.getLogger().info("[NetherRatio DEBUG] EntityPortalEvent for " + event.getEntity().getType() 
                + " | From: " + formatLoc(event.getFrom()));

        Location newTo = calculatePortalDestination(event.getFrom());
        if (newTo != null) {
            event.setTo(newTo);
            event.setCancelled(false);
            plugin.getLogger().info("[NetherRatio DEBUG] Entity destination set to: " + formatLoc(newTo));
        }
    }

    private String formatLoc(Location loc) {
        if (loc == null) return "null";
        return String.format("%s (%.2f, %.2f, %.2f)", 
                loc.getWorld() != null ? loc.getWorld().getName() : "null", 
                loc.getX(), loc.getY(), loc.getZ());
    }

    private Location calculatePortalDestination(Location from) {
        World fromWorld = from.getWorld();
        if (fromWorld == null) {
            plugin.getLogger().warning("[NetherRatio DEBUG] Source world is null");
            return null;
        }

        World toWorld;
        double newX, newZ;
        double scale;

        if (fromWorld.getEnvironment() == World.Environment.NORMAL) {
            // Overworld → Nether
            toWorld = cm.getLinkedNetherWorld(fromWorld.getName());
            if (toWorld == null) {
                plugin.getLogger().warning("[NetherRatio DEBUG] No linked Nether world found for " + fromWorld.getName());
                return null;
            }
            scale = cm.getRatioForWorld(fromWorld.getName());
            newX = CoordinateMath.toNether(from.getX(), scale, cm.getOffsetXForWorld(fromWorld.getName()));
            newZ = CoordinateMath.toNether(from.getZ(), scale, cm.getOffsetZForWorld(fromWorld.getName()));
            plugin.getLogger().info("[NetherRatio DEBUG] OW→Nether | Ratio: " + scale + " | New coords: " + newX + ", " + newZ);
        } else if (fromWorld.getEnvironment() == World.Environment.NETHER) {
            // Nether → Overworld
            toWorld = cm.getLinkedOverworld(fromWorld.getName());
            if (toWorld == null) {
                plugin.getLogger().warning("[NetherRatio DEBUG] No linked Overworld found for " + fromWorld.getName());
                return null;
            }
            scale = cm.getRatioForNetherWorld(fromWorld.getName());
            newX = CoordinateMath.toOverworld(from.getX(), scale, cm.getOffsetXForNetherWorld(fromWorld.getName()));
            newZ = CoordinateMath.toOverworld(from.getZ(), scale, cm.getOffsetZForNetherWorld(fromWorld.getName()));
            plugin.getLogger().info("[NetherRatio DEBUG] Nether→OW | Ratio: " + scale + " | New coords: " + newX + ", " + newZ);
        } else {
            plugin.getLogger().info("[NetherRatio DEBUG] Not Nether/Overworld dimension");
            return null;
        }

        // Coordinate bounds
        if (cm.areBoundsEnabled() && !cm.areCoordinatesWithinBounds(newX, newZ)) {
            double[] clamped = cm.clampCoordinates(newX, newZ);
            newX = clamped[0];
            newZ = clamped[1];
            plugin.getLogger().info("[NetherRatio DEBUG] Clamped coordinates to: " + newX + ", " + newZ);
        }

        return new Location(toWorld, newX, from.getY(), newZ, from.getYaw(), from.getPitch());
    }
}
