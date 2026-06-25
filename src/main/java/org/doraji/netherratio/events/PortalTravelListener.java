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
        plugin.getLogger().info("[NetherRatio Test] MAXIMUM DEBUG - Cancel + Custom Portal Test Loaded");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPortalCreate(PortalCreateEvent event) {
        plugin.getLogger().info("========================================");
        plugin.getLogger().info("[PortalCreate] EVENT FIRED");
        plugin.getLogger().info("[PortalCreate] Thread: " + Thread.currentThread().getName());
        plugin.getLogger().info("[PortalCreate] Reason: " + event.getReason());
        plugin.getLogger().info("[PortalCreate] World: " + event.getWorld().getName());
        plugin.getLogger().info("[PortalCreate] Number of blocks affected: " + event.getBlocks().size());

        if (event.getReason() != PortalCreateEvent.CreateReason.NETHER_PAIR) {
            plugin.getLogger().info("[PortalCreate] Not NETHER_PAIR reason - ignoring");
            plugin.getLogger().info("========================================");
            return;
        }

        // Cancel the normal creation
        event.setCancelled(true);
        plugin.getLogger().info("[PortalCreate] >>> EVENT CANCELLED <<<");

        // Try to find the player who triggered this
        Player player = null;
        for (Player p : event.getWorld().getPlayers()) {
            if (p.getLocation().getBlock().getType() == Material.NETHER_PORTAL) {
                player = p;
                break;
            }
        }

        if (player == null) {
            plugin.getLogger().warning("[PortalCreate] Could not find player in portal block");
            plugin.getLogger().info("========================================");
            return;
        }

        plugin.getLogger().info("[PortalCreate] Triggering player: " + player.getName());
        plugin.getLogger().info("[PortalCreate] Player location: " + formatLoc(player.getLocation()));

        // Calculate custom destination
        Location from = player.getLocation();
        Location customDest = calculateCustomDestination(from);

        if (customDest == null) {
            plugin.getLogger().warning("[PortalCreate] Failed to calculate custom destination");
            plugin.getLogger().info("========================================");
            return;
        }

        plugin.getLogger().info("[PortalCreate] Calculated custom destination: " + formatLoc(customDest));

        // Find safe landing spot
        Location safeDest = findSafeLocation(customDest);
        plugin.getLogger().info("[PortalCreate] Safe destination chosen: " + formatLoc(safeDest));

        // Create custom portal
        plugin.getLogger().info("[PortalCreate] Creating custom portal...");
        createBasicPortal(safeDest.getWorld(), safeDest.getBlockX(), safeDest.getBlockY(), safeDest.getBlockZ());
        plugin.getLogger().info("[PortalCreate] Custom portal creation complete");

        plugin.getLogger().info("[PortalCreate] >>> TEST COMPLETE - Observing where player appears <<<");
        plugin.getLogger().info("========================================");
    }

    private Location calculateCustomDestination(Location from) {
        World fromWorld = from.getWorld();
        if (fromWorld == null) {
            plugin.getLogger().warning("[Calc] fromWorld is null");
            return null;
        }

        plugin.getLogger().info("[Calc] From world environment: " + fromWorld.getEnvironment());

        World toWorld;
        double newX, newZ;
        double scale;

        if (fromWorld.getEnvironment() == World.Environment.NORMAL) {
            toWorld = cm.getLinkedNetherWorld(fromWorld.getName());
            if (toWorld == null) {
                plugin.getLogger().warning("[Calc] No linked nether world found");
                return null;
            }
            scale = cm.getRatioForWorld(fromWorld.getName());
            plugin.getLogger().info("[Calc] Using ratio (Overworld→Nether): " + scale);
            newX = CoordinateMath.toNether(from.getX(), scale, cm.getOffsetXForWorld(fromWorld.getName()));
            newZ = CoordinateMath.toNether(from.getZ(), scale, cm.getOffsetZForWorld(fromWorld.getName()));
        } else {
            toWorld = cm.getLinkedOverworld(fromWorld.getName());
            if (toWorld == null) {
                plugin.getLogger().warning("[Calc] No linked overworld found");
                return null;
            }
            scale = cm.getRatioForNetherWorld(fromWorld.getName());
            plugin.getLogger().info("[Calc] Using ratio (Nether→Overworld): " + scale);
            newX = CoordinateMath.toOverworld(from.getX(), scale, cm.getOffsetXForNetherWorld(fromWorld.getName()));
            newZ = CoordinateMath.toOverworld(from.getZ(), scale, cm.getOffsetZForNetherWorld(fromWorld.getName()));
        }

        return new Location(toWorld, newX, from.getY(), newZ, from.getYaw(), from.getPitch());
    }

    private Location findSafeLocation(Location target) {
        World world = target.getWorld();
        if (world == null) return target;

        int x = target.getBlockX();
        int z = target.getBlockZ();

        for (int y = Math.min(target.getBlockY() + 12, 120); y >= Math.max(target.getBlockY() - 12, 30); y--) {
            if (isSafeSpot(world, x, y, z)) {
                return new Location(world, x + 0.5, y, z + 0.5, target.getYaw(), target.getPitch());
            }
        }
        plugin.getLogger().info("[SafeY] Could not find perfect safe spot, using original Y");
        return target;
    }

    private boolean isSafeSpot(World world, int x, int y, int z) {
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        Block below = world.getBlockAt(x, y - 1, z);
        return feet.getType().isAir() && head.getType().isAir() && below.getType().isSolid();
    }

    private void createBasicPortal(World world, int x, int y, int z) {
        plugin.getLogger().info("[CreatePortal] Creating portal at " + x + "," + y + "," + z);

        // Obsidian frame
        for (int dx = -1; dx <= 2; dx++) {
            for (int dy = 0; dy <= 4; dy++) {
                Block block = world.getBlockAt(x + dx, y + dy, z);
                if ((dy == 0 || dy == 4 || dx == -1 || dx == 2) && block.getType().isAir()) {
                    block.setType(Material.OBSIDIAN);
                }
            }
        }

        // Portal blocks
        for (int dy = 1; dy <= 3; dy++) {
            for (int dx = 0; dx <= 1; dx++) {
                world.getBlockAt(x + dx, y + dy, z).setType(Material.NETHER_PORTAL);
            }
        }

        plugin.getLogger().info("[CreatePortal] Portal creation finished");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() != null && event.getTo().getBlock().getType() == Material.NETHER_PORTAL) {
            long now = System.currentTimeMillis();
            if (now - lastMoveLog > 400) {
                plugin.getLogger().info("[Move] " + event.getPlayer().getName() + 
                    " standing in portal at " + formatLoc(event.getTo()));
                lastMoveLog = now;
            }
        }
    }

    private String formatLoc(Location loc) {
        if (loc == null || loc.getWorld() == null) return "null";
        return loc.getWorld().getName() + " (" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + ")";
    }
}
