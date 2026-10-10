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
| `left_open` | A door, trapdoor or gate you left closed at home is open when you get back. Daytime only. |
| `torch_out` | A torch at the edge of your home is gone, lying on the ground below where it was. |
| `someone_elses_work` | Far from home: a lone torch, a dirt pillar, or underground a short 1x2 tunnel you didn't make. |
| `staring_animal` | A nearby animal stops and follows you with its head until you leave. |
| `the_watcher` | A figure stands at the edge of your view, 20 to 40 blocks away. Look straight at it, get close, or look away for long and it's gone. |
| `behind_you` | The figure stands right behind you for a few moments. Only a quick turn catches it. |
| `breathing` | In tight, dark places: slow breathing just behind your head. Needs `breathing.ogg` (see below). |

Tier 1 (the first three) can happen any time. Tier 2 needs unease of 25 or more, tier 3 (the figure and breathing) 50 or more, and a tier 3 event knocks unease back down by 30. World changes only ever touch doors, single torches, natural stone and open air, and never happen where you can see them.

## The figure

The figure is only drawn on the haunted player's screen; it doesn't exist on the server and nobody else can see it. It wears one skin per world, so it always looks like the same stranger. When a world is first opened, the server borrows the skin of a random real player from Mojang (by trying made-up usernames until one exists). If that fails, for example offline, it wears one of Minecraft's default skins and tries again on the next start, until the figure has been seen once. It never has a name tag.

## Custom sounds

Put custom sounds in `src/main/resources/assets/peripheral/sounds/`:

| File | Used by |
| --- | --- |
| `breathing.ogg` | `breathing` (mono, so it plays from a position) |

## Playtesting

Operator commands:

- `/peripheral debug` toggles an on-screen readout of your unease, the countdown to the next event, and how far and in which direction the last event was. While it's on, every event also posts a chat line with its coordinates (click to teleport) and marks the spot with a column of particles for 30 seconds. Footstep echoes mark each step with smoke.
- `/peripheral unease [player]` and `/peripheral unease set <0-100> [player]`
- `/peripheral trigger <effect> [player]` fires an effect immediately, ignoring grace period, tier and cooldown, and always reports where it happened.
- `/peripheral reload` reloads `config/peripheral.json`.

Config (`config/peripheral.json`):

| Key | Default | Meaning |
| --- | --- | --- |
| `preset` | `"default"` | `"subtle"` (tier 1 and 2 only, slower), `"default"`, or `"relentless"` (faster) |
| `allowWorldChanges` | `true` | Whether effects may open doors, take torches, build or dig |
| `allowFigure` | `true` | Whether the figure can appear |
| `gracePeriodDays` | `2.0` | In-game days of world age before anything happens |
| `uneaseRiseMultiplier` | `1.0` | How fast unease rises, on top of the preset |
| `eventIntervalMultiplier` | `1.0` | Time between events, on top of the preset; lower is more often |
| `affectCreativePlayers` | `false` | Whether creative players are haunted |
| `effects` | `{}` | Per-effect switches, e.g. `{"distant_mining": false}` |

The mod must be installed on both the server and the client.

## License

CC0. See [LICENSE](LICENSE).
