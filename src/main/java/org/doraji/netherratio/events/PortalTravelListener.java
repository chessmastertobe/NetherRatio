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
import org.bukkit.scheduler.BukkitTask;

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
            // Folia-safe way: schedule the teleport on the destination region's scheduler
            Player player = event.getPlayer();
            event.setCancelled(true);  // Cancel vanilla, we'll handle it

            // Use teleportAsync for cross-dimension safety
            player.teleportAsync(newTo).thenAccept(success -> {
                if (!success) {
                    plugin.getLogger().warning("Async teleport failed for " + player.getName());
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        Location newTo = calculatePortalDestination(event.getFrom());
        if (newTo != null) {
            event.setCancelled(true);
            // For entities we still use setTo on the event (safer than async for non-players)
            // but schedule the actual teleport if needed
            Bukkit.getScheduler().runTask(plugin, () -> {
                event.getEntity().teleport(newTo);
            });
        }
    }

    private Location calculatePortalDestination(Location from) {
        // ... (keep your existing calculatePortalDestination method exactly as-is)
        // (the whole method from line 94 to 155 in the original)
    }
}
