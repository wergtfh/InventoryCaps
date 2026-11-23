package com.reconnaissance.factions;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.scoreboard.Team.Option;
import org.bukkit.scoreboard.Team.OptionStatus;

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
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayerList(player);
            updateFactionNameTag(player);
        }
    }

    void updateFactionNameTag(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = "rf" + player.getUniqueId().toString().replace("-", "").substring(0, 14);
        Team old = board.getTeam(teamName);
        if (old != null) old.unregister();
        Faction faction = factions.byPlayer(player.getUniqueId());
        if (faction == null || !faction.official) return;
        Team team = board.registerNewTeam(teamName);
        team.prefix(Component.text(faction.name + " ", factions.color(faction)));
        team.setOption(Option.NAME_TAG_VISIBILITY, OptionStatus.ALWAYS);
        team.addEntry(player.getName());
    }

    void refreshFactionNameTags() {
        for (Player player : Bukkit.getOnlinePlayers()) updateFactionNameTag(player);
    }

    void updatePlayerList(Player player) {
        player.setPlayerListHeader(ChatColor.GOLD.toString() + ChatColor.BOLD + "RECONNAISSANCE SMP");
        player.setPlayerListFooter("");
        Faction faction = factions.byPlayer(player.getUniqueId());
        if (faction != null && faction.official) {
            Component listName = Component.text("[" + faction.name + "] ", factions.color(faction))
                .append(Component.text(player.getName()));
            player.playerListName(listName);
        } else {
            player.playerListName(Component.text(player.getName()));
        }
    }
}
