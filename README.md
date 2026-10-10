# Peripheral

A Minecraft 1.20.1 Fabric mod about the feeling that you are not alone. Nothing attacks you. Something is just always almost there.

A hidden unease meter rises while you are alone, in the dark or underground, and falls when you sleep, stay near other players or villages, or spend time in daylight. When it is your turn, the mod picks something that a real player could have done nearby, and lets you wonder.

Nothing happens during the first two in-game days of a world.

## Effects so far

| Effect | What you notice |
| --- | --- |
| `footstep_echo` | Footsteps a few blocks behind you, keeping pace with yours. They stop when you stop. |
| `phantom_container` | A chest, barrel, door, trapdoor or gate you can't see opens and closes. |
| `distant_mining` | Underground: someone mining through the rock nearby. |

## Playtesting

Operator commands:

- `/peripheral debug` toggles an on-screen readout of your unease, the countdown to the next event and why.
- `/peripheral unease [player]` and `/peripheral unease set <0-100> [player]`
- `/peripheral trigger <effect> [player]` fires an effect immediately, ignoring grace period, tier and cooldown.
- `/peripheral reload` reloads `config/peripheral.json`.

Config (`config/peripheral.json`):

| Key | Default | Meaning |
| --- | --- | --- |
| `gracePeriodDays` | `2.0` | In-game days of world age before anything happens |
| `uneaseRiseMultiplier` | `1.0` | How fast unease rises |
| `eventIntervalMultiplier` | `1.0` | Time between events; lower is more often |
| `affectCreativePlayers` | `false` | Whether creative players are haunted |
| `effects` | `{}` | Per-effect switches, e.g. `{"distant_mining": false}` |

The mod must be installed on both the server and the client.

## License

CC0. See [LICENSE](LICENSE).
