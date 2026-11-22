package com.reconnaissance.factions;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class ReconnaissanceFactionsPlugin extends JavaPlugin {
    private FactionManager factions;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        factions = new FactionManager(this);
        FactionCommand commands = new FactionCommand(this, factions);
        for (String name : new String[]{"team", "faction", "expand"}) {
            if (getCommand(name) != null) {
                getCommand(name).setExecutor(commands);
                getCommand(name).setTabCompleter(commands);
            }
        }
        getServer().getPluginManager().registerEvents(new FactionListener(this, factions), this);
        refreshAllPlayerLists();
        factions.syncTopMaces();
        getLogger().info("Reconnaissance Factions enabled. Loaded " + factions.all().size() + " teams/factions.");
    }

    @Override
    public void onDisable() {
        if (factions != null) factions.save();
    }

    void refreshAllPlayerLists() {
        for (Player player : Bukkit.getOnlinePlayers()) updatePlayerList(player);
    }

    void updatePlayerList(Player player) {
        // Faction standings are intentionally shown only through /faction leaderboard.
        player.setPlayerListHeader(ChatColor.GOLD.toString() + ChatColor.BOLD + "RECONNAISSANCE SMP");
        player.setPlayerListFooter("");
        player.setPlayerListName(ChatColor.WHITE + player.getName());
    }
}
