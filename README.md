# InventoryCaps

Paper plugin with configurable per-player item caps. Defaults:
- Ender pearls: 48 total
- Totems of Undying: 3 total

Counts items in the player's storage, armour, offhand, nested bundles, and carried shulker boxes. Excess restricted items are removed when enforcement runs.

## Build
GitHub Actions builds the JAR on every push to main and on manual workflow dispatch. Download the `InventoryCaps-JAR` artifact from the completed Actions run.

## Compatibility note
Compiled against Paper API 1.21.11 with Java 21 bytecode and Bukkit API version 1.21. This is intended to be compatible with Paper versions using that API surface, but 26.3 has not yet been runtime-tested. Test on a backup server before production use.
