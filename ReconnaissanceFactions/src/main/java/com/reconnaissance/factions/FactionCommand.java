package com.reconnaissance.factions;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class FactionCommand implements CommandExecutor, TabCompleter {
    private final FactionManager manager;
    private final ReconnaissanceFactionsPlugin plugin;

    FactionCommand(ReconnaissanceFactionsPlugin plugin, FactionManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String root = command.getName().toLowerCase(Locale.ROOT);
        if (!(sender instanceof Player player)) {
            sender.sendMessage("These commands can only be used in-game.");
            return true;
        }
        if (root.equals("team")) return team(player, args);
        if (root.equals("faction")) return faction(player, args);
        if (root.equals("expand")) {
            player.sendMessage(ChatColor.GOLD + manager.expand(player));
            return true;
        }
        return true;
    }

    private boolean team(Player player, String[] args) {
        if (args.length == 0) {
            Faction faction = manager.byPlayer(player.getUniqueId());
            if (faction == null) {
                player.sendMessage(ChatColor.YELLOW + "You are not in a team. Use /team create <name>.");
                return true;
            }
            player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + faction.name);
            String leader = Bukkit.getOfflinePlayer(faction.owner).getName();
            player.sendMessage(ChatColor.YELLOW + "Leader: " + ChatColor.WHITE + (leader == null ? "Unknown" : leader));
            player.sendMessage(ChatColor.YELLOW + "Members (" + faction.members.size() + "):");
            for (var id : faction.members) {
                String name = Bukkit.getOfflinePlayer(id).getName();
                player.sendMessage(ChatColor.GRAY + " • " + (name == null ? id.toString() : name) +
                    (id.equals(faction.owner) ? ChatColor.GOLD + " (Leader)" : ""));
            }
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                if (args.length != 2) {
                    player.sendMessage(ChatColor.YELLOW + "Usage: /team create <name>");
                    return true;
                }
                player.sendMessage(ChatColor.GOLD + manager.createTeam(player, args[1]));
                plugin.refreshAllPlayerLists();
            }
            case "invite" -> {
                if (args.length != 2) {
                    player.sendMessage(ChatColor.YELLOW + "Usage: /team invite <player>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    player.sendMessage(ChatColor.RED + "That player must be online to receive an invite.");
                    return true;
                }
                player.sendMessage(ChatColor.GOLD + manager.invite(player, target));
            }
            case "accept" -> {
                player.sendMessage(ChatColor.GOLD + manager.accept(player));
                plugin.refreshAllPlayerLists();
            }
            case "delete" -> {
                if (args.length == 1) {
                    player.sendMessage(ChatColor.GOLD + manager.startDisband(player));
                    plugin.refreshAllPlayerLists();
                } else if (args.length == 2 && args[1].equalsIgnoreCase("confirm")) {
                    player.sendMessage(ChatColor.GOLD + manager.confirmDisband(player));
                    plugin.refreshAllPlayerLists();
                } else {
                    player.sendMessage(ChatColor.YELLOW + "Usage: /team delete [confirm]");
                }
            }
            case "leave" -> {
                player.sendMessage(ChatColor.GOLD + manager.leaveTeam(player));
                plugin.refreshAllPlayerLists();
            }
            default -> player.sendMessage(ChatColor.YELLOW + "Commands: /team, /team create <name>, /team invite <player>, /team accept, /team leave, /team delete [confirm]");
        }
        return true;
    }

    private boolean faction(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage(ChatColor.YELLOW + "Commands: /faction create, /faction leaderboard, /faction color <colour>, /faction <name>");
            return true;
        }
        if (args[0].equalsIgnoreCase("create")) {
            player.sendMessage(ChatColor.GOLD + manager.createClaim(player));
            plugin.refreshAllPlayerLists();
            return true;
        }
        if (args[0].equalsIgnoreCase("leaderboard")) {
            List<Faction> ranking = manager.leaderboard();
            player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "RECONNAISSANCE SMP • FACTION LEADERBOARD");
            if (ranking.isEmpty()) {
                player.sendMessage(ChatColor.GRAY + "No factions have claimed territory yet.");
                return true;
            }
            int rank = 1;
            for (Faction faction : ranking) {
                ChatColor color = rank == 1 ? ChatColor.GOLD : rank == 2 ? ChatColor.WHITE : rank == 3 ? ChatColor.RED : ChatColor.GRAY;
                player.sendMessage(color + "#" + rank++ + " " + faction.name + ChatColor.DARK_GRAY + " • " +
                    ChatColor.YELLOW + faction.points + " points");
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("color")) {
            if (args.length != 2) {
                player.sendMessage(ChatColor.YELLOW + "Usage: /faction color <colour>");
                return true;
            }
            Faction faction = manager.byPlayer(player.getUniqueId());
            if (faction == null || !faction.official) {
                player.sendMessage(ChatColor.RED + "You need an official faction claim before changing its colour.");
                return true;
            }
            if (!manager.isLeader(player, faction)) {
                player.sendMessage(ChatColor.RED + "Only the faction leader can change the faction colour.");
                return true;
            }
            String[] colors = {"black","dark_blue","dark_green","dark_aqua","dark_red","dark_purple","gold","gray","dark_gray","blue","green","aqua","red","light_purple","yellow","white"};
            boolean valid = false;
            for (String color : colors) if (color.equalsIgnoreCase(args[1])) valid = true;
            if (!valid) {
                player.sendMessage(ChatColor.YELLOW + "Colours: " + String.join(", ", colors));
                return true;
            }
            manager.setColor(faction, args[1]);
            plugin.refreshAllPlayerLists();
            player.sendMessage(ChatColor.GREEN + "Faction colour changed to " + args[1] + ".");
            return true;
        }

        Faction faction = manager.byName(args[0]);
        if (faction == null) {
            player.sendMessage(ChatColor.RED + "Faction not found.");
            return true;
        }
        player.sendMessage(manager.info(faction));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("team") && args.length == 1) {
            options.addAll(List.of("create", "invite", "accept", "leave", "delete"));
        } else if (command.getName().equalsIgnoreCase("faction") && args.length == 1) {
            options.addAll(List.of("create", "leaderboard", "color"));
            for (Faction faction : manager.all()) options.add(faction.name);
        } else if (command.getName().equalsIgnoreCase("faction") && args.length == 2 && args[0].equalsIgnoreCase("color")) {
            options.addAll(List.of("red","blue","green","yellow","gold","purple","aqua","white","dark_red","dark_blue","dark_green","light_purple"));
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
