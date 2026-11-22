package com.reconnaissance.factions;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

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
        List<Faction> ranked = factions.leaderboard();
        StringBuilder header = new StringBuilder();
        header.append(ChatColor.GOLD).append(ChatColor.BOLD).append("RECONNAISSANCE SMP")
            .append(ChatColor.RESET).append(ChatColor.DARK_GRAY).append("  |  ")
            .append(ChatColor.WHITE).append("FACTION LEADERBOARD");
        StringBuilder footer = new StringBuilder();
        if (ranked.isEmpty()) {
            footer.append(ChatColor.GRAY).append("No factions have been created yet.");
        } else {
            int shown = Math.min(10, ranked.size());
            for (int i = 0; i < shown; i++) {
                Faction entry = ranked.get(i);
                ChatColor factionColor = factionChatColor(entry.color);
                footer.append(ChatColor.GOLD).append("#").append(i + 1).append(" ")
                    .append(factionColor).append(entry.name)
                    .append(ChatColor.DARK_GRAY).append("  |  ")
                    .append(ChatColor.YELLOW).append(entry.points).append(ChatColor.GRAY).append(" pts");
                if (i < shown - 1) footer.append("\n");
            }
        }
        footer.append("\n").append(ChatColor.DARK_GRAY).append("Play fair. Build your legacy.");
        player.setPlayerListHeaderFooter(header.toString(), footer.toString());

        Faction faction = factions.byPlayer(player.getUniqueId());
        if (faction == null) {
            player.setPlayerListName(ChatColor.WHITE + player.getName());
            return;
        }
        ChatColor color = factionChatColor(faction.color);
        int rank = ranked.indexOf(faction) + 1;
        String rankText = rank > 0 ? ChatColor.GOLD + "#" + rank + " " : "";
        player.setPlayerListName(rankText + color + "[" + faction.name + "] " + ChatColor.WHITE + player.getName());
    }

    private ChatColor factionChatColor(String raw) {
        return switch (raw.toLowerCase()) {
            case "black" -> ChatColor.BLACK; case "dark_blue" -> ChatColor.DARK_BLUE;
            case "dark_green" -> ChatColor.DARK_GREEN; case "dark_aqua" -> ChatColor.DARK_AQUA;
            case "dark_red" -> ChatColor.DARK_RED; case "dark_purple" -> ChatColor.DARK_PURPLE;
            case "gold" -> ChatColor.GOLD; case "gray", "grey" -> ChatColor.GRAY;
            case "dark_gray", "dark_grey" -> ChatColor.DARK_GRAY; case "blue" -> ChatColor.BLUE;
            case "green" -> ChatColor.GREEN; case "aqua" -> ChatColor.AQUA;
            case "red" -> ChatColor.RED; case "light_purple", "pink" -> ChatColor.LIGHT_PURPLE;
            case "yellow" -> ChatColor.YELLOW; default -> ChatColor.WHITE;
        };
    }
}
