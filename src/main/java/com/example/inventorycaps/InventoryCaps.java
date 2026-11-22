package com.example.inventorycaps;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
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
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class InventoryCaps extends JavaPlugin implements Listener {
    private final Map<Material, Integer> limits = new EnumMap<>(Material.class);
    private final Map<UUID, Map<Material, Long>> lastWarnings = new HashMap<>();
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
        // Creative inventory actions use a client-side cursor/slot protocol.
        // Rewriting the inventory during those actions can make items vanish.
        // Leave creative players alone rather than risk deleting their items.
        if (player.getGameMode() == GameMode.CREATIVE || limits.isEmpty()) return;

        EnumMap<Material, Integer> remaining = new EnumMap<>(limits);
        PlayerInventory inv = player.getInventory();

        // Pass 1: bundle contents have priority and consume the allowance first.
        // Shulker boxes are deliberately ignored and are never read or modified.
        ItemStack[] storage = inv.getStorageContents();
        for (int i = 0; i < storage.length; i++) storage[i] = trimBundleContents(player, storage[i], remaining);
        inv.setStorageContents(storage);

        ItemStack[] armor = inv.getArmorContents();
        for (int i = 0; i < armor.length; i++) armor[i] = trimBundleContents(player, armor[i], remaining);
        inv.setArmorContents(armor);

        inv.setItemInOffHand(trimBundleContents(player, inv.getItemInOffHand(), remaining));

        // Pass 2: loose items are trimmed only after all bundle contents have
        // been counted, so loose excess is dropped before bundle contents.
        storage = inv.getStorageContents();
        for (int i = 0; i < storage.length; i++) storage[i] = trimDirect(player, storage[i], remaining);
        inv.setStorageContents(storage);

        armor = inv.getArmorContents();
        for (int i = 0; i < armor.length; i++) armor[i] = trimDirect(player, armor[i], remaining);
        inv.setArmorContents(armor);

        inv.setItemInOffHand(trimDirect(player, inv.getItemInOffHand(), remaining));
        player.updateInventory();
    }

    private ItemStack trimDirect(Player player, ItemStack original, EnumMap<Material, Integer> remaining) {
        if (original == null || original.getType().isAir()) return original;
        Material type = original.getType();
        if (!limits.containsKey(type)) return original;

        ItemStack stack = original.clone();
        int left = Math.max(0, remaining.getOrDefault(type, limits.get(type)));
        int keep = Math.min(stack.getAmount(), left);
        int excess = stack.getAmount() - keep;
        remaining.put(type, left - keep);
        if (excess > 0) dropExcess(player, stack, excess, type);
        if (keep == 0) return null;
        stack.setAmount(keep);
        return stack;
    }

    private ItemStack trimBundleContents(Player player, ItemStack original, EnumMap<Material, Integer> remaining) {
        if (original == null || original.getType().isAir()) return original;
        if (!(original.getItemMeta() instanceof BundleMeta)) return original;

        ItemStack stack = original.clone();
        BundleMeta meta = (BundleMeta) stack.getItemMeta();
        List<ItemStack> cleaned = new ArrayList<>();
        for (ItemStack child : meta.getItems()) {
            ItemStack safe;
            if (child != null && limits.containsKey(child.getType())) {
                Material type = child.getType();
                int left = Math.max(0, remaining.getOrDefault(type, limits.get(type)));
                int keep = Math.min(child.getAmount(), left);
                int excess = child.getAmount() - keep;
                remaining.put(type, left - keep);
                if (excess > 0) dropExcess(player, child, excess, type);
                safe = keep == 0 ? null : child.clone();
                if (safe != null) safe.setAmount(keep);
            } else {
                safe = trimBundleContents(player, child, remaining);
            }
            if (safe != null && !safe.getType().isAir()) cleaned.add(safe);
        }
        meta.setItems(cleaned);
        stack.setItemMeta(meta);
        return stack;
    }

    private void dropExcess(Player player, ItemStack source, int amount, Material type) {
        ItemStack dropped = source.clone();
        dropped.setAmount(amount);
        player.getWorld().dropItemNaturally(player.getLocation(), dropped);
        warn(player, type);
    }

    private void warn(Player player, Material material) {
        if (!notifyPlayer) return;
        long now = System.currentTimeMillis();
        Map<Material, Long> playerWarnings = lastWarnings.computeIfAbsent(
                player.getUniqueId(), id -> new EnumMap<>(Material.class));
        long last = playerWarnings.getOrDefault(material, 0L);
        if (now - last < 2000L) return;
        playerWarnings.put(material, now);

        int limit = limits.getOrDefault(material, 0);
        String itemName = material == Material.ENDER_PEARL ? "pearls" :
                material == Material.TOTEM_OF_UNDYING ? "totems" :
                material.name().toLowerCase().replace('_', ' ') + "s";
        player.sendMessage("§c[Inventory Limit] Limit of " + limit + " " + itemName + ". Extra items dropped.");
    }
}
