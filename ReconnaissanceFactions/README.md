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

## Notes

- Claims and faction data are stored in the plugin data folder.
- Configure `spawn-protection-radius` to match the server's actual spawn protection radius.
- The TAB header is branded Reconnaissance SMP. The Minecraft client renders each listed player's own skin; plugins cannot replace the native player-list skin with arbitrary faction-head icons through the standard Paper API.
