package org.doraji.netherratio.events;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.doraji.netherratio.NetherRatio;

public class PortalTravelListener implements Listener {

    private final NetherRatio plugin;
    private long lastLogTime = 0;

    public PortalTravelListener(NetherRatio plugin) {
        this.plugin = plugin;
        plugin.getLogger().info("[NetherRatio Diagnostic] Diagnostic listener loaded");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAnyTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        plugin.getLogger().info("[Diag] TELEPORT | Cause: " + event.getCause() + 
            " | Player: " + player.getName() +
            " | From: " + formatLoc(event.getFrom()) + 
            " | To: " + formatLoc(event.getTo()) +
            " | Cancelled: " + event.isCancelled());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerPortal(PlayerPortalEvent event) {
        plugin.getLogger().info("[Diag] PLAYER_PORTAL_EVENT | Cause: " + event.getCause() +
            " | Player: " + event.getPlayer().getName() +
            " | From: " + formatLoc(event.getFrom()) +
            " | To: " + formatLoc(event.getTo()) +
            " | Cancelled: " + event.isCancelled());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();

        if (to == null) return;

        // Only log when near or inside Nether portals (to avoid spam)
        if (to.getBlock().getType() == Material.NETHER_PORTAL || 
            event.getFrom().getBlock().getType() == Material.NETHER_PORTAL) {

            long now = System.currentTimeMillis();
            if (now - lastLogTime > 200) { // Throttle logging
                plugin.getLogger().info("[Diag] MOVE near portal | Player: " + player.getName() +
                    " | Block: " + to.getBlock().getType() +
                    " | Location: " + formatLoc(to));
                lastLogTime = now;
            }
        }
    }

    private String formatLoc(Location loc) {
        if (loc == null || loc.getWorld() == null) return "null";
        return loc.getWorld().getName() + " (" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + ")";
    }
}
