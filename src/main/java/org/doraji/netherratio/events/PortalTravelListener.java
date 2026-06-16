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
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            return;
        }

        Location newTo = calculatePortalDestination(event.getFrom());
        if (newTo != null) {
            event.setCancelled(true);
            event.setTo(newTo);   // Let the event handle it instead of manual teleport
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        Location newTo = calculatePortalDestination(event.getFrom());
        if (newTo != null) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> {
                event.getEntity().teleport(newTo);
            });
        }
    }

    /**
     * Calculates the portal destination with custom ratio applied.
     */
    private Location calculatePortalDestination(Location from) {
        World fromWorld = from.getWorld();
        if (fromWorld == null) {
            plugin.getLogger().warning("Cannot calculate portal destination: source world is null");
            return null;
        }

        World toWorld;
        double newX;
        double newZ;
        double scale;

        if (fromWorld.getEnvironment() == World.Environment.NORMAL) {
            // Overworld → Nether
            toWorld = cm.getLinkedNetherWorld(fromWorld.getName());
            if (toWorld == null) {
                plugin.getLogger().warning(
                    plugin.getMessagesManager().getMessage("config.world-not-found-overworld", "world", fromWorld.getName())
                );
                return null;
            }
            scale = cm.getRatioForWorld(fromWorld.getName());
            newX = CoordinateMath.toNether(from.getX(), scale, cm.getOffsetXForWorld(fromWorld.getName()));
            newZ = CoordinateMath.toNether(from.getZ(), scale, cm.getOffsetZForWorld(fromWorld.getName()));
        } else if (fromWorld.getEnvironment() == World.Environment.NETHER) {
            // Nether → Overworld
            toWorld = cm.getLinkedOverworld(fromWorld.getName());
            if (toWorld == null) {
                plugin.getLogger().warning(
                    plugin.getMessagesManager().getMessage("config.world-not-found-nether", "world", fromWorld.getName())
                );
                return null;
            }
            scale = cm.getRatioForNetherWorld(fromWorld.getName());
            newX = CoordinateMath.toOverworld(from.getX(), scale, cm.getOffsetXForNetherWorld(fromWorld.getName()));
            newZ = CoordinateMath.toOverworld(from.getZ(), scale, cm.getOffsetZForNetherWorld(fromWorld.getName()));
        } else {
            return null; // End or other dimensions
        }

        // Apply coordinate bounds if enabled
        if (cm.areBoundsEnabled() && !cm.areCoordinatesWithinBounds(newX, newZ)) {
            double[] clamped = cm.clampCoordinates(newX, newZ);
            newX = clamped[0];
            newZ = clamped[1];
            
            plugin.getLogger().info(String.format(
                "Clamped portal destination from (%.2f, %.2f) to (%.2f, %.2f) in %s",
                newX, newZ, clamped[0], clamped[1], toWorld.getName()
            ));
        }

        return new Location(toWorld, newX, from.getY(), newZ, from.getYaw(), from.getPitch());
    }
}
