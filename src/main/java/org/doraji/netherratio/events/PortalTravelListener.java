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

public class PortalTravelListener implements Listener {

    private final NetherRatio plugin;
    private final ConfigManager cm;

    public PortalTravelListener(NetherRatio plugin) {
        this.plugin = plugin;
        this.cm = plugin.getConfigManager();
        plugin.getLogger().info("§a[NetherRatio TRACE] Listener loaded - Pure tracing mode");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        if (to == null) return;

        if (to.getBlockX() == event.getFrom().getBlockX() &&
            to.getBlockY() == event.getFrom().getBlockY() &&
            to.getBlockZ() == event.getFrom().getBlockZ()) return;

        if (to.getBlock().getType() != Material.NETHER_PORTAL) return;

        plugin.getLogger().info("§e[NetherRatio TRACE] === PORTAL DETECTED for " + player.getName() + " ===");

        // Trace the ratio lookup
        World fromWorld = to.getWorld();
        plugin.getLogger().info("§e[NetherRatio TRACE] From world: " + fromWorld.getName() + " | Environment: " + fromWorld.getEnvironment());

        double ratio = (fromWorld.getEnvironment() == World.Environment.NORMAL) 
            ? cm.getRatioForWorld(fromWorld.getName()) 
            : cm.getRatioForNetherWorld(fromWorld.getName());

        plugin.getLogger().info("§e[NetherRatio TRACE] Ratio retrieved from ConfigManager: " + ratio);

        Location calculated = calculateDestination(to);
        plugin.getLogger().info("§e[NetherRatio TRACE] Calculated destination: " + formatLoc(calculated));
    }

    private Location calculateDestination(Location from) {
        World fromWorld = from.getWorld();
        if (fromWorld == null) return null;

        World toWorld = null;
        double newX = from.getX();
        double newZ = from.getZ();
        double scale = 8.0; // default

        if (fromWorld.getEnvironment() == World.Environment.NORMAL) {
            toWorld = cm.getLinkedNetherWorld(fromWorld.getName());
            scale = cm.getRatioForWorld(fromWorld.getName());
            newX = CoordinateMath.toNether(from.getX(), scale, cm.getOffsetXForWorld(fromWorld.getName()));
            newZ = CoordinateMath.toNether(from.getZ(), scale, cm.getOffsetZForWorld(fromWorld.getName()));
        } else if (fromWorld.getEnvironment() == World.Environment.NETHER) {
            toWorld = cm.getLinkedOverworld(fromWorld.getName());
            scale = cm.getRatioForNetherWorld(fromWorld.getName());
            newX = CoordinateMath.toOverworld(from.getX(), scale, cm.getOffsetXForNetherWorld(fromWorld.getName()));
            newZ = CoordinateMath.toOverworld(from.getZ(), scale, cm.getOffsetZForNetherWorld(fromWorld.getName()));
        }

        plugin.getLogger().info("§e[NetherRatio TRACE] Final ratio used: " + scale + " | New coords: " + newX + ", " + newZ);

        return new Location(toWorld, newX, 80, newZ);
    }

    private String formatLoc(Location loc) {
        if (loc == null || loc.getWorld() == null) return "null";
        return loc.getWorld().getName() + " (" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")";
    }
}
