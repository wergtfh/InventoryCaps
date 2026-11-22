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
        Faction faction = factions.byPlayer(player.getUniqueId());
        if (faction == null) {
            player.setPlayerListName(ChatColor.WHITE + player.getName());
            return;
        }
        ChatColor color = switch (faction.color.toLowerCase()) {
            case "black" -> ChatColor.BLACK; case "dark_blue" -> ChatColor.DARK_BLUE;
            case "dark_green" -> ChatColor.DARK_GREEN; case "dark_aqua" -> ChatColor.DARK_AQUA;
            case "dark_red" -> ChatColor.DARK_RED; case "dark_purple" -> ChatColor.DARK_PURPLE;
            case "gold" -> ChatColor.GOLD; case "gray", "grey" -> ChatColor.GRAY;
            case "dark_gray", "dark_grey" -> ChatColor.DARK_GRAY; case "blue" -> ChatColor.BLUE;
            case "green" -> ChatColor.GREEN; case "aqua" -> ChatColor.AQUA;
            case "red" -> ChatColor.RED; case "light_purple", "pink" -> ChatColor.LIGHT_PURPLE;
            case "yellow" -> ChatColor.YELLOW; default -> ChatColor.WHITE;
        };
        player.setPlayerListName(color + "[" + faction.name + "] " + ChatColor.WHITE + player.getName());
    }
}
