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
import java.util.*;

public final class InventoryCaps extends JavaPlugin implements Listener {
    private final Map<Material,Integer> limits = new EnumMap<>(Material.class);
    private final Map<UUID,Map<Material,Long>> lastWarnings = new HashMap<>();
    private boolean notifyPlayer;
    private int intervalTicks;

    @Override public void onEnable() {
        saveDefaultConfig();
        if (getConfig().getConfigurationSection("limits") != null) {
            for (String key : getConfig().getConfigurationSection("limits").getKeys(false)) {
                Material m = Material.matchMaterial(key);
                int n = getConfig().getInt("limits."+key,-1);
                if (m != null && m.isItem() && n >= 0) limits.put(m,n);
            }
        }
        notifyPlayer = getConfig().getBoolean("notify-player",true);
        intervalTicks = Math.max(1,getConfig().getInt("enforcement-interval-ticks",2));
        Bukkit.getPluginManager().registerEvents(this,this);
        Bukkit.getScheduler().runTaskTimer(this,()->Bukkit.getOnlinePlayers().forEach(this::enforce),1L,intervalTicks);
    }

    @EventHandler(priority=EventPriority.MONITOR) public void join(PlayerJoinEvent e){schedule(e.getPlayer());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void pickup(EntityPickupItemEvent e){if(e.getEntity() instanceof Player p)schedule(p);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void click(InventoryClickEvent e){if(e.getWhoClicked() instanceof Player p)schedule(p);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void drag(InventoryDragEvent e){if(e.getWhoClicked() instanceof Player p)schedule(p);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void drop(PlayerDropItemEvent e){schedule(e.getPlayer());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void held(PlayerItemHeldEvent e){schedule(e.getPlayer());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void swap(PlayerSwapHandItemsEvent e){schedule(e.getPlayer());}

    private void schedule(Player p) {
        Bukkit.getScheduler().runTask(this,()->{if(p.isOnline())enforce(p);});
    }

    private void enforce(Player p) {
        // Skip Creative entirely: rewriting slots while Creative's client-side
        // inventory protocol is active can make clicked items disappear.
        if(p.getGameMode()==GameMode.CREATIVE || limits.isEmpty()) return;
        EnumMap<Material,Integer> remaining = new EnumMap<>(limits);
        PlayerInventory inv = p.getInventory();

        // Loose inventory items have priority and are kept first.
        ItemStack[] storage=inv.getStorageContents();
        for(int i=0;i<storage.length;i++) storage[i]=trimLoose(p,storage[i],remaining);
        inv.setStorageContents(storage);
        ItemStack[] armor=inv.getArmorContents();
        for(int i=0;i<armor.length;i++) armor[i]=trimLoose(p,armor[i],remaining);
        inv.setArmorContents(armor);
        inv.setItemInOffHand(trimLoose(p,inv.getItemInOffHand(),remaining));

        // Bundle contents are counted after loose items, so they are the first
        // source trimmed when the combined total exceeds the limit. Shulkers
        // are not inspected or changed.
        storage=inv.getStorageContents();
        for(int i=0;i<storage.length;i++) storage[i]=trimBundles(p,storage[i],remaining);
        inv.setStorageContents(storage);
        armor=inv.getArmorContents();
        for(int i=0;i<armor.length;i++) armor[i]=trimBundles(p,armor[i],remaining);
        inv.setArmorContents(armor);
        inv.setItemInOffHand(trimBundles(p,inv.getItemInOffHand(),remaining));
    }

    private ItemStack trimLoose(Player p,ItemStack item,EnumMap<Material,Integer> remaining) {
        if(item==null || item.getType().isAir() || !limits.containsKey(item.getType())) return item;
        Material type=item.getType();
        int left=Math.max(0,remaining.getOrDefault(type,limits.get(type)));
        int keep=Math.min(item.getAmount(),left);
        int excess=item.getAmount()-keep;
        remaining.put(type,left-keep);
        if(excess>0) dropExcess(p,item,excess,type);
        if(keep==0)return null;
        ItemStack kept=item.clone(); kept.setAmount(keep); return kept;
    }

    private ItemStack trimBundles(Player p,ItemStack item,EnumMap<Material,Integer> remaining) {
        if(item==null || item.getType().isAir() || !(item.getItemMeta() instanceof BundleMeta)) return item;
        ItemStack result=item.clone();
        BundleMeta meta=(BundleMeta)result.getItemMeta();
        List<ItemStack> kept=new ArrayList<>();
        for(ItemStack child:meta.getItems()) {
            ItemStack safe;
            if(child!=null && limits.containsKey(child.getType())) {
                Material type=child.getType();
                int left=Math.max(0,remaining.getOrDefault(type,limits.get(type)));
                int keep=Math.min(child.getAmount(),left);
                int excess=child.getAmount()-keep;
                remaining.put(type,left-keep);
                if(excess>0)dropExcess(p,child,excess,type);
                safe=keep==0?null:child.clone();
                if(safe!=null)safe.setAmount(keep);
            } else safe=trimBundles(p,child,remaining);
            if(safe!=null && !safe.getType().isAir())kept.add(safe);
        }
        meta.setItems(kept);
        result.setItemMeta(meta);
        return result;
    }

    private void dropExcess(Player p,ItemStack source,int amount,Material type) {
        ItemStack drop=source.clone(); drop.setAmount(amount);
        p.getWorld().dropItemNaturally(p.getLocation(),drop);
        warn(p,type);
    }

    private void warn(Player p,Material type) {
        if(!notifyPlayer)return;
        long now=System.currentTimeMillis();
        Map<Material,Long> map=lastWarnings.computeIfAbsent(p.getUniqueId(),k->new EnumMap<>(Material.class));
        if(now-map.getOrDefault(type,0L)<2000L)return;
        map.put(type,now);
        String name=type==Material.ENDER_PEARL?"pearls":type==Material.TOTEM_OF_UNDYING?"totems":type.name().toLowerCase(Locale.ROOT).replace('_',' ')+"s";
        p.sendMessage("§c[Inventory Limit] Limit of "+limits.get(type)+" "+name+". Extra items dropped.");
    }
}