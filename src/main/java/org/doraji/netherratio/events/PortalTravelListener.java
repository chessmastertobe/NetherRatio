package org.doraji.netherratio.events;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.world.PortalCreateEvent;
import org.doraji.netherratio.NetherRatio;

public class PortalTravelListener implements Listener {

    private final NetherRatio plugin;
    private long lastMoveLog = 0;

    public PortalTravelListener(NetherRatio plugin) {
        this.plugin = plugin;
        plugin.getLogger().info("[NetherRatio Diagnostic] Final comprehensive diagnostic loaded");
    }

    // === CORE TELEPORT EVENTS ===
    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent event) {
        logEvent("TELEPORT", event.getCause().toString(), event.getPlayer(), event.getFrom(), event.getTo(), event.isCancelled());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerPortal(PlayerPortalEvent event) {
        logEvent("PLAYER_PORTAL", event.getCause().toString(), event.getPlayer(), event.getFrom(), event.getTo(), event.isCancelled());
    }

    // === WORLD CHANGE ===
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        plugin.getLogger().info("[Diag] CHANGED_WORLD | Thread: " + Thread.currentThread().getName() +
            " | Player: " + event.getPlayer().getName() +
            " | From: " + event.getFrom().getName() +
            " | To: " + event.getPlayer().getWorld().getName());
    }

    // === MOVE EVENT WITH WORLD CHANGE DETECTION ===
    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();

        if (to == null) return;

        boolean inPortal = to.getBlock().getType() == Material.NETHER_PORTAL || 
                          from.getBlock().getType() == Material.NETHER_PORTAL;

        // Detect world change via move event
        if (from.getWorld() != to.getWorld()) {
            plugin.getLogger().info("[Diag] WORLD_CHANGE_VIA_MOVE | Thread: " + Thread.currentThread().getName() +
                " | Player: " + player.getName() +
                " | FromWorld: " + from.getWorld().getName() +
                " | ToWorld: " + to.getWorld().getName() +
                " | Loc: " + formatLoc(to));
        }

        if (inPortal) {
            long now = System.currentTimeMillis();
            if (now - lastMoveLog > 200) {
                plugin.getLogger().info("[Diag] MOVE_PORTAL | Thread: " + Thread.currentThread().getName() +
                    " | Player: " + player.getName() +
                    " | Block: " + to.getBlock().getType() +
                    " | Loc: " + formatLoc(to));
                lastMoveLog = now;
            }
        }
    }

    // === OTHER PORTAL-RELATED EVENTS ===
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityPortal(EntityPortalEvent event) {
        plugin.getLogger().info("[Diag] ENTITY_PORTAL | Thread: " + Thread.currentThread().getName() +
            " | Entity: " + event.getEntity().getType());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPortalCreate(PortalCreateEvent event) {
        plugin.getLogger().info("[Diag] PORTAL_CREATE | Thread: " + Thread.currentThread().getName() +
            " | Reason: " + event.getReason() +
            " | World: " + event.getWorld().getName());
    }

    private void logEvent(String type, String cause, Player player, Location from, Location to, boolean cancelled) {
        plugin.getLogger().info("[Diag] " + type + " | Thread: " + Thread.currentThread().getName() +
            " | Cause: " + cause +
            " | Player: " + player.getName() +
            " | From: " + formatLoc(from) +
            " | To: " + formatLoc(to) +
            " | Cancelled: " + cancelled);
    }

    private String formatLoc(Location loc) {
        if (loc == null || loc.getWorld() == null) return "null";
        return loc.getWorld().getName() + " (" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + ")";
    }
}
