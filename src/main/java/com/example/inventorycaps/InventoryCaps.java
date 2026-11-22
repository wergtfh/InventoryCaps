package com.example.inventorycaps;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.block.ShulkerBox;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class InventoryCaps extends JavaPlugin implements Listener {
    private final Map<Material, Integer> limits = new EnumMap<>(Material.class);
    private boolean notifyPlayer;
    private int intervalTicks;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadLimits();
        notifyPlayer = getConfig().getBoolean("notify-player", true);
        intervalTicks = Math.max(1, getConfig().getInt("enforcement-interval-ticks", 2));
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) enforce(player);
        }, 1L, intervalTicks);
        getLogger().info("InventoryCaps enabled. Limits: " + limits);
    }

    private void loadLimits() {
        if (getConfig().getConfigurationSection("limits") == null) return;
        for (String key : getConfig().getConfigurationSection("limits").getKeys(false)) {
            Material material = Material.matchMaterial(key);
            if (material == null || !material.isItem()) {
                getLogger().warning("Ignoring invalid item in limits: " + key);
                continue;
            }
            int amount = getConfig().getInt("limits." + key, -1);
            if (amount >= 0) limits.put(material, amount);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) { schedule(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p) schedule(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p) schedule(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p) schedule(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) { schedule(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent e) { schedule(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) { schedule(e.getPlayer()); }

    private void schedule(Player p) {
        Bukkit.getScheduler().runTask(this, () -> {
            if (p.isOnline()) enforce(p);
        });
    }

    private void enforce(Player player) {
        if (limits.isEmpty()) return;
        EnumMap<Material, Integer> remaining = new EnumMap<>(limits);
        PlayerInventory inv = player.getInventory();

        ItemStack[] storage = inv.getStorageContents();
        for (int i = 0; i < storage.length; i++) storage[i] = sanitize(storage[i], remaining);
        inv.setStorageContents(storage);

        ItemStack[] armor = inv.getArmorContents();
        for (int i = 0; i < armor.length; i++) armor[i] = sanitize(armor[i], remaining);
        inv.setArmorContents(armor);

        inv.setItemInOffHand(sanitize(inv.getItemInOffHand(), remaining));
        player.updateInventory();
    }

    private ItemStack sanitize(ItemStack original, EnumMap<Material, Integer> remaining) {
        if (original == null || original.getType().isAir()) return original;
        ItemStack stack = original.clone();
        Material type = stack.getType();

        if (limits.containsKey(type)) {
            int left = remaining.getOrDefault(type, limits.get(type));
            int keep = Math.min(stack.getAmount(), Math.max(0, left));
            remaining.put(type, Math.max(0, left - keep));
            if (keep == 0) return null;
            stack.setAmount(keep);
            return stack;
        }

        if (stack.getItemMeta() instanceof BundleMeta bundle) {
            List<ItemStack> cleaned = new ArrayList<>();
            for (ItemStack child : bundle.getItems()) {
                ItemStack safe = sanitize(child, remaining);
                if (safe != null && !safe.getType().isAir()) cleaned.add(safe);
            }
            bundle.setItems(cleaned);
            stack.setItemMeta(bundle);
        }

        if (stack.getItemMeta() instanceof BlockStateMeta stateMeta
                && stateMeta.getBlockState() instanceof ShulkerBox shulker) {
            ItemStack[] contents = shulker.getInventory().getContents();
            for (int i = 0; i < contents.length; i++) contents[i] = sanitize(contents[i], remaining);
            shulker.getInventory().setContents(contents);
            stateMeta.setBlockState(shulker);
            stack.setItemMeta(stateMeta);
        }
        return stack;
    }
}
