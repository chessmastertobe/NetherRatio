package org.doraji.netherratio.events;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.doraji.netherratio.NetherRatio;
import org.doraji.netherratio.ConfigManager;
import org.doraji.netherratio.util.CoordinateMath;

public class PortalTravelListener implements Listener {

    private final NetherRatio plugin;
    private final ConfigManager cm;
    private long lastMoveLog = 0;

    public PortalTravelListener(NetherRatio plugin) {
        this.plugin = plugin;
        this.cm = plugin.getConfigManager();
        plugin.getLogger().info("[NetherRatio Test] Cancel + Custom Portal Creation Test Loaded");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPortalCreate(PortalCreateEvent event) {
        if (event.getReason() != PortalCreateEvent.CreateReason.NETHER_PAIR) return;

        plugin.getLogger().info("========================================");
        plugin.getLogger().info("[PortalCreate] EVENT FIRED - ATTEMPTING INTERVENTION");
        plugin.getLogger().info("[PortalCreate] Thread: " + Thread.currentThread().getName());
        plugin.getLogger().info("[PortalCreate] World: " + event.getWorld().getName());

        // Cancel the normal creation
        event.setCancelled(true);
        plugin.getLogger().info("[PortalCreate] >>> EVENT CANCELLED <<<");

        // Find a player who is currently in a portal (crude but works for testing)
        Player triggeringPlayer = null;
        for (Player p : event.getWorld().getPlayers()) {
            if (p.getLocation().getBlock().getType() == Material.NETHER_PORTAL) {
                triggeringPlayer = p;
                break;
            }
        }

        if (triggeringPlayer == null) {
            plugin.getLogger().warning("[PortalCreate] Could not find triggering player");
            return;
        }

        plugin.getLogger().info("[PortalCreate] Triggering player: " + triggeringPlayer.getName());

        // Calculate custom destination using 2:1 ratio
        Location from = triggeringPlayer.getLocation();
        Location customDest = calculateCustomDestination(from);

        if (customDest == null) {
            plugin.getLogger().warning("[PortalCreate] Could not calculate custom destination");
            return;
        }

        // Find safe Y
        Location safeDest = findSafeLocation(customDest);
        plugin.getLogger().info("[PortalCreate] Creating custom portal at: " + formatLoc(safeDest));

        // Create basic portal at custom location
        createBasicPortal(safeDest.getWorld(), safeDest.getBlockX(), safeDest.getBlockY(), safeDest.getBlockZ());

        plugin.getLogger().info("[PortalCreate] Custom portal created. Waiting to see where player appears...");
        plugin.getLogger().info("========================================");
    }

    private Location calculateCustomDestination(Location from) {
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
        } else {
            toWorld = cm.getLinkedOverworld(fromWorld.getName());
            if (toWorld == null) return null;
            scale = cm.getRatioForNetherWorld(fromWorld.getName());
            newX = CoordinateMath.toOverworld(from.getX(), scale, cm.getOffsetXForNetherWorld(fromWorld.getName()));
            newZ = CoordinateMath.toOverworld(from.getZ(), scale, cm.getOffsetZForNetherWorld(fromWorld.getName()));
        }

        return new Location(toWorld, newX, from.getY(), newZ);
    }

    private Location findSafeLocation(Location target) {
        World world = target.getWorld();
        if (world == null) return target;

        int x = target.getBlockX();
        int z = target.getBlockZ();

        for (int y = Math.min(target.getBlockY() + 10, 120); y >= Math.max(target.getBlockY() - 10, 30); y--) {
            if (isSafeSpot(world, x, y, z)) {
                return new Location(world, x + 0.5, y, z + 0.5);
            }
        }
        return target;
    }

    private boolean isSafeSpot(World world, int x, int y, int z) {
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        Block below = world.getBlockAt(x, y - 1, z);
        return feet.getType().isAir() && head.getType().isAir() && below.getType().isSolid();
    }

    private void createBasicPortal(World world, int x, int y, int z) {
        // Simple obsidian frame + portal blocks
        for (int dx = -1; dx <= 2; dx++) {
            for (int dy = 0; dy <= 4; dy++) {
                Block block = world.getBlockAt(x + dx, y + dy, z);
                if (dy == 0 || dy == 4 || dx == -1 || dx == 2) {
                    if (block.getType().isAir()) {
                        block.setType(Material.OBSIDIAN);
                    }
                }
            }
        }
        for (int dy = 1; dy <= 3; dy++) {
            for (int dx = 0; dx <= 1; dx++) {
                world.getBlockAt(x + dx, y + dy, z).setType(Material.NETHER_PORTAL);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        // Basic move logging near portals (for context)
        if (event.getTo() != null && event.getTo().getBlock().getType() == Material.NETHER_PORTAL) {
            long now = System.currentTimeMillis();
            if (now - lastMoveLog > 400) {
                plugin.getLogger().info("[Move] " + event.getPlayer().getName() + " in portal at " + formatLoc(event.getTo()));
                lastMoveLog = now;
            }
        }
    }

    private String formatLoc(Location loc) {
        if (loc == null || loc.getWorld() == null) return "null";
        return loc.getWorld().getName() + " (" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + ")";
    }
}
