package org.doraji.netherratio.events;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.doraji.netherratio.NetherRatio;

import java.util.List;

public class PortalTravelListener implements Listener {

    private final NetherRatio plugin;
    private long lastMoveLog = 0;

    public PortalTravelListener(NetherRatio plugin) {
        this.plugin = plugin;
        plugin.getLogger().info("[NetherRatio Test] PortalCreateEvent-focused test version loaded");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPortalCreate(PortalCreateEvent event) {
        plugin.getLogger().info("========================================");
        plugin.getLogger().info("[PortalCreate] EVENT FIRED");
        plugin.getLogger().info("[PortalCreate] Thread: " + Thread.currentThread().getName());
        plugin.getLogger().info("[PortalCreate] Reason: " + event.getReason());
        plugin.getLogger().info("[PortalCreate] World: " + event.getWorld().getName());

        List<BlockState> blocks = event.getBlocks();
        plugin.getLogger().info("[PortalCreate] Number of blocks: " + blocks.size());

        if (!blocks.isEmpty()) {
            BlockState first = blocks.get(0);
            plugin.getLogger().info("[PortalCreate] First block location: " + 
                first.getWorld().getName() + " (" + first.getX() + "," + first.getY() + "," + first.getZ() + ")");
        }

        // Log nearby players (to see who triggered this)
        for (Player p : event.getWorld().getPlayers()) {
            if (p.getLocation().getBlock().getType() == Material.NETHER_PORTAL) {
                plugin.getLogger().info("[PortalCreate] Player in portal: " + p.getName() + 
                    " at " + formatLoc(p.getLocation()));
            }
        }

        plugin.getLogger().info("========================================");

        // === TEST: Cancel the event to see what happens ===
        // Uncomment the next line if you want to test cancelling
        // event.setCancelled(true);
        // plugin.getLogger().info("[PortalCreate] >>> EVENT WAS CANCELLED <<<");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();

        if (to == null) return;

        if (to.getBlock().getType() == Material.NETHER_PORTAL || 
            event.getFrom().getBlock().getType() == Material.NETHER_PORTAL) {

            long now = System.currentTimeMillis();
            if (now - lastMoveLog > 300) {
                plugin.getLogger().info("[Move] Player: " + player.getName() + 
                    " | Block: " + to.getBlock().getType() + 
                    " | Loc: " + formatLoc(to));
                lastMoveLog = now;
            }
        }
    }

    private String formatLoc(Location loc) {
        if (loc == null || loc.getWorld() == null) return "null";
        return loc.getWorld().getName() + " (" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + ")";
    }
}
