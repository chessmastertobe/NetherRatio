package org.doraji.netherratio.events;

import org.doraji.netherratio.NetherRatio;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class PortalTravelListener implements Listener {

    private final NetherRatio plugin;

    public PortalTravelListener(NetherRatio plugin) {
        this.plugin = plugin;
        plugin.getLogger().info("§a[NetherRatio] DEBUG LISTENER REGISTERED SUCCESSFULLY!");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            plugin.getLogger().info("§e[NetherRatio DEBUG] NETHER PORTAL EVENT FIRED for " + event.getPlayer().getName());
            // For now, just log - don't change anything
        }
    }
}
