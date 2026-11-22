# Reconnaissance Factions

Paper plugin for Reconnaissance SMP.

## Build

Requires Java 25 and Gradle 9.1+.

```bash
gradle build
```

The plugin JAR is created in `build/libs/`.

## Commands

- `/team create <name>` - create a team (maximum 10 letters)
- `/team invite <player>` - invite a player (leader only)
- `/team accept` - accept a pending invite
- `/team` - show your team and members
- `/faction create` - create an 8x8 chunk claim (requires 4 members)
- `/faction leaderboard` - show faction points
- `/faction <name>` - show faction details
- `/faction color <colour>` - set faction display colour (leader, after claiming)
- `/expand` - spend one expansion chunk on an adjacent chunk (leader only)

## Top-three faction maces

The three highest-ranked official factions each receive one mace for their faction. It is named after the faction and has Unbreaking III, Mending, Density V, and Wind Burst I. The mace is tagged with its faction ID, restricted to current faction members, and removed when that faction falls outside the top three. If an opposing player tries to pick it up, the pickup is cancelled and the mace is returned to the faction leader (or reissued when the leader next joins). Rankings and rewards resynchronise after eligible point changes and on player join.

## Notes

- Claims and faction data are stored in the plugin data folder.
- Configure `spawn-protection-radius` to match the server's actual spawn protection radius.
- The TAB header is branded Reconnaissance SMP. The Minecraft client renders each listed player's own skin; plugins cannot replace the native player-list skin with arbitrary faction-head icons through the standard Paper API.
