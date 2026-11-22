package com.reconnaissance.factions;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

final class FactionManager {
    private static final String TEST_TEAM_NAME = "mkdiaeruf";
    private final JavaPlugin plugin;
    private final Map<UUID, Faction> factions = new LinkedHashMap<>();
    private final Map<UUID, UUID> pendingInvites = new HashMap<>();
    private final Map<UUID, Set<UUID>> disbandVotes = new HashMap<>();
    private final Map<UUID, ItemStack> pendingMaceReturns = new HashMap<>();
    private final File file;
    private final NamespacedKey maceFactionKey;

    FactionManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.maceFactionKey = new NamespacedKey(plugin, "faction_mace");
        this.file = new File(plugin.getDataFolder(), "factions.yml");
        load();
    }

    Collection<Faction> all() { return Collections.unmodifiableCollection(factions.values()); }
    Faction byPlayer(UUID player) {
        for (Faction faction : factions.values()) if (faction.members.contains(player)) return faction;
        return null;
    }
    Faction byName(String name) {
        for (Faction faction : factions.values()) if (faction.name.equalsIgnoreCase(name)) return faction;
        return null;
    }
    boolean isLeader(Player player, Faction faction) { return faction != null && faction.owner.equals(player.getUniqueId()); }
    boolean isMember(Faction faction, UUID player) { return faction != null && faction.members.contains(player); }

    String createTeam(Player player, String name) {
        if (byPlayer(player.getUniqueId()) != null) return "You are already in a team.";
        if (name == null || !name.matches("[A-Za-z]{1,10}")) return "Team names must contain 1-10 letters only.";
        if (byName(name) != null) return "That team name is already taken.";
        Faction faction = new Faction(UUID.randomUUID(), name, player.getUniqueId());
        if (name.equalsIgnoreCase(TEST_TEAM_NAME)) {
            faction.points = 100;
            faction.expansionChunks = 100;
        }
        factions.put(faction.id, faction);
        save();
        return "Team " + name + " created. Invite players with /team invite <player>.";
    }

    String invite(Player inviter, Player target) {
        Faction faction = byPlayer(inviter.getUniqueId());
        if (faction == null) return "You are not in a team.";
        if (!isLeader(inviter, faction)) return "Only the team leader can invite players.";
        if (byPlayer(target.getUniqueId()) != null) return target.getName() + " is already in a team.";
        if (faction.members.contains(target.getUniqueId())) return "That player is already in your team.";
        pendingInvites.put(target.getUniqueId(), faction.id);
        save();
        target.sendMessage(Component.text("You have been invited to " + faction.name + ". Type /team accept to join.", NamedTextColor.GREEN));
        return "Invite sent to " + target.getName() + ".";
    }

    String accept(Player player) {
        UUID factionId = pendingInvites.remove(player.getUniqueId());
        if (factionId == null) return "You do not have a pending team invite.";
        Faction faction = factions.get(factionId);
        if (faction == null) return "That team no longer exists.";
        if (byPlayer(player.getUniqueId()) != null) return "Leave your current team before accepting another invite.";
        faction.members.add(player.getUniqueId());
        save();
        return "You joined " + faction.name + ".";
    }


    String leaveTeam(Player player) {
        Faction faction = byPlayer(player.getUniqueId());
        if (faction == null) return "You are not in a team.";
        if (isLeader(player, faction)) return "The team leader cannot leave. Use /team delete to disband the team.";
        faction.members.remove(player.getUniqueId());
        faction.lastVictims.remove(player.getUniqueId());
        save();
        syncTopMaces();
        return "You left " + faction.name + ".";
    }

    String startDisband(Player player) {
        Faction faction = byPlayer(player.getUniqueId());
        if (faction == null) return "You are not in a team.";
        if (!isLeader(player, faction)) return "Only the team leader can start a disband vote.";
        if (!faction.official) {
            removeFaction(faction);
            return "Team " + faction.name + " has been disbanded.";
        }
        Set<UUID> votes = disbandVotes.computeIfAbsent(faction.id, ignored -> new HashSet<>());
        votes.add(player.getUniqueId());
        int requiredVotes = (int) Math.ceil(faction.members.size() * 0.90);
        if (votes.size() >= requiredVotes) {
            String name = faction.name;
            removeFaction(faction);
            return "Faction " + name + " has been disbanded. Its claim, border and all points have been removed.";
        }
        return "Disband vote started. Your vote counts. " + votes.size() + "/" + requiredVotes + " yes votes (90% required). Members use /team delete confirm.";
    }

    String confirmDisband(Player player) {
        Faction faction = byPlayer(player.getUniqueId());
        if (faction == null) return "You are not in a team.";
        if (!faction.official) return "This team does not need a vote. Ask its leader to use /team delete.";
        Set<UUID> votes = disbandVotes.get(faction.id);
        if (votes == null) return "There is no active disband vote. Ask the team leader to use /team delete.";
        if (!votes.add(player.getUniqueId())) return "Your vote has already been counted.";
        int requiredVotes = (int) Math.ceil(faction.members.size() * 0.90);
        if (votes.size() >= requiredVotes) {
            String name = faction.name;
            removeFaction(faction);
            return "Faction " + name + " has been disbanded. Its claim, border and all points have been removed.";
        }
        return "Disband vote recorded: " + votes.size() + "/" + requiredVotes + " yes votes (90% required).";
    }

    private void removeFaction(Faction faction) {
        restoreBorders(faction);
        disbandVotes.remove(faction.id);
        factions.remove(faction.id);
        pendingInvites.values().removeIf(id -> id.equals(faction.id));
        for (Player online : Bukkit.getOnlinePlayers()) {
            for (int slot = 0; slot < online.getInventory().getSize(); slot++) {
                if (faction.id.equals(maceFactionId(online.getInventory().getItem(slot))))
                    online.getInventory().setItem(slot, null);
            }
        }
        save();
        syncTopMaces();
    }

    String createClaim(Player player) {
        Faction faction = byPlayer(player.getUniqueId());
        if (faction == null) return "Create a team first with /team create <name>.";
        if (!isLeader(player, faction)) return "Only the team leader can create the faction claim.";
        if (faction.official) return "Your faction already has a claim.";
        boolean testFaction = faction.name.equalsIgnoreCase(TEST_TEAM_NAME);
        if (!testFaction && faction.members.size() < 4) return "You need at least 4 team members to create a faction.";
        if (testFaction) {
            faction.points = Math.max(faction.points, 100);
            faction.expansionChunks = Math.max(faction.expansionChunks, 100);
        }
        World world = player.getWorld();
        int cx = player.getLocation().getChunk().getX();
        int cz = player.getLocation().getChunk().getZ();
        Set<ChunkKey> initial = new LinkedHashSet<>();
        for (int x = cx - 3; x <= cx + 4; x++) {
            for (int z = cz - 3; z <= cz + 4; z++) {
                ChunkKey key = new ChunkKey(x, z);
                if (isChunkUnavailable(world, key, faction)) return "Claim cancelled: another claim or spawn protection is in the way.";
                initial.add(key);
            }
        }
        faction.worldId = world.getUID();
        faction.chunks.addAll(initial);
        faction.official = true;
        rebuildBorders(faction);
        save();
        syncTopMaces();
        return "Faction " + faction.name + " created with an 8x8 chunk claim." + (testFaction ? " Test mode: 5-member requirement bypassed, 100 points and 100 expansion chunks granted." : "");
    }

    String expand(Player player) {
        Faction faction = byPlayer(player.getUniqueId());
        if (faction == null || !faction.official) return "You need an official faction claim first.";
        if (!isLeader(player, faction)) return "Only the faction leader can expand territory.";
        if (faction.expansionChunks < 1) return "Your faction has no expansion chunks. Earn points through eligible kills.";
        World world = Bukkit.getWorld(faction.worldId);
        if (world == null) return "The claim's world is unavailable.";
        Set<ChunkKey> candidates = new LinkedHashSet<>();
        for (ChunkKey key : faction.chunks) {
            candidates.add(new ChunkKey(key.x() + 1, key.z()));
            candidates.add(new ChunkKey(key.x() - 1, key.z()));
            candidates.add(new ChunkKey(key.x(), key.z() + 1));
            candidates.add(new ChunkKey(key.x(), key.z() - 1));
        }
        ChunkKey playerChunk = new ChunkKey(player.getLocation().getChunk().getX(), player.getLocation().getChunk().getZ());
        ChunkKey chosen = candidates.stream()
            .filter(key -> !faction.chunks.contains(key))
            .filter(key -> !isChunkUnavailable(world, key, faction))
            .min(Comparator.comparingInt(key -> Math.abs(key.x() - playerChunk.x()) + Math.abs(key.z() - playerChunk.z())))
            .orElse(null);
        if (chosen == null) return "Cannot expand: every adjacent chunk is claimed or protected by spawn.";
        faction.chunks.add(chosen);
        faction.expansionChunks--;
        rebuildBorders(faction);
        save();
        return "Faction expanded by 1 chunk. Remaining expansion chunks: " + faction.expansionChunks + ".";
    }

    private boolean isChunkUnavailable(World world, ChunkKey key, Faction except) {
        if (isSpawnProtected(world, key)) return true;
        for (Faction other : factions.values()) {
            if (other == except || !other.official || !world.getUID().equals(other.worldId)) continue;
            if (other.chunks.contains(key)) return true;
        }
        return false;
    }

    private boolean isSpawnProtected(World world, ChunkKey key) {
        int radius = Math.max(0, plugin.getConfig().getInt("spawn-protection-radius", 16));
        Location spawn = world.getSpawnLocation();
        int minX = key.x() << 4, minZ = key.z() << 4;
        int maxX = minX + 15, maxZ = minZ + 15;
        return maxX >= spawn.getBlockX() - radius && minX <= spawn.getBlockX() + radius
            && maxZ >= spawn.getBlockZ() - radius && minZ <= spawn.getBlockZ() + radius;
    }

    Faction claimAt(Location location) {
        if (location == null || location.getWorld() == null) return null;
        ChunkKey key = new ChunkKey(location.getBlockX() >> 4, location.getBlockZ() >> 4);
        for (Faction faction : factions.values()) {
            if (faction.official && location.getWorld().getUID().equals(faction.worldId) && faction.chunks.contains(key)) return faction;
        }
        return null;
    }

    boolean isBorderBlock(Location location) {
        if (location == null || location.getWorld() == null) return false;
        Faction faction = claimAt(location);
        if (faction == null) return false;
        String prefix = location.getWorld().getUID() + ":" + location.getBlockX() + ":" +
            location.getBlockY() + ":" + location.getBlockZ() + ":";
        return faction.borderBlocks.stream().anyMatch(encoded -> encoded.startsWith(prefix));
    }

    void awardKill(Player killer, Player victim) {
        Faction faction = byPlayer(killer.getUniqueId());
        if (faction == null || !faction.official) return;
        UUID previous = faction.lastVictims.put(killer.getUniqueId(), victim.getUniqueId());
        if (faction.members.contains(victim.getUniqueId()) || victim.getUniqueId().equals(previous)) {
            save();
            return;
        }
        boolean wearingDiamond = Arrays.stream(killer.getInventory().getArmorContents())
            .filter(Objects::nonNull).anyMatch(item -> switch (item.getType()) {
                case DIAMOND_HELMET, DIAMOND_CHESTPLATE, DIAMOND_LEGGINGS, DIAMOND_BOOTS -> true;
                default -> false;
            });
        if (!wearingDiamond) {
            save();
            return;
        }
        faction.points++;
        faction.expansionChunks++;
        save();
        syncTopMaces();
    }


    boolean isFactionMace(ItemStack item) {
        if (item == null || item.getType() != Material.MACE || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(maceFactionKey, PersistentDataType.STRING);
    }

    UUID maceFactionId(ItemStack item) {
        if (!isFactionMace(item)) return null;
        try {
            return UUID.fromString(item.getItemMeta().getPersistentDataContainer().get(maceFactionKey, PersistentDataType.STRING));
        } catch (Exception ignored) { return null; }
    }

    ItemStack createFactionMace(Faction faction) {
        ItemStack mace = new ItemStack(Material.MACE);
        ItemMeta meta = mace.getItemMeta();
        meta.displayName(Component.text(faction.name, color(faction)));
        meta.lore(List.of(Component.text("RECONNAISSANCE SMP", NamedTextColor.GOLD),
            Component.text("Awarded to a top-three faction", NamedTextColor.GRAY)));
        meta.addEnchant(Enchantment.UNBREAKING, 3, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        meta.addEnchant(Enchantment.DENSITY, 5, true);
        meta.addEnchant(Enchantment.WIND_BURST, 1, true);
        meta.getPersistentDataContainer().set(maceFactionKey, PersistentDataType.STRING, faction.id.toString());
        mace.setItemMeta(meta);
        return mace;
    }

    void returnMaceToOwner(Faction faction, ItemStack item) {
        if (faction == null || item == null || item.getType().isAir()) return;
        Player owner = Bukkit.getPlayer(faction.owner);
        if (owner == null) {
            pendingMaceReturns.put(faction.owner, item.clone());
            save();
            return;
        }
        Map<Integer, ItemStack> leftovers = owner.getInventory().addItem(item.clone());
        leftovers.values().forEach(left -> owner.getWorld().dropItemNaturally(owner.getLocation(), left));
        owner.sendMessage(Component.text("Your faction mace has been returned to you.", NamedTextColor.GOLD));
    }

    void deliverPendingMace(Player player) {
        ItemStack pending = pendingMaceReturns.remove(player.getUniqueId());
        if (pending == null) return;
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(pending);
        leftovers.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        player.sendMessage(Component.text("Your faction mace was returned while you were offline.", NamedTextColor.GOLD));
        save();
    }

    void syncTopMaces() {
        List<Faction> top = leaderboard().stream().filter(f -> f.points >= 5).limit(3).toList();
        Set<UUID> winners = top.stream().map(f -> f.id).collect(Collectors.toSet());
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
                ItemStack item = player.getInventory().getItem(slot);
                UUID factionId = maceFactionId(item);
                if (factionId == null) continue;
                Faction ownerFaction = factions.get(factionId);
                if (ownerFaction == null || !winners.contains(factionId) || !ownerFaction.members.contains(player.getUniqueId())) {
                    player.getInventory().setItem(slot, null);
                    if (ownerFaction != null && winners.contains(factionId)) returnMaceToOwner(ownerFaction, item);
                }
            }
        }
        for (Faction faction : top) {
            Player owner = Bukkit.getPlayer(faction.owner);
            if (owner == null) continue;
            boolean alreadyHas = false;
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!faction.members.contains(online.getUniqueId())) continue;
                for (ItemStack item : online.getInventory().getContents()) {
                    if (faction.id.equals(maceFactionId(item))) { alreadyHas = true; break; }
                }
                if (alreadyHas) break;
            }
            if (!alreadyHas) {
                Map<Integer, ItemStack> leftovers = owner.getInventory().addItem(createFactionMace(faction));
                leftovers.values().forEach(left -> owner.getWorld().dropItemNaturally(owner.getLocation(), left));
                owner.sendMessage(Component.text("Your faction is in the top three! You have received the " + faction.name + " mace.", NamedTextColor.GOLD));
            }
        }
    }

    boolean isTopThreeFaction(UUID factionId) {
        return leaderboard().stream().filter(f -> f.points >= 5).limit(3).anyMatch(f -> f.id.equals(factionId));
    }

    void handleUnauthorizedMace(Player player, ItemStack item) {
        UUID factionId = maceFactionId(item);
        if (factionId == null) return;
        Faction faction = factions.get(factionId);
        if (faction != null && !faction.members.contains(player.getUniqueId())) returnMaceToOwner(faction, item);
    }

    List<Faction> leaderboard() {
        return factions.values().stream().filter(f -> f.official)
            .sorted(Comparator.comparingInt((Faction f) -> f.points).reversed().thenComparing(f -> f.name, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    String info(Faction faction) {
        String members = faction.members.stream().map(id -> {
            OfflinePlayer p = Bukkit.getOfflinePlayer(id);
            return (id.equals(faction.owner) ? "§6" : "§f") + (p.getName() == null ? id.toString().substring(0, 8) : p.getName()) +
                (id.equals(faction.owner) ? " (Leader)" : "");
        }).collect(Collectors.joining("§7, "));
        int rank = leaderboard().indexOf(faction) + 1;
        String center = "Not claimed";
        if (faction.official && !faction.chunks.isEmpty()) {
            double x = faction.chunks.stream().mapToInt(c -> c.x() * 16 + 8).average().orElse(0);
            double z = faction.chunks.stream().mapToInt(c -> c.z() * 16 + 8).average().orElse(0);
            center = "X " + Math.round(x) + ", Z " + Math.round(z);
        }
        return "§6§l" + faction.name + "\n§eLeader: §f" + Optional.ofNullable(Bukkit.getOfflinePlayer(faction.owner).getName()).orElse("Unknown") +
            "\n§eMembers: §f" + members + "\n§ePoints: §f" + faction.points + "\n§eRank: §f" + (rank == 0 ? "Unranked" : "#" + rank) +
            "\n§eExpansion chunks: §f" + faction.expansionChunks + "\n§eClaim centre: §f" + center;
    }

    void setColor(Faction faction, String color) { faction.color = color.toLowerCase(Locale.ROOT); save(); }
    NamedTextColor color(Faction faction) {
        return switch (faction.color.toLowerCase(Locale.ROOT)) {
            case "black" -> NamedTextColor.BLACK; case "dark_blue" -> NamedTextColor.DARK_BLUE;
            case "dark_green" -> NamedTextColor.DARK_GREEN; case "dark_aqua" -> NamedTextColor.DARK_AQUA;
            case "dark_red" -> NamedTextColor.DARK_RED; case "dark_purple" -> NamedTextColor.DARK_PURPLE;
            case "gold" -> NamedTextColor.GOLD; case "gray", "grey" -> NamedTextColor.GRAY;
            case "dark_gray", "dark_grey" -> NamedTextColor.DARK_GRAY; case "blue" -> NamedTextColor.BLUE;
            case "green" -> NamedTextColor.GREEN; case "aqua" -> NamedTextColor.AQUA;
            case "red" -> NamedTextColor.RED; case "light_purple", "pink" -> NamedTextColor.LIGHT_PURPLE;
            case "yellow" -> NamedTextColor.YELLOW; default -> NamedTextColor.WHITE;
        };
    }

    private void restoreBorders(Faction faction) {
        World world = faction.worldId == null ? null : Bukkit.getWorld(faction.worldId);
        if (world == null) return;
        for (String encoded : new ArrayList<>(faction.borderBlocks)) {
            String[] parts = encoded.split(":", 5);
            if (parts.length < 4) continue;
            try {
                Block block = world.getBlockAt(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
                if (block.getType() != Material.GOLD_BLOCK) continue;
                if (parts.length == 5) {
                    String original = new String(Base64.getUrlDecoder().decode(parts[4]), java.nio.charset.StandardCharsets.UTF_8);
                    block.setBlockData(Bukkit.createBlockData(original), false);
                } else {
                    block.setType(Material.AIR, false);
                }
            } catch (Exception ignored) { }
        }
        faction.borderBlocks.clear();
    }

    private int findTerrainSurfaceY(World world, int x, int z) {
        for (int y = world.getMaxHeight() - 1; y >= world.getMinHeight(); y--) {
            Block block = world.getBlockAt(x, y, z);
            Material type = block.getType();
            if (!type.isSolid() || block.isPassable()) continue;
            if (Tag.LEAVES.isTagged(type) || Tag.LOGS.isTagged(type)) continue;
            if (type == Material.BEEHIVE || type == Material.BEE_NEST || type == Material.MUSHROOM_STEM
                    || type == Material.BROWN_MUSHROOM_BLOCK || type == Material.RED_MUSHROOM_BLOCK) continue;
            return y;
        }
        return world.getMinHeight();
    }

    private void rebuildBorders(Faction faction) {
        World world = Bukkit.getWorld(faction.worldId);
        if (world == null) return;
        restoreBorders(faction);
        for (ChunkKey key : faction.chunks) {
            for (int x = key.x() << 4; x < (key.x() << 4) + 16; x++) {
                for (int z = key.z() << 4; z < (key.z() << 4) + 16; z++) {
                    boolean edge = !faction.chunks.contains(new ChunkKey((x - 1) >> 4, z >> 4))
                        || !faction.chunks.contains(new ChunkKey((x + 1) >> 4, z >> 4))
                        || !faction.chunks.contains(new ChunkKey(x >> 4, (z - 1) >> 4))
                        || !faction.chunks.contains(new ChunkKey(x >> 4, (z + 1) >> 4));
                    if (!edge) continue;
                    int y = findTerrainSurfaceY(world, x, z);
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() == Material.BEDROCK || block.getType() == Material.BARRIER) continue;
                    String original = block.getBlockData().getAsString();
                    String encodedOriginal = Base64.getUrlEncoder().withoutPadding().encodeToString(original.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    block.setType(Material.GOLD_BLOCK, false);
                    faction.borderBlocks.add(world.getUID() + ":" + x + ":" + y + ":" + z + ":" + encodedOriginal);
                }
            }
        }
    }
    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection pending = yaml.getConfigurationSection("pendingMaceReturns");
        if (pending != null) {
            for (String ownerId : pending.getKeys(false)) {
                try {
                    ItemStack item = pending.getItemStack(ownerId);
                    if (item != null) pendingMaceReturns.put(UUID.fromString(ownerId), item);
                } catch (Exception ex) {
                    plugin.getLogger().warning("Could not load pending faction mace for " + ownerId);
                }
            }
        }
        ConfigurationSection root = yaml.getConfigurationSection("factions");
        if (root == null) return;
        for (String idText : root.getKeys(false)) {
            try {
                UUID id = UUID.fromString(idText);
                ConfigurationSection s = root.getConfigurationSection(idText);
                if (s == null) continue;
                UUID owner = UUID.fromString(s.getString("owner"));
                Faction f = new Faction(id, s.getString("name", "Faction"), owner);
                f.members.clear();
                for (String member : s.getStringList("members")) f.members.add(UUID.fromString(member));
                f.points = s.getInt("points");
                f.expansionChunks = s.getInt("expansionChunks");
                f.official = s.getBoolean("official");
                f.color = s.getString("color", "white");
                String world = s.getString("world");
                if (world != null && !world.isBlank()) f.worldId = UUID.fromString(world);
                for (String chunk : s.getStringList("chunks")) f.chunks.add(ChunkKey.decode(chunk));
                f.borderBlocks.addAll(s.getStringList("borderBlocks"));
                ConfigurationSection kills = s.getConfigurationSection("lastVictims");
                if (kills != null) for (String killer : kills.getKeys(false)) f.lastVictims.put(UUID.fromString(killer), UUID.fromString(kills.getString(killer)));
                factions.put(id, f);
            } catch (Exception ex) {
                plugin.getLogger().warning("Skipped invalid faction entry " + idText + ": " + ex.getMessage());
            }
        }
    }

    void save() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        YamlConfiguration yaml = new YamlConfiguration();
        for (Faction f : factions.values()) {
            String path = "factions." + f.id;
            yaml.set(path + ".name", f.name);
            yaml.set(path + ".owner", f.owner.toString());
            yaml.set(path + ".members", f.members.stream().map(UUID::toString).toList());
            yaml.set(path + ".points", f.points);
            yaml.set(path + ".expansionChunks", f.expansionChunks);
            yaml.set(path + ".official", f.official);
            yaml.set(path + ".color", f.color);
            yaml.set(path + ".world", f.worldId == null ? null : f.worldId.toString());
            yaml.set(path + ".chunks", f.chunks.stream().map(ChunkKey::encode).toList());
            yaml.set(path + ".borderBlocks", new ArrayList<>(f.borderBlocks));
            for (Map.Entry<UUID, UUID> entry : f.lastVictims.entrySet())
                yaml.set(path + ".lastVictims." + entry.getKey(), entry.getValue().toString());
        }
        for (Map.Entry<UUID, ItemStack> entry : pendingMaceReturns.entrySet())
            yaml.set("pendingMaceReturns." + entry.getKey(), entry.getValue());
        try { yaml.save(file); }
        catch (IOException ex) { plugin.getLogger().severe("Could not save factions.yml: " + ex.getMessage()); }
    }
}
