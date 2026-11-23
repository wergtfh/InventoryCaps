package com.reconnaissance.factions;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class FactionListener implements Listener {
    private final ReconnaissanceFactionsPlugin plugin;
    private final FactionManager manager;
    private final Map<UUID, UUID> lastTerritory = new HashMap<>();

    FactionListener(ReconnaissanceFactionsPlugin plugin, FactionManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.updatePlayerList(event.getPlayer());
        plugin.updateFactionNameTag(event.getPlayer());
        manager.deliverPendingMace(event.getPlayer());
        announceTerritory(event.getPlayer(), event.getPlayer().getLocation());
        manager.syncTopMaces();
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
            && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        announceTerritory(event.getPlayer(), event.getTo());
    }

    private void announceTerritory(Player player, Location location) {
        Faction faction = manager.claimAt(location);
        UUID current = faction == null ? null : faction.id;
        UUID previous = lastTerritory.put(player.getUniqueId(), current);
        if (current != null && !current.equals(previous)) {
            player.sendTitle(ChatColor.GOLD + "ENTERED TERRITORY",
                ChatColor.WHITE + faction.name, 10, 45, 10);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        int before = manager.byPlayer(killer.getUniqueId()) == null ? -1 : manager.byPlayer(killer.getUniqueId()).points;
        manager.awardKill(killer, event.getEntity());
        plugin.refreshAllPlayerLists();
        Faction faction = manager.byPlayer(killer.getUniqueId());
        if (faction != null && faction.official && faction.points > before) {
            killer.sendMessage(ChatColor.GREEN + "+1 faction point. Your faction now has " + faction.points + " points.");
        }
    }


    @EventHandler
    public void onFactionMaceDeathDrop(PlayerDeathEvent event) {
        event.getDrops().removeIf(item -> {
            if (!manager.isFactionMace(item)) return false;
            Faction faction = managerFaction(manager.maceFactionId(item));
            if (faction != null) manager.returnMaceToOwner(faction, item);
            return true;
        });
    }

    @EventHandler
    public void onFactionMaceDestroyed(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Item dropped)) return;
        ItemStack stack = dropped.getItemStack();
        if (!manager.isFactionMace(stack)) return;
        Faction faction = managerFaction(manager.maceFactionId(stack));
        if (faction == null) return;
        event.setCancelled(true);
        dropped.remove();
        manager.returnMaceToOwner(faction, stack);
    }

    @EventHandler
    public void onFactionMaceDespawn(ItemDespawnEvent event) {
        ItemStack stack = event.getEntity().getItemStack();
        if (!manager.isFactionMace(stack)) return;
        Faction faction = managerFaction(manager.maceFactionId(stack));
        if (faction == null) return;
        event.setCancelled(true);
        event.getEntity().remove();
        manager.returnMaceToOwner(faction, stack);
    }

    private Faction managerFaction(UUID factionId) {
        if (factionId == null) return null;
        for (Faction faction : manager.all()) if (faction.id.equals(factionId)) return faction;
        return null;
    }

    @EventHandler
    public void onMacePickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack item = event.getItem().getItemStack();
        if (!manager.isFactionMace(item)) return;
        UUID factionId = manager.maceFactionId(item);
        Faction faction = null;
        for (Faction candidate : manager.all()) if (candidate.id.equals(factionId)) faction = candidate;
        if (faction == null || !manager.isTopThreeFaction(factionId) || !faction.members.contains(player.getUniqueId())) {
            event.setCancelled(true);
            if (faction != null && manager.isTopThreeFaction(factionId)) manager.returnMaceToOwner(faction, item);
            event.getItem().remove();
            player.sendMessage(ChatColor.RED + "That mace is not available to your faction.");
        }
    }

    @EventHandler
    public void onMaceInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        ItemStack restricted = manager.isFactionMace(current) ? current : manager.isFactionMace(cursor) ? cursor : null;
        if (restricted == null) return;
        UUID factionId = manager.maceFactionId(restricted);
        Faction faction = null;
        for (Faction candidate : manager.all()) if (candidate.id.equals(factionId)) faction = candidate;
        if (faction == null || !manager.isTopThreeFaction(factionId) || !faction.members.contains(player.getUniqueId())) {
            event.setCancelled(true);
            if (current != null && manager.isFactionMace(current)) event.setCurrentItem(null);
            if (cursor != null && manager.isFactionMace(cursor)) player.setItemOnCursor(null);
            if (faction != null && manager.isTopThreeFaction(factionId)) manager.returnMaceToOwner(faction, restricted);
            player.sendMessage(ChatColor.RED + "Only current top-three faction members can hold that mace.");
        }
    }

    @EventHandler
    public void onMaceInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack oldCursor = event.getOldCursor();
        if (!manager.isFactionMace(oldCursor)) return;
        UUID factionId = manager.maceFactionId(oldCursor);
        Faction faction = null;
        for (Faction candidate : manager.all()) if (candidate.id.equals(factionId)) faction = candidate;
        if (faction == null || !manager.isTopThreeFaction(factionId) || !faction.members.contains(player.getUniqueId())) {
            event.setCancelled(true);
            player.setItemOnCursor(null);
            if (faction != null && manager.isTopThreeFaction(factionId)) manager.returnMaceToOwner(faction, oldCursor);
            player.sendMessage(ChatColor.RED + "Only current top-three faction members can hold that mace.");
        }
    }

    @EventHandler
    public void onBorderPhysics(BlockPhysicsEvent event) {
        if (manager.isBorderBlock(event.getBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler
    public void onBorderPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> manager.isBorderBlock(block.getLocation()))) event.setCancelled(true);
    }

    @EventHandler
    public void onBorderPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> manager.isBorderBlock(block.getLocation()))) event.setCancelled(true);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Faction claim = manager.claimAt(event.getBlock().getLocation());
        if (claim == null) return;
        if (manager.isBorderBlock(event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "Faction gold borders cannot be broken.");
            return;
        }
        if (!manager.isMember(claim, event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "You cannot break blocks in " + claim.name + "'s territory.");
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (manager.isBorderBlock(event.getBlockPlaced().getLocation())) {
            event.setCancelled(true);
            return;
        }
        Faction claim = manager.claimAt(event.getBlock().getLocation());
        if (claim == null) return;
        Material type = event.getBlockPlaced().getType();
        if (isExplosive(type)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "Explosives are disabled in faction territory.");
            return;
        }
        if (!manager.isMember(claim, event.getPlayer().getUniqueId()) && type != Material.COBWEB) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "Enemies may only place cobwebs and water here.");
        }
    }

    private boolean isExplosive(Material material) {
        String name = material.name();
        return material == Material.TNT || material == Material.END_CRYSTAL ||
            material == Material.RESPAWN_ANCHOR || name.endsWith("_BED");
    }

    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Faction claim = manager.claimAt(event.getBlock().getLocation());
        if (claim == null) return;
        if (event.getBucket() == Material.LAVA_BUCKET) {
            if (!manager.isMember(claim, event.getPlayer().getUniqueId())) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(ChatColor.RED + "Enemies cannot place lava in faction territory.");
            }
            return;
        }
        if (!manager.isMember(claim, event.getPlayer().getUniqueId()) && event.getBucket() != Material.WATER_BUCKET) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "Enemies may only place water here.");
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Location location = event.getInventory().getLocation();
        if (location == null || !(location.getBlock().getState() instanceof InventoryHolder)) return;
        Faction claim = manager.claimAt(location);
        if (claim != null && !manager.isMember(claim, player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "You cannot open containers in " + claim.name + "'s territory.");
        }
    }

    @EventHandler
    public void onIgnite(BlockIgniteEvent event) {
        if (manager.claimAt(event.getBlock().getLocation()) != null) event.setCancelled(true);
    }

    @EventHandler
    public void onBurn(BlockBurnEvent event) {
        if (manager.claimAt(event.getBlock().getLocation()) != null) event.setCancelled(true);
    }

    @EventHandler
    public void onSpread(BlockSpreadEvent event) {
        if (manager.claimAt(event.getBlock().getLocation()) != null) event.setCancelled(true);
    }

    @EventHandler
    public void onEntityExplosion(EntityExplodeEvent event) {
        if (manager.claimAt(event.getLocation()) != null) {
            event.setCancelled(true);
            return;
        }
        event.blockList().removeIf(block -> manager.claimAt(block.getLocation()) != null);
    }

    @EventHandler
    public void onBlockExplosion(BlockExplodeEvent event) {
        if (manager.claimAt(event.getBlock().getLocation()) != null) {
            event.setCancelled(true);
            return;
        }
        event.blockList().removeIf(block -> manager.claimAt(block.getLocation()) != null);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        Faction claim = manager.claimAt(event.getClickedBlock().getLocation());
        if (claim == null) return;
        if (event.getItem() != null && isExplosive(event.getItem().getType())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "Explosives are disabled in faction territory.");
        }
    }
}
