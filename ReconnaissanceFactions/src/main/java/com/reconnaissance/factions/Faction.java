package com.reconnaissance.factions;

import java.util.*;

final class Faction {
    final UUID id;
    final String name;
    UUID owner;
    final Set<UUID> members = new LinkedHashSet<>();
    final Set<ChunkKey> chunks = new LinkedHashSet<>();
    final Set<String> borderBlocks = new LinkedHashSet<>();
    final Map<UUID, UUID> lastVictims = new HashMap<>();
    int points;
    int expansionChunks;
    boolean official;
    String color = "white";
    UUID worldId;

    Faction(UUID id, String name, UUID owner) {
        this.id = id;
        this.name = name;
        this.owner = owner;
        this.members.add(owner);
    }
}

record ChunkKey(int x, int z) {
    String encode() { return x + "," + z; }
    static ChunkKey decode(String value) {
        String[] bits = value.split(",");
        return new ChunkKey(Integer.parseInt(bits[0]), Integer.parseInt(bits[1]));
    }
}
